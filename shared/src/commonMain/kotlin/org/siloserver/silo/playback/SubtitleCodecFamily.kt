package org.siloserver.silo.playback

/**
 * Canonical codec family shared by catalog persistence and mounted-player
 * identity matching. Raw catalog names and decoder/MIME aliases must serialize
 * to the same value or a player-written preference cannot survive a restart.
 */
fun canonicalSubtitleCodecFamily(codecOrMime: String?): String? {
    val normalized = codecOrMime
        ?.trim()
        ?.filter(Char::isLetterOrDigit)
        ?.lowercase()
        ?.takeIf(String::isNotEmpty)
        ?: return null
    return when {
        normalized.contains("pgs") -> "pgs"
        normalized.contains("vobsub") || normalized.contains("dvdsubtitle") -> "vobsub"
        normalized.contains("dvbsub") -> "dvbsub"
        normalized.contains("subrip") || normalized.endsWith("srt") -> "subrip"
        normalized.contains("webvtt") ||
            normalized == "textvtt" ||
            normalized.endsWith("vtt") -> "webvtt"
        normalized.contains("tx3g") || normalized.contains("movtext") -> "tx3g"
        normalized.contains("ssa") || normalized == "ass" -> "ssa"
        normalized.contains("ttml") -> "ttml"
        normalized.contains("cea608") || normalized.contains("eia608") -> "cea608"
        normalized.contains("cea708") -> "cea708"
        else -> normalized
    }
}
