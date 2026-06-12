package com.continuum.app.android.ui.screens.audiobook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.continuum.app.android.ui.util.formatClockTime
import com.continuum.app.model.catalog.VersionChapter

/**
 * Current-chapter header + a solid whole-book seek slider. The scrubber
 * represents the entire book so overall progress reads at a glance; the header
 * keeps chapter context. [onSeek] receives absolute book seconds.
 */
@Composable
fun ChapterProgressBar(
    chapters: List<VersionChapter>,
    chapterCountLabel: String,
    positionSeconds: Double,
    durationSeconds: Double,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasChapters = chapters.isNotEmpty()
    val sliderMax = durationSeconds.toFloat().coerceAtLeast(1f)
    val sliderValue = positionSeconds.toFloat().coerceIn(0f, sliderMax)

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

        Slider(
            value = sliderValue,
            valueRange = 0f..sliderMax,
            onValueChange = { onSeek(it.toDouble().coerceIn(0.0, durationSeconds.coerceAtLeast(0.0))) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatClockTime(positionSeconds), style = MaterialTheme.typography.labelSmall)
            Text(formatClockTime(durationSeconds), style = MaterialTheme.typography.labelSmall)
        }
    }
}
