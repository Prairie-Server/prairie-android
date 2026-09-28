package org.prairieserver.prairie.model.playback

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Prairie-only explicit transcode request (`POST /api/v1/playback/transcode/start`),
 * used by the quality-ladder / server-fallback path in PlaybackSessionManager.
 */
@Serializable
data class TranscodeStartRequest(
    @SerialName("session_id") val sessionId: String,
    @SerialName("seek_seconds") val seekSeconds: Double = 0.0,
    @SerialName("target_resolution") val targetResolution: String = "",
    @SerialName("target_codec_video") val targetCodecVideo: String = "h264",
    @SerialName("target_codec_audio") val targetCodecAudio: String = "aac",
    @SerialName("target_bitrate_kbps") val targetBitrateKbps: Int = 0,
    @SerialName("segment_duration") val segmentDuration: Int = 2,
    @SerialName("audio_track_index") val audioTrackIndex: Int? = null,
    @SerialName("subtitle_track_index") val subtitleTrackIndex: Int? = null,
    @SerialName("subtitle_burn_in") val subtitleBurnIn: Boolean = false,
)

@Serializable
data class TranscodeStartResponse(
    @SerialName("session_id") val sessionId: String,
    val status: String = "",
    @SerialName("switched_file_id") val switchedFileId: Int? = null,
    @SerialName("manifest_url") val manifestUrl: String,
    @SerialName("duration_seconds") val durationSeconds: Double? = null,
    @SerialName("player_start_seconds") val playerStartSeconds: Double = 0.0,
    @SerialName("stream_origin_seconds") val streamOriginSeconds: Double = 0.0,
    @SerialName("timeline_offset_seconds") val timelineOffsetSeconds: Double = 0.0,
    @SerialName("can_seek_anywhere") val canSeekAnywhere: Boolean = false,
)
