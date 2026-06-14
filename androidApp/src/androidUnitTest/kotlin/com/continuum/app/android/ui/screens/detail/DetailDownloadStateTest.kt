package com.continuum.app.android.ui.screens.detail

import com.continuum.app.model.catalog.FileVersion
import com.continuum.app.model.download.DownloadRecord
import com.continuum.app.model.download.DownloadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DetailDownloadStateTest {
    private val version = FileVersion(fileId = 42, fileName = "movie.mkv", container = "mkv")

    @Test
    fun `completed record is downloaded only when local media still exists`() {
        val record = record(status = "completed")

        val present = detailDownloadStateFor(version, listOf(record), hasLocalMedia = true)
        val missing = detailDownloadStateFor(version, listOf(record), hasLocalMedia = false)

        assertTrue(present.isDownloaded)
        assertFalse(present.needsLocalRecovery)
        assertFalse(missing.isDownloaded)
        assertTrue(missing.needsLocalRecovery)
    }

    @Test
    fun `in flight progress ignores missing local media`() {
        val record = record(status = "downloading", bytesSent = 250, fileSize = 1000)

        val state = detailDownloadStateFor(version, listOf(record), hasLocalMedia = false)

        assertFalse(state.isDownloaded)
        assertEquals(0.25f, state.progress)
        assertFalse(state.needsLocalRecovery)
    }

    @Test
    fun `completed missing local media taps replace the stale record`() {
        assertEquals(
            DetailDownloadTapAction.ReplaceAndStart,
            detailDownloadTapAction(
                status = DownloadStatus.Completed,
                forceRedownloadMissingLocal = true,
            ),
        )
        assertEquals(
            DetailDownloadTapAction.Ignore,
            detailDownloadTapAction(
                status = DownloadStatus.Completed,
                forceRedownloadMissingLocal = false,
            ),
        )
    }

    @Test
    fun `active download taps cancel while failed and absent records start`() {
        assertEquals(
            DetailDownloadTapAction.Cancel,
            detailDownloadTapAction(
                status = DownloadStatus.Downloading,
                forceRedownloadMissingLocal = false,
            ),
        )
        assertEquals(
            DetailDownloadTapAction.Cancel,
            detailDownloadTapAction(
                status = DownloadStatus.Queued,
                forceRedownloadMissingLocal = false,
            ),
        )
        assertEquals(
            DetailDownloadTapAction.Start,
            detailDownloadTapAction(
                status = DownloadStatus.Failed,
                forceRedownloadMissingLocal = false,
            ),
        )
        assertEquals(
            DetailDownloadTapAction.Start,
            detailDownloadTapAction(
                status = null,
                forceRedownloadMissingLocal = false,
            ),
        )
    }

    private fun record(
        status: String,
        bytesSent: Long = 0,
        fileSize: Long = 1000,
    ): DownloadRecord = DownloadRecord(
        id = "download-42",
        contentId = "content-42",
        mediaFileId = 42,
        fileSize = fileSize,
        bytesSent = bytesSent,
        kind = "queued",
        status = status,
        createdAt = "2026-06-14T00:00:00Z",
    )
}
