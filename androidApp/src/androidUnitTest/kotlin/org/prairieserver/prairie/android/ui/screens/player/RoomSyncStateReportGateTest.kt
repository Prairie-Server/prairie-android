package org.prairieserver.prairie.android.ui.screens.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoomSyncStateReportGateTest {

    private val cadenceMs = 1_500L
    private val suppressWindowMs = 250L

    @Test fun reports_when_cadence_elapsed_and_no_pending_command() {
        assertTrue(
            shouldEmitStateReport(
                nowMs = 2_000L, lastReportMs = 0L, cadenceMs = cadenceMs,
                pendingExecuteAtMs = null, suppressWindowMs = suppressWindowMs,
            ),
        )
    }

    @Test fun does_not_report_before_cadence_elapsed() {
        assertFalse(
            shouldEmitStateReport(
                nowMs = 1_000L, lastReportMs = 0L, cadenceMs = cadenceMs,
                pendingExecuteAtMs = null, suppressWindowMs = suppressWindowMs,
            ),
        )
    }

    @Test fun suppresses_within_window_of_pending_execute() {
        // cadence elapsed, but a command executes at 2100 and now=2000 → within 250ms
        assertFalse(
            shouldEmitStateReport(
                nowMs = 2_000L, lastReportMs = 0L, cadenceMs = cadenceMs,
                pendingExecuteAtMs = 2_100L, suppressWindowMs = suppressWindowMs,
            ),
        )
    }

    @Test fun reports_when_pending_execute_is_far_away() {
        assertTrue(
            shouldEmitStateReport(
                nowMs = 2_000L, lastReportMs = 0L, cadenceMs = cadenceMs,
                pendingExecuteAtMs = 5_000L, suppressWindowMs = suppressWindowMs,
            ),
        )
    }

    @Test fun suppresses_just_after_execute_too() {
        assertFalse(
            shouldEmitStateReport(
                nowMs = 2_000L, lastReportMs = 0L, cadenceMs = cadenceMs,
                pendingExecuteAtMs = 1_900L, suppressWindowMs = suppressWindowMs,
            ),
        )
    }
}
