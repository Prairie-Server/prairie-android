package org.prairieserver.prairie.android.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import org.koin.compose.koinInject
import org.prairieserver.prairie.android.BuildConfig
import org.prairieserver.prairie.common.network.clientVersionLabel
import org.prairieserver.prairie.update.AppUpdateChecker
import org.prairieserver.prairie.update.AppUpdateStatus
import org.prairieserver.prairie.update.changelogUrlOrNull
import org.prairieserver.prairie.update.latestVersionLabel
import org.prairieserver.prairie.update.releaseUrlOrNull
import org.prairieserver.prairie.update.statusLabel

/**
 * Connection section. Mirrors the iOS phone Settings `Server` row: a
 * single teal-badged `server.rack` row whose trailing value is the
 * active server label, with a disclosure chevron that opens the server
 * list.
 */
@Composable
fun ServerInfoSection(
    serverUrl: String,
    onManageServersClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    SettingsSectionCard(modifier = modifier) {
        SettingsNavigationRow(
            label = "Server",
            description = "The Prairie server this device is signed in to.",
            value = serverUrl.ifBlank { "Not connected" },
            onClick = onManageServersClick,
        )
        SettingsNavigationRow(
            label = "Version",
            description = "The app build running on this device.",
            // Includes the build number so a support report and the server's
            // admin Activity page name the exact same build, in the "1.0.0 (5)"
            // form Play, TestFlight and the server's own diagnostics page all
            // use. Unstamped local builds show the bare version rather than a
            // meaningless "(0)", matching what those builds report.
            value = clientVersionLabel(BuildConfig.VERSION_NAME, BuildConfig.BUILD_NUMBER),
        )
        AppUpdateStatusRow()
    }
}

/**
 * Prairie-only (b3b04cbb): GitHub Releases update check for sideloaded builds.
 * Self-contained (checker from Koin, no ViewModel plumbing) so an upstream
 * rewrite of the settings ViewModel cannot silently drop it again.
 */
@Composable
private fun AppUpdateStatusRow() {
    val checker: AppUpdateChecker = koinInject()
    val uriHandler = LocalUriHandler.current
    val status by produceState<AppUpdateStatus>(AppUpdateStatus.Checking, checker) {
        value = checker.check(BuildConfig.VERSION_NAME)
    }
    val targetUrl = status.releaseUrlOrNull() ?: status.changelogUrlOrNull()
    val latest = status.latestVersionLabel()
    SettingsNavigationRow(
        label = "Updates",
        description = if (status is AppUpdateStatus.UpdateAvailable && latest != null) {
            "Prairie $latest is available."
        } else {
            "Checks Prairie releases on GitHub."
        },
        value = status.statusLabel(),
        onClick = targetUrl?.let { url -> { runCatching { uriHandler.openUri(url) } } },
    )
}
