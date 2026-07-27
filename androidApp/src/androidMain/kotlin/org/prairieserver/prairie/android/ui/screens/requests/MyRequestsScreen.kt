package org.prairieserver.prairie.android.ui.screens.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.prairieserver.prairie.android.ui.components.PrairieTopBar
import org.prairieserver.prairie.android.ui.components.EmptyStateView
import org.prairieserver.prairie.android.ui.components.LoadingIndicator
import org.prairieserver.prairie.model.request.MediaRequest
import org.prairieserver.prairie.model.request.canCancel
import org.prairieserver.prairie.viewmodel.MyRequestsViewModel
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.Close

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyRequestsScreen(
    onBackClick: () -> Unit,
    onRequestClick: (MediaRequest) -> Unit,
    viewModel: MyRequestsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            PrairieTopBar(
                title = "My Requests",
                onBackClick = onBackClick,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.isLoading && state.requests.isEmpty() -> {
                LoadingIndicator(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
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
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        state.error?.let { error ->
                            item {
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        if (state.requests.isEmpty()) {
                            item {
                                EmptyStateView(
                                    title = "No requests yet",
                                    subtitle = "Movies and series you request will show up here with their status.",
                                    icon = Icons.Outlined.Inbox,
                                )
                            }
                        } else {
                            items(state.requests, key = { it.id }) { request ->
                                RequestListItem(
                                    request = request,
                                    onClick = { onRequestClick(request) },
                                    trailing = {
                                        if (request.canCancel()) {
                                            Column {
                                                OutlinedButton(
                                                    onClick = { viewModel.cancel(request.id) },
                                                    enabled = state.actionInFlightId != request.id,
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = if (state.actionInFlightId == request.id) {
                                                            "Canceling"
                                                        } else {
                                                            "Cancel"
                                                        },
                                                        maxLines = 1,
                                                    )
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
