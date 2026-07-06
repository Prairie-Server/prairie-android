package org.siloserver.silo.android.ui.screens.downloads

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class DownloadsMonitoringSourceTest {
    @Test
    fun downloadsScreenExposesMonitoringAndReclaimActions() {
        val screen = File("src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/downloads/DownloadsScreen.kt").readText()
        val vm = File("src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/downloads/DownloadsViewModel.kt").readText()

        assertTrue(screen.contains("Reclaim Watched"), "Downloads screen must expose Reclaim Watched.")
        assertTrue(screen.contains("Monitored"), "Downloads screen must expose monitored downloads.")
        assertTrue(vm.contains("refreshSubscriptions"), "ViewModel must load monitored subscriptions.")
        assertTrue(vm.contains("reclaimWatched"), "ViewModel must execute reclaim watched.")
        assertTrue(vm.contains("localContentStates"), "Reclaim Watched must be based on watched user state.")
    }
}
