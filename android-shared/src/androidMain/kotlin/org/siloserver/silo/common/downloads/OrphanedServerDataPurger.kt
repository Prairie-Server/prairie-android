package org.siloserver.silo.common.downloads

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.siloserver.silo.common.data.db.SiloDatabase
import org.siloserver.silo.common.data.db.dao.ServerPurgeDao
import org.siloserver.silo.common.player.ActivePlaybackFile
import org.siloserver.silo.network.ServerRegistry

/**
 * Deletes the on-disk files and Room rows of servers that are no longer in the
 * registry.
 *
 * Removing a server wiped four token keys and the registry entry, and nothing
 * else. So "remove this server to reclaim space" reclaimed nothing in-app (the
 * bytes stayed until the user found them in the system Files app), and because
 * `idFor()` is base64 of the normalised URL, re-adding the same server
 * resurrected its Downloads list, resume positions, cached home and catalog
 * rows, and any pending outbox ops.
 *
 * Deliberately an observer rather than a step inside `ServerRegistry.remove`:
 *
 *  - `remove` runs inside `identityTransitions.changing { mutex.withLock { … } }`
 *    on Main. Putting a multi-gigabyte filesystem walk there would serialise
 *    every identity mutation behind it.
 *  - `AndroidServerRegistry` lives in `shared` and has no Room access at all.
 *  - Orphans are *derived* — rows present with no matching registry entry —
 *    so this is idempotent, safe to re-run at startup, and cleans installs that
 *    removed servers before any of this existed.
 */
class OrphanedServerDataPurger(
    private val registry: ServerRegistry,
    private val purgeDao: ServerPurgeDao,
    private val storage: DownloadStorage,
    private val cancelDownload: (recordId: String) -> Unit,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val activePlaybackFileId: () -> Int? = { ActivePlaybackFile.fileId.value },
    private val purgeRows: suspend (String) -> Unit = purgeDao::deleteAllRowsForServer,
) {

    private val runLock = Mutex()

    fun start() {
        scope.launch {
            purgeOnce()
            // Re-run whenever the registry loses an entry. Additions cannot
            // orphan anything, and re-running on every change would walk the
            // filesystem during ordinary sign-in churn.
            var previous = registry.entries.value.map { it.id }.toSet()
            registry.entries
                .map { entries -> entries.map { it.id }.toSet() }
                .distinctUntilChanged()
                .collect { ids ->
                    val lost = previous - ids
                    previous = ids
                    if (lost.isNotEmpty()) purgeOnce()
                }
        }
    }

    /**
     * Purges every orphan once. Returns the ids fully purged.
     *
     * Serialised against itself: startup and a removal can fire together, and
     * two passes deleting the same tree would have one of them see files vanish
     * mid-walk.
     */
    suspend fun purgeOnce(): Set<String> = runLock.withLock {
        withContext(io) {
            // Orphan-ness is derived purely from registry absence, so a registry
            // that failed to load presents every server as an orphan and this
            // becomes a device wipe. Absence is only evidence of removal when
            // the registry actually knows what it holds.
            if (registry.stateLoadFailed) {
                Log.w(TAG, "registry state unreadable; skipping purge")
                return@withContext emptySet()
            }
            val registered = registry.entries.value.map { it.id }.toSet()
            val orphans = purgeDao.knownServerIds().toSet() - registered
            val purged = mutableSetOf<String>()
            orphans.forEach { serverId ->
                // Once destructive work starts, complete the filesystem/Room pair
                // even if the observing app scope is cancelled. Otherwise a
                // teardown between those halves can strand rows for a server whose
                // one removal-triggered pass has already been consumed.
                if (withContext(NonCancellable) { purgeServer(serverId) }) {
                    purged += serverId
                }
            }
            purged
        }
    }

    private suspend fun purgeServer(serverId: String): Boolean {
        // Offline playback holds a file open — PiP survives leaving the screen,
        // and the player keeps running after a sign-out. Deleting underneath it
        // ends playback mid-scene. Leave the whole server for the next pass;
        // startup will catch it even if nothing else does.
        val activeFileId = activePlaybackFileId()
        if (activeFileId != null && activeFileId in purgeDao.downloadFileIdsForServer(serverId)) {
            Log.i(TAG, "deferring purge of $serverId: file $activeFileId is playing")
            return false
        }

        // Cancel first. A running DownloadWorker writes its row back on every
        // progress tick, so deleting rows underneath it resurrects one — with
        // no registry entry behind it, permanently un-purgeable by this pass.
        purgeDao.downloadRecordIdsForServer(serverId).forEach { recordId ->
            runCatching { cancelDownload(recordId) }
                .onFailure { Log.w(TAG, "cancel failed for $recordId", it) }
        }

        runCatching { storage.deleteAllForServer(serverId) }
            .onFailure { Log.w(TAG, "file purge failed for $serverId", it) }

        purgeRows(serverId)
        Log.i(TAG, "purged orphaned server data for $serverId")
        return true
    }

    private companion object {
        const val TAG = "OrphanedServerPurge"
    }
}

/**
 * Builds and starts the purger.
 *
 * Lives here rather than in each `Application` because the app modules do not
 * have Room on their compile classpath — they can name [SiloDatabase] but not
 * call through it.
 */
fun installOrphanedServerDataPurge(
    context: Context,
    registry: ServerRegistry,
    database: SiloDatabase,
    storage: DownloadStorage,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
): OrphanedServerDataPurger = OrphanedServerDataPurger(
    registry = registry,
    purgeDao = database.serverPurgeDao(),
    storage = storage,
    cancelDownload = { recordId -> DownloadWorker.cancel(context, recordId) },
    scope = scope,
).also { it.start() }
