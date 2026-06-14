package com.continuum.app.tv.ui.screens.player

import kotlin.test.Test
import kotlin.test.assertFalse
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

    @Test
    fun playbackSessionLifecycleOwnsProgressReporting() {
        assertFalse(
            source.contains("private var progressJob"),
            "TV must not run a second progress reporter beside PlaybackSessionLifecycle",
        )
        assertFalse(
            source.contains("playbackSessionManager.reportProgress("),
            "TV must not report progress directly; lifecycle owns report/recovery/final flush",
        )
        assertFalse(
            source.contains("private fun startProgressReporting("),
            "TV must not keep the legacy progress-reporting loop",
        )
        assertFalse(
            source.contains("private fun recoverMissingPlaybackSession("),
            "TV must not keep duplicate 404 recovery outside PlaybackSessionLifecycle",
        )
        assertFalse(
            source.contains("private suspend fun syncProgressSnapshot("),
            "TV must not keep duplicate progress snapshot flushing outside PlaybackSessionLifecycle",
        )
    }

    @Test
    fun stopSessionForExitClearsPlayableStateBeforeNavigationCompletes() {
        val stopBody = source
            .substringAfter("suspend fun stopSessionForExit()")
            .substringBefore("fun onExit()")

        assertTrue(
            stopBody.contains("sessionId = null"),
            "TV exit must clear the active playback session id",
        )
        assertTrue(
            stopBody.contains("streamUrl = null"),
            "TV exit must clear the stale stream URL so Compose cannot remount it",
        )
        assertTrue(
            stopBody.contains("playMethod = null"),
            "TV exit must clear play method along with the stale stream URL",
        )
    }
}
