package com.continuum.app.android.ui.screens.player

import kotlin.test.Test
import kotlin.test.assertTrue

class PlayerScreenStartPositionTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreen.kt",
    ).readText()

    @Test
    fun playerScreenDelegatesInitialMountToSharedHelper() {
        assertTrue(
            source.contains("VideoPlayerMediaSpec("),
            "mobile player must build the shared video media spec",
        )
        assertTrue(
            source.contains("mountVideoMedia("),
            "mobile player must use the shared mount helper",
        )
        assertTrue(
            !source.contains("controller.setMediaItem(mediaItem, startMs)"),
            "mobile player must not duplicate initial Media3 mount ordering",
        )
    }

    @Test
    fun playerScreenDelegatesSubtitleRefreshToSharedHelper() {
        assertTrue(
            source.contains("refreshMountedVideoMedia("),
            "mobile subtitle refresh must use the shared refresh helper",
        )
    }
}
