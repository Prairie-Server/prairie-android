package org.siloserver.silo.tv.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvAnchoredSelectorMenuStateTest {
    @Test
    fun expandedSelectorStaysClosedAfterInteractivityReturns() {
        var expanded = true

        expanded = selectorExpansionAfterInteractivityChange(
            expanded = expanded,
            interactive = true,
        )
        assertTrue(expanded)

        expanded = selectorExpansionAfterInteractivityChange(
            expanded = expanded,
            interactive = false,
        )
        assertFalse(expanded)

        expanded = selectorExpansionAfterInteractivityChange(
            expanded = expanded,
            interactive = true,
        )
        assertFalse(expanded)
    }
}
