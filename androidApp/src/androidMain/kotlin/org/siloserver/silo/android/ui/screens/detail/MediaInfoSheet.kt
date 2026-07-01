package org.siloserver.silo.android.ui.screens.detail

import org.siloserver.silo.android.ui.util.formatBytes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.siloserver.silo.model.catalog.FileVersion

/**
 * Bottom sheet showing technical media information for a file version.
 *
 * Displays resolution, codecs, bitrate, file size, HDR status,
 * audio tracks, and subtitle tracks.
 *
 * @param versions Available file versions for the item.
 * @param onDismiss Callback when the sheet is dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaInfoSheet(
    versions: List<FileVersion>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Media Info",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            versions.forEachIndexed { index, version ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }

                if (versions.size > 1) {
                    Text(
                        text = "Version ${index + 1}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                // Video info
                InfoRow("Resolution", version.resolution ?: "Unknown")
                InfoRow("Video Codec", version.codecVideo ?: "Unknown")
                InfoRow("Audio Codec", version.codecAudio ?: "Unknown")
                InfoRow("Container", version.container ?: "Unknown")
                InfoRow("HDR", if (version.hdr) "Yes" else "No")
                if (version.bitrate > 0) {
                    InfoRow("Bitrate", formatBitrate(version.bitrate))
                }
                if (version.fileSize > 0) {
                    InfoRow("File Size", formatBytes(version.fileSize))
                }
                if (version.duration > 0) {
                    InfoRow("Duration", formatDuration(version.duration))
                }

                // Audio tracks
                val audioTracks = version.audioTracks
                if (!audioTracks.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Audio Tracks",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    audioTracks.forEach { track ->
                        val desc = buildList {
                            track.codec?.let { add(it) }
                            track.channelLayout?.let { add(it) }
                                ?: track.channels?.let { add("${it}ch") }
                            track.language?.let { add(it) }
                        }.joinToString(" \u2022 ")
                        Text(
                            text = track.title ?: desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }

                // Subtitle tracks
                val subtitleTracks = version.subtitleTracks
                if (!subtitleTracks.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Subtitle Tracks",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    subtitleTracks.forEach { track ->
                        val desc = buildList {
                            track.language?.let { add(it) }
                            track.codec?.let { add(it) }
                            if (track.forced) add("Forced")
                            if (track.external) add("External")
                        }.joinToString(" \u2022 ")
                        Text(
                            text = track.title ?: desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatBitrate(bitrate: Int): String {
    return when {
        bitrate >= 1_000_000 -> "%.1f Mbps".format(bitrate / 1_000_000.0)
        bitrate >= 1_000 -> "%.0f kbps".format(bitrate / 1_000.0)
        else -> "$bitrate bps"
    }
}

private fun formatDuration(seconds: Double): String {
    val totalSeconds = seconds.toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}
