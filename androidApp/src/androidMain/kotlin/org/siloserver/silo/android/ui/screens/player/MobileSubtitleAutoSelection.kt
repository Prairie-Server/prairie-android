package org.siloserver.silo.android.ui.screens.player

import org.siloserver.silo.common.player.isBitmapSubtitleCodecOrMime
import org.siloserver.silo.model.catalog.AudioTrack
import org.siloserver.silo.model.catalog.SubtitleTrack
import org.siloserver.silo.model.playback.PlayerSubtitleInfo

private val hearingImpairedSubtitleTokenRegex = Regex(
    pattern = """(^|[^a-z0-9])(cc|sdh|hi)([^a-z0-9]|$)""",
    option = RegexOption.IGNORE_CASE,
)

internal sealed class MobileSubtitleAutoSelection {
    data object NoChange : MobileSubtitleAutoSelection()
    data object Disable : MobileSubtitleAutoSelection()
    data class Select(val ordinal: Int) : MobileSubtitleAutoSelection()
}

internal fun resolveMobileAutoSubtitleSelection(
    audioTracks: List<AudioTrack>,
    selectedAudioIndex: Int,
    subtitles: List<PlayerSubtitleInfo>,
    preferredLanguage: String?,
    subtitleMode: String?,
    showForcedSubtitles: Boolean,
): MobileSubtitleAutoSelection {
    if (subtitles.isEmpty()) return MobileSubtitleAutoSelection.NoChange

    val mode = subtitleMode?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: "auto"
    if (mode == "off") return MobileSubtitleAutoSelection.Disable

    if (preferredLanguage != null && preferredLanguage.isBlank()) {
        return MobileSubtitleAutoSelection.Disable
    }
    val targetLanguage = normalizedSubtitleLanguage(preferredLanguage)
    if (targetLanguage == null) {
        if (mode == "always") {
            return bestAutoSubtitleOrdinal(
                subtitles = subtitles,
                targetLanguage = null,
                preferForced = showForcedSubtitles,
            )?.let(MobileSubtitleAutoSelection::Select)
                ?: MobileSubtitleAutoSelection.NoChange
        }
        return MobileSubtitleAutoSelection.NoChange
    }

    val selectedAudioLanguage = audioTracks
        .firstOrNull { it.index == selectedAudioIndex }
        ?: audioTracks.getOrNull(selectedAudioIndex)
    val selectedAudioMatches = normalizedSubtitleLanguage(selectedAudioLanguage?.language) == targetLanguage

    if (mode == "auto" && selectedAudioMatches) {
        if (showForcedSubtitles) {
            bestForcedAutoSubtitleOrdinal(
                subtitles = subtitles,
                targetLanguage = targetLanguage,
            )?.let { return MobileSubtitleAutoSelection.Select(it) }
        }
        return MobileSubtitleAutoSelection.Disable
    }

    val targetOrdinal = bestAutoSubtitleOrdinal(
        subtitles = subtitles,
        targetLanguage = targetLanguage,
        preferForced = showForcedSubtitles,
    ) ?: if (showForcedSubtitles) {
        subtitles.indexOfFirst { it.forced == true }.takeIf { it >= 0 }
    } else {
        null
    }

    return targetOrdinal
        ?.let(MobileSubtitleAutoSelection::Select)
        ?: MobileSubtitleAutoSelection.NoChange
}

/**
 * Maps the detail screen's pre-playback subtitle pick — an ordinal into the
 * catalog `FileVersion.subtitleTracks` list — onto the mounted subtitle list
 * the player actually selects from (TV `resolveInitialSubtitleTrackIndex`
 * parity). The two lists use different index spaces and orderings, so the
 * raw ordinal previously either fell out of range (subtitles silently stayed
 * off) or hit the wrong track. Matches by label first, then by
 * language + forced flag + codec; unmatched picks fall back to the raw
 * ordinal when mountable (pre-fix behavior), else null so persisted/auto
 * selection decides.
 */
internal fun resolveInitialMobileSubtitleOrdinal(
    requestedOrdinal: Int,
    catalogTracks: List<SubtitleTrack>,
    mountedSubtitles: List<PlayerSubtitleInfo>,
): Int? {
    if (requestedOrdinal == -1) return -1
    val requested = catalogTracks.getOrNull(requestedOrdinal)
        ?: return requestedOrdinal.takeIf { it in mountedSubtitles.indices }
    mountedSubtitles.indexOfFirst { it.matchesCatalogSubtitle(requested) }
        .takeIf { it >= 0 }
        ?.let { return it }
    return requestedOrdinal.takeIf { it in mountedSubtitles.indices }
}

private fun PlayerSubtitleInfo.matchesCatalogSubtitle(track: SubtitleTrack): Boolean {
    val targetLabel = track.title?.trim()?.takeIf { it.isNotBlank() }
    val mountedLabel = label?.trim()?.takeIf { it.isNotBlank() }
    if (targetLabel != null && mountedLabel != null &&
        mountedLabel.equals(targetLabel, ignoreCase = true)
    ) {
        // Duplicate titles ("English" full + "English" forced/SDH) are only
        // told apart by the forced flag — a bare label match must not cross
        // that boundary when the mounted order differs from the catalog's.
        return (forced == true) == track.forced
    }
    val targetLanguage = normalizedSubtitleLanguage(track.language) ?: return false
    if (normalizedSubtitleLanguage(language) != targetLanguage) return false
    if ((forced == true) != track.forced) return false
    val targetCodec = normalizedSubtitleCodec(track.codec)
    val mountedCodec = normalizedSubtitleCodec(codec ?: subtitleCodecFromUrl(url))
    return targetCodec == null || mountedCodec == null || targetCodec == mountedCodec
}

private fun normalizedSubtitleCodec(codecOrMime: String?): String? {
    val normalized = codecOrMime
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.lowercase()
        ?.replace('_', '-')
        ?: return null
    return when {
        normalized == "ass" || normalized == "ssa" || normalized.contains("x-ssa") -> "ssa"
        normalized == "srt" || normalized.contains("subrip") -> "srt"
        normalized == "vtt" || normalized == "text/vtt" || normalized.contains("webvtt") -> "vtt"
        normalized.contains("pgs") || normalized.contains("hdmv") -> "pgs"
        normalized.contains("dvd") || normalized.contains("dvb") -> "dvd"
        else -> normalized
    }
}

private fun bestAutoSubtitleOrdinal(
    subtitles: List<PlayerSubtitleInfo>,
    targetLanguage: String?,
    preferForced: Boolean,
): Int? {
    val pool = subtitles.withIndex().filter { (_, subtitle) ->
        targetLanguage == null || normalizedSubtitleLanguage(subtitle.language) == targetLanguage
    }
    if (pool.isEmpty()) return null

    if (preferForced) {
        pool.firstOrNull { (_, subtitle) ->
            subtitle.forced == true && !subtitle.isEffectivelyHearingImpaired() && !subtitle.isBitmap()
        }?.let { return it.index }
    }
    pool.firstOrNull { (_, subtitle) ->
        subtitle.forced != true && !subtitle.isEffectivelyHearingImpaired() && !subtitle.isBitmap()
    }?.let { return it.index }
    pool.firstOrNull { (_, subtitle) ->
        subtitle.forced != true && !subtitle.isBitmap()
    }?.let { return it.index }
    pool.firstOrNull { (_, subtitle) -> !subtitle.isBitmap() }
        ?.let { return it.index }
    return pool.first().index
}

private fun bestForcedAutoSubtitleOrdinal(
    subtitles: List<PlayerSubtitleInfo>,
    targetLanguage: String?,
): Int? {
    val pool = subtitles.withIndex().filter { (_, subtitle) ->
        subtitle.forced == true &&
            (targetLanguage == null || normalizedSubtitleLanguage(subtitle.language) == targetLanguage)
    }
    if (pool.isEmpty()) return null

    pool.firstOrNull { (_, subtitle) -> !subtitle.isEffectivelyHearingImpaired() && !subtitle.isBitmap() }
        ?.let { return it.index }
    pool.firstOrNull { (_, subtitle) -> !subtitle.isEffectivelyHearingImpaired() }
        ?.let { return it.index }
    return pool.first().index
}

private fun PlayerSubtitleInfo.isEffectivelyHearingImpaired(): Boolean =
    label.indicatesHearingImpairedSubtitle() ||
        source.indicatesHearingImpairedSubtitle() ||
        url.indicatesHearingImpairedSubtitle()

private fun String?.indicatesHearingImpairedSubtitle(): Boolean {
    val value = this?.takeIf { it.isNotBlank() } ?: return false
    val lower = value.lowercase()
    return lower.contains("closed caption") ||
        lower.contains("hearing impaired") ||
        lower.contains("hearing-impaired") ||
        lower.contains("hearing") ||
        hearingImpairedSubtitleTokenRegex.containsMatchIn(value)
}

private fun PlayerSubtitleInfo.isBitmap(): Boolean =
    isBitmapSubtitleCodecOrMime(codec ?: subtitleCodecFromUrl(url))

private fun subtitleCodecFromUrl(url: String?): String? =
    url
        ?.substringBefore('?')
        ?.substringBefore('#')
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.takeIf { it.isNotBlank() }

private fun normalizedSubtitleLanguage(language: String?): String? {
    val primary = language
        ?.trim()
        ?.takeUnless { it.isBlank() || it.equals("und", ignoreCase = true) }
        ?.lowercase()
        ?.replace('_', '-')
        ?.substringBefore('-')
        ?: return null
    return when (primary) {
        "eng" -> "en"
        "spa" -> "es"
        "fre", "fra" -> "fr"
        "ger", "deu" -> "de"
        "dut", "nld" -> "nl"
        "jpn" -> "ja"
        "dan" -> "da"
        else -> primary
    }
}
