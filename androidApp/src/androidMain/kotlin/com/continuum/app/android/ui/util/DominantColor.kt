package com.continuum.app.android.ui.util

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Process-wide cache of dominant colors keyed by image URL. Palette
 * extraction is expensive enough that re-running it as the user scrolls
 * back to a previously-seen hero would jank — and the result is purely a
 * function of the URL, so a simple in-memory map is the right shape.
 */
private val dominantColorCache = mutableMapOf<String, Color>()

private const val CrossfadeDurationMs = 300

/**
 * Sample a saturated dominant tint from [imageUrl] and crossfade to it
 * over 300ms. Returns [fallback] until the bitmap loads and the palette
 * resolves; once a URL has been sampled, subsequent recompositions with
 * the same URL hit the in-memory cache and snap straight to the result.
 *
 * Mirrors iOS `HeroBackdropPalette` semantically — same role (Plex-style
 * page tint above the fold) but driven by androidx.palette instead of
 * `CIAreaAverage`. Vibrant swatches are preferred so the gradient picks
 * up the artwork's character rather than collapsing to a muddy average.
 */
@Composable
fun rememberDominantColor(imageUrl: String?, fallback: Color): State<Color> {
    val context = LocalContext.current
    var sampled by remember(imageUrl) {
        mutableStateOf(imageUrl?.let { dominantColorCache[it] } ?: fallback)
    }

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrBlank()) {
            sampled = fallback
            return@LaunchedEffect
        }
        dominantColorCache[imageUrl]?.let {
            sampled = it
            return@LaunchedEffect
        }
        try {
            val bitmap = withContext(Dispatchers.IO) {
                val loader = context.imageLoader
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .size(100)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val raw = result.image.toBitmap()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        raw.config == Bitmap.Config.HARDWARE
                    ) {
                        raw.copy(Bitmap.Config.ARGB_8888, false)
                    } else {
                        raw
                    }
                } else {
                    null
                }
            } ?: return@LaunchedEffect

            val color = withContext(Dispatchers.Default) {
                val palette = Palette.from(bitmap).generate()
                bitmap.recycle()
                pickTint(palette)
            } ?: return@LaunchedEffect

            dominantColorCache[imageUrl] = color
            sampled = color
        } catch (_: Exception) {
            // Leave fallback in place — gradient just won't be tinted.
        }
    }

    return animateColorAsState(
        targetValue = sampled,
        animationSpec = tween(CrossfadeDurationMs),
        label = "dominantColor",
    )
}

private fun pickTint(palette: Palette): Color? {
    val candidates = listOfNotNull(
        palette.vibrantSwatch,
        palette.lightVibrantSwatch,
        palette.darkVibrantSwatch,
        palette.mutedSwatch,
        palette.darkMutedSwatch,
        palette.dominantSwatch,
    )
    val swatch = candidates.maxByOrNull { it.hsl[1] } ?: return null
    val hsl = swatch.hsl.copyOf()
    hsl[1] = maxOf(hsl[1], 0.5f)
    hsl[2] = 0.42f
    return Color(ColorUtils.HSLToColor(hsl))
}
