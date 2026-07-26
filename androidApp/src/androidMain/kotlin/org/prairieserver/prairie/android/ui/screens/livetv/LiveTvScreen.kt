package org.prairieserver.prairie.android.ui.screens.livetv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.koin.compose.viewmodel.koinViewModel
import org.prairieserver.prairie.android.ui.components.EmptyStateView
import org.prairieserver.prairie.android.ui.components.LoadingIndicator
import org.prairieserver.prairie.android.ui.components.PrairieTopBar
import org.prairieserver.prairie.model.livetv.LiveTvChannel
import org.prairieserver.prairie.model.livetv.LiveTvProgram
import org.prairieserver.prairie.viewmodel.LiveTvChannelRow
import org.prairieserver.prairie.viewmodel.LiveTvViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvScreen(
    onBackClick: () -> Unit,
    onChannelClick: (LiveTvChannel) -> Unit,
    viewModel: LiveTvViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.recordingMessage) {
        val message = state.recordingMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearRecordingMessage()
    }

    Scaffold(
        topBar = {
            PrairieTopBar(
                title = "Live TV",
                onBackClick = onBackClick,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.isLoading && state.channels.isEmpty() -> {
                LoadingIndicator(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            }
            state.error != null && state.channels.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EmptyStateView(
                        icon = Icons.Outlined.LiveTv,
                        title = "Live TV unavailable",
                        subtitle = state.error ?: "Could not load channels.",
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = viewModel::load) {
                        Text("Retry")
                    }
                }
            }
            state.channels.isEmpty() -> {
                EmptyStateView(
                    icon = Icons.Outlined.LiveTv,
                    title = "No channels",
                    subtitle = "No Live TV channels are available on this server.",
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
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.channels, key = { it.channel.id }) { row ->
                            LiveTvChannelListItem(
                                row = row,
                                onClick = { onChannelClick(row.channel) },
                                onRecord = { program -> viewModel.scheduleRecording(program) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveTvChannelListItem(
    row: LiveTvChannelRow,
    onClick: () -> Unit,
    onRecord: (LiveTvProgram) -> Unit,
) {
    val channel = row.channel
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (channel.logoUrl.isNotBlank()) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.LiveTv,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (channel.displayNumber.isNotBlank()) {
                    Text(
                        text = channel.displayNumber,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = channel.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (channel.hd) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "HD",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            val nowTitle = row.nowPlaying?.title?.takeIf { it.isNotBlank() }
            if (nowTitle != null) {
                Text(
                    text = nowTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val nowProgram = row.nowPlaying
        if (nowProgram != null && nowProgram.id.isNotBlank()) {
            TextButton(onClick = { onRecord(nowProgram) }) {
                Icon(
                    imageVector = Icons.Outlined.FiberManualRecord,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Record")
            }
        }
    }
}
