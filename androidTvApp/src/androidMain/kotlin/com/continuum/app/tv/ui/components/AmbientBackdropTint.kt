package com.continuum.app.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.continuum.app.model.section.SectionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared "ambient backdrop accent" published by the focused hero on Home and
 * consumed by [TvRootHeroBackdrop] (and eventually A.6's card glow). Mirrors
 * the tvOS TVRootHeroBackdrop tint behavior — a single accent extracted from
 * the active hero's backdrop image, tinted at low alpha across the page.
 *
 * Default is [Empty] (no item, no accent) — components reading [accent]
 * should render an untinted variant in that case.
 */
@Stable
class AmbientBackdropTintState internal constructor(
    initialAccent: Color? = null,
) {
    private val _accent = mutableStateOf(initialAccent)
    val accent: Color? get() = _accent.value

    private val _currentItem = mutableStateOf<SectionItem?>(null)
    val currentItem: SectionItem? get() = _currentItem.value

    internal val pendingItem: MutableState<SectionItem?> = mutableStateOf(null)

    fun set(item: SectionItem?) {
        _currentItem.value = item
        pendingItem.value = item
    }

    internal fun acceptAccent(item: SectionItem?, accent: Color?) {
        // Guard against stale extraction completing after the user has scrolled to a different item.
        if (_currentItem.value?.contentId == item?.contentId) {
            _accent.value = accent
        }
    }

    companion object {
        val Empty: AmbientBackdropTintState = AmbientBackdropTintState()
    }
}

/**
 * Composition-scope publisher for the ambient backdrop tint. Default value is
 * [AmbientBackdropTintState.Empty] so consumers don't crash outside Home.
 */
val LocalAmbientBackdropTint = compositionLocalOf { AmbientBackdropTintState.Empty }

/**
 * Creates a remembered [AmbientBackdropTintState] that re-extracts the
 * accent color whenever the published item changes. Use exactly once per
 * page that wants to publish a tint — typically wrap the page content in
 * a `CompositionLocalProvider(LocalAmbientBackdropTint provides …) { … }`.
 */
@Composable
fun rememberAmbientBackdropTintState(): AmbientBackdropTintState {
    val context = LocalContext.current
    val state = remember { AmbientBackdropTintState() }

    LaunchedEffect(state.pendingItem.value?.contentId) {
        val item = state.pendingItem.value
        if (item == null) {
            state.acceptAccent(null, null)
            return@LaunchedEffect
        }
        val url = item.backdropUrl ?: item.posterUrl
        if (url.isNullOrBlank()) {
            state.acceptAccent(item, null)
            return@LaunchedEffect
        }

        val accent: Color? = withContext(Dispatchers.IO) {
            runCatching {
                val loader: ImageLoader = SingletonImageLoader.get(context)
                val result = loader.execute(
                    ImageRequest.Builder(context)
                        .data(url)
                        .allowHardware(false) // Palette requires a software bitmap.
                        // Cap the decode at 128px: this request runs per hero
                        // focus change and allowHardware(false) forces a
                        // software bitmap, so an unconstrained request decodes
                        // the full-res backdrop on every D-pad move. Palette
                        // resizes to a small bitmap before quantizing anyway,
                        // so 128px loses nothing for accent extraction.
                        .size(128)
                        .build(),
                )
                val bitmap = (result as? SuccessResult)?.image?.toBitmap()
                    ?: return@runCatching null
                val palette = Palette.from(bitmap).generate()
                val swatch = palette.vibrantSwatch
                    ?: palette.mutedSwatch
                    ?: palette.dominantSwatch
                swatch?.rgb?.let(::Color)
            }.getOrNull()
        }
        state.acceptAccent(item, accent)
    }
    return state
}
