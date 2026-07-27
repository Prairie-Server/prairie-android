package org.prairieserver.prairie.catalog.filter

import org.prairieserver.prairie.model.catalog.CatalogFiltersResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogFilterStateCoverageTest {

    @Test
    fun facetAvailabilityAndOptionPairs() {
        assertEquals(
            listOf(
                CatalogFacet.Genre, CatalogFacet.Decade, CatalogFacet.WatchStatus, CatalogFacet.ContentRating,
                CatalogFacet.Resolution, CatalogFacet.DynamicRange, CatalogFacet.Studio, CatalogFacet.Network,
                CatalogFacet.Country, CatalogFacet.AudioLanguage, CatalogFacet.SubtitleLanguage,
                CatalogFacet.OriginalLanguage,
            ),
            CatalogFacet.available(BrowseFacetMediaType.Video),
        )
        assertEquals(
            listOf(
                CatalogFacet.Genre, CatalogFacet.Author, CatalogFacet.Narrator, CatalogFacet.SeriesName,
                CatalogFacet.Decade, CatalogFacet.WatchStatus,
            ),
            CatalogFacet.available(BrowseFacetMediaType.Audiobook),
        )
        assertTrue(CatalogFacet.Decade.hasFixedVocabulary)
        assertFalse(CatalogFacet.Genre.hasFixedVocabulary)

        val vocab = CatalogFiltersResponse(
            genres = listOf("Drama"),
            studios = listOf("A24"),
            networks = listOf("HBO"),
            countries = listOf("US"),
            contentRatings = listOf("PG"),
            resolutions = listOf("1080p"),
            audioLanguages = listOf("en"),
            subtitleLanguages = listOf("es"),
            originalLanguages = listOf("fr"),
            authors = listOf("Ada"),
            narrators = listOf("Grace"),
            series = listOf("Discworld"),
        )
        assertEquals(listOf("2020" to "2020s"), facetOptionPairs(CatalogFacet.Decade, vocab).take(1))
        assertEquals(CatalogFacet.WATCH_STATUS_OPTIONS, facetOptionPairs(CatalogFacet.WatchStatus, vocab))
        assertTrue(facetOptionPairs(CatalogFacet.WatchStatus, vocab, hasProfile = false).isEmpty())
        assertEquals(CatalogFacet.DYNAMIC_RANGE_OPTIONS, facetOptionPairs(CatalogFacet.DynamicRange, vocab))
        assertEquals(listOf("Drama" to "Drama"), facetOptionPairs(CatalogFacet.Genre, vocab))
        assertEquals(listOf("PG" to "PG"), facetOptionPairs(CatalogFacet.ContentRating, vocab))
        assertEquals(listOf("1080p" to "1080p"), facetOptionPairs(CatalogFacet.Resolution, vocab))
        assertEquals(listOf("A24" to "A24"), facetOptionPairs(CatalogFacet.Studio, vocab))
        assertEquals(listOf("HBO" to "HBO"), facetOptionPairs(CatalogFacet.Network, vocab))
        assertEquals(listOf("US" to "US"), facetOptionPairs(CatalogFacet.Country, vocab))
        assertEquals(listOf("en" to "en"), facetOptionPairs(CatalogFacet.AudioLanguage, vocab))
        assertEquals(listOf("es" to "es"), facetOptionPairs(CatalogFacet.SubtitleLanguage, vocab))
        assertEquals(listOf("fr" to "fr"), facetOptionPairs(CatalogFacet.OriginalLanguage, vocab))
        assertEquals(listOf("Ada" to "Ada"), facetOptionPairs(CatalogFacet.Author, vocab))
        assertEquals(listOf("Grace" to "Grace"), facetOptionPairs(CatalogFacet.Narrator, vocab))
        assertEquals(listOf("Discworld" to "Discworld"), facetOptionPairs(CatalogFacet.SeriesName, vocab))
    }

    @Test
    fun filterStateToggleClearAndReset() {
        var state = CatalogFilterState()
        assertEquals(0, state.activeFacetCount)
        assertFalse(state.hasActiveFilters)
        assertFalse(state.canResetFilters)

        state = state.toggle(CatalogFacet.Genre, "Drama")
        state = state.toggle(CatalogFacet.Genre, "Action")
        assertEquals(setOf("Drama", "Action"), state.valuesFor(CatalogFacet.Genre))
        state = state.toggle(CatalogFacet.Genre, "Drama")
        assertEquals(setOf("Action"), state.valuesFor(CatalogFacet.Genre))

        state = state.toggle(CatalogFacet.WatchStatus, "watched")
        state = state.toggle(CatalogFacet.WatchStatus, "unwatched")
        assertEquals(setOf("unwatched"), state.valuesFor(CatalogFacet.WatchStatus))

        state = state.copy(matchAll = false)
        assertTrue(state.canResetFilters)
        state = state.clear(CatalogFacet.WatchStatus)
        assertTrue(state.valuesFor(CatalogFacet.WatchStatus).isEmpty())
        state = state.resetFilters()
        assertTrue(state.selections.isEmpty())
        assertTrue(state.matchAll)
    }
}
