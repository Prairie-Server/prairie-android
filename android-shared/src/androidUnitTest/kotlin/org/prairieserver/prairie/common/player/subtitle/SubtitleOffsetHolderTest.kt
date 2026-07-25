package org.prairieserver.prairie.common.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleOffsetHolderTest {

    @Test
    fun `default offset is zero`() {
        val h = SubtitleOffsetHolder()
        assertEquals(0L, h.getOffsetUs())
        assertEquals(0, h.getOffsetMs())
    }

    @Test
    fun `setOffsetMs stores microseconds`() {
        val h = SubtitleOffsetHolder()
        h.setOffsetMs(250)
        assertEquals(250_000L, h.getOffsetUs())
        assertEquals(250, h.getOffsetMs())
    }

    @Test
    fun `negative offset preserves sign`() {
        val h = SubtitleOffsetHolder()
        h.setOffsetMs(-100)
        assertEquals(-100_000L, h.getOffsetUs())
        assertEquals(-100, h.getOffsetMs())
    }

    @Test
    fun `setOffsetMs is idempotent`() {
        val h = SubtitleOffsetHolder()
        h.setOffsetMs(42)
        h.setOffsetMs(42)
        assertEquals(42_000L, h.getOffsetUs())
    }

    @Test
    fun `timeline offset maps source cues onto the local player timeline`() {
        val h = SubtitleOffsetHolder()
        h.setTimelineOffsetSeconds(2_400.0)

        assertEquals(-2_400_000_000L, h.getOffsetUs())
        assertEquals(0L, h.getUserOffsetUs())
        assertEquals(0, h.getOffsetMs())
    }

    @Test
    fun `user sync composes with playback timeline offset`() {
        val h = SubtitleOffsetHolder()
        h.setOffsetMs(250)
        h.setTimelineOffsetSeconds(2_400.0)

        assertEquals(-2_399_750_000L, h.getOffsetUs())
        assertEquals(250_000L, h.getUserOffsetUs())
        assertEquals(250, h.getOffsetMs())
    }

    @Test
    fun `invalid timeline offset is ignored`() {
        val h = SubtitleOffsetHolder()
        h.setTimelineOffsetSeconds(Double.NaN)

        assertEquals(0L, h.getOffsetUs())
    }
}
