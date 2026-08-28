package org.prairieserver.prairie.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.encodeURLParameter
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
 * string (token + profile; unlike [DefaultWatchTogetherRealtimeClient], which
 * keeps the access bearer in the same-origin Authorization header). The
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

    /**
     * The socket paired with the playback session it belongs to.
     *
     * Holding the id alongside the socket is what makes a send answerable to a
     * caller. With a bare socket field, a reconnect overwrites it while the
     * outgoing connection's `finally` clears it unconditionally — so a late
     * close from session A disables session B's remote control, and an ack for
     * A goes out over B's socket and vanishes. Both are indistinguishable from
     * a flaky network at the call site.
     */
    private data class RealtimeConnection(
        val sessionId: String,
        val socket: DefaultClientWebSocketSession,
    )

    /**
     * Guards [connection]. A volatile read-then-write is not a compare-and-set:
     * a newer connection can install itself between the two, and the older one
     * then clears it. Every access is a suspend call site, so a mutex is enough
     * and needs no atomics dependency.
     */
    private val connectionLock = Mutex()
    private var connection: RealtimeConnection? = null

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
        val profileIdentity = tokenManager.getProfileIdentity()
        val profileId = profileIdentity.profileId
        val profileToken = profileIdentity.profileToken
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
        var owned: RealtimeConnection? = null
        // Clear on identity under the lock, never a blind null: this connection
        // may already have been superseded by a newer one, and clearing that
        // would leave the live socket unreachable to every send.
        // NonCancellable: every caller below runs on a teardown path, and two of
        // the three run while this coroutine is already cancelled. A cancellable
        // acquisition simply throws there, leaving a dead socket installed as the
        // target of every subsequent send until some later connection happens to
        // overwrite it.
        suspend fun releaseIfStillOwned() {
            withContext(NonCancellable) {
                connectionLock.withLock {
                    if (connection === owned) connection = null
                }
            }
        }
        try {
            client.webSocket(urlString = url) {
                val current = RealtimeConnection(sessionId, this)
                owned = current
                connectionLock.withLock { connection = current }
                // R2: signal open AFTER the session is assigned, so the
                // controller's hello can't race ahead of a live socket.
                trySend(PlaybackRealtimeEvent.Opened)
                try {
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        decodePlaybackFrame(json, frame.readText())?.let { trySend(it) }
                    }
                } finally {
                    releaseIfStillOwned()
                }
            }
            trySend(PlaybackRealtimeEvent.Closed())
        } catch (cancellation: CancellationException) {
            // Not a socket failure. Reporting Closed here tells the controller to
            // reconnect the very session that is being torn down.
            releaseIfStillOwned()
            throw cancellation
        } catch (e: Throwable) {
            releaseIfStillOwned()
            trySend(PlaybackRealtimeEvent.Closed(e.message))
        } finally {
            close()
        }
        awaitClose { }
    }

    /**
     * Writes only on the connection that belongs to [sessionId]. Every envelope
     * already names its session, so a send that cannot be matched to the open
     * socket is for a connection that has moved on — dropping it is correct, and
     * strictly better than writing it down somebody else's socket.
     */
    private suspend fun sendText(sessionId: String, text: String) {
        // Resolve under the lock, then send outside it — the send is network I/O
        // and must not block a teardown trying to release the field.
        val current = connectionLock.withLock {
            connection?.takeIf { it.sessionId == sessionId }
        } ?: return
        current.socket.send(Frame.Text(text))
    }

    override suspend fun sendHello(sessionId: String) = sendText(
        sessionId,
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
        sessionId,
        json.encodeToString(
            PlaybackAckEnvelope.serializer(),
            PlaybackAckEnvelope(commandId = commandId, sessionId = sessionId),
        ),
    )

    override suspend fun sendResult(sessionId: String, commandId: String, status: String, error: String?) = sendText(
        sessionId,
        json.encodeToString(
            PlaybackResultEnvelope.serializer(),
            PlaybackResultEnvelope(commandId = commandId, sessionId = sessionId, status = status, error = error),
        ),
    )
}
