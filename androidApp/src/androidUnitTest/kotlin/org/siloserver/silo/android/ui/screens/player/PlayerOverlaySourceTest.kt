package org.siloserver.silo.android.ui.screens.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class PlayerOverlaySourceTest {
    private val source = File("src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/player/PlayerOverlay.kt")

    @Test
    fun gestureLayerDoesNotCoverVisibleControls() {
        val text = source.readText()
        val gestureIndex = text.indexOf("PlayerGestureHandler(")
        val controlsIndex = text.indexOf("PlayerControls(")

        assertTrue(gestureIndex >= 0, "PlayerOverlay must mount the mobile gesture layer")
        assertTrue(controlsIndex > gestureIndex, "PlayerOverlay should layer controls after gestures")

        val guardWindow = text.substring(0, gestureIndex).takeLast(240)
        assertTrue(
            guardWindow.contains("if (!state.showControls && !state.showUpNext)"),
            "The full-screen gesture layer must be removed while controls are visible so button taps reach CC, settings, back, and playback controls",
        )
    }
}
