package org.prairieserver.prairie.tv.ui.screens.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvLibraryFocusRestoreTest {
    @Test
    fun restoresSavedItemWhenStillPresent() {
        assertEquals(17, restoredLibraryFocusIndex(17, 40))
    }

    @Test
    fun clampsAfterLibraryShrinks() {
        assertEquals(4, restoredLibraryFocusIndex(17, 5))
    }

    @Test
    fun emptyGridHasNoRestoreTarget() {
        assertNull(restoredLibraryFocusIndex(3, 0))
    }

    @Test
    fun lazyGridTargetIncludesFullSpanHeaders() {
        assertEquals(19, restoredLibraryLazyGridIndex(17, 40, 2))
        assertNull(restoredLibraryLazyGridIndex(0, 0, 2))
    }
}
