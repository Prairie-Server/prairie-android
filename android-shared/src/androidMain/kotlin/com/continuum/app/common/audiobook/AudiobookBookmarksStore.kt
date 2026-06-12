package com.continuum.app.common.audiobook

import com.continuum.app.common.store.ScopedJsonFileStore
import com.continuum.app.model.audiobook.AudiobookBookmark
import java.io.File

/**
 * Per-(serverId, profileId, contentId) on-disk bookmark store. Each
 * book gets its own JSON file at
 * `<filesDir>/audiobook_bookmarks/<serverId>/<profileId>/<contentId>.json`,
 * holding an ordered list of [AudiobookBookmark]s.
 *
 * Local-only for now — once the server exposes a /bookmarks endpoint
 * we'll add a merging sync layer; the [AudiobookBookmark.id] field is
 * stable so server round-tripping is straightforward.
 */
class AudiobookBookmarksStore(baseDir: File) {

    private val store = ScopedJsonFileStore(File(baseDir, "audiobook_bookmarks"), TAG)

    fun list(serverId: String, profileId: String, contentId: String): List<AudiobookBookmark> =
        store.read<List<AudiobookBookmark>>(store.fileFor(serverId, profileId, contentId)).orEmpty()

    /** Add a bookmark (deduped by id, existing entry wins), persisting
     *  the full updated list atomically. Returns the persisted list. */
    fun add(
        serverId: String,
        profileId: String,
        contentId: String,
        bookmark: AudiobookBookmark,
    ): List<AudiobookBookmark> {
        val updated = (list(serverId, profileId, contentId) + bookmark)
            .distinctBy { it.id }
            .sortedBy { it.positionSeconds }
        write(serverId, profileId, contentId, updated)
        return updated
    }

    /** Remove a bookmark by id. No-op if missing. */
    fun remove(
        serverId: String,
        profileId: String,
        contentId: String,
        bookmarkId: String,
    ): List<AudiobookBookmark> {
        val updated = list(serverId, profileId, contentId).filterNot { it.id == bookmarkId }
        write(serverId, profileId, contentId, updated)
        return updated
    }

    /** Replace the bookmark with matching [id]'s note. */
    fun updateNote(
        serverId: String,
        profileId: String,
        contentId: String,
        bookmarkId: String,
        note: String?,
    ): List<AudiobookBookmark> {
        val updated = list(serverId, profileId, contentId).map {
            if (it.id == bookmarkId) it.copy(note = note?.takeIf { n -> n.isNotBlank() }) else it
        }
        write(serverId, profileId, contentId, updated)
        return updated
    }

    private fun write(
        serverId: String,
        profileId: String,
        contentId: String,
        bookmarks: List<AudiobookBookmark>,
    ) {
        store.write(store.fileFor(serverId, profileId, contentId), bookmarks)
    }

    companion object { private const val TAG = "AudiobookBookmarksStore" }
}
