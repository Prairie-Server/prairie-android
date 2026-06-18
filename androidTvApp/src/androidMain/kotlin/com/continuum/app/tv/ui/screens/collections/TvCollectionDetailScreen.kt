package com.continuum.app.tv.ui.screens.collections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.continuum.app.tv.ui.components.TvCatalogEmptyState
import com.continuum.app.tv.ui.components.TvCatalogGrid
import com.continuum.app.tv.ui.components.TvDialogOption
import com.continuum.app.tv.ui.components.TvErrorScreen
import com.continuum.app.tv.ui.components.TvLoadingScreen
import com.continuum.app.tv.ui.components.TvOptionDialog
import com.continuum.app.tv.ui.components.TvTextInputDialog
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

    // Once the collection is deleted, leave the detail screen (the grid
    // re-loads on re-entry and will drop it).
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = state.name.ifBlank { title },
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = viewModel::showRenameDialog) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Rename")
                }
                Button(onClick = viewModel::showDeleteConfirm) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete")
                }
            }
        }

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

    if (state.showRenameDialog) {
        TvTextInputDialog(
            title = "Rename collection",
            label = "Collection name",
            confirmLabel = "Save",
            initialValue = state.name,
            isBusy = state.isRenaming,
            errorMessage = state.renameError,
            onConfirm = viewModel::rename,
            onDismiss = viewModel::hideRenameDialog,
        )
    }

    if (state.showDeleteConfirm) {
        TvOptionDialog(
            title = state.deleteError?.let { "Delete \"${state.name}\"? — $it" }
                ?: "Delete \"${state.name}\"?",
            options = listOf(
                TvDialogOption(
                    key = "confirm",
                    title = if (state.isDeleting) "Deleting…" else "Delete collection",
                    subtitle = "This can't be undone",
                    enabled = !state.isDeleting,
                    onClick = viewModel::delete,
                ),
                TvDialogOption(key = "cancel", title = "Cancel", onClick = viewModel::hideDeleteConfirm),
            ),
            onDismiss = viewModel::hideDeleteConfirm,
        )
    }
}
