package com.continuum.app.common.audiobook

import com.continuum.app.common.store.ScopedJsonFileStore
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Per-(serverId, profileId, contentId) position snapshot. One JSON file
 * per book at `<filesDir>/audiobook_positions/<server>/<profile>/<contentId>.json`.
 *
 * Local-only — once the server exposes /sessions for audiobooks, the VM
 * will write to both and reconcile on restore. Until then, the position
 * survives app restart but doesn't roam across devices.
 */
class AudiobookPositionStore(baseDir: File) {

    private val store = ScopedJsonFileStore(File(baseDir, "audiobook_positions"), TAG)

    @Serializable
    data class Snapshot(
        val positionSeconds: Double,
        val durationSeconds: Double,
        val updatedAtMs: Long,
    )

    fun read(serverId: String, profileId: String, contentId: String): Snapshot? =
        store.read<Snapshot>(store.fileFor(serverId, profileId, contentId))

    /** Persists atomically (tmp + fsync + rename) so a crash mid-write
     *  doesn't leave a half-written file that fails to decode next launch. */
    fun write(
        serverId: String,
        profileId: String,
        contentId: String,
        snapshot: Snapshot,
    ) {
        store.write(store.fileFor(serverId, profileId, contentId), snapshot)
    }

    fun delete(serverId: String, profileId: String, contentId: String): Boolean {
        val file = store.fileFor(serverId, profileId, contentId)
        return if (file.exists()) file.delete() else false
    }

    /** A stored snapshot together with the scope it lives under. */
    data class Entry(
        val serverId: String,
        val profileId: String,
        val contentId: String,
        val snapshot: Snapshot,
    )

    /**
     * Every snapshot on disk, across all (server, profile) scopes. Used by the
     * progress syncer to push offline listening back to the server, and by the
     * scope-agnostic [findLatest] resume fallback. Walks
     * `audiobook_positions/<server>/<profile>/<contentId>.json`.
     */
    fun listAll(): List<Entry> {
        val root = store.resolve("")
        val result = mutableListOf<Entry>()
        val serverDirs = root.listFiles()?.filter { it.isDirectory } ?: return result
        for (serverDir in serverDirs) {
            val profileDirs = serverDir.listFiles()?.filter { it.isDirectory } ?: continue
            for (profileDir in profileDirs) {
                val files = profileDir.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }
                    ?: continue
                for (file in files) {
                    val snapshot = store.read<Snapshot>(file) ?: continue
                    result += Entry(
                        serverId = serverDir.name,
                        profileId = profileDir.name,
                        contentId = file.name.removeSuffix(".json"),
                        snapshot = snapshot,
                    )
                }
            }
        }
        return result
    }

    /**
     * Most-recently-updated snapshot for [contentId] regardless of scope. The
     * resume path uses this as a fallback when the active (server, profile)
     * scope can't be resolved — e.g. offline cold start before the server
     * registry has restored the active server id.
     */
    fun findLatest(contentId: String): Snapshot? =
        listAll()
            .filter { it.contentId == contentId }
            .maxByOrNull { it.snapshot.updatedAtMs }
            ?.snapshot

    companion object { private const val TAG = "AudiobookPositionStore" }
}
