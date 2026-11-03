package org.prairieserver.prairie.android.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.prairieserver.prairie.common.settings.PlayerSettingsStore
import org.prairieserver.prairie.model.settings.PlaybackSettingsKeys

/**
 * "Theme music" and "Loop theme music" for title pages, as the web app's
 * device settings offer them. Each switch writes this device's value; one
 * set for the profile elsewhere applies until then.
 */
@Composable
internal fun ThemeMusicSettingsSection(store: PlayerSettingsStore) {
    val enabled by store.themeMusicEnabledFlow.collectAsState(initial = false)
    val loop by store.themeMusicLoopFlow.collectAsState(initial = false)
    val overrides by store.deviceOverrideKeysFlow.collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()
    fun overridden(key: String) = PlaybackSettingsKeys.hasDeviceOverride(overrides, key)

    SettingsSection(
        title = "Theme Music",
        footer = "Play a title's theme music while its page is open, when the server has one. " +
            "Loop repeats it until you leave the page.",
    ) {
        ProfileLayeredSwitchRow(
            label = "Theme Music",
            checked = enabled,
            overridden = overridden(PlaybackSettingsKeys.ThemeMusicEnabled),
            onCheckedChange = { scope.launch { store.setThemeMusicEnabled(it) } },
            onUseProfileSetting = { scope.launch { store.resetDeviceSetting(PlaybackSettingsKeys.ThemeMusicEnabled) } },
        )
        ProfileLayeredSwitchRow(
            label = "Loop Theme Music",
            checked = loop,
            overridden = overridden(PlaybackSettingsKeys.ThemeMusicLoop),
            onCheckedChange = { scope.launch { store.setThemeMusicLoop(it) } },
            onUseProfileSetting = { scope.launch { store.resetDeviceSetting(PlaybackSettingsKeys.ThemeMusicLoop) } },
        )
    }
}
