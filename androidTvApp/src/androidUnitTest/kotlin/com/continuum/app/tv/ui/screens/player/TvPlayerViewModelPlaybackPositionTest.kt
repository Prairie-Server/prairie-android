package com.continuum.app.tv.ui.screens.player

import kotlin.test.Test
import kotlin.test.assertTrue

class TvPlayerViewModelPlaybackPositionTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerViewModel.kt",
    ).readText()

    @Test
    fun playerProgressIgnoresUnknownMediaSessionPositions() {
        val onPositionChangedBody = source
            .substringAfter("fun onPositionChanged(positionMs: Long, durationMs: Long)")
            .substringBefore("fun onPlayingChanged(isPlaying: Boolean)")

        assertTrue(
            onPositionChangedBody.contains("if (positionMs < 0) return"),
            "MediaSession can report C.TIME_UNSET/-1; TV must not persist that as progress",
        )
    }
}
