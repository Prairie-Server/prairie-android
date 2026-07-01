package org.siloserver.silo.tv.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import org.siloserver.silo.common.ui.components.ThumbhashImage
import org.siloserver.silo.model.section.SectionItem
import org.siloserver.silo.tv.ui.theme.SiloBlue
import org.siloserver.silo.tv.ui.theme.DarkBackground
import org.siloserver.silo.tv.ui.theme.HeroDimens
import org.siloserver.silo.tv.ui.theme.HeroIndicatorInactive
import org.siloserver.silo.tv.ui.theme.HeroScrimBottom
import org.siloserver.silo.tv.ui.theme.HeroScrimLower
import org.siloserver.silo.tv.ui.theme.HeroScrimMid
import org.siloserver.silo.tv.ui.theme.HeroScrimTop
import org.siloserver.silo.tv.ui.theme.Spacing
import org.siloserver.silo.tv.ui.theme.heroDisplay
import org.siloserver.silo.tv.ui.theme.heroMeta

private const val AUTO_ADVANCE_MS = 8_000L

/**
 * Cinematic hero banner. Custom auto-advancing pager — we deliberately avoid
 * `androidx.tv.material3.Carousel` because it consumes LEFT/RIGHT (and other
 * D-pad) events for paging, which prevents the Play ↔ More Info focus
 * navigation and blocks DOWN out of the hero to the first row.
 *
 * Behaviour: cross-fades to the next item every [AUTO_ADVANCE_MS], pauses
 * while the user has focus anywhere inside the hero so the content doesn't
 * shift underneath them, and lets Compose's default focus search handle
 * D-pad navigation (LEFT/RIGHT between the pills, DOWN to the first row).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvFeaturedCarousel(
    items: List<SectionItem>,
    onPlay: (contentId: String) -> Unit,
    onInfo: (contentId: String) -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    onAutoFocusClaimed: () -> Unit = {},
    onFocusEntered: () -> Unit = {},
) {
    if (items.isEmpty()) return
    var activeIndex by remember { mutableStateOf(0) }
    var hasFocus by remember { mutableStateOf(false) }
    val playFocusRequester = remember { FocusRequester() }

    // Claim initial focus on Play the first time the hero appears.
    if (autoFocus) {
        LaunchedEffect(Unit) {
            runCatching { playFocusRequester.requestFocus() }
            onAutoFocusClaimed()
        }
    }

    // Auto-advance only when the user is not interacting with the hero. This
    // keeps the backdrop/text from changing while they read metadata or are
    // about to click Play.
    LaunchedEffect(activeIndex, hasFocus, items.size) {
        if (hasFocus || items.size <= 1) return@LaunchedEffect
        delay(AUTO_ADVANCE_MS)
        activeIndex = (activeIndex + 1) % items.size
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HeroDimens.Height)
            .onFocusChanged {
                val nowFocused = it.hasFocus
                if (nowFocused && !hasFocus) {
                    onFocusEntered()
                }
                hasFocus = nowFocused
            },
    ) {
        AnimatedContent(
            targetState = activeIndex.coerceIn(0, items.lastIndex),
            transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(500)) },
            label = "heroCrossfade",
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            FeaturedHeroSlide(
                item = items[index],
                onPlay = { onPlay(items[index].contentId) },
                onInfo = { onInfo(items[index].contentId) },
                playFocusRequester = if (index == activeIndex) playFocusRequester else null,
            )
        }

        if (items.size > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Spacing.xxl, bottom = Spacing.xl),
            ) {
                repeat(items.size) { index ->
                    val isActive = index == activeIndex
                    Box(
                        modifier = Modifier
                            .size(if (isActive) 12.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) SiloBlue else HeroIndicatorInactive,
                            ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FeaturedHeroSlide(
    item: SectionItem,
    onPlay: () -> Unit,
    onInfo: () -> Unit,
    playFocusRequester: FocusRequester?,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Full-bleed backdrop.
        ThumbhashImage(
            url = item.backdropUrl ?: item.posterUrl,
            thumbhash = item.backdropThumbhash ?: item.posterThumbhash,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // 4-stop vertical scrim fading to app background.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to HeroScrimTop,
                        0.4f to HeroScrimMid,
                        0.75f to HeroScrimLower,
                        0.95f to HeroScrimBottom,
                        1f to DarkBackground,
                    ),
                ),
        )
        // Left-side horizontal overlay for title legibility.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to DarkBackground.copy(alpha = 0.72f),
                        0.4f to Color.Transparent,
                    ),
                ),
        )

        HeroContentColumn(
            item = item,
            onPlay = onPlay,
            onInfo = onInfo,
            playFocusRequester = playFocusRequester,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.48f)
                .padding(
                    start = Spacing.heroTopSafe,
                    end = Spacing.xl,
                    bottom = Spacing.xxl,
                ),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HeroContentColumn(
    item: SectionItem,
    onPlay: () -> Unit,
    onInfo: () -> Unit,
    playFocusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (!item.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = item.logoUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomStart,
                modifier = Modifier
                    .heightIn(min = 120.dp, max = 180.dp)
                    .widthIn(max = 520.dp),
            )
        } else {
            Text(
                text = item.title,
                style = heroDisplay,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        InlineMetadataRow(item = item)

        item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
            Text(
                text = overview,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.xs))

        // Explicit focus wiring so LEFT/RIGHT on Play/More Info is handled
        // before the outer Carousel consumes the key events for paging.
        val moreInfoFocusRequester = remember { FocusRequester() }
        val localPlayFocusRequester = playFocusRequester ?: remember { FocusRequester() }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvHeroActionPill(
                label = "Play",
                icon = Icons.Filled.PlayArrow,
                variant = TvPillVariant.Filled,
                onClick = onPlay,
                focusRequester = localPlayFocusRequester,
                modifier = Modifier.focusProperties { right = moreInfoFocusRequester },
            )
            TvHeroActionPill(
                label = "More Info",
                icon = Icons.Filled.Info,
                variant = TvPillVariant.Hollow,
                onClick = onInfo,
                modifier = Modifier
                    .focusRequester(moreInfoFocusRequester)
                    .focusProperties { left = localPlayFocusRequester },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun InlineMetadataRow(item: SectionItem) {
    val labels = buildList {
        if (item.year > 0) add(item.year.toString())
        item.ratingImdb?.takeIf { it > 0 }?.let { add("★ ${formatRating(it)}") }
        item.genres.take(2).forEach { add(it) }
    }
    if (labels.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            if (index > 0) {
                Text(
                    text = "·",
                    style = heroMeta,
                    color = Color.White.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = label,
                style = heroMeta,
                color = Color.White.copy(alpha = 0.92f),
            )
        }
    }
}

private fun formatRating(value: Double): String {
    val rounded = (value * 10).toInt() / 10.0
    return rounded.toString()
}
