package com.continuum.app.tv.ui.screens.admin

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.continuum.app.model.personal.UserLibrary
import com.continuum.app.tv.ui.components.TvDialogOption
import com.continuum.app.tv.ui.components.TvErrorScreen
import com.continuum.app.tv.ui.components.TvLoadingScreen
import com.continuum.app.tv.ui.components.TvOptionDialog
import com.continuum.app.tv.ui.theme.Spacing
import org.koin.compose.viewmodel.koinViewModel

/**
 * TV Admin "Scans" — mirrors the phone `AdminScansScreen`: a "Scan all
 * libraries" action plus a per-library list where each row opens a dialog to
 * scan or cancel that library. Logic lives in [TvAdminScansViewModel]
 * (per-library scan/cancel + scan-all via the shared AdminRepository).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAdminScansScreen(
    onBack: () -> Unit,
    viewModel: TvAdminScansViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var actionsTarget by remember { mutableStateOf<UserLibrary?>(null) }
    var lastMessage by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = true) { onBack() }

    LaunchedEffect(Unit) {
        viewModel.toasts.collect { lastMessage = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TvAdminScreenHeader(eyebrow = "ADMIN", title = "Scans", subtitle = lastMessage)

        when {
            state.isLoading && state.libraries.isEmpty() -> TvLoadingScreen()

            state.error != null && state.libraries.isEmpty() -> TvErrorScreen(
                message = state.error!!,
                onRetry = viewModel::load,
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.safeArea, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ActionCard(
                        title = if (state.scanningAll) "Scanning all libraries…" else "Scan all libraries",
                        subtitle = "Trigger a full rescan of every library",
                        enabled = !state.scanningAll,
                        onClick = { viewModel.scanAll() },
                    )
                }
                items(state.libraries, key = { it.id }) { library ->
                    LibraryRow(
                        library = library,
                        busy = library.id in state.busyLibraryIds,
                        onClick = { actionsTarget = library },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    actionsTarget?.let { library ->
        TvOptionDialog(
            title = library.name,
            options = listOf(
                TvDialogOption(
                    key = "scan",
                    title = "Scan now",
                    subtitle = "Rescan ${library.name} for new media",
                    onClick = {
                        actionsTarget = null
                        viewModel.scanLibrary(library.id)
                    },
                ),
                TvDialogOption(
                    key = "cancel-scan",
                    title = "Cancel scan",
                    subtitle = "Stop an in-progress scan",
                    onClick = {
                        actionsTarget = null
                        viewModel.cancelLibrary(library.id)
                    },
                ),
                TvDialogOption(
                    key = "dismiss",
                    title = "Cancel",
                    onClick = { actionsTarget = null },
                ),
            ),
            onDismiss = { actionsTarget = null },
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ActionCard(title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Card(
        onClick = { if (enabled) onClick() },
        shape = CardDefaults.shape(shape = RoundedCornerShape(16.dp)),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 960.dp)
            .height(44.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LibraryRow(library: UserLibrary, busy: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(shape = RoundedCornerShape(16.dp)),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 960.dp)
            .height(44.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = library.type.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (busy) {
                Text(
                    text = "Working…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
