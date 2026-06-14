package com.continuum.app.android.ui.screens.downloads

import com.continuum.app.model.download.DownloadMediaType
import com.continuum.app.model.download.DownloadStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadRowActionTest {

    @Test
    fun `completed in app readable ebook opens the reader`() {
        assertEquals(
            DownloadRowAction.ReadInApp,
            downloadRowAction(item(mediaType = DownloadMediaType.Ebook, container = "epub")),
        )
    }

    @Test
    fun `completed external only ebook opens externally`() {
        assertEquals(
            DownloadRowAction.OpenExternally,
            downloadRowAction(item(mediaType = DownloadMediaType.Ebook, container = "mobi")),
        )
    }

    @Test
    fun `completed fictionbook zip ebook opens the reader from original filename`() {
        assertEquals(
            DownloadRowAction.ReadInApp,
            downloadRowAction(item(mediaType = DownloadMediaType.Ebook, displayName = "book.fb2.zip")),
        )
    }

    @Test
    fun `completed ebook with blank container falls back to original filename`() {
        assertEquals(
            DownloadRowAction.ReadInApp,
            downloadRowAction(
                item(
                    mediaType = DownloadMediaType.Ebook,
                    container = " ",
                    displayName = "book.epub",
                ),
            ),
        )
    }

    @Test
    fun `completed video opens internal player`() {
        assertEquals(
            DownloadRowAction.OpenInAppPlayer,
            downloadRowAction(item(mediaType = DownloadMediaType.Movie, container = "mkv")),
        )
    }

    @Test
    fun `completed unknown download opens externally`() {
        assertEquals(
            DownloadRowAction.OpenExternally,
            downloadRowAction(item(mediaType = DownloadMediaType.Unknown, container = "zip")),
        )
    }

    @Test
    fun `incomplete download has no primary open action`() {
        assertEquals(
            DownloadRowAction.None,
            downloadRowAction(item(isComplete = false, status = DownloadStatus.Downloading)),
        )
    }

    private fun item(
        mediaType: DownloadMediaType = DownloadMediaType.Ebook,
        container: String? = null,
        displayName: String? = null,
        isComplete: Boolean = true,
        status: DownloadStatus = DownloadStatus.Completed,
    ) = DownloadItem(
        id = "dl-1",
        contentId = "book-1",
        title = "Book",
        mediaType = mediaType,
        fileId = 1,
        localUri = "content://downloads/1",
        displayName = displayName,
        container = container,
        isComplete = isComplete,
        status = status,
    )
}
