package com.continuum.app.tv.ui.screens.player

import android.util.Log
import com.continuum.app.common.player.PlaybackCapabilityDetector
import com.continuum.app.common.player.PlaybackSessionLifecycle
import com.continuum.app.common.player.PlaybackSessionManager
import com.continuum.app.common.player.StartParams
import com.continuum.app.common.player.video.VideoPlaybackStartRequest
import com.continuum.app.common.player.video.VideoPlaybackStartResult
import com.continuum.app.common.player.video.VideoPlaybackStarter
import com.continuum.app.common.settings.PlayerSettingsStore
import com.continuum.app.model.catalog.FileVersion
import com.continuum.app.model.playback.DefaultResumeRewindSeconds
import com.continuum.app.model.playback.PlayMethod
import com.continuum.app.model.playback.PlaybackSessionResponse
import com.continuum.app.model.playback.applyResumeRewind
import com.continuum.app.model.playback.resolvePlaybackStartPosition
import com.continuum.app.model.playback.resolvePlaybackStartRequestPosition
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.CatalogRepository
import com.continuum.app.repository.ProfileRepository
import kotlinx.coroutines.flow.first

class TvVideoPlaybackStarter(
    private val catalogRepository: CatalogRepository,
    private val playbackSessionManager: PlaybackSessionManager,
    private val profileRepository: ProfileRepository,
    private val capabilityDetector: PlaybackCapabilityDetector,
    private val playerSettingsStore: PlayerSettingsStore,
    private val sessionLifecycle: PlaybackSessionLifecycle,
) : VideoPlaybackStarter {

    override suspend fun start(request: VideoPlaybackStartRequest): VideoPlaybackStartResult {
        return try {
            val watchDetail = when (val r = catalogRepository.getWatchDetail(request.contentId)) {
                is ApiResult.Success -> r.data
                is ApiResult.Error -> return failure(request.contentId, "Failed to load content: ${r.message}")
                is ApiResult.NetworkError -> return failure(
                    request.contentId,
                    "Network error: ${r.exception.message}",
                    r.exception,
                )
            }
            if (watchDetail.versions.isEmpty()) {
                return failure(request.contentId, "No playable versions available")
            }

            val serverUrl = playbackSessionManager.getServerUrl()
            val preferredQuality = playerSettingsStore.preferredQualityFlow.first()
            val version = request.preferredFileId
                ?.let { id -> watchDetail.versions.firstOrNull { it.fileId == id } }
                ?: pickPreferredVersion(
                    watchDetail.versions,
                    watchDetail.userData?.lastFileId,
                    preferredQuality,
                )
            val activeProfile = profileRepository.getActiveProfile()
            val profileId = activeProfile?.id ?: profileRepository.getActiveProfileId()
                ?: return failure(request.contentId, "No active profile selected")
            val preferredAudioLanguage = playerSettingsStore.audioLanguageFlow
                .first().ifBlank { null }
            val accessToken = playbackSessionManager.getAccessToken()
                ?: return failure(request.contentId, "Not authenticated")

            val capabilities = capabilityDetector.detect()
            // Skip-back-on-resume — see MobileVideoPlaybackStarter for the rationale.
            // Suppressed for Start Over / retry (request flag) and Watch Together
            // (roomId); the one rewound value drives both the server seek and the
            // player start so a transcode cut and the player position never disagree.
            val suppressRewind = request.suppressResumeRewind || request.roomId != null
            fun rewound(position: Double?): Double? = position?.let {
                applyResumeRewind(
                    resolvedStartPosition = it,
                    isExplicitOverride = suppressRewind,
                    rewindSeconds = DefaultResumeRewindSeconds,
                )
            }
            val startRequestPosition = rewound(
                resolvePlaybackStartRequestPosition(
                    overridePosition = request.resumePositionOverride,
                    detailPosition = watchDetail.userData?.positionSeconds,
                ),
            )

            val session = when (
                val r = playbackSessionManager.startSession(
                    fileId = version.fileId,
                    profileId = profileId,
                    capabilities = capabilities,
                    // Pre-selected audio track from the detail screen (null = let
                    // the server choose its default); mirrors the phone starter.
                    audioTrackIndex = request.audioTrackIndex,
                    qualityPreference = preferredQuality,
                    startPosition = startRequestPosition,
                )
            ) {
                is ApiResult.Success -> r.data
                is ApiResult.Error -> return failure(request.contentId, "Failed to start playback: ${r.message}")
                is ApiResult.NetworkError -> return failure(
                    request.contentId,
                    "Network error: ${r.exception.message}",
                    r.exception,
                )
            }

            val resolved: PlaybackSessionResponse = when (session.playMethod) {
                PlayMethod.DIRECT -> session
                PlayMethod.REMUX, PlayMethod.TRANSCODE -> {
                    val mode = if (session.playMethod == PlayMethod.REMUX) {
                        PlaybackSessionManager.TranscodeMode.REMUX
                    } else {
                        PlaybackSessionManager.TranscodeMode.FULL
                    }
                    when (val r = playbackSessionManager.startTranscodeFallback(
                        session = session,
                        seekSeconds = startRequestPosition ?: 0.0,
                        resolution = version.resolution.orEmpty(),
                        mode = mode,
                    )) {
                        is ApiResult.Success -> r.data
                        is ApiResult.Error -> return failure(
                            request.contentId,
                            "Failed to start transcode: ${r.message}",
                        )
                        is ApiResult.NetworkError -> return failure(
                            request.contentId,
                            "Network error starting transcode: ${r.exception.message}",
                            r.exception,
                        )
                    }
                }
            }

            val startPos = resolvePlaybackStartPosition(
                // For a resume, follow the server's actual cut (resolved.position)
                // so the player can't seek before a transcode's start; only honor
                // an exact override when rewind is suppressed (Start Over / WT /
                // retry). We already sent the rewound seek above, so a transcode's
                // resolved.position reflects the rewind; direct-play rewind then
                // depends on the server echoing it (device-verify).
                overridePosition = if (suppressRewind) request.resumePositionOverride else null,
                sessionPosition = resolved.position,
                detailPosition = watchDetail.userData?.positionSeconds,
            )

            sessionLifecycle.adoptActiveSession(
                params = StartParams(
                    contentId = request.contentId,
                    fileId = version.fileId,
                    capabilities = capabilities,
                    audioTrackIndex = resolved.audioTrackIndex,
                    qualityPreference = preferredQuality,
                    startPosition = startPos,
                ),
                session = resolved,
            )

            VideoPlaybackStartResult.Ready(
                contentId = request.contentId,
                fileId = version.fileId,
                sessionId = resolved.sessionId,
                streamUrl = resolved.streamUrl,
                playMethod = resolved.playMethod,
                container = version.container,
                title = watchDetail.title,
                subtitle = null,
                artworkUrl = watchDetail.posterUrl?.takeIf { it.isNotBlank() }
                    ?: watchDetail.backdropUrl?.takeIf { it.isNotBlank() },
                startPositionSeconds = startPos,
                serverUrl = serverUrl,
                accessToken = accessToken,
                mediaFileId = resolved.mediaFileId.takeIf { id -> id > 0 }
                    ?: session.mediaFileId.takeIf { id -> id > 0 },
                durationSeconds = resolved.durationSeconds ?: version.duration,
                subtitleUrls = resolved.subtitleUrls ?: emptyList(),
                preferredAudioLanguage = preferredAudioLanguage ?: activeProfile?.language,
                preferredTextLanguage = activeProfile?.subtitleLanguage,
                intro = watchDetail.intro,
                credits = watchDetail.credits,
                chapters = version.chapters.orEmpty(),
                seriesId = watchDetail.seriesId,
                seasonNumber = watchDetail.seasonNumber,
                episodeNumber = watchDetail.episodeNumber,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading content", e)
            failure(request.contentId, "Unexpected error: ${e.message}", e)
        }
    }

    private fun failure(
        contentId: String,
        message: String,
        cause: Throwable? = null,
    ): VideoPlaybackStartResult.Error =
        VideoPlaybackStartResult.Error(
            contentId = contentId,
            message = message,
            cause = cause,
        )

    private fun pickPreferredVersion(
        versions: List<FileVersion>,
        lastFileId: Int?,
        preferredQuality: String?,
    ): FileVersion {
        if (lastFileId != null) {
            versions.firstOrNull { it.fileId == lastFileId }?.let { return it }
        }
        val target = preferredQuality?.lowercase().orEmpty()
        if (target.isBlank() || target == "auto") {
            return versions.first()
        }
        val preferredRank = resolutionRank(target)
        return versions
            .sortedByDescending { resolutionRank(it.resolution) }
            .firstOrNull { version ->
                target == "original" || resolutionRank(version.resolution) <= preferredRank
            }
            ?: versions.first()
    }

    private fun resolutionRank(value: String?): Int {
        val normalized = value?.lowercase().orEmpty()
        return when {
            normalized.contains("2160") || normalized.contains("4k") -> 2160
            normalized.contains("1080") -> 1080
            normalized.contains("720") -> 720
            normalized.contains("480") -> 480
            else -> 0
        }
    }

    private companion object {
        const val TAG = "TvVideoPlaybackStarter"
    }
}
