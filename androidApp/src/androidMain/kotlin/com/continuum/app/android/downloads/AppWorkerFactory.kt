package com.continuum.app.android.downloads

import android.content.Context
import android.util.Log
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.continuum.app.common.downloads.DownloadStorage
import com.continuum.app.common.downloads.DownloadWorker
import com.continuum.app.repository.DownloadsRepository
import io.ktor.client.HttpClient
import org.koin.core.context.GlobalContext

/**
 * Hand-rolled WorkerFactory that constructs DI-dependent workers via Koin.
 *
 * Replaces koin-androidx-workmanager's `workManagerFactory()` /
 * `KoinWorkerFactory` because that path was silently returning null for
 * DownloadWorker on this codebase (WM 2.10 + Koin 4.1.0), forcing
 * WorkerFactory to fall back to reflection — which crashed because
 * DownloadWorker takes injected dependencies, not the default
 * `(Context, WorkerParameters)` constructor.
 *
 * Add a `when` branch per new worker class.
 */
class AppWorkerFactory : WorkerFactory() {
    init {
        Log.i(TAG, "AppWorkerFactory constructed")
    }

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        Log.i(TAG, "createWorker called for $workerClassName")
        val koin = GlobalContext.get()
        return when (workerClassName) {
            DownloadWorker::class.java.name -> {
                Log.i(TAG, "Building DownloadWorker via Koin")
                DownloadWorker(
                    appContext = appContext,
                    params = workerParameters,
                    repository = koin.get<DownloadsRepository>(),
                    storage = koin.get<DownloadStorage>(),
                    httpClient = koin.get<HttpClient>(),
                )
            }
            else -> {
                Log.w(TAG, "No factory match for $workerClassName — returning null")
                null
            }
        }
    }

    companion object {
        private const val TAG = "AppWorkerFactory"
    }
}
