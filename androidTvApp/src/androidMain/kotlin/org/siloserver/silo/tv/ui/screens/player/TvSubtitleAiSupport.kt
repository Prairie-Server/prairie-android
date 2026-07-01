package org.siloserver.silo.tv.ui.screens.player

import org.siloserver.silo.model.playback.PlayerSubtitleInfo

// Mirrors androidApp SubtitleAiSupport.isTranslatableSource (and
// web/src/player/components/SubtitleTranslateModal.tsx): external / downloaded
// sources must be a server-parseable text format, while embedded non-bitmap
// tracks can be extracted to text via ffmpeg. Kept as a TV-local copy so the
// TV module carries no dependency on androidApp.
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
