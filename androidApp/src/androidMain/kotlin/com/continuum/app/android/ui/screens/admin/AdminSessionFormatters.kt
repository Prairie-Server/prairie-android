package com.continuum.app.android.ui.screens.admin

import com.continuum.app.android.ui.util.formatClockTime
import com.continuum.app.model.admin.AdminSession

/**
 * Pure, UI-independent formatters for the admin sessions surface. Kept separate
 * from the composable so they can be unit-tested without Robolectric/Compose.
 *
 * The summary/resolution/bitrate helpers take primitive inputs (not an
 * [AdminSession]) so the contract is stable regardless of how the landed model
 * exposes its transcode detail; the [SessionRow] composable maps the real
 * fields (Kbps bitrates, "WxH" resolution strings, source/target codecs) onto
 * these helpers.
 */

internal fun playMethodLabel(playMethod: String): String = when (playMethod.lowercase()) {
    "directplay", "direct_play", "direct play" -> "Direct Play"
    "directstream", "direct_stream", "direct stream" -> "Direct Stream"
    "transcode" -> "Transcode"
    else -> playMethod.ifBlank { "Playing" }
}

internal fun bitrateLabel(bitrateBps: Long?): String? {
    val bps = bitrateBps ?: return null
    if (bps <= 0) return null
    return if (bps >= 1_000_000) "%.1f Mbps".format(bps / 1_000_000.0) else "${bps / 1000} Kbps"
}

internal fun resolutionLabel(width: Int?, height: Int?): String? {
    val h = height ?: return null
    if (h <= 0) return null
    return when {
        h >= 2000 -> "4K"
        h >= 1080 -> "1080p"
        h >= 720 -> "720p"
        h <= 480 -> "480p"
        else -> "${h}p"
    }
}

internal fun sessionSummaryLine(
    isTranscoding: Boolean,
    playMethod: String,
    bitrateBps: Long?,
    widthTarget: Int?,
    heightTarget: Int?,
    videoCodecSource: String?,
    videoCodecTarget: String?,
): String {
    val head = if (isTranscoding) {
        val src = videoCodecSource?.takeIf { it.isNotBlank() }
        val dst = videoCodecTarget?.takeIf { it.isNotBlank() }
        if (src != null && dst != null && src != dst) "${playMethodLabel(playMethod)} $src→$dst"
        else playMethodLabel(playMethod)
    } else {
        playMethodLabel(playMethod)
    }
    val parts = listOfNotNull(head, bitrateLabel(bitrateBps), resolutionLabel(widthTarget, heightTarget))
    return parts.joinToString(" • ")
}

internal fun sessionProgressLabel(positionSeconds: Double, durationSeconds: Double): String {
    val pos = formatClockTime(positionSeconds)
    if (durationSeconds <= 0.0 || durationSeconds.isNaN()) return pos
    return "$pos / ${formatClockTime(durationSeconds)}"
}

internal fun seasonEpisodeLabel(season: Int?, episode: Int?): String? =
    if (season != null && episode != null) "S${season}E$episode" else null

// ---------------------------------------------------------------------------
// AdminSession adapters — bridge the landed model onto the pure helpers above.
// ---------------------------------------------------------------------------

/**
 * Whether the session is transcoding video and/or audio. The landed model has
 * no boolean flag; it carries per-stream decisions and a transcode-audio flag.
 */
internal fun AdminSession.isTranscoding(): Boolean =
    videoDecision.equals("transcode", ignoreCase = true) ||
        audioDecision.equals("transcode", ignoreCase = true) ||
        transcodeAudio ||
        playMethod.replace("_", "").replace(" ", "").equals("transcode", ignoreCase = true)

/** Best-available stream bitrate in bps: target > stream > source (model is Kbps). */
internal fun AdminSession.effectiveBitrateBps(): Long? =
    (targetBitrateKbps ?: streamBitrateKbps ?: sourceBitrateKbps)
        ?.takeIf { it > 0 }
        ?.toLong()
        ?.times(1000L)

/** Parses a "WIDTHxHEIGHT" resolution string into a height in pixels, if present. */
internal fun parseResolutionHeight(resolution: String): Int? {
    if (resolution.isBlank()) return null
    val parts = resolution.lowercase().split("x")
    if (parts.size != 2) return null
    return parts[1].trim().toIntOrNull()?.takeIf { it > 0 }
}

internal fun parseResolutionWidth(resolution: String): Int? {
    if (resolution.isBlank()) return null
    val parts = resolution.lowercase().split("x")
    if (parts.size != 2) return null
    return parts[0].trim().toIntOrNull()?.takeIf { it > 0 }
}

/** Builds the one-line transcode/play summary from the landed [AdminSession]. */
internal fun AdminSession.summaryLine(): String {
    val transcoding = isTranscoding()
    val resolution = targetResolution.ifBlank { sourceVideoResolution }
    return sessionSummaryLine(
        isTranscoding = transcoding,
        playMethod = playMethod,
        bitrateBps = effectiveBitrateBps(),
        widthTarget = parseResolutionWidth(resolution),
        heightTarget = parseResolutionHeight(resolution),
        videoCodecSource = sourceVideoCodec,
        videoCodecTarget = targetVideoCodec.ifBlank { sourceVideoCodec },
    )
}

/** Position-of-duration label (duration from the landed `file_duration` seconds). */
internal fun AdminSession.progressLabel(): String =
    sessionProgressLabel(positionSeconds, (fileDuration ?: 0).toDouble())

/** "SxEy" line for episodes; null for movies / missing numbering. */
internal fun AdminSession.seasonEpisode(): String? =
    seasonEpisodeLabel(seasonNumber, episodeNumber)
