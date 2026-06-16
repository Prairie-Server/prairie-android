package com.continuum.app.common.player.backend

import kotlinx.serialization.json.Json

/**
 * Contract for the custom MediaSession command that tells the engine owner
 * ([com.continuum.app.common.player.ContinuumPlaybackService]) which playback
 * engine to build/rebind for the media about to play. The request is carried as
 * a JSON string under [ARG_REQUEST_JSON] in the command args — the UI mount path
 * sends it once playMethod/container/subtitle info is known (Track A Task 0).
 *
 * encode/decode are pure (no Android types) so the round-trip is unit-tested;
 * the Bundle wrapping happens at the call site.
 */
object PlaybackEngineCommand {
    const val SET_ENGINE = "silo.SET_ENGINE"
    const val ARG_REQUEST_JSON = "request_json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        // Forward-compat: an unknown enum value on the wire coerces to the
        // field's default (e.g. preference -> Auto) instead of throwing.
        coerceInputValues = true
    }

    fun encode(request: VideoPlaybackBackendRequest): String = json.encodeToString(request)

    fun decode(payload: String): VideoPlaybackBackendRequest = json.decodeFromString(payload)
}
