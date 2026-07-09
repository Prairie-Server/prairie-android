package org.siloserver.silo.android.ui.screens.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerPinchGravitySourceTest {
    private val gestureSource = File(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/player/PlayerGestureHandler.kt",
    ).readText()
    private val overlaySource = File(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/player/PlayerOverlay.kt",
    ).readText()

    @Test
    fun gestureLayerExposesDirectionalPinchVideoGravity() {
        assertTrue(gestureSource.contains("onPinchVideoGravity: (Boolean) -> Unit = {}"))
        assertTrue(gestureSource.contains("awaitEachGesture"))
        assertTrue(gestureSource.contains("PinchGravityThreshold"))
        assertTrue(gestureSource.contains("onPinchVideoGravity(true)"))
        assertTrue(gestureSource.contains("onPinchVideoGravity(false)"))
    }

    @Test
    fun overlayStepsVideoGravityAndShowsToastLabel() {
        assertTrue(overlaySource.contains("nextMobileVideoGravity"))
        assertTrue(overlaySource.contains("previousMobileVideoGravity"))
        assertTrue(overlaySource.contains("mobileVideoGravityLabel"))
        assertTrue(overlaySource.contains("Toast.makeText"))
        assertTrue(overlaySource.contains("viewModel.onSetVideoGravity(nextGravity)"))
    }

    @Test
    fun gravityStepsMatchIosClampedOrder() {
        // Pinch-out walks toward stretch and clamps; pinch-in walks back to
        // fit and clamps (iOS MobilePlayerGestureLayer parity — no wrap).
        assertEquals("fill", nextMobileVideoGravity("fit"))
        assertEquals("stretch", nextMobileVideoGravity("fill"))
        assertEquals("stretch", nextMobileVideoGravity("stretch"))
        assertEquals("fill", previousMobileVideoGravity("stretch"))
        assertEquals("fit", previousMobileVideoGravity("fill"))
        assertEquals("fit", previousMobileVideoGravity("fit"))
    }
}
