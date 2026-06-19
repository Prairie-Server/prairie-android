package com.continuum.app.android.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.continuum.app.android.ui.util.formatClockTime
import com.continuum.app.model.catalog.VersionChapter
import kotlinx.coroutines.launch

/**
 * Chapter picker bottom sheet. Tap a row to seek the player to that chapter's
 * `startSeconds`. Opened from the "Chapters" row in [PlayerSettingsSheet].
 *
 * Mirrors iOS phone's chapter list behavior — server-supplied via
 * `FileVersion.chapters` (FFprobe-extracted at ingest). Thumbnails
 * (`thumbnailUrl` + `thumbnailThumbhash`) intentionally not rendered in the
 * first cut; text rows are complete shipping content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersSheet(
    isVisible: Boolean,
    chapters: List<VersionChapter>,
    onSelect: (chapterIndex: Int) -> Unit,
    onDismiss: () -> Unit,
    // Current playback position (seconds) so the active chapter shows the iOS
    // `play.fill` indicator. Display-only; defaults to 0.
    position: Double = 0.0,
) {
    if (!isVisible) return

    // iOS marks the last chapter whose start time is <= currentTime.
    val currentChapterIndex = chapters.indexOfLast { it.startSeconds <= position }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    LaunchedEffect(isVisible) {
        if (isVisible) sheetState.show()
    }

    ModalBottomSheet(
        onDismissRequest = {
            scope.launch { sheetState.hide() }
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = Color.Transparent,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1F2937).copy(alpha = 0.95f),
                            Color.Black.copy(alpha = 0.92f),
                        ),
                    ),
                ),
        ) {
            Text(
                text = "Chapters",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
            )

            if (chapters.isEmpty()) {
                Text(
                    text = "No chapters in this title",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(
                        chapters,
                        key = { _, c -> c.index },
                        contentType = { _, _ -> "chapter-row" },
                    ) { idx, ch ->
                        ChapterRow(
                            chapter = ch,
                            isCurrent = idx == currentChapterIndex,
                            onClick = {
                                onSelect(idx)
                                scope.launch { sheetState.hide() }
                                onDismiss()
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ChapterRow(
    chapter: VersionChapter,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    // iOS phone row: leading "N." (white 0.6, width 30 trailing-aligned),
    // VStack(title, time caption white 0.6 monospaced), Spacer, trailing
    // `play.fill` (tint) when this is the current chapter.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "${chapter.index + 1}.",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(30.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = chapter.title.ifBlank { "Chapter ${chapter.index + 1}" },
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = formatClockTime(chapter.startSeconds),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        if (isCurrent) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Now playing",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
