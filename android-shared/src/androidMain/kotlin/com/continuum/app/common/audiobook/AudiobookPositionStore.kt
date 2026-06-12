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

    companion object { private const val TAG = "AudiobookPositionStore" }
}
