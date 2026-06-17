package com.continuum.app.network

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
