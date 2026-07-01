package org.siloserver.silo.common.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SiloPlaybackServiceMpvSourceTest {
    private val factorySource = java.io.File(
        "src/androidMain/kotlin/org/siloserver/silo/common/player/SiloPlayerFactory.kt",
    ).readText()
    private val serviceSource = java.io.File(
        "src/androidMain/kotlin/org/siloserver/silo/common/player/SiloPlaybackService.kt",
    ).readText()

    @Test
    fun serviceDefaultsToMedia3AndReachesMpvOnlyViaSetEngine() {
        assertTrue(factorySource.contains("fun createMpvPlayer("))
        assertTrue(factorySource.contains("MpvPlayer.Builder(context)"))
        assertTrue(factorySource.contains("setHttpHeaderFieldsProvider"))
        assertTrue(factorySource.contains("Authorization\" to \"Bearer $"))
        assertTrue(factorySource.contains("\"X-Profile-Id\""))
        assertTrue(factorySource.contains("\"X-Profile-Token\""))
        // The session is created with the Media3/ExoPlayer engine by default.
        assertTrue(serviceSource.contains("private fun createPlaybackPlayer(): Player"))
        assertTrue(serviceSource.contains("playerFactory.createPlayer()"))
        assertTrue(serviceSource.contains("if (player is ExoPlayer)"))
        assertTrue(serviceSource.contains("MediaSession.Builder(this, player)"))
        // MPV is now reachable, but ONLY behind the explicit SET_ENGINE command —
        // never auto-selected by SDK level (that would silently switch everything).
        assertTrue(
            serviceSource.contains("playerFactory.createMpvPlayer()"),
            "MPV must be reachable via the engine-swap path",
        )
        assertTrue(
            serviceSource.contains("PlaybackEngineCommand.SET_ENGINE"),
            "MPV selection must be gated behind the explicit SET_ENGINE command, not defaulted",
        )
        assertFalse(
            serviceSource.contains("Build.VERSION.SDK_INT >= Build.VERSION_CODES.O"),
            "SDK level must not silently switch all modern video playback to MPV",
        )
    }
}
