package org.prairieserver.prairie.repository

import org.prairieserver.prairie.model.playback.ClientCodecCapabilities
import org.prairieserver.prairie.model.playback.ClientPlaybackContext
import org.prairieserver.prairie.model.playback.PlayMethod
import org.prairieserver.prairie.model.playback.PlaybackDecisionResponseV3
import org.prairieserver.prairie.model.playback.PlaybackReplanRequestV3
import org.prairieserver.prairie.model.playback.PlaybackRouteEventV3
import org.prairieserver.prairie.model.playback.PlaybackStartRequestV3
import org.prairieserver.prairie.model.playback.PlaybackSessionResponse
import org.prairieserver.prairie.model.playback.ProgressRequest
import org.prairieserver.prairie.model.playback.StartPlaybackRequest
import org.prairieserver.prairie.model.playback.TranscodeStartRequest
import org.prairieserver.prairie.model.playback.TranscodeStartResponse
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.api.PlaybackApi

class PlaybackRepository(
    private val playbackApi: PlaybackApi,
) {
    /** Starts a protocol-v3 playback attempt using the supplied client and route evidence. */
    suspend fun startPlaybackV3(request: PlaybackStartRequestV3): ApiResult<PlaybackDecisionResponseV3> =
        playbackApi.startPlaybackV3(request)

    /** Requests a replacement protocol-v3 plan for an active [sessionId]. */
    suspend fun replanPlaybackV3(
        sessionId: String,
        request: PlaybackReplanRequestV3,
    ): ApiResult<PlaybackDecisionResponseV3> = playbackApi.replanPlaybackV3(sessionId, request)

    /** Reports attempt-scoped protocol-v3 route telemetry. */
    suspend fun reportRouteEventV3(request: PlaybackRouteEventV3): ApiResult<Unit> =
        playbackApi.reportRouteEventV3(request)

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
        seekableStreamsOnly: Boolean = false,
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
                seekableStreamsOnly = seekableStreamsOnly,
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
