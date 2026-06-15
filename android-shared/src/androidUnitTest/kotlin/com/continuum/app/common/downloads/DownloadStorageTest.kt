package com.continuum.app.common.downloads

import com.continuum.app.model.download.DownloadRecord
import com.continuum.app.model.download.DownloadMediaType
import com.continuum.app.model.download.DownloadSidecar
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadStorageTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newStorage() = DownloadStorage(tmp.newFolder("filesDir"))

    @Test
    fun `prepareWrite uses original filename as display name`() {
        val storage = newStorage()
        val target = storage.prepareWrite("srv1", "profA", 42, fileName = "The Book.epub")

        assertEquals("The Book.epub", target.displayName)
    }

    @Test
    fun `file backed download uris encode paths with spaces and uri reserved characters`() {
        val storage = newStorage()
        val target = storage.prepareWrite("srv1", "profA", 42, fileName = "The #1 Book.epub")
        target.writeTargetBytes(ByteArray(10))

        assertEquals("The #1 Book.epub", storage.locateLocalMedia("srv1", "profA", 42)?.displayName)
        assertTrue(target.uriString.startsWith("file://"))
        assertTrue(target.uriString.contains("The%20%231%20Book.epub"))
        assertFalse(target.uriString.contains("The #1 Book.epub"))
    }

    @Test
    fun `prepareWrite falls back to container extension when filename is unavailable`() {
        val storage = newStorage()
        val target = storage.prepareWrite("srv1", "profA", 42, container = "m4b")

        assertEquals("42.m4b", target.displayName)
    }

    @Test
    fun `prepareWrite sanitizes unsupported filename characters but keeps extension`() {
        val storage = newStorage()
        val target = storage.prepareWrite("srv1", "profA", 42, fileName = "../bad:name?.pdf")

        assertEquals("bad_name_.pdf", target.displayName)
    }

    @Test
    fun `prepareWrite routes video downloads into video collection`() {
        val storage = newStorage()
        val target = storage.prepareWrite(
            serverId = "srv1",
            profileId = "profA",
            fileId = 42,
            fileName = "Movie.mkv",
            mediaType = DownloadMediaType.Movie.wire,
        )

        assertTrue(target.uriString.contains("/Movies/"))
    }

    @Test
    fun `prepareWrite routes audiobook downloads into audio collection`() {
        val storage = newStorage()
        val target = storage.prepareWrite(
            serverId = "srv1",
            profileId = "profA",
            fileId = 42,
            fileName = "Book.m4b",
            mediaType = DownloadMediaType.Audiobook.wire,
        )

        assertTrue(target.uriString.contains("/Music/"))
    }

    @Test
    fun `prepareWrite routes ebook downloads into downloads collection`() {
        val storage = newStorage()
        val target = storage.prepareWrite(
            serverId = "srv1",
            profileId = "profA",
            fileId = 42,
            fileName = "Book.epub",
            mediaType = DownloadMediaType.Ebook.wire,
        )

        assertTrue(target.uriString.contains("/Downloads/"))
    }

    @Test
    fun `file backed store can route collections into distinct public roots`() {
        val downloadsRoot = tmp.newFolder("public-downloads")
        val moviesRoot = tmp.newFolder("public-movies")
        val musicRoot = tmp.newFolder("public-music")
        val storage = DownloadStorage(
            baseDir = tmp.newFolder("filesDir"),
            publicStore = FileBackedPublicDownloadStore(
                collectionRoots = mapOf(
                    PublicDownloadCollection.Downloads to downloadsRoot,
                    PublicDownloadCollection.Video to moviesRoot,
                    PublicDownloadCollection.Audio to musicRoot,
                ),
            ),
        )

        val movie = storage.prepareWrite("srv1", "profA", 10, fileName = "Movie.mkv", mediaType = DownloadMediaType.Movie.wire)
        val audiobook = storage.prepareWrite("srv1", "profA", 11, fileName = "Book.m4b", mediaType = DownloadMediaType.Audiobook.wire)
        val ebook = storage.prepareWrite("srv1", "profA", 12, fileName = "Book.epub", mediaType = DownloadMediaType.Ebook.wire)

        assertTrue(movie.uriString.contains(moviesRoot.name))
        assertFalse(movie.uriString.contains(downloadsRoot.name))
        assertTrue(audiobook.uriString.contains(musicRoot.name))
        assertFalse(audiobook.uriString.contains(downloadsRoot.name))
        assertTrue(ebook.uriString.contains(downloadsRoot.name))
    }

    @Test
    fun `locateLocalFile finds original-format download only`() {
        val storage = newStorage()
        storage.prepareWrite("srv1", "profA", 42, fileName = "The Book.epub").writeTargetBytes(ByteArray(10))

        assertEquals("The Book.epub", storage.locateLocalFile("srv1", "profA", 42)?.name)
    }

    @Test
    fun `completeWrite notifies file backed public store with completed file`() {
        var completedFile: java.io.File? = null
        val storage = DownloadStorage(
            baseDir = tmp.newFolder("filesDir"),
            publicStore = FileBackedPublicDownloadStore(
                root = tmp.newFolder("public"),
                onFileCompleted = { completedFile = it },
            ),
        )
        val target = storage.prepareWrite("srv1", "profA", 42, fileName = "The Book.epub")
        target.writeTargetBytes(ByteArray(10))

        storage.completeWrite(target.uriString)

        assertEquals("The Book.epub", completedFile?.name)
    }

    @Test
    fun `prepareWrite creates parent directories and returns target`() {
        val storage = newStorage()
        val target = storage.prepareWrite("srv1", "profA", 7)
        assertTrue(target.uriString.startsWith("file://"))
        assertEquals("7.download", target.displayName)
    }

    @Test
    fun `exists round-trips with a real write`() {
        val storage = newStorage()
        assertFalse(storage.exists("srv1", "profA", 1))
        val f = storage.prepareWrite("srv1", "profA", 1)
        f.writeTargetBytes(ByteArray(256))
        assertTrue(storage.exists("srv1", "profA", 1))
    }

    @Test
    fun `locateLocalMedia falls back to file id when sidecar uri is stale`() {
        val storage = newStorage()
        storage.prepareWrite("srv1", "profA", 42, fileName = "Recovered.epub").writeTargetBytes(ByteArray(10))
        storage.writeSidecar(
            "srv1",
            "profA",
            stubSidecar(42).copy(localUri = "file://${tmp.root}/missing.epub"),
        )

        assertEquals("Recovered.epub", storage.locateLocalMedia("srv1", "profA", 42)?.displayName)
    }

    @Test
    fun `delete removes only the targeted file`() {
        val storage = newStorage()
        storage.prepareWrite("srv1", "profA", 1).writeTargetBytes(ByteArray(10))
        storage.prepareWrite("srv1", "profA", 2).writeTargetBytes(ByteArray(10))

        assertTrue(storage.delete("srv1", "profA", 1))
        assertFalse(storage.exists("srv1", "profA", 1))
        assertTrue(storage.exists("srv1", "profA", 2))
        // Re-delete returns false (nothing to remove).
        assertFalse(storage.delete("srv1", "profA", 1))
    }

    @Test
    fun `deleteAllForProfile isolates other profiles on the same server`() {
        val storage = newStorage()
        storage.prepareWrite("srv1", "profA", 1, mediaType = DownloadMediaType.Movie.wire).writeTargetBytes(ByteArray(10))
        storage.prepareWrite("srv1", "profA", 2, mediaType = DownloadMediaType.Audiobook.wire).writeTargetBytes(ByteArray(10))
        storage.prepareWrite("srv1", "profB", 3, mediaType = DownloadMediaType.Ebook.wire).writeTargetBytes(ByteArray(10))

        assertTrue(storage.deleteAllForProfile("srv1", "profA"))
        assertFalse(storage.exists("srv1", "profA", 1))
        assertFalse(storage.exists("srv1", "profA", 2))
        assertTrue(storage.exists("srv1", "profB", 3))  // untouched
    }

    @Test
    fun `deleteAllForServer wipes everything under a server`() {
        val storage = newStorage()
        storage.prepareWrite("srv1", "profA", 1, mediaType = DownloadMediaType.Movie.wire).writeTargetBytes(ByteArray(10))
        storage.prepareWrite("srv1", "profB", 2, mediaType = DownloadMediaType.Audiobook.wire).writeTargetBytes(ByteArray(10))
        storage.prepareWrite("srv2", "profA", 3, mediaType = DownloadMediaType.Ebook.wire).writeTargetBytes(ByteArray(10))

        assertTrue(storage.deleteAllForServer("srv1"))
        assertFalse(storage.exists("srv1", "profA", 1))
        assertFalse(storage.exists("srv1", "profB", 2))
        assertTrue(storage.exists("srv2", "profA", 3))
    }

    @Test
    fun `totalBytesUsed sums every downloaded file under the root`() {
        val storage = newStorage()
        storage.prepareWrite("srv1", "profA", 1).writeTargetBytes(ByteArray(100))
        storage.prepareWrite("srv1", "profB", 2).writeTargetBytes(ByteArray(250))
        storage.prepareWrite("srv2", "profA", 3).writeTargetBytes(ByteArray(50))
        assertEquals(400L, storage.totalBytesUsed())
    }

    @Test
    fun `scoped totalBytesUsed sums only one server profile`() {
        val storage = newStorage()
        storage.prepareWrite("srv1", "profA", 1).writeTargetBytes(ByteArray(100))
        storage.prepareWrite("srv1", "profB", 2).writeTargetBytes(ByteArray(250))
        storage.prepareWrite("srv2", "profA", 3).writeTargetBytes(ByteArray(50))

        assertEquals(100L, storage.totalBytesUsed("srv1", "profA"))
    }

    @Test
    fun `totalBytesUsed is zero when no downloads exist`() {
        val storage = newStorage()
        assertEquals(0L, storage.totalBytesUsed())
    }

    // ── Sidecar round-trip ────────────────────────────────────────────────

    private fun stubSidecar(fileId: Int, contentId: String = "tt$fileId"): DownloadSidecar =
        DownloadSidecar(
            record = DownloadRecord(
                id = "dl-$fileId",
                contentId = contentId,
                mediaFileId = fileId,
                fileSize = 1024,
                bytesSent = 1024,
                kind = "queued",
                status = "completed",
                createdAt = "2026-05-24T00:00:00Z",
            ),
            title = "Title $fileId",
            posterUrl = "https://example/p$fileId.jpg",
            updatedAtMs = 1716_500_000_000L,
        )

    @Test
    fun `writeSidecar then readSidecar round-trips`() {
        val storage = newStorage()
        val sidecar = stubSidecar(7)
        storage.writeSidecar("srv1", "profA", sidecar)

        val read = storage.readSidecar("srv1", "profA", 7)
        assertNotNull(read)
        assertEquals(sidecar, read)
    }

    @Test
    fun `readSidecar returns null when file is missing`() {
        val storage = newStorage()
        assertNull(storage.readSidecar("srv1", "profA", 99))
    }

    @Test
    fun `listAllSidecars walks every server-profile combination`() {
        val storage = newStorage()
        storage.writeSidecar("srv1", "profA", stubSidecar(1))
        storage.writeSidecar("srv1", "profB", stubSidecar(2))
        storage.writeSidecar("srv2", "profA", stubSidecar(3))

        val all = storage.listAllSidecars().sortedBy { it.record.mediaFileId }
        assertEquals(listOf(1, 2, 3), all.map { it.record.mediaFileId })
    }

    @Test
    fun `listAllSidecarsWithScope reports the serverId and profileId each sidecar lives under`() {
        val storage = newStorage()
        storage.writeSidecar("srv1", "profA", stubSidecar(1))
        storage.writeSidecar("srv2", "profB", stubSidecar(2))

        val scoped = storage.listAllSidecarsWithScope()
            .associateBy { (_, _, sidecar) -> sidecar.record.mediaFileId }

        assertEquals(2, scoped.size)
        assertEquals("srv1" to "profA", scoped[1]?.let { it.first to it.second })
        assertEquals("srv2" to "profB", scoped[2]?.let { it.first to it.second })
    }

    @Test
    fun `listAllSidecars matches the scoped walk`() {
        val storage = newStorage()
        storage.writeSidecar("srv1", "profA", stubSidecar(1))
        storage.writeSidecar("srv1", "profB", stubSidecar(2))

        assertEquals(
            storage.listAllSidecarsWithScope().map { it.third }.toSet(),
            storage.listAllSidecars().toSet(),
        )
    }

    @Test
    fun `listSidecars returns only the requested server profile scope`() {
        val storage = newStorage()
        storage.writeSidecar("srv1", "profA", stubSidecar(1))
        storage.writeSidecar("srv1", "profB", stubSidecar(2))
        storage.writeSidecar("srv2", "profA", stubSidecar(3))

        assertEquals(
            listOf(1),
            storage.listSidecars("srv1", "profA").map { it.record.mediaFileId },
        )
    }

    @Test
    fun `scoped sidecar lookup does not return same file id from another profile`() {
        val storage = newStorage()
        storage.writeSidecar("srv1", "profA", stubSidecar(7, contentId = "wrong"))
        storage.writeSidecar("srv1", "profB", stubSidecar(7, contentId = "right"))

        assertEquals(
            "right",
            storage.locateSidecarByFileId("srv1", "profB", 7)?.third?.record?.contentId,
        )
        assertNull(storage.locateSidecarByFileId("srv2", "profB", 7))
    }

    @Test
    fun `media store relative path includes the file id directory`() {
        assertEquals(
            "Downloads/Silo/srv1/profA/42/",
            mediaStoreRelativePath(PublicDownloadCollection.Downloads, "srv1", "profA", 42),
        )
    }

    @Test
    fun `findSidecarByContentId locates across server-profile combinations`() {
        val storage = newStorage()
        storage.writeSidecar("srv1", "profA", stubSidecar(1, contentId = "tt-A"))
        storage.writeSidecar("srv2", "profB", stubSidecar(2, contentId = "tt-B"))

        val found = storage.findSidecarByContentId("tt-B")
        assertNotNull(found)
        assertEquals(2, found.record.mediaFileId)
        assertNull(storage.findSidecarByContentId("tt-missing"))
    }

    @Test
    fun `deleteSidecar removes only the targeted sidecar`() {
        val storage = newStorage()
        storage.writeSidecar("srv1", "profA", stubSidecar(1))
        storage.writeSidecar("srv1", "profA", stubSidecar(2))

        assertTrue(storage.deleteSidecar("srv1", "profA", 1))
        assertNull(storage.readSidecar("srv1", "profA", 1))
        assertNotNull(storage.readSidecar("srv1", "profA", 2))
        // Re-delete is a false (nothing left to remove).
        assertFalse(storage.deleteSidecar("srv1", "profA", 1))
    }

    private fun DownloadTarget.writeTargetBytes(bytes: ByteArray) {
        openOutputStream().use { it.write(bytes) }
    }
}
