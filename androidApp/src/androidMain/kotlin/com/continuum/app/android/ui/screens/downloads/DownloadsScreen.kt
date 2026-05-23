package com.continuum.app.android.ui.screens.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.continuum.app.android.ui.components.EmptyStateView
import com.continuum.app.android.ui.components.LoadingIndicator
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen displaying downloaded media items.
 *
 * Currently a stub with an empty state since actual download functionality
 * is not yet implemented. The full UI is ready to be connected.
 *
 * @param onItemClick Called when a download item is tapped.
 * @param showTopBar Whether to show a top bar (false when used as tab content).
 * @param onBackClick Back navigation handler for standalone mode.
 */
@Composable
fun DownloadsScreen(
    onItemClick: (String) -> Unit,
    showTopBar: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    contentTopPadding: androidx.compose.ui.unit.Dp = 0.dp,
    viewModel: DownloadsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            if (showTopBar) {
                com.continuum.app.android.ui.components.ContinuumTopBar(
                    title = "Downloads",
                    onBackClick = onBackClick,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.isLoading -> {
                LoadingIndicator(
                    modifier = Modifier.padding(
                        top = padding.calculateTopPadding() + contentTopPadding,
                    ),
                )
            }
            state.downloads.isEmpty() -> {
                EmptyStateView(
                    title = "No downloads yet",
                    subtitle = "Downloaded media will appear here for offline playback",
                    icon = Icons.Outlined.DownloadForOffline,
                    modifier = Modifier.padding(
                        top = padding.calculateTopPadding() + contentTopPadding,
                    ),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item {
                        Spacer(modifier = Modifier.height(contentTopPadding))
                    }
                    items(
                        items = state.downloads,
                        key = { it.id },
                    ) { item ->
                        DownloadItemRow(
                            item = item,
                            onItemClick = { onItemClick(item.contentId) },
                            onDeleteClick = { viewModel.removeDownload(item.id) },
                        )
                    }
                }
            }
        }
    }
}
