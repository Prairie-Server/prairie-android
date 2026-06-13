package com.continuum.app.tv.ui.screens.player

import kotlin.test.Test
import kotlin.test.assertTrue

class TvPlayerScreenStartPositionTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt",
    ).readText()

    @Test
    fun tvPlayerDelegatesInitialMountToSharedHelper() {
        assertTrue(
            source.contains("VideoPlayerMediaSpec("),
            "TV player must build the shared video media spec",
        )
        assertTrue(
            source.contains("mountVideoMedia("),
            "TV player must use the shared mount helper",
        )
        assertTrue(
            !source.contains("controller.setMediaItem(mediaItem)"),
            "TV player must not call the no-position Media3 mount overload",
        )
        assertTrue(
            !source.contains("controller.seekTo(startMs)"),
            "TV player must not use post-mount seekTo for initial resume",
        )
    }

    @Test
    fun tvPlayerDelegatesSubtitleRefreshToSharedHelper() {
        assertTrue(
            source.contains("refreshMountedVideoMedia("),
            "TV subtitle refresh must use the shared refresh helper",
        )
    }
}
