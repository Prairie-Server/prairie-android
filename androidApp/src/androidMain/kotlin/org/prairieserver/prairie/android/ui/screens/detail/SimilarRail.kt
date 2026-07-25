package org.prairieserver.prairie.android.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.prairieserver.prairie.android.ui.components.MediaCard
import org.prairieserver.prairie.model.catalog.ItemDetail
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.repository.CatalogRepository
import org.prairieserver.prairie.repository.RecommendationRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.koin.compose.koinInject

/**
 * "More Like This" section — header plus a horizontal poster rail —
 * shown at the bottom of Movie / Series detail pages. Mirrors
 * `PhoneSimilarRail.swift`:
 *   1. Hit `/recommendations/similar/{contentId}` for scored IDs
 *   2. Resolve each ID to an `ItemDetail` in parallel
 *   3. Render a poster card per resolved item; tap opens detail
 *
 * The whole section (header included) stays hidden until the request
 * resolves with items — servers without media embeddings return an
 * empty/failed response, and an orphaned header would just read as a
 * broken row.
 */
@Composable
fun SimilarRail(
    contentId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    recommendationRepository: RecommendationRepository = koinInject(),
    catalogRepository: CatalogRepository = koinInject(),
) {
    var items by remember(contentId) { mutableStateOf<List<ItemDetail>>(emptyList()) }

    LaunchedEffect(contentId) {
        items = emptyList()
        items = loadSimilar(contentId, recommendationRepository, catalogRepository)
    }

    if (items.isNotEmpty()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = modifier,
        ) {
            SectionHeader(title = "More Like This")
            SimilarRailContent(items = items, onSelect = onSelect)
        }
    }
}

private suspend fun loadSimilar(
    contentId: String,
    recommendationRepository: RecommendationRepository,
    catalogRepository: CatalogRepository,
): List<ItemDetail> {
    val scored = when (val res = recommendationRepository.getSimilar(contentId, limit = 12)) {
        is ApiResult.Success -> res.data.items
        else -> return emptyList()
    }
    if (scored.isEmpty()) return emptyList()

    // Resolve detail pages in parallel — preserve engine ranking by
    // dropping null results (failed lookups) without reordering.
    return coroutineScope {
        scored
            .map { ref ->
                async {
                    when (val r = catalogRepository.getItemDetail(ref.mediaItemId)) {
                        is ApiResult.Success -> r.data
                        else -> null
                    }
                }
            }
            .awaitAll()
            .filterNotNull()
    }
}

@Composable
private fun SimilarRailContent(
    items: List<ItemDetail>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = SafePadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(
            items,
            key = { it.contentId },
            contentType = { "similar-item" },
        ) { item ->
            MediaCard(
                title = item.title,
                posterUrl = item.posterUrl,
                posterThumbhash = item.posterThumbhash,
                year = item.year.takeIf { it > 0 },
                type = item.type,
                userState = null,
                progress = null,
                onClick = { onSelect(item.contentId) },
                overlay = org.prairieserver.prairie.overlays.OverlayDataExtractor.fromItemDetail(item),
                sharedContentId = item.contentId,
            )
        }
    }
}

