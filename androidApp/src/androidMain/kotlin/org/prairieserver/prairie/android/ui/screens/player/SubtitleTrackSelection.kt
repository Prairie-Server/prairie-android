package org.prairieserver.prairie.android.ui.screens.player

import org.prairieserver.prairie.model.playback.PlayerSubtitleInfo
import org.prairieserver.prairie.model.subtitles.DownloadedSubtitle

/**
 * Position (in the merged subtitleTracks list) of the downloaded subtitle
 * with [subtitleId], used for auto-select after a download / AI job completes.
 *
 * Relies on the mergeDownloadedSubtitles contract (web parity,
 * usePlaybackSession.ts refreshSubtitles): downloaded entries are appended in
 * listing order AFTER all non-downloaded tracks, so the merged position is
 * (merged.size - downloaded.size) + positionInDownloadedListing.
 */
internal fun downloadedTrackIndex(
    merged: List<PlayerSubtitleInfo>,
    downloaded: List<DownloadedSubtitle>,
    subtitleId: Int,
): Int? {
    val pos = downloaded.indexOfFirst { it.id == subtitleId }
    if (pos < 0) return null
    val start = merged.size - downloaded.size
    return (start + pos).takeIf { it in merged.indices }
}
