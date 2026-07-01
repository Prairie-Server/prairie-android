package org.siloserver.silo.playback

import org.siloserver.silo.model.catalog.TimeRange
import org.siloserver.silo.network.PlaybackRealtimeEvent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Parsed `markers_updated` server event: the server recomputed intro/credits
 * ranges for a file (e.g. detection finished mid-playback) and the player should
 * adopt the fresh values so skip-intro and the credits-based auto-advance use
 * them. Only [intro] and [credits] are surfaced — recap/preview have no client
 * feature yet. A `null` range means "no such marker" (the server clears it).
 */
data class PlaybackMarkersUpdate(
    val intro: TimeRange?,
    val credits: TimeRange?,
)

/**
 * Pure decode of a `markers_updated` payload. Mirrors the server's
 * MarkersUpdatedPayload: `{ intro|credits: { start, end } | null, ... }`.
 * A range is dropped (treated as absent) unless both finite `start`/`end` are
 * present, so a malformed marker can never produce a bogus skip target.
 */
fun decodeMarkersUpdate(payload: JsonObject): PlaybackMarkersUpdate {
    fun range(key: String): TimeRange? {
        val obj = payload[key] as? JsonObject ?: return null
        fun num(k: String): Double? =
            (obj[k] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it.isFinite() }
        val start = num("start") ?: return null
        val end = num("end") ?: return null
        return TimeRange(start = start, end = end)
    }
    return PlaybackMarkersUpdate(intro = range("intro"), credits = range("credits"))
}

/**
 * Overload that takes the event directly so callers (the per-app controllers)
 * never have to reference [JsonObject] — keeps the kotlinx-serialization-json
 * dependency contained to the shared module.
 */
fun decodeMarkersUpdate(event: PlaybackRealtimeEvent.ServerEvent): PlaybackMarkersUpdate =
    decodeMarkersUpdate(event.payload)
