package com.continuum.app.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.continuum.app.android.ui.components.ContinuumTopBar
import org.koin.compose.viewmodel.koinViewModel

/**
 * Main settings screen organized in grouped sections.
 *
 * This screen is used as tab content within MainScreen (Settings tab)
 * and does NOT have its own top bar back button since it's a tab root.
 *
 * @param onLoggedOut Called after the user signs out.
 * @param showTopBar Whether to show the top bar (false when inside MainScreen tab).
 * @param onBackClick Back navigation handler for standalone mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLoggedOut: () -> Unit,
    onNavigateToServers: () -> Unit = {},
    onPairDevice: () -> Unit = {},
    onNavigateToRequests: () -> Unit = {},
    onNavigateToAdmin: () -> Unit = {},
    showTopBar: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val sessionsSheetState = rememberModalBottomSheetState()

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) {
            viewModel.onLogoutConsumed()
            onLoggedOut()
        }
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                ContinuumTopBar(
                    title = "Settings",
                    onBackClick = onBackClick,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                if (!showTopBar) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }

            item {
                AccountSection(
                    user = state.user,
                    isLoadingUser = state.isLoadingUser,
                    isAdminVisible = state.isAdminVisible,
                    onManageSessions = viewModel::loadSessions,
                    onPairDevice = onPairDevice,
                    onRequests = onNavigateToRequests,
                    onAdmin = onNavigateToAdmin,
                    onSignOut = viewModel::logout,
                )
            }

            item {
                AppearanceSettings(
                    currentTheme = state.theme,
                    onThemeChanged = viewModel::setTheme,
                )
            }

            item {
                PlaybackSettings(
                    defaultQuality = state.defaultQuality,
                    audioLanguage = state.audioLanguage,
                    autoSkipIntro = state.autoSkipIntro,
                    autoSkipCredits = state.autoSkipCredits,
                    onQualityChanged = viewModel::setDefaultQuality,
                    onAudioLanguageChanged = viewModel::setAudioLanguage,
                    onAutoSkipIntroChanged = viewModel::setAutoSkipIntro,
                    onAutoSkipCreditsChanged = viewModel::setAutoSkipCredits,
                    onResetPlaybackOverrides = viewModel::resetPlaybackOverrides,
                )
            }

            item {
                SubtitleSettings(
                    subtitleLanguage = state.subtitleLanguage,
                    subtitleMode = state.subtitleMode,
                    showForcedSubtitles = state.showForcedSubtitles,
                    onLanguageChanged = viewModel::setSubtitleLanguage,
                    onModeChanged = viewModel::setSubtitleMode,
                    onForcedSubtitlesChanged = viewModel::setShowForcedSubtitles,
                )
            }

            if (state.notificationsAvailable) {
                item {
                    SettingsSectionCard {
                        SettingsSectionHeader(title = "Notifications")
                        SettingsSwitchRow(
                            label = "In-app notifications",
                            checked = state.notificationsEnabled,
                            onCheckedChange = viewModel::setNotificationsEnabled,
                        )
                        if (state.notificationsEnabled) {
                            SettingsSwitchRow(
                                label = "Favorites",
                                checked = state.notifyFavorites,
                                onCheckedChange = viewModel::setNotifyFavorites,
                            )
                            SettingsSwitchRow(
                                label = "Watchlist",
                                checked = state.notifyWatchlist,
                                onCheckedChange = viewModel::setNotifyWatchlist,
                            )
                            SettingsSwitchRow(
                                label = "Continue watching",
                                checked = state.notifyContinueWatching,
                                onCheckedChange = viewModel::setNotifyContinueWatching,
                            )
                            SettingsSwitchRow(
                                label = "Next up",
                                checked = state.notifyNextUp,
                                onCheckedChange = viewModel::setNotifyNextUp,
                            )
                        }
                    }
                }
            }

            item {
                SettingsSectionCard {
                    SettingsSectionHeader(title = "Downloads")
                    SettingsSwitchRow(
                        label = "Wi-Fi only",
                        checked = state.downloadsWifiOnly,
                        onCheckedChange = viewModel::setDownloadsWifiOnly,
                    )
                }
            }

            item {
                ServerInfoSection(
                    serverUrl = state.serverUrl,
                    onManageServersClick = onNavigateToServers,
                )
            }

            // Bottom spacing
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    // Sessions bottom sheet
    if (state.showSessions) {
        SessionsSheet(
            sheetState = sessionsSheetState,
            sessions = state.sessions,
            isLoading = state.isLoadingSessions,
            onRevokeSession = viewModel::revokeSession,
            onDismiss = viewModel::hideSessions,
        )
    }
}

// --- Shared Settings UI Components ---

/**
 * Card container for a settings section with a subtle surface background.
 */
@Composable
fun SettingsSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(vertical = 8.dp),
        content = content,
    )
}

/**
 * Section header text within a settings card.
 */
@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * Generic settings row with a label and a trailing content slot.
 */
@Composable
fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/**
 * Settings row with a switch toggle.
 */
@Composable
fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsRow(label = label, modifier = modifier) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

/**
 * Clickable row with an icon and label, used for action items like "Sign Out".
 */
@Composable
fun SettingsClickableRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = labelColor,
        )
    }
}
