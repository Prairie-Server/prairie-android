package com.continuum.app.tv.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.continuum.app.common.ui.components.ThumbhashImage
import com.continuum.app.model.section.SectionItem

/**
 * 760dp hero region + 420dp fade extension. Mirrors the tvOS
 * `heroHeight + fadeExtension` constants in `TVRootHeroBackdrop.swift` so the
 * blurred backdrop covers the hero band and gracefully tails into the next row.
 */
val TvHeroBackdropTotalHeight = 590.dp

/**
 * Page-level blurred backdrop for tvOS-style hero pages. Mirrors
 * `TVRootHeroBackdrop.swift`: full-bleed artwork at the top fades into the
 * OLED background through a masked vertical gradient that extends ~420dp
 * below the hero zone. When [item] is null the background stays flat black.
 */
@Composable
fun TvRootHeroBackdrop(
    item: SectionItem?,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        targetState = item?.contentId,
        modifier = modifier
            .fillMaxWidth()
            .height(TvHeroBackdropTotalHeight),
        label = "tvRootHeroBackdrop",
    ) { id ->
        if (id == null || item == null) {
            Spacer(modifier = Modifier.fillMaxSize())
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                ThumbhashImage(
                    url = item.backdropUrl ?: item.posterUrl,
                    thumbhash = item.backdropThumbhash ?: item.posterThumbhash,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.04f
                            scaleY = 1.04f
                        }
                        .blur(22.dp),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.54f),
                                1f to Color.Transparent,
                            ),
                        ),
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.34f)),
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.42f to Color.Transparent,
                                0.66f to MaterialTheme.colorScheme.background.copy(alpha = 0.30f),
                                0.86f to MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                                1f to MaterialTheme.colorScheme.background,
                            ),
                        ),
                )

                val tintState = LocalAmbientBackdropTint.current
                val targetAccent = tintState.accent ?: Color.Transparent
                val animatedAccent by animateColorAsState(
                    targetValue = targetAccent,
                    animationSpec = tween(durationMillis = 600),
                    label = "tvRootHeroBackdropAccent",
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(animatedAccent.copy(alpha = 0.18f)),
                )
            }
        }
    }
}
