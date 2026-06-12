package com.continuum.app.common.ebook

import com.continuum.app.model.ebook.SaveEbookProgressRequest
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.EbookReaderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Pushes reading position recorded on this device back to the server. Reading
 * offline only updates the local [EbookLocalStateStore]; this syncer flushes
 * those snapshots once online so server progress + cross-device resume catch up.
 *
 * Unlike the audiobook `/sync/progress` batch endpoint, the ebook progress PUT
 * is a naive overwrite, so we compare against the server's current progress per
 * book and only push when our local read is *further* along — reading is
 * monotonic, so this never regresses progress made on another device.
 *
 * [requestFlush] is fire-and-forget and serialized via a mutex; safe to call
 * from the connectivity + foreground triggers wired in the app.
 */
class EbookProgressSyncer(
    private val localStateStore: EbookLocalStateStore,
    private val ebookReaderRepository: EbookReaderRepository,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()

    fun requestFlush() {
        scope.launch { runCatching { flush() } }
    }

    suspend fun flush() = mutex.withLock {
        val latest = withContext(Dispatchers.IO) { localStateStore.listAllProgress() }
            .groupBy { it.contentId }
            .mapNotNull { (_, list) -> list.maxByOrNull { it.snapshot.updatedAtMs } }
        for (entry in latest) {
            val snapshot = entry.snapshot
            val serverProgress = when (val result = ebookReaderRepository.getProgress(entry.contentId)) {
                is ApiResult.Success -> result.data.progress
                else -> null
            }
            if (serverProgress == null || snapshot.progress > serverProgress) {
                ebookReaderRepository.saveProgress(
                    entry.contentId,
                    SaveEbookProgressRequest(
                        fileId = snapshot.fileId,
                        location = snapshot.location,
                        progress = snapshot.progress,
                    ),
                )
            }
        }
    }
}
