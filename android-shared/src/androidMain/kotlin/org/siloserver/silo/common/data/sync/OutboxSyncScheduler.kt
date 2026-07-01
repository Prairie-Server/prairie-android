package org.siloserver.silo.common.data.sync

/**
 * Requests an outbox drain (enqueues `SyncWorker`). Injected into
 * [org.siloserver.silo.common.data.repository.RoomUserItemStateRepository] so the
 * repository can ask for a drain when a write is left pending, without
 * depending on `Context`/`WorkManager` directly.
 *
 * Triggered from `resolve(RETRIABLE)` — never from the optimistic `record()` —
 * so it cannot race the inline network call and double-send while online.
 *
 * Defaults to [NONE] (no-op) so unit tests and any unbound path stay inert.
 */
fun interface OutboxSyncScheduler {
    fun requestSync()

    companion object {
        val NONE = OutboxSyncScheduler { }
    }
}
