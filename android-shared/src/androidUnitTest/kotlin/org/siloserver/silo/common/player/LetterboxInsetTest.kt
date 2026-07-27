package org.siloserver.silo.common.player

import org.junit.Assert.assertEquals
import org.junit.Test

class LetterboxInsetTest {
    private val fullFrame = SubtitleVideoRect(left = 0, top = 0, width = 1920, height = 1080)

    @Test
    fun scopeContentInsetsToThePicture() {
        val inset = fullFrame.insetByLetterbox(LetterboxInsets(0.1278f, 0.1278f))
        assertEquals(138, inset.top)
        assertEquals(804, inset.height)
        assertEquals(0, inset.left)
        assertEquals(1920, inset.width)
    }

    @Test
    fun noMeasurementLeavesTheRectAlone() {
        assertEquals(fullFrame, fullFrame.insetByLetterbox(LetterboxInsets.NONE))
    }

    @Test
    fun barsLargerThanTheFrameAreRefused() {
        assertEquals(fullFrame, fullFrame.insetByLetterbox(LetterboxInsets(0.6f, 0.6f)))
    }
}
