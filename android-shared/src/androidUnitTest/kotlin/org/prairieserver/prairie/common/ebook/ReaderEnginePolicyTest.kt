package org.prairieserver.prairie.common.ebook

import org.prairieserver.prairie.model.book.BookFormat
import org.prairieserver.prairie.model.ebook.EbookReadMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderEnginePolicyTest {
    @Test
    fun `reflowable formats use ebook engine`() {
        listOf(BookFormat.Epub, BookFormat.Fb2, BookFormat.Fbz, BookFormat.Txt, BookFormat.Markdown).forEach { format ->
            val policy = readerEnginePolicyFor(format, EbookReadMode.InApp)

            assertEquals(ReaderEngineKind.Reflowable, policy.engineKind)
            assertEquals(ReaderContentClass.Ebook, policy.contentClass)
            assertTrue(policy.supportsInAppReading)
        }
    }

    @Test
    fun `pdf uses fixed document engine`() {
        val policy = readerEnginePolicyFor(BookFormat.Pdf, EbookReadMode.InApp)

        assertEquals(ReaderEngineKind.FixedDocument, policy.engineKind)
        assertEquals(ReaderContentClass.Document, policy.contentClass)
        assertTrue(policy.supportsInAppReading)
    }

    @Test
    fun `cbz uses comic manga engine`() {
        val policy = readerEnginePolicyFor(BookFormat.Cbz, EbookReadMode.InApp)

        assertEquals(ReaderEngineKind.ComicManga, policy.engineKind)
        assertEquals(ReaderContentClass.ComicManga, policy.contentClass)
        assertTrue(policy.supportsInAppReading)
    }

    @Test
    fun `external only formats use external engine`() {
        listOf(BookFormat.Cbr, BookFormat.Mobi, BookFormat.Azw, BookFormat.Azw3, BookFormat.Unknown).forEach { format ->
            val policy = readerEnginePolicyFor(format, EbookReadMode.ExternalOnly)

            assertEquals(ReaderEngineKind.External, policy.engineKind)
            assertEquals(ReaderContentClass.ExternalOriginal, policy.contentClass)
            assertFalse(policy.supportsInAppReading)
        }
    }

    @Test
    fun `in app external only formats still use external engine`() {
        listOf(BookFormat.Cbr, BookFormat.Mobi, BookFormat.Azw, BookFormat.Azw3, BookFormat.Unknown).forEach { format ->
            val policy = readerEnginePolicyFor(format, EbookReadMode.InApp)

            assertEquals(ReaderEngineKind.External, policy.engineKind)
            assertEquals(ReaderContentClass.ExternalOriginal, policy.contentClass)
            assertFalse(policy.supportsInAppReading)
        }
    }

    @Test
    fun `unsupported read mode uses external engine`() {
        val policy = readerEnginePolicyFor(BookFormat.Epub, EbookReadMode.Unsupported)

        assertEquals(ReaderEngineKind.External, policy.engineKind)
        assertEquals(ReaderContentClass.ExternalOriginal, policy.contentClass)
        assertFalse(policy.supportsInAppReading)
    }

    @Test
    fun `reader capabilities expose engine kind`() {
        assertEquals(ReaderEngineKind.Reflowable, ReaderCapabilities.forFormat(BookFormat.Epub).engineKind)
        assertEquals(ReaderEngineKind.FixedDocument, ReaderCapabilities.forFormat(BookFormat.Pdf).engineKind)
        assertEquals(ReaderEngineKind.ComicManga, ReaderCapabilities.forFormat(BookFormat.Cbz).engineKind)
        assertEquals(ReaderEngineKind.External, ReaderCapabilities.forFormat(BookFormat.Mobi).engineKind)
    }
}
