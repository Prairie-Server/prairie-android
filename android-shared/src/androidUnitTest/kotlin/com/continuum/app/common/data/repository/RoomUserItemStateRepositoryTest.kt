package com.continuum.app.common.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.continuum.app.common.data.db.SiloDatabase
import com.continuum.app.common.data.sync.OutboxOperation
import com.continuum.app.network.AuthScopeSnapshot
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
        snapshotProvider = { AuthScopeSnapshot("s1", "p1", "https://s1.example", "pt") },
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

        val op = db.dirtyOperationDao().dueBatch("s1", "p1", nowMs = 2000L, limit = 10).single()
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
        val op = db.dirtyOperationDao().dueBatch("s1", "p1", nowMs = 2000L, limit = 10).single()
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
            snapshotProvider = { null },
            now = { 1000L },
            idGenerator = { "id-x" },
        )
        val handle = scopeless.recordWatched("c1", watched = true)
        assertEquals(OutboxHandle.NONE, handle)
        assertEquals(0, db.dirtyOperationDao().count())
    }

    @Test
    fun recordPositionWritesFileProjectionAndContentCoalescedOp() = runTest {
        repo.recordPosition("c1", fileId = 7, positionSeconds = 123.0, durationSeconds = 3600.0)

        // File-level local projection for resume.
        val row = db.userItemStateDao().get("s1", "p1", "c1", 7)
        assertEquals(123.0, row?.positionSeconds)
        assertEquals(3600.0, row?.durationSeconds)

        // Single content-level position op (coalesce key omits fileId).
        val op = db.dirtyOperationDao().dueBatch("s1", "p1", nowMs = 2000L, limit = 10).single()
        assertEquals(OutboxOperation.SET_POSITION, op.opKind)
        assertEquals("s1|p1|c1|${OutboxOperation.SET_POSITION}", op.coalesceKey)
        assertEquals(7, op.targetFileId)
    }

    @Test
    fun recordPositionCoalescesToLatestPerContent() = runTest {
        repo.recordPosition("c1", fileId = 7, positionSeconds = 10.0, durationSeconds = 3600.0)
        repo.recordPosition("c1", fileId = 7, positionSeconds = 99.0, durationSeconds = 3600.0)
        assertEquals(1, db.dirtyOperationDao().count())
        val op = db.dirtyOperationDao().dueBatch("s1", "p1", nowMs = 2000L, limit = 10).single()
        assertEquals(99.0, OutboxOperation.decodePositionPayload(op.payloadJson).first)
    }

    @Test
    fun localPositionReadsBackTheRecordedResumePoint() = runTest {
        assertNull(repo.localPosition("c1", fileId = 7))
        repo.recordPosition("c1", fileId = 7, positionSeconds = 456.0, durationSeconds = 3600.0)
        assertEquals(456.0, repo.localPosition("c1", fileId = 7))
    }

    @Test
    fun recordEbookProgressWritesProjectionOpAndReadsBack() = runTest {
        repo.recordEbookProgress("c1", fileId = 7, location = "epubcfi(/6/4!/4)", progress = 0.42)
        val back = repo.localEbookProgress("c1", fileId = 7)
        assertEquals("epubcfi(/6/4!/4)", back?.location)
        assertEquals(0.42, back?.progress)
        val op = db.dirtyOperationDao().dueBatch("s1", "p1", nowMs = 2000L, limit = 10).single()
        assertEquals(OutboxOperation.SET_EBOOK_PROGRESS, op.opKind)
        assertEquals("s1|p1|c1|${OutboxOperation.SET_EBOOK_PROGRESS}", op.coalesceKey)
    }

    @Test
    fun recordPositionRejectsNonFinitePosition() = runTest {
        repo.recordPosition("c1", fileId = 7, positionSeconds = Double.NaN, durationSeconds = 3600.0)
        repo.recordPosition("c1", fileId = 7, positionSeconds = -5.0, durationSeconds = 3600.0)
        // No projection row, no outbox op — invalid values can't poison the drain.
        assertNull(db.userItemStateDao().get("s1", "p1", "c1", 7))
        assertEquals(0, db.dirtyOperationDao().count())
    }

    @Test
    fun handleCarriesScopeForInlinePinning() = runTest {
        val handle = repo.recordFavorite("c1", favorite = true)
        assertEquals("s1", handle.scope?.serverId)
        assertEquals("p1", handle.scope?.profileId)
        assertEquals("https://s1.example", handle.scope?.serverUrl)
    }
}
