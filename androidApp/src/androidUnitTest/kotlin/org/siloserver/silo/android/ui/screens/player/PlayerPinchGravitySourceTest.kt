package org.siloserver.silo.android.ui.screens.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class PlayerPinchGravitySourceTest {
    private val gestureSource = File(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/player/PlayerGestureHandler.kt",
    ).readText()
    private val overlaySource = File(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/player/PlayerOverlay.kt",
    ).readText()

    @Test
    fun gestureLayerExposesPinchToCycleVideoGravity() {
        assertTrue(gestureSource.contains("onCycleVideoGravity: () -> Unit = {}"))
        assertTrue(gestureSource.contains("awaitEachGesture"))
        assertTrue(gestureSource.contains("PinchGravityThreshold"))
        assertTrue(gestureSource.contains("onCycleVideoGravity()"))
    }

    @Test
    fun overlayCyclesVideoGravityAndShowsToastLabel() {
        assertTrue(overlaySource.contains("nextMobileVideoGravity"))
        assertTrue(overlaySource.contains("mobileVideoGravityLabel"))
        assertTrue(overlaySource.contains("Toast.makeText"))
        assertTrue(overlaySource.contains("viewModel.onSetVideoGravity(nextGravity)"))
    }
}
