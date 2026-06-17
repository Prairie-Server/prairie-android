package com.continuum.app.tv.ui.navigation

import com.continuum.app.model.catalog.isAudiobookItemType

/**
 * Decide the playback route for a Play action on the TV detail screen.
 *
 * Audiobook-type items ([isAudiobookItemType]) open the dedicated
 * [TvRoute.AudiobookPlayer]; everything else opens the video [TvRoute.Player].
 * Pure (returns the route string, no Android/Nav types) so the decision is
 * unit-tested independently of navigation — see `TvAudiobookRoutingTest`.
 */
fun tvPlayDestinationFor(
    itemType: String?,
    contentId: String,
    fileId: Int?,
): String =
    tvPlayDestinationFor(
        itemType = itemType,
        contentId = contentId,
        fileId = fileId,
        resumePositionSeconds = null,
    )

fun tvPlayDestinationFor(
    itemType: String?,
    contentId: String,
    fileId: Int?,
    resumePositionSeconds: Double?,
    audioTrackIndex: Int? = null,
    subtitleTrackIndex: Int? = null,
): String =
    if (isAudiobookItemType(itemType)) {
        // Audiobooks have no audio/subtitle track selection — ignore the indexes.
        TvRoute.AudiobookPlayer(contentId, fileId).route
    } else {
        TvRoute.Player(
            contentId = contentId,
            fileId = fileId,
            resumePositionSeconds = resumePositionSeconds,
            audioTrackIndex = audioTrackIndex,
            subtitleTrackIndex = subtitleTrackIndex,
        ).route
    }
