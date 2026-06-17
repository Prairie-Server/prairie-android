package com.continuum.app.tv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.continuum.app.model.catalog.FileVersion

/**
 * Read-only Media Info panel for TV — the 10-foot equivalent of the phone
 * MediaInfoSheet. Shows resolution / codecs / HDR / container / size plus the
 * audio and subtitle track lists for each [FileVersion]. Opened from the detail
 * "More" menu. Focusable so Back/Escape dismisses it.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvMediaInfoDialog(
    versions: List<FileVersion>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(50)
        runCatching { focus.requestFocus() }
    }
    // Window-level Popup so it overlays the whole screen (not the hero action
    // slot it's called from) and Back is intercepted via dismissOnBackPress
    // before the detail screen's parent BackHandler.
    Popup(
        alignment = Alignment.CenterEnd,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true),
    ) {
    Box(
        modifier = modifier
            .fillMaxWidth(0.46f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(topStart = 30.dp, bottomStart = 30.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(topStart = 30.dp, bottomStart = 30.dp))
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
                .focusRequester(focus)
                .focusable()
                .padding(horizontal = 28.dp, vertical = 34.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Media info",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (versions.isEmpty()) {
                Text(
                    text = "No media details available.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            versions.forEachIndexed { index, version ->
                if (versions.size > 1) {
                    Text(
                        text = version.fileName ?: "Version ${index + 1}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = if (index > 0) 12.dp else 0.dp),
                    )
                }
                InfoRow("Resolution", version.resolution ?: "Unknown")
                InfoRow("Video codec", version.codecVideo ?: "Unknown")
                InfoRow("Audio codec", version.codecAudio ?: "Unknown")
                if (version.hdr) InfoRow("HDR", "Yes")
                version.container?.let { InfoRow("Container", it) }
                if (version.fileSize > 0) InfoRow("Size", formatBytes(version.fileSize))
                if (version.bitrate > 0) InfoRow("Bitrate", "${version.bitrate / 1000} kbps")

                version.audioTracks?.takeIf { it.isNotEmpty() }?.let { tracks ->
                    SectionLabel("Audio tracks")
                    tracks.forEach { t ->
                        val parts = listOfNotNull(
                            t.language,
                            t.codec,
                            t.channels?.let { "${it}ch" },
                            t.title,
                        )
                        Text(
                            text = "• " + parts.joinToString(" · ").ifEmpty { "Track ${t.index}" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                        )
                    }
                }
                version.subtitleTracks?.takeIf { it.isNotEmpty() }?.let { tracks ->
                    SectionLabel("Subtitle tracks")
                    tracks.forEach { t ->
                        val parts = listOfNotNull(
                            t.language,
                            t.codec,
                            t.title,
                            if (t.forced) "forced" else null,
                        )
                        Text(
                            text = "• " + parts.joinToString(" · ").ifEmpty { "Track ${t.index}" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val gb = bytes / 1_000_000_000.0
    if (gb >= 1.0) return "%.2f GB".format(gb)
    val mb = bytes / 1_000_000.0
    return "%.0f MB".format(mb)
}
