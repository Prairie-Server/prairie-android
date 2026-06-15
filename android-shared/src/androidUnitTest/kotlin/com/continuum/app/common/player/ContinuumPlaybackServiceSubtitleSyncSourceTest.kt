package com.continuum.app.common.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContinuumPlaybackServiceSubtitleSyncSourceTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlaybackService.kt",
    ).readText()

    @Test
    fun subtitleSyncChangesReparseCurrentMediaItemAtSamePosition() {
        assertTrue(
            source.contains("reparseCurrentMediaItemAtCurrentPosition(player, offsetMs)"),
            "subtitle sync changes must reprepare the current item so parsed sidecar cue timestamps are rebuilt",
        )
        assertTrue(source.contains("player.setMediaItems(mediaItems, currentIndex, positionMs)"))
        assertTrue(source.contains("player.prepare()"))
        assertTrue(source.contains("player.playWhenReady = playWhenReady"))
    }

    @Test
    fun subtitleSyncReparseIsLimitedToItemsWithTextTracks() {
        assertTrue(source.contains("val hasConfiguredSubtitles ="))
        assertTrue(source.contains("val hasTextTracks ="))
        assertTrue(source.contains("if (!hasConfiguredSubtitles && !hasTextTracks) return"))
    }

    @Test
    fun subtitleSyncDoesNotUseNoOpSeekAsItsOnlyRefreshMechanism() {
        val syncJob = source.substringAfter("subtitleSyncJob = scope.launch")
            .substringBefore("private fun createPlaybackPlayer")

        assertFalse(
            syncJob.contains("player.seekTo(player.currentPosition)"),
            "seekTo(currentPosition) does not force Media3 to reparse already-loaded sidecar cues",
        )
    }
}
