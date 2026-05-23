package com.continuum.app.tv.ui.screens.collections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.continuum.app.tv.ui.components.TvCatalogEmptyState
import com.continuum.app.tv.ui.components.TvCatalogGrid
import com.continuum.app.tv.ui.components.TvErrorScreen
import com.continuum.app.tv.ui.components.TvLoadingScreen
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvCollectionDetailScreen(
    collectionId: String,
    title: String,
    onItemClick: (contentId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: TvCollectionDetailViewModel = koinViewModel(
        key = "collection-$collectionId",
        parameters = { parametersOf(collectionId, title) },
    ),
) {
    val state by viewModel.uiState.collectAsState()

    BackHandler(enabled = true) { onBack() }

    // Without an explicit focus target, the user lands on this screen with
    // nothing focused and has to mash D-pad before anything responds.
    val firstItemFocusRequester = remember { FocusRequester() }
    var initialFocusRequested by remember { mutableStateOf(false) }
    LaunchedEffect(state.items.firstOrNull()?.contentId) {
        if (initialFocusRequested || state.items.isEmpty()) return@LaunchedEffect
        runCatching { firstItemFocusRequester.requestFocus() }
        initialFocusRequested = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Text(
            text = viewModel.title.ifBlank { title },
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 24.dp),
        )

        when {
            state.isLoading && state.items.isEmpty() -> TvLoadingScreen()
            state.error != null && state.items.isEmpty() -> TvErrorScreen(
                message = state.error!!,
                onRetry = viewModel::retry,
            )
            else -> TvCatalogGrid(
                items = state.items,
                isLoading = state.isLoadingMore,
                hasMore = state.hasMore,
                onItemClick = onItemClick,
                onLoadMore = viewModel::loadMore,
                firstItemFocusRequester = firstItemFocusRequester,
                emptyState = {
                    TvCatalogEmptyState(message = "This collection is empty.")
                },
            )
        }
    }
}
