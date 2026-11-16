package org.prairieserver.prairie.common.player

import org.prairieserver.prairie.common.i18n.UiText
import org.prairieserver.prairie.model.playback.PlayerSubtitleInfo
import org.prairieserver.prairie.model.subtitles.SubtitleSyncState
import org.prairieserver.prairie.player.trackLanguageTag

/**
 * The subtitle's language for sync feedback, named in the interface language
 * when the notice is drawn ("English", "inglés"), or null when it has none.
 */
fun subtitleSyncName(tracks: List<PlayerSubtitleInfo>, subtitle: SubtitleSyncState): UiText? =
    trackLanguageTag(tracks.firstOrNull { it.syncKey == subtitle.key }?.language ?: subtitle.language)
        ?.let { UiText.LanguageName(it) }
