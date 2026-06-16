package com.continuum.app.common.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.continuum.app.common.data.db.SiloDatabase
import com.continuum.app.common.data.db.entity.DirtyOperationEntity
import com.continuum.app.network.AuthScopeSnapshot
import com.continuum.app.network.ContinuumJson
import com.continuum.app.network.api.PersonalDataApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {

    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        SiloDatabase::class.java,
    ).allowMainThreadQueries().build()

    private var status = HttpStatusCode.OK
    private var clock = 1_000L

    private val snapshot = AuthScopeSnapshot(
        serverId = "s1",
        profileId = "p1",
        serverUrl = "https://s1.example",
        profileToken = "pt",
    )

    private fun mockClient() = HttpClient(
        MockEngine { respond("{}", status, headersOf(HttpHeaders.ContentType, "application/json")) },
    ) {
        install(ContentNegotiation) { json(ContinuumJson) }
    }

    private val api = PersonalDataApi(mockClient())
    private val ebookApi = com.continuum.app.network.api.EbookReaderApi(mockClient())

    private fun engine(batchLimit: Int = 50) = SyncEngine(
        db = db,
        personalDataApi = api,
        ebookReaderApi = ebookApi,
        snapshotProvider = { snapshot },
        now = { clock },
        batchLimit = batchLimit,
    )

    @AfterTest
    fun tearDown() = db.close()

    private fun op(
        serverId: String = "s1",
        coalesceKey: String = "s1|p1|c1|${OutboxOperation.SET_WATCHED}",
        idempotencyKey: String,
        payload: String = "true",
        opKind: String = OutboxOperation.SET_WATCHED,
        nextAttemptAtMs: Long = 0L,
    ) = DirtyOperationEntity(
        opKind = opKind,
        serverId = serverId,
        profileId = "p1",
        targetContentId = "c1",
        targetFileId = null,
        coalesceKey = coalesceKey,
        idempotencyKey = idempotencyKey,
        payloadJson = payload,
        createdAtMs = 1L,
        nextAttemptAtMs = nextAttemptAtMs,
    )

    @Test
    fun successSyncsAndDeletes() = runTest {
        db.dirtyOperationDao().insert(op(idempotencyKey = "i1"))
        status = HttpStatusCode.OK
        val result = engine().drainOnce()
        assertEquals(1, result.synced)
        assertEquals(0, db.dirtyOperationDao().count())
    }

    @Test
    fun retriableKeepsOpAndBacksOff() = runTest {
        db.dirtyOperationDao().insert(op(idempotencyKey = "i1"))
        status = HttpStatusCode.InternalServerError
        val result = engine().drainOnce()
        assertEquals(1, result.retriable)
        val row = db.dirtyOperationDao().dueBatch("s1", "p1", nowMs = Long.MAX_VALUE, limit = 10).single()
        assertEquals(DirtyOperationEntity.STATE_PENDING, row.state)
        assertEquals(1, row.attemptCount)
        assertTrue(row.nextAttemptAtMs > clock, "backoff should push nextAttemptAtMs into the future")
    }

    @Test
    fun terminalDropsOp() = runTest {
        db.dirtyOperationDao().insert(op(idempotencyKey = "i1"))
        status = HttpStatusCode.Forbidden
        val result = engine().drainOnce()
        assertEquals(1, result.dropped)
        assertEquals(0, db.dirtyOperationDao().count())
    }

    @Test
    fun reclaimsCrashStrandedInFlightThenSyncs() = runTest {
        val dao = db.dirtyOperationDao()
        val id = dao.insert(op(idempotencyKey = "i1"))
        assertEquals(1, dao.claim(id)) // simulate crash mid-send (left in_flight)
        status = HttpStatusCode.OK
        val result = engine().drainOnce()
        assertEquals(1, result.synced)
        assertEquals(0, dao.count())
    }

    @Test
    fun reclaimDropsStrandedInFlightSupersededByNewerPending() = runTest {
        val dao = db.dirtyOperationDao()
        val key = "s1|p1|c1|${OutboxOperation.SET_WATCHED}"
        val stranded = dao.insert(op(coalesceKey = key, idempotencyKey = "i1", payload = "true"))
        assertEquals(1, dao.claim(stranded)) // in_flight
        dao.insert(op(coalesceKey = key, idempotencyKey = "i2", payload = "false")) // newer pending, same key
        status = HttpStatusCode.OK
        engine().drainOnce()
        // Stranded stale op dropped at reclaim; only the newer op synced.
        assertEquals(0, dao.count())
    }

    @Test
    fun drainIsScopedToCurrentServer() = runTest {
        db.dirtyOperationDao().insert(op(serverId = "s2", coalesceKey = "s2|p1|c1|w", idempotencyKey = "i1"))
        status = HttpStatusCode.OK
        val result = engine().drainOnce()
        assertEquals(0, result.synced)
        // The other server's op is untouched (snapshot pins s1/p1).
        assertEquals(1, db.dirtyOperationDao().count())
    }

    @Test
    fun drainsBacklogLargerThanBatchLimitInOneRun() = runTest {
        repeat(5) { i ->
            db.dirtyOperationDao().insert(op(coalesceKey = "s1|p1|c$i|w", idempotencyKey = "i$i"))
        }
        status = HttpStatusCode.OK
        val result = engine(batchLimit = 2).drainOnce()
        assertEquals(5, result.synced)
        assertEquals(0, result.remaining)
        assertEquals(0, db.dirtyOperationDao().count())
    }

    @Test
    fun setPositionDrainsViaSyncProgress() = runTest {
        db.dirtyOperationDao().insert(
            op(
                coalesceKey = "s1|p1|c1|${OutboxOperation.SET_POSITION}",
                idempotencyKey = "i1",
                opKind = OutboxOperation.SET_POSITION,
                payload = OutboxOperation.encodePositionPayload(123.0, 3600.0),
            ),
        )
        status = HttpStatusCode.OK
        val result = engine().drainOnce()
        assertEquals(1, result.synced)
        assertEquals(0, db.dirtyOperationDao().count())
    }

    @Test
    fun noScopeDrainsNothing() = runTest {
        db.dirtyOperationDao().insert(op(idempotencyKey = "i1"))
        val noScopeEngine = SyncEngine(
            db = db,
            personalDataApi = api,
            ebookReaderApi = ebookApi,
            snapshotProvider = { null },
            now = { clock },
        )
        val result = noScopeEngine.drainOnce()
        assertEquals(0, result.synced)
        assertEquals(1, db.dirtyOperationDao().count())
    }

    @Test
    fun ebookProgressDrainsWhenLocalAheadOfServer() = runTest {
        // MockEngine getProgress returns "{}" → server progress 0.0; local 0.5 is
        // ahead → saveProgress runs → 200 → synced.
        db.dirtyOperationDao().insert(
            op(
                coalesceKey = "s1|p1|c1|${OutboxOperation.SET_EBOOK_PROGRESS}",
                idempotencyKey = "i1",
                opKind = OutboxOperation.SET_EBOOK_PROGRESS,
                payload = OutboxOperation.encodeEbookProgressPayload(7, "epubcfi(/6/4!/4)", 0.5),
            ),
        )
        status = HttpStatusCode.OK
        val result = engine().drainOnce()
        assertEquals(1, result.synced)
        assertEquals(0, db.dirtyOperationDao().count())
    }
}
