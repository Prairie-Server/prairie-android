package org.siloserver.silo.tv.ui.screens.player

import org.siloserver.silo.model.playback.PlayerSubtitleInfo
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
    val language = row.language?.trim()?.takeIf { it.isNotBlank() }
        ?.let { tvLanguageDisplayName(it) }
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
