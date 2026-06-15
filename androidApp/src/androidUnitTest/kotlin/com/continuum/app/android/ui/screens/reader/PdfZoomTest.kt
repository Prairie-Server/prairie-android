package com.continuum.app.android.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class PdfZoomTest {
    @Test fun `zoom clamps between 1x and 5x`() {
        assertEquals(5f, clampPdfZoom(10f))
        assertEquals(1f, clampPdfZoom(0.2f))
        assertEquals(2.5f, clampPdfZoom(2.5f))
    }

    @Test fun `double-tap toggles between 1x and 2_5x`() {
        assertEquals(2.5f, nextDoubleTapZoom(1f))
        assertEquals(1f, nextDoubleTapZoom(2.5f))
        assertEquals(1f, nextDoubleTapZoom(4f))
    }
}
