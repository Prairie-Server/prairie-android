package org.prairieserver.prairie.common.player

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.prairieserver.prairie.repository.port.PlaybackWriteScope

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
        internal val state: State,
    ) {
        internal var resolved: Boolean = false
        internal var resolvedSuccess: Boolean = false
    }

    internal class State {
        val mutex = Mutex()
        var latestStartedSequence = 0L
        var latestDurableSequence = 0L
        var outstandingTickets = 0
        var activeWrites = 0
    }

    internal val activeKeyCount: Int
        get() = synchronized(states) {
            states.size
        }

    internal data class Key(
        val scope: PlaybackWriteScope,
        val contentId: String,
        val fileId: Int,
    )

    private val sequence = AtomicLong(0L)
    private val states = mutableMapOf<Key, State>()

    fun capture(
        scope: PlaybackWriteScope,
        contentId: String,
        fileId: Int,
    ): Ticket {
        val key = Key(scope, contentId, fileId)
        val state = synchronized(states) {
            states.getOrPut(key, ::State).also {
                it.outstandingTickets += 1
            }
        }
        return Ticket(
            key = key,
            sequence = sequence.incrementAndGet(),
            state = state,
        )
    }

    suspend fun write(
        ticket: Ticket,
        persist: suspend () -> Boolean,
    ): Boolean {
        synchronized(ticket) {
            if (ticket.resolved) return ticket.resolvedSuccess
            synchronized(states) {
                ticket.state.activeWrites += 1
            }
        }
        val state = ticket.state
        try {
            return state.mutex.withLock {
                synchronized(ticket) {
                    if (ticket.resolved) return@withLock ticket.resolvedSuccess
                }
                val success = if (ticket.sequence < state.latestStartedSequence) {
                    state.latestDurableSequence >= ticket.sequence
                } else {
                    state.latestStartedSequence = ticket.sequence
                    persist().also { durable ->
                        if (durable) state.latestDurableSequence = ticket.sequence
                    }
                }
                if (success) resolve(ticket, success = true)
                success
            }
        } finally {
            synchronized(states) {
                state.activeWrites -= 1
                removeIfUnused(ticket.key, state)
            }
        }
    }

    fun abandon(ticket: Ticket) {
        resolve(ticket, success = false)
    }

    private fun resolve(ticket: Ticket, success: Boolean) {
        synchronized(ticket) {
            if (ticket.resolved) return
            ticket.resolved = true
            ticket.resolvedSuccess = success
        }
        synchronized(states) {
            ticket.state.outstandingTickets -= 1
            removeIfUnused(ticket.key, ticket.state)
        }
    }

    private fun removeIfUnused(key: Key, state: State) {
        if (state.outstandingTickets == 0 && state.activeWrites == 0) {
            states.remove(key, state)
        }
    }

    companion object {
        val Process = PlaybackTrackSelectionWriteCoordinator()
    }
}
