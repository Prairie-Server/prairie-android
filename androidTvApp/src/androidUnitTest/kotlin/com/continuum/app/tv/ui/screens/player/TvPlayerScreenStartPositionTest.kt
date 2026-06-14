package com.continuum.app.tv.ui.screens.player

import kotlin.test.Test
import kotlin.test.assertTrue

class TvPlayerScreenStartPositionTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt",
    ).readText()

    @Test
    fun tvPlayerDelegatesInitialMountToVideoBackend() {
        assertTrue(
            source.contains("VideoPlayerMediaSpec("),
            "TV player must build the shared video media spec",
        )
        assertTrue(
            source.contains("VideoPlaybackBackendFactory"),
            "TV player must inject the shared backend factory",
        )
        assertTrue(
            source.contains("backend.mount(mediaSpec"),
            "TV player must mount through the backend",
        )
        assertTrue(
            !source.contains("controller.setMediaItem(mediaItem)"),
            "TV player must not call the no-position Media3 mount overload",
        )
        assertTrue(
            !source.contains("controller.seekTo(startMs)"),
            "TV player must not use post-mount seekTo for initial resume",
        )
        assertTrue(
            !source.contains("mountVideoMedia("),
            "TV player must not call the raw Media3 mounter directly",
        )
    }

    @Test
    fun tvPlayerDelegatesSubtitleRefreshToVideoBackend() {
        assertTrue(
            source.contains("backend.refresh(mediaSpec"),
            "TV subtitle refresh must use the backend refresh path",
        )
        assertTrue(
            !source.contains("refreshMountedVideoMedia("),
            "TV subtitle refresh must not call the raw Media3 refresh helper directly",
        )
    }

    @Test
    fun tvPlayerRoutesTrackSelectionThroughBackend() {
        assertTrue(
            source.contains("videoBackend?.selectSubtitle("),
            "TV subtitle selection must go through the mounted backend",
        )
        assertTrue(
            source.contains("videoBackend?.selectAudioTrack("),
            "TV audio selection must go through the mounted backend",
        )
        assertTrue(
            !source.contains("trackSelectionCoordinator.selectSubtitle("),
            "TV player must not call the subtitle coordinator directly",
        )
        assertTrue(
            !source.contains("trackSelectionCoordinator.selectAudioTrack("),
            "TV player must not call the audio coordinator directly",
        )
    }
}
