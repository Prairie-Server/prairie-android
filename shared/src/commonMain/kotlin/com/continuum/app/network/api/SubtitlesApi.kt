package com.continuum.app.network.api

import com.continuum.app.model.subtitles.DownloadedSubtitlesResponse
import com.continuum.app.model.subtitles.SubtitleAiJobResponse
import com.continuum.app.model.subtitles.SubtitleAiJobsResponse
import com.continuum.app.model.subtitles.SubtitleAiQuota
import com.continuum.app.model.subtitles.SubtitleAiStatus
import com.continuum.app.model.subtitles.SubtitleDownloadRequest
import com.continuum.app.model.subtitles.SubtitleDownloadResponse
import com.continuum.app.model.subtitles.SubtitleSearchRequest
import com.continuum.app.model.subtitles.SubtitleSearchResponse
import com.continuum.app.model.subtitles.SubtitleTranslateRequest
import com.continuum.app.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Subtitle provider search/download + AI translation endpoints. Kept behind
 * an interface so repository and ViewModel tests can fake the transport,
 * matching the CalendarApi/RequestsApi shape.
 */
interface SubtitlesApi {

    /** POST /api/v1/subtitles/search — errors with server text when no providers are configured. */
    suspend fun search(request: SubtitleSearchRequest): ApiResult<SubtitleSearchResponse>

    /** POST /api/v1/subtitles/download — echoes the chosen search result back. */
    suspend fun download(request: SubtitleDownloadRequest): ApiResult<SubtitleDownloadResponse>

    /** GET /api/v1/subtitles/{media_file_id} — subtitles already stored server-side. */
    suspend fun list(mediaFileId: Int): ApiResult<DownloadedSubtitlesResponse>

    /** GET /api/v1/subtitles/ai/status — both flags false when AI is unconfigured. */
    suspend fun aiStatus(): ApiResult<SubtitleAiStatus>

    /** GET /api/v1/subtitles/ai/quota — transcribe-kind budget; admins are exempt. */
    suspend fun aiQuota(): ApiResult<SubtitleAiQuota>

    /** POST /api/v1/subtitles/ai/translate — 202 with the queued job; 429 quota; 503 unconfigured. */
    suspend fun translate(request: SubtitleTranslateRequest): ApiResult<SubtitleAiJobResponse>

    /** GET /api/v1/subtitles/ai/jobs?media_file_id=N */
    suspend fun listJobs(mediaFileId: Int): ApiResult<SubtitleAiJobsResponse>

    /** GET /api/v1/subtitles/ai/jobs/{id} — 404 once the job row is gone. */
    suspend fun getJob(jobId: Long): ApiResult<SubtitleAiJobResponse>

    /** POST /api/v1/subtitles/ai/jobs/{id}/cancel — 204 on success. */
    suspend fun cancelJob(jobId: Long): ApiResult<Unit>
}

class DefaultSubtitlesApi(private val client: HttpClient) : SubtitlesApi {

    override suspend fun search(request: SubtitleSearchRequest): ApiResult<SubtitleSearchResponse> =
        safeApiCall {
            client.post("/api/v1/subtitles/search") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun download(request: SubtitleDownloadRequest): ApiResult<SubtitleDownloadResponse> =
        safeApiCall {
            client.post("/api/v1/subtitles/download") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun list(mediaFileId: Int): ApiResult<DownloadedSubtitlesResponse> =
        safeApiCall {
            client.get("/api/v1/subtitles/$mediaFileId")
        }

    override suspend fun aiStatus(): ApiResult<SubtitleAiStatus> = safeApiCall {
        client.get("/api/v1/subtitles/ai/status")
    }

    override suspend fun aiQuota(): ApiResult<SubtitleAiQuota> = safeApiCall {
        client.get("/api/v1/subtitles/ai/quota")
    }

    override suspend fun translate(request: SubtitleTranslateRequest): ApiResult<SubtitleAiJobResponse> =
        safeApiCall {
            client.post("/api/v1/subtitles/ai/translate") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun listJobs(mediaFileId: Int): ApiResult<SubtitleAiJobsResponse> =
        safeApiCall {
            client.get("/api/v1/subtitles/ai/jobs") {
                parameter("media_file_id", mediaFileId)
            }
        }

    override suspend fun getJob(jobId: Long): ApiResult<SubtitleAiJobResponse> = safeApiCall {
        client.get("/api/v1/subtitles/ai/jobs/$jobId")
    }

    override suspend fun cancelJob(jobId: Long): ApiResult<Unit> = safeApiCall {
        client.post("/api/v1/subtitles/ai/jobs/$jobId/cancel")
    }
}
