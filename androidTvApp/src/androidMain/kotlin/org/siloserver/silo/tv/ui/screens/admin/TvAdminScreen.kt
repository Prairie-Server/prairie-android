package org.siloserver.silo.tv.ui.screens.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.siloserver.silo.model.admin.AdminStats
import org.siloserver.silo.tv.ui.components.TvErrorScreen
import org.siloserver.silo.tv.ui.components.TvLoadingScreen
import org.siloserver.silo.tv.ui.components.auroraGlass
import org.siloserver.silo.tv.ui.theme.Spacing
import org.siloserver.silo.viewmodel.AdminStatsViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * TV admin stats dashboard — 2-column grid of live server stats.
 * Admin user/library management is deferred to a follow-up phase.
 * Reuses the shared [AdminStatsViewModel]; no TV-specific copy needed.
 *
 * Reachable from Settings when [TvSettingsViewModel.UiState.adminVisible] is true
 * (acting-admin gate: admin role + primary profile).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAdminScreen(
    onBack: () -> Unit,
    viewModel: AdminStatsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    BackHandler(enabled = true) { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TvAdminScreenHeader(eyebrow = "ADMIN", title = "Dashboard")

        when {
            state.isLoading && state.stats == null -> TvLoadingScreen()
            state.error != null && state.stats == null -> TvErrorScreen(
                message = state.error!!,
                onRetry = viewModel::load,
            )
            state.stats != null -> AdminStatsGrid(stats = state.stats!!)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AdminStatsGrid(stats: AdminStats) {
    val tiles = listOf(
        StatTile("Total Items", stats.totalItems.toString()),
        StatTile("Movies", "${stats.totalMovies} / ${stats.totalMovieFiles} files"),
        StatTile("TV Shows", "${stats.totalShows} / ${stats.totalShowFiles} files"),
        StatTile("Users", stats.totalUsers.toString()),
        StatTile("Active Streams", stats.activeStreams.toString()),
        StatTile("Storage", formatBytes(stats.totalStorageBytes)),
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = Spacing.safeArea, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(tiles, key = { it.label }) { tile ->
            AdminStatCard(tile)
        }
    }
}

private data class StatTile(val label: String, val value: String)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AdminStatCard(tile: StatTile) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .auroraGlass(cornerRadius = 10.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = tile.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = tile.value,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    if (gb < 1024) return "%.1f GB".format(gb)
    val tb = gb / 1024.0
    return "%.2f TB".format(tb)
}
