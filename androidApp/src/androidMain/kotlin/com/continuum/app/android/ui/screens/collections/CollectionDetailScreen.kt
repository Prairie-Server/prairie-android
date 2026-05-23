package com.continuum.app.android.ui.screens.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.continuum.app.android.ui.components.ContinuumTopBar
import com.continuum.app.android.ui.components.EmptyStateView
import com.continuum.app.android.ui.components.ErrorView
import com.continuum.app.android.ui.components.LoadingIndicator
import com.continuum.app.android.ui.screens.personal.MediaGridItem
import org.koin.compose.viewmodel.koinViewModel

/**
 * Detail screen for a single collection.
 *
 * Shows items in a grid with edit (rename) and delete actions in the top bar.
 * Long-press on an item (via the standard item actions) removes it from the collection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    collectionId: String,
    onBackClick: () -> Unit,
    onItemClick: (String) -> Unit,
    viewModel: CollectionDetailViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(collectionId) {
        viewModel.initialize(collectionId)
    }

    // Navigate back after deletion
    LaunchedEffect(state.deleted) {
        if (state.deleted) {
            onBackClick()
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= layoutInfo.totalItemsCount - 8
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.hasMore && !state.isLoadingMore && !state.isLoading) {
            viewModel.loadMore()
        }
    }

    Scaffold(
        topBar = {
            ContinuumTopBar(
                title = state.title,
                onBackClick = onBackClick,
                actions = {
                    if (state.canManage) {
                        IconButton(onClick = viewModel::showRenameDialog) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Rename collection",
                            )
                        }
                        IconButton(onClick = viewModel::showDeleteConfirm) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete collection",
                            )
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.isLoading -> {
                LoadingIndicator(modifier = Modifier.padding(padding))
            }
            state.error != null && state.items.isEmpty() -> {
                ErrorView(
                    message = state.error ?: "Unknown error",
                    onRetry = viewModel::refresh,
                    modifier = Modifier.padding(padding),
                )
            }
            state.items.isEmpty() && !state.isLoading -> {
                EmptyStateView(
                    title = "Collection is empty",
                    subtitle = if (state.canManage) {
                        "Add items to this collection from item detail screens"
                    } else {
                        "No items were found in this library collection"
                    },
                    icon = Icons.Outlined.Inbox,
                    modifier = Modifier.padding(padding),
                )
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
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
                        ) { item ->
                            MediaGridItem(
                                item = item,
                                onClick = { onItemClick(item.contentId) },
                            )
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

    // Rename dialog
    if (state.canManage && state.showRenameDialog) {
        AlertDialog(
            onDismissRequest = viewModel::hideRenameDialog,
            title = { Text("Rename Collection") },
            text = {
                OutlinedTextField(
                    value = state.renameText,
                    onValueChange = viewModel::onRenameTextChanged,
                    label = { Text("Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::renameCollection,
                    enabled = !state.isRenaming && state.renameText.isNotBlank(),
                ) {
                    if (state.isRenaming) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Rename")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::hideRenameDialog) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
        )
    }

    // Delete confirmation dialog
    if (state.canManage && state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::hideDeleteConfirm,
            title = { Text("Delete Collection") },
            text = {
                Text("Are you sure you want to delete \"${state.collection?.name}\"? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::deleteCollection,
                    enabled = !state.isDeleting,
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::hideDeleteConfirm) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
        )
    }
}
