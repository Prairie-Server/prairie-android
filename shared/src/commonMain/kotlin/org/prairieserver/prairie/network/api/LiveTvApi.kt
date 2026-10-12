package org.prairieserver.prairie.network.api

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.HttpMethod
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import org.prairieserver.prairie.model.livetv.LiveTvChannelsResponse
import org.prairieserver.prairie.model.livetv.LiveTvGuideResponse
import org.prairieserver.prairie.model.livetv.LiveTvProgram
import org.prairieserver.prairie.model.livetv.LiveTvRecording
import org.prairieserver.prairie.model.livetv.LiveTvRecordingsResponse
import org.prairieserver.prairie.model.livetv.LiveTvScheduleRecordingRequest
import org.prairieserver.prairie.model.livetv.LiveTvSessionStartResponse
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.apiv2.ApiV2Gate
import org.prairieserver.prairie.network.apiv2.safeApiV2Call

/**
 * User-facing Live TV endpoints under `/api/v2/livetv`.
 *
 * Errors are problem+json; when Live TV is off every call is
 * `409 capability_not_configured`, which callers treat as "unavailable".
 * Live media still plays from the v1 `stream_url` the session returns.
 *
 * Kept behind an interface so repository / feature-store tests can fake the
 * transport, matching the Requests API shape.
 */
interface LiveTvApi {

    suspend fun channels(tunerId: String? = null): ApiResult<LiveTvChannelsResponse>

    suspend fun guide(
        channelIds: List<String> = emptyList(),
        start: String? = null,
        end: String? = null,
    ): ApiResult<LiveTvGuideResponse>

    suspend fun program(programId: String): ApiResult<LiveTvProgram>

    suspend fun startSession(channelId: String): ApiResult<LiveTvSessionStartResponse>

    suspend fun releaseSession(sessionId: String): ApiResult<Unit>

    suspend fun recordings(status: String? = null): ApiResult<LiveTvRecordingsResponse>

    suspend fun scheduleRecording(request: LiveTvScheduleRecordingRequest): ApiResult<LiveTvRecording>

    suspend fun cancelRecording(recordingId: String): ApiResult<LiveTvRecording>
}

class DefaultLiveTvApi(
    private val client: HttpClient,
    private val gate: ApiV2Gate = ApiV2Gate(),
) : LiveTvApi {

    private suspend inline fun <reified T> call(
        method: HttpMethod,
        path: String,
        noinline configure: HttpRequestBuilder.() -> Unit = {},
    ): ApiResult<T> = safeApiV2Call(gate) {
        client.request("/api/v2/livetv$path") {
            this.method = method
            configure()
        }
    }

    override suspend fun channels(tunerId: String?): ApiResult<LiveTvChannelsResponse> =
        call(HttpMethod.Get, "/channels") {
            if (!tunerId.isNullOrBlank()) parameter("tuner_id", tunerId)
        }

    override suspend fun guide(
        channelIds: List<String>,
        start: String?,
        end: String?,
    ): ApiResult<LiveTvGuideResponse> = call(HttpMethod.Get, "/guide") {
        channelIds.forEach { parameter("channel_id", it) }
        if (!start.isNullOrBlank()) parameter("start", start)
        if (!end.isNullOrBlank()) parameter("end", end)
    }

    override suspend fun program(programId: String): ApiResult<LiveTvProgram> =
        call(HttpMethod.Get, "/programs/${programId.encodeURLPathPart()}")

    override suspend fun startSession(channelId: String): ApiResult<LiveTvSessionStartResponse> =
        call(HttpMethod.Post, "/channels/${channelId.encodeURLPathPart()}/sessions")

    override suspend fun releaseSession(sessionId: String): ApiResult<Unit> =
        call(HttpMethod.Delete, "/sessions/${sessionId.encodeURLPathPart()}")

    override suspend fun recordings(status: String?): ApiResult<LiveTvRecordingsResponse> =
        call(HttpMethod.Get, "/recordings") {
            if (!status.isNullOrBlank()) parameter("status", status)
        }

    override suspend fun scheduleRecording(
        request: LiveTvScheduleRecordingRequest,
    ): ApiResult<LiveTvRecording> {
        val programId = request.programId?.trim().orEmpty()
        if (programId.isEmpty()) return ApiResult.Error(422, "validation_failed", "program_id is required")
        return call(HttpMethod.Post, "/recordings") {
            contentType(ContentType.Application.Json)
            // Guide-based schedule: server fills channel/window/title from program_id.
            setBody(LiveTvScheduleRecordingRequest(programId = programId))
        }
    }

    override suspend fun cancelRecording(recordingId: String): ApiResult<LiveTvRecording> =
        call(HttpMethod.Delete, "/recordings/${recordingId.encodeURLPathPart()}")
}
