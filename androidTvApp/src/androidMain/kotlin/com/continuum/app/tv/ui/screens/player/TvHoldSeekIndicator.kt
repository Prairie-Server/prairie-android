package com.continuum.app.tv.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/**
 * Floating chip shown during a hold-to-seek session. Mirrors the iOS
 * `HoldSeekIndicator` (see `iosApp/.../tvOS/HoldSeekIndicator.swift`) — a
 * top-anchored surface with the signed rate, a thin progress bar showing
 * where the preview is landing, and a hint row reminding the user which
 * buttons do what.
 *
 * The transport overlay is suppressed while a session is active (it would
 * shift focus away from the press-capture scrubber and lose the remote's
 * input), so this is the only visual feedback between hold-enter and
 * commit / cancel.
 */
@Composable
fun TvHoldSeekIndicator(
    isVisible: Boolean,
    rate: Int,
    previewTimeSec: Double,
    durationSec: Double,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically { -it / 4 },
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.78f),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = formatRate(rate),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                    )

                    val progress = if (durationSec > 0.0) {
                        (previewTimeSec / durationSec).toFloat().coerceIn(0f, 1f)
                    } else 0f
                    Box(
                        modifier = Modifier
                            .width(240.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.22f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(4.dp)
                                .background(Color.White, RoundedCornerShape(2.dp)),
                        )
                    }

                    Text(
                        text = formatTime(previewTimeSec, durationSec),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                Text(
                    text = "← → speed  ·  ◉ play  ·  ↩ cancel",
                    color = Color.White.copy(alpha = 0.60f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

private fun formatRate(rate: Int): String {
    if (rate == 0) return "Paused"
    val sign = if (rate < 0) "-" else ""
    return "$sign${kotlin.math.abs(rate)}×"
}

private fun formatTime(seconds: Double, durationSec: Double): String {
    if (!seconds.isFinite() || seconds < 0) return "0:00"
    val total = seconds.toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val showHours = h > 0 || durationSec >= 3600.0
    return if (showHours) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
