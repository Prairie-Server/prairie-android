package org.siloserver.silo.tv.ui.screens.search

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvSearchFocusPolicyTest {
    @Test
    fun firstEntryFocusesField() {
        assertTrue(shouldFocusSearchField(false, false, false))
    }

    @Test
    fun backReturnKeepsResultsVisible() {
        assertFalse(shouldFocusSearchField(true, true, false))
    }

    @Test
    fun explicitRequestAlwaysFocusesField() {
        assertTrue(shouldFocusSearchField(true, true, true))
    }
}
