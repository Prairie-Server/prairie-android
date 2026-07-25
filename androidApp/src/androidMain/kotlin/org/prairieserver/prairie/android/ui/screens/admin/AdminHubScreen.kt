package org.prairieserver.prairie.android.ui.screens.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.prairieserver.prairie.android.ui.components.PrairieTopBar
import org.prairieserver.prairie.android.ui.components.LoadingIndicator
import org.prairieserver.prairie.android.ui.screens.settings.SettingsSectionCard
import org.koin.compose.viewmodel.koinViewModel

/**
 * Admin hub. Lists the admin sub-sections and routes into each. The acting-admin
 * gate is re-evaluated here as defense in depth, so a deep link to this route by
 * a non-admin lands on a "not authorized" message rather than the section list.
 */
@Composable
fun AdminHubScreen(
    onBackClick: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenUsers: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenScans: () -> Unit,
    viewModel: AdminEntryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { PrairieTopBar(title = "Admin", onBackClick = onBackClick) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.isLoading -> LoadingIndicator(modifier = Modifier.padding(padding))
            !state.isAdminVisible -> NotAuthorized(modifier = Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SettingsSectionCard {
                        HubRow(Icons.Default.Dashboard, "Dashboard", "Server stats & activity", onOpenDashboard)
                        HubRow(Icons.Default.People, "Users", "Manage accounts & access", onOpenUsers)
                        HubRow(Icons.Default.PlayCircle, "Sessions", "Now playing & controls", onOpenSessions)
                        HubRow(Icons.AutoMirrored.Filled.Article, "Logs", "App & audit logs", onOpenLogs)
                        HubRow(Icons.Default.Sync, "Scans", "Library scans", onOpenScans)
                    }
                }
            }
        }
    }
}

@Composable
private fun HubRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NotAuthorized(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "You are not authorized to view this page.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
