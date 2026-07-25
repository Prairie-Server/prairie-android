package org.prairieserver.prairie.tv.ui.screens.player

import kotlin.test.Test
import kotlin.test.assertEquals

class TvSkipSeekIndicatorTest {

    @Test
    fun forwardSkipShowsSignedPlus() {
        assertEquals("+30s", formatSkipDelta(30))
        assertEquals("+10s", formatSkipDelta(10))
    }

    @Test
    fun backwardSkipShowsMinusSign() {
        // Uses a real minus glyph, not a hyphen.
        assertEquals("−10s", formatSkipDelta(-10))
        assertEquals("−30s", formatSkipDelta(-30))
    }
}
