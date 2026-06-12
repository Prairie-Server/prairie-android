package com.continuum.app.android.ui.screens.player

import com.continuum.app.model.playback.PlayerSubtitleInfo
import com.continuum.app.model.subtitles.SubtitleAiJobKind
import com.continuum.app.model.subtitles.SubtitleAiQuota

// Mirrors web/src/player/components/SubtitleTranslateModal.tsx: external /
// downloaded sources must be a server-parseable text format, while embedded
// non-bitmap tracks can be extracted to text via ffmpeg.
private val TRANSLATABLE_TEXT_CODECS = setOf("srt", "subrip", "vtt", "webvtt")
private val BITMAP_CODECS = setOf(
    "pgs", "hdmv_pgs_subtitle", "dvd_subtitle", "dvdsub", "dvb_subtitle", "dvbsub",
)

/** True when the server can use [track] as an AI translation source. */
internal fun isTranslatableSource(track: PlayerSubtitleInfo): Boolean {
    val codec = track.codec?.lowercase().orEmpty()
    return if (track.source == "embedded") {
        codec !in BITMAP_CODECS
    } else {
        codec in TRANSLATABLE_TEXT_CODECS
    }
}

/**
 * Audio-mode job kind: same target as the audio language → plain
 * transcription; otherwise transcribe-then-translate (web parity).
 */
internal fun transcribeKindFor(audioLanguage: String?, targetLanguage: String): String =
    if ((audioLanguage ?: "") == targetLanguage) {
        SubtitleAiJobKind.Transcribe
    } else {
        SubtitleAiJobKind.TranscribeTranslate
    }

/** Quota counter line, wording pinned to the web modal + its period labels. */
internal fun quotaLineText(quota: SubtitleAiQuota): String {
    val window = when (quota.period) {
        "day" -> "24 hours"
        "week" -> "7 days"
        "month" -> "30 days"
        else -> quota.period
    }
    return if (quota.remaining <= 0) {
        "You've used all ${quota.limit} transcriptions for the last $window. Try again later."
    } else {
        "${quota.remaining} of ${quota.limit} transcriptions left for the last $window."
    }
}
