package com.continuum.app.android.ui.screens.reader

import com.continuum.app.common.ebook.ReaderCapabilities
import com.continuum.app.model.book.BookFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderScreenStructureTest {
    private val screen = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt",
    ).readText()
    private val shell = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderShell.kt",
    ).readText()
    private val engineHost = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderEngineHost.kt",
    ).readText()

    @Test
    fun readerScreenDelegatesShellAndEngineWork() {
        assertTrue(screen.contains("ReaderShell("))
        assertTrue(screen.contains("ReaderEngineHost("))
        assertFalse(screen.contains("BookFormat.Pdf -> PdfReader("))
        assertFalse(screen.contains("BookFormat.Cbz -> ComicReader("))
        assertFalse(screen.contains("BookFormat.Epub, BookFormat.Fb2"))
    }

    @Test
    fun shellOwnsImmersiveChromeAndSheets() {
        assertTrue(shell.contains("reduceReaderShellState("))
        assertTrue(shell.contains("ReaderShellEvent.ToggleChrome"))
        assertTrue(shell.contains("ReaderSheet.Bookmarks"))
        assertTrue(shell.contains("ReaderSheet.Sections"))
        assertTrue(shell.contains("ReaderSheet.Settings"))
    }

    @Test
    fun shellProvidesExplicitToggleCallbackToReaderContent() {
        assertTrue(shell.contains("content: @Composable (onToggleChrome: () -> Unit) -> Unit"))
        assertTrue(shell.contains("content(onToggleChrome"))
        assertFalse(shell.contains(".clickable { send(ReaderShellEvent.ToggleChrome) }"))
        assertTrue(screen.contains("{ onToggleChrome ->"))
        assertTrue(screen.contains("onToggleChrome = onToggleChrome"))
        assertTrue(engineHost.contains("onToggleChrome: () -> Unit"))
    }

    @Test
    fun chromeBarsConsumeEmptyAreaTouches() {
        val shieldCount = Regex("""\.consumeChromeTouches\(\)""").findAll(shell).count()

        assertTrue(shell.contains("private fun Modifier.consumeChromeTouches()"))
        assertTrue(shieldCount >= 2)
    }

    @Test
    fun externalBottomChromeLabelDoesNotPretendToHavePages() {
        val label = readerBottomChromeLabel(
            ReaderUiState(
                capabilities = ReaderCapabilities.forFormat(BookFormat.Unknown),
                format = BookFormat.Unknown,
            ),
        )

        assertFalse(label.orEmpty().contains("Page"))
    }
}
