package org.siloserver.silo.android.ui.screens.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.siloserver.silo.android.ui.components.MediaCard
import org.siloserver.silo.android.ui.components.MediaGridDefaults
import org.siloserver.silo.android.ui.components.rememberBrowseItemCardActions
import org.siloserver.silo.model.catalog.BrowseItem
import org.siloserver.silo.overlays.OverlayDataExtractor

/**
 * A vertical grid of media cards with infinite-scroll support.
 *
     * Uses the shared iOS-style adaptive poster grid with automatic load-more
     * triggering when the user scrolls near the bottom.
 *
 * @param items The catalog items to display.
 * @param isLoadingMore Whether additional items are currently loading.
 * @param hasMore Whether there are more items to load.
 * @param onItemClick Callback with content ID when a card is tapped.
 * @param onLoadMore Callback to trigger loading the next page.
 * @param modifier Compose modifier.
 */
@Composable
fun CatalogGrid(
    items: List<BrowseItem>,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    onItemClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()

    // Trigger load more when scrolled near bottom
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = gridState.layoutInfo.totalItemsCount
            hasMore && !isLoadingMore && lastVisibleItem >= totalItems - 6
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }

    LazyVerticalGrid(
        // iOS phone: adaptive poster grid, 110pt minimum card width, 12pt
        // column spacing, 16pt row spacing, 16pt horizontal page padding.
        columns = GridCells.Adaptive(MediaGridDefaults.PosterGridMinWidth),
        state = gridState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(MediaGridDefaults.PosterGridHorizontalSpacing),
        verticalArrangement = Arrangement.spacedBy(MediaGridDefaults.PosterGridVerticalSpacing),
        modifier = modifier,
    ) {
        items(
            items = items,
            key = { it.contentId },
            contentType = { item -> item.type },
        ) { item ->
            val (actions, userState) = rememberBrowseItemCardActions(item)
            MediaCard(
                title = item.title,
                posterUrl = item.posterUrl,
                posterThumbhash = item.posterThumbhash,
                year = item.year,
                type = item.type,
                userState = userState,
                onClick = { onItemClick(item.contentId) },
                width = MediaGridDefaults.PosterGridMinWidth,
                overlay = OverlayDataExtractor.fromBrowseItem(item),
                actions = actions,
            )
        }

        // Loading indicator at bottom
        if (isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
