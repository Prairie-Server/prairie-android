package com.continuum.app.android.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ReaderFileResolverTest {

    @Test
    fun `resolves server relative reader paths against active server url`() {
        assertEquals(
            "https://lib.strm.cafe/api/v1/ebooks/book-1/files/7/read",
            resolveReaderRequestUrl(
                url = "/api/v1/ebooks/book-1/files/7/read",
                serverUrl = "https://lib.strm.cafe/",
            ),
        )
    }

    @Test
    fun `leaves absolute and local reader urls unchanged`() {
        assertEquals(
            "https://cdn.example.test/book.epub",
            resolveReaderRequestUrl("https://cdn.example.test/book.epub", "https://lib.strm.cafe"),
        )
        assertEquals(
            "http://cdn.example.test/book.epub",
            resolveReaderRequestUrl("http://cdn.example.test/book.epub", "https://lib.strm.cafe"),
        )
        assertEquals(
            "file:///storage/emulated/0/Download/Silo/book.epub",
            resolveReaderRequestUrl("file:///storage/emulated/0/Download/Silo/book.epub", "https://lib.strm.cafe"),
        )
        assertEquals(
            "content://media/external/downloads/12",
            resolveReaderRequestUrl("content://media/external/downloads/12", "https://lib.strm.cafe"),
        )
    }

    @Test
    fun `cache filename includes resolved server url for relative reader paths`() {
        val path = "/api/v1/ebooks/book-1/files/7/read"

        assertNotEquals(
            readerCacheFileName(path, "https://one.example", "epub"),
            readerCacheFileName(path, "https://two.example", "epub"),
        )
    }

    @Test
    fun `cache filename for absolute urls ignores active server url`() {
        val url = "https://cdn.example.test/book.epub"

        assertEquals(
            readerCacheFileName(url, "https://one.example", "epub"),
            readerCacheFileName(url, "https://two.example", "epub"),
        )
    }
}
