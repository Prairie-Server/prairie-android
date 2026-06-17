package com.continuum.app.tv.ui.screens.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.continuum.app.tv.data.preferences.PlaybackQuality
import com.continuum.app.tv.data.preferences.SubtitleMode
import com.continuum.app.tv.data.preferences.SubtitleSize
import com.continuum.app.tv.ui.components.TvFilterChip
import com.continuum.app.tv.ui.shell.TvTopMenuLayout
import com.continuum.app.tv.ui.theme.Spacing
import org.koin.compose.viewmodel.koinViewModel

/**
 * TV Settings screen. Sectioned list of settings groups:
 *
 * - Account: username, switch profile, sign out
 * - Playback: quality picker, auto-play next, auto-skip intro/credits
 * - Subtitles: default language + size
 * - Library shortcuts: Favorites, Watchlist, History, Collections
 * - Server: current URL (read-only)
 * - About: version
 *
 * Navigation actions (sign out, switch profile, library shortcuts)
 * are forwarded via callbacks so the Settings screen doesn't need direct
 * access to the top-level NavController.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSettingsScreen(
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToWatchlist: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToCollections: () -> Unit = {},
    onNavigateToBrowse: () -> Unit = {},
    onNavigateToRequests: () -> Unit = {},
    onNavigateToAdmin: () -> Unit = {},
    onSignedOut: () -> Unit = {},
    onSwitchProfile: () -> Unit = {},
    onInitialContentFocus: () -> Unit = {},
    viewModel: TvSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val firstActionFocusRequester = androidx.compose.runtime.remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { firstActionFocusRequester.requestFocus() }
        onInitialContentFocus()
    }

    LaunchedEffect(state.navAction) {
        when (state.navAction) {
            TvSettingsViewModel.NavAction.SIGNED_OUT -> {
                viewModel.onNavActionConsumed()
                onSignedOut()
            }
            TvSettingsViewModel.NavAction.SWITCH_PROFILE -> {
                viewModel.onNavActionConsumed()
                onSwitchProfile()
            }
            null -> Unit
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = 72.dp,
            top = TvTopMenuLayout.contentTopInset,
            end = 72.dp,
            bottom = Spacing.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        item {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = "YOUR PREFERENCES",
                    style = com.continuum.app.tv.ui.theme.sectionEyebrow,
                    color = com.continuum.app.tv.ui.theme.ContinuumBlue.copy(alpha = 0.92f),
                )
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        item {
            AccountSection(
                state = state,
                onSwitchProfile = { viewModel.onSwitchProfile(context) },
                onSignOut = { viewModel.onSignOut(context) },
                firstActionFocusRequester = firstActionFocusRequester,
            )
        }

        item {
            PlaybackSection(
                state = state,
                onQualityChanged = viewModel::onPlaybackQualityChanged,
                onAutoPlayNextChanged = viewModel::onAutoPlayNextChanged,
                onAutoSkipIntroChanged = viewModel::onAutoSkipIntroChanged,
                onAutoSkipCreditsChanged = viewModel::onAutoSkipCreditsChanged,
                onAudioLanguageChanged = viewModel::onAudioLanguageChanged,
                onResetPlaybackOverrides = viewModel::resetPlaybackOverrides,
            )
        }

        item {
            SubtitleSection(
                state = state,
                onSubtitleModeChanged = viewModel::onSubtitleModeChanged,
                onSubtitleLanguageChanged = viewModel::onSubtitleLanguageChanged,
                onSubtitleSizeChanged = viewModel::onSubtitleSizeChanged,
                onShowForcedSubtitlesChanged = viewModel::onShowForcedSubtitlesChanged,
            )
        }

        if (state.notificationsVisible) {
            item {
                NotificationsSection(
                    state = state,
                    onNotificationsEnabledChanged = viewModel::onNotificationsEnabledChanged,
                    onNotifyFavoritesChanged = viewModel::onNotifyFavoritesChanged,
                    onNotifyWatchlistChanged = viewModel::onNotifyWatchlistChanged,
                    onNotifyContinueWatchingChanged = viewModel::onNotifyContinueWatchingChanged,
                    onNotifyNextUpChanged = viewModel::onNotifyNextUpChanged,
                )
            }
        }

        if (state.adminVisible) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(title = "Admin")
                    SettingsRowAction(label = "Admin Dashboard", onClick = onNavigateToAdmin)
                }
            }
        }

        item {
            LibraryShortcutsSection(
                onNavigateToBrowse = onNavigateToBrowse,
                onNavigateToFavorites = onNavigateToFavorites,
                onNavigateToWatchlist = onNavigateToWatchlist,
                onNavigateToHistory = onNavigateToHistory,
                onNavigateToCollections = onNavigateToCollections,
                onNavigateToRequests = onNavigateToRequests,
            )
        }

        item {
            ServerSection(serverUrl = state.serverUrl)
        }

        item {
            AboutSection()
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AccountSection(
    state: TvSettingsViewModel.UiState,
    onSwitchProfile: () -> Unit,
    onSignOut: () -> Unit,
    firstActionFocusRequester: FocusRequester? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "Account")
        val username = state.user?.username ?: "—"
        SettingsRowInfo(label = "Signed in as", value = username)
        state.user?.email?.takeIf { it.isNotBlank() }?.let {
            SettingsRowInfo(label = "Email", value = it)
        }
        state.user?.role?.takeIf { it.isNotBlank() }?.let {
            SettingsRowInfo(label = "Role", value = it.replaceFirstChar { c -> c.uppercase() })
        }
        SettingsRowAction(
            label = "Switch profile",
            onClick = onSwitchProfile,
            focusRequester = firstActionFocusRequester,
        )
        SettingsRowAction(label = "Sign out", onClick = onSignOut)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlaybackSection(
    state: TvSettingsViewModel.UiState,
    onQualityChanged: (PlaybackQuality) -> Unit,
    onAutoPlayNextChanged: (Boolean) -> Unit,
    onAutoSkipIntroChanged: (Boolean) -> Unit,
    onAutoSkipCreditsChanged: (Boolean) -> Unit,
    onAudioLanguageChanged: (String) -> Unit,
    onResetPlaybackOverrides: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(title = "Playback")

        Text(
            text = "Quality",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(PlaybackQuality.values().toList(), key = { it.name }) { q ->
                TvFilterChip(
                    text = q.label,
                    selected = state.playbackQuality == q,
                    onClick = { onQualityChanged(q) },
                )
            }
        }

        Text(
            text = "Audio Language",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(AudioLanguages, key = { it.first.ifEmpty { "default" } }) { (wire, label) ->
                TvFilterChip(
                    text = label,
                    selected = state.audioLanguage == wire,
                    onClick = { onAudioLanguageChanged(wire) },
                )
            }
        }

        SettingsRowToggle(
            label = "Auto-Play Next Episode",
            checked = state.autoPlayNext,
            onCheckedChange = onAutoPlayNextChanged,
        )
        SettingsRowToggle(
            label = "Auto-Skip Intros",
            checked = state.autoSkipIntro,
            onCheckedChange = onAutoSkipIntroChanged,
        )
        SettingsRowToggle(
            label = "Auto-Skip Credits",
            checked = state.autoSkipCredits,
            onCheckedChange = onAutoSkipCreditsChanged,
        )

        SettingsRowAction(
            label = "Reset Playback Overrides",
            onClick = onResetPlaybackOverrides,
        )
    }
}

// Audio-language options mirror the phone: the stored value IS the display
// name (Default => "" locally), persisted to playerSettingsStore.audioLanguage.
private val AudioLanguages = listOf(
    "" to "Default",
    "English" to "English",
    "Spanish" to "Spanish",
    "French" to "French",
    "German" to "German",
    "Japanese" to "Japanese",
    "Korean" to "Korean",
    "Chinese" to "Chinese",
    "Portuguese" to "Portuguese",
    "Italian" to "Italian",
    "Russian" to "Russian",
)

private val SubtitleLanguages = listOf(
    "" to "Off",
    "en" to "English",
    "es" to "Spanish",
    "fr" to "French",
    "de" to "German",
    "ja" to "Japanese",
    "ko" to "Korean",
    "zh" to "Chinese",
    "pt" to "Portuguese",
    "it" to "Italian",
    "ru" to "Russian",
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SubtitleSection(
    state: TvSettingsViewModel.UiState,
    onSubtitleModeChanged: (SubtitleMode) -> Unit,
    onSubtitleLanguageChanged: (String) -> Unit,
    onSubtitleSizeChanged: (SubtitleSize) -> Unit,
    onShowForcedSubtitlesChanged: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(title = "Subtitles")

        Text(
            text = "Mode",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(SubtitleMode.values().toList(), key = { it.name }) { m ->
                TvFilterChip(
                    text = m.label,
                    selected = state.subtitleMode == m,
                    onClick = { onSubtitleModeChanged(m) },
                )
            }
        }

        Text(
            text = "Language",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(SubtitleLanguages, key = { it.first }) { (wire, label) ->
                TvFilterChip(
                    text = label,
                    selected = state.subtitleLanguage == wire,
                    onClick = { onSubtitleLanguageChanged(wire) },
                )
            }
        }

        Text(
            text = "Size",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(SubtitleSize.values().toList(), key = { it.name }) { s ->
                TvFilterChip(
                    text = s.label,
                    selected = state.subtitleSize == s,
                    onClick = { onSubtitleSizeChanged(s) },
                )
            }
        }

        SettingsRowToggle(
            label = "Forced subtitles",
            checked = state.showForcedSubtitles,
            onCheckedChange = onShowForcedSubtitlesChanged,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NotificationsSection(
    state: TvSettingsViewModel.UiState,
    onNotificationsEnabledChanged: (Boolean) -> Unit,
    onNotifyFavoritesChanged: (Boolean) -> Unit,
    onNotifyWatchlistChanged: (Boolean) -> Unit,
    onNotifyContinueWatchingChanged: (Boolean) -> Unit,
    onNotifyNextUpChanged: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(title = "Notifications")
        SettingsRowToggle(
            label = "In-app notifications",
            checked = state.notificationsEnabled,
            onCheckedChange = onNotificationsEnabledChanged,
        )
        if (state.notificationsEnabled) {
            SettingsRowToggle(
                label = "Favorites",
                checked = state.notifyFavorites,
                onCheckedChange = onNotifyFavoritesChanged,
            )
            SettingsRowToggle(
                label = "Watchlist",
                checked = state.notifyWatchlist,
                onCheckedChange = onNotifyWatchlistChanged,
            )
            SettingsRowToggle(
                label = "Continue watching",
                checked = state.notifyContinueWatching,
                onCheckedChange = onNotifyContinueWatchingChanged,
            )
            SettingsRowToggle(
                label = "Next up",
                checked = state.notifyNextUp,
                onCheckedChange = onNotifyNextUpChanged,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LibraryShortcutsSection(
    onNavigateToBrowse: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToWatchlist: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToCollections: () -> Unit,
    onNavigateToRequests: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "Library")
        SettingsRowAction(label = "Browse", onClick = onNavigateToBrowse)
        SettingsRowAction(label = "Favorites", onClick = onNavigateToFavorites)
        SettingsRowAction(label = "Watchlist", onClick = onNavigateToWatchlist)
        SettingsRowAction(label = "Watch history", onClick = onNavigateToHistory)
        SettingsRowAction(label = "Collections", onClick = onNavigateToCollections)
        SettingsRowAction(label = "Requests", onClick = onNavigateToRequests)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ServerSection(serverUrl: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "Server")
        SettingsRowInfo(
            label = "Server URL",
            value = serverUrl.ifBlank { "Not set" },
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AboutSection() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "About")
        SettingsRowInfo(label = "Version", value = "0.1.0")
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsRowInfo(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 960.dp)
            .height(64.dp)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsRowAction(
    label: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .fillMaxWidth()
            .widthIn(max = 960.dp)
            .height(64.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsRowToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        onClick = { onCheckedChange(!checked) },
        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 960.dp)
            .height(64.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (checked) "On" else "Off",
                style = MaterialTheme.typography.titleMedium,
                color = if (checked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
