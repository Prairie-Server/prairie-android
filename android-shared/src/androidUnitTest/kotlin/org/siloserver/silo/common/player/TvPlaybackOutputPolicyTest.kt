package org.siloserver.silo.common.player

import org.siloserver.silo.model.playback.HdrCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvPlaybackOutputPolicyTest {
    @Test
    fun `mdarcy Shield recovers decoder-backed HLG when display enumeration omits it`() {
        val effective = TvPlaybackOutputPolicy.effectiveHdrCapabilities(
            codec = HdrCapabilities(hdr10 = true, hdr10Plus = true, hlg = true),
            display = HdrCapabilities(hdr10 = true),
            manufacturer = "NVIDIA",
            model = "SHIELD Android TV",
            device = "mdarcy",
        )

        assertEquals(
            HdrCapabilities(hdr10 = true, hlg = true),
            effective,
        )
    }

    @Test
    fun `mdarcy Shield quirk never invents HLG decoder support`() {
        val effective = TvPlaybackOutputPolicy.effectiveHdrCapabilities(
            codec = HdrCapabilities(hdr10 = true),
            display = HdrCapabilities(hdr10 = true, hlg = true),
            manufacturer = "NVIDIA",
            model = "SHIELD Android TV",
            device = "mdarcy",
        )

        assertFalse(effective.hlg)
    }

    @Test
    fun `unconfirmed Shield hardware still requires display HLG`() {
        val effective = TvPlaybackOutputPolicy.effectiveHdrCapabilities(
            codec = HdrCapabilities(hdr10 = true, hlg = true),
            display = HdrCapabilities(hdr10 = true),
            manufacturer = "NVIDIA",
            model = "SHIELD Android TV",
            device = "darcy",
        )

        assertFalse(effective.hlg)
    }

    @Test
    fun `normal display HLG survives on other devices`() {
        val effective = TvPlaybackOutputPolicy.effectiveHdrCapabilities(
            codec = HdrCapabilities(hlg = true),
            display = HdrCapabilities(hlg = true),
            manufacturer = "Sony",
            model = "BRAVIA 4K",
            device = "bravia_atv3_4k",
        )

        assertTrue(effective.hlg)
    }

    @Test
    fun `NVIDIA Shield enables hardware AV sync tunneling`() {
        assertTrue(
            TvPlaybackOutputPolicy.shouldEnableTunneling(
                manufacturer = "NVIDIA",
                model = "SHIELD Android TV",
                device = "mdarcy",
            ),
        )
    }

    @Test
    fun `Google TV Streamer keeps tunneling disabled by model`() {
        assertFalse(
            TvPlaybackOutputPolicy.shouldEnableTunneling(
                manufacturer = "Google",
                model = "Google TV Streamer",
                device = "mustang",
            ),
        )
    }

    @Test
    fun `Google TV Streamer keeps tunneling disabled by device codename`() {
        assertFalse(
            TvPlaybackOutputPolicy.shouldEnableTunneling(
                manufacturer = "Google",
                model = "GZRNL",
                device = "mustang",
            ),
        )
    }

    @Test
    fun `other Android TV devices can negotiate tunneling`() {
        assertTrue(
            TvPlaybackOutputPolicy.shouldEnableTunneling(
                manufacturer = "Sony",
                model = "BRAVIA 4K",
                device = "bravia_atv3_4k",
            ),
        )
    }
}
