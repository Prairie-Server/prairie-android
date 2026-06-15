package com.continuum.app.android.ui.screens.reader

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
}
