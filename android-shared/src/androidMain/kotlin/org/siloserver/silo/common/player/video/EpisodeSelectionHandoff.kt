package org.siloserver.silo.common.player.video

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.siloserver.silo.common.player.normalizedSubtitleCodecFamily
import org.siloserver.silo.common.player.subtitleLabelIndicatesHearingImpaired
import org.siloserver.silo.model.catalog.FileVersion
import org.siloserver.silo.model.playback.PlayerSubtitleInfo
import org.siloserver.silo.player.DolbyVisionDetection
import org.siloserver.silo.playback.canonicalSubtitleLanguage

/**
 * Session-only, cross-episode playback intent. It deliberately carries no
 * episode-local identity: target IDs and subtitle indexes are resolved only
 * after the next episode's catalog detail is available.
 */
@Serializable
data class EpisodeSelectionHandoff(
    val source: EpisodeSourceIntent? = null,
    val subtitle: EpisodeSubtitleIntent = EpisodeSubtitleIntent.auto(),
)

@Serializable
data class EpisodeSourceIntent(
    val resolution: String,
    val videoCodec: String? = null,
    val dynamicRange: EpisodeDynamicRange? = null,
    val container: String? = null,
)

@Serializable
enum class EpisodeDynamicRange { SDR, HDR, DOLBY_VISION }

@Serializable
enum class EpisodeSubtitleMode { AUTO, OFF, TRACK }

@Serializable
data class EpisodeSubtitleIntent(
    val mode: EpisodeSubtitleMode,
    val language: String? = null,
    val codecFamily: String? = null,
    val forced: Boolean? = null,
    val hearingImpaired: Boolean? = null,
    val external: Boolean? = null,
) {
    companion object {
        fun auto() = EpisodeSubtitleIntent(EpisodeSubtitleMode.AUTO)
        fun off() = EpisodeSubtitleIntent(EpisodeSubtitleMode.OFF)
    }
}

data class ResolvedEpisodeSubtitle(
    val trackIndex: Int?,
    val intentSpecified: Boolean,
)

data class ResolvedEpisodeSelection(
    val fileId: Int?,
    val subtitleTrackIndex: Int?,
    val subtitleIntentSpecified: Boolean,
)

fun captureEpisodeSourceIntent(version: FileVersion?): EpisodeSourceIntent? {
    val version = version ?: return null
    val resolution = normalizedEpisodeResolution(
        version.resolution ?: version.videoTracks?.firstOrNull()?.resolution,
    ) ?: return null
    return EpisodeSourceIntent(
        resolution = resolution,
        videoCodec = normalizedEpisodeToken(
            version.codecVideo ?: version.videoTracks?.firstOrNull()?.codec,
        ),
        dynamicRange = version.episodeDynamicRange(),
        container = normalizedEpisodeToken(version.container),
    )
}

fun captureEpisodeSubtitleIntent(
    selectedTrackIndex: Int?,
    subtitles: List<PlayerSubtitleInfo>,
): EpisodeSubtitleIntent = when (selectedTrackIndex) {
    null -> EpisodeSubtitleIntent.auto()
    -1 -> EpisodeSubtitleIntent.off()
    else -> subtitles
        .singleOrNull { it.index == selectedTrackIndex }
        ?.toEpisodeSubtitleIntent()
        ?: EpisodeSubtitleIntent.auto()
}

fun resolveEpisodeSourceIntent(
    intent: EpisodeSourceIntent?,
    targetVersions: List<FileVersion>,
): Int? {
    val intent = intent ?: return null
    val candidates = targetVersions.filter {
        normalizedEpisodeResolution(it.resolution ?: it.videoTracks?.firstOrNull()?.resolution) == intent.resolution
    }
    if (candidates.isEmpty()) return null

    val highestScore = candidates.maxOf { candidate ->
        candidate.episodeSourceScore(intent)
    }
    return candidates
        .filter { it.episodeSourceScore(intent) == highestScore }
        .singleOrNull()
        ?.fileId
}

fun resolveEpisodeSubtitleIntent(
    intent: EpisodeSubtitleIntent,
    targetSubtitles: List<PlayerSubtitleInfo>,
): ResolvedEpisodeSubtitle = when (intent.mode) {
    EpisodeSubtitleMode.AUTO -> ResolvedEpisodeSubtitle(
        trackIndex = null,
        intentSpecified = false,
    )
    EpisodeSubtitleMode.OFF -> ResolvedEpisodeSubtitle(
        trackIndex = -1,
        intentSpecified = true,
    )
    EpisodeSubtitleMode.TRACK -> ResolvedEpisodeSubtitle(
        trackIndex = targetSubtitles
            .filter { it.matchesEpisodeSubtitleIntent(intent) }
            .singleOrNull()
            ?.index,
        intentSpecified = true,
    )
}

fun resolveEpisodeSelectionHandoff(
    handoff: EpisodeSelectionHandoff?,
    targetVersions: List<FileVersion>,
    targetSubtitles: List<PlayerSubtitleInfo>,
): ResolvedEpisodeSelection {
    val subtitle = resolveEpisodeSubtitleIntent(
        handoff?.subtitle ?: EpisodeSubtitleIntent.auto(),
        targetSubtitles,
    )
    return ResolvedEpisodeSelection(
        fileId = resolveEpisodeSourceIntent(handoff?.source, targetVersions),
        subtitleTrackIndex = subtitle.trackIndex,
        subtitleIntentSpecified = subtitle.intentSpecified,
    )
}

fun encodeEpisodeSelectionHandoff(handoff: EpisodeSelectionHandoff): String =
    episodeSelectionHandoffJson.encodeToString(handoff)

fun decodeEpisodeSelectionHandoff(value: String?): EpisodeSelectionHandoff? =
    value?.takeIf { it.isNotBlank() }?.let { encoded ->
        runCatching { episodeSelectionHandoffJson.decodeFromString<EpisodeSelectionHandoff>(encoded) }
            .getOrNull()
    }

private fun FileVersion.episodeSourceScore(intent: EpisodeSourceIntent): Int {
    var score = 0
    if (
        intent.videoCodec != null &&
        normalizedEpisodeToken(codecVideo ?: videoTracks?.firstOrNull()?.codec) == intent.videoCodec
    ) {
        score++
    }
    if (intent.dynamicRange != null && episodeDynamicRange() == intent.dynamicRange) score++
    if (intent.container != null && normalizedEpisodeToken(container) == intent.container) score++
    return score
}

private fun FileVersion.episodeDynamicRange(): EpisodeDynamicRange {
    val tracks = videoTracks.orEmpty()
    val isDolbyVision = tracks.any { track ->
        DolbyVisionDetection.isDolbyVision(
            dolbyVisionProfile = track.dolbyVisionProfile,
            hdrFormat = track.hdrFormat,
            videoCodec = track.codec,
        )
    } || DolbyVisionDetection.isDolbyVision(videoCodec = codecVideo)
    return when {
        isDolbyVision -> EpisodeDynamicRange.DOLBY_VISION
        hdr || tracks.any { it.hdr } -> EpisodeDynamicRange.HDR
        else -> EpisodeDynamicRange.SDR
    }
}

private fun PlayerSubtitleInfo.toEpisodeSubtitleIntent(): EpisodeSubtitleIntent = EpisodeSubtitleIntent(
    mode = EpisodeSubtitleMode.TRACK,
    language = canonicalSubtitleLanguage(language),
    codecFamily = normalizedSubtitleCodecFamily(codec),
    forced = forced,
    hearingImpaired = subtitleLabelIndicatesHearingImpaired(catalogLabel ?: label),
    external = episodeSubtitleExternal(),
)

private fun PlayerSubtitleInfo.matchesEpisodeSubtitleIntent(intent: EpisodeSubtitleIntent): Boolean =
    (intent.language == null || canonicalSubtitleLanguage(language) == intent.language) &&
        (intent.codecFamily == null || normalizedSubtitleCodecFamily(codec) == intent.codecFamily) &&
        (intent.forced == null || forced == intent.forced) &&
        (intent.hearingImpaired == null || episodeSubtitleHearingImpaired() == intent.hearingImpaired) &&
        (intent.external == null || episodeSubtitleExternal() == intent.external)

private fun PlayerSubtitleInfo.episodeSubtitleHearingImpaired(): Boolean =
    subtitleLabelIndicatesHearingImpaired(catalogLabel ?: label)

private fun PlayerSubtitleInfo.episodeSubtitleExternal(): Boolean? = when (
    (catalogSource ?: source)?.trim()?.lowercase()
) {
    "embedded" -> false
    "external", "downloaded" -> true
    else -> null
}

private fun normalizedEpisodeResolution(value: String?): String? {
    val normalized = value?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    return when {
        normalized.contains("4320") || normalized.contains("8k") -> "4320p"
        normalized.contains("2160") || normalized.contains("4k") || normalized.contains("uhd") -> "2160p"
        normalized.contains("1440") || normalized.contains("qhd") -> "1440p"
        normalized.contains("1080") || normalized.contains("fhd") -> "1080p"
        normalized.contains("720") || normalized.contains("hd") -> "720p"
        normalized.contains("576") -> "576p"
        normalized.contains("480") || normalized.contains("sd") -> "480p"
        else -> normalized
    }
}

private fun normalizedEpisodeToken(value: String?): String? =
    value
        ?.trim()
        ?.lowercase()
        ?.filter(Char::isLetterOrDigit)
        ?.takeIf { it.isNotEmpty() }

private val episodeSelectionHandoffJson = Json {
    encodeDefaults = false
    explicitNulls = false
    ignoreUnknownKeys = true
}
