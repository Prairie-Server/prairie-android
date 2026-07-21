package org.siloserver.silo.tv.ui.screens.player

import kotlin.test.Test
import kotlin.test.assertEquals

class TvPlayerScrubberTest {
    @Test
    fun previewDrivesLabelsWhileScrubbing() {
        assertEquals(42.0, playerScrubberLabelPosition(10.0, 42.0, true))
    }

    @Test
    fun playbackPositionDrivesLabelsOtherwise() {
        assertEquals(10.0, playerScrubberLabelPosition(10.0, 42.0, false))
    }
}
