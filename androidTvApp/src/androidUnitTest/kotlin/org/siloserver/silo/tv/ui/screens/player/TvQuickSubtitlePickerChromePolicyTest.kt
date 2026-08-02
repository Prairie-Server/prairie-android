package org.siloserver.silo.tv.ui.screens.player

import kotlin.test.Test
import kotlin.test.assertEquals

class TvQuickSubtitlePickerChromePolicyTest {
    @Test
    fun selectionClosesPickerAndPlaybackControls() {
        assertEquals(
            TvQuickSubtitlePickerChromeState(
                pickerVisible = false,
                controlsVisible = false,
            ),
            tvQuickSubtitlePickerChromeState(TvQuickSubtitlePickerExit.Selection),
        )
    }

    @Test
    fun backClosesPickerButKeepsPlaybackControlsVisible() {
        assertEquals(
            TvQuickSubtitlePickerChromeState(
                pickerVisible = false,
                controlsVisible = true,
            ),
            tvQuickSubtitlePickerChromeState(TvQuickSubtitlePickerExit.Back),
        )
    }
}
