package org.siloserver.silo.tv.ui.screens.player

import org.siloserver.silo.model.playback.PlayerSubtitleInfo
import org.siloserver.silo.player.trackLanguageDisplayName
import java.util.Locale

/**
 * Human labels for mounted subtitle rows ("Danish SRT", "Danish PGS
 * (External)"). Server row labels are identity strings — for external
 * sidecars they are the literal release filename — so pickers must build
 * display labels from language + format + provenance instead of echoing
 * them. A meaningful custom title (chapter-style names like "Signs", or
 * "SDH") still rides along; codec junk ("SUBRIP") and filenames do not.
 */
internal fun subtitleChoiceLabel(row: PlayerSubtitleInfo, position: Int): String {
    // trackLanguageDisplayName handles ISO 639-2/B codes ("ger","fre","dut")
    // that Locale(code) alone cannot; fall back to the uppercased raw code.
    val language = row.language?.trim()?.takeIf { it.isNotBlank() }
        ?.let { trackLanguageDisplayName(it) ?: it.uppercase(Locale.US) }
    val format = subtitleFormatShortName(
        row.codec?.trim()?.takeIf { it.isNotBlank() }
            ?: row.url.substringBefore('?').substringBefore('#').substringAfterLast('.', ""),
    )
    val base = when {
        language != null && format != null -> "$language $format"
        language != null -> language
        format != null -> format
        else -> "Track ${position + 1}"
    }
    val title = meaningfulSubtitleRowTitle(row, language, format)
    val qualifiers = buildList {
        if (title != null) add(title)
        if (row.forced == true) add("Forced")
        when (row.source) {
            "external" -> add("External")
            "downloaded" -> add("Downloaded")
        }
    }
    return if (qualifiers.isEmpty()) base else "$base (${qualifiers.joinToString(", ")})"
}

/** ffmpeg codec ids / file extensions → the short format names users know. */
internal fun subtitleFormatShortName(codec: String?): String? =
    when (val normalized = codec?.trim()?.lowercase(Locale.US)) {
        null, "" -> null
        "subrip", "srt" -> "SRT"
        "ass", "ssa" -> "ASS"
        "webvtt", "vtt" -> "VTT"
        "mov_text", "tx3g" -> "MP4"
        "hdmv_pgs_subtitle", "pgs", "pgssub", "sup" -> "PGS"
        "dvd_subtitle", "dvdsub", "vobsub", "sub" -> "VobSub"
        "dvb_subtitle", "dvbsub" -> "DVB"
        "dvb_teletext" -> "Teletext"
        "eia_608", "eia608", "cea_608", "cea608" -> "CC"
        else -> normalized.uppercase(Locale.US)
    }

/**
 * A row label worth showing next to language+format: not blank, not codec
 * junk ("SUBRIP"), not redundant with the language, and never a filename.
 */
private fun meaningfulSubtitleRowTitle(
    row: PlayerSubtitleInfo,
    language: String?,
    format: String?,
): String? {
    val label = row.label?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (label.length > 28) return null
    val lowered = label.lowercase(Locale.US)
    if (lowered.contains('[') || FILENAME_SUFFIXES.any { lowered.endsWith(it) }) return null
    if (language != null && lowered == language.lowercase(Locale.US)) return null
    if (row.language != null && lowered == row.language!!.lowercase(Locale.US)) return null
    if (format != null && lowered == format.lowercase(Locale.US)) return null
    if (row.codec != null && lowered == row.codec!!.lowercase(Locale.US)) return null
    return label
}

private val FILENAME_SUFFIXES = listOf(".srt", ".ass", ".ssa", ".vtt", ".sub", ".sup", ".idx")

/**
 * Human labels for audio track rows ("English DTS 5.1", "Japanese AAC
 * Stereo") — the audio twin of [subtitleChoiceLabel]. Media3 labels echo
 * server identity strings and the old fallback was a bare uppercased ISO
 * code, so pickers build from language + codec + channel layout instead.
 */
internal fun audioChoiceLabel(entry: PlayerTrackEntry, position: Int): String {
    val language = entry.language?.trim()?.takeIf { it.isNotBlank() }
        ?.let { trackLanguageDisplayName(it) ?: it.uppercase(Locale.US) }
    val codec = audioFormatShortName(entry.codecOrMime)
    val layout = audioChannelLayoutLabel(entry.channelCount)
    val parts = listOfNotNull(language, codec, layout)
    if (parts.isEmpty()) {
        return entry.displayLabel.trim().takeIf { it.isNotBlank() } ?: "Track ${position + 1}"
    }
    return parts.joinToString(" ")
}

/** Media3 audio mimes / codec ids → the short names users know. */
internal fun audioFormatShortName(codecOrMime: String?): String? =
    when (codecOrMime?.trim()?.lowercase(Locale.US)?.substringAfterLast('/')) {
        null, "" -> null
        "mp4a-latm", "aac", "mp4a" -> "AAC"
        "ac3" -> "AC3"
        "eac3" -> "E-AC3"
        "eac3-joc" -> "Atmos"
        "true-hd", "truehd" -> "TrueHD"
        "vnd.dts", "dts" -> "DTS"
        "vnd.dts.hd", "dts.hd" -> "DTS-HD"
        "vnd.dts.uhd;profile=p2", "dts.uhd" -> "DTS:X"
        "opus" -> "Opus"
        "flac" -> "FLAC"
        "mpeg", "mp3", "mpeg-l2" -> "MP3"
        "vorbis" -> "Vorbis"
        "raw", "pcm", "wav" -> "PCM"
        else -> codecOrMime.substringAfterLast('/').uppercase(Locale.US).take(12)
    }

/** Channel count → familiar layout name. */
internal fun audioChannelLayoutLabel(channelCount: Int): String? = when {
    channelCount <= 0 -> null
    channelCount == 1 -> "Mono"
    channelCount == 2 -> "Stereo"
    channelCount == 6 -> "5.1"
    channelCount == 8 -> "7.1"
    else -> "${channelCount}ch"
}
