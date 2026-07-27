package org.siloserver.silo.network

import org.siloserver.silo.model.watchtogether.RoomSnapshot
import org.siloserver.silo.model.watchtogether.Suggestion
import org.siloserver.silo.model.watchtogether.TransportCommand
import org.siloserver.silo.model.watchtogether.WsAttachSession
import org.siloserver.silo.model.watchtogether.WsBuffering
import org.siloserver.silo.model.watchtogether.WsPing
import org.siloserver.silo.model.watchtogether.WsReady
import org.siloserver.silo.model.watchtogether.WsStateReport
import org.siloserver.silo.model.watchtogether.WsTransportRequest
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.http.encodeURLParameter
import io.ktor.http.encodeURLPathPart
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Per-room websocket. One [connect] = one connection to
 * `/api/v1/watch-together/rooms/{id}/ws`. The same-origin Silo auth plugin
 * supplies the access bearer and profile headers. The server still requires
 * `room_token`, `profile_id`, and (when present) `profile_token` in the query;
 * these residual URL credentials remain exposed to infrastructure that logs
 * request targets until the server protocol can be changed.
 *
 * [RoomRealtimeEvent.Closed] is reserved for an explicit decoded
 * `room_closed` frame. Physical EOF and socket failures surface as
 * [RoomRealtimeEvent.TransportTerminated], while owner cancellation is silent.
 */
interface WatchTogetherRealtimeClient {
    /** Open one physical room socket and publish [RoomRealtimeEvent.Opened] once writable. */
    fun connect(roomId: String, roomToken: String): Flow<RoomRealtimeEvent>

    /** Client→server sends report whether a frame reached the current writable socket. */
    suspend fun attachSession(sessionId: String): Boolean
    suspend fun transportRequest(action: String, positionSeconds: Double?, isPaused: Boolean): Boolean
    suspend fun stateReport(sessionId: String, positionSeconds: Double, isPaused: Boolean): Boolean
    suspend fun ready(sessionId: String, positionSeconds: Double, isPaused: Boolean): Boolean
    suspend fun buffering(sessionId: String, positionSeconds: Double, isPaused: Boolean): Boolean
    suspend fun ping(clientSentAt: String): Boolean
}

internal interface WatchTogetherSocketConnection {
    suspend fun receiveText(): String?
    suspend fun sendText(text: String)
    suspend fun close()
}

internal data class WatchTogetherSocketRequest(
    val url: String,
    val authScope: AuthScopeSnapshot,
)

internal fun interface WatchTogetherSocketConnector {
    suspend fun open(request: WatchTogetherSocketRequest): WatchTogetherSocketConnection
}

private class KtorWatchTogetherSocketConnector(
    private val client: HttpClient,
) : WatchTogetherSocketConnector {
    override suspend fun open(request: WatchTogetherSocketRequest): WatchTogetherSocketConnection =
        KtorWatchTogetherSocketConnection(
            client.webSocketSession {
                url(request.url)
                authScope(request.authScope)
                requireSiloAuth()
            },
        )
}

private class KtorWatchTogetherSocketConnection(
    private val session: DefaultClientWebSocketSession,
) : WatchTogetherSocketConnection {
    override suspend fun receiveText(): String? {
        while (true) {
            val result = session.incoming.receiveCatching()
            result.exceptionOrNull()?.let { throw it }
            val frame = result.getOrNull() ?: return null
            if (frame is Frame.Text) return frame.readText()
        }
    }

    override suspend fun sendText(text: String) {
        session.send(Frame.Text(text))
    }

    override suspend fun close() {
        session.close()
    }
}

class DefaultWatchTogetherRealtimeClient private constructor(
    private val tokenManager: TokenManager,
    private val json: Json,
    private val socketConnector: WatchTogetherSocketConnector,
) : WatchTogetherRealtimeClient {

    constructor(
        client: HttpClient,
        tokenManager: TokenManager,
        json: Json = SiloJson,
    ) : this(tokenManager, json, KtorWatchTogetherSocketConnector(client))

    internal constructor(
        tokenManager: TokenManager,
        socketConnector: WatchTogetherSocketConnector,
        json: Json = SiloJson,
    ) : this(tokenManager, json, socketConnector)

    private val sessionMutex = Mutex()
    private var session: WatchTogetherSocketConnection? = null

    override fun connect(roomId: String, roomToken: String): Flow<RoomRealtimeEvent> = callbackFlow {
        val scope = tokenManager.snapshotCurrentScope()
        val profileId = scope?.profileId
        val scopedAccessToken = scope?.let { tokenManager.getAccessTokenForScope(it) }
        if (scope == null || scopedAccessToken.isNullOrBlank() || profileId.isNullOrBlank()) {
            trySend(
                RoomRealtimeEvent.TransportTerminated(
                    IllegalStateException("missing_auth_scope"),
                ),
            )
            close()
            return@callbackFlow
        }

        val url = buildString {
            append("/api/v1/watch-together/rooms/")
            append(roomId.encodeURLPathPart())
            append("/ws?room_token=").append(roomToken.encodeURLParameter())
            append("&profile_id=").append(profileId.encodeURLParameter())
            if (!scope.profileToken.isNullOrBlank()) {
                append("&profile_token=").append(scope.profileToken.encodeURLParameter())
            }
        }

        var connection: WatchTogetherSocketConnection? = null
        try {
            connection = socketConnector.open(
                WatchTogetherSocketRequest(
                    url = url,
                    authScope = scope,
                ),
            )
            sessionMutex.withLock {
                session = connection
            }
            trySend(RoomRealtimeEvent.Opened)
            while (true) {
                val raw = connection.receiveText() ?: break
                decodeRoomFrame(json, raw)?.let { trySend(it) }
            }
            trySend(RoomRealtimeEvent.TransportTerminated())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            trySend(RoomRealtimeEvent.TransportTerminated(failure))
        } finally {
            connection?.let { completed ->
                sessionMutex.withLock {
                    if (session === completed) session = null
                }
                runCatching { completed.close() }
            }
            close()
        }

        awaitClose { }
    }

    private suspend fun sendText(text: String): Boolean {
        val writable = sessionMutex.withLock { session } ?: return false
        return try {
            writable.sendText(text)
            true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            false
        }
    }

    override suspend fun attachSession(sessionId: String) =
        sendText(json.encodeToString(WsAttachSession.serializer(), WsAttachSession(sessionId = sessionId)))

    override suspend fun transportRequest(action: String, positionSeconds: Double?, isPaused: Boolean) =
        sendText(
            json.encodeToString(
                WsTransportRequest.serializer(),
                WsTransportRequest(action = action, positionSeconds = positionSeconds, isPaused = isPaused),
            ),
        )

    override suspend fun stateReport(sessionId: String, positionSeconds: Double, isPaused: Boolean) =
        sendText(
            json.encodeToString(
                WsStateReport.serializer(),
                WsStateReport(sessionId = sessionId, positionSeconds = positionSeconds, isPaused = isPaused),
            ),
        )

    override suspend fun ready(sessionId: String, positionSeconds: Double, isPaused: Boolean) =
        sendText(
            json.encodeToString(
                WsReady.serializer(),
                WsReady(sessionId = sessionId, positionSeconds = positionSeconds, isPaused = isPaused),
            ),
        )

    override suspend fun buffering(sessionId: String, positionSeconds: Double, isPaused: Boolean) =
        sendText(
            json.encodeToString(
                WsBuffering.serializer(),
                WsBuffering(sessionId = sessionId, positionSeconds = positionSeconds, isPaused = isPaused),
            ),
        )

    override suspend fun ping(clientSentAt: String) =
        sendText(json.encodeToString(WsPing.serializer(), WsPing(clientSentAt = clientSentAt)))
}

/**
 * Pure decode of one room WS server frame into a [RoomRealtimeEvent], or null
 * when the frame is not one we surface (unknown `type`, malformed payload, or
 * malformed JSON). Never throws — this is the load-bearing, fully-tested
 * logic; socket I/O above is kept thin and untested.
 *
 *  - `snapshot {room}`            → [RoomRealtimeEvent.SnapshotEvent]
 *  - `transport_command {command}`→ [RoomRealtimeEvent.TransportCommandEvent]
 *  - `suggestions_update {suggestions}` → [RoomRealtimeEvent.SuggestionsEvent]
 *  - `room_closed {reason}`       → [RoomRealtimeEvent.Closed]
 *  - `pong {…}`                   → [RoomRealtimeEvent.Pong]
 *  - `error {code,message}`       → [RoomRealtimeEvent.Error]
 *  - anything else                → null
 */
fun decodeRoomFrame(json: Json, raw: String): RoomRealtimeEvent? {
    val obj: JsonObject = try {
        val element = json.parseToJsonElement(raw)
        element as? JsonObject ?: return null
    } catch (_: Exception) {
        return null
    }

    val type = (obj["type"] as? JsonPrimitive)?.content ?: return null

    return when (type) {
        WatchTogetherRealtime.TypeSnapshot -> {
            val room = obj["room"] as? JsonObject ?: return null
            val snapshot = try {
                json.decodeFromJsonElement(RoomSnapshot.serializer(), room)
            } catch (_: Exception) {
                return null
            }
            RoomRealtimeEvent.SnapshotEvent(snapshot)
        }
        WatchTogetherRealtime.TypeTransportCommand -> {
            val command = obj["command"] as? JsonObject ?: return null
            val parsed = try {
                json.decodeFromJsonElement(TransportCommand.serializer(), command)
            } catch (_: Exception) {
                return null
            }
            RoomRealtimeEvent.TransportCommandEvent(parsed)
        }
        WatchTogetherRealtime.TypeSuggestionsUpdate -> {
            val array = obj["suggestions"] as? JsonArray ?: return null
            val list = try {
                json.decodeFromJsonElement(ListSerializer(Suggestion.serializer()), array)
            } catch (_: Exception) {
                return null
            }
            RoomRealtimeEvent.SuggestionsEvent(list)
        }
        WatchTogetherRealtime.TypeRoomClosed -> {
            val reason = (obj["reason"] as? JsonPrimitive)?.content
            RoomRealtimeEvent.Closed(reason)
        }
        WatchTogetherRealtime.TypePong -> {
            fun str(key: String) = (obj[key] as? JsonPrimitive)?.content ?: ""
            RoomRealtimeEvent.Pong(
                clientSentAt = str("client_sent_at"),
                serverReceivedAt = str("server_received_at"),
                serverSentAt = str("server_sent_at"),
            )
        }
        WatchTogetherRealtime.TypeError -> {
            fun str(key: String) = (obj[key] as? JsonPrimitive)?.content ?: ""
            RoomRealtimeEvent.Error(code = str("code"), message = str("message"))
        }
        else -> null
    }
}
