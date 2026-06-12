package com.continuum.app.android.ui.screens.admin

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.continuum.app.android.ui.components.ContinuumTopBar
import com.continuum.app.android.ui.components.ErrorView
import com.continuum.app.android.ui.components.LoadingIndicator
import com.continuum.app.android.ui.util.formatBytes
import com.continuum.app.model.admin.AdminStats
import com.continuum.app.model.admin.WatchProviderActivity
import com.continuum.app.viewmodel.AdminStatsViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminStatsScreen(
    onBackClick: () -> Unit,
    viewModel: AdminStatsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { ContinuumTopBar(title = "Dashboard", onBackClick = onBackClick) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.isLoading && state.stats == null -> LoadingIndicator(modifier = Modifier.padding(padding))
            state.error != null && state.stats == null ->
                ErrorView(
                    message = state.error!!,
                    onRetry = viewModel::load,
                    modifier = Modifier.padding(padding),
                )
            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    state.stats?.let { stats ->
                        item { StatsGrid(stats) }
                        item { TraktSection(stats) }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun StatsGrid(stats: AdminStats) {
    val tiles = listOf(
        Triple(Icons.Default.Inventory2, "Total Items", stats.totalItems.toString()),
        Triple(Icons.Default.Movie, "Movies", "${stats.totalMovies} (${stats.totalMovieFiles} files)"),
        Triple(Icons.Default.Tv, "Shows", "${stats.totalShows} (${stats.totalShowFiles} files)"),
        Triple(Icons.Default.VideoLibrary, "Files", stats.totalFiles.toString()),
        Triple(Icons.Default.People, "Users", stats.totalUsers.toString()),
        Triple(Icons.Default.PlayArrow, "Active Streams", stats.activeStreams.toString()),
        Triple(Icons.Default.Storage, "Storage", formatBytes(stats.totalStorageBytes)),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (icon, label, value) ->
                    StatCard(icon, label, value, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCard(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TraktSection(stats: AdminStats) {
    val activity = stats.watchProviderActivity
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Watch Provider Activity",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            ),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TraktActivityRows(activity)
            }
        }
    }
}

@Composable
private fun TraktActivityRows(activity: WatchProviderActivity) {
    ActivityRow(Icons.Default.People, "Trakt-connected profiles", activity.traktConnectedProfiles.toString())
    ActivityRow(Icons.Default.People, "Trakt-enabled profiles", activity.traktEnabledProfiles.toString())
    ActivityRow(Icons.Default.Sync, "Sync runs (24h)", activity.syncRuns24h.toString())
    ActivityRow(Icons.Default.Sync, "Sync errors (24h)", activity.syncErrors24h.toString())
    ActivityRow(Icons.Default.Bolt, "Scrobbles (24h)", activity.scrobbles24h.toString())
    ActivityRow(Icons.Default.Bolt, "Open scrobbles", activity.openScrobbles.toString())
    ActivityRow(Icons.Default.VideoLibrary, "Imported watched (24h)", activity.importedWatched24h.toString())
    ActivityRow(Icons.Default.VideoLibrary, "Imported progress (24h)", activity.importedProgress24h.toString())
    ActivityRow(Icons.Default.VideoLibrary, "Exported watched (24h)", activity.exportedWatched24h.toString())
    ActivityRow(Icons.Default.VideoLibrary, "Pending exports", activity.pendingExports.toString())
    ActivityRow(Icons.Default.VideoLibrary, "Failed exports", activity.failedExports.toString())
    val lastSync = activity.lastSyncCompletedAt
    if (lastSync != null) {
        ActivityRow(Icons.Default.Sync, "Last sync completed", lastSync)
    }
}

@Composable
private fun ActivityRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
