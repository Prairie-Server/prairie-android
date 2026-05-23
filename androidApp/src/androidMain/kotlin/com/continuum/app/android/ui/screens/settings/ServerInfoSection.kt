package com.continuum.app.android.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Settings section showing server connection details and management entry points.
 */
@Composable
fun ServerInfoSection(
    serverUrl: String,
    isAdmin: Boolean,
    onAdminClick: () -> Unit,
    onManageServersClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    SettingsSectionCard(modifier = modifier) {
        SettingsSectionHeader("Server")

        SettingsRow(label = "Server URL") {
            Text(
                text = serverUrl.ifBlank { "Not connected" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsClickableRow(
            icon = Icons.Default.Storage,
            label = "Manage Servers",
            onClick = onManageServersClick,
        )

        if (isAdmin) {
            SettingsClickableRow(
                icon = Icons.Default.AdminPanelSettings,
                label = "Admin Panel",
                onClick = onAdminClick,
                labelColor = MaterialTheme.colorScheme.primary,
                iconTint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
