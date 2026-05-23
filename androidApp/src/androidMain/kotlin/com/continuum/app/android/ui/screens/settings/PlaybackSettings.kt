package com.continuum.app.android.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val qualityOptions = listOf("Auto", "Original", "1080p", "720p", "480p")
private val languageOptions = listOf("Default", "English", "Spanish", "French", "German", "Japanese", "Korean", "Chinese", "Portuguese", "Italian", "Russian")

/**
 * Playback settings section with quality preference, audio language,
 * and auto-skip toggles.
 */
@Composable
fun PlaybackSettings(
    defaultQuality: String,
    audioLanguage: String,
    autoSkipIntro: Boolean,
    autoSkipCredits: Boolean,
    onQualityChanged: (String) -> Unit,
    onAudioLanguageChanged: (String) -> Unit,
    onAutoSkipIntroChanged: (Boolean) -> Unit,
    onAutoSkipCreditsChanged: (Boolean) -> Unit,
    onResetPlaybackOverrides: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSectionCard(modifier = modifier) {
        SettingsSectionHeader("Playback")

        SettingsDropdownRow(
            label = "Default Quality",
            value = defaultQuality,
            options = qualityOptions,
            onOptionSelected = onQualityChanged,
        )

        SettingsDropdownRow(
            label = "Audio Language",
            value = audioLanguage,
            options = languageOptions,
            onOptionSelected = onAudioLanguageChanged,
        )

        SettingsSwitchRow(
            label = "Auto-Skip Intros",
            checked = autoSkipIntro,
            onCheckedChange = onAutoSkipIntroChanged,
        )

        SettingsSwitchRow(
            label = "Auto-Skip Credits",
            checked = autoSkipCredits,
            onCheckedChange = onAutoSkipCreditsChanged,
        )

        SettingsActionRow(
            label = "Reset Playback Overrides",
            onClick = onResetPlaybackOverrides,
        )
    }
}

@Composable
private fun SettingsActionRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsRow(
        label = label,
        modifier = modifier.clickable(onClick = onClick),
    ) {}
}

/**
 * A settings row with a dropdown menu for selecting from a list of options.
 */
@Composable
fun SettingsDropdownRow(
    label: String,
    value: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        SettingsRow(
            label = label,
            modifier = Modifier.clickable { expanded = true },
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
