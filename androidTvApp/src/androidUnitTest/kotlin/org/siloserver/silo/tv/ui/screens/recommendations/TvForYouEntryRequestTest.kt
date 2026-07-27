package org.siloserver.silo.tv.ui.screens.recommendations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvForYouEntryRequestTest {

    @Test
    fun repeatedSelectionStillCreatesANewRequest() {
        val first = TvForYouEntryRequest().next(SavedListSelection.Watchlist)
        val second = first.next(SavedListSelection.Watchlist)

        assertEquals(1, first.sequence)
        assertEquals(2, second.sequence)
        assertEquals(SavedListSelection.Watchlist, second.selection)
    }

    @Test
    fun recommendationsRequestClearsSavedListSelection() {
        val request = TvForYouEntryRequest(
            sequence = 4,
            selection = SavedListSelection.Favorites,
        ).next(null)

        assertEquals(5, request.sequence)
        assertNull(request.selection)
    }

    @Test
    fun unrelatedRecompositionDoesNotOverrideInPageSelection() {
        val applied = applyForYouEntryRequest(
            currentSelection = SavedListSelection.Favorites,
            lastAppliedSequence = 3,
            request = TvForYouEntryRequest(
                sequence = 3,
                selection = SavedListSelection.Watchlist,
            ),
        )

        assertEquals(SavedListSelection.Favorites, applied.selection)
        assertEquals(3, applied.lastAppliedSequence)
    }

    @Test
    fun newerRequestAppliesRequestedInlineSelection() {
        val applied = applyForYouEntryRequest(
            currentSelection = null,
            lastAppliedSequence = 3,
            request = TvForYouEntryRequest(
                sequence = 4,
                selection = SavedListSelection.Watchlist,
            ),
        )

        assertEquals(SavedListSelection.Watchlist, applied.selection)
        assertEquals(4, applied.lastAppliedSequence)
    }
}
