package com.continuum.app.tv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.continuum.app.common.ui.components.ThumbhashImage
import com.continuum.app.tv.ui.theme.DarkBackground
import com.continuum.app.tv.ui.theme.HeroDimens
import com.continuum.app.tv.ui.theme.Spacing
import com.continuum.app.tv.ui.theme.heroDisplay
import com.continuum.app.tv.ui.theme.heroMeta
import com.continuum.app.tv.ui.theme.sectionEyebrow

/**
 * Tokens used by the hero facts row. Plain `Text` items get `·` separators
 * between them; `Chip` renders an outlined pill (e.g. 4K / HDR / ATMOS / CC).
 */
internal sealed class TvHeroFactToken {
    data class TextToken(val value: String) : TvHeroFactToken()
    data class Chip(val value: String) : TvHeroFactToken()
}

/**
 * Full-bleed cinematic hero for the Android TV detail screen. Mirrors the
 * tvOS `TVDetailHero` 1:1: a 720dp tall backdrop, a 4-stop horizontal
 * darkening on the left, a soft vertical fade into the rail body at the
 * bottom, a 1200dp-wide content column on the left, and a quiet
 * "Starring …" line on the right at mid-height.
 *
 * The action row is wrapped in `focusGroup()` so D-pad up from any rail
 * below lands cleanly back on whichever pill was previously focused.
 */
@Composable
internal fun TvDetailHero(
    title: String,
    seriesTitle: String?,
    logoUrl: String?,
    backdropUrl: String?,
    backdropThumbhash: String?,
    eyebrow: String?,
    sourceTokens: List<String>,
    ratingChip: String?,
    overview: String?,
    factsLine: List<TvHeroFactToken>,
    starringText: String?,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val heroHeight = HeroDimens.Height
    val contentMaxWidth = 1200.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight),
    ) {
        // Backdrop
        ThumbhashImage(
            url = backdropUrl,
            thumbhash = backdropThumbhash,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Heavy left-side darkening — clears toward the right so the imagery
        // breathes while text stays legible.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.00f to Color.Black.copy(alpha = 0.92f),
                        0.22f to Color.Black.copy(alpha = 0.70f),
                        0.55f to Color.Black.copy(alpha = 0.35f),
                        0.88f to Color.Transparent,
                    ),
                ),
        )

        // Soft bottom fade that hands off into the rail body underneath, so a
        // hint of the next rail peeks through the seam.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Transparent,
                        0.55f to Color.Transparent,
                        0.85f to DarkBackground.copy(alpha = 0.55f),
                        1.00f to DarkBackground,
                    ),
                ),
        )

        // Right-side starring overlay, placed in the upper hero band like tvOS.
        starringText?.takeIf { it.isNotBlank() }?.let { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                ),
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 124.dp, end = Spacing.safeArea + 32.dp)
                    .widthIn(max = 260.dp),
            )
        }

        // Editorial column + action row in the tvOS title band.
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = Spacing.safeArea, end = Spacing.safeArea, top = 130.dp)
                .widthIn(max = contentMaxWidth),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EditorialColumn(
                title = title,
                seriesTitle = seriesTitle,
                logoUrl = logoUrl,
                eyebrow = eyebrow,
                sourceTokens = sourceTokens,
                ratingChip = ratingChip,
                overview = overview,
                factsLine = factsLine,
            )

            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .focusGroup(),
            ) {
                actions()
            }
        }
    }
}

@Composable
private fun EditorialColumn(
    title: String,
    seriesTitle: String?,
    logoUrl: String?,
    eyebrow: String?,
    sourceTokens: List<String>,
    ratingChip: String?,
    overview: String?,
    factsLine: List<TvHeroFactToken>,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        if (!eyebrow.isNullOrBlank()) {
            HeroEyebrowPill(text = eyebrow)
        }

        TitleBlock(
            title = title,
            seriesTitle = seriesTitle,
            logoUrl = logoUrl,
        )

        if (sourceTokens.isNotEmpty() || !ratingChip.isNullOrBlank()) {
            SourceRow(tokens = sourceTokens, ratingChip = ratingChip)
        }

        overview?.takeIf { it.isNotBlank() }?.let { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 700.dp),
            )
        }

        if (factsLine.isNotEmpty()) {
            FactsRow(tokens = factsLine)
        }
    }
}

@Composable
private fun TitleBlock(
    title: String,
    seriesTitle: String?,
    logoUrl: String?,
) {
    val seriesContext = seriesTitle?.trim()?.takeIf { it.isNotEmpty() }

    when {
        seriesContext != null -> EpisodeHierarchyTitle(seriesTitle = seriesContext, episodeTitle = title)
        !logoUrl.isNullOrBlank() -> AsyncImage(
            model = logoUrl,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .height(180.dp)
                .widthIn(max = 620.dp),
        )
        else -> HeroTextTitle(title = title)
    }
}

@Composable
private fun HeroTextTitle(title: String) {
    val parts = remember(title) { splitDisplayTitle(title) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = parts.first.uppercase(),
            style = heroDisplay,
            color = Color.White,
            maxLines = 2,
        )
        parts.second?.let { sub ->
            Text(
                text = sub.uppercase(),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp,
                ),
                color = Color.White.copy(alpha = 0.95f),
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun EpisodeHierarchyTitle(seriesTitle: String, episodeTitle: String) {
    val parts = remember(episodeTitle) { splitDisplayTitle(episodeTitle) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = seriesTitle.uppercase(),
            style = heroDisplay,
            color = Color.White,
            maxLines = 2,
        )
        Text(
            text = parts.first,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp,
                lineHeight = 28.sp,
            ),
            color = Color.White.copy(alpha = 0.94f),
            maxLines = 2,
        )
        parts.second?.let { sub ->
            Text(
                text = sub.uppercase(),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp,
                    fontSize = 20.sp,
                ),
                color = Color.White.copy(alpha = 0.82f),
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun HeroEyebrowPill(text: String) {
    Box(
        modifier = Modifier
            .background(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(100.dp),
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(100.dp),
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = sectionEyebrow,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SourceRow(tokens: List<String>, ratingChip: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tokens.forEachIndexed { index, token ->
            if (index > 0) {
                Text(
                    text = "·",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
            Text(
                text = token,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 1,
            )
        }
        if (!ratingChip.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(2.dp))
            RatingChip(text = ratingChip)
        }
    }
}

@Composable
private fun FactsRow(tokens: List<TvHeroFactToken>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        tokens.forEachIndexed { index, token ->
            val previous = tokens.getOrNull(index - 1)
            if (index > 0 && token is TvHeroFactToken.TextToken && previous is TvHeroFactToken.TextToken) {
                Text(
                    text = "·",
                    style = heroMeta,
                    color = Color.White.copy(alpha = 0.45f),
                )
            }
            when (token) {
                is TvHeroFactToken.TextToken -> Text(
                    text = token.value,
                    style = heroMeta,
                    color = Color.White.copy(alpha = 0.88f),
                    maxLines = 1,
                )
                is TvHeroFactToken.Chip -> QualityChip(text = token.value)
            }
        }
    }
}

@Composable
private fun RatingChip(text: String) {
    Box(
        modifier = Modifier
            .border(
                width = 1.5.dp,
                color = Color.White.copy(alpha = 0.7f),
                shape = RoundedCornerShape(5.dp),
            )
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 1.0.sp,
                fontSize = 12.sp,
            ),
            color = Color.White,
            maxLines = 1,
        )
    }
}

@Composable
private fun QualityChip(text: String) {
    Box(
        modifier = Modifier
            .border(
                width = 1.2.dp,
                color = Color.White.copy(alpha = 0.65f),
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 1.0.sp,
                fontSize = 10.sp,
            ),
            color = Color.White,
            maxLines = 1,
        )
    }
}

private fun splitDisplayTitle(raw: String): Pair<String, String?> {
    val separators = listOf(": ", " — ", " – ", " - ")
    for (sep in separators) {
        val idx = raw.indexOf(sep)
        if (idx > 0) {
            val head = raw.substring(0, idx).trim()
            val tail = raw.substring(idx + sep.length).trim()
            if (head.isNotEmpty() && tail.isNotEmpty()) return head to tail
        }
    }
    return raw to null
}
