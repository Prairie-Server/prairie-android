package com.continuum.app.tv.ui.screens.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.continuum.app.model.request.canCancel
import com.continuum.app.tv.ui.components.TvErrorScreen
import com.continuum.app.tv.ui.components.TvLoadingScreen
import com.continuum.app.tv.ui.shell.TvTopMenuLayout
import com.continuum.app.tv.ui.theme.ContinuumBlue
import com.continuum.app.tv.ui.theme.Spacing
import com.continuum.app.tv.ui.theme.sectionEyebrow
import com.continuum.app.viewmodel.MyRequestsViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvMyRequestsScreen(
    onOpenLibraryItem: (contentId: String) -> Unit = {},
    onOpenRequestDetail: (mediaType: String, tmdbId: Int) -> Unit = { _, _ -> },
    onInitialContentFocus: () -> Unit = {},
    viewModel: MyRequestsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val visibleRequests = state.requests.filterTvMediaRequests()
    val firstItemFocusRequester = remember { FocusRequester() }
    val firstRequestId = visibleRequests.firstOrNull()?.id
    var initialFocusRequested by remember { mutableStateOf(false) }

    LaunchedEffect(firstRequestId) {
        if (initialFocusRequested || firstRequestId == null) return@LaunchedEffect
        runCatching { firstItemFocusRequester.requestFocus() }
        onInitialContentFocus()
        initialFocusRequested = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        MyRequestsHeader(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
        )
        when {
            state.isLoading && state.requests.isEmpty() -> TvLoadingScreen()
            state.error != null && state.requests.isEmpty() -> TvErrorScreen(
                message = state.error ?: "Failed to load your requests.",
                onRetry = viewModel::load,
            )
            visibleRequests.isEmpty() -> EmptyMyRequests()
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(
                    start = Spacing.safeArea,
                    end = Spacing.safeArea,
                    bottom = 56.dp,
                ),
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
                itemsIndexed(visibleRequests, key = { _, request -> request.id }) { index, request ->
                    TvRequestListCard(
                        request = request,
                        onClick = {
                            // In-library items open library detail; everything else
                            // opens the request detail (phone parity — rows are always
                            // actionable, not only when a library item exists).
                            val contentId = request.libraryContentId
                            if (contentId != null) onOpenLibraryItem(contentId)
                            else onOpenRequestDetail(request.mediaType, request.tmdbId)
                        },
                        focusRequester = firstItemFocusRequester.takeIf { index == 0 },
                        trailing = {
                            if (request.canCancel()) {
                                TvRequestActionPill(
                                    label = if (state.actionInFlightId == request.id) "Canceling" else "Cancel",
                                    icon = Icons.Filled.Cancel,
                                    onClick = { viewModel.cancel(request.id) },
                                    enabled = state.actionInFlightId != request.id,
                                )
                            }
                        },
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun MyRequestsHeader(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(
            start = Spacing.safeArea,
            end = Spacing.safeArea,
            top = TvTopMenuLayout.contentTopInset,
            bottom = Spacing.lg,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = "REQUESTS",
            style = sectionEyebrow,
            color = ContinuumBlue.copy(alpha = 0.92f),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "My Requests",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            TvRequestActionPill(
                label = if (isRefreshing) "Refreshing" else "Refresh",
                icon = Icons.Filled.Refresh,
                onClick = onRefresh,
                enabled = !isRefreshing,
            )
        }
    }
}

@Composable
private fun EmptyMyRequests() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            androidx.tv.material3.Icon(
                imageVector = Icons.Filled.Inbox,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.62f),
                modifier = Modifier.height(30.dp),
            )
            Text(
                text = "No requests yet",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Text(
                text = "Requested movies and series will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.68f),
            )
        }
    }
}
