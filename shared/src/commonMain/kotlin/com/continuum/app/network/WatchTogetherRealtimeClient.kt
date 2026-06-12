package com.continuum.app.network

import com.continuum.app.model.watchtogether.RoomSnapshot
import com.continuum.app.model.watchtogether.Suggestion
import com.continuum.app.model.watchtogether.TransportCommand
import com.continuum.app.model.watchtogether.WsAttachSession
import com.continuum.app.model.watchtogether.WsBuffering
import com.continuum.app.model.watchtogether.WsPing
import com.continuum.app.model.watchtogether.WsReady
import com.continuum.app.model.watchtogether.WsStateReport
import com.continuum.app.model.watchtogether.WsTransportRequest
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.encodeURLParameter
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Per-room websocket. One [connect] = one connection to
 * `/api/v1/watch-together/rooms/{id}/ws`, authenticated by query string only:
 * `?token=<authJWT>&room_token=<roomJWT>&profile_id=<id>&profile_token=<token>`
 * (a separate socket from `/events/ws`). Every server frame is mapped through
 * the pure [decodeRoomFrame] into the returned [Flow]; the flow completes
 * (emitting [RoomRealtimeEvent.Closed]) when the socket ends — reconnect with
 * capped backoff is the repository's job.
 *
 * [send*] methods write client frames on an open session; the repository holds
 * the session and the ping loop. Auth values are read from [TokenManager] at
 * connect time. Behind an interface so the repository's tests use a fake flow
 * — the only logic worth unit-testing is the pure [decodeRoomFrame].
 */
interface WatchTogetherRealtimeClient {
    /** Open the room socket. The returned flow ends with [RoomRealtimeEvent.Closed]. */
    fun connect(roomId: String, roomToken: String): Flow<RoomRealtimeEvent>

    /** Client→server sends. No-op when no session is open (repository gates on connection). */
    suspend fun attachSession(sessionId: String)
    suspend fun transportRequest(action: String, positionSeconds: Double?, isPaused: Boolean)
    suspend fun stateReport(sessionId: String, positionSeconds: Double, isPaused: Boolean)
    suspend fun ready(sessionId: String, positionSeconds: Double, isPaused: Boolean)
    suspend fun buffering(sessionId: String, positionSeconds: Double, isPaused: Boolean)
    suspend fun ping(clientSentAt: String)
}

class DefaultWatchTogetherRealtimeClient(
    private val client: HttpClient,
    private val tokenManager: TokenManager,
    private val json: Json = ContinuumJson,
) : WatchTogetherRealtimeClient {

    // The live session for the current connect(); send* methods write to it.
    // Single-connection-at-a-time (the repository owns one room).
    private var session: DefaultClientWebSocketSession? = null

    override fun connect(roomId: String, roomToken: String): Flow<RoomRealtimeEvent> = callbackFlow {
        val token = tokenManager.getAccessToken()
        val profileId = tokenManager.getProfileId()
        val profileToken = tokenManager.getProfileToken()
        if (token.isNullOrBlank() || profileId.isNullOrBlank()) {
            trySend(RoomRealtimeEvent.Closed("missing_auth"))
            close()
            return@callbackFlow
        }

        val url = buildString {
            append("/api/v1/watch-together/rooms/")
            append(roomId.encodeURLParameter())
            append("/ws?token=").append(token.encodeURLParameter())
            append("&room_token=").append(roomToken.encodeURLParameter())
            append("&profile_id=").append(profileId.encodeURLParameter())
            if (!profileToken.isNullOrBlank()) {
                append("&profile_token=").append(profileToken.encodeURLParameter())
            }
        }

        try {
            client.webSocket(urlString = url) {
                session = this
                try {
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        decodeRoomFrame(json, frame.readText())?.let { trySend(it) }
                    }
                } finally {
                    session = null
                }
            }
            trySend(RoomRealtimeEvent.Closed())
        } catch (e: Throwable) {
            session = null
            trySend(RoomRealtimeEvent.Closed(e.message))
        } finally {
            close()
        }

        awaitClose { /* socket closes when the collector is cancelled */ }
    }

    private suspend fun sendText(text: String) {
        session?.send(Frame.Text(text))
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
