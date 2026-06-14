package com.continuum.app.common.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContinuumPlaybackServiceMpvSourceTest {
    private val factorySource = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlayerFactory.kt",
    ).readText()
    private val serviceSource = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlaybackService.kt",
    ).readText()

    @Test
    fun serviceOwnsMedia3PlayerByDefault() {
        assertTrue(factorySource.contains("fun createMpvPlayer("))
        assertTrue(factorySource.contains("MpvPlayer.Builder(context)"))
        assertTrue(factorySource.contains("setHttpHeaderFieldsProvider"))
        assertTrue(factorySource.contains("Authorization\" to \"Bearer $"))
        assertTrue(factorySource.contains("\"X-Profile-Id\""))
        assertTrue(factorySource.contains("\"X-Profile-Token\""))
        assertTrue(serviceSource.contains("private fun createPlaybackPlayer(): Player"))
        assertTrue(serviceSource.contains("playerFactory.createPlayer()"))
        assertFalse(
            serviceSource.contains("playerFactory.createMpvPlayer()"),
            "the session-owned video player must default to Media3/ExoPlayer; MPV cannot be selected from a MediaController wrapper",
        )
        assertFalse(
            serviceSource.contains("Build.VERSION.SDK_INT >= Build.VERSION_CODES.O"),
            "SDK level must not silently switch all modern video playback to MPV",
        )
        assertTrue(serviceSource.contains("if (player is ExoPlayer)"))
        assertTrue(serviceSource.contains("MediaSession.Builder(this, player).build()"))
    }
}
