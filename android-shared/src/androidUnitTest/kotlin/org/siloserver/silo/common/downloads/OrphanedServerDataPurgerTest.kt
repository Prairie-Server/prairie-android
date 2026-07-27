package org.siloserver.silo.common.downloads

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.siloserver.silo.common.data.db.SiloDatabase
import org.siloserver.silo.common.data.db.entity.DownloadEntity
import org.siloserver.silo.common.data.sync.OutboxOperation
import org.siloserver.silo.common.data.db.entity.DirtyOperationEntity
import org.siloserver.silo.network.AndroidServerRegistry
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Removing a server wiped four token keys and its registry entry and nothing
 * else. Every Room row survived, so reclaiming space reclaimed none — and
 * because `idFor()` is base64 of the normalised URL, re-adding the same server
 * resurrected its Downloads list, resume positions, cached rows and pending
 * outbox ops under an identity the user believed they had deleted.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class OrphanedServerDataPurgerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, SiloDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @AfterTest
    fun tearDown() = db.close()

    private fun registry(name: String): AndroidServerRegistry {
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        return AndroidServerRegistry(prefs)
    }

    private suspend fun seed(serverId: String, mediaFileId: Int, recordId: String) {
        db.downloadDao().upsert(
            DownloadEntity(
                serverId = serverId,
                profileId = "p1",
                mediaFileId = mediaFileId,
                recordId = recordId,
                contentId = "c$mediaFileId",
                title = "Episode $mediaFileId",
                subtitle = null,
                posterUrl = null,
                posterThumbhash = null,
                year = null,
                seriesTitle = null,
                seasonNumber = null,
                episodeNumber = null,
                fileName = "file.mkv",
                container = "mkv",
                localUri = null,
                mediaType = "movie",
                overview = null,
                author = null,
                narrator = null,
                durationSeconds = null,
                chaptersJson = null,
                status = "complete",
                kind = "queued",
                fileSize = 0L,
                bytesSent = 0L,
                createdAt = "2026-06-16T00:00:00Z",
                completedAt = null,
                updatedAtMs = 1L,
            ),
        )
        db.dirtyOperationDao().insert(
            DirtyOperationEntity(
                opKind = OutboxOperation.SET_WATCHED,
                serverId = serverId,
                profileId = "p1",
                targetContentId = "c$mediaFileId",
                targetFileId = null,
                coalesceKey = "$serverId|p1|c$mediaFileId|${OutboxOperation.SET_WATCHED}",
                idempotencyKey = "idem-$mediaFileId",
                payloadJson = "{}",
                createdAtMs = 0L,
                nextAttemptAtMs = 0L,
            ),
        )
    }

    private fun purger(
        registry: AndroidServerRegistry,
        cancelled: MutableList<String> = mutableListOf(),
        activeFileId: Int? = null,
    ) = OrphanedServerDataPurger(
        registry = registry,
        purgeDao = db.serverPurgeDao(),
        storage = DownloadStorage(context),
        cancelDownload = { cancelled += it },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        io = UnconfinedTestDispatcher(),
        activePlaybackFileId = { activeFileId },
    )

    @Test
    fun `rows for a server that is no longer registered are purged`() = runTest {
        val registry = registry("purge-basic")
        val keptId = registry.addOrUpdate("https://keep.example")
        seed(keptId, mediaFileId = 1, recordId = "rec-keep")
        seed("orphan-server", mediaFileId = 2, recordId = "rec-orphan")

        val cancelled = mutableListOf<String>()
        val purged = purger(registry, cancelled).purgeOnce()

        assertEquals(setOf("orphan-server"), purged)
        assertEquals(
            listOf("rec-orphan"),
            cancelled,
            "a live DownloadWorker rewrites its row on every progress tick",
        )
        assertNull(db.downloadDao().get("orphan-server", "p1", 2))
        assertTrue(db.dirtyOperationDao().dueBatch("orphan-server", "p1", 1L, 10).isEmpty())

        // The registered server is untouched.
        assertEquals("rec-keep", db.downloadDao().get(keptId, "p1", 1)?.recordId)
        assertEquals(1, db.dirtyOperationDao().dueBatch(keptId, "p1", 1L, 10).size)
    }

    @Test
    fun `an unreadable registry blob purges nothing`() = runTest {
        // The registry decodes its whole state from one JSON blob and treats a
        // decode failure as "no servers". Purging on that reading would turn a
        // corrupt preference — recoverable by signing in again, because ids are
        // derived from the URL — into permanent deletion of every download,
        // resume position and queued outbox op on the device.
        val prefs = context.getSharedPreferences("purge-corrupt", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        prefs.edit()
            .putString(AndroidServerRegistry.KEY_REGISTRY_STATE, "{ not valid json")
            .commit()
        val registry = AndroidServerRegistry(prefs)
        assertTrue(registry.entries.value.isEmpty(), "corrupt state presents as an empty registry")
        assertTrue(registry.stateLoadFailed, "and must be flagged as untrustworthy")
        seed("server-with-data", mediaFileId = 7, recordId = "rec-precious")

        val cancelled = mutableListOf<String>()
        val purged = purger(registry, cancelled).purgeOnce()

        assertEquals(emptySet(), purged)
        assertEquals(emptyList(), cancelled)
        assertEquals(
            "rec-precious",
            db.downloadDao().get("server-with-data", "p1", 7)?.recordId,
            "downloads must survive a registry that could not be read",
        )
        assertEquals(1, db.dirtyOperationDao().dueBatch("server-with-data", "p1", 1L, 10).size)
    }

    @Test
    fun `a file that is currently playing defers its whole server`() = runTest {
        val registry = registry("purge-active")
        seed("orphan-server", mediaFileId = 42, recordId = "rec-playing")

        // Offline PiP survives leaving the screen and outlives a sign-out.
        val purged = purger(registry, activeFileId = 42).purgeOnce()

        assertEquals(emptySet(), purged)
        assertEquals("rec-playing", db.downloadDao().get("orphan-server", "p1", 42)?.recordId)

        // Once playback ends the next pass collects it — nothing is stranded.
        assertEquals(setOf("orphan-server"), purger(registry).purgeOnce())
        assertNull(db.downloadDao().get("orphan-server", "p1", 42))
    }

    @Test
    fun `purging is idempotent and re-runnable`() = runTest {
        val registry = registry("purge-idempotent")
        seed("orphan-server", mediaFileId = 7, recordId = "rec-7")

        assertEquals(setOf("orphan-server"), purger(registry).purgeOnce())
        assertEquals(
            emptySet(),
            purger(registry).purgeOnce(),
            "a second pass must find nothing — this runs on every cold start",
        )
    }

    @Test
    fun `cancelling an in-flight purge cannot strand partially deleted server data`() = runTest {
        val registry = registry("purge-cancel")
        seed("orphan-server", mediaFileId = 9, recordId = "rec-9")
        val rowDeletionStarted = CompletableDeferred<Unit>()
        val allowRowDeletion = CompletableDeferred<Unit>()
        val purger = OrphanedServerDataPurger(
            registry = registry,
            purgeDao = db.serverPurgeDao(),
            storage = DownloadStorage(context),
            cancelDownload = {},
            scope = backgroundScope,
            io = UnconfinedTestDispatcher(testScheduler),
            activePlaybackFileId = { null },
            purgeRows = { serverId ->
                rowDeletionStarted.complete(Unit)
                allowRowDeletion.await()
                db.serverPurgeDao().deleteAllRowsForServer(serverId)
            },
        )

        val purge = backgroundScope.async { purger.purgeOnce() }
        rowDeletionStarted.await()
        purge.cancel()
        allowRowDeletion.complete(Unit)
        purge.join()

        assertTrue(purge.isCancelled)
        assertNull(
            db.downloadDao().get("orphan-server", "p1", 9),
            "once file deletion has begun, cancellation must not leave the Room half behind",
        )
    }
}
