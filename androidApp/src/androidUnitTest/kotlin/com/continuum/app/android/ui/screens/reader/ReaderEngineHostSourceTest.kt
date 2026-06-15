package com.continuum.app.android.ui.screens.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ReaderEngineHostSourceTest {
    private val sourceFile = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderEngineHost.kt",
    )

    @Test
    fun hostDispatchesEveryReaderEngineKind() {
        val source = sourceFile.readText()

        assertTrue(source.contains("ReaderEngineKind.Reflowable"))
        assertTrue(source.contains("ReaderEngineKind.FixedDocument"))
        assertTrue(source.contains("ReaderEngineKind.ComicManga"))
        assertTrue(source.contains("ReaderEngineKind.External"))
    }

    @Test
    fun externalPanelUsesPublicOriginalOpenPath() {
        val source = sourceFile.readText()

        assertTrue(source.contains("DownloadOpenTarget.from("))
        assertTrue(source.contains("openDownloadTargetInExternalApp("))
        assertTrue(source.contains("Open with another reader"))
    }
}
