package org.prairieserver.prairie.android.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.prairieserver.prairie.domain.player.IntroAutoSkipState

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
                // iOS: Label("Skip Intro", systemImage: "forward.end.fill"),
                // size 16 semibold, black-on-white capsule, padding 18/12.
                SkipNowPill(label = "Skip Intro", onClick = onSkipNow)
            }
            is IntroAutoSkipState.CountingDown -> {
                // iOS introSkipButton during countdown: a "Skipping intro in N"
                // capsule label stacked above a [Cancel] + [Skip Now] row,
                // trailing-aligned with spacing 8 / 10.
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(percent = 50),
                    ) {
                        Text(
                            text = "Skipping intro in ${current.secondsRemaining.coerceAtLeast(0)}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Cancel: white text on black 0.65 capsule with a
                        // white 0.28 hairline border, padding 16/11.
                        Surface(
                            onClick = onCancelCountdown,
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(percent = 50),
                            modifier = Modifier.border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.28f),
                                shape = RoundedCornerShape(percent = 50),
                            ),
                        ) {
                            Text(
                                text = "Cancel",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                            )
                        }

                        SkipNowPill(label = "Skip Now", onClick = onSkipNow)
                    }
                }
            }
        }
    }
}

/**
 * iOS "Skip Intro" / "Skip Now" pill: a `forward.end.fill` leading icon plus a
 * label, size 16 semibold, black-on-white capsule, padding 18/12.
 */
@Composable
private fun SkipNowPill(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color.Black,
        ),
        shape = RoundedCornerShape(percent = 50),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 18.dp,
            vertical = 12.dp,
        ),
    ) {
        Icon(
            imageVector = Icons.Filled.SkipNext,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
