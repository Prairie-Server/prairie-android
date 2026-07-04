package org.siloserver.silo.common.player

import org.siloserver.silo.model.playback.HdrCapabilities
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackCapabilityDetectorDolbyVisionTest {

    @Test
    fun profile8DirectPlayIsAllowedEvenWithoutNativeDolbyVisionOutput() {
        assertTrue(
            isDirectPlayableDolbyVisionProfile(
                profile = 8,
                supportedHdr = HdrCapabilities(),
            ),
            "Dolby Vision Profile 8 has a renderable base layer; do not force a server fallback just because the display probe lacks native DV.",
        )
    }

    @Test
    fun profile7DirectPlayRequiresNativeDualLayerSupport() {
        assertFalse(
            isDirectPlayableDolbyVisionProfile(
                profile = 7,
                supportedHdr = HdrCapabilities(dolbyVisionProfiles = listOf(5, 8)),
            ),
            "Without a native dual-layer DV decoder, Media3 cannot direct-play P7 — recovery must route to mpv or transcode instead.",
        )
    }

    @Test
    fun profile7DirectPlayIsAllowedWithNativeDualLayerSupport() {
        assertTrue(
            isDirectPlayableDolbyVisionProfile(
                profile = 7,
                supportedHdr = HdrCapabilities(dolbyVisionProfiles = listOf(5, 7, 8)),
            ),
            "Devices whose DV decoder claims dual-layer profiles with multi-instance HEVC (Shield-class) can direct-play P7.",
        )
    }

    @Test
    fun profile5DirectPlayRequiresNativeDolbyVisionDecoder() {
        assertFalse(
            isDirectPlayableDolbyVisionProfile(
                profile = 5,
                supportedHdr = HdrCapabilities(),
            ),
            "P5 has no backward-compatible base layer; without a DV decoder the Media3 route cannot render it.",
        )
    }
}
