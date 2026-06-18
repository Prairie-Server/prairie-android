package com.continuum.app.tv.ui.screens.personal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.continuum.app.tv.ui.components.TvCatalogGrid
import com.continuum.app.tv.ui.components.TvErrorScreen
import com.continuum.app.tv.ui.components.TvLoadingScreen
import com.continuum.app.tv.ui.theme.ContinuumBlue
import com.continuum.app.tv.ui.theme.Spacing
import com.continuum.app.tv.ui.theme.sectionEyebrow
import com.continuum.app.tv.ui.theme.tvPageContentPadding
import com.continuum.app.tv.ui.theme.tvPageStartPadding
import com.continuum.app.viewmodel.FavoritesViewModel
import com.continuum.app.viewmodel.HistoryViewModel
import com.continuum.app.viewmodel.PersonalListUiState
import com.continuum.app.viewmodel.WatchlistViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Favorites / Watchlist / History screens all share the same `6-column grid
 * with pagination` layout — the only thing that differs is the header icon,
 * the title, and the ViewModel they read from. This file has one composable
 * per screen that forwards to a shared [PersonalGrid] helper.
 *
 * Navigated to from Settings → Library shortcuts (Phase F). None of the
 * three appears directly on the navigation rail, matching tvOS.
 */

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvFavoritesScreen(
    onItemClick: (contentId: String) -> Unit,
    onInitialContentFocus: () -> Unit = {},
    viewModel: FavoritesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    PersonalGrid(
        title = "Favorites",
        icon = Icons.Filled.Favorite,
        emptyMessage = "No favorites yet",
        state = state,
        onItemClick = onItemClick,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::retry,
        onInitialContentFocus = onInitialContentFocus,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvWatchlistScreen(
    onItemClick: (contentId: String) -> Unit,
    onInitialContentFocus: () -> Unit = {},
    viewModel: WatchlistViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    PersonalGrid(
        title = "Watchlist",
        icon = Icons.Outlined.BookmarkBorder,
        emptyMessage = "Your watchlist is empty",
        state = state,
        onItemClick = onItemClick,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::retry,
        onInitialContentFocus = onInitialContentFocus,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvHistoryScreen(
    onItemClick: (contentId: String) -> Unit,
    onInitialContentFocus: () -> Unit = {},
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    PersonalGrid(
        title = "Watch History",
        icon = Icons.Filled.History,
        emptyMessage = "No watch history yet",
        state = state,
        onItemClick = onItemClick,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::retry,
        onInitialContentFocus = onInitialContentFocus,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PersonalGrid(
    title: String,
    icon: ImageVector,
    emptyMessage: String,
    state: PersonalListUiState,
    onItemClick: (contentId: String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onInitialContentFocus: () -> Unit,
) {
    val startPadding = tvPageStartPadding()
    val firstItemFocusRequester = remember { FocusRequester() }
    val firstItemId = state.items.firstOrNull()?.contentId

    // One-shot guard so pagination, retries, or any other ViewModel re-emission
    // doesn't yank focus back to the first card after the user has scrolled.
    var initialFocusRequested by remember { mutableStateOf(false) }
    LaunchedEffect(firstItemId) {
        if (initialFocusRequested || firstItemId == null) return@LaunchedEffect
        runCatching { firstItemFocusRequester.requestFocus() }
        onInitialContentFocus()
        initialFocusRequested = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier.padding(
                start = startPadding,
                end = Spacing.safeArea,
                top = Spacing.xxl,
                bottom = Spacing.sm,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = "YOUR LIBRARY",
                style = sectionEyebrow,
                color = ContinuumBlue.copy(alpha = 0.92f),
            )
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ContinuumBlue,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        when {
            state.isLoading && state.items.isEmpty() -> TvLoadingScreen()
            state.error != null && state.items.isEmpty() -> TvErrorScreen(
                message = state.error ?: "",
                onRetry = onRetry,
            )
            state.items.isEmpty() -> EmptyState(
                message = emptyMessage,
                icon = icon,
            )
            else -> TvCatalogGrid(
                items = state.items,
                isLoading = state.isLoadingMore,
                hasMore = state.hasMore,
                onItemClick = onItemClick,
                onLoadMore = onLoadMore,
                contentPadding = tvPageContentPadding(top = Spacing.lg),
                firstItemFocusRequester = firstItemFocusRequester,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EmptyState(message: String, icon: ImageVector) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Unused import suppressor (Movie icon retained for future use).
@Suppress("unused")
private val _unused = Icons.Filled.Movie
