package org.siloserver.silo.common.ebook

import org.siloserver.silo.model.book.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderControlsTest {
    @Test
    fun `epub supports text settings and sections`() {
        val caps = ReaderCapabilities.forFormat(BookFormat.Epub)

        assertTrue(caps.supportsTheme)
        assertTrue(caps.supportsTextSize)
        assertTrue(caps.supportsMargins)
        assertTrue(caps.supportsSections)
        assertTrue(caps.supportsBookmarks)
        assertFalse(caps.supportsExternalOnly)
    }

    @Test
    fun `text based readers support text settings without sections`() {
        listOf(BookFormat.Txt, BookFormat.Markdown, BookFormat.Fb2, BookFormat.Fbz).forEach { format ->
            val caps = ReaderCapabilities.forFormat(format)

            assertTrue(caps.supportsTheme)
            assertTrue(caps.supportsTextSize)
            assertTrue(caps.supportsMargins)
            assertTrue(caps.supportsBookmarks)
            assertTrue(caps.supportsPageJump)
            assertFalse(caps.supportsSections)
            assertFalse(caps.supportsExternalOnly)
        }
    }

    @Test
    fun `pdf and cbz use page controls without text reflow settings`() {
        listOf(BookFormat.Pdf, BookFormat.Cbz).forEach { format ->
            assertPageOnlyCapabilities(ReaderCapabilities.forFormat(format))
        }
    }

    @Test
    fun `cbr is external only until rar rendering exists`() {
        assertExternalOnlyCapabilities(ReaderCapabilities.forFormat(BookFormat.Cbr))
    }

    @Test
    fun `unknown format is external only`() {
        assertExternalOnlyCapabilities(ReaderCapabilities.forFormat(BookFormat.Unknown))
    }

    @Test
    fun `display settings normalize scale values`() {
        val tooLow = ReaderDisplaySettings(textScale = 0.2f, marginScale = 0.2f).normalized()
        val tooHigh = ReaderDisplaySettings(textScale = 3.5f, marginScale = 2.5f).normalized()
        val inRange = ReaderDisplaySettings(textScale = 1.2f, marginScale = 1.1f).normalized()

        assertEquals(0.6f, tooLow.textScale)
        assertEquals(0.75f, tooLow.marginScale)
        assertEquals(3.0f, tooHigh.textScale)
        assertEquals(1.5f, tooHigh.marginScale)
        assertEquals(1.2f, inRange.textScale)
        assertEquals(1.1f, inRange.marginScale)
    }

    private fun assertPageOnlyCapabilities(caps: ReaderCapabilities) {
        assertTrue(caps.supportsBookmarks)
        assertTrue(caps.supportsPageJump)
        assertFalse(caps.supportsSections)
        assertFalse(caps.supportsTheme)
        assertFalse(caps.supportsTextSize)
        assertFalse(caps.supportsMargins)
        assertFalse(caps.supportsExternalOnly)
    }

    private fun assertExternalOnlyCapabilities(caps: ReaderCapabilities) {
        assertFalse(caps.supportsBookmarks)
        assertFalse(caps.supportsPageJump)
        assertFalse(caps.supportsSections)
        assertFalse(caps.supportsTheme)
        assertFalse(caps.supportsTextSize)
        assertFalse(caps.supportsMargins)
        assertTrue(caps.supportsExternalOnly)
    }
}
