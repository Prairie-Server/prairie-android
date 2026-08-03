package org.siloserver.silo.tv.ui.screens.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun weekStripMovesUpToActiveFilter() {
        assertEquals(
            CalendarUpFallbackAction.FocusFilter,
            calendarUpFallbackAction(null, 0, false, CalendarControlFocusZone.WeekStrip),
        )
    }

    @Test
    fun returnInFlightDoesNotRestartChoreography() {
        assertFalse(shouldReturnCalendarFocusToControls(2, 2, true))
    }

    @Test
    fun filterMovesUpToCalendarMenuTab() {
        assertEquals(
            CalendarUpFallbackAction.EnterMenu,
            calendarUpFallbackAction(null, 0, false, CalendarControlFocusZone.Filter),
        )
    }

    @Test
    fun heldUpOnControlsDoesNotSkipALayer() {
        assertEquals(
            CalendarUpFallbackAction.StayInContent,
            calendarUpFallbackAction(
                null,
                0,
                false,
                CalendarControlFocusZone.WeekStrip,
                isRepeat = true,
            ),
        )
    }
}
