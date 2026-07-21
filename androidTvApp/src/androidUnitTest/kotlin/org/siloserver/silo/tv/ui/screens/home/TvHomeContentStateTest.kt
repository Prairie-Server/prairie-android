package org.siloserver.silo.tv.ui.screens.home

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvHomeContentStateTest {
    @Test fun filteredEmptyHomeShowsEmptyState() {
        assertTrue(shouldShowHomeEmptyState(false, null, 0))
    }

    @Test fun loadingAndErrorOwnTheirStates() {
        assertFalse(shouldShowHomeEmptyState(true, null, 0))
        assertFalse(shouldShowHomeEmptyState(false, "offline", 0))
        assertFalse(shouldShowHomeEmptyState(false, null, 2))
    }
}
