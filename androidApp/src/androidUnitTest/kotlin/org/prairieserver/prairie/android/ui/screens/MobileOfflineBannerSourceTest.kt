package org.prairieserver.prairie.android.ui.screens

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class MobileOfflineBannerSourceTest {
    private val source = File("src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/MainScreen.kt")
        .readText()

    @Test
    fun mainShellExposesServerReachabilityBanner() {
        assertTrue(source.contains("ServerReachabilityMonitor"))
        assertTrue(source.contains("ServerReachabilityStatus.Unreachable"))
        assertTrue(source.contains("Offline mode"))
        assertTrue(source.contains("retryNow()"))
    }
}
