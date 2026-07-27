package org.siloserver.silo.repository

import org.siloserver.silo.RoomSyncEngine
import org.siloserver.silo.model.watchtogether.AddSuggestionRequest
import org.siloserver.silo.model.watchtogether.CreateRoomRequest
import org.siloserver.silo.model.watchtogether.JoinRoomRequest
import org.siloserver.silo.model.watchtogether.PromoteSuggestionRequest
import org.siloserver.silo.model.watchtogether.RoomResponse
import org.siloserver.silo.model.watchtogether.RoomSnapshot
import org.siloserver.silo.model.watchtogether.SetSelectionRequest
import org.siloserver.silo.model.watchtogether.Suggestion
import org.siloserver.silo.model.watchtogether.SuggestionsResponse
import org.siloserver.silo.model.watchtogether.TransportCommand
import org.siloserver.silo.model.watchtogether.UpdatePolicyRequest
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.AuthScopeSnapshot
import org.siloserver.silo.network.RoomRealtimeEvent
import org.siloserver.silo.network.WatchTogetherRealtimeClient
import org.siloserver.silo.network.api.WatchTogetherApi
import org.siloserver.silo.util.parseRfc3339ToEpochMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

/**
 * A transport command paired with its `execute_at` already parsed to a
 * server-epoch millisecond value, so the player binding never has to touch the
 * RFC3339Nano wire string. [executeAtMs] is null when the wire timestamp is
 * malformed (the binding should then apply immediately / fall back).
 */
data class ScheduledTransportCommand(
    val command: TransportCommand,
    val executeAtMs: Long?,
)

/**
 * A pong frame with its three server-clock RFC3339Nano timestamps parsed to
 * epoch millis. The fourth NTP sample value, `clientReceivedMs`, is NOT here:
 * it must be stamped by the player binding at the instant it receives the pong
 * (the shared module has no wall clock — `Date.now()`/`System.currentTimeMillis`
 * are platform APIs), and supplied when the binding calls
 * `RoomSyncEngine.recordPongSample(clientSentMs, serverReceivedMs, serverSentMs, clientReceivedMs)`.
 * Any field is null when its wire timestamp was malformed.
 */
data class PongSample(
    val clientSentMs: Long?,
    val serverReceivedMs: Long?,
    val serverSentMs: Long?,
)

data class WatchTogetherConnectionState(
    val generation: Long = 0L,
    val epoch: Long = 0L,
    val writable: Boolean = false,
)

/**
 * Singleton owner of one Watch Together room's state and websocket lifecycle.
 * The per-room WS IS the feature (no REST fallback); the REST calls are for
 * create/join + host management + suggestion mutations.
 *
 * Holds the **room JWT** internally after create/join so every room-scoped op
 * passes it transparently. Folds snapshot/suggestions WS events into
 * [roomSnapshot]/[suggestions] StateFlows, re-merging `voted_by_me` (forced
 * false in broadcasts) from a locally-tracked vote set. Exposes the
 * sync-relevant client→server send passthroughs and a [transportCommands] flow
 * the player binding feeds to its [RoomSyncEngine] (the engine needs the
 * player's local position/playing/clock, which live in the binding, not here).
 *
 * [realtimeFactory] is injected so tests supply a fake event flow.
 */
class WatchTogetherRepository(
    private val api: WatchTogetherApi,
    private val realtimeFactory: () -> WatchTogetherRealtimeClient? = { null },
    private val monotonicNowMs: () -> Long = { MONOTONIC_ORIGIN.elapsedNow().inWholeMilliseconds },
    private val authScopeProvider: suspend () -> AuthScopeSnapshot? = { null },
) {
    private data class RoomBinding(
        val roomId: String,
        val roomToken: String,
        val authScope: AuthScopeSnapshot,
        val generation: Long,
    )

    private val stateMutex = Mutex()
    private var nextGeneration = 0L
    private var latestRoomRequest = 0L
    private var binding: RoomBinding? = null
    private var terminalGeneration: Long? = null
    private var realtimeGeneration: Long? = null
    private val _roomSnapshot = MutableStateFlow<RoomSnapshot?>(null)
    private val _suggestions = MutableStateFlow<List<Suggestion>>(emptyList())

    val roomSnapshot: StateFlow<RoomSnapshot?> = _roomSnapshot.asStateFlow()
    val suggestions: StateFlow<List<Suggestion>> = _suggestions.asStateFlow()
    private val _connectionState = MutableStateFlow(WatchTogetherConnectionState())
    val connectionState: StateFlow<WatchTogetherConnectionState> = _connectionState.asStateFlow()

    /**
     * Transport commands surfaced for the player binding to feed to its
     * [RoomSyncEngine]. Buffered + drop-oldest so emission never suspends the
     * collect loop. replay=0 — a late subscriber should not re-apply a stale
     * command.
     */
    private val _transportCommands = MutableSharedFlow<ScheduledTransportCommand>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val transportCommands: SharedFlow<ScheduledTransportCommand> = _transportCommands.asSharedFlow()

    /**
     * Pong samples for the player binding's engine clock-sync, with the three
     * server-clock timestamps parsed to epoch millis. The binding stamps
     * `clientReceivedMs` itself (see [PongSample]).
     */
    private val _pongs = MutableSharedFlow<PongSample>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val pongs: SharedFlow<PongSample> = _pongs.asSharedFlow()

    /**
     * Why the room ended — TERMINAL only. Set exclusively on the server
     * `room_closed` / [RoomRealtimeEvent.Closed] reason path (host left / explicit
     * close); the player observes this and exits. Cleared on [reset] and at the
     * start of a fresh [connect]. Transient server `error` frames do NOT populate
     * this (they would eject the user) — see [errors].
     */
    private val _roomClosedReason = MutableStateFlow<String?>(null)
    val roomClosedReason: StateFlow<String?> = _roomClosedReason.asStateFlow()

    /**
     * Transient, non-terminal server `error` frames (e.g. a rejected
     * transport_request). The UI may surface these as a snackbar/toast WITHOUT
     * exiting the room. Buffered + drop-oldest so emission never suspends the
     * fold; replay=0 so a late subscriber doesn't re-show a stale error.
     */
    private val _errors = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    // Locally-tracked vote set: ids the local user has voted for. Used to
    // re-merge voted_by_me into broadcast suggestion lists (which force false).
    private val votedIds = mutableSetOf<String>()

    @kotlin.concurrent.Volatile
    private var realtime: WatchTogetherRealtimeClient? = null

    // ---- REST: create / join (store the room token) ---------------------------

    suspend fun createRoom(request: CreateRoomRequest): ApiResult<RoomResponse> {
        val scope = authScopeProvider() ?: return missingAuthScope()
        val requestGeneration = beginRoomRequest()
        val r = api.createRoom(request, scope)
        if (authScopeProvider() != scope) return obsoleteRoomRequest()
        return if (r is ApiResult.Success) installRoomResponse(r.data, scope, requestGeneration) else r
    }

    suspend fun joinRoom(request: JoinRoomRequest): ApiResult<RoomResponse> {
        val scope = authScopeProvider() ?: return missingAuthScope()
        val requestGeneration = beginRoomRequest()
        val r = api.joinRoom(request, scope)
        if (authScopeProvider() != scope) return obsoleteRoomRequest()
        return if (r is ApiResult.Success) installRoomResponse(r.data, scope, requestGeneration) else r
    }

    private suspend fun beginRoomRequest(): Long = stateMutex.withLock { ++latestRoomRequest }

    private suspend fun installRoomResponse(
        data: RoomResponse,
        scope: AuthScopeSnapshot,
        requestGeneration: Long,
    ): ApiResult<RoomResponse> {
        if (data.room.roomId.isBlank() || data.roomAccessToken.isBlank()) {
            return invalidRoomResponse()
        }
        stateMutex.withLock {
            if (requestGeneration != latestRoomRequest) return obsoleteRoomRequest()
            val installed = RoomBinding(
                roomId = data.room.roomId,
                roomToken = data.roomAccessToken,
                authScope = scope,
                generation = ++nextGeneration,
            )
            binding = installed
            terminalGeneration = null
            votedIds.clear()
            _suggestions.value = emptyList()
            _roomClosedReason.value = null
            _roomSnapshot.value = data.room
            _connectionState.value = WatchTogetherConnectionState(generation = installed.generation)
        }
        return ApiResult.Success(data)
    }

    // ---- REST: host management ------------------------------------------------

    suspend fun setSelection(request: SetSelectionRequest): ApiResult<RoomResponse> {
        val lease = activeBinding() ?: return missingRoom()
        val r = api.setSelection(lease.roomId, lease.roomToken, request, lease.authScope)
        return publishRoomResponse(lease, r)
    }

    suspend fun updatePolicy(request: UpdatePolicyRequest): ApiResult<RoomResponse> {
        val lease = activeBinding() ?: return missingRoom()
        val r = api.updatePolicy(lease.roomId, lease.roomToken, request, lease.authScope)
        return publishRoomResponse(lease, r)
    }

    suspend fun closeRoom(): ApiResult<Unit> {
        val lease = activeBinding() ?: return missingRoom()
        return api.closeRoom(lease.roomId, lease.roomToken, lease.authScope)
    }

    // ---- REST: suggestions ----------------------------------------------------

    suspend fun refreshSuggestions(): ApiResult<SuggestionsResponse> {
        val lease = activeBinding() ?: return missingRoom()
        val r = api.listSuggestions(lease.roomId, lease.roomToken, lease.authScope)
        return publishSuggestionsResponse(lease, r)
    }

    suspend fun addSuggestion(request: AddSuggestionRequest): ApiResult<SuggestionsResponse> {
        val lease = activeBinding() ?: return missingRoom()
        val r = api.addSuggestion(lease.roomId, lease.roomToken, request, lease.authScope)
        return publishSuggestionsResponse(lease, r)
    }

    suspend fun deleteSuggestion(suggestionId: String): ApiResult<SuggestionsResponse> {
        val lease = activeBinding() ?: return missingRoom()
        val r = api.deleteSuggestion(lease.roomId, lease.roomToken, suggestionId, lease.authScope)
        return publishSuggestionsResponse(lease, r)
    }

    suspend fun vote(suggestionId: String): ApiResult<SuggestionsResponse> {
        val lease = activeBinding() ?: return missingRoom()
        val r = api.vote(lease.roomId, lease.roomToken, suggestionId, lease.authScope)
        if (r !is ApiResult.Success) return r
        stateMutex.withLock {
            if (!isCurrentLocked(lease)) return r
            votedIds.add(suggestionId)
            applySuggestions(r.data.suggestions, fromBroadcast = false)
        }
        return r
    }

    suspend fun unvote(suggestionId: String): ApiResult<SuggestionsResponse> {
        val lease = activeBinding() ?: return missingRoom()
        val r = api.unvote(lease.roomId, lease.roomToken, suggestionId, lease.authScope)
        if (r !is ApiResult.Success) return r
        stateMutex.withLock {
            if (!isCurrentLocked(lease)) return r
            votedIds.remove(suggestionId)
            applySuggestions(r.data.suggestions, fromBroadcast = false)
        }
        return r
    }

    suspend fun promoteSuggestion(request: PromoteSuggestionRequest): ApiResult<RoomResponse> {
        val lease = activeBinding() ?: return missingRoom()
        val r = api.promoteSuggestion(lease.roomId, lease.roomToken, request, lease.authScope)
        return publishRoomResponse(lease, r)
    }

    private suspend fun activeBinding(): RoomBinding? = stateMutex.withLock {
        binding?.takeIf { terminalGeneration != it.generation }
    }

    private fun isCurrentLocked(lease: RoomBinding): Boolean =
        binding == lease && terminalGeneration != lease.generation

    private suspend fun publishRoomResponse(
        lease: RoomBinding,
        result: ApiResult<RoomResponse>,
    ): ApiResult<RoomResponse> {
        if (result !is ApiResult.Success) return result
        if (result.data.room.roomId != lease.roomId) return invalidRoomResponse()
        stateMutex.withLock {
            if (isCurrentLocked(lease)) _roomSnapshot.value = result.data.room
        }
        return result
    }

    private suspend fun publishSuggestionsResponse(
        lease: RoomBinding,
        result: ApiResult<SuggestionsResponse>,
    ): ApiResult<SuggestionsResponse> {
        if (result !is ApiResult.Success) return result
        stateMutex.withLock {
            if (isCurrentLocked(lease)) applySuggestions(result.data.suggestions, fromBroadcast = false)
        }
        return result
    }

    private fun invalidRoomResponse(): ApiResult.Error =
        ApiResult.Error(502, "invalid_room_response", "The room response was missing or mismatched.")

    private fun missingRoom(): ApiResult.Error =
        ApiResult.Error(409, "no_active_room", "No active Watch Together room.")

    private fun missingAuthScope(): ApiResult.Error =
        ApiResult.Error(401, "missing_auth_scope", "No authenticated Watch Together scope.")

    private fun obsoleteRoomRequest(): ApiResult.Error =
        ApiResult.Error(409, "obsolete_room_request", "The Watch Together identity changed.")

    /**
     * Publish suggestions, re-merging `voted_by_me` from the local [votedIds]
     * set. For REST results we also seed [votedIds] from authoritative
     * voted_by_me; broadcasts force false, so we only OR the local set in.
     */
    private fun applySuggestions(list: List<Suggestion>, fromBroadcast: Boolean) {
        if (!fromBroadcast) {
            list.forEach { if (it.votedByMe) votedIds.add(it.id) }
        }
        _suggestions.value = list.map { s ->
            if (s.id in votedIds) s.copy(votedByMe = true) else s
        }
    }

    // ---- WS: client→server send passthroughs ----------------------------------

    private suspend fun currentWritableRealtime(): WatchTogetherRealtimeClient? =
        stateMutex.withLock {
            val current = binding
            realtime.takeIf {
                current != null &&
                    terminalGeneration != current.generation &&
                    realtimeGeneration == current.generation
            }
        }

    suspend fun attachSession(sessionId: String): Boolean =
        currentWritableRealtime()?.attachSession(sessionId) ?: false

    suspend fun transportRequest(
        action: String,
        positionSeconds: Double?,
        isPaused: Boolean,
    ): Boolean = currentWritableRealtime()?.transportRequest(action, positionSeconds, isPaused) ?: false

    suspend fun stateReport(
        sessionId: String,
        positionSeconds: Double,
        isPaused: Boolean,
    ): Boolean = currentWritableRealtime()?.stateReport(sessionId, positionSeconds, isPaused) ?: false

    suspend fun ready(
        sessionId: String,
        positionSeconds: Double,
        isPaused: Boolean,
    ): Boolean = currentWritableRealtime()?.ready(sessionId, positionSeconds, isPaused) ?: false

    suspend fun buffering(
        sessionId: String,
        positionSeconds: Double,
        isPaused: Boolean,
    ): Boolean = currentWritableRealtime()?.buffering(sessionId, positionSeconds, isPaused) ?: false

    suspend fun ping(clientSentAt: String): Boolean =
        currentWritableRealtime()?.ping(clientSentAt) ?: false

    // ---- WS lifecycle: connect + reconnect-with-backoff ------------------------

    /**
     * Collect the room socket with capped-backoff reconnect, folding each event
     * into the state flows. Suspends until the caller's scope is cancelled OR a
     * server `room_closed` arrives (which stops reconnecting). Backoff steps are
     * [BACKOFF_MS]; a healthy event resets the index.
     */
    suspend fun connect(roomId: String) {
        val lease = activeBinding()?.takeIf { it.roomId == roomId } ?: return
        val client = realtimeFactory() ?: return
        stateMutex.withLock {
            if (!isCurrentLocked(lease)) return
            realtime = client
            realtimeGeneration = lease.generation
            _roomClosedReason.value = null
        }
        var backoffIndex = 0
        var failures = 0
        while (true) {
            var closedByServer = false
            var openedAtMs: Long? = null
            var sawSnapshot = false
            try {
                client.connect(lease.roomId, lease.roomToken).collect { event ->
                    if (!isCurrent(lease)) throw ObsoleteBinding
                    if (event is RoomRealtimeEvent.Closed) {
                        // Any server-initiated close (with or without a reason) is terminal.
                        // The event flow (a hot SharedFlow) never completes on its
                        // own, so we stop collecting by throwing a private sentinel.
                        closedByServer = true
                        stateMutex.withLock {
                            if (isCurrentLocked(lease)) {
                                terminalGeneration = lease.generation
                                _roomClosedReason.value = event.reason
                                _roomSnapshot.value = null
                            }
                        }
                        throw ServerClosed
                    } else if (event is RoomRealtimeEvent.TransportTerminated) {
                        markNotWritable(lease)
                        throw TransportEnded
                    } else if (event is RoomRealtimeEvent.Opened) {
                        openedAtMs = monotonicNowMs()
                        markOpened(lease)
                    } else if (event is RoomRealtimeEvent.SnapshotEvent) {
                        sawSnapshot = true
                    }
                    fold(lease, event)
                }
                markNotWritable(lease)
                if (isStableAttempt(openedAtMs, sawSnapshot)) {
                    failures = 0
                    backoffIndex = 0
                }
                failures++
            } catch (e: CancellationException) {
                clearRealtimeIfCurrent(lease, client)
                throw e
            } catch (_: ObsoleteBinding) {
                break
            } catch (_: ServerClosed) {
                // terminal — handled below via closedByServer
            } catch (_: Throwable) {
                // Any throw (including from a flapping server) counts as a failure,
                // regardless of whether a healthy event arrived in the same attempt.
                if (isStableAttempt(openedAtMs, sawSnapshot)) {
                    failures = 0
                    backoffIndex = 0
                }
                failures++
            }
            if (closedByServer) break
            if (!isCurrent(lease)) break
            if (failures >= MAX_RECONNECT_ATTEMPTS) {
                stateMutex.withLock {
                    if (isCurrentLocked(lease)) {
                        terminalGeneration = lease.generation
                        _roomClosedReason.value = "connection_lost"
                        _roomSnapshot.value = null
                    }
                }
                break
            }
            delay(BACKOFF_MS[backoffIndex])
            backoffIndex = (backoffIndex + 1).coerceAtMost(BACKOFF_MS.lastIndex)
        }
        clearRealtimeIfCurrent(lease, client)
    }

    private suspend fun isCurrent(lease: RoomBinding): Boolean =
        stateMutex.withLock { isCurrentLocked(lease) }

    private suspend fun clearRealtimeIfCurrent(
        lease: RoomBinding,
        client: WatchTogetherRealtimeClient,
    ) {
        stateMutex.withLock {
            if (realtime === client && realtimeGeneration == lease.generation) {
                realtime = null
                realtimeGeneration = null
                _connectionState.value = _connectionState.value.copy(writable = false)
            }
        }
    }

    private suspend fun markOpened(lease: RoomBinding) {
        stateMutex.withLock {
            if (isCurrentLocked(lease)) {
                val previous = _connectionState.value
                _connectionState.value = WatchTogetherConnectionState(
                    generation = lease.generation,
                    epoch = if (previous.generation == lease.generation) previous.epoch + 1 else 1,
                    writable = true,
                )
            }
        }
    }

    private suspend fun markNotWritable(lease: RoomBinding) {
        stateMutex.withLock {
            if (binding == lease) {
                _connectionState.value = _connectionState.value.copy(writable = false)
            }
        }
    }

    private fun isStableAttempt(openedAtMs: Long?, sawSnapshot: Boolean): Boolean =
        sawSnapshot && openedAtMs != null && monotonicNowMs() - openedAtMs >= STABLE_CONNECTION_MS

    /** Sentinel to unwind the [connect] collect loop on a server `room_closed`. */
    private object ServerClosed : Throwable()
    private object TransportEnded : Throwable()
    private object ObsoleteBinding : Throwable()

    /** Pure-ish fold of one realtime event into the state flows + side streams. */
    private suspend fun fold(lease: RoomBinding, event: RoomRealtimeEvent) {
        stateMutex.withLock {
            if (!isCurrentLocked(lease)) return
            when (event) {
                RoomRealtimeEvent.Opened -> Unit
                is RoomRealtimeEvent.SnapshotEvent -> _roomSnapshot.value = event.room
                is RoomRealtimeEvent.SuggestionsEvent -> applySuggestions(event.suggestions, fromBroadcast = true)
                is RoomRealtimeEvent.TransportCommandEvent -> _transportCommands.tryEmit(
                    ScheduledTransportCommand(
                        command = event.command,
                        executeAtMs = parseRfc3339ToEpochMillis(event.command.executeAt),
                    ),
                )
                is RoomRealtimeEvent.Pong -> _pongs.tryEmit(
                    PongSample(
                        clientSentMs = parseRfc3339ToEpochMillis(event.clientSentAt),
                        serverReceivedMs = parseRfc3339ToEpochMillis(event.serverReceivedAt),
                        serverSentMs = parseRfc3339ToEpochMillis(event.serverSentAt),
                    ),
                )
                is RoomRealtimeEvent.Closed -> { /* lifecycle handled in connect() */ }
                is RoomRealtimeEvent.TransportTerminated -> Unit
                is RoomRealtimeEvent.Error ->
                    // Transient, NON-terminal: a server `error` frame (e.g. a rejected
                    // transport_request) must NOT feed roomClosedReason.
                    _errors.tryEmit(event.message.ifBlank { event.code })
            }
        }
    }

    /** Clear all room state on leave. The connect() loop ends via scope cancellation. */
    suspend fun reset() {
        stateMutex.withLock {
            nextGeneration++
            latestRoomRequest++
            binding = null
            terminalGeneration = null
            _roomSnapshot.value = null
            _suggestions.value = emptyList()
            _roomClosedReason.value = null
            votedIds.clear()
            realtime = null
            realtimeGeneration = null
            _connectionState.value = WatchTogetherConnectionState(generation = nextGeneration)
        }
    }

    companion object {
        /** Reconnect backoff steps (ms) — spec: not after room_closed. */
        val BACKOFF_MS = longArrayOf(500L, 1_000L, 2_000L, 5_000L)

        /**
         * Maximum number of consecutive connection failures (e.g. throws during
         * [connect] collection, factory/handshake errors) before the reconnect
         * loop gives up. Reset to zero on any healthy server event.
         */
        const val MAX_RECONNECT_ATTEMPTS = 6
        const val STABLE_CONNECTION_MS = 30_000L
        private val MONOTONIC_ORIGIN = TimeSource.Monotonic.markNow()
    }
}
