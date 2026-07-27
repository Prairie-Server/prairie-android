package org.prairieserver.prairie.android.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.prairieserver.prairie.update.AppUpdateStatus
import org.prairieserver.prairie.update.latestVersionLabel
import org.prairieserver.prairie.update.releaseUrlOrNull
import org.prairieserver.prairie.update.statusLabel

/**
 * Connection + About section. Mirrors the iOS phone Settings `Server` row and
 * About version block: server management plus current version / update status.
 */
@Composable
fun ServerInfoSection(
    serverUrl: String,
    appVersionName: String,
    appUpdateStatus: AppUpdateStatus,
    onManageServersClick: () -> Unit = {},
    onUpdateClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val latest = appUpdateStatus.latestVersionLabel()
    val releaseUrl = appUpdateStatus.releaseUrlOrNull()
    SettingsSectionCard(modifier = modifier) {
        SettingsRowLabel(
            title = "Server",
            icon = Icons.Default.Dns,
            badgeColor = SettingsBadgeTeal,
            value = serverUrl.ifBlank { "Not connected" },
            onClick = onManageServersClick,
            showChevron = true,
        )
        SettingsRowLabel(
            title = "Version",
            icon = Icons.Default.Info,
            badgeColor = SettingsBadgeGray,
            value = appVersionName,
        )
        SettingsRowLabel(
            title = "Update status",
            icon = Icons.Default.SystemUpdate,
            badgeColor = if (appUpdateStatus is AppUpdateStatus.UpdateAvailable) {
                SettingsBadgeTeal
            } else {
                SettingsBadgeGray
            },
            value = appUpdateStatus.statusLabel(),
            onClick = releaseUrl?.let { url ->
                onUpdateClick?.let { handler -> { handler(url) } }
            },
            showChevron = releaseUrl != null && onUpdateClick != null,
        )
        if (latest != null &&
            appUpdateStatus !is AppUpdateStatus.Checking &&
            appUpdateStatus !is AppUpdateStatus.Unavailable
        ) {
            SettingsRowLabel(
                title = "Latest version",
                icon = Icons.Default.Info,
                badgeColor = SettingsBadgeGray,
                value = latest,
            )
        }
    }
}
