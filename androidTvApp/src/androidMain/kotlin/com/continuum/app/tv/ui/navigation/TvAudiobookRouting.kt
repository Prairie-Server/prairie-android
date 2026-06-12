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
    if (isAudiobookItemType(itemType)) {
        TvRoute.AudiobookPlayer(contentId, fileId).route
    } else {
        TvRoute.Player(contentId, fileId).route
    }
