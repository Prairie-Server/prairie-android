package com.continuum.app.common.downloads

import com.continuum.app.model.download.DownloadRecord
import com.continuum.app.model.download.DownloadSidecar
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OfflineMediaResolverTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newStorage() = DownloadStorage(tmp.newFolder("filesDir"))

    @Test
    fun `findLocalMedia returns completed original-named download`() {
        val storage = newStorage()
        storage.prepareWrite("srv1", "profA", 42, fileName = "Novel.epub").writeTargetBytes(ByteArray(10))
        storage.writeSidecar("srv1", "profA", sidecar(42, contentId = "book-1", fileName = "Novel.epub"))

        val media = OfflineMediaResolver(storage).findLocalMedia("srv1", "profA", "book-1", requestedFileId = 42)

        assertEquals("srv1", media?.serverId)
        assertEquals("profA", media?.profileId)
        assertEquals(42, media?.fileId)
        assertEquals("Novel.epub", media?.file?.name)
        assertEquals("file://${media?.file?.absolutePath}", media?.fileUrl)
    }

    @Test
    fun `findLocalMedia ignores incomplete downloads`() {
        val storage = newStorage()
        storage.prepareWrite("srv1", "profA", 42, fileName = "Novel.epub").writeTargetBytes(ByteArray(10))
        storage.writeSidecar(
            "srv1",
            "profA",
            sidecar(42, contentId = "book-1", status = "downloading", fileName = "Novel.epub"),
        )

        assertNull(OfflineMediaResolver(storage).findLocalMedia("srv1", "profA", "book-1", requestedFileId = 42))
    }

    @Test
    fun `findLocalMedia honors requested file id when fallback is disabled`() {
        val storage = newStorage()
        storage.prepareWrite("srv1", "profA", 1, fileName = "A.epub").writeTargetBytes(ByteArray(10))
        storage.writeSidecar("srv1", "profA", sidecar(1, contentId = "book-1", fileName = "A.epub"))

        assertNull(
            OfflineMediaResolver(storage).findLocalMedia(
                serverId = "srv1",
                profileId = "profA",
                contentId = "book-1",
                requestedFileId = 2,
                allowFallback = false,
            ),
        )
    }

    @Test
    fun `findLocalMedia stays inside the requested server profile scope`() {
        val storage = newStorage()
        storage.prepareWrite("srv1", "profA", 42, fileName = "Wrong.epub").writeTargetBytes(ByteArray(10))
        storage.writeSidecar("srv1", "profA", sidecar(42, contentId = "book-1", fileName = "Wrong.epub"))
        storage.prepareWrite("srv1", "profB", 42, fileName = "Right.epub").writeTargetBytes(ByteArray(10))
        storage.writeSidecar("srv1", "profB", sidecar(42, contentId = "book-1", fileName = "Right.epub"))

        val media = OfflineMediaResolver(storage).findLocalMedia(
            serverId = "srv1",
            profileId = "profB",
            contentId = "book-1",
            requestedFileId = 42,
        )

        assertEquals("profB", media?.profileId)
        assertEquals("Right.epub", media?.displayName)
    }

    @Test
    fun `listLocalMedia returns every completed local candidate for content`() {
        val storage = newStorage()
        storage.prepareWrite("srv1", "profA", 7, fileName = "Book.mobi").writeTargetBytes(ByteArray(10))
        storage.writeSidecar("srv1", "profA", sidecar(7, contentId = "book-1", fileName = "Book.mobi"))
        storage.prepareWrite("srv1", "profA", 8, fileName = "Book.epub").writeTargetBytes(ByteArray(10))
        storage.writeSidecar("srv1", "profA", sidecar(8, contentId = "book-1", fileName = "Book.epub"))
        storage.prepareWrite("srv1", "profA", 9, fileName = "Other.epub").writeTargetBytes(ByteArray(10))
        storage.writeSidecar("srv1", "profA", sidecar(9, contentId = "book-2", fileName = "Other.epub"))

        val media = OfflineMediaResolver(storage).listLocalMedia("srv1", "profA", "book-1")

        assertEquals(listOf(7, 8), media.map { it.fileId })
        assertEquals(listOf("Book.mobi", "Book.epub"), media.map { it.displayName })
    }

    private fun sidecar(
        fileId: Int,
        contentId: String,
        status: String = "completed",
        fileName: String,
    ): DownloadSidecar =
        DownloadSidecar(
            record = DownloadRecord(
                id = "dl-$fileId",
                contentId = contentId,
                mediaFileId = fileId,
                fileSize = 10,
                bytesSent = 10,
                kind = "queued",
                status = status,
                createdAt = "2026-06-09T00:00:00Z",
            ),
            title = "Title",
            fileName = fileName,
            container = fileName.substringAfterLast('.', missingDelimiterValue = ""),
            updatedAtMs = 1L,
        )

    private fun DownloadTarget.writeTargetBytes(bytes: ByteArray) {
        openOutputStream().use { it.write(bytes) }
    }
}
