package org.siloserver.silo.common.ebook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderShellStateTest {
    @Test
    fun `center tap toggles chrome when no sheet is open`() {
        val shown = reduceReaderShellState(ReaderShellUiState(), ReaderShellEvent.ToggleChrome)
        val hidden = reduceReaderShellState(shown, ReaderShellEvent.ToggleChrome)

        assertTrue(shown.chromeVisible)
        assertFalse(hidden.chromeVisible)
        assertEquals(ReaderSheet.None, hidden.activeSheet)
    }

    @Test
    fun `opening sheet shows chrome and stores active sheet`() {
        val state = reduceReaderShellState(
            ReaderShellUiState(chromeVisible = false),
            ReaderShellEvent.OpenSheet(ReaderSheet.Bookmarks),
        )

        assertTrue(state.chromeVisible)
        assertEquals(ReaderSheet.Bookmarks, state.activeSheet)
    }

    @Test
    fun `opening none sheet does not close or alter open sheet`() {
        val initial = ReaderShellUiState(chromeVisible = true, activeSheet = ReaderSheet.More)

        val state = reduceReaderShellState(
            initial,
            ReaderShellEvent.OpenSheet(ReaderSheet.None),
        )

        assertEquals(initial, state)
    }

    @Test
    fun `dismissing sheet keeps chrome visible and clears active sheet`() {
        val state = reduceReaderShellState(
            ReaderShellUiState(chromeVisible = true, activeSheet = ReaderSheet.Settings),
            ReaderShellEvent.DismissSheet,
        )

        assertTrue(state.chromeVisible)
        assertEquals(ReaderSheet.None, state.activeSheet)
    }

    @Test
    fun `hide chrome does not hide chrome while a sheet is open`() {
        val state = reduceReaderShellState(
            ReaderShellUiState(chromeVisible = true, activeSheet = ReaderSheet.Settings),
            ReaderShellEvent.HideChrome,
        )

        assertTrue(state.chromeVisible)
        assertEquals(ReaderSheet.Settings, state.activeSheet)
    }

    @Test
    fun `toggle chrome keeps chrome visible while a sheet is open`() {
        val state = reduceReaderShellState(
            ReaderShellUiState(chromeVisible = true, activeSheet = ReaderSheet.Bookmarks),
            ReaderShellEvent.ToggleChrome,
        )

        assertTrue(state.chromeVisible)
        assertEquals(ReaderSheet.Bookmarks, state.activeSheet)
    }

    @Test
    fun `show chrome preserves active sheet`() {
        val state = reduceReaderShellState(
            ReaderShellUiState(chromeVisible = false, activeSheet = ReaderSheet.Sections),
            ReaderShellEvent.ShowChrome,
        )

        assertTrue(state.chromeVisible)
        assertEquals(ReaderSheet.Sections, state.activeSheet)
    }

    @Test
    fun `auto hide does not close or hide when a sheet is open`() {
        val state = reduceReaderShellState(
            ReaderShellUiState(chromeVisible = true, activeSheet = ReaderSheet.Sections),
            ReaderShellEvent.AutoHideChrome,
        )

        assertTrue(state.chromeVisible)
        assertEquals(ReaderSheet.Sections, state.activeSheet)
    }

    @Test
    fun `auto hide hides chrome when no sheet is open`() {
        val state = reduceReaderShellState(
            ReaderShellUiState(chromeVisible = true, activeSheet = ReaderSheet.None),
            ReaderShellEvent.AutoHideChrome,
        )

        assertFalse(state.chromeVisible)
        assertEquals(ReaderSheet.None, state.activeSheet)
    }
}
