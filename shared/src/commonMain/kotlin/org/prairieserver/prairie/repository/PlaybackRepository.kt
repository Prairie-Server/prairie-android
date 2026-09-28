package org.prairieserver.prairie.repository

import org.prairieserver.prairie.model.playback.PlaybackDecisionResponseV3
import org.prairieserver.prairie.model.playback.PlaybackReplanRequestV3
import org.prairieserver.prairie.model.playback.PlaybackRouteEventV3
import org.prairieserver.prairie.model.playback.PlaybackStartRequestV3
import org.prairieserver.prairie.model.playback.TranscodeStartRequest
import org.prairieserver.prairie.model.playback.TranscodeStartResponse
import kotlinx.coroutines.CancellationException
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.api.PlaybackApi
import org.prairieserver.prairie.playback.QualityLadderResponse
import org.prairieserver.prairie.network.AuthScopeSnapshot

/**
 * [transcodeApi] carries the Prairie-only v1 transcode/quality-ladder routes; it is
 * optional so upstream call sites that only drive [SequencedPlayback] keep working.
 */
class PlaybackRepository(
    private val sequenced: SequencedPlayback,
    private val transcodeApi: PlaybackApi? = null,
) {
    suspend fun controlOwner(sessionId: String): Pair<AuthScopeSnapshot, String?>? = sequenced.controlOwner(sessionId)
    val pendingPlayback = sequenced.pending
    fun isSequenced(sessionId: String): Boolean = sequenced.owns(sessionId)
    suspend fun pendingPlaybackCount(): Int = sequenced.pendingForCurrentViewer()
    suspend fun recoverPlayback(): ApiResult<Unit> = guarded { sequenced.recover() }
    /** [SequencedPlayback] answers null for a session it never journaled. */
    private fun unknownSession() = ApiResult.Error(0, "playback_unavailable", "This playback session is not owned by the app.")
    private suspend fun <T> guarded(block: suspend () -> ApiResult<T>): ApiResult<T> = try { block() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { ApiResult.Error(0, "playback_storage", "Playback recovery storage is unavailable.") }

    /** Starts a protocol-v3 playback attempt using the supplied client and route evidence. */
    suspend fun startPlaybackV3(request: PlaybackStartRequestV3, expectedMetadataOwner: AuthScopeSnapshot? = null): ApiResult<PlaybackDecisionResponseV3> =
        guarded { sequenced.start(request, expectedMetadataOwner) }

    /** Requests a replacement protocol-v3 plan for an active [sessionId]. */
    suspend fun replanPlaybackV3(
        sessionId: String,
        request: PlaybackReplanRequestV3,
    ): ApiResult<PlaybackDecisionResponseV3> = guarded { sequenced.replan(sessionId, request) }

    /** Reports attempt-scoped telemetry under the original v2 admission authority. */
    suspend fun reportRouteEventV3(request: PlaybackRouteEventV3): ApiResult<Unit> =
        guarded { sequenced.routeEvent(request) }

    /** Reports current playback position and paused state to the server. */
    suspend fun updateProgress(
        sessionId: String,
        position: Double,
        isPaused: Boolean,
    ): ApiResult<Unit> =
        guarded { sequenced.progress(sessionId, position, isPaused) ?: unknownSession() }

    /** Stops an active playback session. */
    suspend fun stopPlayback(sessionId: String): ApiResult<Unit> =
        guarded { sequenced.stop(sessionId) ?: unknownSession() }

    /** Explicitly requests a transcode session (e.g. for quality changes). Prairie-only. */
    suspend fun startTranscode(request: TranscodeStartRequest): ApiResult<TranscodeStartResponse> =
        transcodeApi?.startTranscode(request) ?: transcodeUnavailable()

    /** Server's transcode quality ladder for the in-player quality menu. Prairie-only. */
    suspend fun getQualityLadder(sourceHeight: Int? = null): ApiResult<QualityLadderResponse> =
        transcodeApi?.getQualityLadder(sourceHeight) ?: transcodeUnavailable()

    private fun transcodeUnavailable() =
        ApiResult.Error(0, "transcode_unavailable", "Transcode routes are not configured.")
}
