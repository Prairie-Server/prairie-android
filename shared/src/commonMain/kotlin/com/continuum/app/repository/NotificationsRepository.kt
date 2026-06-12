package com.continuum.app.repository

import com.continuum.app.model.notifications.NotificationCapability
import com.continuum.app.model.notifications.NotificationPreferences
import com.continuum.app.model.notifications.NotificationPreferencesUpdate
import com.continuum.app.model.notifications.NotificationRow
import com.continuum.app.network.ApiResult
import com.continuum.app.network.NotificationRealtimeEvent
import com.continuum.app.network.NotificationsRealtimeClient
import com.continuum.app.network.api.NotificationsApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Immutable repository state, folded by the pure [applyEvent]. Newest-first
 * row ordering matches the inbox list API.
 */
data class NotificationsState(
    val rows: List<NotificationRow> = emptyList(),
    val unreadCount: Int = 0,
)

/** Count of unread rows. Pure. */
fun recomputeUnread(rows: List<NotificationRow>): Int = rows.count { !it.isRead }

/** Newest-first by created_at (string ISO-8601 sorts lexicographically). */
private fun List<NotificationRow>.sortedNewestFirst(): List<NotificationRow> =
    sortedByDescending { it.createdAt }

/** Dedupe by id keeping the FIRST occurrence (callers put the winner first). */
private fun List<NotificationRow>.dedupeById(): List<NotificationRow> {
    val seen = HashSet<String>(size)
    return filter { seen.add(it.id) }
}

/**
 * Pure fold of one realtime event into [state]. The repository applies the
 * result to its StateFlows; keeping this pure makes the fold fully unit
 * testable without coroutines.
 *
 *  - [NotificationRealtimeEvent.Created]: prepend (dedupe by id), recompute unread.
 *  - [NotificationRealtimeEvent.Read]: flip read_at on the row, recompute unread.
 *  - [NotificationRealtimeEvent.ReadAll]: mark every row read, unread = 0.
 *  - [NotificationRealtimeEvent.Snapshot]: merge by id (snapshot copy wins),
 *    sort newest-first, recompute unread.
 *  - [NotificationRealtimeEvent.Closed]: no-op (reconnect is the loop's job).
 */
fun applyEvent(state: NotificationsState, event: NotificationRealtimeEvent): NotificationsState =
    when (event) {
        is NotificationRealtimeEvent.Created -> {
            val merged = (listOf(event.row) + state.rows).dedupeById()
            state.copy(rows = merged, unreadCount = recomputeUnread(merged))
        }
        is NotificationRealtimeEvent.Read -> {
            if (state.rows.none { it.id == event.id }) {
                state
            } else {
                val rows = state.rows.map {
                    if (it.id == event.id && !it.isRead) it.copy(readAt = it.createdAt) else it
                }
                state.copy(rows = rows, unreadCount = recomputeUnread(rows))
            }
        }
        NotificationRealtimeEvent.ReadAll -> {
            val rows = state.rows.map { if (it.isRead) it else it.copy(readAt = it.createdAt) }
            state.copy(rows = rows, unreadCount = 0)
        }
        is NotificationRealtimeEvent.Snapshot -> {
            // Snapshot copies win on id collision, so they come first.
            val merged = (event.rows + state.rows).dedupeById().sortedNewestFirst()
            state.copy(rows = merged, unreadCount = recomputeUnread(merged))
        }
        is NotificationRealtimeEvent.Closed -> state
    }

/**
 * Singleton owner of notification inbox state. REST is the source of truth;
 * the realtime client is a foreground accelerator folded into the same flows
 * via [applyEvent]. The repository owns reconnect (capped backoff); the client
 * manages a single connection per [NotificationsRealtimeClient.connect].
 *
 * [realtimeFactory] is injected so tests can supply a fake event flow.
 */
class NotificationsRepository(
    private val api: NotificationsApi,
    private val realtimeFactory: () -> NotificationsRealtimeClient? = { null },
) {
    private val _state = MutableStateFlow(NotificationsState())

    private val _unreadCount = MutableStateFlow(0)
    private val _rows = MutableStateFlow<List<NotificationRow>>(emptyList())
    private val _nextCursor = MutableStateFlow<String?>(null)
    private val _preferences = MutableStateFlow<NotificationPreferences?>(null)
    private val _capability = MutableStateFlow<NotificationCapability?>(null)

    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()
    val rows: StateFlow<List<NotificationRow>> = _rows.asStateFlow()
    val nextCursor: StateFlow<String?> = _nextCursor.asStateFlow()
    val preferences: StateFlow<NotificationPreferences?> = _preferences.asStateFlow()
    val capability: StateFlow<NotificationCapability?> = _capability.asStateFlow()

    // Buffered + drop-oldest so [reset] can emit synchronously (tryEmit never
    // suspends and never fails). Foreground starters collect this WHILE
    // FOREGROUNDED to tear down + recreate the realtime scope (so the socket
    // reconnects with the new profile's ws-ticket) and run a REST [refresh]
    // for the new profile. replay=0: a fresh subscriber should not re-handle a
    // past switch.
    private val _resetSignals = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Emits [Unit] each time [reset] runs (i.e. on a profile switch). The live
     * realtime socket was opened with the previous profile's ws-ticket, so a
     * foregrounded starter must, on each signal, cancel + recreate its realtime
     * scope (reconnecting [connectRealtime] with the new profile's ticket) and
     * call [refresh]. Socket ownership stays in the starter, so this works for
     * both the mobile and TV foreground starters — each collects independently.
     */
    val resetSignals: SharedFlow<Unit> = _resetSignals.asSharedFlow()

    private fun publish(state: NotificationsState) {
        _state.value = state
        _rows.value = state.rows
        _unreadCount.value = state.unreadCount
    }

    /** Foreground / inbox-open refresh: unread count + first page. */
    suspend fun refresh() {
        when (val r = api.unreadCount()) {
            is ApiResult.Success -> _unreadCount.value = r.data.count
            else -> { /* keep last value on failure (spec: badge is silent) */ }
        }
        when (val r = api.list(limit = 25, unreadOnly = false, before = null)) {
            is ApiResult.Success -> {
                val rows = r.data.notifications.dedupeById()
                publish(NotificationsState(rows = rows, unreadCount = _unreadCount.value))
                _nextCursor.value = r.data.nextCursor
            }
            else -> { /* surfaced by the caller via ApiResult */ }
        }
    }

    /** Appends the next page; dedupes by id; updates the cursor. */
    suspend fun loadMore(cursor: String) {
        when (val r = api.list(limit = 25, unreadOnly = false, before = cursor)) {
            is ApiResult.Success -> {
                val merged = (_rows.value + r.data.notifications).dedupeById()
                publish(_state.value.copy(rows = merged, unreadCount = recomputeUnread(merged)))
                _nextCursor.value = r.data.nextCursor
            }
            else -> { /* surfaced by the caller */ }
        }
    }

    /** Optimistic mark-read with revert on failure. */
    suspend fun markRead(id: String) {
        val before = _state.value
        publish(applyEvent(before, NotificationRealtimeEvent.Read(id)))
        val r = api.markRead(id)
        if (r !is ApiResult.Success) publish(before)
    }

    /** Optimistic mark-all-read with revert on failure. */
    suspend fun markAllRead() {
        val before = _state.value
        publish(applyEvent(before, NotificationRealtimeEvent.ReadAll))
        val r = api.markAllRead()
        if (r !is ApiResult.Success) publish(before)
    }

    suspend fun loadPreferences() {
        when (val r = api.getPreferences()) {
            is ApiResult.Success -> _preferences.value = r.data
            else -> { /* hidden settings on failure (spec) */ }
        }
    }

    suspend fun updatePreferences(
        update: NotificationPreferencesUpdate,
    ): ApiResult<NotificationPreferences> {
        val r = api.updatePreferences(update)
        if (r is ApiResult.Success) _preferences.value = r.data
        return r
    }

    suspend fun loadCapability() {
        when (val r = api.capability()) {
            is ApiResult.Success -> _capability.value = r.data
            else -> { /* hidden settings on failure (spec) */ }
        }
    }

    /**
     * Clears all state on profile switch (badge is per-profile) and signals
     * [resetSignals] so a foregrounded starter can reconnect the realtime
     * socket with the new profile's ticket and refresh. Stays synchronous —
     * [tryEmit] into the buffered flow never suspends.
     */
    fun reset() {
        publish(NotificationsState())
        _nextCursor.value = null
        _preferences.value = null
        _capability.value = null
        _resetSignals.tryEmit(Unit)
    }

    /**
     * Collects the realtime client with capped-backoff reconnect, folding each
     * event into the state flows. Returns the [Job] so the lifecycle starter
     * can cancel it when the app backgrounds — the reconnect loop honors scope
     * cancellation. A null factory (no realtime binding) makes this a no-op
     * completed job.
     */
    fun connectRealtime(scope: CoroutineScope): Job = scope.launch {
        val client = realtimeFactory() ?: return@launch
        var backoffMs = INITIAL_BACKOFF_MS
        while (true) {
            try {
                client.connect().collect { event ->
                    if (event !is NotificationRealtimeEvent.Closed) {
                        backoffMs = INITIAL_BACKOFF_MS // healthy traffic resets backoff
                    }
                    publish(applyEvent(_state.value, event))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // fall through to backoff-reconnect
            }
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private companion object {
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
    }
}
