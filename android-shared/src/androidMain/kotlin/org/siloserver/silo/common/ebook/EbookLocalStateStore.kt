package org.siloserver.silo.common.ebook

import org.siloserver.silo.common.store.ScopedJsonFileStore
import kotlinx.serialization.Serializable
import java.io.File

class EbookLocalStateStore(baseDir: File) {

    private val store = ScopedJsonFileStore(File(baseDir, "ebook_state"), TAG)

    @Serializable
    data class ProgressSnapshot(
        val fileId: Int,
        val location: String,
        val progress: Double,
        val updatedAtMs: Long,
    )

    @Serializable
    data class BookmarkSnapshot(
        val id: String,
        val location: String,
        val createdAtMs: Long,
    )

    private fun progressFile(serverId: String, profileId: String, contentId: String): File =
        store.fileFor(serverId, profileId, contentId, suffix = ".progress.json")

    private fun bookmarksFile(serverId: String, profileId: String, contentId: String): File =
        store.fileFor(serverId, profileId, contentId, suffix = ".bookmarks.json")

    private fun displaySettingsFile(serverId: String, profileId: String): File =
        store.resolve("$serverId/$profileId/reader-settings.json")

    fun readProgress(serverId: String, profileId: String, contentId: String): ProgressSnapshot? =
        store.read<ProgressSnapshot>(progressFile(serverId, profileId, contentId))

    fun writeProgress(
        serverId: String,
        profileId: String,
        contentId: String,
        snapshot: ProgressSnapshot,
    ) {
        store.write(progressFile(serverId, profileId, contentId), snapshot)
    }

    /** A stored progress snapshot together with the scope it lives under. */
    data class ProgressEntry(
        val serverId: String,
        val profileId: String,
        val contentId: String,
        val snapshot: ProgressSnapshot,
    )

    /**
     * Every reading-progress snapshot on disk, across all (server, profile)
     * scopes. Used by the progress syncer to push offline reading back to the
     * server. Walks `ebook_state/<server>/<profile>/<contentId>.progress.json`.
     */
    fun listAllProgress(): List<ProgressEntry> {
        val root = store.resolve("")
        val result = mutableListOf<ProgressEntry>()
        val serverDirs = root.listFiles()?.filter { it.isDirectory } ?: return result
        for (serverDir in serverDirs) {
            val profileDirs = serverDir.listFiles()?.filter { it.isDirectory } ?: continue
            for (profileDir in profileDirs) {
                val files = profileDir.listFiles()
                    ?.filter { it.isFile && it.name.endsWith(".progress.json") }
                    ?: continue
                for (file in files) {
                    val snapshot = store.read<ProgressSnapshot>(file) ?: continue
                    result += ProgressEntry(
                        serverId = serverDir.name,
                        profileId = profileDir.name,
                        contentId = file.name.removeSuffix(".progress.json"),
                        snapshot = snapshot,
                    )
                }
            }
        }
        return result
    }

    fun listBookmarks(serverId: String, profileId: String, contentId: String): List<BookmarkSnapshot> =
        store.read<List<BookmarkSnapshot>>(bookmarksFile(serverId, profileId, contentId))
            .orEmpty()
            .sortedBy { it.createdAtMs }

    fun addBookmark(
        serverId: String,
        profileId: String,
        contentId: String,
        location: String,
        createdAtMs: Long = System.currentTimeMillis(),
    ): BookmarkSnapshot {
        val bookmark = BookmarkSnapshot(
            id = "local-$createdAtMs",
            location = location,
            createdAtMs = createdAtMs,
        )
        val updated = (listBookmarks(serverId, profileId, contentId) + bookmark)
            .distinctBy { it.id }
            .sortedBy { it.createdAtMs }
        store.write(bookmarksFile(serverId, profileId, contentId), updated)
        return bookmark
    }

    fun removeBookmark(
        serverId: String,
        profileId: String,
        contentId: String,
        bookmarkId: String,
    ): List<BookmarkSnapshot> {
        val updated = listBookmarks(serverId, profileId, contentId)
            .filterNot { it.id == bookmarkId }
        store.write(bookmarksFile(serverId, profileId, contentId), updated)
        return updated
    }

    fun readDisplaySettings(serverId: String, profileId: String): ReaderDisplaySettings? =
        store.read<ReaderDisplaySettings>(displaySettingsFile(serverId, profileId))?.normalized()

    fun writeDisplaySettings(
        serverId: String,
        profileId: String,
        settings: ReaderDisplaySettings,
    ) {
        store.write(displaySettingsFile(serverId, profileId), settings.normalized())
    }

    companion object { private const val TAG = "EbookLocalStateStore" }
}
