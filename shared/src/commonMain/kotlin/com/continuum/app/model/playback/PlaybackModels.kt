package com.continuum.app.model.playback

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PlayMethod {
    @SerialName("direct") DIRECT,
    @SerialName("remux") REMUX,
    @SerialName("transcode") TRANSCODE
}

@Serializable
data class PlaybackSessionResponse(
    @SerialName("session_id") val sessionId: String,
    @SerialName("user_id") val userId: Int,
    @SerialName("profile_id") val profileId: String? = null,
    @SerialName("media_file_id") val mediaFileId: Int,
    @SerialName("play_method") val playMethod: PlayMethod,
    val position: Double = 0.0,
    @SerialName("is_paused") val isPaused: Boolean = false,
    @SerialName("stream_url") val streamUrl: String,
    @SerialName("audio_track_index") val audioTrackIndex: Int = 0,
    @SerialName("duration_seconds") val durationSeconds: Double? = null,
    @SerialName("subtitle_urls") val subtitleUrls: List<PlayerSubtitleInfo>? = null,
    @SerialName("playback_info") val playbackInfo: PlaybackInfo? = null
)

@Serializable
data class PlaybackInfo(
    @SerialName("stream_type") val streamType: String? = null,
    @SerialName("transcode_audio") val transcodeAudio: Boolean = false,
    @SerialName("video_codec") val videoCodec: String? = null,
    @SerialName("audio_codec") val audioCodec: String? = null
)

@Serializable
data class PlayerSubtitleInfo(
    val index: Int,
    val language: String? = null,
    val codec: String? = null,
    val label: String? = null,
    val source: String? = null,
    val forced: Boolean? = null,
    val url: String
)

/**
 * Granular HDR support advertised by the client. Optional; absent means the
 * server uses the legacy [ClientCodecCapabilities.hdr] boolean for SDR-vs-HDR
 * version selection.
 *
 * Dolby Vision profile numbers map to MediaCodec constants:
 *   - 5 = `DolbyVisionProfileDvheStn` / `DvheSt`
 *   - 7 = `DolbyVisionProfileDvheDtb` / `DvheDtr` (BL+EL — needs codec multi-instance)
 *   - 8 = `DolbyVisionProfileDvheSt4k` / `DvavSe`
 */
@Serializable
data class HdrCapabilities(
    val hdr10: Boolean = false,
    @SerialName("hdr10_plus") val hdr10Plus: Boolean = false,
    val hlg: Boolean = false,
    @SerialName("dolby_vision_profiles") val dolbyVisionProfiles: List<Int> = emptyList(),
)

/**
 * Audio passthrough support advertised by the client — what the connected sink
 * (HDMI receiver, soundbar, headphones) can decode bit-exact. Distinct from
 * [ClientCodecCapabilities.codecsAudio], which describes what the client can
 * decode in software/hardware. Passthrough capability comes from
 * `AudioCapabilities.getCapabilities` / `AudioCapabilitiesReceiver`.
 */
@Serializable
data class AudioPassthroughCapabilities(
    @SerialName("passthrough_codecs") val passthroughCodecs: List<String> = emptyList(),
    @SerialName("spatializer_enabled") val spatializerEnabled: Boolean = false,
    @SerialName("max_channels") val maxChannels: Int = 2,
)

@Serializable
data class ClientCodecCapabilities(
    @SerialName("codecs_video") val codecsVideo: List<String> = emptyList(),
    @SerialName("codecs_audio") val codecsAudio: List<String> = emptyList(),
    val containers: List<String> = emptyList(),
    @SerialName("max_resolution") val maxResolution: String? = null,
    val hdr: Boolean = false,
    @SerialName("hdr_details") val hdrDetails: HdrCapabilities? = null,
    @SerialName("audio_passthrough") val audioPassthrough: AudioPassthroughCapabilities? = null,
)

/**
 * Body for `POST /api/v1/playback/start`.
 *
 * The server expects codec/container/HDR fields **flat at the top level** —
 * see `Continuum/internal/api/handlers/playback.go::startPlaybackRequest`. A
 * previous version of this class nested them under `client_capabilities`,
 * which the Go JSON decoder silently ignored; the server then saw empty codec
 * lists and force-transcoded every stream. Keep this flat.
 */
@Serializable
data class StartPlaybackRequest(
    @SerialName("file_id") val fileId: Int,
    @SerialName("profile_id") val profileId: String? = null,
    @SerialName("play_method") val playMethod: String? = null,
    @SerialName("start_position") val startPosition: Double? = null,
    @SerialName("audio_track_index") val audioTrackIndex: Int? = null,
    @SerialName("codecs_video") val codecsVideo: List<String> = emptyList(),
    @SerialName("codecs_audio") val codecsAudio: List<String> = emptyList(),
    val containers: List<String> = emptyList(),
    @SerialName("max_resolution") val maxResolution: String? = null,
    val hdr: Boolean = false,
    @SerialName("hdr_details") val hdrDetails: HdrCapabilities? = null,
    @SerialName("audio_passthrough") val audioPassthrough: AudioPassthroughCapabilities? = null,
)

@Serializable
data class ProgressRequest(
    val position: Double,
    @SerialName("is_paused") val isPaused: Boolean
)

@Serializable
data class TranscodeStartRequest(
    @SerialName("session_id") val sessionId: String,
    @SerialName("seek_seconds") val seekSeconds: Double,
    @SerialName("target_resolution") val targetResolution: String? = null,
    @SerialName("target_codec_video") val targetCodecVideo: String? = null,
    @SerialName("target_codec_audio") val targetCodecAudio: String? = null,
    @SerialName("target_bitrate_kbps") val targetBitrateKbps: Int,
    @SerialName("segment_duration") val segmentDuration: Int,
    @SerialName("subtitle_track_index") val subtitleTrackIndex: Int,
    @SerialName("subtitle_burn_in") val subtitleBurnIn: Boolean
)

@Serializable
data class TranscodeStartResponse(
    @SerialName("session_id") val sessionId: String,
    val status: String,
    @SerialName("switched_file_id") val switchedFileId: Int? = null,
    @SerialName("manifest_url") val manifestUrl: String,
    @SerialName("duration_seconds") val durationSeconds: Double? = null,
    @SerialName("player_start_seconds") val playerStartSeconds: Double = 0.0,
    @SerialName("timeline_offset_seconds") val timelineOffsetSeconds: Double = 0.0,
    @SerialName("can_seek_anywhere") val canSeekAnywhere: Boolean = false
)

@Serializable
data class ChangeAudioResponse(
    @SerialName("audio_track_index") val audioTrackIndex: Int,
    @SerialName("play_method") val playMethod: PlayMethod,
    @SerialName("stream_url") val streamUrl: String,
    @SerialName("switch_mode") val switchMode: String? = null,
    @SerialName("playback_info") val playbackInfo: PlaybackInfo? = null
)
