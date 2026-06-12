package com.continuum.app.common.ebook

import com.continuum.app.common.store.ScopedJsonFileStore
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
