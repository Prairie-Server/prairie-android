package org.prairieserver.prairie.playback

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.prairieserver.prairie.network.ApiResult
import kotlin.math.abs

/** One rung of the server's transcode ladder. Key selection on [id] — never label/height. */
@Serializable
data class QualityLadderRung(
    val id: String,
    val label: String,
    val resolution: String,
    val height: Int,
    @SerialName("bitrate_kbps") val bitrateKbps: Int,
)

/** Picker payload from `GET /api/v1/playback/quality-ladder`. */
@Serializable
data class QualityLadderResponse(
    val rungs: List<QualityLadderRung> = emptyList(),
    val modes: List<String> = emptyList(),
    @SerialName("source_height") val sourceHeight: Int? = null,
)

/** One row in the in-player quality menu. */
data class QualityMenuOption(
    val id: String,
    val label: String,
    val sublabel: String = "",
    val resolution: String = "",
    val bitrateKbps: Int = 0,
    val isOriginal: Boolean = false,
    val isAuto: Boolean = false,
)

/**
 * Resolved encode targets for a menu selection.
 *
 * Null [QualityTargets] for Original on a direct-play base means drop HLS and
 * play the raw file. Remux Original uses [copyVideo]=true.
 */
data class QualityTargets(
    val resolution: String,
    val bitrateKbps: Int,
    val copyVideo: Boolean,
)

/**
 * Fallback ladder when the server cannot be reached.
 * Mirrors `internal/playback/quality_ladder.go` and web/smarttv FALLBACK_LADDER.
 */
val FALLBACK_QUALITY_LADDER: List<QualityLadderRung> = listOf(
    QualityLadderRung("2160p", "4K", "2160p", 2160, 20_000),
    QualityLadderRung("1080p-high", "1080p High", "1080p", 1080, 10_000),
    QualityLadderRung("1080p", "1080p", "1080p", 1080, 6_000),
    QualityLadderRung("720p-high", "720p High", "720p", 720, 4_000),
    QualityLadderRung("720p", "720p", "720p", 720, 2_000),
    QualityLadderRung("480p", "480p", "480p", 480, 1_500),
    QualityLadderRung("420p", "420p", "420p", 420, 720),
)

val DEFAULT_QUALITY_MODES: List<String> = listOf("auto", "original")

/** True when every rung is fully populated (all-or-nothing). */
fun isValidQualityLadder(rungs: List<QualityLadderRung>?): Boolean {
    if (rungs.isNullOrEmpty()) return false
    return rungs.all { rung ->
        rung.id.isNotBlank() &&
            rung.label.isNotBlank() &&
            rung.resolution.isNotBlank() &&
            rung.height > 0 &&
            rung.bitrateKbps > 0
    }
}

/**
 * Caps a ladder to rungs the source can offer (highest first).
 * Mirrors server `QualityLadderFor`: omit upscales; +8 tolerance; never empty.
 */
fun qualityLadderForSourceHeight(
    ladder: List<QualityLadderRung>,
    sourceHeight: Int,
): List<QualityLadderRung> {
    if (sourceHeight <= 0) return ladder.toList()
    val out = ladder.filter { it.height <= sourceHeight + 8 }
    return out.ifEmpty { listOf(ladder.last()) }
}

fun formatQualityBitrate(kbps: Int): String {
    if (kbps >= 1000) {
        val tenths = (kbps + 50) / 100 // round to 0.1 Mbps
        return if (tenths % 10 == 0) {
            "${tenths / 10} Mbps"
        } else {
            "${tenths / 10}.${tenths % 10} Mbps"
        }
    }
    return "$kbps kbps"
}

/** Numeric height for a resolution token, preferring the live ladder. */
fun resolveNativeHeight(resolution: String, ladder: List<QualityLadderRung>): Int {
    val needle = resolution.trim().lowercase()
    ladder.firstOrNull { it.resolution.equals(needle, ignoreCase = true) && it.height > 0 }
        ?.let { return it.height }

    return when (needle) {
        "2160p", "4k", "uhd" -> 2160
        "1440p" -> 1440
        "1080p", "fhd" -> 1080
        "720p", "hd" -> 720
        "480p", "sd" -> 480
        "420p" -> 420
        else -> needle.removeSuffix("p").toIntOrNull()?.takeIf { it > 0 } ?: 0
    }
}

fun sourceHeightForFile(
    ladder: List<QualityLadderRung>,
    resolution: String?,
    probedHeight: Int? = null,
): Int {
    if (probedHeight != null && probedHeight > 0) return probedHeight
    if (resolution.isNullOrBlank()) return 0
    return resolveNativeHeight(resolution, ladder)
}

private fun playMethodLabel(playMethod: String?): String = when (playMethod?.trim()?.lowercase()) {
    "direct" -> "Direct Play"
    "remux" -> "Remux"
    "transcode" -> "Transcode"
    else -> ""
}

/**
 * Builds the quality menu: modes (`auto`, `original`) first, then rungs
 * strictly below native height (Original already covers source resolution).
 */
fun buildQualityOptions(
    ladder: List<QualityLadderRung>,
    nativeHeight: Int,
    playMethod: String? = null,
    sourceResolutionLabel: String? = null,
    sourceBitrateKbps: Int = 0,
    modes: List<String> = DEFAULT_QUALITY_MODES,
): List<QualityMenuOption> {
    val options = mutableListOf<QualityMenuOption>()
    val orderedModes = modes.ifEmpty { DEFAULT_QUALITY_MODES }

    for (mode in orderedModes) {
        when (val id = mode.trim().lowercase()) {
            "auto" -> options += QualityMenuOption(id = "auto", label = "Auto", isAuto = true)
            "original", "source", "max" -> {
                val res = sourceResolutionLabel?.trim().orEmpty()
                val displayRes = when {
                    res == "2160p" -> "4K"
                    res.isEmpty() -> "Original"
                    else -> res
                }
                val methodLabel = playMethodLabel(playMethod)
                val bitrateLabel =
                    if (sourceBitrateKbps > 0) formatQualityBitrate(sourceBitrateKbps) else ""
                val sublabel = listOf(methodLabel, bitrateLabel).filter { it.isNotEmpty() }
                    .joinToString(" · ")
                options += QualityMenuOption(
                    id = "original",
                    label = if (res.isEmpty()) "Original" else "Original ($displayRes)",
                    sublabel = sublabel,
                    isOriginal = true,
                )
            }
        }
    }

    if (nativeHeight <= 0) {
        for (tier in ladder) {
            options += QualityMenuOption(
                id = tier.id,
                label = tier.label,
                sublabel = "~${formatQualityBitrate(tier.bitrateKbps)}",
                resolution = tier.resolution,
                bitrateKbps = tier.bitrateKbps,
            )
        }
        return options
    }

    for (tier in ladder) {
        if (tier.height >= nativeHeight) continue
        options += QualityMenuOption(
            id = tier.id,
            label = tier.label,
            sublabel = "~${formatQualityBitrate(tier.bitrateKbps)}",
            resolution = tier.resolution,
            bitrateKbps = tier.bitrateKbps,
        )
    }
    return options
}

/** Best rung at or below [maxHeight] for Auto starts. */
fun bestAutoRung(ladder: List<QualityLadderRung>, maxHeight: Int): QualityLadderRung? {
    if (ladder.isEmpty()) return null
    if (maxHeight <= 0) return ladder.first()
    return ladder.firstOrNull { it.height <= maxHeight + 8 } ?: ladder.last()
}

/**
 * Resolves a quality menu id to transcode targets.
 * Returns null for `original` on a direct-play base (caller should drop HLS).
 */
fun resolveQualityTargets(
    qualityId: String,
    options: List<QualityMenuOption>,
    playMethod: String?,
    ladder: List<QualityLadderRung>,
    deviceMaxHeight: Int = 0,
): QualityTargets? {
    val id = qualityId.trim().lowercase()
    if (id == "original") {
        val method = playMethod?.trim()?.lowercase().orEmpty()
        if (method == "direct") return null
        if (method == "remux") {
            return QualityTargets(resolution = "", bitrateKbps = 0, copyVideo = true)
        }
        val top = bestAutoRung(ladder, deviceMaxHeight)
        return QualityTargets(
            resolution = "",
            bitrateKbps = top?.bitrateKbps ?: 0,
            copyVideo = false,
        )
    }

    if (id == "auto") {
        val rung = bestAutoRung(ladder, deviceMaxHeight)
        if (rung == null) {
            return QualityTargets(resolution = "1080p", bitrateKbps = 6_000, copyVideo = false)
        }
        return QualityTargets(
            resolution = rung.resolution,
            bitrateKbps = rung.bitrateKbps,
            copyVideo = false,
        )
    }

    options.firstOrNull { it.id == qualityId && it.resolution.isNotEmpty() && it.bitrateKbps > 0 }
        ?.let {
            return QualityTargets(
                resolution = it.resolution,
                bitrateKbps = it.bitrateKbps,
                copyVideo = false,
            )
        }
    ladder.firstOrNull { it.id == qualityId }?.let {
        return QualityTargets(
            resolution = it.resolution,
            bitrateKbps = it.bitrateKbps,
            copyVideo = false,
        )
    }
    return null
}

/**
 * Maps a ladder menu id to a protocol-v3 `quality_preference` token.
 *
 * V3 [NormalizeQualityV3] only knows auto/original/2160p/1080p/720p/480p.
 * High variants and 420p collapse to their nearest supported resolution so a
 * replan still encodes at the intended height; callers that need the exact
 * bitrate should also pass [QualityTargets] into `transcode/start`.
 */
fun toV3QualityPreference(qualityId: String): String {
    val id = qualityId.trim().lowercase()
    return when {
        id.isEmpty() || id == "auto" -> "auto"
        id == "original" || id == "source" || id == "max" -> "original"
        id == "4k" || id == "uhd" || id == "2160p" -> "2160p"
        id.startsWith("1080") || id == "fhd" -> "1080p"
        id.startsWith("720") || id == "hd" -> "720p"
        id.startsWith("480") || id == "sd" || id.startsWith("420") -> "480p"
        else -> id
    }
}

/** Parse a ladder response body; invalid bodies yield null so callers can fall back. */
fun parseQualityLadderResponse(response: QualityLadderResponse): List<QualityLadderRung>? =
    response.rungs.takeIf(::isValidQualityLadder)

/**
 * Process-wide quality-ladder client: fetch once, cache successes, never cache
 * failures (mirrors web/smarttv).
 *
 * [fetchResponse] is typically [org.prairieserver.prairie.network.api.PlaybackApi.getQualityLadder].
 */
class QualityLadderClient(
    private val fetchResponse: suspend () -> ApiResult<QualityLadderResponse>,
) {
    private val mutex = Mutex()
    @Volatile
    private var cached: List<QualityLadderRung>? = null

    fun cachedOrFallback(sourceHeight: Int = 0): List<QualityLadderRung> =
        qualityLadderForSourceHeight(cached ?: FALLBACK_QUALITY_LADDER, sourceHeight)

    suspend fun fetch(sourceHeight: Int = 0): List<QualityLadderRung> {
        val ladder = loadLadder()
        return qualityLadderForSourceHeight(ladder, sourceHeight)
    }

    fun resetCacheForTests() {
        cached = null
    }

    private suspend fun loadLadder(): List<QualityLadderRung> {
        cached?.let { return it }
        return mutex.withLock {
            cached?.let { return it }
            when (val result = fetchResponse()) {
                is ApiResult.Success -> {
                    val rungs = parseQualityLadderResponse(result.data)
                    if (rungs != null) {
                        cached = rungs
                        rungs
                    } else {
                        FALLBACK_QUALITY_LADDER
                    }
                }
                is ApiResult.Error, is ApiResult.NetworkError -> FALLBACK_QUALITY_LADDER
            }
        }
    }
}

/** Nearest rung at [resolution] by bitrate distance — mirrors server RungForSession. */
fun rungForSession(
    ladder: List<QualityLadderRung>,
    resolution: String,
    bitrateKbps: Int,
): QualityLadderRung? {
    if (resolution.isBlank()) return null
    return ladder
        .filter { it.resolution.equals(resolution, ignoreCase = true) }
        .minByOrNull { abs(it.bitrateKbps - bitrateKbps) }
}
