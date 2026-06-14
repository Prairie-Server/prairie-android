package com.continuum.app.common.player.backend

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Media3VideoPlaybackBackendSourceTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/common/player/backend/Media3VideoPlaybackBackend.kt",
    )

    @Test
    fun media3BackendDelegatesToExistingSharedHelpers() {
        val text = source.readText()

        assertTrue(text.contains("class Media3VideoPlaybackBackend"))
        assertTrue(text.contains("VideoPlaybackBackend"))
        assertTrue(text.contains("override val kind: VideoPlaybackBackendKind = VideoPlaybackBackendKind.Media3"))
        assertTrue(text.contains("VideoBackendCapabilities.media3()"))
        assertTrue(text.contains("mountVideoMedia("))
        assertTrue(text.contains("refreshMountedVideoMedia("))
        assertTrue(text.contains("trackSelectionCoordinator.selectSubtitle("))
        assertTrue(text.contains("trackSelectionCoordinator.selectMountedSubtitle("))
        assertTrue(text.contains("trackSelectionCoordinator.selectAudioTrack("))
        assertTrue(text.contains("playerFactory.applyTrackSelectionPresets("))
        assertFalse(text.contains("createPlayer("), "surface backend must wrap an already-bound Player")
        assertFalse(text.contains("mpv", ignoreCase = true))
    }
}
