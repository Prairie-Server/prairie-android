package org.prairieserver.prairie.android.cast

import org.prairieserver.prairie.model.catalog.ItemDetail
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.repository.CatalogRepository

/** Artwork resolved locally from the content identity in Remote Control state. */
data class PrairieCastArtwork(
    val posterUrl: String? = null,
    val posterThumbhash: String? = null,
    val backdropUrl: String? = null,
    val backdropThumbhash: String? = null,
    /** The item's display title (see [castDisplayTitle]), for one the TV has not reported yet. */
    val title: String? = null,
) {
    val isEmpty: Boolean get() = posterUrl == null && backdropUrl == null
}

/**
 * Episodes use their series' portrait poster while retaining the episode
 * still/backdrop for wide and blurred surfaces.
 */
internal suspend fun resolveCastArtwork(
    repository: CatalogRepository,
    contentId: String,
): PrairieCastArtwork {
    val detail = repository.detailOrNull(contentId) ?: return PrairieCastArtwork()
    val series = detail.seriesId
        ?.takeIf { detail.type == "episode" }
        ?.let { repository.detailOrNull(it) }
    return PrairieCastArtwork(
        posterUrl = series?.posterUrl ?: detail.posterUrl,
        posterThumbhash = if (series?.posterUrl != null) series.posterThumbhash else detail.posterThumbhash,
        backdropUrl = detail.backdropUrl ?: series?.backdropUrl,
        backdropThumbhash = if (detail.backdropUrl != null) detail.backdropThumbhash else series?.backdropThumbhash,
        title = castDisplayTitle(detail),
    )
}

/**
 * One line naming an item that is starting on a TV. An episode reads
 * "Series · S1 · E2", the TV player's own subtitle, since its bare title is
 * often ambiguous ("Pilot").
 */
internal fun castDisplayTitle(detail: ItemDetail): String? {
    val series = detail.seriesTitle?.takeIf { detail.type == "episode" && it.isNotBlank() }
        ?: return detail.title.takeIf { it.isNotBlank() }
    return listOfNotNull(
        series,
        detail.seasonNumber?.let { "S$it" },
        detail.episodeNumber?.let { "E$it" },
    ).joinToString(" · ")
}

private suspend fun CatalogRepository.detailOrNull(contentId: String): ItemDetail? =
    getCachedItemDetail(contentId)
        ?: (getItemDetail(contentId) as? ApiResult.Success)?.data
