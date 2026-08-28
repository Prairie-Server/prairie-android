package org.prairieserver.prairie.tv.ui.screens.player

import org.prairieserver.prairie.model.playback.SubtitleIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun selectingAResolvedRowSelectsBeforeDismissingChrome() {
        val off = row(SubtitleIdentity.Off, "off")
        val english = row(SubtitleIdentity.ServerSidecar(4), "english")
        val events = mutableListOf<String>()
        val selected = mutableListOf<SubtitleIdentity>()

        val handled = dispatchTvQuickSubtitlePickerSelection(
            presentation = presentation(off, english),
            stableId = english.stableId,
            onSelect = { identity ->
                selected += identity
                events += "select"
            },
            onSelectionComplete = { events += "dismiss" },
        )

        assertTrue(handled)
        assertEquals(listOf<SubtitleIdentity>(SubtitleIdentity.ServerSidecar(4)), selected)
        assertEquals(listOf("select", "dismiss"), events)
    }

    @Test
    fun selectingAnUnknownRowKeepsPickerOpen() {
        val events = mutableListOf<String>()

        val handled = dispatchTvQuickSubtitlePickerSelection(
            presentation = presentation(row(SubtitleIdentity.Off, "off")),
            stableId = "unknown",
            onSelect = { events += "select" },
            onSelectionComplete = { events += "dismiss" },
        )

        assertFalse(handled)
        assertEquals(emptyList<String>(), events)
    }

    private fun presentation(vararg rows: TvSubtitleHudRow): TvSubtitleHudPresentation =
        TvSubtitleHudPresentation(
            rows = rows.toList(),
            hudOpen = true,
            focusedStableId = rows.firstOrNull()?.stableId,
            focusTrapActive = true,
        )

    private fun row(identity: SubtitleIdentity, stableId: String): TvSubtitleHudRow =
        TvSubtitleHudRow(
            stableId = stableId,
            identity = identity,
            label = stableId,
            checked = false,
            applying = false,
            focused = false,
        )
}
