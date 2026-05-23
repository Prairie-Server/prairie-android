package com.continuum.app.common.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

private val DefaultPlaceholderColor = Color(0xFF1A1D27)

/**
 * Async image composable that shows a neutral placeholder while the real image
 * loads from the network.
 *
 * Uses Coil for async image loading with crossfade.
 *
 * @param url The remote image URL to load.
 * @param thumbhash Base64-encoded thumbhash reserved for a future decoded preview.
 * @param contentDescription Accessibility description for the image.
 * @param modifier Compose modifier.
 * @param contentScale How the image content should be scaled.
 */
@Composable
fun ThumbhashImage(
    url: String?,
    thumbhash: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    transparent: Boolean = false,
) {
    val context = LocalContext.current

    if (url.isNullOrBlank()) {
        if (!transparent) {
            Box(modifier = modifier.background(DefaultPlaceholderColor))
        }
        return
    }

    val model = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(300)
            .build()
    }

    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = if (transparent) modifier else modifier.background(DefaultPlaceholderColor),
    )
}
