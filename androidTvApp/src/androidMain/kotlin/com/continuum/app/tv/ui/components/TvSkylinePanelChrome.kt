package com.continuum.app.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared surface chrome for every Skyline anchored menu — the cascade
 * selector + flyout (§5.3) and the Stage-5 profile dropdown (§5.8) — so they
 * read as one family. Port of tvOS `TVSkylinePanelChrome` (`TVTopMenuBar.swift`).
 *
 * A near-opaque vertical tint (lighter at the top, as if lit from above)
 * finished with a gradient hairline that brightens along the top lip and a
 * drop shadow, so the panel floats over the page on its own depth rather than
 * needing a page scrim to darken everything behind it.
 *
 * Compose has no `.regularMaterial` blur; the dark fill is opaque enough that
 * the absence of a frost blur is not noticeable over the dimmed page.
 */
fun Modifier.tvSkylinePanelChrome(corner: Dp = 20.dp): Modifier {
    val shape = RoundedCornerShape(corner)
    return this
        // Two-layer drop shadow approximated by a single soft elevation shadow;
        // `clip = false` keeps the shadow outside the rounded body.
        .shadow(elevation = 24.dp, shape = shape, clip = false)
        .clip(shape)
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF23252C).copy(alpha = 0.96f),
                    Color(0xFF141519).copy(alpha = 0.97f),
                ),
            ),
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.22f),
                    Color.White.copy(alpha = 0.05f),
                ),
            ),
            shape = shape,
        )
}
