package com.continuum.app.repository

import com.continuum.app.model.notifications.NotificationCapability
import com.continuum.app.model.notifications.NotificationListResponse
import com.continuum.app.model.notifications.NotificationPreferences
import com.continuum.app.model.notifications.NotificationPreferencesUpdate
import com.continuum.app.model.notifications.NotificationRow
import com.continuum.app.model.notifications.NotificationSyncResponse
import com.continuum.app.model.notifications.UnreadCountResponse
import com.continuum.app.model.notifications.WsTicketResponse
import com.continuum.app.network.ApiResult
import com.continuum.app.network.NotificationRealtimeEvent
import com.continuum.app.network.api.NotificationsApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationsRepositoryTest {

    private fun row(
        id: String,
        createdAt: String,
        readAt: String? = null,
    ) = NotificationRow(
        id = id,
        rawType = "episode.available",
        profileId = "p",
        reasonFlags = JsonObject(emptyMap()),
        createdAt = createdAt,
        readAt = readAt,
    )

    // ---- pure recomputeUnread -------------------------------------------------

    @Test
    fun `recomputeUnread counts only unread rows`() {
        val rows = listOf(
            row("a", "2026-06-12T09:00:00Z"),
            row("b", "2026-06-12T08:00:00Z", readAt = "2026-06-12T09:30:00Z"),
            row("c", "2026-06-12T07:00:00Z"),
        )
        assertEquals(2, recomputeUnread(rows))
    }

    // ---- pure applyEvent fold -------------------------------------------------

    @Test
    fun `created prepends row and increments unread`() {
        val state = NotificationsState(
            rows = listOf(row("a", "2026-06-12T09:00:00Z")),
            unreadCount = 1,
        )
        val next = applyEvent(
            state,
            NotificationRealtimeEvent.Created(row("b", "2026-06-12T09:05:00Z")),
        )
        assertEquals(listOf("b", "a"), next.rows.map { it.id })
        assertEquals(2, next.unreadCount)
    }

    @Test
    fun `created dedupes an already-present id without double counting`() {
        val state = NotificationsState(
            rows = listOf(row("a", "2026-06-12T09:00:00Z")),
            unreadCount = 1,
        )
        val next = applyEvent(
            state,
            NotificationRealtimeEvent.Created(row("a", "2026-06-12T09:00:00Z")),
        )
        assertEquals(listOf("a"), next.rows.map { it.id })
        assertEquals(1, next.unreadCount)
    }

    @Test
    fun `read flips read_at, decrements unread, and is idempotent`() {
        val state = NotificationsState(
            rows = listOf(
                row("a", "2026-06-12T09:00:00Z"),
                row("b", "2026-06-12T08:00:00Z"),
            ),
            unreadCount = 2,
        )
        val once = applyEvent(state, NotificationRealtimeEvent.Read("a"))
        assertTrue(once.rows.first { it.id == "a" }.isRead)
        assertEquals(1, once.unreadCount)

        // Idempotent: reading an already-read row does not go negative.
        val twice = applyEvent(once, NotificationRealtimeEvent.Read("a"))
        assertEquals(1, twice.unreadCount)
    }

    @Test
    fun `read for unknown id is a no-op`() {
        val state = NotificationsState(
            rows = listOf(row("a", "2026-06-12T09:00:00Z")),
            unreadCount = 1,
        )
        val next = applyEvent(state, NotificationRealtimeEvent.Read("zzz"))
        assertEquals(state, next)
    }

    @Test
    fun `readAll marks every row read and zeroes unread`() {
        val state = NotificationsState(
            rows = listOf(
                row("a", "2026-06-12T09:00:00Z"),
                row("b", "2026-06-12T08:00:00Z", readAt = "2026-06-12T09:30:00Z"),
            ),
            unreadCount = 1,
        )
        val next = applyEvent(state, NotificationRealtimeEvent.ReadAll)
        assertTrue(next.rows.all { it.isRead })
        assertEquals(0, next.unreadCount)
    }

    @Test
    fun `snapshot merges by id newest-first and recomputes unread`() {
        val state = NotificationsState(
            rows = listOf(
                row("a", "2026-06-12T09:00:00Z"),
                row("old", "2026-06-12T06:00:00Z", readAt = "2026-06-12T07:00:00Z"),
            ),
            unreadCount = 1,
        )
        // snapshot brings a new unread "b" plus an updated "a" (now read).
        val next = applyEvent(
            state,
            NotificationRealtimeEvent.Snapshot(
                listOf(
                    row("b", "2026-06-12T09:10:00Z"),
                    row("a", "2026-06-12T09:00:00Z", readAt = "2026-06-12T09:20:00Z"),
                ),
            ),
        )
        // dedupe by id, snapshot copy of "a" wins, sorted newest-first.
        assertEquals(listOf("b", "a", "old"), next.rows.map { it.id })
        assertEquals(1, next.rows.count { it.id == "a" })
        assertTrue(next.rows.first { it.id == "a" }.isRead) // snapshot copy (read) won
        assertEquals(1, next.unreadCount) // only "b" unread
    }

    @Test
    fun `closed event leaves state unchanged`() {
        val state = NotificationsState(
            rows = listOf(row("a", "2026-06-12T09:00:00Z")),
            unreadCount = 1,
        )
        assertEquals(state, applyEvent(state, NotificationRealtimeEvent.Closed("x")))
    }

    // ---- repository over a fake API ------------------------------------------

    private class FakeNotificationsApi : NotificationsApi {
        var listResponse: ApiResult<NotificationListResponse> =
            ApiResult.Success(NotificationListResponse())
        var unreadResponse: ApiResult<UnreadCountResponse> =
            ApiResult.Success(UnreadCountResponse(0))
        var markReadResult: ApiResult<Unit> = ApiResult.Success(Unit)
        var markAllReadResult: ApiResult<Unit> = ApiResult.Success(Unit)
        var prefsResponse: ApiResult<NotificationPreferences> =
            ApiResult.Success(NotificationPreferences())
        var capabilityResponse: ApiResult<NotificationCapability> =
            ApiResult.Success(NotificationCapability())
        var markReadCalls = mutableListOf<String>()
        var markAllReadCalls = 0

        override suspend fun list(limit: Int, unreadOnly: Boolean, before: String?) = listResponse
        override suspend fun sync(since: String?, limit: Int) =
            ApiResult.Success(NotificationSyncResponse())
        override suspend fun get(id: String) = ApiResult.Success(
            NotificationRow(id, "episode.available", "p", createdAt = ""),
        )
        override suspend fun unreadCount() = unreadResponse
        override suspend fun markRead(id: String): ApiResult<Unit> {
            markReadCalls += id
            return markReadResult
        }
        override suspend fun markAllRead(): ApiResult<Unit> {
            markAllReadCalls++
            return markAllReadResult
        }
        override suspend fun getPreferences() = prefsResponse
        override suspend fun updatePreferences(update: NotificationPreferencesUpdate) = prefsResponse
        override suspend fun capability() = capabilityResponse
        override suspend fun wsTicket() = ApiResult.Success(WsTicketResponse("t", 30))
    }

    @Test
    fun `refresh loads unread count and first page`() = kotlinx.coroutines.test.runTest {
        val api = FakeNotificationsApi().apply {
            unreadResponse = ApiResult.Success(UnreadCountResponse(2))
            listResponse = ApiResult.Success(
                NotificationListResponse(
                    notifications = listOf(
                        row("a", "2026-06-12T09:00:00Z"),
                        row("b", "2026-06-12T08:00:00Z", readAt = "2026-06-12T09:30:00Z"),
                    ),
                    nextCursor = "cur-1",
                ),
            )
        }
        val repo = NotificationsRepository(api)
        repo.refresh()
        assertEquals(2, repo.unreadCount.value)
        assertEquals(listOf("a", "b"), repo.rows.value.map { it.id })
        assertEquals("cur-1", repo.nextCursor.value)
    }

    @Test
    fun `loadMore appends the next page and dedupes`() = kotlinx.coroutines.test.runTest {
        val api = FakeNotificationsApi().apply {
            listResponse = ApiResult.Success(
                NotificationListResponse(
                    notifications = listOf(row("a", "2026-06-12T09:00:00Z")),
                    nextCursor = "cur-1",
                ),
            )
        }
        val repo = NotificationsRepository(api)
        repo.refresh()
        api.listResponse = ApiResult.Success(
            NotificationListResponse(
                notifications = listOf(
                    row("a", "2026-06-12T09:00:00Z"), // duplicate of page 1
                    row("z", "2026-06-12T07:00:00Z"),
                ),
                nextCursor = null,
            ),
        )
        repo.loadMore("cur-1")
        assertEquals(listOf("a", "z"), repo.rows.value.map { it.id })
        assertEquals(null, repo.nextCursor.value)
    }

    @Test
    fun `markRead is optimistic and reverts on failure`() = kotlinx.coroutines.test.runTest {
        val api = FakeNotificationsApi().apply {
            listResponse = ApiResult.Success(
                NotificationListResponse(notifications = listOf(row("a", "2026-06-12T09:00:00Z"))),
            )
            unreadResponse = ApiResult.Success(UnreadCountResponse(1))
        }
        val repo = NotificationsRepository(api)
        repo.refresh()
        assertEquals(1, repo.unreadCount.value)

        api.markReadResult = ApiResult.Error(500, "internal_error", "boom")
        repo.markRead("a")
        // reverted
        assertEquals(1, repo.unreadCount.value)
        assertFalse(repo.rows.value.first { it.id == "a" }.isRead)
        assertEquals(listOf("a"), api.markReadCalls)
    }

    @Test
    fun `connectRealtime folds a created event into the state flows`() = kotlinx.coroutines.test.runTest {
        val api = FakeNotificationsApi()
        val events = kotlinx.coroutines.flow.MutableSharedFlow<NotificationRealtimeEvent>(
            replay = 0, extraBufferCapacity = 8,
        )
        val realtime = object : com.continuum.app.network.NotificationsRealtimeClient {
            override fun connect(): kotlinx.coroutines.flow.Flow<NotificationRealtimeEvent> = events
        }
        val repo = NotificationsRepository(api, realtimeFactory = { realtime })
        val job = repo.connectRealtime(this)
        // Let the launched collector subscribe before we emit (replay=0 drops
        // emissions made before a subscriber is present).
        events.subscriptionCount.first { it > 0 }
        events.emit(NotificationRealtimeEvent.Created(row("live", "2026-06-12T10:00:00Z")))
        kotlinx.coroutines.yield()
        assertEquals(listOf("live"), repo.rows.value.map { it.id })
        assertEquals(1, repo.unreadCount.value)
        job.cancel()
    }

    @Test
    fun `reset clears rows unread and cursor on profile switch`() = kotlinx.coroutines.test.runTest {
        val api = FakeNotificationsApi().apply {
            listResponse = ApiResult.Success(
                NotificationListResponse(
                    notifications = listOf(row("a", "2026-06-12T09:00:00Z")),
                    nextCursor = "cur-1",
                ),
            )
            unreadResponse = ApiResult.Success(UnreadCountResponse(1))
        }
        val repo = NotificationsRepository(api)
        repo.refresh()
        repo.reset()
        assertTrue(repo.rows.value.isEmpty())
        assertEquals(0, repo.unreadCount.value)
        assertEquals(null, repo.nextCursor.value)
    }

    @Test
    fun `reset emits on resetSignals so foreground starter can reconnect`() = kotlinx.coroutines.test.runTest {
        val repo = NotificationsRepository(FakeNotificationsApi())
        val signals = mutableListOf<Unit>()
        val collector = launch { repo.resetSignals.collect { signals += it } }
        // Let the collector subscribe before emitting (replay=0 drops emissions
        // made before a subscriber is present).
        kotlinx.coroutines.yield()

        repo.reset()
        kotlinx.coroutines.yield()
        assertEquals(1, signals.size)

        repo.reset()
        kotlinx.coroutines.yield()
        assertEquals(2, signals.size)

        collector.cancel()
    }

    @Test
    fun `reset is synchronous even with no subscriber`() {
        // tryEmit into a buffered DROP_OLDEST flow never suspends or throws,
        // so reset() stays callable from the synchronous selectProfile path.
        val repo = NotificationsRepository(FakeNotificationsApi())
        repo.reset()
        assertTrue(repo.rows.value.isEmpty())
    }
}
