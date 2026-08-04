package org.siloserver.silo.tv.ui.screens.requests

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import org.siloserver.silo.tv.ui.focus.rememberTvFlatReturnRestoration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.siloserver.silo.model.request.canCancel
import org.siloserver.silo.tv.ui.components.TvErrorScreen
import org.siloserver.silo.tv.ui.components.TvLoadingScreen
import org.siloserver.silo.tv.ui.shell.TvTopMenuLayout
import org.siloserver.silo.tv.ui.theme.SiloBlue
import org.siloserver.silo.tv.ui.theme.Spacing
import org.siloserver.silo.tv.ui.theme.sectionEyebrow
import org.siloserver.silo.viewmodel.MyRequestsViewModel
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
    val restoreItemFocusRequester = remember { FocusRequester() }
    var attachedRequesterId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // Rows are identified by the REQUEST, not by what they open. A row with a
    // library item opens that item while every other row opens the request
    // detail, so two rows can share a navigation target; keying restoration on
    // it would send focus to whichever came first. request.id is the row.
    //
    // Projected from the FILTERED list, because that is what is rendered — the
    // view model's unfiltered list would put every index in a different
    // coordinate space from the rows these indices address.
    //
    // Nothing here paginates, so hasMore is false and the page hunt never runs;
    // the restoration is purely resolve, scroll, confirm.
    val restoration = rememberTvFlatReturnRestoration(
        itemIds = visibleRequests.map { it.id },
        hasMore = false,
        isLoadingMore = false,
        // Refresh here is a button rather than a resume hook, so it cannot
        // collide with entry the way the personal lists' does — but it still
        // REPLACES the list, and a viewer who presses it and opens a row
        // before it lands would otherwise restore against the outgoing one.
        isReplacingContent = state.isRefreshing,
        errorMessage = state.error,
        surfaceKey = "my-requests",
        onLoadMore = {},
        scrollToItem = { itemIndex -> listState.scrollToItem(itemIndex + requestsHeaderSlots(state.error)) },
        requestFocus = restoreItemFocusRequester::requestFocus,
        onRestored = onInitialContentFocus,
    )

    // No separate first-entry path. On a fresh arrival the restoration already
    // targets row zero, so a second requester would race it — and worse, the
    // one that ran first was UNATTACHED whenever restoration owned row zero,
    // meaning it reported a content handoff it had not actually made. One
    // claimant, and it only reports once focus is confirmed.

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
                state = listState,
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
                    if (index == restoration.requesterItemIndex) {
                        DisposableEffect(request.id) {
                            attachedRequesterId = request.id
                            restoration.onRequesterAttached(request.id)
                            onDispose {
                                // Only if this row is still the owner. When the
                                // requester moves, the new row can attach
                                // before the old one disposes, and an
                                // unconditional clear would erase the live
                                // attachment and lose the restore.
                                if (attachedRequesterId == request.id) {
                                    attachedRequesterId = null
                                    restoration.onRequesterAttached(null)
                                }
                            }
                        }
                    }
                    TvRequestListCard(
                        request = request,
                        onClick = {
                            restoration.onItemClicked(itemId = request.id, index = index)
                            // In-library items open library detail; everything else
                            // opens the request detail (phone parity — rows are always
                            // actionable, not only when a library item exists).
                            val contentId = request.libraryContentId
                            if (contentId != null) onOpenLibraryItem(contentId)
                            else onOpenRequestDetail(request.mediaType, request.tmdbId)
                        },
                        focusRequester = restoreItemFocusRequester
                            .takeIf { index == restoration.requesterItemIndex },
                        // hasFocus, not isFocused: the card's own Surface owns
                        // focus below this modifier, so isFocused never fires
                        // here and the restoration could never confirm.
                        modifier = Modifier.onFocusChanged {
                            if (it.hasFocus) {
                                restoration.onItemFocused(request.id, index)
                            } else {
                                restoration.onItemFocusLost(request.id)
                            }
                        },
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
            color = SiloBlue.copy(alpha = 0.92f),
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

/** The error banner, when shown, is one list slot ahead of the request rows. */
private fun requestsHeaderSlots(error: String?): Int = if (error != null) 1 else 0
