package com.continuum.app.android.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.continuum.app.android.ui.theme.ContinuumBackground
import com.continuum.app.android.ui.util.rememberDominantColor
import com.continuum.app.model.catalog.ItemDetail

/**
 * Phone movie / episode detail. Cinematic backdrop hero up top, then a
 * scrollable body of cast, details, and (for episodes) a series shortcut.
 *
 * Mirrors `MovieDetailContent.swift` semantically — same hero metadata,
 * same primary play + circle action row, same single consolidated
 * version selector — sized for touch.
 */
@Composable
fun MovieDetailContent(
    detail: ItemDetail,
    isFavorite: Boolean,
    isInWatchlist: Boolean,
    selectedVersionIndex: Int,
    selectedAudioIndex: Int,
    selectedSubtitleIndex: Int,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onWatchlistClick: () -> Unit,
    onVersionSelected: (Int) -> Unit,
    onAudioSelected: (Int) -> Unit,
    onSubtitleSelected: (Int) -> Unit,
    onPersonClick: (String) -> Unit,
    onItemDetailClick: (String) -> Unit,
    onSeriesClick: (() -> Unit)? = null,
    onSeasonClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showMediaInfo by remember { mutableStateOf(false) }
    var showVersionPicker by remember { mutableStateOf(false) }
    var showAudioPicker by remember { mutableStateOf(false) }
    var showSubtitlePicker by remember { mutableStateOf(false) }

    val dominantColor by rememberDominantColor(detail.backdropUrl, fallback = ContinuumBackground)

    val selectedVersion = detail.versions.getOrNull(selectedVersionIndex)
    val audioTracks = selectedVersion?.audioTracks.orEmpty()
    val subtitleTracks = selectedVersion?.subtitleTracks.orEmpty()
    val hasMultipleVersions = detail.versions.size > 1
    val hasAudioOptions = audioTracks.size > 1
    val hasSubtitleOptions = subtitleTracks.isNotEmpty()
    val hasMediaInfo = detail.versions.isNotEmpty()
    val hasOverflow = hasAudioOptions || hasSubtitleOptions || hasMediaInfo ||
        onSeriesClick != null || onSeasonClick != null

    val eyebrow = if (detail.type == "episode") {
        HeroMetadata.episodeEyebrow(detail)
    } else {
        HeroMetadata.movieEyebrow(detail)
    }
    val sourceTokens = HeroMetadata.movieSourceTokens(detail)
    val factsLine = HeroMetadata.movieFactsLine(detail)

    val versionLabel = if (hasMultipleVersions && selectedVersion != null) {
        formatVersionMenuLabel(selectedVersion)
    } else {
        null
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
                    primaryLabel = computePlayLabel(detail),
                    onPlay = onPlayClick,
                    isFavorite = isFavorite,
                    isInWatchlist = isInWatchlist,
                    isWatched = detail.userData?.played == true,
                    onToggleFavorite = onFavoriteClick,
                    onToggleWatchlist = onWatchlistClick,
                    onToggleWatched = { /* no-op until shared API exposes it */ },
                    versionLabel = versionLabel,
                    onVersionClick = if (hasMultipleVersions) {
                        { showVersionPicker = true }
                    } else {
                        null
                    },
                    overflow = if (hasOverflow) {
                        { dismiss ->
                            if (hasAudioOptions) {
                                DropdownMenuItem(
                                    text = { Text("Audio") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.AudioFile, contentDescription = null)
                                    },
                                    trailingIcon = {
                                        Text(formatAudioLabel(audioTracks.getOrNull(selectedAudioIndex)))
                                    },
                                    onClick = {
                                        dismiss()
                                        showAudioPicker = true
                                    },
                                )
                            }
                            if (hasSubtitleOptions) {
                                DropdownMenuItem(
                                    text = { Text("Subtitles") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.ClosedCaption, contentDescription = null)
                                    },
                                    trailingIcon = {
                                        Text(formatSubtitleLabel(subtitleTracks.getOrNull(selectedSubtitleIndex)))
                                    },
                                    onClick = {
                                        dismiss()
                                        showSubtitlePicker = true
                                    },
                                )
                            }
                            if (hasMediaInfo) {
                                DropdownMenuItem(
                                    text = { Text("Media Info") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Info, contentDescription = null)
                                    },
                                    onClick = {
                                        dismiss()
                                        showMediaInfo = true
                                    },
                                )
                            }
                            if (onSeasonClick != null) {
                                DropdownMenuItem(
                                    text = { Text("Go to Season") },
                                    onClick = {
                                        dismiss()
                                        onSeasonClick()
                                    },
                                )
                            }
                            if (onSeriesClick != null) {
                                DropdownMenuItem(
                                    text = { Text("Go to Series") },
                                    onClick = {
                                        dismiss()
                                        onSeriesClick()
                                    },
                                )
                            }
                        }
                    } else {
                        null
                    },
                )
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

        // Hide the similar rail on episode pages — viewers usually want
        // the next episode, not a tangentially related title.
        if (detail.type != "episode") {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionHeader(label = "Recommended", title = "More Like This")
                    SimilarRail(
                        contentId = detail.contentId,
                        onSelect = onItemDetailClick,
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    if (showVersionPicker) {
        VersionPickerSheet(
            versions = detail.versions,
            selectedIndex = selectedVersionIndex,
            onSelect = { index ->
                onVersionSelected(index)
                showVersionPicker = false
            },
            onDismiss = { showVersionPicker = false },
        )
    }

    if (showAudioPicker && hasAudioOptions) {
        AudioPickerSheet(
            tracks = audioTracks,
            selectedIndex = selectedAudioIndex,
            onSelect = { index ->
                onAudioSelected(index)
                showAudioPicker = false
            },
            onDismiss = { showAudioPicker = false },
        )
    }

    if (showSubtitlePicker && hasSubtitleOptions) {
        SubtitlePickerSheet(
            tracks = subtitleTracks,
            selectedIndex = selectedSubtitleIndex,
            onSelect = { index ->
                onSubtitleSelected(index)
                showSubtitlePicker = false
            },
            onDismiss = { showSubtitlePicker = false },
        )
    }

    if (showMediaInfo && hasMediaInfo) {
        MediaInfoSheet(
            versions = detail.versions,
            onDismiss = { showMediaInfo = false },
        )
    }
}
