package com.continuum.app.tv.ui.screens.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Border
import androidx.compose.foundation.BorderStroke
import androidx.tv.material3.Text
import com.continuum.app.tv.ui.components.TvAuroraBackdrop
import com.continuum.app.tv.ui.components.TvAuroraVariant
import com.continuum.app.tv.ui.components.TvDialogOption
import com.continuum.app.tv.ui.components.TvFilterChip
import com.continuum.app.tv.ui.components.TvHeroActionPill
import com.continuum.app.tv.ui.components.TvOptionDialog
import com.continuum.app.tv.ui.components.TvPillVariant
import com.continuum.app.tv.ui.components.tvOutlinedTextFieldColors
import com.continuum.app.tv.ui.theme.FocusedContainer
import com.continuum.app.tv.ui.theme.FocusedContent
import com.continuum.app.tv.ui.theme.Spacing

/**
 * Snapshot of the editable profile fields shared by create + edit. The screen
 * wrappers map their respective UiState into this so the form composable below
 * is identical for both flows (parity with the phone, which shares the same
 * form widgets between CreateProfileScreen and EditProfileScreen).
 */
data class TvProfileFormState(
    val title: String,
    val name: String,
    val selectedAvatar: String?,
    val isChild: Boolean,
    val maxContentRating: String?,
    val pinEnabled: Boolean,
    val pin: String,
    val qualityPreference: String?,
    val subtitleMode: String?,
    val pinHelper: String,
    val submitLabel: String,
    val isSubmitting: Boolean,
    val error: String?,
)

data class TvProfileFormCallbacks(
    val onNameChanged: (String) -> Unit,
    val onAvatarSelected: (String) -> Unit,
    val onChildToggled: (Boolean) -> Unit,
    val onContentRatingSelected: (String) -> Unit,
    val onPinToggled: (Boolean) -> Unit,
    val onPinChanged: (String) -> Unit,
    val onQualitySelected: (String) -> Unit,
    val onSubtitleModeSelected: (String) -> Unit,
    val onSubmit: () -> Unit,
)

/** Identifies which option picker dialog is currently open. */
private enum class TvProfilePicker { Rating, Quality, Subtitle }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvProfileForm(
    state: TvProfileFormState,
    callbacks: TvProfileFormCallbacks,
) {
    var activePicker by remember { mutableStateOf<TvProfilePicker?>(null) }
    val nameFocus = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        TvAuroraBackdrop(variant = TvAuroraVariant.Profile)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(420.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 48.dp, bottom = Spacing.xxl, start = Spacing.xl, end = Spacing.xl),
        ) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )

            // --- Avatar preview + picker ---
            ProfileAvatarPreview(
                avatar = state.selectedAvatar,
                name = state.name.ifBlank { "?" },
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(TV_AVATAR_EMOJIS, key = { it }) { emoji ->
                    AvatarPickerChip(
                        emoji = emoji,
                        selected = state.selectedAvatar == emoji,
                        onClick = { callbacks.onAvatarSelected(emoji) },
                    )
                }
            }

            // --- Name ---
            OutlinedTextField(
                value = state.name,
                onValueChange = callbacks.onNameChanged,
                label = { Text("Profile Name", color = Color.White) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(nameFocus),
                colors = tvOutlinedTextFieldColors(),
            )

            // --- Child toggle ---
            FormToggleRow(
                label = "Child Profile",
                checked = state.isChild,
                onToggle = callbacks.onChildToggled,
            )

            if (state.isChild) {
                FormPickerRow(
                    label = "Max Content Rating",
                    value = state.maxContentRating ?: "PG",
                    onClick = { activePicker = TvProfilePicker.Rating },
                )
            }

            // --- PIN ---
            FormToggleRow(
                label = "Require PIN",
                checked = state.pinEnabled,
                onToggle = callbacks.onPinToggled,
            )

            if (state.pinEnabled) {
                OutlinedTextField(
                    value = state.pin,
                    onValueChange = callbacks.onPinChanged,
                    label = { Text(state.pinHelper, color = Color.White) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    colors = tvOutlinedTextFieldColors(),
                )
            }

            // --- Quality ---
            FormPickerRow(
                label = "Quality Preference",
                value = state.qualityPreference ?: "Auto",
                onClick = { activePicker = TvProfilePicker.Quality },
            )

            // --- Subtitles ---
            FormPickerRow(
                label = "Subtitles",
                value = subtitleModeLabel(state.subtitleMode),
                onClick = { activePicker = TvProfilePicker.Subtitle },
            )

            if (state.error != null) {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            TvHeroActionPill(
                label = if (state.isSubmitting) "Saving…" else state.submitLabel,
                icon = Icons.Filled.Check,
                variant = TvPillVariant.Filled,
                onClick = { if (!state.isSubmitting) callbacks.onSubmit() },
            )
        }
    }

    // --- Option picker dialogs (TV-native replacement for the phone dropdowns) ---
    when (activePicker) {
        TvProfilePicker.Rating -> TvOptionDialog(
            title = "Max Content Rating",
            options = TV_CONTENT_RATINGS.map { rating ->
                TvDialogOption(
                    key = rating,
                    title = rating,
                    selected = (state.maxContentRating ?: "PG") == rating,
                    onClick = {
                        callbacks.onContentRatingSelected(rating)
                        activePicker = null
                    },
                )
            },
            onDismiss = { activePicker = null },
        )

        TvProfilePicker.Quality -> TvOptionDialog(
            title = "Quality Preference",
            options = TV_QUALITY_OPTIONS.map { quality ->
                TvDialogOption(
                    key = quality,
                    title = quality,
                    selected = (state.qualityPreference ?: "Auto") == quality,
                    onClick = {
                        callbacks.onQualitySelected(quality)
                        activePicker = null
                    },
                )
            },
            onDismiss = { activePicker = null },
        )

        TvProfilePicker.Subtitle -> TvOptionDialog(
            title = "Subtitles",
            options = TV_SUBTITLE_MODES.map { mode ->
                TvDialogOption(
                    key = mode,
                    title = mode,
                    selected = subtitleModeLabel(state.subtitleMode) == mode,
                    onClick = {
                        callbacks.onSubtitleModeSelected(mode)
                        activePicker = null
                    },
                )
            },
            onDismiss = { activePicker = null },
        )

        null -> Unit
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProfileAvatarPreview(avatar: String?, name: String) {
    val isEmoji = !avatar.isNullOrBlank()
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (isEmoji) avatar!! else initial,
            fontSize = if (isEmoji) 56.sp else 48.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.94f),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AvatarPickerChip(
    emoji: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = CircleShape
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f),
            contentColor = Color.White,
            focusedContainerColor = FocusedContainer,
            focusedContentColor = FocusedContent,
            pressedContainerColor = FocusedContainer,
            pressedContentColor = FocusedContent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(
                    width = if (selected) 2.dp else 0.dp,
                    color = if (selected) Color.White.copy(alpha = 0.9f) else Color.Transparent,
                ),
                shape = shape,
            ),
        ),
        modifier = Modifier.size(64.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = emoji, fontSize = 30.sp)
        }
    }
}

/** A label + on/off chip pair. Mirrors the phone's SwitchRow on a D-pad surface. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FormToggleRow(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TvFilterChip(text = "Off", selected = !checked, onClick = { onToggle(false) })
            TvFilterChip(text = "On", selected = checked, onClick = { onToggle(true) })
        }
    }
}

/** A label + current-value chip that opens an option dialog when activated. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FormPickerRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        TvHeroActionPill(
            label = value,
            variant = TvPillVariant.Hollow,
            onClick = onClick,
        )
    }
}
