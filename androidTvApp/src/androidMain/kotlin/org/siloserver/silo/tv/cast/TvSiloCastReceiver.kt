package org.siloserver.silo.tv.cast

import android.util.Log
import java.io.Closeable
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.siloserver.silo.cast.SiloCastError
import org.siloserver.silo.cast.SiloCastHello
import org.siloserver.silo.cast.SiloCastLaunchRequest
import org.siloserver.silo.cast.SiloCastMessage
import org.siloserver.silo.cast.SiloCastPeerRole
import org.siloserver.silo.cast.SiloCastPlaybackState
import org.siloserver.silo.cast.SiloCastProtocol
import org.siloserver.silo.common.cast.SiloCastFrame
import org.siloserver.silo.common.cast.SiloCastFrameBuffer
import org.siloserver.silo.common.cast.SiloCastNsdAdvertiser
import org.siloserver.silo.common.lan.SiloCastTls
import org.siloserver.silo.common.lan.SiloCastTlsSession
import org.siloserver.silo.network.ServerRegistry

/**
 * TV-side SiloCast receiver, session-semantics-compatible with silo-apple's
 * TVControlReceiver (Apple clients are already shipped and cannot change):
 *
 * - Transport is TLS-PSK ([SiloCastTls]) over the advertised `_silocast._tcp`
 *   port; newest controller wins the single session slot.
 * - The TV sends its `hello` + a state snapshot immediately after the TLS
 *   handshake. The controller must reply with a `hello` whose serverId
 *   matches the TV's active server within [AUTH_GRACE_MS] — that check is
 *   the trust anchor (the PSK is fixed and non-secret). Mismatch → `error
 *   server_mismatch` + graceful close; silence → close.
 * - `launch`/`control` before authorization → `error unauthorized`.
 * - Heartbeat: the TV pings every [HEARTBEAT_INTERVAL_MS]; only a `pong`
 *   proves the controller can still receive, so only `pong` resets the
 *   missed counter. More than [MAX_MISSED_HEARTBEATS] misses → drop.
 * - State pushes every [STATE_INTERVAL_MS] (Apple's 500ms cadence) while a
 *   player is registered, an idle "Ready" state otherwise, and one push
 *   right after every accepted control command.
 * - A deliberate teardown sends a `close` frame ahead of the FIN so the
 *   controller can tell "disconnected on purpose" from a dropped link.
 */
class TvSiloCastReceiver(
    private val advertiser: SiloCastNsdAdvertiser,
    private val serverRegistry: ServerRegistry,
    private val deviceNameProvider: () -> String,
    private val deviceIdProvider: () -> String,
) {
    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    private var scope: CoroutineScope? = null
    private var serverSocket: ServerSocket? = null
    private var activeSession: ControllerSession? = null
    private var activePlayer: ActivePlayer? = null
    private var launchHandler: ((SiloCastLaunchRequest) -> Unit)? = null

    @Synchronized
    fun start() {
        if (scope != null) return
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = newScope
        newScope.launch {
            val socket = ServerSocket(0).also { serverSocket = it }
            val server = serverRegistry.activeEntry.value
            advertiser.start(
                port = socket.localPort,
                serverId = server?.id,
                serverName = server?.displayName,
                playing = activePlayer != null,
            )
            Log.i(TAG, "SiloCast listening on ${socket.localPort} for ${SiloCastProtocol.serviceType}")
            acceptLoop(socket)
        }
    }

    @Synchronized
    fun stop() {
        advertiser.stop()
        closePreviousController()
        runCatching { serverSocket?.close() }
        serverSocket = null
        scope?.cancel()
        scope = null
    }

    @Synchronized
    fun setLaunchHandler(handler: ((SiloCastLaunchRequest) -> Unit)?) {
        launchHandler = handler
    }

    @Synchronized
    fun registerPlayer(
        adapter: TvSiloCastPlayerAdapter,
        stateProvider: () -> SiloCastPlaybackState,
    ): Closeable {
        val player = ActivePlayer(adapter = adapter, stateProvider = stateProvider)
        activePlayer = player
        advertiser.updatePlaying(true)
        return Closeable {
            synchronized(this) {
                if (activePlayer === player) {
                    activePlayer = null
                    advertiser.updatePlaying(false)
                }
            }
        }
    }

    @Synchronized
    fun closePreviousController() {
        activeSession?.let { session ->
            session.enqueueGoodbyeAndClose()
        }
        activeSession = null
    }

    private suspend fun acceptLoop(socket: ServerSocket) {
        while (true) {
            val client = try {
                withContext(Dispatchers.IO) { socket.accept() }
            } catch (_: Throwable) {
                Log.i(TAG, "SiloCast listener stopped")
                return
            }
            closePreviousController()
            val ownerScope = scope ?: run {
                runCatching { client.close() }
                return
            }
            val sessionJob = ownerScope.launch {
                runControllerSession(client)
            }
            // runControllerSession registers the ControllerSession itself once
            // the TLS handshake succeeds; a handshake failure just ends the job.
            sessionJob.invokeOnCompletion { }
        }
    }

    private suspend fun runControllerSession(client: Socket) {
        val tls = try {
            withContext(Dispatchers.IO) { SiloCastTls.accept(client) }
        } catch (t: Throwable) {
            Log.w(TAG, "SiloCast TLS handshake failed", t)
            runCatching { client.close() }
            return
        }
        val session = ControllerSession(socket = client, tls = tls, json = json)
        synchronized(this) { activeSession = session }
        try {
            coroutineScope {
                session.job = coroutineContext[Job]

                // TV speaks first: hello + current state, like tvOS.
                session.send(SiloCastMessage.Hello(makeHello()))
                session.send(SiloCastMessage.State(currentState()))

                val stateJob = launch {
                    while (isActive) {
                        session.send(SiloCastMessage.State(currentState()))
                        delay(STATE_INTERVAL_MS)
                    }
                }
                val heartbeatJob = launch {
                    while (isActive) {
                        delay(HEARTBEAT_INTERVAL_MS)
                        session.missedHeartbeats += 1
                        if (session.missedHeartbeats > MAX_MISSED_HEARTBEATS) {
                            Log.i(TAG, "SiloCast controller heartbeat timed out")
                            session.close()
                            return@launch
                        }
                        session.send(SiloCastMessage.Ping())
                    }
                }
                val authWatchdog = launch {
                    delay(AUTH_GRACE_MS)
                    if (!session.isAuthorized) {
                        Log.i(TAG, "SiloCast controller never authorized; closing")
                        session.enqueueGoodbyeAndClose()
                    }
                }

                readLoop(tls) { message ->
                    handleMessage(session, message)
                }
                stateJob.cancel()
                heartbeatJob.cancel()
                authWatchdog.cancel()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "SiloCast controller session ended", t)
        } finally {
            synchronized(this) {
                if (activeSession === session) {
                    activeSession = null
                }
            }
            session.close()
        }
    }

    /** @return false to end the session's read loop. */
    private suspend fun handleMessage(session: ControllerSession, message: SiloCastMessage): Boolean {
        when (message) {
            is SiloCastMessage.Hello -> {
                val activeServerId = serverRegistry.activeServerId.value
                val offered = message.hello.serverId
                if (offered.isNullOrEmpty() || activeServerId == null || offered != activeServerId) {
                    session.send(
                        SiloCastMessage.Error(
                            SiloCastError(
                                code = "server_mismatch",
                                message = "This TV is connected to a different Silo server.",
                            ),
                        ),
                    )
                    session.enqueueGoodbyeAndClose()
                    return false
                }
                session.isAuthorized = true
            }
            is SiloCastMessage.Launch -> {
                if (!requireAuthorized(session)) return true
                if (message.launch.serverId != serverRegistry.activeServerId.value) {
                    session.send(
                        SiloCastMessage.Error(
                            SiloCastError(
                                code = "server_mismatch",
                                message = "This TV is connected to a different Silo server.",
                            ),
                        ),
                    )
                    return true
                }
                launchHandler?.invoke(message.launch)
            }
            is SiloCastMessage.Control -> {
                if (!requireAuthorized(session)) return true
                val player = activePlayer
                if (player == null) {
                    session.send(
                        SiloCastMessage.Error(
                            SiloCastError(code = "player_not_ready", message = "The TV player is not ready yet."),
                        ),
                    )
                    return true
                }
                player.adapter.handle(message.control)
                session.send(SiloCastMessage.State(currentState()))
            }
            is SiloCastMessage.Ping -> session.send(SiloCastMessage.Pong())
            is SiloCastMessage.Pong -> session.missedHeartbeats = 0
            is SiloCastMessage.Close -> return false
            is SiloCastMessage.State, is SiloCastMessage.Error -> Unit
        }
        return true
    }

    private suspend fun requireAuthorized(session: ControllerSession): Boolean {
        if (session.isAuthorized) return true
        session.send(
            SiloCastMessage.Error(
                SiloCastError(code = "unauthorized", message = "Connect with a matching Silo account first."),
            ),
        )
        return false
    }

    private suspend fun readLoop(
        tls: SiloCastTlsSession,
        onMessage: suspend (SiloCastMessage) -> Boolean,
    ) {
        val buffer = SiloCastFrameBuffer()
        val chunk = ByteArray(8 * 1024)
        while (true) {
            val read = withContext(Dispatchers.IO) { tls.input.read(chunk) }
            if (read < 0) return
            val payloads = buffer.append(chunk.copyOf(read))
            for (payload in payloads) {
                val text = payload.decodeToString()
                val message = json.decodeFromString(SiloCastMessage.serializer(), text)
                if (!onMessage(message)) return
            }
        }
    }

    private fun makeHello(): SiloCastHello {
        val server = serverRegistry.activeEntry.value
        return SiloCastHello(
            role = SiloCastPeerRole.Tv,
            deviceName = deviceNameProvider(),
            deviceId = deviceIdProvider(),
            serverId = server?.id,
            serverName = server?.displayName,
            supportedVersions = listOf(SiloCastProtocol.version),
        )
    }

    private fun currentState(): SiloCastPlaybackState =
        activePlayer?.stateProvider?.invoke() ?: idleState()

    private fun idleState(): SiloCastPlaybackState = SiloCastPlaybackState(
        contentId = null,
        sessionId = null,
        title = "Ready",
        subtitle = null,
        isPlaying = false,
        isLoading = false,
        isBuffering = false,
        currentTime = 0.0,
        duration = 0.0,
        audioTracks = emptyList(),
        subtitleTracks = emptyList(),
        selectedAudioTrackId = null,
        selectedSubtitleTrackId = null,
        qualityOptions = emptyList(),
        activeQualityId = "auto",
        isQualitySwitching = false,
        playbackSpeed = 1.0,
        videoGravity = "fit",
        hdrEnabled = false,
        supportsVideoGravity = false,
        supportsHDRToggle = false,
        volume = 1.0,
        isMuted = false,
        hasNextEpisode = false,
        nextEpisodeTitle = null,
        error = null,
    )

    private class ControllerSession(
        val socket: Socket,
        val tls: SiloCastTlsSession,
        private val json: Json,
    ) {
        val writeMutex = Mutex()

        @Volatile
        var isAuthorized: Boolean = false

        @Volatile
        var missedHeartbeats: Int = 0
        var job: Job? = null

        suspend fun send(message: SiloCastMessage) {
            val payload = json.encodeToString(SiloCastMessage.serializer(), message).encodeToByteArray()
            val frame = SiloCastFrame.encode(payload)
            writeMutex.withLock {
                withContext(Dispatchers.IO) {
                    tls.output.write(frame)
                    tls.output.flush()
                }
            }
        }

        /**
         * Best-effort `close` goodbye ahead of the FIN, then teardown. The
         * goodbye lets the peer distinguish a deliberate disconnect from a
         * dropped link (Apple auto-reconnects on bare EOF).
         */
        fun enqueueGoodbyeAndClose() {
            val payload = json.encodeToString(
                SiloCastMessage.serializer(),
                SiloCastMessage.Close(),
            ).encodeToByteArray()
            runCatching {
                tls.output.write(SiloCastFrame.encode(payload))
                tls.output.flush()
            }
            close()
        }

        fun close() {
            tls.close()
            runCatching { socket.close() }
            job?.cancel()
        }
    }

    private data class ActivePlayer(
        val adapter: TvSiloCastPlayerAdapter,
        val stateProvider: () -> SiloCastPlaybackState,
    )

    private companion object {
        const val TAG = "TvSiloCastReceiver"

        // Apple TVControlReceiver constants — keep in lockstep.
        const val STATE_INTERVAL_MS = 500L
        const val HEARTBEAT_INTERVAL_MS = 3_000L
        const val MAX_MISSED_HEARTBEATS = 3
        const val AUTH_GRACE_MS = 5_000L
    }
}
