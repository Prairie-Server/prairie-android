package com.continuum.app.common.data.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Drains the outbox via [SyncEngine] in the background. Constructor-injected by
 * the per-app hand-rolled WorkerFactory (the Koin `worker{}` DSL is dead at
 * runtime on WM 2.10 + Koin 4.1.0 — see `AppWorkerFactory`).
 *
 * Returns [Result.retry] while any op for the scope remains (failed-and-backing-
 * off or not-yet-processed) so WorkManager keeps re-running with its own
 * exponential backoff — a clean partial batch or a backoff row can never strand
 * the outbox. The chain ends only when the scope's outbox is empty.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val syncEngine: SyncEngine,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        val result = syncEngine.drainOnce()
        Log.i(
            TAG,
            "drain: synced=${result.synced} dropped=${result.dropped} " +
                "retriable=${result.retriable} remaining=${result.remaining}",
        )
        if (result.hasPendingWork) Result.retry() else Result.success()
    } catch (t: Throwable) {
        Log.w(TAG, "drain failed; will retry", t)
        Result.retry()
    }

    companion object {
        private const val TAG = "SyncWorker"
        const val UNIQUE_NAME = "silo-outbox-sync"

        /**
         * Enqueue a single outbox drain. [ExistingWorkPolicy.KEEP] coalesces
         * bursts of mutations into one drain; the DB-backed outbox means a
         * queued duplicate would add no value.
         */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
