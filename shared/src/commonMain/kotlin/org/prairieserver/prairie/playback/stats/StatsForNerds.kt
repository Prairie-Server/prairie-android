package org.prairieserver.prairie.playback.stats

import org.prairieserver.prairie.model.playback.PlayMethod
import org.prairieserver.prairie.model.playback.PlaybackDelivery
import org.prairieserver.prairie.model.playback.PlaybackExecutionPlan

/**
 * Prairie "stats for nerds" building blocks shared by the phone sheet and the
 * TV HUD, at parity with prairie-smarttv (PR #116): the server's plan in one
 * line, the audio track actually playing, the stream path handed to the
 * player (never its query string or tokens), the quality preference, and the
 * last few player events.
 *
 * Everything here is pure so the formatting is unit-tested once for both apps.
 */

/** One label/value row in a stats panel. */
data class StatsRow(val label: String, val value: String)

// ---------------------------------------------------------------------------
// Player event ring buffer
// ---------------------------------------------------------------------------

enum class PlayerEventSeverity { INFO, WARNING, ERROR }

data class PlayerEvent(
    val atEpochMs: Long,
    val severity: PlayerEventSeverity,
    val message: String,
    /** How many identical consecutive events this entry stands for. */
    val repeat: Int = 1,
)

/**
 * Immutable ring buffer of the most recent player events, oldest first.
 *
 * Consecutive identical messages collapse into one entry with a repeat count
 * (and the newest timestamp), so a burst of dropped-frame or buffering events
 * cannot push the one error that matters out of the window.
 */
data class PlayerEventLog(
    val capacity: Int = DEFAULT_CAPACITY,
    val entries: List<PlayerEvent> = emptyList(),
) {
    fun append(event: PlayerEvent): PlayerEventLog {
        val size = capacity.coerceAtLeast(1)
        val last = entries.lastOrNull()
        val next = if (last != null && last.message == event.message && last.severity == event.severity) {
            entries.dropLast(1) + event.copy(repeat = last.repeat + event.repeat)
        } else {
            entries + event
        }
        return copy(entries = if (next.size > size) next.takeLast(size) else next)
    }

    fun append(
        atEpochMs: Long,
        severity: PlayerEventSeverity,
        message: String,
    ): PlayerEventLog = append(PlayerEvent(atEpochMs, severity, redactUrlSecrets(message)))

    val isEmpty: Boolean get() = entries.isEmpty()

    companion object {
        /** Same window as prairie-smarttv's overlay. */
        const val DEFAULT_CAPACITY = 8
    }
}

/**
 * `HH:mm:ss` wall-clock time for [epochMs], shifted by [utcOffsetMs] (the
 * platform supplies its zone offset; pure so tests pin it).
 */
fun formatEventClock(epochMs: Long, utcOffsetMs: Long = 0L): String {
    val dayMs = 24L * 60 * 60 * 1000
    val local = ((epochMs + utcOffsetMs) % dayMs + dayMs) % dayMs
    val totalSeconds = local / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "${h.pad2()}:${m.pad2()}:${s.pad2()}"
}

/** `"14:03:07 ERROR Load error ×3"`; INFO carries no severity tag. */
fun PlayerEvent.displayLine(utcOffsetMs: Long = 0L): String = buildString {
    append(formatEventClock(atEpochMs, utcOffsetMs))
    when (severity) {
        PlayerEventSeverity.INFO -> Unit
        PlayerEventSeverity.WARNING -> append(" WARN")
        PlayerEventSeverity.ERROR -> append(" ERROR")
    }
    append(' ')
    append(message)
    if (repeat > 1) append(" ×").append(repeat)
}

private fun Long.pad2(): String = toString().padStart(2, '0')

// ---------------------------------------------------------------------------
// URL redaction
// ---------------------------------------------------------------------------

private val urlWithQuery = Regex("""([a-zA-Z][a-zA-Z0-9+.-]*://[^\s?#"']*)[?#][^\s"']*""")
private val userInfo = Regex("""://[^/@\s]+@""")

/**
 * Strips query strings, fragments and userinfo from every URL in free text
 * (player error messages quote the failing URL, and Prairie stream URLs carry
 * the stream token in the query).
 */
fun redactUrlSecrets(text: String): String =
    text.replace(urlWithQuery) { it.groupValues[1] + "?…" }
        .replace(userInfo, "://")

/** What is actually being played, safe to put on screen. */
data class StreamUrlDescription(
    /** "HLS", "DASH", "Progressive" or "Local file". */
    val kind: String,
    /** Path only: no scheme, host, userinfo, query or fragment; token-like segments elided. */
    val path: String,
) {
    fun displayValue(): String = "$kind · $path"
}

/**
 * Describes [url] for the stats panel. [streamType] is the plan's
 * `stream_type` hint (e.g. "hls") and wins over the extension when present.
 */
fun describeStreamUrl(url: String?, streamType: String? = null): StreamUrlDescription? {
    val raw = url?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val schemeEnd = raw.indexOf("://")
    val scheme = if (schemeEnd > 0) raw.substring(0, schemeEnd).lowercase() else ""
    val afterScheme = if (schemeEnd > 0) raw.substring(schemeEnd + 3) else raw
    val withoutQuery = afterScheme.substringBefore('?').substringBefore('#')
    val isLocal = scheme == "file" || scheme == "content" || (schemeEnd < 0 && raw.startsWith("/"))
    val path = when {
        schemeEnd > 0 && !isLocal -> "/" + withoutQuery.substringAfter('/', "")
        else -> withoutQuery
    }
    val safePath = path.split('/')
        .joinToString("/") { segment -> if (looksLikeSecret(segment)) "…" else segment }
        .ifEmpty { "/" }
    val lowerPath = path.lowercase()
    val hint = streamType?.trim()?.lowercase().orEmpty()
    val kind = when {
        isLocal -> "Local file"
        hint.contains("hls") || lowerPath.endsWith(".m3u8") -> "HLS"
        hint.contains("dash") || lowerPath.endsWith(".mpd") -> "DASH"
        else -> "Progressive"
    }
    return StreamUrlDescription(kind = kind, path = if (isLocal) safePath.substringAfterLast('/') else safePath)
}

/**
 * A path segment that is probably a credential: a JWT (three base64url
 * parts) or a long opaque run with no file extension. Content ids (UUIDs,
 * short hashes) stay visible because they are what a bug report needs.
 */
private fun looksLikeSecret(segment: String): Boolean {
    if (segment.length < 40) return false
    val jwtParts = segment.split('.')
    if (jwtParts.size == 3 && jwtParts.all { it.length >= 8 }) return true
    val stem = segment.substringBeforeLast('.', segment)
    return stem.length >= 40 && stem.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '=' }
}

// ---------------------------------------------------------------------------
// Plan / track / quality summaries
// ---------------------------------------------------------------------------

/** Human label for how the server is delivering this session. */
fun playMethodLabel(method: PlayMethod?, delivery: PlaybackDelivery?): String? = when (method) {
    PlayMethod.DIRECT -> "Direct play"
    PlayMethod.REMUX -> "Remux"
    PlayMethod.TRANSCODE -> "Transcode"
    null -> when (delivery) {
        PlaybackDelivery.ORIGINAL_HTTP -> "Direct play"
        PlaybackDelivery.SERVER_REMUX_HLS, PlaybackDelivery.SERVER_REMUX_PROGRESSIVE -> "Remux"
        PlaybackDelivery.SERVER_TRANSCODE_HLS -> "Transcode"
        PlaybackDelivery.CLIENT_LOCAL_NORMALIZATION -> "Local normalization"
        null -> null
    }
}

/** Wire name of the delivery, matching the server/smarttv vocabulary. */
fun PlaybackDelivery.wireName(): String = when (this) {
    PlaybackDelivery.ORIGINAL_HTTP -> "original_http"
    PlaybackDelivery.SERVER_REMUX_HLS -> "server_remux_hls"
    PlaybackDelivery.SERVER_REMUX_PROGRESSIVE -> "server_remux_progressive"
    PlaybackDelivery.SERVER_TRANSCODE_HLS -> "server_transcode_hls"
    PlaybackDelivery.CLIENT_LOCAL_NORMALIZATION -> "client_local_normalization"
}

/**
 * One-line plan summary in smarttv's shape, e.g.
 * `Remux · server_remux_progressive · container=mp4 · video=hevc 3840x1600 hdr10 · audio=aac`.
 */
fun PlaybackExecutionPlan.statsSummary(): String {
    val video = listOfNotNull(
        source.videoCodec.clean(),
        source.resolution.clean(),
        source.hdrFormat.clean(),
    ).joinToString(" ")
    val audio = (claims.audio.codec.clean() ?: source.audioCodec.clean())
        ?.let { codec -> if (claims.audio.passthrough) "$codec passthrough" else codec }
    return listOfNotNull(
        playMethodLabel(stream.playMethod, delivery),
        delivery.wireName(),
        source.container.clean()?.let { "container=$it" },
        video.ifEmpty { null }?.let { "video=$it" },
        audio?.let { "audio=$it" },
    ).joinToString(" · ")
}

/**
 * Why the planner chose this route: its decision trace when it sent one,
 * else degradation warnings, else the audio claim's reason.
 */
fun PlaybackExecutionPlan.statsReason(): String? {
    val trace = decisionTrace.mapNotNull { it.clean() }
    if (trace.isNotEmpty()) return trace.takeLast(2).joinToString(" → ")
    val warnings = degradationWarnings.mapNotNull { it.message.clean() ?: it.code.clean() }
    if (warnings.isNotEmpty()) return warnings.joinToString("; ")
    return claims.audio.reason.clean() ?: claims.video.dolbyVisionReason.clean()
}

/** Server-side transformations applied to this plan, e.g. "audio_transcode, subtitle_burn". */
fun PlaybackExecutionPlan.statsTransformations(): String? =
    transformations.mapNotNull { it.name.clean() }.distinct().joinToString(", ").ifEmpty { null }

/** `"Mono"`, `"Stereo"`, `"5.1"`, `"7.1"`, else `"<n>ch"`. */
fun audioChannelsLabel(channels: Int?): String? = when {
    channels == null || channels <= 0 -> null
    channels == 1 -> "Mono"
    channels == 2 -> "Stereo"
    channels == 6 -> "5.1"
    channels == 8 -> "7.1"
    else -> "${channels}ch"
}

/**
 * `"#2 · eng · EAC3 5.1 · Commentary"` — index (1-based ordinal), language,
 * codec + channels, then the title when it adds something.
 */
fun describeAudioTrackForStats(
    ordinal: Int?,
    language: String?,
    codec: String?,
    channels: Int?,
    title: String? = null,
): String? {
    val codecPart = listOfNotNull(codec.clean()?.uppercase(), audioChannelsLabel(channels))
        .joinToString(" ").ifEmpty { null }
    val lang = language.clean()
    val extra = title.clean()?.takeIf { t ->
        !t.equals(lang, ignoreCase = true) && (codecPart == null || !t.equals(codecPart, ignoreCase = true))
    }
    val parts = listOfNotNull(
        ordinal?.takeIf { it >= 0 }?.let { "#${it + 1}" },
        lang,
        codecPart,
        extra,
    )
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}

/** `"Auto"`, `"Original"`, else the ladder id/label as given. */
fun qualityPreferenceLabel(preference: String?): String? {
    val value = preference.clean() ?: return null
    return when (value.lowercase()) {
        "auto", "-1" -> "Auto"
        "original" -> "Original"
        else -> value
    }
}

/** `"12.3 s"` of media buffered ahead of the playhead. */
fun bufferHealthLabel(bufferedAheadMs: Long?): String? {
    val ms = bufferedAheadMs?.takeIf { it >= 0 } ?: return null
    val tenths = (ms + 50) / 100
    return "${tenths / 10}.${tenths % 10} s"
}

/** `"19.4 Mbps"` / `"640 Kbps"` / `"900 bps"`. */
fun formatStatsBitrate(bps: Long): String = when {
    bps >= 1_000_000 -> {
        val tenths = (bps + 50_000) / 100_000
        "${tenths / 10}.${tenths % 10} Mbps"
    }
    bps >= 1_000 -> "${(bps + 500) / 1_000} Kbps"
    else -> "$bps bps"
}

/** What the playback layer knows about the session, beyond the player's own stats. */
data class PlaybackStatsContext(
    val plan: PlaybackExecutionPlan? = null,
    val playMethod: PlayMethod? = null,
    val audioTrack: String? = null,
    val streamUrl: String? = null,
    val qualityPreference: String? = null,
)

/**
 * The plan-level rows, in display order: Plan, Planner reason,
 * Transformations, Audio track, Stream, Quality. Rows with no data are
 * omitted so an offline/local session shows only what applies.
 */
fun PlaybackStatsContext.planRows(): List<StatsRow> = buildList {
    plan?.let { p ->
        add(StatsRow("Plan", p.statsSummary()))
        p.statsReason()?.let { add(StatsRow("Planner reason", it)) }
        p.statsTransformations()?.let { add(StatsRow("Transformations", it)) }
    } ?: playMethodLabel(playMethod, null)?.let { add(StatsRow("Plan", it)) }
    audioTrack.clean()?.let { add(StatsRow("Audio track", it)) }
    describeStreamUrl(streamUrl ?: plan?.stream?.url, plan?.stream?.streamType)
        ?.let { add(StatsRow("Stream", it.displayValue())) }
    qualityPreferenceLabel(qualityPreference)?.let { add(StatsRow("Quality", it)) }
}

private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
