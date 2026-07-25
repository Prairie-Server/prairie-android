package org.prairieserver.prairie.android.ui.screens.detail

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class DownloadQualityPickerSourceTest {
    private val screen = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/detail/ItemDetailScreen.kt",
    ).readText()
    private val picker = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/detail/DownloadQualityPickerSheet.kt",
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
        // The picker is capability-gated: it iterates the allowedQualities passed
        // in (filtered to what the server/account permits) rather than all six
        // presets, and never renders an empty list (falls back to Original).
        assertTrue(source.contains("allowedQualities: List<DownloadQuality>"))
        assertTrue(source.contains("allowedQualities.ifEmpty { listOf(DownloadQuality.Original) }"))
        assertTrue(source.contains("qualities.forEach"))
        assertTrue(source.contains("quality.label"))
        assertTrue(source.contains("onQualitySelected(quality)"))
    }
}
