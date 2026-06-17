package com.continuum.app.tv.ui.screens.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.continuum.app.common.ui.components.ThumbhashImage
import com.continuum.app.model.catalog.EpisodeListItem
import com.continuum.app.tv.ui.theme.ContinuumBlueGlow
import com.continuum.app.tv.ui.theme.DarkSurfaceElevated
import com.continuum.app.tv.ui.theme.ProgressFill
import com.continuum.app.tv.ui.theme.Spacing

/**
 * Horizontal episode rail used on the series and episode detail screens. Each
 * card is a 16:9 still topped by an episode-number eyebrow and a 2-line title
 * snippet. Pressing OK plays (or resumes) the episode directly; long-pressing
 * opens the episode's own detail page (parity with the phone's tap-to-play).
 */
@Composable
internal fun TvDetailEpisodeRail(
    episodes: List<EpisodeListItem>,
    onEpisodeSelected: (EpisodeListItem) -> Unit,
    onEpisodeLongPress: (EpisodeListItem) -> Unit,
    modifier: Modifier = Modifier,
    currentContentId: String? = null,
    firstItemFocusRequester: FocusRequester? = null,
) {
    if (episodes.isEmpty()) return

    val listState = rememberLazyListState()
    val currentIndex = remember(currentContentId, episodes) {
        episodes.indexOfFirst { it.contentId == currentContentId }.takeIf { it >= 0 }
    }

    LaunchedEffect(currentContentId, episodes.size) {
        val target = currentIndex ?: return@LaunchedEffect
        listState.animateScrollToItem(target)
    }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .focusGroup(),
        state = listState,
        contentPadding = PaddingValues(
            horizontal = Spacing.safeArea,
            vertical = 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        items(episodes, key = { it.contentId }) { episode ->
            TvDetailEpisodeCard(
                episode = episode,
                isCurrent = episode.contentId == currentContentId,
                onClick = { onEpisodeSelected(episode) },
                onLongClick = { onEpisodeLongPress(episode) },
                focusRequester = firstItemFocusRequester
                    ?.takeIf { episode.contentId == episodes.first().contentId },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvDetailEpisodeCard(
    episode: EpisodeListItem,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    focusRequester: FocusRequester?,
) {
    val cardWidth = 360.dp
    val stillHeight = 200.dp
    val shape = RoundedCornerShape(10.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(
        modifier = Modifier.width(cardWidth),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            interactionSource = interactionSource,
            shape = ClickableSurfaceDefaults.shape(shape = shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = DarkSurfaceElevated,
                contentColor = Color.White,
                focusedContainerColor = DarkSurfaceElevated,
                focusedContentColor = Color.White,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
            border = ClickableSurfaceDefaults.border(
                border = Border(
                    border = BorderStroke(
                        width = if (isCurrent) 2.dp else 0.dp,
                        color = if (isCurrent) Color.White.copy(alpha = 0.7f) else Color.Transparent,
                    ),
                    shape = shape,
                ),
                focusedBorder = Border(
                    border = BorderStroke(3.dp, Color.White.copy(alpha = 0.95f)),
                    shape = shape,
                ),
            ),
            glow = ClickableSurfaceDefaults.glow(
                focusedGlow = Glow(
                    elevationColor = ContinuumBlueGlow,
                    elevation = 18.dp,
                ),
            ),
            modifier = Modifier
                .width(cardWidth)
                .height(stillHeight)
                .then(
                    if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
                ),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                ThumbhashImage(
                    url = episode.stillUrl,
                    thumbhash = episode.stillThumbhash,
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                if (episode.userData?.played == true) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                    )
                    Box(
                        modifier = Modifier
                            .padding(12.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .align(Alignment.TopEnd),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                episode.progressFraction()?.let { fraction ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .align(Alignment.BottomStart)
                            .background(Color.Black.copy(alpha = 0.6f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(5.dp)
                                .background(ProgressFill),
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = episodeEyebrow(episode),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.0.sp,
                    ),
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                )
                if (isCurrent) {
                    NowViewingTag()
                }
            }

            Text(
                text = episode.title ?: "Episode ${episode.episodeNumber}",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (isFocused || isCurrent) {
                    Color.White
                } else {
                    Color.White.copy(alpha = 0.92f)
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            episode.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 22.sp,
                )
            }
        }
    }
}

@Composable
private fun NowViewingTag() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = "NOW VIEWING",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 1.1.sp,
                fontSize = 16.sp,
            ),
            color = Color.Black,
        )
    }
}

private fun episodeEyebrow(episode: EpisodeListItem): String {
    val base = "EPISODE ${episode.episodeNumber}"
    if (episode.runtime <= 0) return base
    val runtime = if (episode.runtime >= 60) {
        "${episode.runtime / 60}h ${episode.runtime % 60}m"
    } else {
        "${episode.runtime}m"
    }
    return "$base  ·  $runtime"
}

private fun EpisodeListItem.progressFraction(): Float? {
    val user = userData ?: return null
    val pos = user.positionSeconds ?: return null
    val dur = user.durationSeconds ?: return null
    if (dur <= 0 || pos <= 0 || pos >= dur) return null
    return (pos / dur).toFloat().coerceIn(0f, 1f)
}
