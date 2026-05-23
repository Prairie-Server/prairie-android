package com.continuum.app.model.admin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AdminUser(
    val id: Int,
    val username: String,
    val email: String,
    val role: String,
    val enabled: Boolean = true,
    @SerialName("library_ids") val libraryIds: List<Int>? = null,
    @SerialName("max_playback_quality") val maxPlaybackQuality: String? = null,
    @SerialName("max_streams") val maxStreams: Int = 0,
    @SerialName("max_transcodes") val maxTranscodes: Int = 0,
    @SerialName("download_allowed") val downloadAllowed: Boolean = false,
    @SerialName("download_transcode_allowed") val downloadTranscodeAllowed: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class CreateUserRequest(
    val username: String,
    val email: String,
    val password: String,
    val role: String,
    @SerialName("library_ids") val libraryIds: List<Int>? = null,
    @SerialName("max_playback_quality") val maxPlaybackQuality: String? = null,
    @SerialName("max_streams") val maxStreams: Int? = null,
    @SerialName("max_transcodes") val maxTranscodes: Int? = null,
    @SerialName("download_allowed") val downloadAllowed: Boolean? = null,
    @SerialName("download_transcode_allowed") val downloadTranscodeAllowed: Boolean? = null
)

@Serializable
data class UpdateUserRequest(
    val username: String? = null,
    val email: String? = null,
    val password: String? = null,
    val role: String? = null,
    val enabled: Boolean? = null,
    @SerialName("library_ids") val libraryIds: List<Int>? = null,
    @SerialName("max_playback_quality") val maxPlaybackQuality: String? = null,
    @SerialName("max_streams") val maxStreams: Int? = null,
    @SerialName("max_transcodes") val maxTranscodes: Int? = null,
    @SerialName("download_allowed") val downloadAllowed: Boolean? = null,
    @SerialName("download_transcode_allowed") val downloadTranscodeAllowed: Boolean? = null
)

@Serializable
data class AdminStats(
    @SerialName("total_items") val totalItems: Int = 0,
    @SerialName("total_files") val totalFiles: Int = 0,
    @SerialName("total_users") val totalUsers: Int = 0,
    @SerialName("total_movies") val totalMovies: Int = 0,
    @SerialName("total_shows") val totalShows: Int = 0,
    @SerialName("active_streams") val activeStreams: Int = 0,
    @SerialName("total_storage_bytes") val totalStorageBytes: Long = 0
)

@Serializable
data class AdminSession(
    @SerialName("session_id") val sessionId: String,
    @SerialName("user_id") val userId: Int,
    val username: String? = null,
    @SerialName("profile_id") val profileId: String? = null,
    @SerialName("profile_name") val profileName: String? = null,
    @SerialName("media_file_id") val mediaFileId: Int,
    @SerialName("content_id") val contentId: String? = null,
    @SerialName("media_title") val mediaTitle: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("series_name") val seriesName: String? = null,
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("play_method") val playMethod: String? = null,
    @SerialName("reporting_node") val reportingNode: String? = null,
    @SerialName("node_display_name") val nodeDisplayName: String? = null,
    @SerialName("file_duration") val fileDuration: Double? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_paused") val isPaused: Boolean = false,
    @SerialName("client_ip") val clientIp: String? = null,
    @SerialName("audio_track_index") val audioTrackIndex: Int = 0,
    @SerialName("transcode_audio") val transcodeAudio: Boolean = false,
    @SerialName("stream_bitrate_kbps") val streamBitrateKbps: Int? = null,
    @SerialName("target_resolution") val targetResolution: String? = null,
    @SerialName("target_video_codec") val targetVideoCodec: String? = null,
    @SerialName("target_audio_codec") val targetAudioCodec: String? = null,
    @SerialName("target_bitrate_kbps") val targetBitrateKbps: Int? = null,
    @SerialName("source_container") val sourceContainer: String? = null,
    @SerialName("source_bitrate_kbps") val sourceBitrateKbps: Int? = null,
    @SerialName("source_video_codec") val sourceVideoCodec: String? = null,
    @SerialName("source_video_resolution") val sourceVideoResolution: String? = null,
    @SerialName("source_audio_codec") val sourceAudioCodec: String? = null,
    @SerialName("source_audio_channels") val sourceAudioChannels: Int? = null,
    @SerialName("source_audio_language") val sourceAudioLanguage: String? = null,
    @SerialName("source_audio_title") val sourceAudioTitle: String? = null,
    @SerialName("source_audio_layout") val sourceAudioLayout: String? = null,
    @SerialName("video_decision") val videoDecision: String? = null,
    @SerialName("audio_decision") val audioDecision: String? = null
)

@Serializable
data class Library(
    val id: Int,
    val paths: List<String> = emptyList(),
    val type: String,
    val name: String,
    val enabled: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("last_scanned_at") val lastScannedAt: String? = null
)

@Serializable
data class AdminSettingValue(
    val key: String,
    val value: JsonElement? = null
)

@Serializable
data class UpdateSettingRequest(
    val value: JsonElement
)

@Serializable
data class AdminUsersResponse(
    val users: List<AdminUser>
)

@Serializable
data class AdminLibrariesResponse(
    val libraries: List<Library>
)

@Serializable
data class AdminSettingsResponse(
    val settings: List<AdminSettingValue>
)

@Serializable
data class AdminSessionsResponse(
    val sessions: List<AdminSession>
)

@Serializable
data class ScanRequest(
    @SerialName("library_id") val libraryId: Int? = null
)
