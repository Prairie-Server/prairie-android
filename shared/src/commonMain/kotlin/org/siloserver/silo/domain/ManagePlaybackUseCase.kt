package org.siloserver.silo.domain

import org.siloserver.silo.model.catalog.WatchDetail
import org.siloserver.silo.model.playback.ClientCodecCapabilities
import org.siloserver.silo.model.playback.PlaybackSessionResponse
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.repository.CatalogRepository
import org.siloserver.silo.repository.PlaybackRepository

/**
 * Orchestrates the full playback lifecycle: session creation, progress reporting,
 * and session teardown.
 *
 * Combines [PlaybackRepository] for session management with [CatalogRepository]
 * for fetching watch detail (versions, intro/credits markers, user progress).
 */
class ManagePlaybackUseCase(
    private val playbackRepo: PlaybackRepository,
    private val catalogRepo: CatalogRepository,
) {
    /**
     * Starts a playback session for a content item.
     *
     * @param contentId The content ID (used for logging/context; the server uses fileId).
     * @param fileId The specific file version to play.
     * @param profileId The active user profile.
     * @param capabilities Client codec support for direct-play/transcode decisions.
     * @param qualityPreference Optional quality preference (e.g. "original", "1080p").
     * @return The playback session info including stream URL and decision (direct/transcode).
     */
    suspend fun startPlayback(
        contentId: String,
        fileId: Int,
        profileId: String,
        capabilities: ClientCodecCapabilities,
        qualityPreference: String? = null,
        startPosition: Double? = null,
    ): ApiResult<PlaybackSessionResponse> =
        playbackRepo.startPlayback(
            fileId = fileId,
            profileId = profileId,
            qualityPreference = qualityPreference,
            startPosition = startPosition,
            capabilities = capabilities,
        )

    /**
     * Reports the current playback position and paused state.
     * Should be called periodically during playback (e.g. every 10 seconds).
     */
    suspend fun reportProgress(
        sessionId: String,
        position: Double,
        isPaused: Boolean,
    ): ApiResult<Unit> =
        playbackRepo.updateProgress(sessionId, position, isPaused)

    /**
     * Stops the playback session.
     * Should be called when the user exits the player or playback completes.
     */
    suspend fun stopPlayback(sessionId: String): ApiResult<Unit> =
        playbackRepo.stopPlayback(sessionId)

    /**
     * Fetches playback-oriented detail for a content item.
     * Includes available versions, subtitle info, intro/credits markers,
     * and the user's last playback position.
     */
    suspend fun getWatchDetail(contentId: String): ApiResult<WatchDetail> =
        catalogRepo.getWatchDetail(contentId)
}
