package org.siloserver.silo.tv.watchnext

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.repository.SectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Periodic (and on-demand) sync worker that mirrors the server's home-screen
 * "continue watching" / "next up" sections into Android TV's Watch Next channel
 * via [WatchNextRepository.diffAndApply].
 *
 * Constructed by [TvWorkerFactory], installed via `WorkManager.initialize`
 * in `SiloTvApplication` (KoinWorkerFactory was silently returning
 * null on WM 2.10 + Koin 4.1.0 — see androidApp's AppWorkerFactory).
 */
class WatchNextSyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val sectionRepository: SectionRepository,
    private val repository: WatchNextRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sections = when (val r = sectionRepository.getHomeSections()) {
            is ApiResult.Success -> r.data.sections
            is ApiResult.Error -> return@withContext Result.retry()
            is ApiResult.NetworkError -> return@withContext Result.retry()
        }

        val nowMs = System.currentTimeMillis()
        val fields = sections.asSequence()
            .filter { it.sectionType in WATCH_NEXT_SECTION_TYPES }
            .flatMap { section ->
                section.items.asSequence().mapNotNull { item ->
                    WatchNextProgramMapper.map(item, section.sectionType, nowMs)
                }
            }
            .toList()

        repository.diffAndApply(fields)
        Result.success()
    }

    companion object {
        const val UNIQUE_NAME_PERIODIC = "watch_next_sync_periodic"
        const val UNIQUE_NAME_ONESHOT = "watch_next_sync_oneshot"
        private val WATCH_NEXT_SECTION_TYPES = setOf("continue_watching", "next_up")
    }
}
