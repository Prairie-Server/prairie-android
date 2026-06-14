package com.continuum.app.common.player.backend

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoPlaybackBackendFactorySourceTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendFactory.kt",
    )

    @Test
    fun factoryWrapsBoundMedia3PlayerOnly() {
        val text = source.readText()

        assertTrue(text.contains("class VideoPlaybackBackendFactory"))
        assertTrue(text.contains("fun create("))
        assertTrue(text.contains("player: Player"))
        assertTrue(text.contains("request: VideoPlaybackBackendRequest = VideoPlaybackBackendRequest()"))
        assertTrue(text.contains("Media3VideoPlaybackBackend("))
        assertTrue(text.contains("VideoTrackSelectionCoordinator(subtitleManager)"))
        assertFalse(text.contains("createPlayer("), "factory must wrap the bound MediaController, not create a service player")
        assertFalse(text.contains("mpv", ignoreCase = true), "MPV is out of scope for this slice")
        assertFalse(text.contains("libass", ignoreCase = true), "libass is out of scope for this slice")
    }
}
