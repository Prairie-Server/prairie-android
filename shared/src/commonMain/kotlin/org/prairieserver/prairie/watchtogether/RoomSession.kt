package org.prairieserver.prairie.watchtogether

import org.prairieserver.prairie.model.watchtogether.RoomSnapshot
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.IdentityTransitionBarrier
import org.prairieserver.prairie.network.IdentityTransitionPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Narrow room lifecycle port so the application-scoped owner can be tested
 * independently from REST and websocket implementations.
 */
interface RoomSessionRepository {
    val roomSnapshot: StateFlow<RoomSnapshot?>
    val roomClosedReason: StateFlow<String?>

    suspend fun connect(roomId: String)
    suspend fun reset()
    suspend fun closeRoom(): ApiResult<Unit>
}

/** A Watch Together room, not a device-local episode queue, owns title changes. */
fun shouldNavigateToLocalNext(inWatchTogetherRoom: Boolean): Boolean =
    !inWatchTogetherRoom

/**
 * The single application-scoped owner of a Watch Together connection.
 *
 * Screen scopes adopt this session; they never own the socket. Replacement
 * and leave are serialized, and the previous job is joined before repository
 * state can be reused by another room.
 */
class RoomSession(
    private val repository: RoomSessionRepository,
    private val scope: CoroutineScope,
    identityTransitions: IdentityTransitionBarrier,
) {
    private val mutex = Mutex()
    private var connectionJob: Job? = null
    private var connectedRoomId: String? = null

    val room: StateFlow<RoomSnapshot?> = repository.roomSnapshot
    val closedReason: StateFlow<String?> = repository.roomClosedReason

    init {
        identityTransitions.installGate { transition ->
            if (transition.phase == IdentityTransitionPhase.WILL_CHANGE) {
                leave()
            }
        }
    }

    /**
     * UI-facing entry point. The returned job is owned by the process scope, so
     * navigation cannot cancel a queued replacement midway through teardown.
     */
    fun adopt(roomId: String): Job =
        scope.launch(start = CoroutineStart.UNDISPATCHED) { enter(roomId) }

    /**
     * UI-facing durable departure. If requested, close is attempted while the
     * current lease is still live; teardown then joins the socket and resets
     * state even if the initiating screen disappears.
     */
    fun depart(closeRoom: Boolean = false): Job =
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutex.withLock {
                if (closeRoom && connectedRoomId != null) {
                    try {
                        repository.closeRoom()
                    } finally {
                        leaveLocked()
                    }
                } else {
                    leaveLocked()
                }
            }
        }

    suspend fun enter(roomId: String) {
        if (roomId.isBlank()) return
        mutex.withLock {
            if (connectedRoomId == roomId && connectionJob?.isActive == true) return
            connectionJob?.cancelAndJoin()
            connectedRoomId = roomId
            // Establish repository ownership before enter() returns. Without
            // UNDISTPATCHED, a concurrent replacement can cancel a queued job
            // before it ever installs its connection owner.
            connectionJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                repository.connect(roomId)
            }
        }
    }

    suspend fun leave() {
        mutex.withLock {
            leaveLocked()
        }
    }

    suspend fun isActive(): Boolean = mutex.withLock { connectionJob?.isActive == true }

    private suspend fun leaveLocked() {
        connectionJob?.cancelAndJoin()
        connectionJob = null
        connectedRoomId = null
        repository.reset()
    }
}
