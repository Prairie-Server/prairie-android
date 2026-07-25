package org.prairieserver.prairie.android.ui.screens.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class PlayerFastForwardHoldSourceTest {
    private val gestureSource = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/player/PlayerGestureHandler.kt",
    ).readText()
    private val overlaySource = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/player/PlayerOverlay.kt",
    ).readText()
    private val screenSource = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/player/PlayerScreen.kt",
    ).readText()

    @Test
    fun phoneGestureLayerSupportsHoldToTemporary2x() {
        assertTrue(
            gestureSource.contains("onFastForwardHold: (Boolean) -> Unit"),
            "PlayerGestureHandler must expose a hold callback without persisting speed",
        )
        assertTrue(
            gestureSource.contains("delay(500)") &&
                gestureSource.contains("tryAwaitRelease()") &&
                gestureSource.contains("onFastForwardHold(true)") &&
                gestureSource.contains("onFastForwardHold(false)"),
            "Hold-to-2x must start after the long-press threshold and stop on release/cancel",
        )
    }

    @Test
    fun phonePlayerTemporarilyOverridesThenRestoresPlaybackSpeed() {
        assertTrue(
            screenSource.contains("fastForwardHoldActive"),
            "PlayerScreen must keep hold-to-2x as transient UI state",
        )
        assertTrue(
            screenSource.contains("controller.setPlaybackSpeed(2.0f)") &&
                screenSource.contains("preferredPlaybackSpeed.toFloat()"),
            "Hold-to-2x must apply 2.0x only while held and restore the user's selected speed",
        )
    }

    @Test
    fun holdToFastForwardHasChipAndDoesNotRunInWatchTogether() {
        assertTrue(
            overlaySource.contains("isFastForwardHoldActive") &&
                overlaySource.contains("2x"),
            "PlayerOverlay must show a visible rate chip while hold-to-2x is active",
        )
        assertTrue(
            overlaySource.contains("if (!inRoom) onFastForwardHold else { _: Boolean -> }"),
            "Watch Together rooms own transport; hold-to-2x must be suppressed there",
        )
    }
}
