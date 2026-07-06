package org.siloserver.silo.tv.ui.shell

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class TvOfflineBannerSourceTest {
    private val source = File("src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvMainShell.kt")
        .readText()

    @Test
    fun mainShellExposesServerReachabilityBanner() {
        assertTrue(source.contains("ServerReachabilityMonitor"))
        assertTrue(source.contains("ServerReachabilityStatus.Unreachable"))
        assertTrue(source.contains("Offline mode"))
        assertTrue(source.contains("retryNow()"))
    }
}
