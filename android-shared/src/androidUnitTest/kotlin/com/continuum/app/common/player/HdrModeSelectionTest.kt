package com.continuum.app.common.player

import kotlin.test.Test
import kotlin.test.assertEquals

class HdrModeSelectionTest {
    @Test
    fun prefersExactContentHdrTypeWhenDisplaySupportsIt() {
        val result = HdrModeSelection.choose(
            contentHdr = HdrType.HDR10,
            displaySupported = setOf(HdrType.HDR10, HdrType.DOLBY_VISION),
        )
        assertEquals(HdrType.HDR10, result)
    }

    @Test
    fun fallsBackToSdrWhenDisplayLacksContentHdrType() {
        val result = HdrModeSelection.choose(
            contentHdr = HdrType.DOLBY_VISION,
            displaySupported = setOf(HdrType.HDR10),
        )
        assertEquals(HdrType.SDR, result)
    }

    @Test
    fun sdrContentStaysSdr() {
        val result = HdrModeSelection.choose(
            contentHdr = HdrType.SDR,
            displaySupported = setOf(HdrType.HDR10),
        )
        assertEquals(HdrType.SDR, result)
    }
}
