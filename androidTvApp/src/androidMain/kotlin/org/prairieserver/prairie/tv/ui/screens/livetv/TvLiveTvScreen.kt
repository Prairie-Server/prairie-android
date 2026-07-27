package org.prairieserver.prairie.tv.ui.screens.livetv

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import org.koin.compose.viewmodel.koinViewModel
import org.prairieserver.prairie.model.livetv.LiveTvChannel
import org.prairieserver.prairie.model.livetv.LiveTvRecording
import org.prairieserver.prairie.tv.ui.shell.TvTopMenuLayout
import org.prairieserver.prairie.tv.ui.theme.Spacing
import org.prairieserver.prairie.viewmodel.LiveTvChannelRow
import org.prairieserver.prairie.viewmodel.LiveTvTab
import org.prairieserver.prairie.viewmodel.LiveTvViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvLiveTvScreen(
    onChannelClick: (LiveTvChannel) -> Unit,
    onInitialContentFocus: () -> Unit = {},
    viewModel: LiveTvViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(state.channels.firstOrNull()?.channel?.id, state.isLoading, state.selectedTab) {
        if (!state.isLoading && state.channels.isNotEmpty()) {
            runCatching { firstFocus.requestFocus() }
            onInitialContentFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LiveTvHeader(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.safeArea),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(LiveTvTab.entries) { tab ->
                val selected = state.selectedTab == tab
                Surface(
                    onClick = { viewModel.selectTab(tab) },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        } else {
                            Color.White.copy(alpha = 0.08f)
                        },
                        contentColor = Color.White,
                        focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        focusedContentColor = Color.White,
                    ),
                ) {
                    Text(
                        text = when (tab) {
                            LiveTvTab.Guide -> "Guide"
                            LiveTvTab.Channels -> "Channels"
                            LiveTvTab.Recordings -> "My recordings"
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when {
            state.isLoading && state.channels.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading channels…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.error != null && state.channels.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = state.error ?: "Live TV unavailable",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.selectedTab == LiveTvTab.Recordings -> {
                TvRecordingsList(
                    active = state.activeRecordings,
                    history = state.historyRecordings,
                    onCancel = viewModel::cancelRecording,
                )
            }
            state.channels.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No Live TV channels are available.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.safeArea,
                        end = Spacing.safeArea,
                        bottom = 56.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(state.channels, key = { _, row -> row.channel.id }) { index, row ->
                        TvLiveTvChannelRow(
                            row = row,
                            showNext = state.selectedTab == LiveTvTab.Guide,
                            focusRequester = firstFocus.takeIf { index == 0 },
                            onClick = { onChannelClick(row.channel) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvRecordingsList(
    active: List<LiveTvRecording>,
    history: List<LiveTvRecording>,
    onCancel: (LiveTvRecording) -> Unit,
) {
    if (active.isEmpty() && history.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No recordings yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.safeArea,
            end = Spacing.safeArea,
            bottom = 56.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (active.isNotEmpty()) {
            item {
                Text(
                    text = "Scheduled & in progress",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(active, key = { it.id }) { recording ->
                TvRecordingRow(recording = recording, onCancel = { onCancel(recording) })
            }
        }
        if (history.isNotEmpty()) {
            item {
                Text(
                    text = "History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(history, key = { it.id }) { recording ->
                TvRecordingRow(recording = recording, onCancel = null)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvRecordingRow(
    recording: LiveTvRecording,
    onCancel: (() -> Unit)?,
) {
    Surface(
        onClick = { onCancel?.invoke() },
        enabled = onCancel != null,
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.015f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f),
            contentColor = Color.White,
            focusedContainerColor = Color.White.copy(alpha = 0.16f),
            focusedContentColor = Color.White,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = recording.title.ifBlank { "Recording" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = recording.status.replaceFirstChar { it.uppercase() } +
                    if (onCancel != null) " · Select to cancel" else "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LiveTvHeader(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Spacing.safeArea,
                end = Spacing.safeArea,
                top = TvTopMenuLayout.contentTopInset,
                bottom = Spacing.lg,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Live TV",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Surface(
            onClick = onRefresh,
            enabled = !isRefreshing,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isRefreshing) "Refreshing" else "Refresh")
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvLiveTvChannelRow(
    row: LiveTvChannelRow,
    showNext: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val channel = row.channel
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.015f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f),
            contentColor = Color.White,
            focusedContainerColor = Color.White.copy(alpha = 0.16f),
            focusedContentColor = Color.White,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (channel.logoUrl.isNotBlank()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.25f)),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.LiveTv,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (channel.displayNumber.isNotBlank()) {
                        Text(
                            text = channel.displayNumber,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        text = channel.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val nowTitle = row.nowPlaying?.title?.takeIf { it.isNotBlank() }
                if (nowTitle != null) {
                    Text(
                        text = "Now · $nowTitle",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (showNext) {
                    val nextTitle = row.upNext?.title?.takeIf { it.isNotBlank() }
                    if (nextTitle != null) {
                        Text(
                            text = "Next · $nextTitle",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
