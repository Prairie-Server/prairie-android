package org.siloserver.silo.common.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiloLoadControlTest {
    @Test
    fun `average bitrate takes precedence over peak bitrate`() {
        val selected =
            selectBufferSizingBitrateBps(
                listOf(
                    BufferSizingTrackBitrates(
                        averageBitrateBps = 4_000_000,
                        peakBitrateBps = 9_000_000,
                        latestNetworkEstimateBps = 100_000_000L,
                    ),
                ),
            )

        assertEquals(4_000_000L, selected)
    }

    @Test
    fun `peak bitrate is used when average bitrate is invalid`() {
        val selected =
            selectBufferSizingBitrateBps(
                listOf(
                    BufferSizingTrackBitrates(
                        averageBitrateBps = -1,
                        peakBitrateBps = 9_000_000,
                        latestNetworkEstimateBps = 100_000_000L,
                    ),
                ),
            )

        assertEquals(9_000_000L, selected)
    }

    @Test
    fun `known selected media bitrates are summed and network capacity is ignored`() {
        val selected =
            selectBufferSizingBitrateBps(
                listOf(
                    BufferSizingTrackBitrates(4_000_000, 8_000_000, 100_000_000L),
                    BufferSizingTrackBitrates(-1, 192_000, 100_000_000L),
                ),
            )

        assertEquals(4_192_000L, selected)
    }

    @Test
    fun `one known media rate suppresses network fallback from metadata-poor tracks`() {
        val selected =
            selectBufferSizingBitrateBps(
                listOf(
                    BufferSizingTrackBitrates(4_000_000, 8_000_000, 100_000_000L),
                    BufferSizingTrackBitrates(-1, -1, 100_000_000L),
                ),
            )

        assertEquals(4_000_000L, selected)
    }

    @Test
    fun `largest network estimate is the last resort when all media metadata is invalid`() {
        val selected =
            selectBufferSizingBitrateBps(
                listOf(
                    BufferSizingTrackBitrates(0, -1, 18_000_000L),
                    BufferSizingTrackBitrates(-1, 0, 25_000_000L),
                ),
            )

        assertEquals(25_000_000L, selected)
    }

    @Test
    fun `unknown bitrate remains unknown when metadata and network estimates are invalid`() {
        val selected =
            selectBufferSizingBitrateBps(
                listOf(
                    BufferSizingTrackBitrates(-1, 0, -1L),
                ),
            )

        assertNull(selected)
    }

    @Test
    fun `empty track selection remains unknown`() {
        assertNull(selectBufferSizingBitrateBps(emptyList()))
    }
}
