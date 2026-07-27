package org.prairieserver.prairie.model.livetv

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LiveTvChannel(
    val id: String,
    @SerialName("tuner_id") val tunerId: String = "",
    val number: String = "",
    @SerialName("number_override") val numberOverride: String? = null,
    val callsign: String = "",
    val name: String = "",
    @SerialName("logo_url") val logoUrl: String = "",
    val hd: Boolean = false,
    val enabled: Boolean = true,
    @SerialName("stream_url") val streamUrl: String = "",
    @SerialName("guide_station_id") val guideStationId: String = "",
) {
    /** Prefer an admin override when present, otherwise the tuner lineup number. */
    val displayNumber: String
        get() = numberOverride?.takeIf { it.isNotBlank() } ?: number

    val displayName: String
        get() = name.takeIf { it.isNotBlank() }
            ?: callsign.takeIf { it.isNotBlank() }
            ?: displayNumber.ifBlank { id }
}

@Serializable
data class LiveTvChannelsResponse(
    val channels: List<LiveTvChannel> = emptyList(),
)

@Serializable
data class LiveTvProgram(
    val id: String,
    @SerialName("channel_id") val channelId: String = "",
    @SerialName("source_id") val sourceId: String = "",
    @SerialName("series_id") val seriesId: String = "",
    @SerialName("external_id") val externalId: String = "",
    val start: String = "",
    val stop: String = "",
    val title: String = "",
    val subtitle: String = "",
    val description: String = "",
    val season: Int? = null,
    val episode: Int? = null,
    val genres: List<String> = emptyList(),
    @SerialName("image_url") val imageUrl: String = "",
    @SerialName("is_new") val isNew: Boolean = false,
    @SerialName("is_live") val isLive: Boolean = false,
)

@Serializable
data class LiveTvGuideResponse(
    val programs: List<LiveTvProgram> = emptyList(),
    val start: String = "",
    val end: String = "",
)

/**
 * Response from `POST /api/v1/livetv/channels/{id}/session`.
 *
 * [transport] is `"hls"` when the server HLS remux bridge is active, otherwise
 * `"mpegts"` for the authenticated session MPEG-TS proxy. Clients play
 * [playableUrl], then `DELETE /api/v1/livetv/sessions/{sessionId}` when stopped.
 */
@Serializable
data class LiveTvSessionStartResponse(
    @SerialName("session_id") val sessionId: String,
    @SerialName("playback_ticket") val playbackTicket: String = "",
    @SerialName("hls_url") val hlsUrl: String = "",
    @SerialName("stream_url") val streamUrl: String = "",
    val transport: String = "mpegts",
    val note: String = "",
) {
    /** True when the server (or URL shape) indicates HLS remux. */
    val isHls: Boolean
        get() {
            val explicit = transport.trim().lowercase()
            if (explicit == "hls") return true
            if (explicit == "mpegts") return false
            val url = (hlsUrl.ifBlank { streamUrl }).lowercase()
            return url.contains(".m3u8") || url.contains("/live-hls/")
        }

    val playableUrl: String
        get() = when {
            isHls -> hlsUrl.takeIf { it.isNotBlank() } ?: streamUrl
            else -> streamUrl.takeIf { it.isNotBlank() } ?: hlsUrl
        }
}

@Serializable
data class LiveTvSession(
    val id: String,
    @SerialName("channel_id") val channelId: String = "",
    @SerialName("tuner_id") val tunerId: String = "",
    @SerialName("tuner_index") val tunerIndex: Int = 0,
    val status: String = "",
    @SerialName("hls_url") val hlsUrl: String = "",
    @SerialName("stream_url") val streamUrl: String = "",
    val note: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("released_at") val releasedAt: String? = null,
)

@Serializable
data class LiveTvRecording(
    val id: String,
    @SerialName("program_id") val programId: String? = null,
    @SerialName("channel_id") val channelId: String = "",
    @SerialName("series_rule_id") val seriesRuleId: String? = null,
    val status: String = "",
    // Intentionally omit server filesystem `path` from the client model.
    @SerialName("library_item_id") val libraryItemId: String? = null,
    val start: String = "",
    val stop: String = "",
    val title: String = "",
    @SerialName("last_error") val lastError: String = "",
) {
    val isActive: Boolean
        get() = status.equals("scheduled", ignoreCase = true) ||
            status.equals("recording", ignoreCase = true)

    val isCompleted: Boolean
        get() = status.equals("completed", ignoreCase = true)
}

@Serializable
data class LiveTvRecordingsResponse(
    val recordings: List<LiveTvRecording> = emptyList(),
)

/** Guide-based schedule body — server fills channel/window/title from program_id. */
@Serializable
data class LiveTvScheduleRecordingRequest(
    @SerialName("program_id") val programId: String? = null,
)
