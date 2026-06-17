package com.continuum.app.tv.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.continuum.app.model.settings.SubtitleAppearance
import com.continuum.app.model.settings.SubtitleBackgroundStylePreset
import com.continuum.app.model.settings.SubtitleFontSizePreset
import com.continuum.app.model.settings.SubtitlePositionPreset

/**
 * In-player subtitle-style editor for TV — the 10-foot equivalent of the phone
 * `SubtitleStyleSheet`. Edits the per-profile [SubtitleAppearance]; changes are
 * applied live by `SubtitleManager.applyAppearance` (the screen re-applies on
 * every appearance emission). All controls are D-pad-friendly click-committed
 * chips / swatches.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSubtitleStyleDialog(
    appearance: SubtitleAppearance,
    onUpdate: (SubtitleAppearance) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Capture focus into the panel on open so D-pad works and the panel's own
    // Back/Escape handler fires (the menu row that opened this is now gone).
    val panelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(50)
        runCatching { panelFocus.requestFocus() }
    }
    Box(
        modifier = modifier
            .fillMaxWidth(0.42f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(topStart = 30.dp, bottomStart = 30.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(topStart = 30.dp, bottomStart = 30.dp),
            )
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyUp && (ev.key == Key.Back || ev.key == Key.Escape)) {
                    onDismiss(); true
                } else {
                    false
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .focusRequester(panelFocus)
                .focusGroup()
                .padding(horizontal = 28.dp, vertical = 34.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Subtitle style",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            StyleSection("Text size") {
                FONT_SIZES.forEach { (preset, label) ->
                    StyleChip(label, appearance.fontSize == preset) {
                        onUpdate(appearance.copy(fontSize = preset))
                    }
                }
            }
            StyleSection("Font") {
                FONT_FAMILIES.forEach { (value, label) ->
                    StyleChip(label, appearance.fontFamily == value) {
                        onUpdate(appearance.copy(fontFamily = value))
                    }
                }
            }
            StyleSection("Text color") {
                TEXT_COLOR_SWATCHES.forEach { hex ->
                    ColorSwatch(hex, appearance.fontColor.equals(hex, ignoreCase = true)) {
                        onUpdate(appearance.copy(fontColor = hex))
                    }
                }
            }
            StyleSection("Background") {
                BACKGROUND_STYLES.forEach { (preset, label) ->
                    StyleChip(label, appearance.backgroundStyle == preset) {
                        onUpdate(appearance.copy(backgroundStyle = preset))
                    }
                }
            }
            StyleSection("Background color") {
                BACKGROUND_COLOR_SWATCHES.forEach { hex ->
                    ColorSwatch(hex, appearance.backgroundColor.equals(hex, ignoreCase = true)) {
                        onUpdate(appearance.copy(backgroundColor = hex))
                    }
                }
            }
            StyleSection("Background opacity") {
                OPACITY_STEPS.forEach { pct ->
                    StyleChip("$pct%", appearance.backgroundOpacity == pct) {
                        onUpdate(appearance.copy(backgroundOpacity = pct))
                    }
                }
            }
            StyleSection("Outline") {
                StyleChip("On", appearance.textOutline) { onUpdate(appearance.copy(textOutline = true)) }
                StyleChip("Off", !appearance.textOutline) { onUpdate(appearance.copy(textOutline = false)) }
            }
            if (appearance.textOutline) {
                StyleSection("Outline color") {
                    TEXT_COLOR_SWATCHES.forEach { hex ->
                        ColorSwatch(hex, appearance.textOutlineColor.equals(hex, ignoreCase = true)) {
                            onUpdate(appearance.copy(textOutlineColor = hex))
                        }
                    }
                }
            }
            StyleSection("Position") {
                POSITIONS.forEach { (preset, label) ->
                    StyleChip(label, appearance.position == preset) {
                        onUpdate(appearance.copy(position = preset))
                    }
                }
            }
        }
    }
}

@Composable
private fun StyleSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

@Composable
private fun StyleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val bg = when {
        isFocused -> Color.White.copy(alpha = 0.94f)
        selected -> Color.White.copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.06f)
    }
    val fg = when {
        isFocused -> Color.Black
        selected -> Color.White
        else -> Color.White.copy(alpha = 0.72f)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun ColorSwatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val ring = when {
        isFocused -> Color.White
        selected -> Color.White.copy(alpha = 0.85f)
        else -> Color.White.copy(alpha = 0.25f)
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(hexToColor(hex))
            .border(width = if (isFocused || selected) 3.dp else 1.dp, color = ring, shape = CircleShape)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
    )
}

private fun hexToColor(hex: String): Color = try {
    val cleaned = if (hex.startsWith("#")) hex.drop(1) else hex
    Color(0xFF000000.toInt() or (cleaned.toLong(16).toInt() and 0x00FFFFFF))
} catch (_: NumberFormatException) {
    Color.White
}

private val FONT_SIZES = listOf(
    SubtitleFontSizePreset.Small to "Small",
    SubtitleFontSizePreset.Medium to "Medium",
    SubtitleFontSizePreset.Large to "Large",
    SubtitleFontSizePreset.XLarge to "X-Large",
    SubtitleFontSizePreset.XXLarge to "XX-Large",
)
private val FONT_FAMILIES = listOf(
    SubtitleAppearance.SANS_SERIF to "Sans-serif",
    SubtitleAppearance.SERIF to "Serif",
    SubtitleAppearance.MONOSPACE to "Monospace",
)
private val BACKGROUND_STYLES = listOf(
    SubtitleBackgroundStylePreset.Box to "Box",
    SubtitleBackgroundStylePreset.Shadow to "Shadow",
    SubtitleBackgroundStylePreset.Outline to "Outline",
    SubtitleBackgroundStylePreset.None to "None",
)
private val POSITIONS = listOf(
    SubtitlePositionPreset.Bottom to "Bottom",
    SubtitlePositionPreset.LowerThird to "Lower Third",
    SubtitlePositionPreset.Top to "Top",
)
private val OPACITY_STEPS = listOf(0, 25, 50, 75, 100)
private val TEXT_COLOR_SWATCHES = listOf(
    "#ffffff", "#facc15", "#22c55e", "#06b6d4", "#d946ef", "#ef4444", "#3b82f6", "#000000",
)
private val BACKGROUND_COLOR_SWATCHES = listOf(
    "#000000", "#374151", "#1e3a5f", "#7f1d1d", "#14532d",
)
