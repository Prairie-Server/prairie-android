package org.prairieserver.prairie.common.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.prairieserver.prairie.playback.TrickplayTilePreview

/**
 * Crops one tile out of a trickplay sprite sheet using the same layout math as
 * web SeekBar (`backgroundSize` / `backgroundPosition` percentage sprites).
 */
@Composable
fun TrickplayTileImage(
    tile: TrickplayTilePreview,
    previewWidth: Dp = 176.dp,
    modifier: Modifier = Modifier,
) {
    val aspect = tile.width.toFloat() / tile.height.toFloat().coerceAtLeast(1f)
    val previewHeight = previewWidth / aspect
    val density = LocalDensity.current
    val sheetWidth = previewWidth * tile.columns
    val sheetHeight = previewHeight * tile.rows
    val offsetX = with(density) { (-previewWidth * tile.col).toPx() }
    val offsetY = with(density) { (-previewHeight * tile.row).toPx() }

    Box(
        modifier = modifier
            .width(previewWidth)
            .height(previewHeight)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black),
    ) {
        AsyncImage(
            model = tile.url,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .size(sheetWidth, sheetHeight)
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()) },
        )
    }
}
