package com.continuum.app.android.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Transport controls overlay for the video player. Top-bar icon layout
 * mirrors iOS phone's `MobilePlayerControls` (lock | chapters | tracks |
 * settings) — see `iosApp/Screens/Player/iOS/MobilePlayerControls.swift:73`.
 *
 * Three-row layout:
 * - Top: Back (chevron) · title · orientation lock toggle · chapters (when
 *   present) · tracks (audio + subs) · settings (gear)
 * - Center: Skip back · play/pause · skip forward
 * - Bottom: Seek bar with timestamps
 */
@Composable
fun PlayerControls(
    title: String,
    subtitle: String,
    isPlaying: Boolean,
    isPaused: Boolean,
    position: Double,
    duration: Double,
    hasChapters: Boolean,
    hasTracks: Boolean,
    isOrientationLocked: Boolean,
    // Watch Together guest gate: when false the scrubber + skip buttons are
    // inert and dimmed (seek is host-only, so disabled for all guests).
    // Defaults true for solo playback.
    seekEnabled: Boolean = true,
    // Separate from [seekEnabled]: a guest under guest_play_pause keeps the
    // play/pause affordance but loses seek. Defaults true for solo playback.
    playPauseEnabled: Boolean = true,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Double) -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onToggleOrientationLock: () -> Unit,
    onOpenChapters: () -> Unit,
    onOpenTracks: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        // Top gradient + header bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent),
                    )
                )
                .padding(top = 8.dp, start = 4.dp, end = 4.dp, bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Orientation lock toggle — mirrors iOS `lock.fill` / `lock.open`.
                IconButton(onClick = onToggleOrientationLock) {
                    Icon(
                        imageVector = if (isOrientationLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (isOrientationLocked) "Landscape Locked" else "Rotate Freely",
                        tint = Color.White,
                    )
                }

                // Chapters — only shown when the file has chapters (iOS parity).
                if (hasChapters) {
                    IconButton(onClick = onOpenChapters) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "Chapters",
                            tint = Color.White,
                        )
                    }
                }

                // Tracks (audio + subtitles) — iOS uses `captions.bubble`. Dimmed
                // when there's nothing to pick.
                IconButton(
                    onClick = onOpenTracks,
                    enabled = hasTracks,
                ) {
                    Icon(
                        imageVector = Icons.Default.ClosedCaption,
                        contentDescription = "Audio and subtitles",
                        tint = if (hasTracks) Color.White else Color.White.copy(alpha = 0.3f),
                    )
                }

                // Settings — iOS `gearshape`, opens the playback settings sheet.
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Playback settings",
                        tint = Color.White,
                    )
                }
            }
        }

        // Center controls: spacer pushes them to the middle
        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onSkipBackward,
                enabled = seekEnabled,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Replay10,
                    contentDescription = "Skip back 10 seconds",
                    tint = if (seekEnabled) Color.White else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(36.dp),
                )
            }

            Spacer(modifier = Modifier.width(32.dp))

            IconButton(
                onClick = onPlayPause,
                enabled = playPauseEnabled,
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape),
            ) {
                Icon(
                    imageVector = if (isPaused || !isPlaying) {
                        Icons.Default.PlayArrow
                    } else {
                        Icons.Default.Pause
                    },
                    contentDescription = if (isPaused || !isPlaying) "Play" else "Pause",
                    tint = if (playPauseEnabled) Color.White else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(48.dp),
                )
            }

            Spacer(modifier = Modifier.width(32.dp))

            IconButton(
                onClick = onSkipForward,
                enabled = seekEnabled,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Forward10,
                    contentDescription = "Skip forward 10 seconds",
                    tint = if (seekEnabled) Color.White else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom gradient + seek bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                    )
                )
                .padding(bottom = 16.dp, start = 8.dp, end = 8.dp, top = 24.dp),
        ) {
            PlayerProgressBar(
                position = position,
                duration = duration,
                onSeek = onSeek,
                enabled = seekEnabled,
            )
        }
    }
}
