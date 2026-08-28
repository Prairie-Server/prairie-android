package org.prairieserver.prairie.tv.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.prairieserver.prairie.tv.ui.theme.FocusedContainer
import org.prairieserver.prairie.tv.ui.theme.FocusedContent

class TvSelectorRowVisualStateTest {

    @Test
    fun focusedRowsUseInvertedTvContrast() {
        val state = tvSelectorRowVisualState(focused = true, selected = false, enabled = true)

        assertEquals(FocusedContainer, state.container)
        assertEquals(FocusedContent, state.content)
        assertTrue(state.border.alpha > 0f)
    }

    @Test
    fun selectedIdleRowsRemainDistinctFromIdleRows() {
        val selected = tvSelectorRowVisualState(focused = false, selected = true, enabled = true)
        val idle = tvSelectorRowVisualState(focused = false, selected = false, enabled = true)

        assertNotEquals(idle.container, selected.container)
        assertNotEquals(idle.border, selected.border)
    }

    @Test
    fun disabledRowsStayMutedEvenWhenSelected() {
        val state = tvSelectorRowVisualState(focused = false, selected = true, enabled = false)

        assertTrue(state.content.alpha < 0.5f)
    }
}
