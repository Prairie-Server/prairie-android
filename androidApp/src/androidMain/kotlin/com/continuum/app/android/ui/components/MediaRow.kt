package com.continuum.app.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import com.continuum.app.model.catalog.isBookLikeItemType
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.continuum.app.model.section.SectionItem
import com.continuum.app.overlays.OverlayDataExtractor

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
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    cardActions: (SectionItem) -> MediaCardActions = { MediaCardActions() },
) {
    Column(modifier = modifier) {
        // iOS MediaRow header: optional leading icon (16pt semibold onSurface,
        // 6pt to the title), continuumHeadline (16sp) title, plain caption
        // "See All" at onSurface 0.6 — no chevron. rowVerticalSpacing =
        // smallPadding (8dp) below the header.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (onSeeAllClick != null) {
                Text(
                    text = "See All",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.clickable(onClick = onSeeAllClick),
                )
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
                        val isBook = isBookLikeItemType(item.type)
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
                            overlayIcon = if (isBook) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.PlayArrow,
                            overlayContentDescription = if (isBook) "Read" else "Play",
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
                            overlay = OverlayDataExtractor.fromSectionItem(item),
                            actions = cardActions(item),
                        )
                    }
                }
            }
        }
    }
}
