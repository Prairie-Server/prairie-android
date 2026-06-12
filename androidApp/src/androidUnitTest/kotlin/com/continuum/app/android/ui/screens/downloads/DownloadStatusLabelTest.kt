package com.continuum.app.android.ui.screens.downloads

import com.continuum.app.model.download.DownloadStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadStatusLabelTest {

    @Test
    fun queuedWithNoBytesShowsQueued() {
        assertEquals("Queued", downloadStatusLabel(DownloadStatus.Queued, progress = 0f))
    }

    @Test
    fun downloadingWithNoBytesShowsDownloading() {
        assertEquals("Downloading", downloadStatusLabel(DownloadStatus.Downloading, progress = 0f))
    }

    @Test
    fun downloadingWithProgressShowsPercentage() {
        assertEquals("Downloading · 42%", downloadStatusLabel(DownloadStatus.Downloading, progress = 0.42f))
    }

    @Test
    fun completedShowsReady() {
        assertEquals("Ready", downloadStatusLabel(DownloadStatus.Completed, progress = 1f))
    }

    @Test
    fun failedShowsFailed() {
        assertEquals("Failed", downloadStatusLabel(DownloadStatus.Failed, progress = 0.18f))
    }

    @Test
    fun cancelledShowsCancelled() {
        assertEquals("Cancelled", downloadStatusLabel(DownloadStatus.Cancelled, progress = 0.18f))
    }

    @Test
    fun unknownShowsNeedsAttention() {
        assertEquals("Needs attention", downloadStatusLabel(DownloadStatus.Unknown, progress = 0f))
    }
}
