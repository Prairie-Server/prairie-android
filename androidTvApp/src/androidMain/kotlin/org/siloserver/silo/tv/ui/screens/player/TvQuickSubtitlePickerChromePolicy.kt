package org.siloserver.silo.tv.ui.screens.player

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
