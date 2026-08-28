package org.prairieserver.prairie.tv.ui.screens.library

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Restoring the grid used to be a saved index clamped to the current size,
 * which is why these once asserted "17 comes back as 17". Identity resolution
 * lives in the shared contract now; what remains here is the grid's own
 * arithmetic — turning an item position into a LazyGrid position.
 */
class TvLibraryFocusRestoreTest {
    @Test
    fun lazyGridTargetIncludesFullSpanHeaders() {
        // Sort/filter controls and genre chips occupy full-span slots ahead of
        // the cards, so the grid runs ahead of the item index by however many
        // are showing.
        assertEquals(19, libraryLazyGridIndex(itemIndex = 17, headerCount = 2))
    }

    @Test
    fun aGridWithNoHeadersAddressesCardsDirectly() {
        assertEquals(17, libraryLazyGridIndex(itemIndex = 17, headerCount = 0))
    }

    @Test
    fun negativeInputsClampRatherThanAddressingBeforeTheGrid() {
        assertEquals(0, libraryLazyGridIndex(itemIndex = -1, headerCount = 0))
        assertEquals(3, libraryLazyGridIndex(itemIndex = 3, headerCount = -2))
    }
}
