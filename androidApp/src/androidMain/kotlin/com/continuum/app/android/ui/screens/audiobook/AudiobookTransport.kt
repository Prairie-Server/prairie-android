package com.continuum.app.android.ui.screens.audiobook

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    onPrevChapter: () -> Unit,
    onSkipBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSkipForward: () -> Unit,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasChapters) {
            IconButton(
                onClick = onPrevChapter,
                enabled = enabled,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous chapter", modifier = Modifier.size(30.dp))
            }
            Spacer(modifier = Modifier.size(8.dp))
        }
        IconButton(
            onClick = onSkipBack,
            enabled = enabled,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(Icons.Filled.Replay30, contentDescription = "Back 30 seconds", modifier = Modifier.size(36.dp))
        }
        Spacer(modifier = Modifier.size(16.dp))
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(enabled = enabled, onClick = onTogglePlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(modifier = Modifier.size(16.dp))
        IconButton(
            onClick = onSkipForward,
            enabled = enabled,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(Icons.Filled.Forward30, contentDescription = "Forward 30 seconds", modifier = Modifier.size(36.dp))
        }
        if (hasChapters) {
            Spacer(modifier = Modifier.size(8.dp))
            IconButton(
                onClick = onNextChapter,
                enabled = enabled,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next chapter", modifier = Modifier.size(30.dp))
            }
        }
    }
}
