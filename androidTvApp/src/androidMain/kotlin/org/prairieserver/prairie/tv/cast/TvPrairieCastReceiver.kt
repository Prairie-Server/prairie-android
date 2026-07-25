package org.prairieserver.prairie.tv.cast

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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.prairieserver.prairie.cast.PrairieCastError
import org.prairieserver.prairie.cast.PrairieCastHello
import org.prairieserver.prairie.cast.PrairieCastHandoffCancel
import org.prairieserver.prairie.cast.PrairieCastLaunchRequest
import org.prairieserver.prairie.cast.PrairieCastMessage
import org.prairieserver.prairie.cast.PrairieCastPeerRole
import org.prairieserver.prairie.cast.PrairieCastPlaybackState
import org.prairieserver.prairie.cast.PrairieCastProtocol
import org.prairieserver.prairie.common.cast.PrairieCastFrame
import org.prairieserver.prairie.common.diagnostics.DiagnosticsCastLogger
import org.prairieserver.prairie.common.cast.PrairieCastFrameBuffer
import org.prairieserver.prairie.common.cast.PrairieCastNsdAdvertiser
import org.prairieserver.prairie.common.lan.PrairieCastTls
import org.prairieserver.prairie.common.lan.PrairieCastTlsSession
import org.prairieserver.prairie.network.ServerRegistry

/**
 * TV-side PrairieCast receiver for the cross-platform v2 protocol shared with
 * prairie-apple's TVControlReceiver:
 *
 * - Transport is TLS-PSK ([PrairieCastTls]) over the advertised `_prairiecast._tcp`
 *   port; newest controller wins the single session slot.
 * - The TV sends `hello` immediately after the TLS handshake. The controller
 *   must reply within [AUTH_GRACE_MS]. A same-server controller is authorized
 *   directly; a different-server launch must first complete the temporary
 *   remote-playback handoff. Playback state is never sent before authorization.
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
class TvPrairieCastReceiver(
    private val advertiser: PrairieCastNsdAdvertiser,
    private val serverRegistry: ServerRegistry,
    private val identityManager: RemotePlaybackIdentityManager,
    private val deviceNameProvider: () -> String,
    private val deviceIdProvider: () -> String,
) {
    data class StandbyState(
        val controllerName: String?,
        val serverName: String?,
    ) {
        val controllerLabel: String get() = controllerName?.takeIf { it.isNotBlank() } ?: "phone"
    }

    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    private var scope: CoroutineScope? = null
    private var serverSocket: ServerSocket? = null
    @Volatile
    private var activeSession: ControllerSession? = null
    private val _standbyState = MutableStateFlow<StandbyState?>(null)
    val standbyState: StateFlow<StandbyState?> = _standbyState.asStateFlow()

    /** Bumped whenever the active-controller slot is force-cleared (new accept,
     *  server switch, stop). Handshakes started before the bump must not
     *  register — they belong to the previous epoch. */
    private var sessionEpoch: Long = 0
    private var activePlayer: ActivePlayer? = null
    private val launchRequestChannel = Channel<PrairieCastLaunchRequest>(capacity = 1)
    val launchRequests: Flow<PrairieCastLaunchRequest> = launchRequestChannel.receiveAsFlow()
    private var pendingPlayerIdentityGeneration: String? = null
    private var identityEndJob: Job? = null
    // stop() cancels the receiver scope, so cleanup for a ready-but-unconsumed
    // temporary identity must have an owner that survives that cancellation.
    private val identityCleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Synchronized
    fun start() {
        if (scope != null) return
        DiagnosticsCastLogger.event("TV cast receiver started")
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = newScope
        newScope.launch {
            val socket = ServerSocket(0).also { serverSocket = it }
            val server = serverRegistry.activeEntry.value
            val remote = identityManager.activeIdentity
            advertiser.start(
                port = socket.localPort,
                serverId = remote?.serverId ?: server?.id,
                serverName = remote?.serverName ?: server?.displayName,
                playing = activePlayer != null,
            )
            Log.i(TAG, "PrairieCast listening on ${socket.localPort} for ${PrairieCastProtocol.serviceType}")
            acceptLoop(socket)
        }
        // Keep the Bonjour TXT record in sync with the active server while the
        // receiver runs (it stays up across server switches until onStop), and
        // drop the live controller session — its hello was authorized against
        // the previous server, so keeping it would let an old-server remote
        // drive playback on the new one.
        newScope.launch {
            serverRegistry.activeEntry.drop(1).collect { entry ->
                closePreviousController()
                identityManager.end()
                val port = synchronized(this@TvPrairieCastReceiver) { serverSocket?.localPort }
                if (port != null) {
                    advertiser.start(
                        port = port,
                        serverId = entry?.id,
                        serverName = entry?.displayName,
                        playing = activePlayer != null,
                    )
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        DiagnosticsCastLogger.event("TV cast receiver stopped")
        advertiser.stop()
        val identityGeneration = identityManager.activeIdentity?.generationId
        pendingPlayerIdentityGeneration = null
        identityEndJob = null
        // Close the session directly (not via closePreviousController, which
        // launches the goodbye on `scope` — the scope we cancel a line later,
        // which would kill the goodbye before it writes). A direct close()
        // sends the FIN; the goodbye frame is best-effort and the socket
        // teardown below guarantees the peer disconnects regardless.
        sessionEpoch += 1
        activeSession?.close()
        activeSession = null
        _standbyState.value = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        scope?.cancel()
        scope = null
        if (identityGeneration != null) {
            identityCleanupScope.launch {
                // Exact-generation guard: a rapid stop/start/new handoff must
                // not let the old shutdown clean up the replacement identity.
                if (identityManager.activeIdentity?.generationId == identityGeneration) {
                    identityManager.end()
                }
            }
        }
    }

    @Synchronized
    fun registerPlayer(
        adapter: TvPrairieCastPlayerAdapter,
        stateProvider: () -> PrairieCastPlaybackState,
    ): Closeable {
        DiagnosticsCastLogger.event("TV cast player registered")
        identityEndJob?.cancel()
        identityEndJob = null
        val identityGeneration = pendingPlayerIdentityGeneration
            ?: identityManager.activeIdentity?.generationId
        pendingPlayerIdentityGeneration = null
        val player = ActivePlayer(
            adapter = adapter,
            stateProvider = stateProvider,
            identityGeneration = identityGeneration,
        )
        activePlayer = player
        _standbyState.value = null
        advertiser.updatePlaying(true)
        return Closeable {
            synchronized(this) {
                if (activePlayer === player) {
                    DiagnosticsCastLogger.event("TV cast player unregistered")
                    activePlayer = null
                    advertiser.updatePlaying(false)
                    if (player.identityGeneration != null) {
                        scheduleIdentityEnd(player.identityGeneration)
                    }
                    refreshStandbyState()
                }
            }
        }
    }

    @Synchronized
    fun closePreviousController() {
        // Bump the epoch unconditionally — a handshake in flight (not yet
        // registered, so activeSession is still null) during a server switch
        // must also be invalidated, else it registers into the new epoch.
        sessionEpoch += 1
        val session = activeSession ?: return
        DiagnosticsCastLogger.event("TV cast controller disconnected")
        activeSession = null
        _standbyState.value = null
        val owner = scope
        if (owner != null) {
            // Goodbye + teardown off the monitor — a blocking write to a
            // half-open peer must not stall registerPlayer/stop/accept.
            owner.launch { session.goodbyeAndClose() }
        } else {
            session.close()
        }
    }

    fun disconnectRemoteControl() {
        closePreviousController()
    }

    private suspend fun acceptLoop(socket: ServerSocket) {
        while (true) {
            val client = try {
                withContext(Dispatchers.IO) { socket.accept() }
            } catch (_: Throwable) {
                Log.i(TAG, "PrairieCast listener stopped")
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
        val epochAtAccept = synchronized(this) { sessionEpoch }
        val tls = try {
            withContext(Dispatchers.IO) { PrairieCastTls.accept(client) }
        } catch (t: Throwable) {
            Log.w(TAG, "PrairieCast TLS handshake failed", t)
            DiagnosticsCastLogger.warning("TV cast handshake failed")
            runCatching { client.close() }
            return
        }
        val session = ControllerSession(socket = client, tls = tls, json = json)
        val registered = synchronized(this) {
            if (sessionEpoch != epochAtAccept) {
                // A server switch / newer controller / stop() happened while
                // this handshake was in flight — this session lost.
                false
            } else {
                // Newest wins: a session that finished handshaking after us in
                // the same epoch would have replaced us here; close any loser.
                activeSession?.close()
                activeSession = session
                true
            }
        }
        if (!registered) {
            session.close()
            return
        }
        DiagnosticsCastLogger.event("TV cast controller connected")
        try {
            coroutineScope {
                session.job = coroutineContext[Job]

                // TV speaks first with identity only. Playback state is private
                // until the controller's hello has been authorized.
                session.send(PrairieCastMessage.Hello(makeHello()))

                val stateJob = launch {
                    while (isActive) {
                        if (session.isAuthorized) {
                            session.send(PrairieCastMessage.State(currentState()))
                        }
                        delay(STATE_INTERVAL_MS)
                    }
                }
                val heartbeatJob = launch {
                    while (isActive) {
                        delay(HEARTBEAT_INTERVAL_MS)
                        session.missedHeartbeats += 1
                        if (session.missedHeartbeats > MAX_MISSED_HEARTBEATS) {
                            Log.i(TAG, "PrairieCast controller heartbeat timed out")
                            DiagnosticsCastLogger.warning("TV cast controller timed out")
                            session.close()
                            return@launch
                        }
                        session.send(PrairieCastMessage.Ping())
                    }
                }
                val authWatchdog = launch {
                    delay(AUTH_GRACE_MS)
                    if (!session.didReceiveHello) {
                        Log.i(TAG, "PrairieCast controller never authorized; closing")
                        DiagnosticsCastLogger.warning("TV cast authorization timed out")
                        session.goodbyeAndClose()
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
            Log.w(TAG, "PrairieCast controller session ended", t)
        } finally {
            val orphanedReadyIdentity = if (session.remoteLaunchReady && activePlayer == null) {
                identityManager.activeIdentity?.generationId
            } else {
                null
            }
            synchronized(this) {
                if (activeSession === session) {
                    activeSession = null
                    _standbyState.value = null
                }
            }
            session.close()
            if (orphanedReadyIdentity != null) {
                scheduleIdentityEnd(orphanedReadyIdentity, READY_TIMEOUT_MS)
            }
        }
    }

    /** @return false to end the session's read loop. */
    private suspend fun handleMessage(session: ControllerSession, message: PrairieCastMessage): Boolean {
        when (message) {
            is PrairieCastMessage.Hello -> {
                val negotiated = PrairieCastProtocol.negotiatedVersion(message.hello.supportedVersions)
                if (message.hello.role != PrairieCastPeerRole.Phone || negotiated != PrairieCastProtocol.version) {
                    session.send(
                        PrairieCastMessage.Error(
                            PrairieCastError(
                                code = "version_unsupported",
                                message = "Update Prairie on both devices to continue.",
                            ),
                        ),
                    )
                    session.goodbyeAndClose()
                    return false
                }
                session.didReceiveHello = true
                session.negotiatedVersion = negotiated
                session.controllerDeviceId = message.hello.deviceId
                session.controllerDeviceName = message.hello.deviceName
                session.controllerServerId = message.hello.serverId
                val activeServerId = identityManager.activeIdentity?.serverId
                    ?: serverRegistry.activeServerId.value
                val offered = message.hello.serverId
                session.isAuthorized = !offered.isNullOrEmpty() && activeServerId != null && offered == activeServerId
                DiagnosticsCastLogger.event(
                    if (session.isAuthorized) "TV cast controller authorized" else "TV cast handoff required",
                )
                refreshStandbyState()
                if (session.isAuthorized) {
                    session.send(PrairieCastMessage.State(currentState()))
                }
            }
            is PrairieCastMessage.HandoffOffer -> {
                val offer = message.handoffOffer
                val controllerId = session.controllerDeviceId
                if (session.negotiatedVersion != PrairieCastProtocol.version || controllerId == null) {
                    session.send(
                        PrairieCastMessage.HandoffCancel(
                            PrairieCastHandoffCancel(
                                requestId = offer.requestId,
                                reason = "version_unsupported",
                                message = "Update Prairie on both devices to continue.",
                            ),
                        ),
                    )
                    return true
                }
                session.handoffJob?.cancel()
                identityEndJob?.cancel()
                identityEndJob = null
                session.handoffJob = scope?.launch {
                    try {
                        val ready = identityManager.prepare(
                            offer = offer,
                            controllerDeviceId = controllerId,
                            controllerDeviceName = session.controllerDeviceName,
                        ) { challenge ->
                            if (activeSession === session) {
                                session.send(PrairieCastMessage.HandoffChallenge(challenge))
                            }
                        }
                        if (activeSession !== session) {
                            identityManager.end()
                            return@launch
                        }
                        session.isAuthorized = true
                        DiagnosticsCastLogger.event("TV cast handoff authorized")
                        session.remoteLaunchReady = true
                        refreshStandbyState()
                        refreshAdvertisement()
                        session.send(PrairieCastMessage.HandoffReady(ready))
                        session.send(PrairieCastMessage.State(currentState()))
                        session.readyTimeoutJob?.cancel()
                        session.readyTimeoutJob = launch {
                            delay(READY_TIMEOUT_MS)
                            // Ready-without-launch only abandons an UNUSED
                            // identity: with a player active (a reused-identity
                            // offer mid-playback), ending it would rip the
                            // token overlay out from under the live stream.
                            if (activeSession === session && session.remoteLaunchReady) {
                                session.remoteLaunchReady = false
                                if (activePlayer == null) {
                                    identityManager.end()
                                    refreshAdvertisement()
                                    session.send(
                                        PrairieCastMessage.Error(
                                            PrairieCastError(
                                                code = "launch_timeout",
                                                message = "No content was launched, so the temporary profile was restored.",
                                            ),
                                        ),
                                    )
                                    session.goodbyeAndClose()
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        if (session.remoteLaunchReady && activePlayer == null) {
                            identityManager.end()
                            session.remoteLaunchReady = false
                            refreshAdvertisement()
                        }
                        if (activeSession === session) {
                            session.send(
                                PrairieCastMessage.HandoffCancel(
                                    PrairieCastHandoffCancel(
                                        requestId = offer.requestId,
                                        reason = "handoff_failed",
                                        message = t.message ?: "Remote playback handoff failed.",
                                    ),
                                ),
                            )
                        }
                    }
                }
            }
            is PrairieCastMessage.HandoffCancel -> {
                session.handoffJob?.cancel()
                session.handoffJob = null
                // Disarm the ready-without-launch watchdog: the offer it was
                // guarding is dead, and with a player active (identity in use
                // by live playback) letting it fire would end the session.
                session.readyTimeoutJob?.cancel()
                session.readyTimeoutJob = null
                if (session.remoteLaunchReady && activePlayer == null) {
                    val generation = identityManager.activeIdentity?.generationId
                    if (generation != null) scheduleIdentityEnd(generation, 0L)
                }
                session.remoteLaunchReady = false
            }
            is PrairieCastMessage.Launch -> {
                if (!requireAuthorized(session)) return true
                if (!session.remoteLaunchReady || identityManager.activeIdentity == null) {
                    session.send(
                        PrairieCastMessage.Error(
                            PrairieCastError(
                                code = "handoff_required",
                                message = "Prepare the phone profile before playing.",
                            ),
                        ),
                    )
                    return true
                }
                if (message.launch.serverId != identityManager.activeIdentity?.serverId) {
                    session.send(
                        PrairieCastMessage.Error(
                            PrairieCastError(
                                code = "server_mismatch",
                                message = "This TV is connected to a different Prairie server.",
                            ),
                        ),
                    )
                    return true
                }
                val generation = identityManager.activeIdentity?.generationId
                pendingPlayerIdentityGeneration = generation
                if (generation == null || !launchRequestChannel.trySend(message.launch).isSuccess) {
                    pendingPlayerIdentityGeneration = null
                    session.send(
                        PrairieCastMessage.Error(
                            PrairieCastError(
                                code = "launch_failed",
                                message = "The TV could not open remote playback.",
                            ),
                        ),
                    )
                    return true
                }
                session.remoteLaunchReady = false
                _standbyState.value = null
                session.readyTimeoutJob?.cancel()
                session.readyTimeoutJob = null
            }
            is PrairieCastMessage.Control -> {
                if (!requireAuthorized(session)) return true
                val player = activePlayer
                if (player == null) {
                    session.send(
                        PrairieCastMessage.Error(
                            PrairieCastError(code = "player_not_ready", message = "The TV player is not ready yet."),
                        ),
                    )
                    return true
                }
                // Media3 controllers and the Compose-backed state provider are
                // application-thread confined. The receiver reads the socket
                // on IO, so cross that boundary before touching the player.
                withContext(Dispatchers.Main.immediate) {
                    player.adapter.handle(message.control)
                }
                session.send(PrairieCastMessage.State(currentState()))
            }
            is PrairieCastMessage.Ping -> session.send(PrairieCastMessage.Pong())
            is PrairieCastMessage.Pong -> session.missedHeartbeats = 0
            is PrairieCastMessage.Close -> return false
            is PrairieCastMessage.State,
            is PrairieCastMessage.Error,
            is PrairieCastMessage.HandoffChallenge,
            is PrairieCastMessage.HandoffReady,
            -> Unit
        }
        return true
    }

    private suspend fun requireAuthorized(session: ControllerSession): Boolean {
        if (session.isAuthorized) return true
        session.send(
            PrairieCastMessage.Error(
                PrairieCastError(code = "unauthorized", message = "Connect with a matching Prairie account first."),
            ),
        )
        return false
    }

    private fun scheduleIdentityEnd(
        generationId: String,
        delayMs: Long = IDENTITY_END_GRACE_MS,
    ) {
        identityEndJob?.cancel()
        val owner = scope ?: return
        identityEndJob = owner.launch {
            delay(delayMs)
            if (identityManager.activeIdentity?.generationId != generationId) return@launch
            identityManager.end()
            pendingPlayerIdentityGeneration = null
            refreshAdvertisement()
            val session = activeSession
            if (session != null) {
                session.isAuthorized = session.controllerServerId == serverRegistry.activeServerId.value
                if (session.isAuthorized) {
                    session.send(PrairieCastMessage.State(currentState()))
                    refreshStandbyState()
                } else {
                    closePreviousController()
                }
            }
        }
    }

    private fun refreshStandbyState() {
        val session = activeSession
        _standbyState.value = if (session != null && session.isAuthorized && activePlayer == null) {
            StandbyState(
                controllerName = session.controllerDeviceName,
                serverName = identityManager.activeIdentity?.serverName
                    ?: serverRegistry.activeEntry.value?.displayName,
            )
        } else {
            null
        }
    }

    @Synchronized
    private fun refreshAdvertisement() {
        val port = serverSocket?.localPort ?: return
        val remote = identityManager.activeIdentity
        val saved = serverRegistry.activeEntry.value
        advertiser.start(
            port = port,
            serverId = remote?.serverId ?: saved?.id,
            serverName = remote?.serverName ?: saved?.displayName,
            playing = activePlayer != null,
        )
    }

    private suspend fun readLoop(
        tls: PrairieCastTlsSession,
        onMessage: suspend (PrairieCastMessage) -> Boolean,
    ) {
        val buffer = PrairieCastFrameBuffer()
        val chunk = ByteArray(8 * 1024)
        while (true) {
            val read = withContext(Dispatchers.IO) { tls.input.read(chunk) }
            if (read < 0) return
            val payloads = buffer.append(chunk.copyOf(read))
            for (payload in payloads) {
                val text = payload.decodeToString()
                val message = json.decodeFromString(PrairieCastMessage.serializer(), text)
                if (!onMessage(message)) return
            }
        }
    }

    private fun makeHello(): PrairieCastHello {
        val server = serverRegistry.activeEntry.value
        val remote = identityManager.activeIdentity
        return PrairieCastHello(
            role = PrairieCastPeerRole.Tv,
            deviceName = deviceNameProvider(),
            deviceId = deviceIdProvider(),
            serverId = remote?.serverId ?: server?.id,
            serverName = remote?.serverName ?: server?.displayName,
            supportedVersions = PrairieCastProtocol.supportedVersions,
        )
    }

    private suspend fun currentState(): PrairieCastPlaybackState =
        withContext(Dispatchers.Main.immediate) {
            activePlayer?.stateProvider?.invoke() ?: idleState()
        }

    private fun idleState(): PrairieCastPlaybackState = PrairieCastPlaybackState(
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
        val tls: PrairieCastTlsSession,
        private val json: Json,
    ) {
        val writeMutex = Mutex()

        @Volatile
        var isAuthorized: Boolean = false

        @Volatile
        var didReceiveHello: Boolean = false

        @Volatile
        var negotiatedVersion: Int? = null

        @Volatile
        var controllerDeviceId: String? = null

        @Volatile
        var controllerDeviceName: String? = null

        @Volatile
        var controllerServerId: String? = null

        @Volatile
        var remoteLaunchReady: Boolean = false

        @Volatile
        var missedHeartbeats: Int = 0
        var job: Job? = null
        var handoffJob: Job? = null
        var readyTimeoutJob: Job? = null

        suspend fun send(message: PrairieCastMessage) {
            val payload = json.encodeToString(PrairieCastMessage.serializer(), message).encodeToByteArray()
            val frame = PrairieCastFrame.encode(payload)
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
         * dropped link (Apple auto-reconnects on bare EOF). The write goes
         * through the same mutex as every other frame so a mid-flight state
         * push can't interleave with it; the whole thing runs suspending so
         * callers holding the receiver monitor don't block on a dead peer's
         * TCP buffers.
         */
        suspend fun goodbyeAndClose() {
            runCatching {
                kotlinx.coroutines.withTimeout(GOODBYE_TIMEOUT_MS) {
                    send(PrairieCastMessage.Close())
                }
            }
            close()
        }

        fun close() {
            handoffJob?.cancel()
            readyTimeoutJob?.cancel()
            tls.close()
            runCatching { socket.close() }
            job?.cancel()
        }
    }

    private data class ActivePlayer(
        val adapter: TvPrairieCastPlayerAdapter,
        val stateProvider: () -> PrairieCastPlaybackState,
        val identityGeneration: String?,
    )

    private companion object {
        const val TAG = "TvPrairieCastReceiver"

        // Apple TVControlReceiver constants — keep in lockstep.
        const val STATE_INTERVAL_MS = 500L
        const val HEARTBEAT_INTERVAL_MS = 3_000L
        const val MAX_MISSED_HEARTBEATS = 3
        const val AUTH_GRACE_MS = 5_000L
        const val GOODBYE_TIMEOUT_MS = 1_000L
        const val READY_TIMEOUT_MS = 60_000L
        const val IDENTITY_END_GRACE_MS = 2_000L
    }
}
