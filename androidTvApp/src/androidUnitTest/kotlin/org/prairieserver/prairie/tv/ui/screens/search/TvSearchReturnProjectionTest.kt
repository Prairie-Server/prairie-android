package org.prairieserver.prairie.tv.ui.screens.search

import org.prairieserver.prairie.model.catalog.BrowseItem
import org.prairieserver.prairie.model.request.RequestAvailability
import org.prairieserver.prairie.model.request.RequestMediaResult
import org.prairieserver.prairie.tv.ui.focus.TvReturnRelocation
import org.prairieserver.prairie.tv.ui.focus.TvReturnResolution
import org.prairieserver.prairie.tv.ui.focus.TvReturnTarget
import org.prairieserver.prairie.tv.ui.focus.resolveTvReturnTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Search puts library items and requestable titles on one screen, and a
 * requestable title that is already in the library carries both identities.
 * These cover the collision that namespacing exists to prevent.
 */
class TvSearchReturnProjectionTest {

    private fun catalogItem(id: String) = BrowseItem(contentId = id, type = "movie", title = id)

    private fun requestResult(
        mediaType: String,
        tmdbId: Int,
        libraryContentId: String? = null,
    ) = RequestMediaResult(
        mediaType = mediaType,
        tmdbId = tmdbId,
        title = "t$tmdbId",
        availability = if (libraryContentId != null) RequestAvailability.Available else RequestAvailability.Missing,
        libraryContentId = libraryContentId,
    )

    @Test
    fun aRequestCardsLibraryTwinDoesNotStealTheReturn() {
        // The same title in both places: content id "m1" in the grid, and a
        // request card that opens that very item.
        val sections = tvSearchReturnSections(
            catalogItems = listOf(catalogItem("m0"), catalogItem("m1")),
            requestResults = listOf(requestResult("movie", 55, libraryContentId = "m1")),
            catalogComplete = true,
            requestsComplete = true,
        )

        // The viewer opened the REQUEST card, so the return belongs in the
        // request row — not on the grid card it happens to navigate to.
        val resolved = resolveTvReturnTarget(
            target = TvReturnTarget(
                sectionId = TvSearchRequestSectionId,
                itemId = tvSearchRequestItemId("movie", 55),
                sectionIndex = 1,
                itemIndex = 0,
            ),
            sections = sections,
        )

        val located = resolved as TvReturnResolution.Exact
        assertEquals(TvSearchRequestSectionId, located.sectionId)
        assertEquals(0, located.itemIndex)
    }

    @Test
    fun aCatalogIdIsNeverMatchedAgainstTheRequestRow() {
        val sections = tvSearchReturnSections(
            catalogItems = emptyList(),
            requestResults = listOf(requestResult("movie", 55, libraryContentId = "m1")),
            catalogComplete = true,
            requestsComplete = true,
        )

        // "m1" exists on screen — as the request card's library id. An
        // un-namespaced projection would match it here.
        val resolved = resolveTvReturnTarget(
            target = TvReturnTarget(
                sectionId = TvSearchCatalogSectionId,
                itemId = tvSearchCatalogItemId("m1"),
                sectionIndex = 0,
                itemIndex = 0,
            ),
            sections = sections,
            relocation = TvReturnRelocation.FollowAcrossSections,
        )

        assertTrue(
            resolved !is TvReturnResolution.Exact,
            "a catalog id must not resolve exactly against a request card",
        )
    }

    @Test
    fun aRequestSearchStillInFlightLetsTheTargetWait() {
        // Request search clears its results when a query starts, so an empty
        // row is not evidence that the card is gone.
        val sections = tvSearchReturnSections(
            catalogItems = listOf(catalogItem("m0")),
            requestResults = emptyList(),
            catalogComplete = true,
            requestsComplete = false,
        )

        val resolved = resolveTvReturnTarget(
            target = TvReturnTarget(
                sectionId = TvSearchRequestSectionId,
                itemId = tvSearchRequestItemId("movie", 55),
                sectionIndex = 1,
                itemIndex = 0,
            ),
            sections = sections,
        )

        assertEquals(TvReturnResolution.Pending, resolved)
    }

    @Test
    fun aCatalogIdSpelledLikeARequestIdIsStillDistinct() {
        // Proves the catalog namespace independently: without it, this content
        // id would BE the request row's id.
        val encoded = tvSearchRequestItemId("movie", 55)
        assertTrue(tvSearchCatalogItemId(encoded) != encoded)
    }

    @Test
    fun mediaTypeAliasesResolveToOneIdentity() {
        // The rendering pipeline accepts all of these for the same card, so
        // they must not become different saved identities.
        val canonical = tvSearchRequestItemId("audiobook", 9)
        assertEquals(canonical, tvSearchRequestItemId("audiobooks", 9))
        assertEquals(canonical, tvSearchRequestItemId("  AudioBook ", 9))
        assertEquals(tvSearchRequestItemId("movie", 9), tvSearchRequestItemId("MOVIE", 9))
    }

    @Test
    fun theSameTmdbIdUnderDifferentMediaTypesStaysDistinct() {
        assertTrue(tvSearchRequestItemId("movie", 7) != tvSearchRequestItemId("tv", 7))
    }

    @Test
    fun anIncompleteCatalogLetsADeepTargetWait() {
        val sections = tvSearchReturnSections(
            catalogItems = listOf(catalogItem("m0")),
            requestResults = emptyList(),
            catalogComplete = false,
            requestsComplete = true,
        )

        // Not yet loaded rather than not there: settling for a near miss here
        // would strand focus on a stand-in for good.
        val resolved = resolveTvReturnTarget(
            target = TvReturnTarget(
                sectionId = TvSearchCatalogSectionId,
                itemId = tvSearchCatalogItemId("m99"),
                sectionIndex = 0,
                itemIndex = 40,
            ),
            sections = sections,
        )

        assertEquals(TvReturnResolution.Pending, resolved)
    }
}
