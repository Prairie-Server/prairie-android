package org.siloserver.silo.tv.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import org.siloserver.silo.common.ui.components.ThumbhashImage
import org.siloserver.silo.model.section.SectionItem

/**
 * Page-level Skyline backdrop for tvOS-style root screens. Faithful Android
 * port of the reworked tvOS `TVRootHeroBackdrop` (§5.4): crisp (un-blurred)
 * artwork anchored in the top-RIGHT corner at ~0.64w × 0.70h, faded out toward
 * the leading edge and the bottom by a two-axis corner mask into a color
 * sampled from the art itself, which is carried (dimmed) diagonally
 * topEnd → bottomStart across the page so the metadata and rows sit on the same
 * tint. A ~190dp top scrim keeps the menu bar legible over bright art.
 *
 * Driven by whichever row card holds focus — pass that item's marquee
 * [content]; when null the page renders flat on the app background.
 */
@Composable
fun TvRootHeroBackdrop(
    content: TvMarqueeContent?,
    modifier: Modifier = Modifier,
) {
    val tintState = LocalAmbientBackdropTint.current
    val targetAccent = tintState.accent ?: MaterialTheme.colorScheme.background
    val animatedTint by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = tween(durationMillis = 600),
        label = "tvRootHeroBackdropTint",
    )

    val isVisible = content != null

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Diagonal sampled-tint wash: richest in the top-right behind the art,
        // carried dimmed to the bottom-left (tvOS stops 1.0 / 0.5 / 0.18).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colorStops = arrayOf(
                            0.0f to animatedTint.copy(alpha = if (isVisible) 1.0f else 0.0f),
                            0.45f to animatedTint.copy(alpha = if (isVisible) 0.5f else 0.0f),
                            1.0f to animatedTint.copy(alpha = if (isVisible) 0.18f else 0.0f),
                        ),
                        // topEnd → bottomStart (mirrors SwiftUI .topTrailing →
                        // .bottomLeading); Offset.Infinite lets Compose resolve
                        // the gradient to the layout bounds' corners.
                        start = Offset(Float.POSITIVE_INFINITY, 0f),
                        end = Offset(0f, Float.POSITIVE_INFINITY),
                    ),
                ),
        )

        Crossfade(
            targetState = content?.heroBackdropUrl,
            animationSpec = tween(TvMarqueeCrossfadeMs),
            label = "tvRootHeroBackdropArt",
        ) { url ->
            if (url != null) {
                CornerAnchoredArt(
                    url = url,
                    thumbhash = content?.heroBackdropThumbhash,
                )
            } else {
                Box(modifier = Modifier.fillMaxSize())
            }
        }

        // Full-width top scrim so the menu bar stays legible over bright art.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TopScrimHeight)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.5f),
                        1f to Color.Transparent,
                    ),
                ),
        )
    }
}

/**
 * Crisp art block pinned to the top-right corner, masked with a two-axis ramp:
 * opaque only at the top-right, fading to clear toward the leading edge
 * (horizontal ramp) and the bottom (vertical ramp). The two ramps multiply via
 * [BlendMode.DstIn] so the art dissolves into the sampled wash on its left and
 * below.
 */
@Composable
private fun CornerAnchoredArt(
    url: String?,
    thumbhash: String?,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val artWidth = maxWidth * ArtWidthFraction
        val artHeight = maxHeight * ArtHeightFraction

        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.TopEnd,
        ) {
            Box(
                modifier = Modifier
                    .size(artWidth, artHeight)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        // Horizontal ramp: opaque at trailing (right) edge,
                        // clear toward the leading (left) edge.
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.68f to Color.Black,
                                    1.0f to Color.Black,
                                ),
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                        // Vertical ramp: opaque at the top, clear toward bottom.
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Black,
                                    0.58f to Color.Black,
                                    1.0f to Color.Transparent,
                                ),
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                ThumbhashImage(
                    url = url,
                    thumbhash = thumbhash,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    transparent = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Backward-compatible entry point for surfaces that still drive the backdrop
 * from a raw [SectionItem] (the library Browse landing, Sweep 3). Adapts the
 * item into the Skyline [TvMarqueeContent] payload and renders the corner-
 * anchored treatment.
 */
@Composable
fun TvRootHeroBackdrop(
    item: SectionItem?,
    modifier: Modifier = Modifier,
) {
    val content = remember(item?.contentId) {
        item?.let { TvMarqueeContent.from(it, rowTitle = "") }
    }
    TvRootHeroBackdrop(content = content, modifier = modifier)
}

private const val ArtWidthFraction = 0.64f
private const val ArtHeightFraction = 0.70f
private val TopScrimHeight = 190.dp
