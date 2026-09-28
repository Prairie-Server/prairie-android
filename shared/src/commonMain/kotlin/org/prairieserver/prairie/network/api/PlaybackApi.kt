package org.prairieserver.prairie.network.api

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.parameter
import io.ktor.http.*
import org.prairieserver.prairie.model.playback.TranscodeStartRequest
import org.prairieserver.prairie.model.playback.TranscodeStartResponse
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.playback.QualityLadderResponse

/**
 * Prairie-only v1 playback routes (explicit transcode start and the quality ladder).
 *
 * Upstream dropped the rest of the v1 playback transport in favour of
 * SequencedPlayback + PlaybackV2Api; only the Prairie additions remain here.
 */
class PlaybackApi(private val client: HttpClient) {

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
