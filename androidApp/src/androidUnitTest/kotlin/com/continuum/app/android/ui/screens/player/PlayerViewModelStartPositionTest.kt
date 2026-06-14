package com.continuum.app.android.ui.screens.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerViewModelStartPositionTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerViewModel.kt",
    ).readText()

    @Test
    fun offlinePlaybackUsesSharedStartPositionResolver() {
        val offlineBranch = source
            .substringAfter("private suspend fun tryLocalPlayback")
            .substringBefore("override fun onCleared")

        assertTrue(offlineBranch.contains("resolvePlaybackStartPosition("))
        assertFalse(offlineBranch.contains("?: watchDetail?.userData?.positionSeconds"))
    }

    @Test
    fun userSeeksUseDedicatedSeekRequestChannel() {
        val onSeekBody = source
            .substringAfter("fun onSeek(position: Double)")
            .substringBefore("/**\n     * Immediate, deadband-free seek")

        assertTrue(
            source.contains("private val _seekRequests"),
            "mobile player must have a dedicated channel for user seek commands",
        )
        assertTrue(
            source.contains("val seekRequests"),
            "mobile player must expose seek requests for PlayerScreen to collect",
        )
        assertTrue(
            onSeekBody.contains("_seekRequests.tryEmit(position)"),
            "onSeek must emit an explicit player command instead of relying on mirrored position state",
        )
    }

    @Test
    fun playerProgressIgnoresUnknownMediaSessionPositions() {
        val onPositionChangedBody = source
            .substringAfter("fun onPositionChanged(positionMs: Long, durationMs: Long)")
            .substringBefore("/**\n     * Called when the player's actual playing state changes.")

        assertTrue(
            onPositionChangedBody.contains("if (positionMs < 0) return"),
            "MediaSession can report C.TIME_UNSET/-1; that must not overwrite progress or trigger resume reports",
        )
    }
}
