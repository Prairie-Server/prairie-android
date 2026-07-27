package org.prairieserver.prairie.common.downloads

import kotlinx.coroutines.CompletableDeferred

/**
 * Process-local barrier between a DownloadWorker and destructive metadata
 * cleanup. WorkManager's cancellation Operation acknowledges the stop request,
 * not completion of CoroutineWorker cleanup, so callers must also await this
 * lease.
 */
internal object DownloadWorkerLifetime {
    private const val MAX_CANCELLED_TOMBSTONES = 1_024
    private val lock = Any()
    private val states = LinkedHashMap<String, State>(16, 0.75f, true)

    private class State {
        var active = 0
        var cancelling = false
        var idle = CompletableDeferred(Unit)
    }

    class Lease internal constructor(private val downloadId: String) {
        fun close() = release(downloadId)
    }

    fun acquire(downloadId: String): Lease? = synchronized(lock) {
        val state = states.getOrPut(downloadId) { State() }
        if (state.cancelling) return@synchronized null
        if (state.active == 0) state.idle = CompletableDeferred()
        state.active += 1
        Lease(downloadId)
    }

    fun beginCancellation(downloadId: String) = synchronized(lock) {
        states.getOrPut(downloadId) { State() }.cancelling = true
        trimCancelledTombstones()
    }

    suspend fun awaitIdle(downloadId: String) {
        val idle = synchronized(lock) {
            states[downloadId]?.idle ?: CompletableDeferred(Unit)
        }
        idle.await()
    }

    private fun release(downloadId: String) = synchronized(lock) {
        val state = states[downloadId] ?: return@synchronized
        state.active -= 1
        if (state.active == 0) {
            state.idle.complete(Unit)
            if (!state.cancelling) states.remove(downloadId)
        }
    }

    private fun trimCancelledTombstones() {
        if (states.size <= MAX_CANCELLED_TOMBSTONES) return
        val iterator = states.entries.iterator()
        while (states.size > MAX_CANCELLED_TOMBSTONES && iterator.hasNext()) {
            val state = iterator.next().value
            if (state.cancelling && state.active == 0) iterator.remove()
        }
    }
}
