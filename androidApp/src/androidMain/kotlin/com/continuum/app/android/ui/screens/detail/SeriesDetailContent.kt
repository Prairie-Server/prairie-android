package com.continuum.app.android.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.continuum.app.android.ui.theme.ContinuumBackground
import com.continuum.app.android.ui.util.rememberDominantColor
import com.continuum.app.model.catalog.EpisodeListItem
import com.continuum.app.model.catalog.ItemDetail
import com.continuum.app.model.catalog.Season

/**
 * Phone series detail. Cinematic backdrop hero up top, then a scrollable
 * body of season chips + episode list, cast, and the details list.
 */
@Composable
fun SeriesDetailContent(
    detail: ItemDetail,
    seasons: List<Season>,
    selectedSeasonNumber: Int,
    episodes: List<EpisodeListItem>,
    isLoadingEpisodes: Boolean,
    isFavorite: Boolean,
    isInWatchlist: Boolean,
    nextEpisodeLabel: String?,
    onPlayClick: () -> Unit,
    onEpisodePlayClick: (String) -> Unit,
    onEpisodeDetailClick: (String) -> Unit,
    onSeasonSelected: (Int) -> Unit,
    onFavoriteClick: () -> Unit,
    onWatchlistClick: () -> Unit,
    onPersonClick: (String) -> Unit,
    onItemDetailClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dominantColor by rememberDominantColor(detail.backdropUrl, fallback = ContinuumBackground)

    val eyebrow = HeroMetadata.seriesEyebrow(detail)
    val sourceTokens = HeroMetadata.seriesSourceTokens(detail)
    val factsLine = HeroMetadata.seriesFactsLine(detail)

    val selectedSeason = seasons.firstOrNull { it.seasonNumber == selectedSeasonNumber }
    val episodeCountSubtitle = selectedSeason?.episodeCount?.takeIf { it > 0 }?.let { count ->
        "$count episode${if (count == 1) "" else "s"}"
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ContinuumBackground)
            .background(detailScreenBackgroundBrush(dominantColor)),
        verticalArrangement = Arrangement.spacedBy(LargePadding),
    ) {
        item {
            DetailHero(
                detail = detail,
                eyebrow = eyebrow,
                sourceTokens = sourceTokens,
                factsLine = factsLine,
                dominantColor = dominantColor,
            ) {
                HeroActionStack(
                    primaryLabel = computePlayLabel(detail, nextEpisodeLabel),
                    onPlay = onPlayClick,
                    isFavorite = isFavorite,
                    isInWatchlist = isInWatchlist,
                    isWatched = detail.userData?.played == true,
                    onToggleFavorite = onFavoriteClick,
                    onToggleWatchlist = onWatchlistClick,
                    onToggleWatched = { /* no-op until shared API exposes it */ },
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionHeader(
                    label = selectedSeason?.let { "Season ${it.seasonNumber}" } ?: "Episodes",
                    title = "Episodes",
                    trailingText = episodeCountSubtitle,
                )
                if (seasons.size > 1) {
                    SeasonChips(
                        seasons = seasons,
                        selectedSeasonNumber = selectedSeasonNumber,
                        onSeasonSelected = onSeasonSelected,
                    )
                }
                when {
                    isLoadingEpisodes -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    episodes.isEmpty() -> {
                        Text(
                            text = "No episodes available",
                            style = MaterialTheme.typography.bodySmall,
                            color = DetailTertiaryText,
                            modifier = Modifier.padding(horizontal = SafePadding),
                        )
                    }
                    else -> {
                        EpisodeList(
                            episodes = episodes,
                            onEpisodePlayClick = onEpisodePlayClick,
                            onEpisodeDetailClick = onEpisodeDetailClick,
                        )
                    }
                }
            }
        }

        if (detail.cast.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionHeader(label = "Cast", title = "& Crew")
                    CastCrewSection(
                        cast = detail.cast,
                        crew = detail.crew,
                        onPersonClick = onPersonClick,
                    )
                }
            }
        }

        if (detail.genres.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionHeader(label = "Tags", title = "Genres")
                    GenrePillRow(genres = detail.genres)
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionHeader(label = "Info", title = "Details")
                DetailFactsList(detail = detail)
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionHeader(label = "Recommended", title = "More Like This")
                SimilarRail(
                    contentId = detail.contentId,
                    onSelect = onItemDetailClick,
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
