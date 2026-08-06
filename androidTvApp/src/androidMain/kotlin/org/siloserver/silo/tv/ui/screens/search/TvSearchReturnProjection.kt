package org.siloserver.silo.tv.ui.screens.search

import org.siloserver.silo.model.catalog.BrowseItem
import org.siloserver.silo.model.request.RequestMediaResult
import org.siloserver.silo.model.request.RequestMediaType
import org.siloserver.silo.tv.ui.focus.TvReturnSection

/**
 * Search is the one surface where two different kinds of thing sit on the same
 * screen, so the return target has to say WHICH.
 *
 * The catalog grid holds library items keyed by content id; the footer row
 * holds requestable titles keyed by (mediaType, tmdbId). Those id spaces are
 * unrelated, and a requestable title that is already in the library carries
 * BOTH — it opens the library item while still being a request card. Left
 * un-namespaced, a return could resolve a content id against the request row,
 * or match the library twin of the request card the viewer actually opened and
 * put focus in the wrong section entirely.
 */
internal const val TvSearchCatalogSectionId: String = "search-catalog"

internal const val TvSearchRequestSectionId: String = "search-requests"

/** Namespaced so a content id can never collide with a request id. */
internal fun tvSearchCatalogItemId(contentId: String): String = "catalog:$contentId"

/**
 * Namespaced on the request's OWN identity, not on the library item it may
 * open. Two request cards can point at the same library item; the card is what
 * the viewer left.
 *
 * The media type is canonicalised first. The rendering pipeline already
 * accepts case and whitespace variants, and treats "audiobooks" as
 * "audiobook" — so the same result can arrive spelled differently across two
 * responses. Encoding it raw would give one card two identities and quietly
 * lose the return whenever the spelling changed under it.
 */
internal fun tvSearchRequestItemId(mediaType: String, tmdbId: Int): String =
    "request:${canonicalTvRequestMediaType(mediaType)}:$tmdbId"

private fun canonicalTvRequestMediaType(mediaType: String): String =
    when (val normalized = mediaType.trim().lowercase()) {
        "audiobooks" -> RequestMediaType.Audiobook
        else -> normalized
    }

/**
 * Sections in rendered order: the grid, then the footer row beneath it.
 *
 * [catalogComplete] is false while more pages can still arrive, which is what
 * lets a target deeper than the loaded results wait rather than settle for a
 * near miss.
 *
 * [requestsComplete] is not the same thing and cannot be assumed from "this
 * row does not paginate". Request search CLEARS its results when a query
 * starts and installs the response later, so there is a window where the row
 * is empty and not yet answered. Resolving then would read absence as final
 * and consume the target on a card that was about to come back. Modelled here
 * rather than left to the driver, so it cannot be forgotten at the call site.
 */
internal fun tvSearchReturnSections(
    catalogItems: List<BrowseItem>,
    requestResults: List<RequestMediaResult>,
    catalogComplete: Boolean,
    requestsComplete: Boolean,
): List<TvReturnSection> = listOf(
    TvReturnSection(
        id = TvSearchCatalogSectionId,
        itemIds = catalogItems.map { tvSearchCatalogItemId(it.contentId) },
        isComplete = catalogComplete,
    ),
    TvReturnSection(
        id = TvSearchRequestSectionId,
        itemIds = requestResults.map { tvSearchRequestItemId(it.mediaType, it.tmdbId) },
        isComplete = requestsComplete,
    ),
)
