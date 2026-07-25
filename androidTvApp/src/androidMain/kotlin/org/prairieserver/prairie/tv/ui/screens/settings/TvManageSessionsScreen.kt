package org.prairieserver.prairie.tv.ui.screens.settings

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
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
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.prairieserver.prairie.model.auth.AuthSession
import org.prairieserver.prairie.tv.ui.components.TvDialogOption
import org.prairieserver.prairie.tv.ui.components.TvErrorScreen
import org.prairieserver.prairie.tv.ui.components.TvLoadingScreen
import org.prairieserver.prairie.tv.ui.components.TvOptionDialog
import org.koin.compose.viewmodel.koinViewModel

/**
 * TV "Manage Sessions" — the signed-in user's own active login sessions with a
 * revoke action. Mirrors the phone Settings → Manage Sessions (AuthRepository
 * getSessions/deleteSession). Each session is a focusable Card; selecting it
 * opens a confirm dialog to revoke that device's session.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvManageSessionsScreen(
    onBack: () -> Unit,
    viewModel: TvManageSessionsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var revokeTarget by remember { mutableStateOf<AuthSession?>(null) }
    var lastMessage by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = true) { onBack() }

    LaunchedEffect(state.message) {
        if (state.message != null) {
            lastMessage = state.message
            viewModel.consumeMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Header(title = "Manage Sessions", subtitle = lastMessage)

        when {
            state.isLoading && state.sessions.isEmpty() -> TvLoadingScreen()

            state.error != null && state.sessions.isEmpty() -> TvErrorScreen(
                message = state.error!!,
                onRetry = viewModel::load,
            )

            state.sessions.isEmpty() -> TvErrorScreen(message = "No active sessions.")

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.sessions, key = { it.id }) { session ->
                    SessionRow(
                        session = session,
                        busy = session.id in state.busyIds,
                        onClick = { revokeTarget = session },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    revokeTarget?.let { session ->
        TvOptionDialog(
            title = "Revoke session?",
            options = listOf(
                TvDialogOption(
                    key = "revoke",
                    title = "Revoke",
                    subtitle = "Sign out ${session.deviceName}",
                    onClick = {
                        revokeTarget = null
                        viewModel.revoke(session.id)
                    },
                ),
                TvDialogOption(
                    key = "cancel",
                    title = "Keep",
                    onClick = { revokeTarget = null },
                ),
            ),
            onDismiss = { revokeTarget = null },
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Header(title: String, subtitle: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Devices,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SessionRow(session: AuthSession, busy: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(shape = RoundedCornerShape(16.dp)),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .heightIn(min = 48.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = session.deviceName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = session.ipAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Signed in ${session.createdAt}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (busy) {
                Text(
                    text = "Revoking…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
