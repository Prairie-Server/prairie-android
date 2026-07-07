package org.siloserver.silo.android.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

private val subtitleLanguageOptions = listOf("Off", "English", "Spanish", "French", "German", "Japanese", "Korean", "Chinese", "Portuguese", "Italian", "Russian")

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

        if (metadataLanguageEnabled) {
            SettingsDropdownRow(
                label = "Metadata Language",
                value = metadataLanguage,
                options = subtitleLanguageOptions,
                onOptionSelected = onMetadataLanguageChanged,
            )
        }
    }
}
