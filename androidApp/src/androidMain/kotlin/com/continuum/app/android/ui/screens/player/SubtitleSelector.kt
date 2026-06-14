package com.continuum.app.android.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.continuum.app.model.playback.PlayerSubtitleInfo
import com.continuum.app.player.formatSubtitleTrackDisplayLabel

/**
 * Bottom sheet for selecting a subtitle track.
 * Shows all available subtitle tracks with the currently selected one marked.
 * Index -1 represents "Off" (no subtitles).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSelector(
    subtitles: List<PlayerSubtitleInfo>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Subtitles",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn {
                // "Off" option
                item {
                    SubtitleOptionRow(
                        label = "Off",
                        detail = null,
                        isSelected = selectedIndex == -1,
                        onClick = {
                            onSelect(-1)
                            onDismiss()
                        },
                    )
                }

                // Subtitle tracks
                itemsIndexed(subtitles) { index, subtitle ->
                    val label = formatSubtitleTrackDisplayLabel(
                        rawLabel = subtitle.label,
                        language = subtitle.language,
                        codecOrMime = subtitle.codec,
                        isForced = subtitle.forced == true,
                        index = index,
                    )
                    val detail = buildString {
                        subtitle.source?.let { append(it.replaceFirstChar { c -> c.uppercase() }) }
                        subtitle.codec?.let {
                            if (isNotEmpty()) append(" - ")
                            append(it.uppercase())
                        }
                        if (subtitle.forced == true) {
                            if (isNotEmpty()) append(" - ")
                            append("Forced")
                        }
                    }.ifEmpty { null }

                    SubtitleOptionRow(
                        label = label,
                        detail = detail,
                        isSelected = selectedIndex == index,
                        onClick = {
                            onSelect(index)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SubtitleOptionRow(
    label: String,
    detail: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Spacer(modifier = Modifier.width(24.dp))
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
