package org.siloserver.silo.android.ui.screens.detail

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class DownloadQualityPickerSourceTest {
    private val screen = File(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/detail/ItemDetailScreen.kt",
    ).readText()
    private val picker = File(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/detail/DownloadQualityPickerSheet.kt",
    )

    @Test
    fun detailDownloadsOpenQualityPickerBeforeStartingNewDownloads() {
        assertTrue(screen.contains("pendingDownloadQualityAction"))
        assertTrue(screen.contains("showDownloadQualityPicker"))
        assertTrue(screen.contains("DownloadQualityPickerSheet("))
        assertTrue(screen.contains("quality -> action(quality)"))
        assertTrue(screen.contains("downloadQuality = quality"))
    }

    @Test
    fun qualityPickerListsAllSupportedDownloadPresets() {
        assertTrue(picker.exists(), "DownloadQualityPickerSheet should be a focused detail-screen component")
        val source = picker.readText()
        assertTrue(source.contains("DownloadQuality.entries.forEach"))
        assertTrue(source.contains("quality.label"))
        assertTrue(source.contains("onQualitySelected(quality)"))
    }
}
