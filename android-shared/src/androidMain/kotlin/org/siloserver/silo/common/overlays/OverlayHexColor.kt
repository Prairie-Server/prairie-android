package org.siloserver.silo.common.overlays

import androidx.compose.ui.graphics.Color

/**
 * Parse a CSS-style hex string into a Compose [Color].
 *
 * Accepts `#rgb`, `#rrggbb`, and `#aarrggbb` (the leading `#` is
 * optional). Returns [Color.Transparent] for `null`/blank/malformed
 * input so a missing or hand-edited accent value never crashes a card.
 *
 * Note: the 8-digit form is `#aarrggbb` (alpha first), matching the
 * Android platform convention. The overlay registry's accent values are
 * all 6-digit `#rrggbb`, so the alpha channel only matters for
 * user-supplied overrides.
 */
internal fun overlayColorFromHex(hex: String?): Color {
    if (hex.isNullOrBlank()) return Color.Transparent
    val cleaned = (if (hex.startsWith("#")) hex.substring(1) else hex).trim()
    val expanded =
        when (cleaned.length) {
            // #rgb -> #rrggbb
            3 -> buildString {
                for (c in cleaned) {
                    append(c)
                    append(c)
                }
            }
            else -> cleaned
        }
    return when (expanded.length) {
        6 -> {
            val rgb = expanded.toLongOrNull(16) ?: return Color.Transparent
            Color(0xFF000000.toInt() or (rgb.toInt() and 0x00FFFFFF))
        }
        8 -> {
            val argb = expanded.toLongOrNull(16) ?: return Color.Transparent
            Color(argb.toInt())
        }
        else -> Color.Transparent
    }
}
