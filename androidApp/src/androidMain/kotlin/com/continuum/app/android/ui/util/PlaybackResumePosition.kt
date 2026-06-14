package com.continuum.app.android.ui.util

import com.continuum.app.model.catalog.EpisodeListItem
import com.continuum.app.model.catalog.LeafItemUserData
import com.continuum.app.model.section.SectionItem

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
