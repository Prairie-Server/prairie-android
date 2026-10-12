package org.prairieserver.prairie.common.player

import org.prairieserver.prairie.model.playback.PlayerSubtitleInfo
import org.prairieserver.prairie.model.subtitles.SubtitleSyncState
import org.prairieserver.prairie.player.trackLanguageDisplayName

/** The subtitle's language name for sync feedback ("English"), or null when it has none. */
fun subtitleSyncName(tracks: List<PlayerSubtitleInfo>, subtitle: SubtitleSyncState): String? =
    trackLanguageDisplayName(tracks.firstOrNull { it.syncKey == subtitle.key }?.language ?: subtitle.language)
