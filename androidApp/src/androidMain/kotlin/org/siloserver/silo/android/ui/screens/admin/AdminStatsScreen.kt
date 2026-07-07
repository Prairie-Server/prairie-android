package org.siloserver.silo.android.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.siloserver.silo.android.ui.components.SiloTopBar
import org.siloserver.silo.android.ui.components.ErrorView
import org.siloserver.silo.android.ui.components.LoadingIndicator
import org.siloserver.silo.android.ui.theme.SiloError
import org.siloserver.silo.android.ui.theme.SiloPrimary
import org.siloserver.silo.android.ui.theme.SiloSecondaryText
import org.siloserver.silo.android.ui.theme.SiloSuccess
import org.siloserver.silo.android.ui.theme.SiloSurface
import org.siloserver.silo.android.ui.theme.SiloWarning
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import org.siloserver.silo.model.admin.AdminStats
import org.siloserver.silo.viewmodel.AdminStatsViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminStatsScreen(
    onBackClick: () -> Unit,
    viewModel: AdminStatsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // A refresh that fails while stats are already on screen otherwise just
    // stops the spinner silently (ErrorView only shows for the empty state).
    LaunchedEffect(state.error, state.stats) {
        val message = state.error
        if (message != null && state.stats != null) {
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = { SiloTopBar(title = "Admin", onBackClick = onBackClick) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                state.stats?.let { stats ->
                    StatsGrid(stats)
                }
            }
        }
    }
}

private data class StatTile(
    val title: String,
    val value: String,
    val icon: ImageVector,
    val color: Color,
)

@Composable
private fun StatsGrid(stats: AdminStats) {
    // Mirrors iOS AdminDashboardView.statsContent: a 2-column LazyVGrid with 12pt
    // spacing and 16pt content padding, six stat cards in this exact order.
    val tiles = listOf(
        StatTile("Total Items", stats.totalItems.toString(), Icons.Filled.VideoLibrary, SiloPrimary),
        StatTile("Users", stats.totalUsers.toString(), Icons.Filled.People, SiloSuccess),
        StatTile("Movies", stats.totalMovies.toString(), Icons.Filled.Movie, SiloWarning),
        StatTile("TV Shows", stats.totalShows.toString(), Icons.Filled.Tv, SiloPrimary),
        StatTile("Active Streams", stats.activeStreams.toString(), Icons.Filled.PlayCircle, SiloError),
        StatTile("Storage", formatStorageBytes(stats.totalStorageBytes), Icons.Filled.Storage, SiloSecondaryText),
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(tiles) { tile ->
            StatCard(tile)
        }
    }
}

@Composable
private fun StatCard(tile: StatTile) {
    // Mirrors iOS statCard: VStack(leading, spacing 12) with the icon top-left,
    // the value in siloTitle, the label in siloCaption, 16pt padding,
    // an 8pt rounded siloSurface background.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = SiloSurface,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = tile.icon,
                    contentDescription = null,
                    tint = tile.color,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.weight(1f))
            }
            Text(
                text = tile.value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = tile.title,
                style = MaterialTheme.typography.bodySmall,
                color = SiloSecondaryText,
            )
        }
    }
}

// Mirrors iOS AdminDashboardView.formatBytes exactly: 1.1f TB / 1.1f GB / .0f MB.
private fun formatStorageBytes(bytes: Long): String {
    val tb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0 * 1024.0)
    if (tb >= 1.0) return "%.1f TB".format(tb)
    val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    if (gb >= 1.0) return "%.1f GB".format(gb)
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return "%.0f MB".format(mb)
}
