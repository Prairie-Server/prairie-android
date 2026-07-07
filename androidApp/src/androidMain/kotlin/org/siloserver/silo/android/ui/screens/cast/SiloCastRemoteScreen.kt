package org.siloserver.silo.android.ui.screens.cast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.siloserver.silo.android.cast.SiloCastController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiloCastRemoteScreen(
    onBack: () -> Unit,
    controller: SiloCastController = koinInject(),
) {
    val state by controller.state.collectAsState()
    val playback = state.playbackState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SiloCast") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { controller.disconnect() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Disconnect")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Cast, contentDescription = null)
                Column {
                    Text(playback?.title ?: state.connectedTarget?.name ?: "Not connected", style = MaterialTheme.typography.headlineSmall)
                    playback?.subtitle?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (playback != null) {
                val duration = playback.duration.takeIf { it > 0.0 } ?: 0.0
                val position = controller.displayTime().coerceIn(0.0, duration.coerceAtLeast(0.0))
                if (duration > 0.0) {
                    LinearProgressIndicator(
                        progress = { (position / duration).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${position.toInt().formatSeconds()} / ${duration.toInt().formatSeconds()}")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = { controller.playPause() }) {
                    Icon(
                        if (controller.isPlaying()) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play or pause",
                    )
                }
                IconButton(onClick = { controller.seek((controller.displayTime() - 10.0).coerceAtLeast(0.0)) }) {
                    Text("-10")
                }
                IconButton(onClick = { controller.seek(controller.displayTime() + 30.0) }) {
                    Text("+30")
                }
                IconButton(onClick = { controller.playNext() }, enabled = playback?.hasNextEpisode == true) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next episode")
                }
            }

            playback?.audioTracks?.takeIf { it.isNotEmpty() }?.let { tracks ->
                CastChipGroup("Audio") {
                    tracks.forEach { track ->
                        FilterChip(
                            selected = playback.selectedAudioTrackId == track.trackId,
                            onClick = { controller.selectAudioTrack(track.trackId) },
                            label = { Text(track.title) },
                        )
                    }
                }
            }

            playback?.subtitleTracks?.let { tracks ->
                CastChipGroup("Subtitles") {
                    FilterChip(
                        selected = playback.selectedSubtitleTrackId == null,
                        onClick = { controller.selectSubtitleTrack(null) },
                        label = { Text("Off") },
                    )
                    tracks.forEach { track ->
                        FilterChip(
                            selected = playback.selectedSubtitleTrackId == track.trackId,
                            onClick = { controller.selectSubtitleTrack(track.trackId) },
                            label = { Text(track.title) },
                        )
                    }
                }
            }

            playback?.qualityOptions?.takeIf { it.isNotEmpty() }?.let { options ->
                CastChipGroup("Quality") {
                    options.forEach { option ->
                        FilterChip(
                            selected = playback.activeQualityId == option.id,
                            onClick = { controller.selectQuality(option.id) },
                            label = { Text(option.label) },
                        )
                    }
                }
            }

            state.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun CastChipGroup(
    title: String,
    content: @Composable RowScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

private fun Int.formatSeconds(): String {
    val minutes = this / 60
    val seconds = this % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
