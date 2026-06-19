package com.continuum.app.android.ui.screens.audiobook

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Five-control audiobook transport: prev-chapter · skip-back · play/pause ·
 * skip-forward · next-chapter. Chapter buttons are hidden (not just disabled)
 * when the book has no chapters, so the layout collapses to the classic
 * skip-back / play / skip-forward triple.
 */
@Composable
fun AudiobookTransport(
    isPlaying: Boolean,
    enabled: Boolean,
    hasChapters: Boolean,
    skipBackSeconds: Int,
    skipForwardSeconds: Int,
    onPrevChapter: () -> Unit,
    onSkipBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSkipForward: () -> Unit,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // iOS AudioTransportControls: HStack spacing 28, onSurface-tinted glyphs;
    // outer chapter buttons ~24dp (.title3), skip buttons ~32dp (.title), and
    // an 82dp accent play circle with a 32dp heavy glyph.
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasChapters) {
            IconButton(
                onClick = onPrevChapter,
                enabled = enabled,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Previous Chapter",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        IconButton(
            onClick = onSkipBack,
            enabled = enabled,
            modifier = Modifier.size(50.dp),
        ) {
            Icon(
                imageVector = skipBackIcon(skipBackSeconds),
                contentDescription = "Back $skipBackSeconds seconds",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(32.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(82.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                )
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(enabled = enabled, onClick = onTogglePlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(32.dp),
            )
        }
        IconButton(
            onClick = onSkipForward,
            enabled = enabled,
            modifier = Modifier.size(50.dp),
        ) {
            Icon(
                imageVector = skipForwardIcon(skipForwardSeconds),
                contentDescription = "Forward $skipForwardSeconds seconds",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(32.dp),
            )
        }
        if (hasChapters) {
            IconButton(
                onClick = onNextChapter,
                enabled = enabled,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Next Chapter",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * Best matching glyph for the configured skip-back interval. Material ships
 * Replay10 / Replay30 numbered icons; other configured intervals (15 / 60s)
 * fall back to the generic rewind glyph — the exact value is still conveyed by
 * the button's content description and the secondary-bar skip chip.
 */
private fun skipBackIcon(seconds: Int): ImageVector = when (seconds) {
    10 -> Icons.Filled.Replay10
    30 -> Icons.Filled.Replay30
    else -> Icons.Filled.FastRewind
}

private fun skipForwardIcon(seconds: Int): ImageVector = when (seconds) {
    10 -> Icons.Filled.Forward10
    30 -> Icons.Filled.Forward30
    else -> Icons.Filled.FastForward
}
