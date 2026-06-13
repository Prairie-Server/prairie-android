package com.continuum.app.common.player

import kotlin.test.Test
import kotlin.test.assertTrue

class VideoPlayerMediaMounterSourceTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/common/player/VideoPlayerMediaMounter.kt",
    ).readText()

    @Test
    fun mountUsesMedia3StartPositionOverloadBeforePrepare() {
        val setIndex = source.indexOf(".setMediaItem(mediaItem, startPositionMs")
        val prepareIndex = source.indexOf(".prepare()")
        val seekIndex = source.indexOf(".seekTo(")

        assertTrue(setIndex >= 0, "mount must call setMediaItem(mediaItem, startPositionMs)")
        assertTrue(prepareIndex > setIndex, "prepare must happen after mounted start position")
        assertTrue(seekIndex < 0, "mount must not use post-mount seekTo for initial resume")
    }

    @Test
    fun refreshPreservesCurrentPositionAndPlayingState() {
        assertTrue(
            source.contains("refreshMountedVideoMedia"),
            "subtitle refresh must use a shared helper",
        )
        assertTrue(
            source.contains("val resumePositionMs = player.currentPosition"),
            "refresh must preserve current player position",
        )
        assertTrue(
            source.contains("val wasPlaying = player.playWhenReady"),
            "refresh must preserve playWhenReady",
        )
        assertTrue(
            source.contains(".setMediaItem(mediaItem, resumePositionMs)"),
            "refresh must remount at the preserved position",
        )
    }
}
