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

        val media = OfflineMediaResolver(storage).findLocalMedia("book-1", requestedFileId = 42)

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

        assertNull(OfflineMediaResolver(storage).findLocalMedia("book-1", requestedFileId = 42))
    }

    @Test
    fun `findLocalMedia honors requested file id when fallback is disabled`() {
        val storage = newStorage()
        storage.prepareWrite("srv1", "profA", 1, fileName = "A.epub").writeTargetBytes(ByteArray(10))
        storage.writeSidecar("srv1", "profA", sidecar(1, contentId = "book-1", fileName = "A.epub"))

        assertNull(
            OfflineMediaResolver(storage).findLocalMedia(
                contentId = "book-1",
                requestedFileId = 2,
                allowFallback = false,
            ),
        )
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
