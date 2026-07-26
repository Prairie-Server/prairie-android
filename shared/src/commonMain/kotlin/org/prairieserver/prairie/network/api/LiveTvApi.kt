package org.prairieserver.prairie.network.api

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import org.prairieserver.prairie.model.livetv.LiveTvChannelsResponse
import org.prairieserver.prairie.model.livetv.LiveTvGuideResponse
import org.prairieserver.prairie.model.livetv.LiveTvProgram
import org.prairieserver.prairie.model.livetv.LiveTvRecording
import org.prairieserver.prairie.model.livetv.LiveTvRecordingsResponse
import org.prairieserver.prairie.model.livetv.LiveTvScheduleRecordingRequest
import org.prairieserver.prairie.model.livetv.LiveTvSession
import org.prairieserver.prairie.model.livetv.LiveTvSessionStartResponse
import org.prairieserver.prairie.network.ApiResult

/**
 * User-facing Live TV endpoints under `/api/v1/livetv`.
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

    suspend fun releaseSession(sessionId: String): ApiResult<LiveTvSession>

    suspend fun recordings(status: String? = null): ApiResult<LiveTvRecordingsResponse>

    suspend fun scheduleRecording(request: LiveTvScheduleRecordingRequest): ApiResult<LiveTvRecording>

    suspend fun cancelRecording(recordingId: String): ApiResult<LiveTvRecording>
}

class DefaultLiveTvApi(private val client: HttpClient) : LiveTvApi {

    override suspend fun channels(tunerId: String?): ApiResult<LiveTvChannelsResponse> = safeApiCall {
        client.get("/api/v1/livetv/channels") {
            if (!tunerId.isNullOrBlank()) {
                parameter("tuner_id", tunerId)
            }
        }
    }

    override suspend fun guide(
        channelIds: List<String>,
        start: String?,
        end: String?,
    ): ApiResult<LiveTvGuideResponse> = safeApiCall {
        client.get("/api/v1/livetv/guide") {
            if (channelIds.isNotEmpty()) {
                parameter("channels", channelIds.joinToString(","))
            }
            if (!start.isNullOrBlank()) {
                parameter("start", start)
            }
            if (!end.isNullOrBlank()) {
                parameter("end", end)
            }
        }
    }

    override suspend fun program(programId: String): ApiResult<LiveTvProgram> = safeApiCall {
        client.get("/api/v1/livetv/programs/${programId.encodeURLPathPart()}")
    }

    override suspend fun startSession(channelId: String): ApiResult<LiveTvSessionStartResponse> =
        safeApiCall {
            client.post("/api/v1/livetv/channels/${channelId.encodeURLPathPart()}/session")
        }

    override suspend fun releaseSession(sessionId: String): ApiResult<LiveTvSession> = safeApiCall {
        client.delete("/api/v1/livetv/sessions/${sessionId.encodeURLPathPart()}")
    }

    override suspend fun recordings(status: String?): ApiResult<LiveTvRecordingsResponse> = safeApiCall {
        client.get("/api/v1/livetv/recordings") {
            if (!status.isNullOrBlank()) {
                parameter("status", status)
            }
        }
    }

    override suspend fun scheduleRecording(
        request: LiveTvScheduleRecordingRequest,
    ): ApiResult<LiveTvRecording> = safeApiCall {
        val programId = request.programId?.trim().orEmpty()
        require(programId.isNotEmpty()) { "program_id is required" }
        client.post("/api/v1/livetv/recordings") {
            contentType(ContentType.Application.Json)
            // Guide-based schedule: server fills channel/window/title from program_id.
            setBody(LiveTvScheduleRecordingRequest(programId = programId))
        }
    }

    override suspend fun cancelRecording(recordingId: String): ApiResult<LiveTvRecording> =
        safeApiCall {
            client.delete("/api/v1/livetv/recordings/${recordingId.encodeURLPathPart()}")
        }
}
