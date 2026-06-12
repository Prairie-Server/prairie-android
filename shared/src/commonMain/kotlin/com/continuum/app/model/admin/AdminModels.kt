// shared/src/commonMain/kotlin/com/continuum/app/model/admin/AdminModels.kt
package com.continuum.app.model.admin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ---------------------------------------------------------------------------
// Stats — GET /api/v1/admin/stats[?refresh=true]
// (silo-server internal/api/handlers/admin_stats.go: AdminStats / WatchProviderActivity)
// ---------------------------------------------------------------------------

@Serializable
data class AdminStats(
    @SerialName("total_items") val totalItems: Int = 0,
    @SerialName("total_files") val totalFiles: Int = 0,
    @SerialName("total_users") val totalUsers: Int = 0,
    @SerialName("total_movies") val totalMovies: Int = 0,
    @SerialName("total_movie_files") val totalMovieFiles: Int = 0,
    @SerialName("total_shows") val totalShows: Int = 0,
    @SerialName("total_show_files") val totalShowFiles: Int = 0,
    @SerialName("active_streams") val activeStreams: Int = 0,
    @SerialName("total_storage_bytes") val totalStorageBytes: Long = 0,
    @SerialName("watch_provider_activity") val watchProviderActivity: WatchProviderActivity = WatchProviderActivity(),
)

@Serializable
data class WatchProviderActivity(
    @SerialName("trakt_connected_profiles") val traktConnectedProfiles: Long = 0,
    @SerialName("trakt_enabled_profiles") val traktEnabledProfiles: Long = 0,
    @SerialName("trakt_export_enabled") val traktExportEnabled: Long = 0,
    @SerialName("trakt_scrobble_enabled") val traktScrobbleEnabled: Long = 0,
    @SerialName("last_sync_completed_at") val lastSyncCompletedAt: String? = null,
    @SerialName("sync_runs_24h") val syncRuns24h: Long = 0,
    @SerialName("sync_errors_24h") val syncErrors24h: Long = 0,
    @SerialName("imported_watched_24h") val importedWatched24h: Long = 0,
    @SerialName("imported_progress_24h") val importedProgress24h: Long = 0,
    @SerialName("exported_watched_24h") val exportedWatched24h: Long = 0,
    @SerialName("pending_exports") val pendingExports: Long = 0,
    @SerialName("failed_exports") val failedExports: Long = 0,
    @SerialName("open_scrobbles") val openScrobbles: Long = 0,
    @SerialName("scrobbles_24h") val scrobbles24h: Long = 0,
)

// ---------------------------------------------------------------------------
// Users — GET/POST /admin/users, GET/PUT/DELETE /admin/users/{id}
// (admin.go: adminUserResponse / createUserRequest / updateUserRequest)
// The list endpoint returns a bare JSON array of AdminUser.
// ---------------------------------------------------------------------------

@Serializable
data class AdminUser(
    val id: Int,
    val username: String,
    val email: String,
    val role: String,
    val permissions: List<String> = emptyList(),
    val enabled: Boolean = true,
    @SerialName("library_ids") val libraryIds: List<Int> = emptyList(),
    @SerialName("max_playback_quality") val maxPlaybackQuality: String = "",
    @SerialName("max_streams") val maxStreams: Int = 0,
    @SerialName("max_transcodes") val maxTranscodes: Int = 0,
    @SerialName("max_profiles") val maxProfiles: Int = 0,
    @SerialName("download_allowed") val downloadAllowed: Boolean = false,
    @SerialName("download_transcode_allowed") val downloadTranscodeAllowed: Boolean = false,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("last_active_at") val lastActiveAt: String? = null,
)

/**
 * POST /admin/users body. Required: username/email/password/role. The server
 * treats `permissions` and `library_ids` as present-when-sent; optional caps
 * (`max_streams` etc.) and `download_*` are pointer fields server-side, so we
 * leave them nullable and rely on explicitNulls=false to omit them.
 */
@Serializable
data class CreateUserRequest(
    val username: String,
    val email: String,
    val password: String,
    val role: String,
    val permissions: List<String> = emptyList(),
    @SerialName("create_default_profile") val createDefaultProfile: Boolean = false,
    @SerialName("default_profile_name") val defaultProfileName: String? = null,
    @SerialName("library_ids") val libraryIds: List<Int> = emptyList(),
    @SerialName("max_playback_quality") val maxPlaybackQuality: String = "",
    @SerialName("max_streams") val maxStreams: Int? = null,
    @SerialName("max_transcodes") val maxTranscodes: Int? = null,
    @SerialName("max_profiles") val maxProfiles: Int? = null,
    @SerialName("download_allowed") val downloadAllowed: Boolean? = null,
    @SerialName("download_transcode_allowed") val downloadTranscodeAllowed: Boolean? = null,
)

/**
 * PUT /admin/users/{id} — fully partial; every field is optional. With
 * explicitNulls=false, unset (null) fields are omitted from the body, matching
 * the server's "omitted key keeps current value" pointer semantics.
 */
@Serializable
data class UpdateUserRequest(
    val username: String? = null,
    val email: String? = null,
    val password: String? = null,
    val role: String? = null,
    val permissions: List<String>? = null,
    val enabled: Boolean? = null,
    @SerialName("library_ids") val libraryIds: List<Int>? = null,
    @SerialName("max_playback_quality") val maxPlaybackQuality: String? = null,
    @SerialName("max_streams") val maxStreams: Int? = null,
    @SerialName("max_transcodes") val maxTranscodes: Int? = null,
    @SerialName("max_profiles") val maxProfiles: Int? = null,
    @SerialName("download_allowed") val downloadAllowed: Boolean? = null,
    @SerialName("download_transcode_allowed") val downloadTranscodeAllowed: Boolean? = null,
)

// ---------------------------------------------------------------------------
// Sessions — GET /admin/sessions returns a bare JSON array of AdminSession.
// (playback_sessions.go: playbackSessionRow — note `transcode_node_url` is
// json:"-" on the server and intentionally NOT modeled here.)
// ---------------------------------------------------------------------------

@Serializable
data class AdminSession(
    @SerialName("session_id") val sessionId: String,
    @SerialName("user_id") val userId: Int,
    val username: String,
    @SerialName("profile_id") val profileId: String,
    @SerialName("profile_name") val profileName: String = "",
    @SerialName("media_file_id") val mediaFileId: Int,
    @SerialName("requested_media_file_id") val requestedMediaFileId: Int,
    @SerialName("content_id") val contentId: String = "",
    @SerialName("media_title") val mediaTitle: String,
    @SerialName("media_type") val mediaType: String,
    @SerialName("series_name") val seriesName: String = "",
    @SerialName("episode_name") val episodeName: String = "",
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    @SerialName("poster_url") val posterUrl: String = "",
    @SerialName("play_method") val playMethod: String,
    @SerialName("reporting_node") val reportingNode: String,
    @SerialName("node_display_name") val nodeDisplayName: String = "",
    @SerialName("file_duration") val fileDuration: Int? = null,
    @SerialName("started_at") val startedAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("position_seconds") val positionSeconds: Double = 0.0,
    @SerialName("is_paused") val isPaused: Boolean = false,
    @SerialName("has_playback_control") val hasPlaybackControl: Boolean = false,
    @SerialName("client_ip") val clientIp: String = "",
    @SerialName("audio_track_index") val audioTrackIndex: Int = 0,
    @SerialName("transcode_audio") val transcodeAudio: Boolean = false,
    @SerialName("stream_bitrate_kbps") val streamBitrateKbps: Int? = null,
    @SerialName("target_resolution") val targetResolution: String = "",
    @SerialName("target_video_codec") val targetVideoCodec: String = "",
    @SerialName("target_audio_codec") val targetAudioCodec: String = "",
    @SerialName("target_bitrate_kbps") val targetBitrateKbps: Int? = null,
    @SerialName("transcode_hw_accel") val transcodeHwAccel: String = "",
    @SerialName("source_container") val sourceContainer: String = "",
    @SerialName("source_bitrate_kbps") val sourceBitrateKbps: Int? = null,
    @SerialName("source_video_codec") val sourceVideoCodec: String = "",
    @SerialName("source_video_resolution") val sourceVideoResolution: String = "",
    @SerialName("source_audio_codec") val sourceAudioCodec: String = "",
    @SerialName("source_audio_channels") val sourceAudioChannels: Int? = null,
    @SerialName("source_audio_language") val sourceAudioLanguage: String = "",
    @SerialName("source_audio_title") val sourceAudioTitle: String = "",
    @SerialName("source_audio_layout") val sourceAudioLayout: String = "",
    @SerialName("requested_video_codec") val requestedVideoCodec: String = "",
    @SerialName("requested_video_resolution") val requestedVideoResolution: String = "",
    @SerialName("video_decision") val videoDecision: String = "",
    @SerialName("audio_decision") val audioDecision: String = "",
)

/**
 * Session control body for POST /admin/sessions/{id}/{pause|resume|stop|terminate|message}.
 * (admin_playback_control.go: playbackControlRequest — `message`/`title`
 * required only for the message action.) All fields optional here; callers
 * populate only what the chosen action needs.
 */
@Serializable
data class SessionControlRequest(
    val reason: String? = null,
    val title: String? = null,
    val message: String? = null,
    @SerialName("deadline_ms") val deadlineMs: Int? = null,
)

/** Response from a session control action (admin_playback_control.go: playbackControlResponse). */
@Serializable
data class SessionControlResponse(
    @SerialName("command_id") val commandId: String,
    val status: String,
)

/** Known session control actions (URL path segment). */
enum class SessionControlAction(val wire: String) {
    Pause("pause"),
    Resume("resume"),
    Stop("stop"),
    Terminate("terminate"),
    Message("message"),
}

// ---------------------------------------------------------------------------
// Logs — GET /admin/logs/app and /admin/logs/audit
// (opslog.EntryRow / activitylog.AuditEntry; both pages: {entries, next_cursor?})
// ---------------------------------------------------------------------------

@Serializable
data class AdminLogEntry(
    val id: Long,
    val timestamp: String,
    val level: String,
    val component: String,
    val message: String,
    @SerialName("request_id") val requestId: String? = null,
    @SerialName("user_id") val userId: Int? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("playback_session_id") val playbackSessionId: String? = null,
    @SerialName("client_ip") val clientIp: String? = null,
    @SerialName("node_id") val nodeId: String? = null,
    val attrs: Map<String, JsonElement>? = null,
)

@Serializable
data class AdminAuditEntry(
    val id: Long,
    val timestamp: String,
    @SerialName("client_ip") val clientIp: String,
    @SerialName("user_id") val userId: Int? = null,
    @SerialName("impersonator_user_id") val impersonatorUserId: Int? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("playback_session_id") val playbackSessionId: String? = null,
    @SerialName("request_id") val requestId: String? = null,
    @SerialName("node_id") val nodeId: String? = null,
    val method: String,
    val path: String,
    @SerialName("path_pattern") val pathPattern: String? = null,
    @SerialName("status_code") val statusCode: Int,
    @SerialName("user_agent") val userAgent: String? = null,
    @SerialName("duration_ms") val durationMs: Int = 0,
)

/** App log page (opslog.ListResult). */
@Serializable
data class AdminLogPage(
    val entries: List<AdminLogEntry> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

/** Audit log page (activitylog.ListResult). */
@Serializable
data class AdminAuditPage(
    val entries: List<AdminAuditEntry> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

// ---------------------------------------------------------------------------
// Scans — POST /libraries/scan and /libraries/scan/cancel
// (libraries.go: scanRequest / scanResponse / scanCancelRequest / scanCancelResponse)
// NOTE: these live under /libraries, NOT /admin — see AdminApi for placement.
// ---------------------------------------------------------------------------

@Serializable
data class ScanRequest(
    @SerialName("library_id") val libraryId: Int? = null,
    val path: String? = null,
)

@Serializable
data class ScanResponse(
    val status: String,
    val mode: String,
    @SerialName("library_id") val libraryId: Int,
)

@Serializable
data class ScanCancelRequest(
    @SerialName("library_id") val libraryId: Int,
)

@Serializable
data class ScanCancelResponse(
    val cancelled: Int,
    @SerialName("library_id") val libraryId: Int,
)
