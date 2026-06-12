package com.continuum.app.tv.ui.screens.audiobook

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon

/**
 * Five-button audiobook transport, D-pad navigable. Mirrors the video player's
 * transport focus visuals (white↔black flip, 66 dp circles, KeyUp-driven
 * Select). Up/Down are left to Compose's default focus traversal so focus moves
 * naturally between this row and the secondary chips. Chapter buttons dim +
 * no-op when [chaptersEnabled] is false (single-chapter degrade, spec §8).
 */
@Composable
fun TvAudiobookTransportRow(
    isPlaying: Boolean,
    chaptersEnabled: Boolean,
    onPrevChapter: () -> Unit,
    onSkipBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipForward: () -> Unit,
    onNextChapter: () -> Unit,
    playPauseFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TransportIconButton(
            icon = Icons.Filled.SkipPrevious,
            description = "Previous chapter",
            enabled = chaptersEnabled,
            onClick = onPrevChapter,
        )
        TransportIconButton(
            icon = Icons.Filled.Replay30,
            description = "Skip back 30 seconds",
            onClick = onSkipBack,
        )
        TransportIconButton(
            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            description = if (isPlaying) "Pause" else "Play",
            isPrimary = true,
            focusRequester = playPauseFocus,
            onClick = onPlayPause,
        )
        TransportIconButton(
            icon = Icons.Filled.Forward30,
            description = "Skip forward 30 seconds",
            onClick = onSkipForward,
        )
        TransportIconButton(
            icon = Icons.Filled.SkipNext,
            description = "Next chapter",
            enabled = chaptersEnabled,
            onClick = onNextChapter,
        )
    }
}

@Composable
private fun TransportIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isPrimary: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val buttonSize = 66.dp
    val symbolSize = if (isPrimary) 30.dp else 25.dp
    val focusBg = if (isFocused) Color.White else Color.Black.copy(alpha = 0.35f)
    val iconTint = when {
        !enabled -> Color.White.copy(alpha = 0.30f)
        isFocused -> Color.Black
        else -> Color.White
    }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.025f else 1f,
        animationSpec = tween(120),
        label = "abTransportScale",
    )

    Box(
        modifier = Modifier
            .size(buttonSize)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(focusBg)
            .border(
                width = 1.dp,
                color = if (isFocused) Color.Transparent else Color.White.copy(alpha = 0.22f),
                shape = CircleShape,
            )
            .let { mod -> if (focusRequester != null) mod.focusRequester(focusRequester) else mod }
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (enabled) onClick()
                        true
                    }
                    else -> false
                }
            }
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(symbolSize),
        )
    }
}
