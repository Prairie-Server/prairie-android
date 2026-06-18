package com.continuum.app.android.ui.screens.browse

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
import com.continuum.app.android.ui.components.MediaCard
import com.continuum.app.android.ui.components.rememberBrowseItemCardActions
import com.continuum.app.model.catalog.BrowseItem
import com.continuum.app.overlays.OverlayDataExtractor

/**
 * A vertical grid of media cards with infinite-scroll support.
 *
 * Uses a 3-column grid layout with automatic load-more triggering when
 * the user scrolls near the bottom.
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
        columns = GridCells.Fixed(3),
        state = gridState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier,
    ) {
        items(
            items = items,
            key = { it.contentId },
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
                width = 120.dp, // Will be constrained by grid cell
                overlay = OverlayDataExtractor.fromBrowseItem(item),
                actions = actions,
            )
        }

        // Loading indicator at bottom
        if (isLoadingMore) {
            item(span = { GridItemSpan(3) }) {
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
