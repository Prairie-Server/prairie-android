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
    private val reflowSourceFile = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/reflow/ReflowableReader.kt",
    )
    private val pdfSourceFile = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PdfReader.kt",
    )
    private val comicSourceFile = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicReader.kt",
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

    @Test
    fun hostPassesShellChromeToggleToEveryReaderSurface() {
        val source = sourceFile.readText()

        assertTrue(source.contains("onToggleChrome: () -> Unit"))
        assertTrue(source.contains("ExternalReadingPanel(state = state, onToggleChrome = onToggleChrome)"))
        assertTrue(source.contains("CenteredReaderMessage(\"Loading book...\", onToggleChrome = onToggleChrome)"))
        assertTrue(source.contains("onToggleChrome = onToggleChrome"))
    }

    @Test
    fun readerEnginesExposeTapPathToToggleShellChrome() {
        val reflowSource = reflowSourceFile.readText()
        val pdfSource = pdfSourceFile.readText()
        val comicSource = comicSourceFile.readText()

        assertTrue(reflowSource.contains("onToggleChrome: () -> Unit"))
        assertTrue(reflowSource.contains("else -> onToggleChrome()"))
        assertTrue(pdfSource.contains("onToggleChrome: () -> Unit"))
        assertTrue(pdfSource.contains("detectTapGestures(onTap = { onToggleChrome() })"))
        assertTrue(comicSource.contains("onToggleChrome: () -> Unit"))
        assertTrue(comicSource.contains("detectTapGestures(onTap = { onToggleChrome() })"))
    }
}
