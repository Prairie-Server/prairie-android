package org.prairieserver.prairie.tv.ui.focus

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvControlEnablementTest {
    @Test
    fun structuralDisablementLeavesTheFocusGraph() {
        val state = TvControlState.structural(isEnabled = false)

        assertFalse(state.focusable)
        assertFalse(state.actionable)
    }

    @Test
    fun transientDisablementPreservesFocusButSuppressesAction() {
        val state = TvControlState.transient(isEnabled = false)
        var invoked = false

        state.perform { invoked = true }

        assertTrue(state.focusable)
        assertFalse(state.actionable)
        assertFalse(invoked)
    }

    @Test
    fun enabledTransientControlRunsItsAction() {
        val state = TvControlState.transient(isEnabled = true)
        var invoked = false

        state.perform { invoked = true }

        assertTrue(state.focusable)
        assertTrue(state.actionable)
        assertTrue(invoked)
    }
}
