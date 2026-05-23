package com.continuum.app.android.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.continuum.app.domain.player.IntroAutoSkipState

/**
 * Banner that surfaces the intro auto-skip flow.
 *
 * Three states it crossfades between:
 *  - [IntroAutoSkipState.Hidden]: takes no space (caller can leave the slot composed).
 *  - [IntroAutoSkipState.ShowingButton]: a manual "Skip Intro" pill.
 *  - [IntroAutoSkipState.CountingDown]: a countdown ring + "Skipping intro" + Cancel.
 *
 * The component itself never positions itself; the parent should anchor it
 * (typically bottom-end of the player overlay).
 */
@Composable
fun IntroAutoSkipBanner(
    state: IntroAutoSkipState,
    onSkipNow: () -> Unit,
    onCancelCountdown: () -> Unit,
    modifier: Modifier = Modifier,
    totalSeconds: Int = 5,
) {
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            fadeIn(animationSpec = tween(durationMillis = 180)) togetherWith
                fadeOut(animationSpec = tween(durationMillis = 180))
        },
        label = "introAutoSkipBanner",
        modifier = modifier,
    ) { current ->
        when (current) {
            IntroAutoSkipState.Hidden -> {
                // Render nothing but stay in the layout slot so AnimatedContent can fade in/out.
                Spacer(Modifier.size(0.dp))
            }
            IntroAutoSkipState.ShowingButton -> {
                Button(
                    onClick = onSkipNow,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.92f),
                        contentColor = Color.Black,
                    ),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 10.dp,
                    ),
                ) {
                    Text(
                        text = "Skip Intro",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            is IntroAutoSkipState.CountingDown -> {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CountdownRing(
                            secondsRemaining = current.secondsRemaining,
                            totalSeconds = totalSeconds,
                        )
                        Text(
                            text = "Skipping intro",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                        )
                        TextButton(
                            onClick = onCancelCountdown,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                        ) {
                            Text(
                                text = "Cancel",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 24dp circular countdown ring with the remaining digit centered. Drawn from
 * 0° (top) sweeping clockwise to (remaining/total * 360°), so the ring shrinks
 * as the countdown progresses.
 */
@Composable
private fun CountdownRing(
    secondsRemaining: Int,
    totalSeconds: Int,
) {
    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val strokeWidth = 2.dp.toPx()
            val sweep = if (totalSeconds <= 0) 0f
            else (secondsRemaining.coerceAtLeast(0).toFloat() / totalSeconds.toFloat()) * 360f
            val inset = strokeWidth / 2f
            drawArc(
                color = Color.White,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Text(
            text = secondsRemaining.coerceAtLeast(0).toString(),
            color = Color.White,
            fontSize = 12.sp,
        )
    }
}
