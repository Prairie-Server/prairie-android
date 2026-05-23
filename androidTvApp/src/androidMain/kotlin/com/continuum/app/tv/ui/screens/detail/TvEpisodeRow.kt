package com.continuum.app.tv.ui.screens.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.continuum.app.model.catalog.EpisodeListItem
import com.continuum.app.common.ui.components.ThumbhashImage
import com.continuum.app.tv.ui.theme.ProgressFill

/**
 * Horizontal episode row inside the series detail screen. Looks like:
 *
 *   [16:9 still] | Episode 1 · "Pilot"        [SxxExx]
 *                | Overview snippet (2 lines)
 *                | 20m · [progress bar if resuming]
 *
 * Focusing a row scales it up and draws a highlight border via the native
 * `.card` behavior of [Card]. OK plays the episode.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvEpisodeRow(
    episode: EpisodeListItem,
    onPlayClick: () -> Unit,
    onDetailClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(8.dp)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Card(
            onClick = onDetailClick,
            shape = CardDefaults.shape(shape = cardShape),
            scale = CardDefaults.scale(focusedScale = 1f),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(3.dp, Color.White),
                    shape = cardShape,
                ),
            ),
            glow = CardDefaults.glow(
                focusedGlow = Glow(
                    elevationColor = Color.White.copy(alpha = 0.25f),
                    elevation = 20.dp,
                ),
            ),
            modifier = Modifier
                .weight(1f)
                .height(180.dp),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(16f / 9f),
                ) {
                    ThumbhashImage(
                        url = episode.stillUrl,
                        thumbhash = episode.stillThumbhash,
                        contentDescription = episode.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    val progress = episode.progressFraction()
                    if (progress != null && progress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .align(Alignment.BottomCenter)
                                .background(Color.Black.copy(alpha = 0.4f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .height(3.dp)
                                    .background(ProgressFill),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(20.dp))
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 20.dp, horizontal = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "S${episode.seasonNumber.toString().padStart(2, '0')}E${episode.episodeNumber.toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.52f),
                        )
                        episode.title?.let { title ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(720.dp),
                            )
                        }
                        episode.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = overview,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.72f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(720.dp),
                            )
                        }
                    }
                    if (episode.runtime > 0) {
                        Text(
                            text = "${episode.runtime} min",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.52f),
                        )
                    }
                }
            }
        }

        Button(
            onClick = onPlayClick,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp),
            modifier = Modifier
                .widthIn(min = 170.dp)
                .height(72.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Play",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

private fun EpisodeListItem.progressFraction(): Float? {
    val user = userData ?: return null
    val pos = user.positionSeconds ?: return null
    val dur = user.durationSeconds ?: return null
    if (dur <= 0) return null
    return (pos / dur).toFloat().coerceIn(0f, 1f)
}
