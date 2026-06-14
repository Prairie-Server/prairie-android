package com.continuum.app.common.ebook

enum class ReaderSheet {
    None,
    Sections,
    Bookmarks,
    Settings,
    More,
}

data class ReaderShellUiState(
    val chromeVisible: Boolean = false,
    val activeSheet: ReaderSheet = ReaderSheet.None,
)

sealed interface ReaderShellEvent {
    data object ToggleChrome : ReaderShellEvent
    data object ShowChrome : ReaderShellEvent
    data object HideChrome : ReaderShellEvent
    data object AutoHideChrome : ReaderShellEvent
    data object DismissSheet : ReaderShellEvent
    data class OpenSheet(val sheet: ReaderSheet) : ReaderShellEvent
}

fun reduceReaderShellState(
    state: ReaderShellUiState,
    event: ReaderShellEvent,
): ReaderShellUiState = when (event) {
    ReaderShellEvent.ToggleChrome -> state.copy(
        chromeVisible = if (state.activeSheet == ReaderSheet.None) {
            !state.chromeVisible
        } else {
            true
        },
    )
    ReaderShellEvent.ShowChrome -> state.copy(chromeVisible = true)
    ReaderShellEvent.HideChrome -> if (state.activeSheet == ReaderSheet.None) {
        state.copy(chromeVisible = false)
    } else {
        state
    }
    ReaderShellEvent.AutoHideChrome -> if (state.activeSheet == ReaderSheet.None) {
        state.copy(chromeVisible = false)
    } else {
        state
    }
    ReaderShellEvent.DismissSheet -> state.copy(activeSheet = ReaderSheet.None)
    is ReaderShellEvent.OpenSheet -> state.copy(
        chromeVisible = true,
        activeSheet = event.sheet,
    )
}
