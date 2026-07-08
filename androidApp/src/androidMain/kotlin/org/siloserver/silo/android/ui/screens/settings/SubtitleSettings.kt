package org.siloserver.silo.android.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.ui.Modifier

private val subtitleLanguageOptions = listOf("Off", "English", "Spanish", "French", "German", "Japanese", "Korean", "Chinese", "Portuguese", "Italian", "Russian")

// Metadata language stores ISO 639-1 codes (server contract; "" = inherit) —
// display labels, persist codes. Kept in lockstep with the TV list.
private val metadataLanguageOptions = listOf(
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

/**
 * Subtitle settings section with language, display mode, and forced subtitles toggle.
 */
@Composable
fun SubtitleSettings(
    subtitleLanguage: String,
    subtitleMode: SubtitleMode,
    showForcedSubtitles: Boolean,
    onLanguageChanged: (String) -> Unit,
    onModeChanged: (SubtitleMode) -> Unit,
    onForcedSubtitlesChanged: (Boolean) -> Unit,
    subtitleMatchesDevice: Boolean = false,
    onSubtitleMatchesDeviceChanged: (Boolean) -> Unit = {},
    onOpenSubtitleAppearance: () -> Unit = {},
    modifier: Modifier = Modifier,
    // Metadata AI description translation (server-gated; row hidden when off).
    metadataLanguageEnabled: Boolean = false,
    metadataLanguage: String = "Off",
    onMetadataLanguageChanged: (String) -> Unit = {},
) {
    SettingsSectionCard(modifier = modifier) {
        SettingsSectionHeader("Subtitles")

        SettingsDropdownRow(
            label = "Subtitle Language",
            value = subtitleLanguage,
            options = subtitleLanguageOptions,
            onOptionSelected = onLanguageChanged,
        )

        SettingsDropdownRow(
            label = "Subtitle Mode",
            value = subtitleMode.label,
            options = SubtitleMode.entries.map { it.label },
            onOptionSelected = { label ->
                SubtitleMode.entries.find { it.label == label }?.let(onModeChanged)
            },
        )

        SettingsSwitchRow(
            label = "Show Forced Subtitles",
            checked = showForcedSubtitles,
            onCheckedChange = onForcedSubtitlesChanged,
        )

        // iOS SubtitleSettingsView APPEARANCE parity: Match Device Settings
        // (appearance follows the OS captioning preferences) + the custom
        // appearance editor (the same sheet the player uses).
        SettingsSwitchRow(
            label = "Match Device Settings",
            checked = subtitleMatchesDevice,
            onCheckedChange = onSubtitleMatchesDeviceChanged,
        )
        if (!subtitleMatchesDevice) {
            SettingsClickableRow(
                icon = Icons.Filled.ClosedCaption,
                label = "Subtitle Appearance",
                onClick = onOpenSubtitleAppearance,
            )
        }

        if (metadataLanguageEnabled) {
            val selectedLabel = metadataLanguageOptions.firstOrNull { it.first == metadataLanguage }?.second ?: "Off"
            SettingsDropdownRow(
                label = "Metadata Language",
                value = selectedLabel,
                options = metadataLanguageOptions.map { it.second },
                onOptionSelected = { label ->
                    metadataLanguageOptions.firstOrNull { it.second == label }?.let {
                        onMetadataLanguageChanged(it.first)
                    }
                },
            )
        }
    }
}
