package com.continuum.app.android.ui.screens.search

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchFilteredPaginationTest {
    @Test
    fun advancesWhileFilteredPagesAreEmptyAndUnderCap() {
        (1 until MAX_FILTERED_SEARCH_PAGES).forEach { page ->
            assertTrue(
                shouldAutoAdvanceFilteredSearchPage(
                    isClientFiltered = true,
                    visibleItemCount = 0,
                    hasMore = true,
                    pagesFetched = page,
                ),
                "page $page should auto-advance",
            )
        }
    }

    @Test
    fun stopsAtPageCapEvenWhenServerHasMore() {
        assertFalse(
            shouldAutoAdvanceFilteredSearchPage(
                isClientFiltered = true,
                visibleItemCount = 0,
                hasMore = true,
                pagesFetched = MAX_FILTERED_SEARCH_PAGES,
            ),
        )
        assertFalse(
            shouldAutoAdvanceFilteredSearchPage(
                isClientFiltered = true,
                visibleItemCount = 0,
                hasMore = true,
                pagesFetched = MAX_FILTERED_SEARCH_PAGES + 3,
            ),
        )
    }

    @Test
    fun stopsWhenVisibleResultsExist() {
        assertFalse(
            shouldAutoAdvanceFilteredSearchPage(
                isClientFiltered = true,
                visibleItemCount = 1,
                hasMore = true,
                pagesFetched = 1,
            ),
        )
    }

    @Test
    fun stopsWhenServerHasNoMore() {
        assertFalse(
            shouldAutoAdvanceFilteredSearchPage(
                isClientFiltered = true,
                visibleItemCount = 0,
                hasMore = false,
                pagesFetched = 1,
            ),
        )
    }

    @Test
    fun neverAdvancesUnfilteredSearches() {
        assertFalse(
            shouldAutoAdvanceFilteredSearchPage(
                isClientFiltered = false,
                visibleItemCount = 0,
                hasMore = true,
                pagesFetched = 1,
            ),
        )
    }
}
