package com.continuum.app.tv.ui.screens.player

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Tune
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
 * Bottom transport row mirroring `iosApp/.../tvOS/TVPlayerTransportCluster.swift`.
 * Uniform circular buttons that flip white-on-black ↔ black-on-white when
 * focused; left/right cycles within the row, Up returns focus to the scrubber.
 *
 * The layout is a single row split into two clusters with `Spacer(weight=1)`
 * between — primary (skip back / play-pause / skip forward) on the left,
 * secondary (HUD options + close) on the right. Matches the spec's
 * "4–5 icon buttons, 24-32 dp gap, focus glow ring".
 */
@Composable
fun TvPlayerTransportCluster(
    isPlaying: Boolean,
    onSkipBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipForward: () -> Unit,
    onOpenHUD: () -> Unit,
    onClose: () -> Unit,
    playPauseFocus: FocusRequester,
    onMoveUpToScrubber: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TransportIconButton(
            icon = Icons.Filled.Replay10,
            description = "Skip back 10 seconds",
            onClick = onSkipBack,
            onMoveUp = onMoveUpToScrubber,
        )
        TransportIconButton(
            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            description = if (isPlaying) "Pause" else "Play",
            onClick = onPlayPause,
            isPrimary = true,
            focusRequester = playPauseFocus,
            onMoveUp = onMoveUpToScrubber,
        )
        TransportIconButton(
            icon = Icons.Filled.Forward10,
            description = "Skip forward 10 seconds",
            onClick = onSkipForward,
            onMoveUp = onMoveUpToScrubber,
        )
        Spacer(modifier = Modifier.width(24.dp))
        Spacer(modifier = Modifier.weight(1f))
        TransportIconButton(
            icon = Icons.Filled.Tune,
            description = "Info and options",
            onClick = onOpenHUD,
            onMoveUp = onMoveUpToScrubber,
        )
        TransportIconButton(
            icon = Icons.Filled.Close,
            description = "Close player",
            onClick = onClose,
            onMoveUp = onMoveUpToScrubber,
        )
    }
}

@Composable
private fun TransportIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    isPrimary: Boolean = false,
    focusRequester: FocusRequester? = null,
    onMoveUp: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val buttonSize = 66.dp
    val symbolSize = if (isPrimary) 30.dp else 25.dp

    val focusBg = if (isFocused) Color.White else Color.Black.copy(alpha = 0.35f)
    val iconTint = if (isFocused) Color.Black else Color.White

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.025f else 1f,
        animationSpec = tween(120),
        label = "transportScale",
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
                        onClick()
                        true
                    }
                    Key.DirectionUp -> {
                        onMoveUp()
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
