package com.continuum.app.tv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
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
 * `AuroraBackdrop` (`DesignSystem/Aurora/AuroraBackdrop.swift`). Each first-run
 * screen gets its own variant (where the light band sits, how bright) so the
 * flow feels related but never identical, inside one plum-night palette.
 *
 * The SwiftUI original layers the warm bloom + ribbons with `.screen`; we draw
 * the night gradient, bloom and ribbons into a single blurred layer using
 * additive [BlendMode.Plus] so the bright warm/pink/violet bands *add* light to
 * the dark night (a real glow), then blur the layer into flowing aurora.
 */
data class TvAuroraVariant(
    val rotationDegrees: Float,
    val centerY: Float,
    val intensity: Float,
    val ribbonIntensity: Float = 1f,
) {
    companion object {
        val Welcome = TvAuroraVariant(-12f, 0.24f, 0.92f)
        val Server = TvAuroraVariant(-9f, 0.32f, 0.62f)
        val SignIn = TvAuroraVariant(-14f, 0.22f, 0.90f)
        val Profile = TvAuroraVariant(-10f, 0.27f, 0.66f, ribbonIntensity = 0f)
    }
}

enum class TvAuroraScrim { Left, Soft, None }

private val NightTop = Color(0xFF1C1329)
private val NightMid = Color(0xFF0D0A17)
private val NightBottom = Color(0xFF070509)

@Composable
fun TvAuroraBackdrop(
    variant: TvAuroraVariant = TvAuroraVariant.SignIn,
    scrim: TvAuroraScrim = TvAuroraScrim.Soft,
    modifier: Modifier = Modifier,
) {
    // Deterministic starfield (so it doesn't reshuffle each recomposition).
    val stars = remember {
        val rng = Random(0x5110)
        List(90) { Triple(rng.nextFloat(), rng.nextFloat() * 0.62f, 0.25f + rng.nextFloat() * 0.55f) }
    }

    Box(modifier = modifier.fillMaxSize().background(NightBottom)) {
        // Glow layer: night gradient + additive bloom + additive ribbons, all
        // blurred together so the bright bands read as flowing aurora light.
        Canvas(Modifier.matchParentSize().blur(64.dp)) {
            val w = size.width
            val h = size.height

            // Base night gradient.
            drawRect(Brush.verticalGradient(listOf(NightTop, NightMid, NightBottom)), size = size)

            // Warm key-light bloom (additive).
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFDCA6).copy(alpha = 0.50f * variant.intensity),
                        Color.Transparent,
                    ),
                    center = Offset(w * 0.7f, h * (variant.centerY - 0.03f)),
                    radius = max(w, h) * 0.55f,
                ),
                size = size,
                blendMode = BlendMode.Plus,
            )

            // Three soft, wide light bands rotated as a group (additive).
            // Profile selection keeps the bloom but suppresses the bands; on
            // Android's canvas they read as a hard diagonal stripe, unlike tvOS.
            if (variant.ribbonIntensity > 0f) {
                val cy = h * variant.centerY
                val ribbonAlpha = variant.intensity * variant.ribbonIntensity
                rotate(degrees = variant.rotationDegrees, pivot = Offset(w / 2f, cy)) {
                    ribbon(cy + 0f, w * 1.7f, 150f, listOf(Color(0xFFFFD9A4), Color(0xFFFF90A8), Color(0xFFC490FF)), ribbonAlpha)
                    ribbon(cy + 96f, w * 1.7f, 116f, listOf(Color(0xFFFFADC6), Color(0xFF9B8BFF)), ribbonAlpha)
                    ribbon(cy - 120f, w * 1.5f, 92f, listOf(Color(0xFFC6F0E2), Color(0xFF8FE7CF)), ribbonAlpha * 0.7f)
                }
            }
        }

        // Starfield (sharp, top band).
        Canvas(Modifier.matchParentSize()) {
            stars.forEach { (fx, fy, a) ->
                drawCircle(
                    color = Color.White.copy(alpha = a * 0.65f),
                    radius = 1.5f,
                    center = Offset(fx * size.width, fy * size.height),
                )
            }
        }

        // Vignette (gentle — the glow needs to survive it).
        Canvas(Modifier.matchParentSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.height * 0.95f,
                ),
                size = size,
            )
        }

        // Scrim so a centered glass card / left hero text pops.
        when (scrim) {
            TvAuroraScrim.Soft -> Box(
                Modifier.matchParentSize().background(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, NightBottom.copy(alpha = 0.72f)),
                    ),
                ),
            )
            TvAuroraScrim.Left -> Box(
                Modifier.matchParentSize().background(
                    Brush.horizontalGradient(
                        0.0f to NightBottom.copy(alpha = 0.9f),
                        0.3f to NightBottom.copy(alpha = 0.55f),
                        0.72f to Color.Transparent,
                    ),
                ),
            )
            TvAuroraScrim.None -> Unit
        }

        // Top scrim keeps the wordmark + step chrome legible over the light.
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.22f)
                .align(Alignment.TopStart)
                .background(
                    Brush.verticalGradient(listOf(NightBottom.copy(alpha = 0.66f), Color.Transparent)),
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
