package com.continuum.app.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.continuum.app.model.section.SectionItem

enum class CardStyle { Poster, Backdrop }

/**
 * Horizontal row of media cards with a section headline above.
 *
 * Mirrors iOS `SectionRow` (HomeView.swift): 16sp semibold headline,
 * 12dp gap between cards, 16dp horizontal screen padding.
 */
@Composable
fun MediaRow(
    title: String,
    items: List<SectionItem>,
    onItemClick: (String) -> Unit,
    onSeeAllClick: (() -> Unit)? = null,
    showProgress: Boolean = false,
    cardStyle: CardStyle = CardStyle.Poster,
    modifier: Modifier = Modifier,
    cardActions: (SectionItem) -> MediaCardActions = { MediaCardActions() },
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (onSeeAllClick != null) {
                Row(
                    modifier = Modifier.clickable(onClick = onSeeAllClick),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "See All",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = items,
                key = { it.contentId },
            ) { item ->
                val pos = item.positionSeconds
                val dur = item.durationSeconds
                val progress = if (showProgress && pos != null && dur != null && dur > 0) {
                    (pos / dur).toFloat()
                } else {
                    null
                }

                when (cardStyle) {
                    CardStyle.Backdrop -> {
                        val remainingMinutes = if (pos != null && dur != null && dur > 0) {
                            ((dur - pos) / 60.0).toInt()
                        } else {
                            null
                        }
                        // For episodes, posterUrl is the per-episode still; for movies fall
                        // back to the backdrop. Mirrors the iOS BackdropCard logic.
                        val isEpisode = item.seriesTitle != null
                        val imageUrl = if (isEpisode) {
                            item.posterUrl ?: item.backdropUrl
                        } else {
                            item.backdropUrl ?: item.posterUrl
                        }
                        val imageThumbhash = if (isEpisode) {
                            item.posterThumbhash ?: item.backdropThumbhash
                        } else {
                            item.backdropThumbhash ?: item.posterThumbhash
                        }
                        BackdropCard(
                            title = item.title,
                            backdropUrl = imageUrl,
                            backdropThumbhash = imageThumbhash,
                            seriesTitle = item.seriesTitle,
                            seasonNumber = item.seasonNumber,
                            episodeNumber = item.episodeNumber,
                            progress = progress,
                            remainingMinutes = remainingMinutes,
                            onClick = { onItemClick(item.contentId) },
                            userState = item.userState,
                            actions = cardActions(item),
                        )
                    }
                    CardStyle.Poster -> {
                        MediaCard(
                            title = item.title,
                            posterUrl = item.posterUrl,
                            posterThumbhash = item.posterThumbhash,
                            year = item.year,
                            type = item.type,
                            userState = item.userState,
                            progress = progress,
                            onClick = { onItemClick(item.contentId) },
                            actions = cardActions(item),
                        )
                    }
                }
            }
        }
    }
}
