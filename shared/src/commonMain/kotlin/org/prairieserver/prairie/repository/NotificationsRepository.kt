package org.prairieserver.prairie.repository

import org.prairieserver.prairie.model.notifications.NotificationCapability
import org.prairieserver.prairie.model.notifications.NotificationPreferences
import org.prairieserver.prairie.model.notifications.NotificationPreferencesUpdate
import org.prairieserver.prairie.model.notifications.NotificationRow
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.NotificationRealtimeEvent
import org.prairieserver.prairie.network.NotificationsRealtimeClient
import org.prairieserver.prairie.network.api.NotificationsApi
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
import kotlinx.coroutines.flow.updateAndGet
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
 * Sentinel written to [org.prairieserver.prairie.model.notifications.NotificationRow.readAt] when the
 * row's [org.prairieserver.prairie.model.notifications.NotificationRow.createdAt] is blank.
 * [org.prairieserver.prairie.model.notifications.NotificationRow.isRead] checks `!readAt.isNullOrBlank()`,
 * so any non-blank value satisfies the predicate. Without this, rows with a blank createdAt could
 * never be marked read because copying blank into readAt leaves it blank.
 */
private const val READ_SENTINEL = "1970-01-01T00:00:00Z"

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
                    if (it.id == event.id && !it.isRead) it.copy(readAt = it.createdAt.ifBlank { READ_SENTINEL }) else it
                }
                state.copy(rows = rows, unreadCount = recomputeUnread(rows))
            }
        }
        NotificationRealtimeEvent.ReadAll -> {
            val rows = state.rows.map { if (it.isRead) it else it.copy(readAt = it.createdAt.ifBlank { READ_SENTINEL }) }
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
 * Returns true when this [NotificationRealtimeEvent.Closed] event carries an auth-class
 * reason (HTTP 401, 403, or a reason string containing "auth"). Non-[Closed] events
 * always return false. Marked [internal] so tests in the same module can exercise it
 * directly.
 */
internal fun NotificationRealtimeEvent.isAuthClose(): Boolean =
    this is NotificationRealtimeEvent.Closed &&
        (reason?.contains("401") == true || reason?.contains("403") == true ||
            reason?.contains("auth", ignoreCase = true) == true)

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

    /** True once [connectRealtime] gives up permanently due to an auth-class [NotificationRealtimeEvent.Closed]. */
    private val _realtimeFatal = MutableStateFlow(false)

    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()
    val rows: StateFlow<List<NotificationRow>> = _rows.asStateFlow()
    val nextCursor: StateFlow<String?> = _nextCursor.asStateFlow()
    val preferences: StateFlow<NotificationPreferences?> = _preferences.asStateFlow()
    val capability: StateFlow<NotificationCapability?> = _capability.asStateFlow()

    /**
     * Becomes true if [connectRealtime] receives an auth-class [NotificationRealtimeEvent.Closed]
     * (401/403/auth reason). When true the realtime loop has permanently stopped; callers should
     * surface an appropriate error state rather than attempting to reconnect.
     */
    val realtimeFatal: StateFlow<Boolean> = _realtimeFatal.asStateFlow()

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

    /**
     * Atomically transforms [_state] via [MutableStateFlow.updateAndGet], then
     * propagates the result to the derived flows. All forward folds (realtime
     * events, loadMore, markRead/markAllRead) funnel through here so that
     * concurrent callers cannot interleave a stale read with a subsequent write.
     */
    private fun mutate(transform: (NotificationsState) -> NotificationsState) {
        val next = _state.updateAndGet(transform)
        _rows.value = next.rows
        _unreadCount.value = next.unreadCount
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
                val incoming = r.data.notifications
                mutate { current ->
                    val merged = (current.rows + incoming).dedupeById()
                    current.copy(rows = merged, unreadCount = recomputeUnread(merged))
                }
                _nextCursor.value = r.data.nextCursor
            }
            else -> { /* surfaced by the caller */ }
        }
    }

    /** Direct row lookup used by data-only push handling before rendering a system notification. */
    suspend fun get(id: String): ApiResult<NotificationRow> =
        api.get(id)

    /** Optimistic mark-read with revert on failure. */
    suspend fun markRead(id: String) {
        val before = _state.value
        mutate { applyEvent(it, NotificationRealtimeEvent.Read(id)) }
        val r = api.markRead(id)
        if (r !is ApiResult.Success) publish(before)
    }

    /** Optimistic mark-all-read with revert on failure. */
    suspend fun markAllRead() {
        val before = _state.value
        mutate { applyEvent(it, NotificationRealtimeEvent.ReadAll) }
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
            var established = false // true once the first non-Closed event of this connection arrives
            var authFailed = false  // set inside collect, checked after to break the loop
            try {
                client.connect().collect { event ->
                    // Auth-class closes are terminal — stop reconnecting.
                    if (event.isAuthClose()) {
                        _realtimeFatal.value = true
                        authFailed = true
                        return@collect
                    }
                    if (!established && event !is NotificationRealtimeEvent.Closed) {
                        // First healthy event: the connection is up — reset backoff so a
                        // healthy-then-clean-close cycle does not escalate the delay.
                        backoffMs = INITIAL_BACKOFF_MS
                        established = true
                    }
                    mutate { applyEvent(it, event) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // fall through to backoff-reconnect
            }
            if (authFailed) return@launch
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    // ---- test seams (internal so tests can exercise mutate paths) ----------------

    /** Seeds state atomically. For tests only. */
    internal fun seedForTest(state: NotificationsState) = mutate { state }

    /** Applies one realtime event via the production mutate path. For tests only. */
    internal fun applyForTest(event: NotificationRealtimeEvent) = mutate { applyEvent(it, event) }

    private companion object {
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
    }
}
