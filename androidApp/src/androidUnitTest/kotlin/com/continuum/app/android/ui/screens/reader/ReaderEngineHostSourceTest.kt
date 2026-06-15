package com.continuum.app.android.ui.screens.reader

import com.continuum.app.common.ebook.ReaderCapabilities
import com.continuum.app.model.book.BookFormat
import com.continuum.app.model.ebook.EbookReadMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
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

    @Test
    fun externalPanelRoutingRequiresExternalOnlyReadMode() {
        val unsupportedExternalEngineState = ReaderUiState(
            isLoading = false,
            readMode = EbookReadMode.Unsupported,
            capabilities = ReaderCapabilities.forFormat(BookFormat.Unknown),
        )

        assertFalse(shouldShowExternalReadingPanel(unsupportedExternalEngineState))
        assertTrue(
            shouldShowExternalReadingPanel(
                unsupportedExternalEngineState.copy(readMode = EbookReadMode.ExternalOnly),
            ),
        )
        assertFalse(
            shouldShowExternalReadingPanel(
                unsupportedExternalEngineState.copy(
                    readMode = EbookReadMode.ExternalOnly,
                    capabilities = ReaderCapabilities.forFormat(BookFormat.Epub),
                ),
            ),
        )
    }

    @Test
    fun sourceGuardsExternalPanelByReadMode() {
        val source = sourceFile.readText()

        assertTrue(source.contains("state.readMode == EbookReadMode.ExternalOnly"))
        assertTrue(source.contains("shouldShowExternalReadingPanel(state)"))
    }
}
