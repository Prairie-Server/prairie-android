package org.siloserver.silo.tv.cast

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.siloserver.silo.cast.SiloCastLaunchRequest
import org.siloserver.silo.cast.SiloCastMessage
import org.siloserver.silo.cast.SiloCastPlaybackState
import org.siloserver.silo.cast.SiloCastProtocol
import org.siloserver.silo.common.cast.SiloCastFrame
import org.siloserver.silo.common.cast.SiloCastFrameBuffer
import org.siloserver.silo.common.cast.SiloCastNsdAdvertiser
import java.io.Closeable
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

class TvSiloCastReceiver(
    private val advertiser: SiloCastNsdAdvertiser,
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
            advertiser.start(socket.localPort)
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
        return Closeable {
            synchronized(this) {
                if (activePlayer === player) {
                    activePlayer = null
                }
            }
        }
    }

    @Synchronized
    fun closePreviousController() {
        activeSession?.let { session ->
            runCatching { session.socket.close() }
            session.job.cancel()
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
            synchronized(this) {
                activeSession = ControllerSession(socket = client, job = sessionJob)
            }
        }
    }

    private suspend fun runControllerSession(client: Socket) {
        val outputMutex = Mutex()
        try {
            coroutineScope {
                val output = withContext(Dispatchers.IO) { client.getOutputStream() }
                val stateJob = launch {
                    while (isActive) {
                        activePlayer?.stateProvider?.invoke()?.let { state ->
                            send(output, outputMutex, SiloCastMessage.State(state))
                        }
                        delay(STATE_INTERVAL_MS)
                    }
                }

                readLoop(client) { message ->
                    when (message) {
                        is SiloCastMessage.Ping -> send(output, outputMutex, SiloCastMessage.Pong(message.id))
                        is SiloCastMessage.Launch -> launchHandler?.invoke(message.launch)
                        is SiloCastMessage.Control -> activePlayer?.adapter?.handle(message.command)
                        is SiloCastMessage.Close -> return@readLoop false
                        else -> Unit
                    }
                    true
                }
                stateJob.cancelAndJoin()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "SiloCast controller session ended", t)
        } finally {
            synchronized(this) {
                if (activeSession?.socket === client) {
                    activeSession = null
                }
            }
            runCatching { client.close() }
        }
    }

    private suspend fun readLoop(
        client: Socket,
        onMessage: suspend (SiloCastMessage) -> Boolean,
    ) {
        val input = withContext(Dispatchers.IO) { client.getInputStream() }
        val buffer = SiloCastFrameBuffer()
        val chunk = ByteArray(8 * 1024)
        while (true) {
            val read = withContext(Dispatchers.IO) { input.read(chunk) }
            if (read < 0) return
            val payloads = buffer.append(chunk.copyOf(read))
            for (payload in payloads) {
                val text = payload.decodeToString()
                val message = json.decodeFromString(SiloCastMessage.serializer(), text)
                if (!onMessage(message)) return
            }
        }
    }

    private suspend fun send(
        output: OutputStream,
        mutex: Mutex,
        message: SiloCastMessage,
    ) {
        val payload = json.encodeToString(SiloCastMessage.serializer(), message).encodeToByteArray()
        val frame = SiloCastFrame.encode(payload)
        mutex.withLock {
            withContext(Dispatchers.IO) {
                output.write(frame)
                output.flush()
            }
        }
    }

    private data class ControllerSession(
        val socket: Socket,
        val job: Job,
    )

    private data class ActivePlayer(
        val adapter: TvSiloCastPlayerAdapter,
        val stateProvider: () -> SiloCastPlaybackState,
    )

    private companion object {
        const val TAG = "TvSiloCastReceiver"
        const val STATE_INTERVAL_MS = 1_000L
    }
}
