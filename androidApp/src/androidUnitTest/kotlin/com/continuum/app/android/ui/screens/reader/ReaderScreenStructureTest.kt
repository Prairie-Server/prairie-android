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

    @Test
    fun settingsSheetLabelsThemeTypographyAndMargins() {
        assertTrue(shell.contains("Theme"))
        assertTrue(shell.contains("Text size"))
        assertTrue(shell.contains("Margins"))
        assertTrue(shell.contains("Normal"))
        assertTrue(shell.contains("%"))
    }

    @Test
    fun settingsThemeUsesSelectableChips() {
        assertTrue(shell.contains("FilterChip("))
        assertTrue(shell.contains("selected = settings.theme == theme"))
        assertFalse(shell.contains("TextButton("))
    }

    @Test
    fun `settings sheet wires font family and line height controls`() {
        assertTrue(shell.contains("copy(fontFamily = "), "settings sheet must wire fontFamily into a settings copy()")
        assertTrue(shell.contains("copy(lineHeight = "), "settings sheet must wire lineHeight into a settings copy()")
    }

    @Test
    fun shellOverlaysReaderChromeWithoutResizingReaderSurface() {
        assertTrue(shell.contains("ReaderSystemBarEffect("))
        assertTrue(shell.contains("WindowCompat.getInsetsController"))
        assertTrue(shell.contains("isAppearanceLightStatusBars"))
        assertTrue(shell.contains("isAppearanceLightNavigationBars"))
        assertTrue(shell.contains("ReaderContentFrame("))
        assertTrue(shell.contains("WindowInsets.statusBars"))
        assertTrue(shell.contains("WindowInsets.navigationBars"))
        assertTrue(shell.contains(".background(readerBackground)"))
        assertFalse(shell.contains("chromeVisible: Boolean"))
        assertFalse(shell.contains("topChromePadding"))
        assertFalse(shell.contains("bottomChromePadding"))
        assertFalse(shell.contains("READER_TOP_CHROME_HEIGHT"))
        assertFalse(shell.contains("READER_BOTTOM_CHROME_HEIGHT"))
    }
}
