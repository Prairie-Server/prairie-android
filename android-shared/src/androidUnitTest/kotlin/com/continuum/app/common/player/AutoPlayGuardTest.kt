package com.continuum.app.common.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoPlayGuardTest {
    @Test fun gatesAfterThresholdRecordedAdvances() {
        val guard = AutoPlayGuard(threshold = { 3 })
        guard.recordAutoAdvance() // 1
        guard.recordAutoAdvance() // 2
        assertFalse(guard.shouldGate())
        guard.recordAutoAdvance() // 3 — threshold reached; next auto-advance gates
        assertTrue(guard.shouldGate())
    }

    /**
     * Encodes the REQUIRED VM sequence (check shouldGate() first, record only
     * when not gated). With threshold 3, exactly 3 auto-advances run before the
     * prompt appears (i.e. the 4th is gated).
     */
    @Test fun vmSequenceAllowsThresholdAdvancesThenGates() {
        val guard = AutoPlayGuard(threshold = { 3 })
        var advances = 0
        var gatedAt = -1
        repeat(6) { attempt ->
            if (guard.shouldGate()) {
                if (gatedAt < 0) gatedAt = attempt
            } else {
                guard.recordAutoAdvance()
                advances += 1
            }
        }
        assertEquals(3, advances)
        assertEquals(3, gatedAt) // attempts 0,1,2 advanced; attempt 3 gated
    }

    @Test fun userActionResetsCounter() {
        val guard = AutoPlayGuard(threshold = { 3 })
        repeat(3) { guard.recordAutoAdvance() }
        assertTrue(guard.shouldGate())
        guard.recordUserAction()
        assertFalse(guard.shouldGate())
    }

    @Test fun continueAfterPromptResetsAndAllowsMore() {
        val guard = AutoPlayGuard(threshold = { 3 })
        repeat(3) { guard.recordAutoAdvance() }
        guard.recordUserAction() // user tapped "Continue"
        guard.recordAutoAdvance() // 1
        assertFalse(guard.shouldGate())
    }

    @Test fun thresholdZeroNeverGates() {
        val guard = AutoPlayGuard(threshold = { 0 })
        repeat(10) { guard.recordAutoAdvance() }
        assertFalse(guard.shouldGate())
    }

    @Test fun negativeThresholdNeverGates() {
        val guard = AutoPlayGuard(threshold = { -1 })
        repeat(5) { guard.recordAutoAdvance() }
        assertFalse(guard.shouldGate())
    }

    @Test fun thresholdIsReadLazilyOnEachCheck() {
        // The provider can change between checks (the user edits the setting
        // mid-session): gating must reflect the latest value.
        var threshold = 3
        val guard = AutoPlayGuard(threshold = { threshold })
        repeat(3) { guard.recordAutoAdvance() }
        assertTrue(guard.shouldGate())
        threshold = 0 // user switched pass-out protection off
        assertFalse(guard.shouldGate())
    }
}
