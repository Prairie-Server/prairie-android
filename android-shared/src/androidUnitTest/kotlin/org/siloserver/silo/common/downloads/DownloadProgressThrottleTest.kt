package org.siloserver.silo.common.downloads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadProgressThrottleTest {

    @Test
    fun `first bytes report and update the foreground notification`() {
        val throttle = DownloadProgressThrottle(intervalMs = 200)
        val decision = throttle.onBytes(nowMs = 0, written = 1, total = 1000)
        assertTrue(decision.report)
        assertTrue(decision.updateForeground)
        assertEquals(0, decision.percent)
    }

    @Test
    fun `large byte deltas inside the interval do not report`() {
        // Regression: the old gate also fired on every 1 MB of bytes, which
        // on a fast network meant ~100 notification rebuilds per second.
        val throttle = DownloadProgressThrottle(intervalMs = 200)
        throttle.onBytes(nowMs = 0, written = 1, total = 10_000_000_000)
        for (mb in 1..50) {
            val decision = throttle.onBytes(nowMs = 100, written = mb * 1_048_576L, total = 10_000_000_000)
            assertFalse(decision.report)
            assertFalse(decision.updateForeground)
        }
    }

    @Test
    fun `report after the interval without a percent change skips the foreground update`() {
        val throttle = DownloadProgressThrottle(intervalMs = 200)
        throttle.onBytes(nowMs = 0, written = 100, total = 1_000_000)                     // 0%
        val decision = throttle.onBytes(nowMs = 250, written = 200, total = 1_000_000)    // still 0%
        assertTrue(decision.report)
        assertFalse(decision.updateForeground)
    }

    @Test
    fun `report after the interval with a percent change updates the foreground`() {
        val throttle = DownloadProgressThrottle(intervalMs = 200)
        throttle.onBytes(nowMs = 0, written = 0, total = 1000)                            // 0%
        val decision = throttle.onBytes(nowMs = 250, written = 370, total = 1000)         // 37%
        assertTrue(decision.report)
        assertTrue(decision.updateForeground)
        assertEquals(37, decision.percent)
    }

    @Test
    fun `unknown total reports zero percent with a single foreground update`() {
        val throttle = DownloadProgressThrottle(intervalMs = 200)
        val first = throttle.onBytes(nowMs = 0, written = 500, total = -1)
        assertTrue(first.updateForeground)
        assertEquals(0, first.percent)
        val second = throttle.onBytes(nowMs = 300, written = 5_000_000, total = -1)
        assertTrue(second.report)
        assertFalse(second.updateForeground)
    }

    @Test
    fun `percent clamps and tolerates zero total`() {
        assertEquals(100, DownloadProgressThrottle.percentOf(written = 2000, total = 1000))
        assertEquals(0, DownloadProgressThrottle.percentOf(written = 2000, total = 0))
    }
}
