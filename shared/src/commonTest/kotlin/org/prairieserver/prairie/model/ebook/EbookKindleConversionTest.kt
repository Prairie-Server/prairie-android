package org.prairieserver.prairie.model.ebook

import org.prairieserver.prairie.model.book.BookFormat
import org.prairieserver.prairie.model.catalog.FileVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EbookKindleConversionTest {
    private val mobi = FileVersion(fileId = 1, fileName = "book.mobi", container = "mobi")
    private val azw3 = FileVersion(fileId = 2, fileName = "book.azw3", container = null)
    private val epub = FileVersion(fileId = 3, fileName = "book.epub", container = "epub")
    private val video = FileVersion(fileId = 4, fileName = "movie.mkv", container = "mkv")

    @Test
    fun supportAndDisplayNamesFollowTheFormatKey() {
        assertTrue(mobi.isSupportedEbookVersion())
        assertFalse(video.isSupportedEbookVersion())
        assertEquals("MOBI", mobi.ebookFormatDisplayName())
        assertEquals("Kindle", azw3.ebookFormatDisplayName())
        assertEquals("Unknown", video.ebookFormatDisplayName())
    }

    @Test
    fun kindleFilesReadInAppOnlyWhenServerConverts() {
        assertTrue(mobi.isKindleConvertibleEbookVersion())
        assertFalse(epub.isKindleConvertibleEbookVersion())
        assertFalse(mobi.isInAppReadableEbookVersion(kindleConversionAvailable = false))
        assertTrue(mobi.isInAppReadableEbookVersion(kindleConversionAvailable = true))
        assertTrue(epub.isInAppReadableEbookVersion(kindleConversionAvailable = false))
        assertFalse(video.isInAppReadableEbookVersion(kindleConversionAvailable = true))
    }

    @Test
    fun externalKindleTargetIsPromotedToEpubWhenConversionIsAvailable() {
        val target = assertNotNull(chooseReaderVersion(listOf(azw3), requestedFileId = null))
        assertTrue(target.isKindleConvertibleExternal())
        assertSame(target, target.promotedForKindleConversion(available = false))

        val promoted = target.promotedForKindleConversion(available = true)
        assertEquals(EbookReadMode.InApp, promoted.support.readMode)
        assertEquals(BookFormat.Epub, promoted.format)
        assertEquals(2, promoted.version.fileId)
        assertFalse(promoted.isKindleConvertibleExternal())
        // Already in-app targets are left alone.
        val epubTarget = assertNotNull(chooseReaderVersion(listOf(epub), requestedFileId = null))
        assertFalse(epubTarget.isKindleConvertibleExternal())
        assertSame(epubTarget, epubTarget.promotedForKindleConversion(available = true))
    }

    @Test
    fun singlePageBooksAreEitherUnreadOrFinished() {
        assertEquals(0.0, ebookProgressPercentForPage(page = 0, pageCount = 1, currentProgress = 0.4))
        assertEquals(1.0, ebookProgressPercentForPage(page = 1, pageCount = 1, currentProgress = 0.4))
        assertEquals(0.5, ebookProgressPercentForPage(page = 2, pageCount = 5, currentProgress = 0.0))
        assertEquals(1.0, ebookProgressPercentForPage(page = 3, pageCount = null, currentProgress = 7.0))
    }

    @Test
    fun bookFormatFallsBackToThePathWhenKeyIsUnknownToTheRenderer() {
        assertEquals(BookFormat.Mobi, mobi.bookFormatFromEbookVersion())
        assertEquals(BookFormat.Unknown, video.bookFormatFromEbookVersion())
    }
}
