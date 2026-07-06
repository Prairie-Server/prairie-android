package org.siloserver.silo.android.ui.screens.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class DownloadsSettingsParitySourceTest {
    @Test
    fun downloadsSettingsExposeAppleCleanupControls() {
        val settingsScreen = File("src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/settings/SettingsScreen.kt").readText()
        val settingsViewModel = File("src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/settings/SettingsViewModel.kt").readText()
        val downloadsScreen = File("src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/downloads/DownloadsScreen.kt").readText()
        val downloadsViewModel = File("src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/downloads/DownloadsViewModel.kt").readText()

        assertTrue(settingsViewModel.contains("keepWatchedDownloads"))
        assertTrue(settingsViewModel.contains("setKeepWatchedDownloads"))
        assertTrue(settingsScreen.contains("Keep watched downloads"))
        assertTrue(settingsScreen.contains("Remove All Downloads"))
        assertTrue(settingsScreen.contains("removeAllDownloads"))
        assertTrue(settingsScreen.contains("formatBytes(downloadsState.totalBytesUsed)"))
        assertTrue(settingsScreen.contains("Your library and server media stay intact."))
        assertTrue(downloadsViewModel.contains("fun removeAllDownloads()"))
        assertTrue(downloadsViewModel.contains("keepWatchedDownloadsFlow"))
        assertTrue(downloadsViewModel.contains("isRemovingAllDownloads"))
        assertTrue(downloadsScreen.contains("showReclaimWatched = !state.keepWatchedDownloads"))
        assertTrue(downloadsScreen.contains("if (isReclaiming) \"Reclaiming...\" else \"Reclaim Watched\""))
    }
}
