package com.continuum.app.android.ui.screens.audiobook

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.continuum.app.android.ui.util.formatClockTime
import com.continuum.app.model.catalog.VersionChapter

/** Seconds elapsed within the current chapter, clamped to [0, chapterLength].
 *  Degrades to the whole-book position when there are no chapters. Out-of-range
 *  index yields 0 so a transient bad index never throws. */
internal fun chapterRelativeSeconds(
    chapters: List<VersionChapter>,
    currentIndex: Int,
    positionSeconds: Double,
): Double {
    if (chapters.isEmpty()) return positionSeconds.coerceAtLeast(0.0)
    val chapter = chapters.getOrNull(currentIndex) ?: return 0.0
    val length = (chapter.endSeconds - chapter.startSeconds).coerceAtLeast(0.0)
    return (positionSeconds - chapter.startSeconds).coerceIn(0.0, length)
}

/** Length of the current chapter in seconds. 0 when there are no chapters
 *  or the index is out of range. */
internal fun chapterRelativeDuration(
    chapters: List<VersionChapter>,
    currentIndex: Int,
): Double {
    val chapter = chapters.getOrNull(currentIndex) ?: return 0.0
    return (chapter.endSeconds - chapter.startSeconds).coerceAtLeast(0.0)
}

/**
 * Current-chapter header + a seek slider that toggles between book-relative
 * and chapter-relative progress. Dragging always seeks an *absolute* book
 * position, so [onSeek] receives book seconds regardless of mode.
 */
@Composable
fun ChapterProgressBar(
    chapters: List<VersionChapter>,
    currentChapterIndex: Int,
    chapterCountLabel: String,
    positionSeconds: Double,
    durationSeconds: Double,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasChapters = chapters.isNotEmpty()
    // Default to chapter view when chapters exist; survives config change.
    var chapterMode by rememberSaveable(hasChapters) { mutableStateOf(hasChapters) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (hasChapters && chapterCountLabel.isNotBlank()) {
            Text(
                text = chapterCountLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        val chapter = chapters.getOrNull(currentChapterIndex)
        val relSeconds = chapterRelativeSeconds(chapters, currentChapterIndex, positionSeconds)
        val relDuration = chapterRelativeDuration(chapters, currentChapterIndex)

        val sliderValue: Float
        val sliderMax: Float
        val leftLabel: String
        val rightLabel: String
        if (chapterMode && hasChapters && relDuration > 0.0) {
            sliderMax = relDuration.toFloat().coerceAtLeast(1f)
            sliderValue = relSeconds.toFloat().coerceIn(0f, sliderMax)
            leftLabel = formatClockTime(relSeconds)
            rightLabel = formatClockTime(relDuration)
        } else {
            sliderMax = durationSeconds.toFloat().coerceAtLeast(1f)
            sliderValue = positionSeconds.toFloat().coerceIn(0f, sliderMax)
            leftLabel = formatClockTime(positionSeconds)
            rightLabel = formatClockTime(durationSeconds)
        }

        Slider(
            value = sliderValue,
            valueRange = 0f..sliderMax,
            onValueChange = { v ->
                // Map the slider's value back to an absolute book position.
                val book = if (chapterMode && hasChapters && chapter != null && relDuration > 0.0) {
                    chapter.startSeconds + v.toDouble()
                } else {
                    v.toDouble()
                }
                onSeek(book.coerceIn(0.0, durationSeconds.coerceAtLeast(0.0)))
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(leftLabel, style = MaterialTheme.typography.labelSmall)
            if (hasChapters) {
                Text(
                    text = if (chapterMode) "Chapter" else "Book",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { chapterMode = !chapterMode }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Text(rightLabel, style = MaterialTheme.typography.labelSmall)
        }
    }
}
