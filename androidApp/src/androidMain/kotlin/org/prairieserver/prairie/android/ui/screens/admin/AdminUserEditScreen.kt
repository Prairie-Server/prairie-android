package org.prairieserver.prairie.android.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import org.prairieserver.prairie.android.ui.components.PrairieTopBar
import org.prairieserver.prairie.android.ui.components.LoadingIndicator
import org.prairieserver.prairie.viewmodel.ADMIN_USER_ROLES
import org.prairieserver.prairie.viewmodel.AdminUserEditViewModel
import org.prairieserver.prairie.viewmodel.roleDisplayName
import org.koin.compose.viewmodel.koinViewModel

/**
 * Create (userId == null) / edit form for an admin user. On create, username /
 * email / password are required; on edit they are read-only except for an
 * optional password reset. Role, enabled, library access and playback quotas
 * are editable in both modes. Submits via [AdminUserEditViewModel], which pops
 * back through [onSaved] on success.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminUserEditScreen(
    userId: Int?,
    onBackClick: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AdminUserEditViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(userId) { viewModel.load(userId) }

    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) onSaved()
    }

    val isEdit = state.isEditMode

    Scaffold(
        topBar = {
            PrairieTopBar(
                title = if (isEdit) "Edit user" else "Create user",
                onBackClick = onBackClick,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.isLoading) {
            LoadingIndicator(Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text("Username") },
                singleLine = true,
                enabled = !isEdit,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text("Email") },
                singleLine = true,
                enabled = !isEdit,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text(if (isEdit) "Reset password (optional)" else "Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Role", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ADMIN_USER_ROLES.forEach { role ->
                    FilterChip(
                        selected = state.role == role,
                        onClick = { viewModel.onRoleChange(role) },
                        label = { Text(roleDisplayName(role)) },
                    )
                }
            }

            SwitchRow(
                label = "Enabled",
                checked = state.enabled,
                onChange = viewModel::onEnabledChange,
            )

            OutlinedTextField(
                value = state.libraryIdsText,
                onValueChange = viewModel::onLibraryIdsChange,
                label = { Text("Library access (comma-separated ids)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            QuotaField("Max streams", state.maxStreamsText, viewModel::onMaxStreamsChange)
            QuotaField("Max transcodes", state.maxTranscodesText, viewModel::onMaxTranscodesChange)
            QuotaField("Max profiles", state.maxProfilesText, viewModel::onMaxProfilesChange)

            SwitchRow(
                label = "Downloads allowed",
                checked = state.downloadAllowed,
                onChange = viewModel::onDownloadAllowedChange,
            )
            SwitchRow(
                label = "Download transcode allowed",
                checked = state.downloadTranscodeAllowed,
                onChange = viewModel::onDownloadTranscodeAllowedChange,
            )

            state.error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = viewModel::submit,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(if (isEdit) "Save changes" else "Create user")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QuotaField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("$label (blank = unlimited)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
