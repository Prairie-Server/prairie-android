package org.siloserver.silo.common.player

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.siloserver.silo.repository.port.PlaybackWriteScope

/**
 * Process-global latest-write-wins ordering for final playback track choices.
 *
 * Adapter-local queues preserve FIFO while an adapter is alive. Tickets issued
 * here extend that ordering across a retired adapter and its replacement.
 */
class PlaybackTrackSelectionWriteCoordinator {
    class Ticket internal constructor(
        internal val key: Key,
        internal val sequence: Long,
    )

    internal data class Key(
        val scope: PlaybackWriteScope,
        val contentId: String,
        val fileId: Int,
    )

    private val sequence = AtomicLong(0L)
    private val states = mutableMapOf<Key, State>()

    private class State {
        val mutex = Mutex()
        var latestStartedSequence = 0L
        var latestDurableSequence = 0L
    }

    fun capture(
        scope: PlaybackWriteScope,
        contentId: String,
        fileId: Int,
    ): Ticket = Ticket(
        key = Key(scope, contentId, fileId),
        sequence = sequence.incrementAndGet(),
    )

    suspend fun write(
        ticket: Ticket,
        persist: suspend () -> Boolean,
    ): Boolean {
        val state = synchronized(states) {
            states.getOrPut(ticket.key, ::State)
        }
        return state.mutex.withLock {
            if (ticket.sequence < state.latestStartedSequence) {
                return@withLock state.latestDurableSequence >= ticket.sequence
            }
            state.latestStartedSequence = ticket.sequence

            if (!persist()) return@withLock false
            state.latestDurableSequence = ticket.sequence
            true
        }
    }

    companion object {
        val Process = PlaybackTrackSelectionWriteCoordinator()
    }
}
