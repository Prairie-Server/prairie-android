package com.continuum.app.tv.ui.screens.detail

import com.continuum.app.model.catalog.AudioTrack
import com.continuum.app.model.catalog.FileVersion
import com.continuum.app.model.catalog.SubtitleTrack
import java.util.Locale

/**
 * Pure formatting helpers for the TV detail playback selector row (Version /
 * Audio / Subtitles / Edition). Mirrors silo-apple's
 * `Screens/Detail/DetailPlaybackFormatting.swift` + `PlaybackEditions.swift`,
 * adapted to the real Android [FileVersion] / [AudioTrack] / [SubtitleTrack]
 * field names.
 *
 * Track-index semantics (preserved from the existing VM contract):
 * - audio `selectedAudioTrackIndex`: `null` = Auto/default, else the
 *   zero-based ordinal into [FileVersion.audioTracks].
 * - subtitle `selectedSubtitleTrackIndex`: `null` = Auto, `-1` = Off, else the
 *   zero-based ORDINAL into [FileVersion.subtitleTracks]. (TV playback selects
 *   subtitles by flat text-track ordinal — `PlayerTrackEntry.index` — NOT the
 *   raw [SubtitleTrack.index] stream index, which is non-ordinal and collides
 *   at 0 for external/unindexed tracks.)
 *
 * NOTE on editions: unlike Apple's `FileVersion` (which carries
 * `edition_key` / `edition_raw` / `edition`), the Android [FileVersion] model
 * exposes NO edition data. [editions] therefore always returns a single
 * "Standard" group (or empty for no versions), so the Edition selector in the
 * UI stays hidden. This becomes meaningful only once the model/server adds
 * edition fields (see SPEC §6 / §11).
 */
object TvPlaybackFormatting {

    data class TvAudioOption(
        val ordinal: Int,
        val title: String,
        val detail: String,
        val isSelected: Boolean,
    )

    data class TvSubtitleOption(
        /** Zero-based ordinal among subtitle tracks — the value to pass to
         *  `onSelectSubtitleTrack` (matches the player's flat text-track index). */
        val selectionIndex: Int,
        val title: String,
        val detail: String,
        val isSelected: Boolean,
        val stableId: String,
    )

    data class TvEdition(
        val id: String,
        val label: String,
        val versions: List<FileVersion>,
    )

    // --- Version ---------------------------------------------------------

    /** "4K · HDR" / "1080P" / "Auto" (null or no usable tokens → "Auto"). */
    fun versionShortLabel(version: FileVersion?): String {
        if (version == null) return "Auto"
        val tokens = buildList {
            displayResolution(version.resolution)?.let { add(it) }
            if (version.hdr) add("HDR")
        }
        return if (tokens.isEmpty()) "Auto" else tokens.joinToString(" · ")
    }

    /** resolution · codec · container · size detail line. */
    fun versionDetailLabel(version: FileVersion): String {
        val tokens = buildList {
            normalizedVideoCodec(version.codecVideo)?.let { add(it) }
            nonEmpty(version.container)?.uppercase(Locale.US)?.let { add(it) }
            formatFileSize(version.fileSize)?.let { add(it) }
        }
        return tokens.joinToString(" · ")
    }

    // --- Audio -----------------------------------------------------------

    fun audioOptions(version: FileVersion?, selectedAudioTrackIndex: Int?): List<TvAudioOption> {
        if (version == null) return emptyList()
        val selectedOrdinal = resolvedAudioOrdinal(version, selectedAudioTrackIndex)
        return (version.audioTracks ?: emptyList()).mapIndexed { ordinal, track ->
            TvAudioOption(
                ordinal = ordinal,
                title = audioTitle(track, ordinal),
                detail = audioDetail(track),
                isSelected = selectedOrdinal == ordinal,
            )
        }
    }

    fun audioValueLabel(version: FileVersion?, selectedAudioTrackIndex: Int?): String {
        val tracks = version?.audioTracks ?: return "Unknown"
        val ordinal = resolvedAudioOrdinal(version, selectedAudioTrackIndex) ?: return "Unknown"
        val track = tracks.getOrNull(ordinal) ?: return "Unknown"
        return audioTitle(track, ordinal)
    }

    private fun resolvedAudioOrdinal(version: FileVersion?, selectedAudioTrackIndex: Int?): Int? {
        val tracks = version?.audioTracks ?: return null
        if (tracks.isEmpty()) return null
        if (selectedAudioTrackIndex != null && selectedAudioTrackIndex in tracks.indices) {
            return selectedAudioTrackIndex
        }
        val defaultIndex = tracks.indexOfFirst { it.isDefault }
        if (defaultIndex >= 0) return defaultIndex
        return 0
    }

    private fun audioTitle(track: AudioTrack, ordinal: Int): String {
        val format = listOfNotNull(
            normalizedAudioCodec(track.codec),
            compactAudioLayout(track),
        ).joinToString(" ")
        val language = languageDisplayName(track.language)
        val formatNonEmpty = nonEmpty(format)

        return when {
            formatNonEmpty != null && language != null -> "$formatNonEmpty - $language"
            formatNonEmpty != null -> formatNonEmpty
            usefulAudioTitle(track.title) != null ->
                listOfNotNull(usefulAudioTitle(track.title), language).joinToString(" - ")
            language != null -> language
            else -> "Track ${ordinal + 1}"
        }
    }

    private fun audioDetail(track: AudioTrack): String {
        val tokens = buildList {
            usefulAudioTitle(track.title)?.let { add(it) }
            if (track.isDefault) add("Default")
        }
        return tokens.joinToString(" · ")
    }

    // --- Subtitles -------------------------------------------------------

    fun subtitleOptions(version: FileVersion?, selectedSubtitleTrackIndex: Int?): List<TvSubtitleOption> {
        val tracks = version?.subtitleTracks ?: return emptyList()
        return tracks.mapIndexed { ordinal, track ->
            TvSubtitleOption(
                selectionIndex = ordinal,
                title = subtitleTitle(track, ordinal),
                detail = subtitleDetail(track),
                isSelected = selectedSubtitleTrackIndex == ordinal,
                stableId = "sub-$ordinal",
            )
        }
    }

    fun subtitleValueLabel(version: FileVersion?, selectedSubtitleTrackIndex: Int?): String {
        if (selectedSubtitleTrackIndex == null) return "Auto"
        if (selectedSubtitleTrackIndex == -1) return "Off"
        val tracks = version?.subtitleTracks ?: return "Auto"
        val track = tracks.getOrNull(selectedSubtitleTrackIndex) ?: return "Auto"
        return subtitleTitle(track, selectedSubtitleTrackIndex)
    }

    private fun subtitleTitle(track: SubtitleTrack, ordinal: Int): String {
        val type = subtitleType(track)
        val language = languageDisplayName(track.language)
        return when {
            type != null && language != null -> "$type - $language"
            type != null -> type
            language != null -> language
            else -> "Track ${ordinal + 1}"
        }
    }

    private fun subtitleDetail(track: SubtitleTrack): String {
        val tokens = buildList {
            normalizedSubtitleCodec(track.codec)?.let { add(it) }
            if (track.forced) add("Forced")
            if (track.isDefault) add("Default")
            if (track.external) add("External")
        }
        return tokens.joinToString(" · ")
    }

    /**
     * Badge/type derivation: SDH/HI (hearing-impaired), CC, Forced, a
     * meaningful custom title, then codec. Returns null when nothing
     * distinguishing applies (the caller falls back to language).
     */
    private fun subtitleType(track: SubtitleTrack): String? {
        nonEmpty(track.title)?.let { title ->
            val lowered = title.lowercase(Locale.US)
            if (lowered.contains("sdh") || lowered.contains("hearing") || lowered.contains("(hi)") ||
                lowered == "hi"
            ) {
                return "SDH"
            }
            if (lowered.contains("closed caption") || lowered == "cc") return "CC"
            if (lowered.contains("forced")) return "Forced"
            if (!isRedundantSubtitleTitle(title, track)) return displayTitle(title)
        }
        if (track.forced) return "Forced"
        return normalizedSubtitleCodec(track.codec)
    }

    // --- Editions (Android model has no edition data) --------------------

    fun currentEdition(versions: List<FileVersion>, currentVersion: FileVersion?): TvEdition? =
        edition(forFileId = currentVersion?.fileId, versions = versions)
            ?: editions(versions).firstOrNull()

    /**
     * Distinct editions in first-seen order. The Android [FileVersion] carries
     * no edition fields, so every version lands in one "Standard" group; this
     * keeps the UI's Edition selector hidden until model support lands.
     */
    fun editions(versions: List<FileVersion>): List<TvEdition> {
        if (versions.isEmpty()) return emptyList()
        return listOf(TvEdition(id = "standard", label = "Standard", versions = versions))
    }

    private fun edition(forFileId: Int?, versions: List<FileVersion>): TvEdition? {
        if (forFileId == null) return null
        return editions(versions).firstOrNull { ed -> ed.versions.any { it.fileId == forFileId } }
    }

    // --- Codec / layout / size normalization (mirrors Swift) -------------

    fun normalizedVideoCodec(codec: String?): String? {
        val lowered = codec?.lowercase(Locale.US)?.trim()
        if (lowered.isNullOrEmpty()) return null
        return when {
            lowered.contains("hevc") || lowered.contains("h265") -> "HEVC"
            lowered.contains("av1") -> "AV1"
            lowered.contains("avc") || lowered.contains("h264") -> "H.264"
            else -> lowered.uppercase(Locale.US)
        }
    }

    fun normalizedAudioCodec(codec: String?): String? {
        val lowered = codec?.lowercase(Locale.US)?.trim()
        if (lowered.isNullOrEmpty()) return null
        return when {
            lowered.contains("eac3") || lowered.contains("e-ac-3") || lowered.contains("ec-3") -> "EAC3"
            lowered.contains("ac3") || lowered.contains("ac-3") -> "AC3"
            lowered.contains("aac") -> "AAC"
            lowered.contains("mp3") -> "MP3"
            lowered.contains("truehd") -> "TrueHD"
            lowered.contains("dts") -> "DTS"
            lowered.contains("flac") -> "FLAC"
            else -> lowered.uppercase(Locale.US)
        }
    }

    private fun normalizedSubtitleCodec(codec: String?): String? {
        val lowered = codec?.lowercase(Locale.US)?.trim()
        if (lowered.isNullOrEmpty()) return null
        return when {
            lowered == "srt" || lowered.contains("subrip") -> "SubRip"
            lowered.contains("ass") -> "ASS"
            lowered.contains("ssa") -> "SSA"
            lowered == "vtt" || lowered.contains("webvtt") -> "WebVTT"
            lowered.contains("pgs") || lowered.contains("hdmv") -> "PGS"
            lowered.contains("dvd") || lowered.contains("vobsub") -> "VobSub"
            lowered.contains("mov_text") || lowered.contains("tx3g") -> "TX3G"
            else -> lowered.uppercase(Locale.US)
        }
    }

    /**
     * Display-friendly resolution token. Apple's helper just uppercases the raw
     * resolution; the TV row additionally maps 2160 → "4K" (SPEC §6 value
     * table: "4K · HDR" / "1080P").
     */
    private fun displayResolution(value: String?): String? {
        val token = nonEmpty(value) ?: return null
        val lowered = token.lowercase(Locale.US)
        return when {
            lowered.contains("4320") || lowered.contains("8k") -> "8K"
            lowered.contains("2160") || lowered == "4k" || lowered.contains("uhd") -> "4K"
            else -> token.uppercase(Locale.US)
        }
    }

    /**
     * Mirrors Apple's `ByteCountFormatter` (`.file` countStyle, adaptive):
     * DECIMAL units (÷1000), GB to 2 decimals / MB to 1 decimal. e.g. 8 GiB →
     * "8.59 GB", 500 MiB → "524.3 MB". Returns null for non-positive counts.
     */
    private fun formatFileSize(bytes: Long): String? {
        if (bytes <= 0) return null
        val gb = bytes / 1_000_000_000.0
        if (gb >= 1.0) return String.format(Locale.US, "%.2f GB", gb)
        val mb = bytes / 1_000_000.0
        return String.format(Locale.US, "%.1f MB", mb)
    }

    private fun compactAudioLayout(track: AudioTrack): String? {
        nonEmpty(track.channelLayout)?.let { layout ->
            val lowered = layout.lowercase(Locale.US)
            return when {
                lowered.contains("atmos") -> "Atmos"
                lowered.contains("7.1") -> "7.1"
                lowered.contains("5.1") -> "5.1"
                lowered.contains("stereo") -> "Stereo"
                else -> layout
            }
        }
        return when (track.channels) {
            null -> null
            1 -> "Mono"
            2 -> "Stereo"
            6 -> "5.1"
            8 -> "7.1"
            else -> "${track.channels}ch"
        }
    }

    private fun usefulAudioTitle(title: String?): String? {
        val trimmed = nonEmpty(title) ?: return null
        val lowered = trimmed.lowercase(Locale.US)
        val technicalTerms = listOf(
            "atsc", "a/52", "ac-3", "e-ac-3", "eac3", "truehd", "dts", "aac", "flac",
        )
        if (technicalTerms.any { lowered.contains(it) }) return null
        return displayTitle(trimmed)
    }

    private fun isRedundantSubtitleTitle(title: String, track: SubtitleTrack): Boolean {
        val lowered = title.lowercase(Locale.US)
        val language = languageDisplayName(track.language)?.lowercase(Locale.US)
        val languageCode = nonEmpty(track.language)?.lowercase(Locale.US)
        if (lowered == "subtitle" || lowered == "subtitles") return true
        if (language != null && lowered == language) return true
        if (languageCode != null && lowered == languageCode) return true
        val codec = normalizedSubtitleCodec(track.codec)?.lowercase(Locale.US)
        if (codec != null && (lowered == codec || lowered == track.codec?.lowercase(Locale.US))) return true
        return false
    }

    private fun displayTitle(title: String): String {
        val trimmed = title.trim()
        return when (trimmed.lowercase(Locale.US)) {
            "sdh" -> "SDH"
            "cc" -> "CC"
            "srt", "subrip" -> "SubRip"
            "webvtt", "vtt" -> "WebVTT"
            else -> trimmed
        }
    }

    private fun languageDisplayName(value: String?): String? {
        val trimmed = nonEmpty(value) ?: return null
        val normalized = trimmed.lowercase(Locale.US).replace('_', '-')
        val primary = normalized.split('-').firstOrNull() ?: normalized
        if (primary.length > 3) return capitalizeWords(trimmed)
        val languageCode = languageCodeAliases[primary] ?: primary
        val display = Locale.forLanguageTag(languageCode).getDisplayLanguage(Locale.ENGLISH)
        return if (display.isNotBlank() && !display.equals(languageCode, ignoreCase = true)) {
            capitalizeWords(display)
        } else {
            trimmed.uppercase(Locale.US)
        }
    }

    private val languageCodeAliases: Map<String, String> = mapOf(
        "ara" to "ar", "chi" to "zh", "cze" to "cs", "dan" to "da", "deu" to "de",
        "dut" to "nl", "eng" to "en", "fin" to "fi", "fra" to "fr", "fre" to "fr",
        "ger" to "de", "hin" to "hi", "ita" to "it", "jpn" to "ja", "kor" to "ko",
        "nld" to "nl", "nor" to "no", "pol" to "pl", "por" to "pt", "rus" to "ru",
        "spa" to "es", "swe" to "sv", "zho" to "zh",
    )

    private fun capitalizeWords(value: String): String =
        value.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        }

    private fun nonEmpty(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
}
