package com.continuum.app.android.ui.screens.reader.reflow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SectionWeightsTest {
    @Test fun `progress weights by cumulative char offset`() {
        val w = SectionWeights(listOf(100, 300)) // section 0 is 25% of the book
        assertEquals(0.0, w.bookProgression(0, 0.0), 1e-9)
        assertEquals(0.25, w.bookProgression(1, 0.0), 1e-9)
        assertEquals(0.25, w.bookProgression(0, 1.0), 1e-9)
        assertEquals(1.0, w.bookProgression(1, 1.0), 1e-9)
    }

    @Test fun `clamps out-of-range section index`() {
        val w = SectionWeights(listOf(100, 100))
        assertTrue(w.bookProgression(9, 0.5) in 0.0..1.0)
    }

    @Test fun `empty sections fall back to raw page progression`() {
        val w = SectionWeights(emptyList())
        assertEquals(0.5, w.bookProgression(0, 0.5), 1e-9)
    }
}
