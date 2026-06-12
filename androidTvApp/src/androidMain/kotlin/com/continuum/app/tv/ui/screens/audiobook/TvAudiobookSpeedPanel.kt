package com.continuum.app.tv.ui.screens.audiobook

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlin.math.abs

private val SPEED_PRESETS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)

/** Focusable speed-preset overlay. Select applies the speed and closes (the
 *  host clears the panel). Replaces the phone SpeedSheet (spec §4.9). */
@Composable
fun TvAudiobookSpeedPanel(
    currentSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    TvAudiobookOverlayScaffold(title = "Speed", modifier = modifier) {
        SPEED_PRESETS.forEach { speed ->
            TvAudiobookOverlayRow(
                label = formatSpeedLabel(speed),
                isCurrent = abs(speed - currentSpeed) < 0.001f,
                onSelect = { onSelectSpeed(speed) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun formatSpeedLabel(speed: Float): String {
    val s = if (speed % 1f == 0f) {
        speed.toInt().toString()
    } else {
        "%.2f".format(speed).trimEnd('0').trimEnd('.')
    }
    return "${s}×"
}
