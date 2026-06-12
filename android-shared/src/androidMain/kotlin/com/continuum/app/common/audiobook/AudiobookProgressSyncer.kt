package com.continuum.app.common.audiobook

import com.continuum.app.model.personal.SyncProgressItem
import com.continuum.app.repository.PersonalDataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Pushes audiobook listening position recorded on this device back to the
 * server. Playing offline only updates the local [AudiobookPositionStore];
 * this syncer flushes those snapshots to `POST /api/v1/sync/progress` so the
 * server's Continue-Listening row and cross-device resume catch up once the
 * device is online again.
 *
 * [requestFlush] is fire-and-forget and serialized via a mutex, safe to call
 * from the connectivity + foreground triggers wired in the app. The flush is
 * scope-agnostic: it pushes the latest snapshot per content id across all
 * on-disk scopes, so it still works when an offline write landed under a
 * default scope before the active server id was restored.
 */
class AudiobookProgressSyncer(
    private val positionStore: AudiobookPositionStore,
    private val personalDataRepository: PersonalDataRepository,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()

    fun requestFlush() {
        scope.launch { runCatching { flush() } }
    }

    suspend fun flush() = mutex.withLock {
        val entries = withContext(Dispatchers.IO) { positionStore.listAll() }
            .filter { it.snapshot.positionSeconds > 0 && it.snapshot.durationSeconds > 0 }
        if (entries.isNotEmpty()) {
            val items = entries
                .groupBy { it.contentId }
                .mapNotNull { (_, list) -> list.maxByOrNull { it.snapshot.updatedAtMs } }
                .map {
                    SyncProgressItem(
                        mediaItemId = it.contentId,
                        position = it.snapshot.positionSeconds,
                        duration = it.snapshot.durationSeconds,
                    )
                }
            personalDataRepository.syncProgress(items)
        }
    }
}
