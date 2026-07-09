package org.siloserver.silo.repository

import org.siloserver.silo.model.playback.ChangeAudioResponse
import org.siloserver.silo.model.playback.ClientCodecCapabilities
import org.siloserver.silo.model.playback.ClientPlaybackContext
import org.siloserver.silo.model.playback.PlayMethod
import org.siloserver.silo.model.playback.PlaybackPlanResponse
import org.siloserver.silo.model.playback.PlaybackRouteEventRequest
import org.siloserver.silo.model.playback.PlaybackSessionResponse
import org.siloserver.silo.model.playback.ProgressRequest
import org.siloserver.silo.model.playback.StartPlaybackRequest
import org.siloserver.silo.model.playback.TranscodeStartRequest
import org.siloserver.silo.model.playback.TranscodeStartResponse
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.api.PlaybackApi

class PlaybackRepository(
    private val playbackApi: PlaybackApi,
) {
    /**
     * Starts a new playback session.
     * The server decides whether to direct-play or transcode based on client capabilities.
     */
    suspend fun startPlayback(
        fileId: Int,
        profileId: String,
        qualityPreference: String? = null,
        audioTrackIndex: Int? = null,
        subtitleTrackIndex: Int? = null,
        startPosition: Double? = null,
        capabilities: ClientCodecCapabilities,
        clientPlaybackContext: ClientPlaybackContext? = null,
        preserveDirectAudioSelection: Boolean = false,
        playMethod: PlayMethod? = null,
        disableProgressPersistence: Boolean = false,
    ): ApiResult<PlaybackSessionResponse> =
        playbackApi.startPlayback(
            StartPlaybackRequest(
                fileId = fileId,
                profileId = profileId,
                playMethod = playMethod?.wireValue(),
                startPosition = startPosition,
                audioTrackIndex = audioTrackIndex,
                subtitleTrackIndex = subtitleTrackIndex,
                qualityPreference = qualityPreference,
                preserveDirectAudioSelection = preserveDirectAudioSelection,
                codecsVideo = capabilities.codecsVideo,
                codecsAudio = capabilities.codecsAudio,
                containers = capabilities.containers,
                maxResolution = capabilities.maxResolution,
                hdr = capabilities.hdr,
                hdrDetails = capabilities.hdrDetails,
                audioPassthrough = capabilities.audioPassthrough,
                clientPlaybackContext = clientPlaybackContext,
                disableProgressPersistence = disableProgressPersistence,
            ),
        )

    /** Requests a non-counting V2 route plan without starting a playback session. */
    suspend fun decidePlayback(
        fileId: Int,
        profileId: String,
        qualityPreference: String? = null,
        audioTrackIndex: Int? = null,
        subtitleTrackIndex: Int? = null,
        startPosition: Double? = null,
        capabilities: ClientCodecCapabilities,
        clientPlaybackContext: ClientPlaybackContext? = null,
        preserveDirectAudioSelection: Boolean = false,
        playMethod: PlayMethod? = null,
    ): ApiResult<PlaybackPlanResponse> =
        playbackApi.decidePlayback(
            StartPlaybackRequest(
                fileId = fileId,
                profileId = profileId,
                playMethod = playMethod?.wireValue(),
                startPosition = startPosition,
                audioTrackIndex = audioTrackIndex,
                subtitleTrackIndex = subtitleTrackIndex,
                qualityPreference = qualityPreference,
                preserveDirectAudioSelection = preserveDirectAudioSelection,
                codecsVideo = capabilities.codecsVideo,
                codecsAudio = capabilities.codecsAudio,
                containers = capabilities.containers,
                maxResolution = capabilities.maxResolution,
                hdr = capabilities.hdr,
                hdrDetails = capabilities.hdrDetails,
                audioPassthrough = capabilities.audioPassthrough,
                clientPlaybackContext = clientPlaybackContext,
            ),
        )

    /** Reports current playback position and paused state to the server. */
    suspend fun updateProgress(
        sessionId: String,
        position: Double,
        isPaused: Boolean,
    ): ApiResult<Unit> =
        playbackApi.updateProgress(
            sessionId = sessionId,
            request = ProgressRequest(position = position, isPaused = isPaused),
        )

    /** Stops an active playback session. */
    suspend fun stopPlayback(sessionId: String): ApiResult<Unit> =
        playbackApi.stopPlayback(sessionId)

    /** Explicitly requests a transcode session (e.g. for quality changes). */
    suspend fun startTranscode(request: TranscodeStartRequest): ApiResult<TranscodeStartResponse> =
        playbackApi.startTranscode(request)

    /** Switches the audio track mid-stream (may trigger a new transcode). */
    suspend fun changeAudio(
        sessionId: String,
        audioTrackIndex: Int,
        position: Double? = null,
    ): ApiResult<ChangeAudioResponse> =
        playbackApi.changeAudio(sessionId, audioTrackIndex, position)

    suspend fun reportRouteEvent(
        sessionId: String,
        request: PlaybackRouteEventRequest,
    ): ApiResult<Unit> =
        playbackApi.reportRouteEvent(sessionId, request)
}

// Mirror the enum's @SerialName wire values explicitly (not name.lowercase()),
// so adding a constant whose serial name differs from its lowercased name is a
// compile error here rather than a silently wrong wire value. Exhaustive on
// purpose — no `else`.
private fun PlayMethod.wireValue(): String = when (this) {
    PlayMethod.DIRECT -> "direct"
    PlayMethod.REMUX -> "remux"
    PlayMethod.TRANSCODE -> "transcode"
}
