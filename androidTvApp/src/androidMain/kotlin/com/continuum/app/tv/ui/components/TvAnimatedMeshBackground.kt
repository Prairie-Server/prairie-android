package com.continuum.app.tv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.continuum.app.tv.ui.theme.DarkBackground
import com.continuum.app.tv.ui.theme.StageLightSoft
import com.continuum.app.tv.ui.theme.StageLightStrong
import kotlin.math.cos
import kotlin.math.sin

/**
 * Slow-drifting stage lights for auth and profile screens. The lighting stays
 * mostly monochrome so the form/profile surfaces feel closer to the current
 * tvOS presentation than the previous branded mesh treatment.
 */
@Composable
fun TvAnimatedMeshBackground(
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "meshTransition")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(34_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "meshAngle",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val largeRadius = minOf(w, h) * 0.95f
            val mediumRadius = minOf(w, h) * 0.62f

            val lights = listOf(
                Triple(0.28f, 0.18f, 0f),
                Triple(0.74f, 0.22f, (Math.PI * 2f / 3f).toFloat()),
                Triple(0.50f, 0.58f, (Math.PI * 4f / 3f).toFloat()),
            )
            for ((cx, cy, phase) in lights) {
                val x = w * cx + cos(angle + phase) * w * 0.05f
                val y = h * cy + sin(angle + phase) * h * 0.06f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            StageLightStrong,
                            StageLightSoft,
                            Color.Transparent,
                        ),
                        center = Offset(x, y),
                        radius = largeRadius,
                    ),
                    radius = largeRadius,
                    center = Offset(x, y),
                )
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.08f),
                        StageLightSoft,
                        Color.Transparent,
                    ),
                    center = Offset(w * 0.5f, h * 0.2f),
                    radius = mediumRadius,
                ),
                radius = mediumRadius,
                center = Offset(w * 0.5f, h * 0.2f),
            )

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        DarkBackground.copy(alpha = 0.22f),
                        DarkBackground.copy(alpha = 0.88f),
                    ),
                ),
            )
        }
    }
}
