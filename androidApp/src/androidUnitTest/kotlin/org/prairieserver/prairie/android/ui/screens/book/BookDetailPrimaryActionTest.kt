package org.prairieserver.prairie.android.ui.screens.book

import org.prairieserver.prairie.model.catalog.FileVersion
import kotlin.test.Test
import kotlin.test.assertEquals

class BookDetailPrimaryActionTest {

    @Test
    fun `readable ebook opens in app reader`() {
        assertEquals(
            BookDetailPrimaryAction.ReadInApp,
            bookDetailPrimaryAction(
                selectedVersion = FileVersion(fileId = 1, fileName = "book.epub", container = "epub"),
                canReadSelectedVersion = true,
                isDownloaded = false,
                canOpenExternal = false,
            ),
        )
    }

    @Test
    fun `downloaded external only ebook opens externally`() {
        assertEquals(
            BookDetailPrimaryAction.OpenExternally,
            bookDetailPrimaryAction(
                selectedVersion = FileVersion(fileId = 2, fileName = "book.mobi", container = "mobi"),
                canReadSelectedVersion = false,
                isDownloaded = true,
                canOpenExternal = true,
            ),
        )
    }

    @Test
    fun `external only ebook waits for download before opening`() {
        assertEquals(
            BookDetailPrimaryAction.None,
            bookDetailPrimaryAction(
                selectedVersion = FileVersion(fileId = 3, fileName = "book.azw3", container = "azw3"),
                canReadSelectedVersion = false,
                isDownloaded = false,
                canOpenExternal = true,
            ),
        )
    }
}
