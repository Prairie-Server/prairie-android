package com.continuum.app.repository

import com.continuum.app.RoomSyncEngine
import com.continuum.app.model.watchtogether.AddSuggestionRequest
import com.continuum.app.model.watchtogether.CreateRoomRequest
import com.continuum.app.model.watchtogether.JoinRoomRequest
import com.continuum.app.model.watchtogether.PromoteSuggestionRequest
import com.continuum.app.model.watchtogether.RoomResponse
import com.continuum.app.model.watchtogether.RoomSnapshot
import com.continuum.app.model.watchtogether.SetSelectionRequest
import com.continuum.app.model.watchtogether.Suggestion
import com.continuum.app.model.watchtogether.SuggestionsResponse
import com.continuum.app.model.watchtogether.TransportCommand
import com.continuum.app.model.watchtogether.UpdatePolicyRequest
import com.continuum.app.network.ApiResult
import com.continuum.app.network.RoomRealtimeEvent
import com.continuum.app.network.WatchTogetherRealtimeClient
import com.continuum.app.network.api.WatchTogetherApi
import com.continuum.app.util.parseRfc3339ToEpochMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

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
) {
    private val _roomSnapshot = MutableStateFlow<RoomSnapshot?>(null)
    private val _suggestions = MutableStateFlow<List<Suggestion>>(emptyList())

    val roomSnapshot: StateFlow<RoomSnapshot?> = _roomSnapshot.asStateFlow()
    val suggestions: StateFlow<List<Suggestion>> = _suggestions.asStateFlow()

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
    private var roomToken: String = ""

    @kotlin.concurrent.Volatile
    private var realtime: WatchTogetherRealtimeClient? = null

    // ---- REST: create / join (store the room token) ---------------------------

    suspend fun createRoom(request: CreateRoomRequest): ApiResult<RoomResponse> {
        val r = api.createRoom(request)
        if (r is ApiResult.Success) onRoomResponse(r.data)
        return r
    }

    suspend fun joinRoom(request: JoinRoomRequest): ApiResult<RoomResponse> {
        val r = api.joinRoom(request)
        if (r is ApiResult.Success) onRoomResponse(r.data)
        return r
    }

    private fun onRoomResponse(data: RoomResponse) {
        if (data.roomAccessToken.isNotBlank()) roomToken = data.roomAccessToken
        _roomSnapshot.value = data.room
    }

    // ---- REST: host management ------------------------------------------------

    suspend fun setSelection(request: SetSelectionRequest): ApiResult<RoomResponse> {
        val roomId = _roomSnapshot.value?.roomId ?: ""
        val r = api.setSelection(roomId, roomToken, request)
        if (r is ApiResult.Success) _roomSnapshot.value = r.data.room
        return r
    }

    suspend fun updatePolicy(request: UpdatePolicyRequest): ApiResult<RoomResponse> {
        val roomId = _roomSnapshot.value?.roomId ?: ""
        val r = api.updatePolicy(roomId, roomToken, request)
        if (r is ApiResult.Success) _roomSnapshot.value = r.data.room
        return r
    }

    suspend fun closeRoom(): ApiResult<Unit> {
        val roomId = _roomSnapshot.value?.roomId ?: ""
        return api.closeRoom(roomId, roomToken)
    }

    // ---- REST: suggestions ----------------------------------------------------

    suspend fun refreshSuggestions(): ApiResult<SuggestionsResponse> {
        val roomId = _roomSnapshot.value?.roomId ?: ""
        val r = api.listSuggestions(roomId, roomToken)
        if (r is ApiResult.Success) applySuggestions(r.data.suggestions, fromBroadcast = false)
        return r
    }

    suspend fun addSuggestion(request: AddSuggestionRequest): ApiResult<SuggestionsResponse> {
        val roomId = _roomSnapshot.value?.roomId ?: ""
        val r = api.addSuggestion(roomId, roomToken, request)
        if (r is ApiResult.Success) applySuggestions(r.data.suggestions, fromBroadcast = false)
        return r
    }

    suspend fun deleteSuggestion(suggestionId: String): ApiResult<SuggestionsResponse> {
        val roomId = _roomSnapshot.value?.roomId ?: ""
        val r = api.deleteSuggestion(roomId, roomToken, suggestionId)
        if (r is ApiResult.Success) applySuggestions(r.data.suggestions, fromBroadcast = false)
        return r
    }

    suspend fun vote(suggestionId: String): ApiResult<SuggestionsResponse> {
        val roomId = _roomSnapshot.value?.roomId ?: ""
        val r = api.vote(roomId, roomToken, suggestionId)
        if (r is ApiResult.Success) {
            votedIds.add(suggestionId)
            applySuggestions(r.data.suggestions, fromBroadcast = false)
        }
        return r
    }

    suspend fun unvote(suggestionId: String): ApiResult<SuggestionsResponse> {
        val roomId = _roomSnapshot.value?.roomId ?: ""
        val r = api.unvote(roomId, roomToken, suggestionId)
        if (r is ApiResult.Success) {
            votedIds.remove(suggestionId)
            applySuggestions(r.data.suggestions, fromBroadcast = false)
        }
        return r
    }

    suspend fun promoteSuggestion(request: PromoteSuggestionRequest): ApiResult<RoomResponse> {
        val roomId = _roomSnapshot.value?.roomId ?: ""
        val r = api.promoteSuggestion(roomId, roomToken, request)
        if (r is ApiResult.Success) _roomSnapshot.value = r.data.room
        return r
    }

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

    suspend fun attachSession(sessionId: String) { realtime?.attachSession(sessionId) }
    suspend fun transportRequest(action: String, positionSeconds: Double?, isPaused: Boolean) {
        realtime?.transportRequest(action, positionSeconds, isPaused)
    }
    suspend fun stateReport(sessionId: String, positionSeconds: Double, isPaused: Boolean) {
        realtime?.stateReport(sessionId, positionSeconds, isPaused)
    }
    suspend fun ready(sessionId: String, positionSeconds: Double, isPaused: Boolean) {
        realtime?.ready(sessionId, positionSeconds, isPaused)
    }
    suspend fun buffering(sessionId: String, positionSeconds: Double, isPaused: Boolean) {
        realtime?.buffering(sessionId, positionSeconds, isPaused)
    }
    suspend fun ping(clientSentAt: String) { realtime?.ping(clientSentAt) }

    // ---- WS lifecycle: connect + reconnect-with-backoff ------------------------

    /**
     * Collect the room socket with capped-backoff reconnect, folding each event
     * into the state flows. Suspends until the caller's scope is cancelled OR a
     * server `room_closed` arrives (which stops reconnecting). Backoff steps are
     * [BACKOFF_MS]; a healthy event resets the index.
     */
    suspend fun connect(roomId: String) {
        val client = realtimeFactory() ?: return
        realtime = client
        _roomClosedReason.value = null // fresh connect: clear any stale close/error reason
        var backoffIndex = 0
        var failures = 0
        while (true) {
            var closedByServer = false
            var healthy = false
            try {
                client.connect(roomId, roomToken).collect { event ->
                    if (event is RoomRealtimeEvent.Closed) {
                        // Any server-initiated close (with or without a reason) is terminal.
                        // The event flow (a hot SharedFlow) never completes on its
                        // own, so we stop collecting by throwing a private sentinel.
                        closedByServer = true
                        _roomClosedReason.value = event.reason // surface "host left" etc.
                        _roomSnapshot.value = null
                        throw ServerClosed
                    } else {
                        healthy = true
                        backoffIndex = 0 // healthy traffic resets backoff
                        failures = 0
                    }
                    fold(event)
                }
            } catch (e: CancellationException) {
                realtime = null
                throw e
            } catch (_: ServerClosed) {
                // terminal — handled below via closedByServer
            } catch (_: Throwable) {
                // fall through to backoff-reconnect; count failure if no healthy event arrived
                if (!healthy) failures++
            }
            if (closedByServer) break
            if (failures >= MAX_RECONNECT_ATTEMPTS) {
                _roomClosedReason.value = "connection_lost"
                break
            }
            delay(BACKOFF_MS[backoffIndex])
            backoffIndex = (backoffIndex + 1).coerceAtMost(BACKOFF_MS.lastIndex)
        }
        realtime = null
    }

    /** Sentinel to unwind the [connect] collect loop on a server `room_closed`. */
    private object ServerClosed : Throwable()

    /** Pure-ish fold of one realtime event into the state flows + side streams. */
    private fun fold(event: RoomRealtimeEvent) {
        when (event) {
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
            is RoomRealtimeEvent.Error ->
                // Transient, NON-terminal: a server `error` frame (e.g. a rejected
                // transport_request) must be "ignored gracefully" per the design.
                // It must NOT feed roomClosedReason — that is reserved for the
                // terminal room_closed/ServerClosed path, which the player observes
                // to exit. Folding errors here would eject the user on any transient
                // rejection. Surface to the transient [errors] stream instead.
                _errors.tryEmit(event.message.ifBlank { event.code })
        }
    }

    /** Clear all room state on leave. The connect() loop ends via scope cancellation. */
    fun reset() {
        _roomSnapshot.value = null
        _suggestions.value = emptyList()
        _roomClosedReason.value = null
        votedIds.clear()
        roomToken = ""
        realtime = null
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
    }
}
