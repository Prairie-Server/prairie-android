package org.siloserver.silo.android.ui.util

import org.siloserver.silo.model.catalog.EpisodeListItem
import org.siloserver.silo.model.catalog.LeafItemUserData
import org.siloserver.silo.model.section.SectionItem

private const val MinimumResumeSeconds = 30.0

fun playbackResumePosition(positionSeconds: Double?, played: Boolean): Double? =
    positionSeconds?.takeIf { it.isFinite() && it > MinimumResumeSeconds && !played }

fun playbackResumePosition(userData: LeafItemUserData?): Double? =
    playbackResumePosition(
        positionSeconds = userData?.positionSeconds,
        played = userData?.played == true,
    )

fun playbackResumePosition(item: SectionItem): Double? =
    playbackResumePosition(
        positionSeconds = item.positionSeconds,
        played = item.userState?.played == true,
    )

fun playbackResumePosition(episode: EpisodeListItem): Double? =
    playbackResumePosition(
        positionSeconds = episode.userData?.positionSeconds,
        played = episode.userData?.played == true,
    )
