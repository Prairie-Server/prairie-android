package org.siloserver.silo.common.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvPlaybackOutputPolicyTest {
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
