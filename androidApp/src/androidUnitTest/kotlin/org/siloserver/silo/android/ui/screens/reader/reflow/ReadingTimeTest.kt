package org.siloserver.silo.android.ui.screens.reader.reflow
import kotlin.test.Test
import kotlin.test.assertEquals
class ReadingTimeTest {
    @Test fun `minutes remaining from total chars and progress`() {
        // 110_000 chars ≈ 20_000 words ≈ 100 min at 200 wpm; halfway → ~50 min
        assertEquals(50, estimateMinutesRemaining(totalChars = 110_000, bookProgression = 0.5))
    }
    @Test fun `finished book is zero minutes`() {
        assertEquals(0, estimateMinutesRemaining(totalChars = 110_000, bookProgression = 1.0))
    }
    @Test fun `clamps progress outside 0_1`() {
        assertEquals(0, estimateMinutesRemaining(totalChars = 110_000, bookProgression = 1.5))
        assertEquals(100, estimateMinutesRemaining(totalChars = 110_000, bookProgression = -0.2))
    }
}
