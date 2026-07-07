package org.siloserver.silo.common.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StyledSubtitleBurnInTest {

    @Test
    fun burnsStyledTrackOnSubFloorDeviceDuringFullTranscode() {
        assertTrue(
            shouldBurnStyledSubtitle(
                isRemux = false,
                subtitleTrackIndex = 0,
                subtitleCodec = "ass",
                mpvSupportedOnDevice = false,
            ),
        )
        assertTrue(
            shouldBurnStyledSubtitle(
                isRemux = false,
                subtitleTrackIndex = 1,
                subtitleCodec = "SSA",
                mpvSupportedOnDevice = false,
            ),
        )
    }

    @Test
    fun neverBurnsWhenFidelityOrTogglingWouldRegress() {
        // MPV devices render libass client-side — burning would force encodes.
        assertFalse(shouldBurnStyledSubtitle(false, 0, "ass", mpvSupportedOnDevice = true))
        // Plain text tracks stay client-rendered so toggling needs no restart.
        assertFalse(shouldBurnStyledSubtitle(false, 0, "subrip", mpvSupportedOnDevice = false))
        // Remux has no video encode to burn into.
        assertFalse(shouldBurnStyledSubtitle(true, 0, "ass", mpvSupportedOnDevice = false))
        // No subtitle selected.
        assertFalse(shouldBurnStyledSubtitle(false, null, "ass", mpvSupportedOnDevice = false))
        assertFalse(shouldBurnStyledSubtitle(false, 0, null, mpvSupportedOnDevice = false))
    }
}
