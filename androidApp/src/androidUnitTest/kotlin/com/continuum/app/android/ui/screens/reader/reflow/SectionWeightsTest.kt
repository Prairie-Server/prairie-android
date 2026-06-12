package com.continuum.app.android.ui.screens.reader.reflow

import kotlin.test.Test
import kotlin.test.assertEquals

class SectionWeightsTest {
    private val w = SectionWeights(listOf(100, 300)) // total 400

    @Test fun `start of first section is zero`() =
        assertEquals(0.0, w.bookProgression(0, 0.0), 1e-9)
    @Test fun `mid first section weights by chars`() =
        assertEquals(0.125, w.bookProgression(0, 0.5), 1e-9) // 0 + 0.25*0.5
    @Test fun `start of second section is first section weight`() =
        assertEquals(0.25, w.bookProgression(1, 0.0), 1e-9)
    @Test fun `end of last section is one`() =
        assertEquals(1.0, w.bookProgression(1, 1.0), 1e-9)
    @Test fun `single empty section degrades to page progression`() =
        assertEquals(0.5, SectionWeights(listOf(0)).bookProgression(0, 0.5), 1e-9)
}
