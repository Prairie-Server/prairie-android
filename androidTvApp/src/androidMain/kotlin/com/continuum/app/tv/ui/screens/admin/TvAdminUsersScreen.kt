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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
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
import com.continuum.app.model.admin.AdminUser
import com.continuum.app.tv.ui.components.TvDialogOption
import com.continuum.app.tv.ui.components.TvErrorScreen
import com.continuum.app.tv.ui.components.TvLoadingScreen
import com.continuum.app.tv.ui.components.TvOptionDialog
import com.continuum.app.tv.ui.theme.Spacing
import com.continuum.app.viewmodel.AdminUsersViewModel
import com.continuum.app.viewmodel.roleDisplayName
import org.koin.compose.viewmodel.koinViewModel

/**
 * TV admin users management — list + per-user actions. Reuses the shared
 * [AdminUsersViewModel] (UI-independent: generation-gated load/refresh, delete,
 * one-shot messages) used by the phone's `AdminUsersScreen`, so the logic/flow
 * stays in lockstep with the gold-standard phone app.
 *
 * TV adapts the UI to D-pad: each user is a focusable [Card]; clicking it opens
 * a [TvOptionDialog] with the supported actions (currently Delete, mirroring the
 * phone's destructive action). Create/edit use a full form on the phone and are
 * deferred to a follow-up phase on TV.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAdminUsersScreen(
    onBack: () -> Unit,
    onCreateUser: () -> Unit = {},
    onEditUser: (userId: Int) -> Unit = {},
    viewModel: AdminUsersViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var actionsTarget by remember { mutableStateOf<AdminUser?>(null) }
    var pendingDelete by remember { mutableStateOf<AdminUser?>(null) }

    BackHandler(enabled = true) { onBack() }

    // Re-load when re-entering (parity with phone, which refreshes on resume).
    LaunchedEffect(Unit) { viewModel.refresh() }

    // Surface one-shot mutation messages by clearing them after they land; TV
    // has no snackbar, so we just consume so the flag doesn't stick.
    LaunchedEffect(state.message) {
        if (state.message != null) viewModel.consumeMessage()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TvAdminScreenHeader(eyebrow = "ADMIN", title = "Users", subtitle = state.message)

        when {
            state.isLoading && state.users.isEmpty() -> TvLoadingScreen()

            state.error != null && state.users.isEmpty() -> TvErrorScreen(
                message = state.error!!,
                onRetry = viewModel::load,
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.safeArea, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { AddUserRow(onClick = onCreateUser) }
                items(state.users, key = { it.id }) { user ->
                    UserRow(user = user, onClick = { actionsTarget = user })
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    actionsTarget?.let { user ->
        TvOptionDialog(
            title = user.username,
            options = buildList {
                add(
                    TvDialogOption(
                        key = "edit",
                        title = "Edit user",
                        subtitle = "Role, access, quotas & password",
                        onClick = {
                            val id = user.id
                            actionsTarget = null
                            onEditUser(id)
                        },
                    ),
                )
                if (user.role == "admin") {
                    add(
                        TvDialogOption(
                            key = "role-user",
                            title = "Make standard user",
                            subtitle = "Remove admin privileges",
                            onClick = {
                                actionsTarget = null
                                viewModel.setRole(user.id, "user")
                            },
                        ),
                    )
                } else {
                    add(
                        TvDialogOption(
                            key = "role-admin",
                            title = "Make admin",
                            subtitle = "Grant admin privileges",
                            onClick = {
                                actionsTarget = null
                                viewModel.setRole(user.id, "admin")
                            },
                        ),
                    )
                }
                add(
                    TvDialogOption(
                        key = "enabled",
                        title = if (user.enabled) "Disable user" else "Enable user",
                        subtitle = if (user.enabled) "Block sign-in for this account" else "Allow sign-in again",
                        onClick = {
                            actionsTarget = null
                            viewModel.setEnabled(user.id, !user.enabled)
                        },
                    ),
                )
                add(
                    TvDialogOption(
                        key = "delete",
                        title = "Delete user",
                        subtitle = "Permanently remove this account",
                        onClick = {
                            actionsTarget = null
                            pendingDelete = user
                        },
                    ),
                )
                add(
                    TvDialogOption(
                        key = "cancel",
                        title = "Cancel",
                        onClick = { actionsTarget = null },
                    ),
                )
            },
            onDismiss = { actionsTarget = null },
        )
    }

    pendingDelete?.let { user ->
        TvOptionDialog(
            title = "Delete ${user.username}?",
            options = listOf(
                TvDialogOption(
                    key = "confirm",
                    title = "Delete",
                    subtitle = "This cannot be undone",
                    onClick = {
                        viewModel.deleteUser(user.id)
                        pendingDelete = null
                    },
                ),
                TvDialogOption(
                    key = "cancel",
                    title = "Keep user",
                    onClick = { pendingDelete = null },
                ),
            ),
            onDismiss = { pendingDelete = null },
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AddUserRow(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(shape = RoundedCornerShape(16.dp)),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 960.dp)
            .height(36.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.PersonAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "Add user",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun UserRow(user: AdminUser, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(shape = RoundedCornerShape(16.dp)),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 960.dp)
            .height(48.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = user.username,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                user.lastActiveAt?.let {
                    Text(
                        text = "Last active $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = roleDisplayName(user.role),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (user.role == "admin") {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = if (user.enabled) "Enabled" else "Disabled",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (user.enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }
    }
}
