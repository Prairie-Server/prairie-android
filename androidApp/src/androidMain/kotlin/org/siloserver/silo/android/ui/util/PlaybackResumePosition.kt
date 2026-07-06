package org.siloserver.silo.android.ui.util

import org.siloserver.silo.model.catalog.EpisodeListItem
import org.siloserver.silo.model.catalog.LeafItemUserData
import org.siloserver.silo.model.section.SectionItem

private const val MinimumResumeSeconds = 30.0
private const val ResumeEndBufferSeconds = 5.0

fun playbackResumePosition(positionSeconds: Double?, durationSeconds: Double?): Double? =
    positionSeconds?.takeIf { position ->
        position.isFinite() &&
            position > MinimumResumeSeconds &&
            !isAtOrPastResumeEnd(position, durationSeconds)
    }

fun playbackResumePosition(userData: LeafItemUserData?): Double? =
    playbackResumePosition(
        positionSeconds = userData?.positionSeconds,
        durationSeconds = userData?.durationSeconds,
    )

fun playbackResumePosition(item: SectionItem): Double? =
    playbackResumePosition(
        positionSeconds = item.positionSeconds,
        durationSeconds = item.durationSeconds,
    )

fun playbackResumePosition(episode: EpisodeListItem): Double? =
    playbackResumePosition(
        positionSeconds = episode.userData?.positionSeconds,
        durationSeconds = episode.userData?.durationSeconds,
    )

private fun isAtOrPastResumeEnd(positionSeconds: Double, durationSeconds: Double?): Boolean {
    val duration = durationSeconds?.takeIf { it.isFinite() && it > 0 } ?: return false
    return positionSeconds >= duration - ResumeEndBufferSeconds
}
