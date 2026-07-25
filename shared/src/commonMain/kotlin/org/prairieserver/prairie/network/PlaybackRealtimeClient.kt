package org.prairieserver.prairie.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.encodeURLParameter
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Pure decode of one control-socket server frame into a [PlaybackRealtimeEvent],
 * or null when the frame is not one we handle (unknown type, missing fields,
 * malformed JSON). Never throws. This is the load-bearing tested logic; the
 * socket I/O in [DefaultPlaybackRealtimeClient] is kept thin.
 *
 * Note: [PlaybackRealtimeEvent.Opened]/[Closed] are produced by the socket
 * lifecycle, not by this decoder.
 */
fun decodePlaybackFrame(json: Json, raw: String): PlaybackRealtimeEvent? {
    val obj: JsonObject = try {
        json.parseToJsonElement(raw).jsonObject
    } catch (_: Exception) {
        return null
    }
    // Real JSON strings only — a numeric/null/bool primitive is not a valid
    // string field, so the frame is treated as malformed (returns null).
    fun str(key: String) = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    val type = str("type") ?: return null
    val sessionId = str("session_id") ?: return null
    val payload = (obj["payload"] as? JsonObject) ?: JsonObject(emptyMap())
    return when (type) {
        "command" -> {
            val commandId = str("command_id") ?: return null
            val name = str("name") ?: return null
            PlaybackRealtimeEvent.Command(commandId, sessionId, name, payload)
        }
        "event" -> {
            val name = str("name") ?: return null
            PlaybackRealtimeEvent.ServerEvent(sessionId, name, payload)
        }
        else -> null
    }
}

/**
 * Per-session control socket. One [connect] = one connection to
 * `/api/v1/playback/sessions/{session_id}/control/ws`, authenticated by query
 * string (token + profile, matching [DefaultWatchTogetherRealtimeClient]). The
 * returned flow emits [PlaybackRealtimeEvent.Opened] once the socket is live,
 * then decoded frames, and ends with [PlaybackRealtimeEvent.Closed]; reconnect
 * with backoff is the controller's job. [sendHello]/[sendAck]/[sendResult]
 * write on the open session.
 */
interface PlaybackRealtimeClient {
    fun connect(sessionId: String): Flow<PlaybackRealtimeEvent>
    suspend fun sendHello(sessionId: String)
    suspend fun sendAck(sessionId: String, commandId: String)
    suspend fun sendResult(sessionId: String, commandId: String, status: String, error: String? = null)
}

class DefaultPlaybackRealtimeClient(
    private val client: HttpClient,
    private val tokenManager: TokenManager,
    private val json: Json = PrairieJson,
) : PlaybackRealtimeClient {

    private var session: DefaultClientWebSocketSession? = null

    override fun connect(sessionId: String): Flow<PlaybackRealtimeEvent> = callbackFlow {
        val token = tokenManager.getAccessToken()
        // The control socket is auth-only (the server mounts it outside
        // RequireProfile — it authorizes by user + session ownership), so a
        // missing profile must NOT block the connection. Only the access token
        // is required; profile params are sent as optional extras.
        if (token.isNullOrBlank()) {
            trySend(PlaybackRealtimeEvent.Closed("missing_auth"))
            close()
            return@callbackFlow
        }
        val profileId = tokenManager.getProfileId()
        val profileToken = tokenManager.getProfileToken()
        val url = buildString {
            append("/api/v1/playback/sessions/")
            append(sessionId.encodeURLParameter())
            append("/control/ws?token=").append(token.encodeURLParameter())
            if (!profileId.isNullOrBlank()) {
                append("&profile_id=").append(profileId.encodeURLParameter())
            }
            if (!profileToken.isNullOrBlank()) {
                append("&profile_token=").append(profileToken.encodeURLParameter())
            }
        }
        try {
            client.webSocket(urlString = url) {
                session = this
                // R2: signal open AFTER the session is assigned, so the
                // controller's hello can't race ahead of a live socket.
                trySend(PlaybackRealtimeEvent.Opened)
                try {
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        decodePlaybackFrame(json, frame.readText())?.let { trySend(it) }
                    }
                } finally {
                    session = null
                }
            }
            trySend(PlaybackRealtimeEvent.Closed())
        } catch (e: Throwable) {
            session = null
            trySend(PlaybackRealtimeEvent.Closed(e.message))
        } finally {
            close()
        }
        awaitClose { }
    }

    private suspend fun sendText(text: String) { session?.send(Frame.Text(text)) }

    override suspend fun sendHello(sessionId: String) = sendText(
        json.encodeToString(
            PlaybackHelloEnvelope.serializer(),
            PlaybackHelloEnvelope(
                sessionId = sessionId,
                client = HelloClient(),
                capabilities = HelloCapabilities(PlaybackCommandNames.Supported),
            ),
        ),
    )

    override suspend fun sendAck(sessionId: String, commandId: String) = sendText(
        json.encodeToString(
            PlaybackAckEnvelope.serializer(),
            PlaybackAckEnvelope(commandId = commandId, sessionId = sessionId),
        ),
    )

    override suspend fun sendResult(sessionId: String, commandId: String, status: String, error: String?) = sendText(
        json.encodeToString(
            PlaybackResultEnvelope.serializer(),
            PlaybackResultEnvelope(commandId = commandId, sessionId = sessionId, status = status, error = error),
        ),
    )
}
