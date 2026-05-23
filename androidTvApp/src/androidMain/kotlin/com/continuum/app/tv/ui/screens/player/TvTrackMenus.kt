package com.continuum.app.tv.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Modal list of subtitle tracks (with an explicit "Off" option). Tapping a
 * row fires [onSelect] with the track index (or -1 for Off) and the caller
 * closes the dialog and pushes the selection into [com.continuum.app.common.player.SubtitleManager].
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSubtitleMenu(
    tracks: List<PlayerTrackEntry>,
    onSelect: (index: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    TrackMenu(
        title = "Subtitles",
        tracks = tracks,
        includeOff = true,
        onSelect = onSelect,
        onDismiss = onDismiss,
    )
}

/**
 * Modal list of audio tracks. Tapping a row fires [onSelect] with the track
 * index and the caller passes it to [com.continuum.app.common.player.AudioTrackManager].
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAudioTrackMenu(
    tracks: List<PlayerTrackEntry>,
    onSelect: (index: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    TrackMenu(
        title = "Audio",
        tracks = tracks,
        includeOff = false,
        onSelect = onSelect,
        onDismiss = onDismiss,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TrackMenu(
    title: String,
    tracks: List<PlayerTrackEntry>,
    includeOff: Boolean,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // Auto-focus the first row so D-pad navigation works immediately.
    // We pick either the "Off" row (when shown) or the currently-selected
    // track, and fall back to the first track otherwise.
    val firstRowFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { firstRowFocus.requestFocus() }
    }
    val focusedTrackKey = when {
        includeOff -> -1
        else -> tracks.firstOrNull { it.isSelected }?.index ?: tracks.firstOrNull()?.index ?: 0
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center,
        ) {
            // Non-interactive container (no onClick) so it doesn't steal
            // focus from the rows inside.
            Column(
                modifier = Modifier
                    .width(560.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (includeOff) {
                        item(key = -1) {
                            TrackRow(
                                label = "Off",
                                selected = tracks.none { it.isSelected },
                                onClick = { onSelect(-1) },
                                modifier = if (focusedTrackKey == -1) {
                                    Modifier.focusRequester(firstRowFocus)
                                } else {
                                    Modifier
                                },
                            )
                        }
                    }
                    items(tracks, key = { it.index }) { track ->
                        TrackRow(
                            label = track.displayLabel(),
                            selected = track.isSelected,
                            onClick = { onSelect(track.index) },
                            modifier = if (!includeOff && focusedTrackKey == track.index) {
                                Modifier.focusRequester(firstRowFocus)
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TrackRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    } else {
                        Color.Transparent
                    },
                )
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (selected) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(2.dp))
}

private fun PlayerTrackEntry.displayLabel(): String {
    val base = label.ifBlank { language?.uppercase() ?: "Track ${index + 1}" }
    return base
}
