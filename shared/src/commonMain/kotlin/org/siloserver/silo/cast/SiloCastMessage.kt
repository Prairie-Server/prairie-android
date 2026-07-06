package org.siloserver.silo.cast

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class SiloCastMessage {
    abstract val v: Int

    @Serializable
    @SerialName("hello")
    data class Hello(
        @SerialName("device_id") val deviceId: String,
        @SerialName("device_name") val deviceName: String,
        @SerialName("device_model") val deviceModel: String? = null,
        @SerialName("app_version") val appVersion: String? = null,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) override val v: Int = SiloCastProtocol.version,
    ) : SiloCastMessage()

    @Serializable
    @SerialName("launch")
    data class Launch(
        val launch: SiloCastLaunchRequest,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) override val v: Int = SiloCastProtocol.version,
    ) : SiloCastMessage()

    @Serializable
    @SerialName("control")
    data class Control(
        @SerialName("control") val command: SiloCastControlCommand,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) override val v: Int = SiloCastProtocol.version,
    ) : SiloCastMessage()

    @Serializable
    @SerialName("state")
    data class State(
        val state: SiloCastPlaybackState,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) override val v: Int = SiloCastProtocol.version,
    ) : SiloCastMessage()

    @Serializable
    @SerialName("error")
    data class Error(
        val error: SiloCastError,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) override val v: Int = SiloCastProtocol.version,
    ) : SiloCastMessage()

    @Serializable
    @SerialName("ping")
    data class Ping(
        val id: String? = null,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) override val v: Int = SiloCastProtocol.version,
    ) : SiloCastMessage()

    @Serializable
    @SerialName("pong")
    data class Pong(
        val id: String? = null,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) override val v: Int = SiloCastProtocol.version,
    ) : SiloCastMessage()

    @Serializable
    @SerialName("close")
    data class Close(
        val reason: String? = null,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) override val v: Int = SiloCastProtocol.version,
    ) : SiloCastMessage()
}

@Serializable
data class SiloCastLaunchRequest(
    @SerialName("server_id") val serverId: String? = null,
    @SerialName("content_id") val contentId: String,
    @SerialName("file_id") val fileId: String? = null,
    @SerialName("media_kind") val mediaKind: String,
    val title: String,
    val subtitle: String? = null,
    @SerialName("resume_time") val resumeTime: Double? = null,
    @SerialName("audio_track_id") val audioTrackId: String? = null,
    @SerialName("subtitle_track_id") val subtitleTrackId: String? = null,
    @SerialName("quality_id") val qualityId: String? = null,
)

@Serializable
data class SiloCastControlCommand(
    val name: String,
    val position: Double? = null,
    val seconds: Double? = null,
    @SerialName("track_id") val trackId: String? = null,
    @SerialName("quality_id") val qualityId: String? = null,
    @SerialName("playback_speed") val playbackSpeed: Double? = null,
    @SerialName("video_gravity") val videoGravity: String? = null,
    val enabled: Boolean? = null,
    @SerialName("delta_ms") val deltaMs: Int? = null,
    val volume: Double? = null,
) {
    companion object {
        const val Play = "play"
        const val Pause = "pause"
        const val PlayPause = "play_pause"
        const val Stop = "stop"
        const val Seek = "seek"
        const val Skip = "skip"
        const val SelectAudioTrack = "select_audio_track"
        const val SelectSubtitleTrack = "select_subtitle_track"
        const val SelectQuality = "select_quality"
        const val SetPlaybackSpeed = "set_playback_speed"
        const val SetVideoGravity = "set_video_gravity"
        const val SetHdrEnabled = "set_hdr_enabled"
        const val SetSubtitleDelay = "set_subtitle_delay"
        const val SetSubtitlePosition = "set_subtitle_position"
        const val SetVolume = "set_volume"
        const val SetMuted = "set_muted"
        const val NextEpisode = "next_episode"

        fun play(): SiloCastControlCommand = SiloCastControlCommand(name = Play)

        fun pause(): SiloCastControlCommand = SiloCastControlCommand(name = Pause)

        fun playPause(): SiloCastControlCommand = SiloCastControlCommand(name = PlayPause)

        fun stop(): SiloCastControlCommand = SiloCastControlCommand(name = Stop)

        fun seek(position: Double): SiloCastControlCommand =
            SiloCastControlCommand(name = Seek, position = position)

        fun skip(seconds: Double): SiloCastControlCommand =
            SiloCastControlCommand(name = Skip, seconds = seconds)

        fun selectAudioTrack(trackId: String?): SiloCastControlCommand =
            SiloCastControlCommand(name = SelectAudioTrack, trackId = trackId)

        fun selectSubtitleTrack(trackId: String?): SiloCastControlCommand =
            SiloCastControlCommand(name = SelectSubtitleTrack, trackId = trackId)

        fun selectQuality(qualityId: String): SiloCastControlCommand =
            SiloCastControlCommand(name = SelectQuality, qualityId = qualityId)

        fun setPlaybackSpeed(speed: Double): SiloCastControlCommand =
            SiloCastControlCommand(name = SetPlaybackSpeed, playbackSpeed = speed)

        fun setVideoGravity(videoGravity: String): SiloCastControlCommand =
            SiloCastControlCommand(name = SetVideoGravity, videoGravity = videoGravity)

        fun setHdrEnabled(enabled: Boolean): SiloCastControlCommand =
            SiloCastControlCommand(name = SetHdrEnabled, enabled = enabled)

        fun setSubtitleDelay(deltaMs: Int): SiloCastControlCommand =
            SiloCastControlCommand(name = SetSubtitleDelay, deltaMs = deltaMs)

        fun setSubtitlePosition(position: Double): SiloCastControlCommand =
            SiloCastControlCommand(name = SetSubtitlePosition, position = position)

        fun setVolume(volume: Double): SiloCastControlCommand =
            SiloCastControlCommand(name = SetVolume, volume = volume)

        fun setMuted(enabled: Boolean): SiloCastControlCommand =
            SiloCastControlCommand(name = SetMuted, enabled = enabled)

        fun nextEpisode(): SiloCastControlCommand = SiloCastControlCommand(name = NextEpisode)
    }
}

@Serializable
data class SiloCastPlaybackState(
    @SerialName("content_id") val contentId: String,
    @SerialName("session_id") val sessionId: String? = null,
    val title: String,
    val subtitle: String? = null,
    @SerialName("is_playing") val isPlaying: Boolean,
    @SerialName("is_loading") val isLoading: Boolean,
    @SerialName("is_buffering") val isBuffering: Boolean,
    @SerialName("current_time") val currentTime: Double,
    val duration: Double,
    @SerialName("audio_tracks") val audioTracks: List<SiloCastTrack>,
    @SerialName("subtitle_tracks") val subtitleTracks: List<SiloCastTrack>,
    @SerialName("selected_audio_track_id") val selectedAudioTrackId: String? = null,
    @SerialName("selected_subtitle_track_id") val selectedSubtitleTrackId: String? = null,
    @SerialName("quality_options") val qualityOptions: List<SiloCastQualityOption>,
    @SerialName("active_quality_id") val activeQualityId: String? = null,
    @SerialName("is_quality_switching") val isQualitySwitching: Boolean,
    @SerialName("playback_speed") val playbackSpeed: Double,
    @SerialName("video_gravity") val videoGravity: String,
    @SerialName("hdr_enabled") val hdrEnabled: Boolean,
    @SerialName("supports_video_gravity") val supportsVideoGravity: Boolean,
    @SerialName("supports_hdr_toggle") val supportsHDRToggle: Boolean,
    @SerialName("subtitle_sync_ms") val subtitleSyncMs: Int? = null,
    @SerialName("subtitle_position") val subtitlePosition: Double? = null,
    @SerialName("supports_subtitle_delay") val supportsSubtitleDelay: Boolean,
    @SerialName("supports_subtitle_position") val supportsSubtitlePosition: Boolean,
    val volume: Double,
    @SerialName("is_muted") val isMuted: Boolean,
    @SerialName("has_next_episode") val hasNextEpisode: Boolean,
    @SerialName("next_episode_title") val nextEpisodeTitle: String? = null,
    val error: String? = null,
)

@Serializable
data class SiloCastTrack(
    val id: String? = null,
    val label: String,
    val language: String? = null,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("is_forced") val isForced: Boolean = false,
)

@Serializable
data class SiloCastQualityOption(
    val id: String,
    val label: String,
    @SerialName("is_auto") val isAuto: Boolean = false,
    @SerialName("height") val height: Int? = null,
    @SerialName("bitrate") val bitrate: Long? = null,
)

@Serializable
data class SiloCastError(
    val code: String,
    val message: String,
    @SerialName("recoverable") val isRecoverable: Boolean = true,
)
