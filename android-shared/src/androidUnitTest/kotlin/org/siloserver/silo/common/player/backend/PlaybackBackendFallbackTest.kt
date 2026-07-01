package org.siloserver.silo.common.player.backend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackBackendFallbackTest {
    @Test
    fun mpvStartFailureFallsBackToMedia3WithReason() {
        val next = PlaybackBackendFallback.onStartFailure(
            attempted = VideoPlaybackBackendKind.Mpv,
            error = "mpv: vo init failed",
        )
        assertEquals(VideoPlaybackBackendKind.Media3, next?.fallbackTo)
        assertEquals("mpv: vo init failed", next?.reason)
    }

    @Test
    fun media3StartFailureHasNoFurtherFallback() {
        val next = PlaybackBackendFallback.onStartFailure(
            attempted = VideoPlaybackBackendKind.Media3,
            error = "decoder init failed",
        )
        assertNull(next)
    }
}
