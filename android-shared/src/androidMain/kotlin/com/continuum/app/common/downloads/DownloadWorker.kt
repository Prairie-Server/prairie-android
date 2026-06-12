package com.continuum.app.common.downloads

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.continuum.app.model.download.DownloadStatus
import com.continuum.app.repository.DownloadsRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Streams `GET /api/v1/downloads/{id}/file` to the local
 * `<filesDir>/downloads/<serverId>/<profileId>/<fileId>/<original-name>`
 * location via [DownloadStorage], reporting progress to WorkManager at
 * most every ~200ms; the foreground notification is rebuilt only when
 * the integer percent actually changes.
 *
 * Constructed by Koin's [org.koin.androidx.workmanager.factory.KoinWorkerFactory]
 * — see the `worker { ... }` registration in `androidModule`.
 *
 * **Failure handling.** On a transient IO error we return [Result.retry] so
 * WorkManager schedules a fresh attempt; the partial bytes on disk are
 * deleted first (no resume in v1) so the next attempt starts clean.
 */
class DownloadWorker(
    private val appContext: Context,
    params: androidx.work.WorkerParameters,
    private val repository: DownloadsRepository,
    private val storage: DownloadStorage,
    private val httpClient: HttpClient,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID)
            ?: return@withContext Result.failure()
        val fileId = inputData.getInt(KEY_FILE_ID, -1)
        val serverId = inputData.getString(KEY_SERVER_ID)
            ?: return@withContext Result.failure()
        val profileId = inputData.getString(KEY_PROFILE_ID)
            ?: return@withContext Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME)
        val container = inputData.getString(KEY_CONTAINER)
        val mediaType = inputData.getString(KEY_MEDIA_TYPE)
        val displayTitle = inputData.getString(KEY_DISPLAY_TITLE) ?: "Download"
        if (fileId < 0) return@withContext Result.failure()

        Log.i(TAG, "doWork start id=$downloadId fileId=$fileId title=$displayTitle")
        runCatching {
            setForeground(buildForegroundInfo(downloadId, displayTitle, progress = 0, indeterminate = true))
        }.onFailure { Log.w(TAG, "setForeground initial failed", it) }

        val target = storage.prepareWrite(serverId, profileId, fileId, fileName, container, mediaType)

        try {
            httpClient.prepareGet("/api/v1/downloads/$downloadId/file").execute { response ->
                val total = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
                val channel = response.bodyAsChannel()
                var written = 0L
                val throttle = DownloadProgressThrottle()

                val buf = ByteArray(BUFFER_BYTES)
                // Ktor 3.x ByteReadChannel → java.io.InputStream bridge.
                // Avoids version-fragile ByteReadChannel read APIs and keeps
                // the streaming copy + progress reporting on the same thread.
                channel.toInputStream().use { input ->
                    target.openOutputStream().use { out ->
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            written += n

                            val decision = throttle.onBytes(System.currentTimeMillis(), written, total)
                            if (decision.report) {
                                setProgress(workDataOf(KEY_BYTES_WRITTEN to written, KEY_TOTAL_BYTES to total))
                                // Rebuilding the notification (and its cancel
                                // PendingIntent) is the expensive part — only
                                // do it when the visible percent changed.
                                if (decision.updateForeground) {
                                    setForeground(buildForegroundInfo(downloadId, displayTitle, progress = decision.percent, indeterminate = total <= 0))
                                }
                                // Push progress into the shared repo so any
                                // currently-foregrounded UI re-renders without
                                // a round-trip GET /downloads.
                                repository.recordForFile(fileId)?.let { existing ->
                                    repository.upsertLocal(
                                        existing.copy(
                                            bytesSent = written,
                                            fileSize = if (total > 0) total else existing.fileSize,
                                            status = DownloadStatus.Downloading.wire,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Server flips status → completed when its serve handler returns;
            // a refresh here ensures the cache reflects that before the
            // worker exits and the UI re-renders.
            storage.completeWrite(target.uriString)
            Log.i(TAG, "doWork success id=$downloadId bytes=${target.sizeBytes()}")
            repository.refresh()
            // Update the sidecar to status=completed. Enqueuer wrote the
            // initial sidecar with title + poster; we just flip status here
            // so it survives an offline app launch.
            updateSidecarStatus(
                serverId, profileId, fileId,
                status = com.continuum.app.model.download.DownloadStatus.Completed.wire,
                bytesSent = target.sizeBytes(),
                fileSize = target.sizeBytes(),
                localUri = target.uriString,
            )
            Result.success(workDataOf(KEY_BYTES_WRITTEN to target.sizeBytes(), KEY_TOTAL_BYTES to target.sizeBytes()))
        } catch (e: CancellationException) {
            // Worker stopped — user cancel (notification action /
            // DownloadEnqueuer.cancel → cancelAllWorkByTag) or a
            // constraint / quota stop. Not a failure: drop the partial
            // bytes (prepareWrite starts clean on the next attempt
            // anyway) but leave the repo record + sidecar status alone.
            // A constraint-stop must keep "downloading" state so the
            // WorkManager retry restarts cleanly; a user cancel is
            // finalized by the record-delete path, not here. Writing
            // Failed here is what used to paint cancelled / paused
            // downloads with a red badge and delete-then-fail them.
            Log.i(TAG, "doWork cancelled id=$downloadId")
            withContext(NonCancellable) {
                runCatching { storage.deleteUri(target.uriString) }
            }
            throw e
        } catch (e: IOException) {
            // Transient — let WorkManager retry. Drop the partial file so the
            // next attempt starts fresh (no resume in v1). Sidecar stays so
            // the row is visible in the UI as "downloading" awaiting retry.
            Log.w(TAG, "doWork IO error id=$downloadId → retry", e)
            runCatching { storage.deleteUri(target.uriString) }
            Result.retry()
        } catch (e: Throwable) {
            // Permanent — clean up local file and let the user retry manually.
            Log.e(TAG, "doWork fatal id=$downloadId", e)
            runCatching { storage.deleteUri(target.uriString) }
            // Best-effort: publish failed state into the repo + sidecar.
            val record = repository.recordForFile(fileId)
            if (record != null) {
                repository.upsertLocal(record.copy(status = DownloadStatus.Failed.wire))
            }
            updateSidecarStatus(
                serverId, profileId, fileId,
                status = DownloadStatus.Failed.wire,
                bytesSent = 0,
                fileSize = 0,
                localUri = target.uriString,
            )
            Result.failure()
        }
    }

    /**
     * Read-modify-write the sidecar on disk so its status / bytesSent /
     * fileSize match the worker's current view. No-op if the sidecar
     * doesn't exist yet (Enqueuer always writes it at download start, so
     * this should only happen for legacy / corrupted state).
     */
    private fun updateSidecarStatus(
        serverId: String,
        profileId: String,
        fileId: Int,
        status: String,
        bytesSent: Long,
        fileSize: Long,
        localUri: String? = null,
    ) {
        runCatching {
            val existing = storage.readSidecar(serverId, profileId, fileId) ?: return@runCatching
            storage.writeSidecar(
                serverId, profileId,
                existing.copy(
                    record = existing.record.copy(
                        status = status,
                        bytesSent = if (bytesSent > 0) bytesSent else existing.record.bytesSent,
                        fileSize = if (fileSize > 0) fileSize else existing.record.fileSize,
                    ),
                    localUri = localUri ?: existing.localUri,
                    updatedAtMs = System.currentTimeMillis(),
                ),
            )
        }.onFailure { Log.w(TAG, "updateSidecarStatus failed for fileId=$fileId", it) }
    }

    private fun buildForegroundInfo(
        downloadId: String,
        title: String,
        progress: Int,
        indeterminate: Boolean,
    ): ForegroundInfo {
        val cancelIntent = WorkManager.getInstance(appContext)
            .createCancelPendingIntent(id)
        val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(if (indeterminate) "Starting…" else "$progress%")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate)
            .addAction(android.R.drawable.ic_delete, "Cancel", cancelIntent)
            .build()

        val notificationId = notificationIdFor(downloadId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    companion object {
        private const val TAG = "DownloadWorker"
        const val NOTIFICATION_CHANNEL_ID = "continuum_downloads"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_FILE_ID = "file_id"
        const val KEY_SERVER_ID = "server_id"
        const val KEY_PROFILE_ID = "profile_id"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_CONTAINER = "container"
        const val KEY_MEDIA_TYPE = "media_type"
        const val KEY_DISPLAY_TITLE = "display_title"
        const val KEY_BYTES_WRITTEN = "bytes"
        const val KEY_TOTAL_BYTES = "total"

        private const val BUFFER_BYTES = 64 * 1024

        fun tagFor(downloadId: String): String = "download_$downloadId"
        private fun notificationIdFor(downloadId: String): Int =
            // Stable per download, avoids collisions across concurrent workers.
            ("dl_$downloadId").hashCode() and 0x7FFFFFFF

        /**
         * Enqueue a unique one-time download for [downloadId]. Caller supplies
         * the `(serverId, profileId, fileId)` triple + a display title for
         * the notification. The [wifiOnly] flag drives the work constraint.
         *
         * Cancel via `WorkManager.cancelAllWorkByTag(tagFor(downloadId))`.
         */
        fun enqueue(
            context: Context,
            downloadId: String,
            fileId: Int,
            serverId: String,
            profileId: String,
            fileName: String?,
            container: String?,
            mediaType: String?,
            displayTitle: String,
            wifiOnly: Boolean,
        ) {
            val data = workDataOf(
                KEY_DOWNLOAD_ID to downloadId,
                KEY_FILE_ID to fileId,
                KEY_SERVER_ID to serverId,
                KEY_PROFILE_ID to profileId,
                KEY_FILE_NAME to fileName,
                KEY_CONTAINER to container,
                KEY_MEDIA_TYPE to mediaType,
                KEY_DISPLAY_TITLE to displayTitle,
            )
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(data)
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(tagFor(downloadId))
                .build()
            Log.i(TAG, "enqueue id=$downloadId fileId=$fileId wifiOnly=$wifiOnly")
            WorkManager.getInstance(context)
                .enqueueUniqueWork(tagFor(downloadId), ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context, downloadId: String) {
            WorkManager.getInstance(context).cancelAllWorkByTag(tagFor(downloadId))
        }
    }
}
