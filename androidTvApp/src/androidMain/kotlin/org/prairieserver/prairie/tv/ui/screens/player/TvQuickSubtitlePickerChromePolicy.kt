package org.prairieserver.prairie.tv.ui.screens.player

import org.prairieserver.prairie.model.playback.SubtitleIdentity

internal enum class TvQuickSubtitlePickerExit {
    Selection,
    Back,
}

internal data class TvQuickSubtitlePickerChromeState(
    val pickerVisible: Boolean,
    val controlsVisible: Boolean,
)

internal fun tvQuickSubtitlePickerChromeState(
    exit: TvQuickSubtitlePickerExit,
): TvQuickSubtitlePickerChromeState = when (exit) {
    TvQuickSubtitlePickerExit.Selection -> TvQuickSubtitlePickerChromeState(
        pickerVisible = false,
        controlsVisible = false,
    )
    TvQuickSubtitlePickerExit.Back -> TvQuickSubtitlePickerChromeState(
        pickerVisible = false,
        controlsVisible = true,
    )
}

/**
 * Resolves a quick-picker row before changing player chrome, so invalid IDs
 * leave the picker visible and a valid selection is applied first.
 */
internal fun dispatchTvQuickSubtitlePickerSelection(
    presentation: TvSubtitleHudPresentation,
    stableId: String,
    onSelect: (SubtitleIdentity) -> Unit,
    onSelectionComplete: () -> Unit,
): Boolean {
    val row = presentation.rows.firstOrNull { it.stableId == stableId } ?: return false
    onSelect(row.identity)
    onSelectionComplete()
    return true
}
