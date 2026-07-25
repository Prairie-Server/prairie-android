package org.prairieserver.prairie.android.ui.screens.reader

import android.graphics.Bitmap
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Robolectric resolves android.graphics.Bitmap.Config under JVM unit tests.
// Pinned to SDK 34 (matches existing Robolectric tests in this module).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PdfRenderBudgetTest {
    @Test fun `low memory devices cap width hard and use RGB_565`() {
        val b = pdfRenderBudget(pageWidth = 1200, pageHeight = 1600, memoryClassMb = 48)
        assertTrue(b.targetWidth <= 1200)
        assertEquals(Bitmap.Config.RGB_565, b.config)
    }
    @Test fun `high memory devices allow 2x up to the cap and ARGB_8888`() {
        val b = pdfRenderBudget(pageWidth = 1000, pageHeight = 1400, memoryClassMb = 256)
        assertEquals(2000, b.targetWidth)
        assertEquals(Bitmap.Config.ARGB_8888, b.config)
    }
    @Test fun `never returns a non-positive width`() {
        assertTrue(pdfRenderBudget(0, 0, 16).targetWidth >= 1)
    }
}
