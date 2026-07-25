package org.prairieserver.prairie.tv.ui.screens.calendar

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvCalendarFocusRoutingTest {
    @Test
    fun firstFocusableShelfReturnsToControls() {
        assertTrue(shouldReturnCalendarFocusToControls(2, 2, false))
    }

    @Test
    fun laterShelfUsesNormalUpMovement() {
        assertFalse(shouldReturnCalendarFocusToControls(4, 2, false))
    }

    @Test
    fun controlsUseNormalUpMovement() {
        assertFalse(shouldReturnCalendarFocusToControls(null, 2, false))
    }

    @Test
    fun returnInFlightDoesNotRestartChoreography() {
        assertFalse(shouldReturnCalendarFocusToControls(2, 2, true))
    }
}
