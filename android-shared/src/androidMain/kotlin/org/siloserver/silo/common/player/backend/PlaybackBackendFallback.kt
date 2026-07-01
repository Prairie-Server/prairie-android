package org.siloserver.silo.common.player.backend

/** A single fallback step: which engine to retry on, and why. */
data class PlaybackBackendFallbackStep(
    val fallbackTo: VideoPlaybackBackendKind,
    val reason: String,
)

/**
 * Fallback contract: MPV start failure must retry on Media3 and record the
 * reason; Media3 is the terminal engine (no further fallback). Pure so the
 * contract is unit-tested; the start-failure wiring (Track A Task 3 Step 5,
 * at the SiloPlaybackService engine-owner boundary and the media mount)
 * calls this.
 */
object PlaybackBackendFallback {
    fun onStartFailure(
        attempted: VideoPlaybackBackendKind,
        error: String,
    ): PlaybackBackendFallbackStep? = when (attempted) {
        VideoPlaybackBackendKind.Mpv ->
            PlaybackBackendFallbackStep(VideoPlaybackBackendKind.Media3, error)
        VideoPlaybackBackendKind.Media3 -> null
    }
}
