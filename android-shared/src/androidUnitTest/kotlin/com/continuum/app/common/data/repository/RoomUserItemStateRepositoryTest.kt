package com.continuum.app.common.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.continuum.app.common.data.db.SiloDatabase
import com.continuum.app.common.data.sync.OutboxOperation
import com.continuum.app.repository.port.OutboxHandle
import com.continuum.app.repository.port.WriteOutcome
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RoomUserItemStateRepositoryTest {

    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        SiloDatabase::class.java,
    ).allowMainThreadQueries().build()

    private var nextId = 0
    private val repo = RoomUserItemStateRepository(
        db = db,
        currentServerId = { "s1" },
        currentProfileId = { "p1" },
        now = { 1000L },
        idGenerator = { "id-${nextId++}" },
    )

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun recordWatchedWritesProjectionAndContentScopedOutboxOp() = runTest {
        val handle = repo.recordWatched("c1", watched = true)
        assertTrue(handle.opId >= 0)

        assertEquals(true, db.contentItemStateDao().get("s1", "p1", "c1")?.watched)

        val op = db.dirtyOperationDao().dueBatch(nowMs = 2000L, limit = 10).single()
        assertEquals(OutboxOperation.SET_WATCHED, op.opKind)
        assertNull(op.targetFileId)
        assertEquals("s1|p1|c1|${OutboxOperation.SET_WATCHED}", op.coalesceKey)
    }

    @Test
    fun favoriteToggleDoesNotClobberExistingRating() = runTest {
        repo.recordRating("c1", rating = 5)
        repo.recordFavorite("c1", favorite = true)
        val row = db.contentItemStateDao().get("s1", "p1", "c1")
        assertEquals(5, row?.ratingValue)
        assertEquals(true, row?.favorite)
        // Distinct kinds do not coalesce against each other.
        assertEquals(2, db.dirtyOperationDao().count())
    }

    @Test
    fun repeatedSameKindCoalescesToLatest() = runTest {
        repo.recordWatched("c1", watched = true)
        repo.recordWatched("c1", watched = false)
        assertEquals(1, db.dirtyOperationDao().count())
        val op = db.dirtyOperationDao().dueBatch(nowMs = 2000L, limit = 10).single()
        assertEquals(false, OutboxOperation.decodeBooleanPayload(op.payloadJson))
    }

    @Test
    fun resolveSyncedDeletesOp() = runTest {
        val handle = repo.recordFavorite("c1", favorite = true)
        repo.resolve(handle, WriteOutcome.SYNCED)
        assertEquals(0, db.dirtyOperationDao().count())
    }

    @Test
    fun resolveTerminalDropsOp() = runTest {
        val handle = repo.recordFavorite("c1", favorite = true)
        repo.resolve(handle, WriteOutcome.TERMINAL)
        assertEquals(0, db.dirtyOperationDao().count())
    }

    @Test
    fun resolveRetriableKeepsOpPending() = runTest {
        val handle = repo.recordFavorite("c1", favorite = true)
        repo.resolve(handle, WriteOutcome.RETRIABLE)
        assertEquals(1, db.dirtyOperationDao().count())
    }

    @Test
    fun missingScopeRecordsNothing() = runTest {
        val scopeless = RoomUserItemStateRepository(
            db = db,
            currentServerId = { null },
            currentProfileId = { "p1" },
            now = { 1000L },
            idGenerator = { "id-x" },
        )
        val handle = scopeless.recordWatched("c1", watched = true)
        assertEquals(OutboxHandle.NONE, handle)
        assertEquals(0, db.dirtyOperationDao().count())
    }
}
