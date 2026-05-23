package com.continuum.app.tv.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Compose overlay for the TV player. Fades in/out on the `visible` flag so
 * the screen owns the auto-hide timer — the controls themselves are purely
 * declarative.
 *
 * Layout:
 *
 *   ┌───────────────────────────────────────────────┐
 *   │ ← Title                              [CC] [♪] │   top bar
 *   │                                               │
 *   │                                               │
 *   │            ⏪      ▶/⏸      ⏩                 │   center transport
 *   │                                               │
 *   │   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━      │   progress bar
 *   │   0:23                         1h 52m         │   time labels
 *   └───────────────────────────────────────────────┘
 *
 * No drag scrubbing — following tvOS parity, seeking is skip-button only.
 * The play/pause button is auto-focused on show so the user can immediately
 * press OK to toggle playback.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvPlayerControls(
    title: String,
    positionSec: Double,
    durationSec: Double,
    isPaused: Boolean,
    visible: Boolean,
    hasSubtitles: Boolean,
    hasAudioTracks: Boolean,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onBack: () -> Unit,
    onSubtitleMenu: () -> Unit,
    onAudioMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playPauseFocus = remember { FocusRequester() }
    LaunchedEffect(visible) {
        if (visible) runCatching { playPauseFocus.requestFocus() }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.6f),
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.7f),
                    ),
                ),
        ) {
            TopBar(
                title = title,
                hasSubtitles = hasSubtitles,
                hasAudioTracks = hasAudioTracks,
                onBack = onBack,
                onSubtitleMenu = onSubtitleMenu,
                onAudioMenu = onAudioMenu,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            TransportRow(
                isPaused = isPaused,
                onPlayPause = onPlayPause,
                onSkipBack = onSkipBack,
                onSkipForward = onSkipForward,
                playPauseFocus = playPauseFocus,
                modifier = Modifier.align(Alignment.Center),
            )

            ProgressBar(
                positionSec = positionSec,
                durationSec = durationSec,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 80.dp, vertical = 64.dp),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TopBar(
    title: String,
    hasSubtitles: Boolean,
    hasAudioTracks: Boolean,
    onBack: () -> Unit,
    onSubtitleMenu: () -> Unit,
    onAudioMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        if (hasSubtitles) {
            IconButton(onClick = onSubtitleMenu, modifier = Modifier.size(56.dp)) {
                Icon(
                    imageVector = Icons.Filled.Subtitles,
                    contentDescription = "Subtitles",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        if (hasAudioTracks) {
            IconButton(onClick = onAudioMenu, modifier = Modifier.size(56.dp)) {
                Icon(
                    imageVector = Icons.Filled.Audiotrack,
                    contentDescription = "Audio",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TransportRow(
    isPaused: Boolean,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    playPauseFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportButton(
            icon = Icons.Filled.Replay10,
            description = "Skip back 10 seconds",
            onClick = onSkipBack,
            size = 72.dp,
        )
        TransportButton(
            icon = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
            description = if (isPaused) "Play" else "Pause",
            onClick = onPlayPause,
            size = 96.dp,
            modifier = Modifier.focusRequester(playPauseFocus),
        )
        TransportButton(
            icon = Icons.Filled.Forward10,
            description = "Skip forward 10 seconds",
            onClick = onSkipForward,
            size = 72.dp,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(Color.Black.copy(alpha = 0.35f), CircleShape),
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = Color.White,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProgressBar(
    positionSec: Double,
    durationSec: Double,
    modifier: Modifier = Modifier,
) {
    val progress = if (durationSec > 0) {
        (positionSec / durationSec).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(modifier = modifier) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.White.copy(alpha = 0.3f),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatSeconds(positionSec),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
            Text(
                text = formatSeconds(durationSec),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
    }
}

/** h:mm:ss (or m:ss for sub-hour) formatter for the progress labels. */
private fun formatSeconds(sec: Double): String {
    if (sec < 0) return "0:00"
    val total = sec.toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%d:%02d".format(m, s)
    }
}

