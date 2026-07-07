package org.siloserver.silo.android.cast

import android.util.Log
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.siloserver.silo.cast.SiloCastControlCommand
import org.siloserver.silo.cast.SiloCastHello
import org.siloserver.silo.cast.SiloCastLaunchRequest
import org.siloserver.silo.cast.SiloCastMessage
import org.siloserver.silo.cast.SiloCastPeerRole
import org.siloserver.silo.cast.SiloCastPlaybackClock
import org.siloserver.silo.cast.SiloCastPlaybackState
import org.siloserver.silo.cast.SiloCastProtocol
import org.siloserver.silo.common.cast.SiloCastFrame
import org.siloserver.silo.common.cast.SiloCastFrameBuffer
import org.siloserver.silo.common.cast.SiloCastNsdBrowser
import org.siloserver.silo.common.cast.SiloCastTarget
import org.siloserver.silo.common.lan.SiloCastTls
import org.siloserver.silo.common.lan.SiloCastTlsClientSession
import org.siloserver.silo.network.ServerRegistry

data class SiloCastControllerState(
    val targets: List<SiloCastTarget> = emptyList(),
    val connectedTarget: SiloCastTarget? = null,
    val playbackState: SiloCastPlaybackState? = null,
    val isConnecting: Boolean = false,
    val error: String? = null,
) {
    val isConnected: Boolean get() = connectedTarget != null
}

/**
 * Phone-side SiloCast controller. Wire-compatible with silo-apple's
 * SiloControlClient/TVControlReceiver: TLS-PSK transport ([SiloCastTls]),
 * a `hello` carrying role=phone and the active serverId (the receiver
 * authorizes only a matching server), pong replies to receiver pings, and a
 * `close` goodbye on deliberate disconnect so the receiver doesn't treat it
 * as a dropped link.
 */
class SiloCastController(
    private val browser: SiloCastNsdBrowser,
    private val serverRegistry: ServerRegistry,
    private val deviceNameProvider: () -> String,
    private val deviceIdProvider: () -> String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }
    private val sendMutex = Mutex()
    private val clock = SiloCastPlaybackClock()

    private val _state = MutableStateFlow(SiloCastControllerState())
    val state: StateFlow<SiloCastControllerState> = _state.asStateFlow()

    private var session: SiloCastTlsClientSession? = null
    private var output: OutputStream? = null
    private var readJob: Job? = null

    init {
        scope.launch {
            browser.targets.collect { targets ->
                _state.update { it.copy(targets = targets) }
            }
        }
    }

    fun startBrowsing() {
        browser.start()
    }

    fun stopBrowsing() {
        browser.stop()
    }

    fun launchOnTarget(target: SiloCastTarget, request: SiloCastLaunchRequest) {
        scope.launch {
            runCatching {
                ensureConnected(target)
                send(SiloCastMessage.Launch(request))
            }.onFailure { error ->
                if (error !is CancellationException) {
                    _state.update { it.copy(isConnecting = false, error = error.message ?: "Unable to cast.") }
                }
            }
        }
    }

    fun disconnect() {
        scope.launch {
            runCatching { send(SiloCastMessage.Close()) }
            closeConnection()
        }
    }

    fun playPause() {
        sendControl(SiloCastControlCommand.playPause())
        clock.setOptimisticPlaying(!isPlaying(), nowMs())
    }

    fun seek(seconds: Double) {
        sendControl(SiloCastControlCommand.seek(seconds))
        clock.setOptimisticTime(seconds, nowMs())
    }

    fun selectAudioTrack(trackId: Long) {
        sendControl(SiloCastControlCommand.selectAudioTrack(trackId))
    }

    fun selectSubtitleTrack(trackId: Long?) {
        sendControl(SiloCastControlCommand.selectSubtitleTrack(trackId))
    }

    fun selectQuality(qualityId: String) {
        sendControl(SiloCastControlCommand.setQuality(qualityId))
    }

    fun setPlaybackSpeed(speed: Double) {
        sendControl(SiloCastControlCommand.setPlaybackSpeed(speed))
    }

    fun playNext() {
        sendControl(SiloCastControlCommand.playNext())
    }

    fun displayTime(): Double = clock.displayTime(nowMs())

    fun isPlaying(): Boolean = clock.isPlaying(nowMs())

    private fun sendControl(command: SiloCastControlCommand) {
        scope.launch {
            runCatching { send(SiloCastMessage.Control(command)) }
                .onFailure { error -> _state.update { it.copy(error = error.message) } }
        }
    }

    private suspend fun ensureConnected(target: SiloCastTarget) {
        if (_state.value.connectedTarget?.deviceId == target.deviceId && session?.isConnected == true) return
        closeConnection()
        _state.update { it.copy(isConnecting = true, error = null) }
        val newSession = withContext(Dispatchers.IO) {
            SiloCastTls.connect(target.host, target.port, CONNECT_TIMEOUT_MS)
        }
        session = newSession
        output = newSession.output
        _state.update { it.copy(connectedTarget = target, isConnecting = false, error = null) }
        send(SiloCastMessage.Hello(makeHello()))
        readJob = scope.launch { readLoop(newSession) }
    }

    private fun makeHello(): SiloCastHello {
        val server = serverRegistry.activeEntry.value
        return SiloCastHello(
            role = SiloCastPeerRole.Phone,
            deviceName = deviceNameProvider(),
            deviceId = deviceIdProvider(),
            serverId = server?.id,
            serverName = server?.displayName,
            supportedVersions = listOf(SiloCastProtocol.version),
        )
    }

    private suspend fun readLoop(activeSession: SiloCastTlsClientSession) {
        val frameBuffer = SiloCastFrameBuffer()
        val input = activeSession.input
        val chunk = ByteArray(8 * 1024)
        try {
            while (true) {
                val read = withContext(Dispatchers.IO) { input.read(chunk) }
                if (read < 0) break
                frameBuffer.append(chunk.copyOf(read)).forEach { payload ->
                    handleMessage(json.decodeFromString(SiloCastMessage.serializer(), payload.decodeToString()))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "SiloCast read loop ended", t)
            // Guard like closeConnection() below: the old session's read loop
            // can outlive a reconnect and must not clobber the fresh
            // session's state with its stale error.
            if (session === activeSession) {
                _state.update { it.copy(error = t.message) }
            }
        } finally {
            if (session === activeSession) {
                closeConnection()
            }
        }
    }

    private suspend fun handleMessage(message: SiloCastMessage) {
        when (message) {
            is SiloCastMessage.Hello -> Unit // receiver identity; nothing to act on
            is SiloCastMessage.State -> {
                clock.ingest(message.state, nowMs())
                _state.update { it.copy(playbackState = message.state, error = null) }
            }
            is SiloCastMessage.Error -> _state.update { it.copy(error = message.error.message) }
            is SiloCastMessage.Ping -> send(SiloCastMessage.Pong())
            is SiloCastMessage.Pong -> Unit
            is SiloCastMessage.Close -> closeConnection()
            else -> Unit
        }
    }

    private suspend fun send(message: SiloCastMessage) {
        val out = output ?: error("SiloCast is not connected.")
        val frame = SiloCastFrame.encode(json.encodeToString(SiloCastMessage.serializer(), message).encodeToByteArray())
        sendMutex.withLock {
            withContext(Dispatchers.IO) {
                out.write(frame)
                out.flush()
            }
        }
    }

    private fun closeConnection() {
        runCatching { session?.close() }
        session = null
        output = null
        readJob?.cancel()
        readJob = null
        _state.update { it.copy(connectedTarget = null, playbackState = null, isConnecting = false) }
    }

    fun close() {
        browser.stop()
        closeConnection()
        scope.cancel()
    }

    private fun nowMs(): Long = android.os.SystemClock.elapsedRealtime()

    private companion object {
        const val TAG = "SiloCastController"
        const val CONNECT_TIMEOUT_MS = 5_000
    }
}
