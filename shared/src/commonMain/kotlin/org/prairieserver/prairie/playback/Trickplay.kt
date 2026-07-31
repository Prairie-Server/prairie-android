package org.prairieserver.prairie.playback

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * One sprite sheet covering a contiguous tile range.
 * Mirrors server `VersionTrickplaySheet` / web `PlayerTrickplaySheet`.
 */
@Serializable
data class TrickplaySheet(
    val index: Int = 0,
    val url: String = "",
)

/**
 * Interval sprite-sheet metadata for seek scrubbing previews.
 * Mirrors server `VersionTrickplay` / web `PlayerTrickplay`.
 */
@Serializable
data class TrickplayInfo(
    @SerialName("interval_seconds") val intervalSeconds: Double = 0.0,
    val width: Int = 0,
    val height: Int = 0,
    @SerialName("tile_columns") val tileColumns: Int = 0,
    @SerialName("tile_rows") val tileRows: Int = 0,
    @SerialName("thumbnail_count") val thumbnailCount: Int = 0,
    val sheets: List<TrickplaySheet> = emptyList(),
)

/**
 * Resolved sprite tile for a scrub preview — same math as web
 * `resolveTrickplayTile` in SeekBar.tsx.
 */
data class TrickplayTilePreview(
    val url: String,
    val width: Int,
    val height: Int,
    /** CSS-style background-position percentages (for Coil alignmentOffset). */
    val backgroundPositionXPercent: Float,
    val backgroundPositionYPercent: Float,
    /** Columns/rows of the sheet (for backgroundSize = columns*100% × rows*100%). */
    val columns: Int,
    val rows: Int,
    val col: Int,
    val row: Int,
)

/**
 * Resolves which sprite tile covers [seconds], or null when trickplay is absent
 * / incomplete. Graceful no-op for missing sheets.
 */
fun resolveTrickplayTile(
    trickplay: TrickplayInfo?,
    seconds: Double,
): TrickplayTilePreview? {
    if (trickplay == null || trickplay.thumbnailCount <= 0 || trickplay.sheets.isEmpty()) {
        return null
    }
    val interval = if (trickplay.intervalSeconds > 0) trickplay.intervalSeconds else 10.0
    val columns = if (trickplay.tileColumns > 0) trickplay.tileColumns else 10
    val rows = if (trickplay.tileRows > 0) trickplay.tileRows else 10
    val width = if (trickplay.width > 0) trickplay.width else 320
    val height = if (trickplay.height > 0) {
        trickplay.height
    } else {
        round(width * 9.0 / 16.0).toInt()
    }
    val tilesPerSheet = columns * rows
    val tileIndex = min(
        max(0, floor(seconds / interval).toInt()),
        max(0, trickplay.thumbnailCount - 1),
    )
    val sheetIndex = tileIndex / tilesPerSheet
    val sheet = trickplay.sheets.firstOrNull { it.index == sheetIndex }
    if (sheet == null || sheet.url.isBlank()) return null
    val local = tileIndex % tilesPerSheet
    val col = local % columns
    val row = local / columns
    val posX = if (columns > 1) (col.toFloat() / (columns - 1)) * 100f else 0f
    val posY = if (rows > 1) (row.toFloat() / (rows - 1)) * 100f else 0f
    return TrickplayTilePreview(
        url = sheet.url,
        width = width,
        height = height,
        backgroundPositionXPercent = posX,
        backgroundPositionYPercent = posY,
        columns = columns,
        rows = rows,
        col = col,
        row = row,
    )
}
