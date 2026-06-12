// shared/src/commonMain/kotlin/com/continuum/app/repository/SubtitlesRepository.kt
package com.continuum.app.repository

import com.continuum.app.model.subtitles.DownloadedSubtitlesResponse
import com.continuum.app.model.subtitles.SubtitleAiJob
import com.continuum.app.model.subtitles.SubtitleAiJobResponse
import com.continuum.app.model.subtitles.SubtitleAiJobsResponse
import com.continuum.app.model.subtitles.SubtitleAiJobStatus
import com.continuum.app.model.subtitles.SubtitleAiQuota
import com.continuum.app.model.subtitles.SubtitleAiStatus
import com.continuum.app.model.subtitles.SubtitleDownloadRequest
import com.continuum.app.model.subtitles.SubtitleDownloadResponse
import com.continuum.app.model.subtitles.SubtitleSearchRequest
import com.continuum.app.model.subtitles.SubtitleSearchResponse
import com.continuum.app.model.subtitles.SubtitleTranslateRequest
import com.continuum.app.network.ApiResult
import com.continuum.app.network.api.SubtitlesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Subtitle provider search/download + AI translation. Thin pass-throughs over
 * [SubtitlesApi] plus [pollJob], a suspend loop modeled on
 * [DeviceLoginRepository.runPollLoop]:
 *  - polls immediately (don't wait the first interval)
 *  - swallows transient errors (non-404 [ApiResult.Error], [ApiResult.NetworkError])
 *    and retries after the interval
 *  - 404 = the job row is gone → terminal [SubtitleJobOutcome.Failed]
 *  - terminal job statuses (completed/failed/cancelled) end the loop
 *  - rethrows [CancellationException] so callers can cancel via structured
 *    concurrency (player exit cancels the viewModelScope job)
 */
class SubtitlesRepository(private val api: SubtitlesApi) {

    /** Terminal result of [pollJob]. */
    sealed class SubtitleJobOutcome {
        data class Completed(val resultSubtitleId: Int?) : SubtitleJobOutcome()
        data class Failed(val message: String?) : SubtitleJobOutcome()
        object Cancelled : SubtitleJobOutcome() {
            override fun toString(): String = "Cancelled"
        }
    }

    suspend fun search(request: SubtitleSearchRequest): ApiResult<SubtitleSearchResponse> =
        api.search(request)

    suspend fun download(request: SubtitleDownloadRequest): ApiResult<SubtitleDownloadResponse> =
        api.download(request)

    suspend fun list(mediaFileId: Int): ApiResult<DownloadedSubtitlesResponse> =
        api.list(mediaFileId)

    suspend fun aiStatus(): ApiResult<SubtitleAiStatus> = api.aiStatus()

    suspend fun aiQuota(): ApiResult<SubtitleAiQuota> = api.aiQuota()

    suspend fun translate(request: SubtitleTranslateRequest): ApiResult<SubtitleAiJobResponse> =
        api.translate(request)

    suspend fun listJobs(mediaFileId: Int): ApiResult<SubtitleAiJobsResponse> =
        api.listJobs(mediaFileId)

    suspend fun getJob(jobId: Long): ApiResult<SubtitleAiJobResponse> = api.getJob(jobId)

    suspend fun cancelJob(jobId: Long): ApiResult<Unit> = api.cancelJob(jobId)

    /**
     * Polls GET /ai/jobs/{id} every [intervalMs] until the job reaches a
     * terminal status, invoking [onUpdate] with every successfully fetched
     * snapshot (including the terminal one, so progress UIs can show 100%
     * before dismissing).
     */
    suspend fun pollJob(
        jobId: Long,
        intervalMs: Long = 1_000L,
        onUpdate: (SubtitleAiJob) -> Unit = {},
    ): SubtitleJobOutcome {
        while (true) {
            try {
                val job = when (val r = api.getJob(jobId)) {
                    is ApiResult.Success -> r.data.job
                    is ApiResult.Error -> {
                        if (r.code == 404) {
                            return SubtitleJobOutcome.Failed(
                                "This job no longer exists on the server.",
                            )
                        }
                        // Transient — keep trying.
                        delay(intervalMs)
                        continue
                    }
                    is ApiResult.NetworkError -> {
                        // Transient network blip — keep trying.
                        delay(intervalMs)
                        continue
                    }
                }

                onUpdate(job)

                when (job.status) {
                    SubtitleAiJobStatus.Completed ->
                        return SubtitleJobOutcome.Completed(job.resultSubtitleId)
                    SubtitleAiJobStatus.Failed ->
                        return SubtitleJobOutcome.Failed(job.errorMessage)
                    SubtitleAiJobStatus.Cancelled ->
                        return SubtitleJobOutcome.Cancelled
                    else ->
                        // pending / running (and forward-compatible unknowns).
                        delay(intervalMs)
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }
}
