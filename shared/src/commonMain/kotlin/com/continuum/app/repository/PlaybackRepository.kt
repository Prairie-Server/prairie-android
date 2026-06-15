package com.continuum.app.repository

import com.continuum.app.model.playback.ChangeAudioResponse
import com.continuum.app.model.playback.ClientCodecCapabilities
import com.continuum.app.model.playback.PlaybackSessionResponse
import com.continuum.app.model.playback.ProgressRequest
import com.continuum.app.model.playback.StartPlaybackRequest
import com.continuum.app.model.playback.TranscodeStartRequest
import com.continuum.app.model.playback.TranscodeStartResponse
import com.continuum.app.network.ApiResult
import com.continuum.app.network.api.PlaybackApi

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
    ): ApiResult<PlaybackSessionResponse> =
        playbackApi.startPlayback(
            StartPlaybackRequest(
                fileId = fileId,
                profileId = profileId,
                startPosition = startPosition,
                audioTrackIndex = audioTrackIndex,
                codecsVideo = capabilities.codecsVideo,
                codecsAudio = capabilities.codecsAudio,
                containers = capabilities.containers,
                maxResolution = capabilities.maxResolution,
                hdr = capabilities.hdr,
                hdrDetails = capabilities.hdrDetails,
                audioPassthrough = capabilities.audioPassthrough,
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
}
