package org.siloserver.silo.tv.ui.screens.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.siloserver.silo.model.admin.AdminAuditEntry
import org.siloserver.silo.model.admin.AdminLogEntry
import org.siloserver.silo.tv.ui.components.TvErrorScreen
import org.siloserver.silo.tv.ui.components.TvFilterChip
import org.siloserver.silo.tv.ui.components.TvLoadingScreen
import org.siloserver.silo.tv.ui.theme.Spacing
import org.koin.compose.viewmodel.koinViewModel

/**
 * TV Admin "Logs" — App + Audit tabs over the shared AdminRepository log APIs,
 * with a level filter (App) and cursor pagination (load-more near the end).
 * Mirrors the phone AdminLogsScreen, adapted to D-pad: tab + level chip rails
 * above a scrollable list of monospace log rows.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAdminLogsScreen(
    onBack: () -> Unit,
    viewModel: TvAdminLogsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    BackHandler(enabled = true) { onBack() }

    val tabCount = if (state.tab == TvLogTab.App) state.appEntries.size else state.auditEntries.size
    val tabCursor = if (state.tab == TvLogTab.App) state.appCursor else state.auditCursor
    val nearEnd by remember(state.tab, tabCount) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            tabCount > 0 && last >= tabCount - 4
        }
    }
    // Key on tab/count/cursor too: after a page lands, if the viewport is still
    // near the end, nearEnd may stay true and a key on it alone would miss the
    // next page. Re-evaluating when count/cursor change re-fires loadMore.
    LaunchedEffect(nearEnd, state.tab, tabCount, tabCursor) {
        if (nearEnd && tabCursor != null) viewModel.loadMore()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TvAdminScreenHeader(eyebrow = "ADMIN", title = "Logs")

        // Tab rail
        Row(
            modifier = Modifier.padding(horizontal = Spacing.safeArea),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvLogTab.entries.forEach { tab ->
                TvFilterChip(text = tab.label, selected = state.tab == tab, onClick = { viewModel.selectTab(tab) })
            }
        }

        // Level filter (App tab only)
        if (state.tab == TvLogTab.App) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.safeArea, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LOG_LEVELS.forEach { (wire, label) ->
                    TvFilterChip(text = label, selected = state.level == wire, onClick = { viewModel.setLevel(wire) })
                }
            }
        } else {
            Spacer(Modifier.height(12.dp))
        }

        when {
            state.isLoading && tabCount == 0 -> TvLoadingScreen()
            state.error != null && tabCount == 0 ->
                TvErrorScreen(message = state.error!!, onRetry = viewModel::load)
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.safeArea, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.tab == TvLogTab.App) {
                    items(state.appEntries, key = { it.id }) { AppLogRow(it) }
                } else {
                    items(state.auditEntries, key = { it.id }) { AuditLogRow(it) }
                }
                if (state.isLoadingMore) {
                    item { Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AppLogRow(entry: AdminLogEntry) {
    Card(
        onClick = {},
        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
        modifier = Modifier.fillMaxWidth().widthIn(max = 1400.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(
                text = "${entry.timestamp}  ${entry.level.uppercase()}  ${entry.component}",
                style = MaterialTheme.typography.labelMedium,
                color = levelColor(entry.level),
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AuditLogRow(entry: AdminAuditEntry) {
    Card(
        onClick = {},
        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
        modifier = Modifier.fillMaxWidth().widthIn(max = 1400.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(
                text = "${entry.timestamp}  ${entry.method} ${entry.statusCode}  ${entry.durationMs}ms",
                style = MaterialTheme.typography.labelMedium,
                color = if (entry.statusCode >= 400) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = entry.path,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun levelColor(level: String) = when (level.lowercase()) {
    "error", "fatal" -> MaterialTheme.colorScheme.error
    "warn", "warning" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}

private val LOG_LEVELS = listOf(
    null to "All",
    "info" to "Info",
    "warn" to "Warn",
    "error" to "Error",
)
