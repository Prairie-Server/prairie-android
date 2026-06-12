package com.continuum.app.android.ui.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.continuum.app.android.ui.util.formatClockTime

/**
 * Seek bar with current and total time display.
 *
 * Allows the user to scrub through the video. While scrubbing, the displayed time
 * follows the thumb position rather than the actual playback position.
 */
@Composable
fun PlayerProgressBar(
    position: Double,
    duration: Double,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }

    val displayPosition = if (isSeeking) seekPosition else position.toFloat()
    val maxDuration = duration.toFloat().coerceAtLeast(1f)

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = displayPosition.coerceIn(0f, maxDuration),
            enabled = enabled,
            onValueChange = { value ->
                isSeeking = true
                seekPosition = value
            },
            onValueChangeFinished = {
                isSeeking = false
                onSeek(seekPosition.toDouble())
            },
            valueRange = 0f..maxDuration,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatClockTime(displayPosition.toDouble()),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
            Text(
                text = formatClockTime(duration),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }
    }
}
