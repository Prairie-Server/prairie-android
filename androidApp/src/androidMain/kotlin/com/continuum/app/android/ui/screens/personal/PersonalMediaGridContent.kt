package com.continuum.app.android.ui.screens.personal

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.continuum.app.android.ui.components.EmptyStateView
import com.continuum.app.android.ui.components.ErrorView
import com.continuum.app.android.ui.components.FavoriteButton
import com.continuum.app.android.ui.components.LoadingIndicator
import com.continuum.app.android.ui.components.MediaCardContextMenu
import com.continuum.app.android.ui.components.WatchedBadge
import com.continuum.app.android.ui.components.WatchlistButton
import com.continuum.app.android.ui.components.rememberBrowseItemCardActions
import com.continuum.app.common.ui.components.ThumbhashImage
import com.continuum.app.model.catalog.BrowseItem
import com.continuum.app.viewmodel.FavoritesViewModel
import com.continuum.app.viewmodel.PersonalListUiState
import com.continuum.app.viewmodel.WatchlistViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FavoritesGridContent(
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: FavoritesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    PersonalMediaGridContent(
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        emptyTitle = "No favorites yet",
        emptySubtitle = "Items you mark as favorites will appear here",
        emptyIcon = Icons.Outlined.FavoriteBorder,
        onRetry = viewModel::retry,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        itemContent = { item ->
            MediaGridItem(
                item = item,
                onClick = { onItemClick(item.contentId) },
                onFavoriteToggle = { viewModel.toggleFavorite(item.contentId) },
                showFavoriteButton = true,
                isFavorite = true,
            )
        },
    )
}

@Composable
fun WatchlistGridContent(
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: WatchlistViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    PersonalMediaGridContent(
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        emptyTitle = "Your watchlist is empty",
        emptySubtitle = "Add items to your watchlist to keep track of what you want to watch",
        emptyIcon = Icons.Outlined.BookmarkBorder,
        onRetry = viewModel::retry,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        itemContent = { item ->
            MediaGridItem(
                item = item,
                onClick = { onItemClick(item.contentId) },
                onWatchlistToggle = { viewModel.removeFromWatchlist(item.contentId) },
                showWatchlistButton = true,
                isInWatchlist = true,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonalMediaGridContent(
    state: PersonalListUiState,
    emptyTitle: String,
    emptySubtitle: String,
    emptyIcon: ImageVector,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    itemContent: @Composable (BrowseItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val gridState = rememberLazyGridState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= layoutInfo.totalItemsCount - 8
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.hasMore && !state.isLoadingMore && !state.isLoading) {
            onLoadMore()
        }
    }

    when {
        state.isLoading -> {
            LoadingIndicator(modifier = modifier.padding(contentPadding))
        }
        state.error != null && state.items.isEmpty() -> {
            ErrorView(
                message = state.error ?: "Unknown error",
                onRetry = onRetry,
                modifier = modifier.padding(contentPadding),
            )
        }
        state.items.isEmpty() -> {
            EmptyStateView(
                title = emptyTitle,
                subtitle = emptySubtitle,
                icon = emptyIcon,
                modifier = modifier.padding(contentPadding),
            )
        }
        else -> {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    state = gridState,
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = state.items,
                        key = { it.contentId },
                        contentType = { item -> item.type },
                    ) { item ->
                        itemContent(item)
                    }

                    if (state.isLoadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaGridItem(
    item: BrowseItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFavoriteToggle: (() -> Unit)? = null,
    showFavoriteButton: Boolean = false,
    isFavorite: Boolean = false,
    onWatchlistToggle: (() -> Unit)? = null,
    showWatchlistButton: Boolean = false,
    isInWatchlist: Boolean = false,
) {
    val (actions, userState) = rememberBrowseItemCardActions(item)
    var menuExpanded by remember { mutableStateOf(false) }

    androidx.compose.foundation.layout.Column(
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = { menuExpanded = true },
        ),
    ) {
        Box {
            ThumbhashImage(
                url = item.posterUrl,
                thumbhash = item.posterThumbhash,
                contentDescription = item.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(MaterialTheme.shapes.small),
            )

            if (userState.played) {
                WatchedBadge(modifier = Modifier.align(Alignment.TopEnd))
            }
        }

        androidx.compose.material3.Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )

        if (item.year > 0) {
            androidx.compose.material3.Text(
                text = item.year.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (showFavoriteButton && onFavoriteToggle != null) {
            FavoriteButton(
                isFavorite = isFavorite,
                onToggle = onFavoriteToggle,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (showWatchlistButton && onWatchlistToggle != null) {
            WatchlistButton(
                isInWatchlist = isInWatchlist,
                onToggle = onWatchlistToggle,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        MediaCardContextMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            actions = actions,
            isPlayed = userState.played,
            isFavorite = userState.isFavorite,
            isInWatchlist = userState.inWatchlist,
        )
    }
}
