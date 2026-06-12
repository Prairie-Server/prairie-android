package com.continuum.app.tv.ui.screens.audiobook

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Right-aligned full-screen overlay panel over a dimming scrim, shared by the
 * chapters / speed / sleep audiobook panels (spec §4.9 — focusable overlays,
 * not phone bottom sheets). Back is handled by the host screen.
 */
@Composable
internal fun TvAudiobookOverlayScaffold(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(520.dp)
                .background(Color(0xFF101010))
                .padding(horizontal = 28.dp, vertical = 32.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            content()
        }
    }
}

/**
 * Focusable D-pad row used inside the audiobook overlay panels. Select fires
 * [onSelect]; the current selection is tinted, the focused row inverts. An
 * optional [trailing] string (e.g. a chapter timestamp) is right-aligned.
 */
@Composable
internal fun TvAudiobookOverlayRow(
    label: String,
    isCurrent: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val bg = when {
        isFocused -> Color.White
        isCurrent -> Color.White.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val fg = if (isFocused) Color.Black else Color.White
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { onSelect(); true }
                    else -> false
                }
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodyMedium,
                color = fg.copy(alpha = 0.7f),
            )
        }
    }
}

/** H:MM:SS / M:SS clock used across the audiobook TV surfaces. */
internal fun formatAudiobookTime(seconds: Double): String {
    if (seconds.isNaN() || seconds < 0.0) return "0:00"
    val total = seconds.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
