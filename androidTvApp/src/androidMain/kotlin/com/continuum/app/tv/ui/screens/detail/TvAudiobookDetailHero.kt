package com.continuum.app.tv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.continuum.app.common.ui.components.ThumbhashImage
import com.continuum.app.model.catalog.ItemDetail
import com.continuum.app.tv.ui.components.TvPoster
import com.continuum.app.tv.ui.components.TvPrimaryPillButton
import com.continuum.app.tv.ui.components.TvSecondaryPillButton
import com.continuum.app.tv.ui.screens.audiobook.formatAudiobookTime
import com.continuum.app.tv.ui.theme.DarkBackground
import com.continuum.app.tv.ui.theme.DarkSurface
import com.continuum.app.tv.ui.theme.Spacing

@Composable
internal fun TvAudiobookDetailHero(
    detail: ItemDetail,
    state: TvItemDetailUiState,
    playFocus: FocusRequester,
    onPlay: (contentId: String, fileId: Int?, audioTrackIndex: Int?, subtitleTrackIndex: Int?, itemType: String?, resumePositionSeconds: Double?) -> Unit,
    overview: String?,
    modifier: Modifier = Modifier,
) {
    val selectedFileId = state.selectedFileId ?: detail.versions.firstOrNull()?.fileId
    val resumePosition = audiobookResumePositionSeconds(detail)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 380.dp),
    ) {
        if (!detail.posterUrl.isNullOrBlank() || !detail.posterThumbhash.isNullOrBlank()) {
            ThumbhashImage(
                url = detail.posterUrl,
                thumbhash = detail.posterThumbhash,
                contentDescription = detail.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .blur(30.dp)
                    .alpha(0.5f),
            )
        } else {
            Box(modifier = Modifier.matchParentSize().background(DarkSurface))
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to DarkBackground.copy(alpha = 0.55f),
                        0.50f to DarkBackground.copy(alpha = 0.30f),
                        1.00f to DarkBackground,
                    ),
                ),
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = Spacing.safeArea, vertical = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            TvPoster(
                imageUrl = detail.posterUrl,
                contentDescription = detail.title,
                modifier = Modifier
                    .size(180.dp)
                    .shadow(20.dp, RoundedCornerShape(14.dp)),
                cornerRadius = 14.dp,
            )

            Column(
                modifier = Modifier.widthIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AudiobookEyebrow()

                Text(
                    text = detail.title,
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 2,
                )

                audiobookMetadataLine(detail).takeIf { it.isNotBlank() }?.let { metadata ->
                    Text(
                        text = metadata,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 2,
                    )
                }

                detail.audiobook?.publisher?.takeIf { it.isNotBlank() }?.let { publisher ->
                    Text(
                        text = publisher,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.58f),
                        maxLines = 1,
                    )
                }

                overview?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 21.sp),
                        color = Color.White.copy(alpha = 0.76f),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvPrimaryPillButton(
                        icon = Icons.Filled.PlayArrow,
                        title = audiobookPlayLabel(resumePosition),
                        focusRequester = playFocus,
                        onClick = {
                            onPlay(
                                detail.contentId,
                                selectedFileId,
                                state.selectedAudioIndex,
                                state.selectedSubtitleIndex,
                                detail.type,
                                resumePosition,
                            )
                        },
                    )
                    TvSecondaryPillButton(
                        icon = Icons.Filled.Replay,
                        title = "Start Over",
                        onClick = {
                            onPlay(
                                detail.contentId,
                                selectedFileId,
                                state.selectedAudioIndex,
                                state.selectedSubtitleIndex,
                                detail.type,
                                0.0,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AudiobookEyebrow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 17.dp, height = 2.dp)
                .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(1.dp)),
        )
        Text(
            text = "AUDIOBOOK",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            ),
            color = Color.White.copy(alpha = 0.78f),
        )
    }
}

private fun audiobookMetadataLine(detail: ItemDetail): String {
    val audiobook = detail.audiobook
    val duration = audiobook?.totalDurationSeconds?.toDouble()
        ?: detail.userData?.durationSeconds
        ?: detail.versions.sumOf { it.duration }.takeIf { it > 0.0 }
    return listOfNotNull(
        audiobook?.authorNames,
        audiobook?.narratorNames?.let { "Narrated by $it" },
        audiobookRuntimeLabel(duration),
    )
        .filter { it.isNotBlank() }
        .joinToString("  ")
}

private fun audiobookRuntimeLabel(seconds: Double?): String? {
    val total = seconds?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    val hours = (total / 3600).toInt()
    val minutes = ((total % 3600) / 60).toInt()
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> null
    }
}

private fun audiobookResumePositionSeconds(detail: ItemDetail): Double? {
    val userData = detail.userData ?: return null
    val position = userData.positionSeconds ?: return null
    val duration = userData.durationSeconds ?: return null
    if (position <= 30 || duration <= 0 || position >= duration - 5) return null
    return position
}

private fun audiobookPlayLabel(resumePosition: Double?): String {
    return if (resumePosition != null) {
        "Resume ${formatAudiobookTime(resumePosition)}"
    } else {
        "Play"
    }
}
