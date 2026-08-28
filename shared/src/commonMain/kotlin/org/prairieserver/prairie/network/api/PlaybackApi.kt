package org.prairieserver.prairie.network.api

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.parameter
import io.ktor.http.*
import org.prairieserver.prairie.model.playback.PlaybackDecisionResponseV3
import org.prairieserver.prairie.model.playback.PlaybackReplanRequestV3
import org.prairieserver.prairie.model.playback.PlaybackRouteEventV3
import org.prairieserver.prairie.model.playback.PlaybackStartRequestV3
import org.prairieserver.prairie.model.playback.ProgressRequest
import org.prairieserver.prairie.model.playback.TranscodeStartRequest
import org.prairieserver.prairie.model.playback.TranscodeStartResponse
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.playback.QualityLadderResponse

class PlaybackApi(private val client: HttpClient) {

    suspend fun startPlaybackV3(request: PlaybackStartRequestV3): ApiResult<PlaybackDecisionResponseV3> = safeApiCall {
        client.post("/api/v1/playback/start") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun replanPlaybackV3(
        sessionId: String,
        request: PlaybackReplanRequestV3,
    ): ApiResult<PlaybackDecisionResponseV3> = safeApiCall {
        client.post("/api/v1/playback/$sessionId/replan") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun reportRouteEventV3(request: PlaybackRouteEventV3): ApiResult<Unit> = safeApiCall {
        client.post("/api/v1/playback/route-events") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun updateProgress(
        sessionId: String,
        request: ProgressRequest
    ): ApiResult<Unit> = safeApiCall {
        client.post("/api/v1/playback/$sessionId/progress") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun stopPlayback(sessionId: String): ApiResult<Unit> = safeApiCall {
        client.delete("/api/v1/playback/$sessionId")
    }

    suspend fun startTranscode(request: TranscodeStartRequest): ApiResult<TranscodeStartResponse> = safeApiCall {
        client.post("/api/v1/playback/transcode/start") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    /** Server's transcode quality ladder (`GET /api/v1/playback/quality-ladder`). */
    suspend fun getQualityLadder(sourceHeight: Int? = null): ApiResult<QualityLadderResponse> = safeApiCall {
        client.get("/api/v1/playback/quality-ladder") {
            if (sourceHeight != null && sourceHeight > 0) {
                parameter("source_height", sourceHeight)
            }
        }
    }
}
