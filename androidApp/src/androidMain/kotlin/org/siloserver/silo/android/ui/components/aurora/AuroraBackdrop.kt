package org.siloserver.silo.android.ui.components.aurora

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.random.Random

/**
 * Per-screen Aurora composition — a Compose port of silo-apple's
 * `AuroraBackdrop` (`DesignSystem/Aurora/AuroraBackdrop.swift`), scaled for the
 * phone. Each first-run screen gets its own variant (where the light band sits,
 * how bright) so the flow feels related but never identical, inside one
 * plum-night palette.
 *
 * The SwiftUI original layers the warm bloom + ribbons with `.screen`; we draw
 * the night gradient, bloom and ribbons into a single blurred layer using
 * additive [BlendMode.Plus] so the bright warm/pink/violet bands *add* light to
 * the dark night (a real glow), then blur the layer into flowing aurora.
 */
data class AuroraVariant(
    val rotationDegrees: Float,
    val centerY: Float,
    val intensity: Float,
) {
    companion object {
        val Welcome = AuroraVariant(-12f, 0.24f, 0.92f)
        val Server = AuroraVariant(-9f, 0.32f, 0.55f)
        val SignIn = AuroraVariant(-14f, 0.22f, 0.86f)
        val Profile = AuroraVariant(-10f, 0.27f, 0.66f)
    }
}

enum class AuroraScrim { Left, Soft, None }

internal val AuroraNightTop = Color(0xFF1C1329)
internal val AuroraNightMid = Color(0xFF0D0A17)
internal val AuroraNightBottom = Color(0xFF070509)

@Composable
fun AuroraBackdrop(
    variant: AuroraVariant = AuroraVariant.SignIn,
    scrim: AuroraScrim = AuroraScrim.Soft,
    modifier: Modifier = Modifier,
) {
    // Deterministic starfield (so it doesn't reshuffle each recomposition).
    val stars = remember {
        val rng = Random(0x5110)
        List(64) { Triple(rng.nextFloat(), rng.nextFloat() * 0.62f, 0.12f + rng.nextFloat() * 0.50f) }
    }

    Box(modifier = modifier.fillMaxSize().background(AuroraNightBottom)) {
        // Glow layer: night gradient + additive bloom + additive ribbons, all
        // blurred together so the bright bands read as flowing aurora light.
        Canvas(Modifier.matchParentSize().blur(56.dp)) {
            val w = size.width
            val h = size.height

            // Base night gradient.
            drawRect(Brush.verticalGradient(listOf(AuroraNightTop, AuroraNightMid, AuroraNightBottom)), size = size)

            // Warm key-light bloom (additive).
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFDCA6).copy(alpha = 0.46f * variant.intensity),
                        Color.Transparent,
                    ),
                    center = Offset(w * 0.7f, h * (variant.centerY - 0.03f)),
                    radius = max(w, h) * 0.55f,
                ),
                size = size,
                blendMode = BlendMode.Plus,
            )

            // Three soft, wide light bands rotated as a group (additive).
            val cy = h * variant.centerY
            rotate(degrees = variant.rotationDegrees, pivot = Offset(w / 2f, cy)) {
                ribbon(cy + 0f, w * 1.7f, 150f, listOf(Color(0xFFFFD9A4), Color(0xFFFF90A8), Color(0xFFC490FF)), variant.intensity)
                ribbon(cy + 96f, w * 1.7f, 116f, listOf(Color(0xFFFFADC6), Color(0xFF9B8BFF)), variant.intensity)
                ribbon(cy - 120f, w * 1.5f, 92f, listOf(Color(0xFFC6F0E2), Color(0xFF8FE7CF)), variant.intensity * 0.7f)
            }
        }

        // Starfield (sharp, top band).
        Canvas(Modifier.matchParentSize()) {
            stars.forEach { (fx, fy, a) ->
                drawCircle(
                    color = Color.White.copy(alpha = a * 0.7f),
                    radius = 1.3f,
                    center = Offset(fx * size.width, fy * size.height),
                )
            }
        }

        // Vignette.
        Canvas(Modifier.matchParentSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.height * 0.9f,
                ),
                size = size,
            )
        }

        // Scrim so a centered glass card / left hero text pops.
        when (scrim) {
            AuroraScrim.Soft -> Box(
                Modifier.matchParentSize().background(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, AuroraNightBottom.copy(alpha = 0.72f)),
                    ),
                ),
            )
            AuroraScrim.Left -> Box(
                Modifier.matchParentSize().background(
                    Brush.horizontalGradient(
                        0.0f to AuroraNightBottom.copy(alpha = 0.9f),
                        0.3f to AuroraNightBottom.copy(alpha = 0.55f),
                        0.72f to Color.Transparent,
                    ),
                ),
            )
            AuroraScrim.None -> Unit
        }

        // Top scrim keeps the wordmark + step chrome legible over the light.
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.22f)
                .align(Alignment.TopStart)
                .background(
                    Brush.verticalGradient(listOf(AuroraNightBottom.copy(alpha = 0.66f), Color.Transparent)),
                ),
        )
    }
}

/** One soft, wide horizontal light band centered on the canvas, at [yCenter]. */
private fun DrawScope.ribbon(
    yCenter: Float,
    width: Float,
    height: Float,
    colors: List<Color>,
    intensity: Float,
) {
    val left = (size.width - width) / 2f
    val top = yCenter - height / 2f
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent) + colors + listOf(Color.Transparent),
            startX = left,
            endX = left + width,
        ),
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(height / 2f, height / 2f),
        alpha = intensity.coerceIn(0f, 1f),
        blendMode = BlendMode.Plus,
    )
}
