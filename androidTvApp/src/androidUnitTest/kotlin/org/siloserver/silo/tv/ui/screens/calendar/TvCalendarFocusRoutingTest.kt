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
    fun nullControlZoneUsesNormalContentMovement() {
        assertEquals(
            CalendarUpFallbackAction.MoveWithinContent,
            calendarUpFallbackAction(null, 0, false, null),
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

    @Test
    fun heldUpBelowFirstShelfContinuesContentMovement() {
        assertEquals(
            CalendarUpFallbackAction.MoveWithinContent,
            calendarUpFallbackAction(
                focusedShelfIndex = 4,
                firstFocusableShelfIndex = 2,
                isReturningToControls = false,
                focusedControlZone = null,
                isRepeat = true,
            ),
        )
    }

    @Test
    fun heldUpFreezesWhileTheReturnToControlsIsStillInFlight() {
        // The handoff is under way but the shelf has not yet given up focus,
        // so it still reports its own index. Previously this fell through to
        // geometric movement and the held key walked back into the content.
        assertEquals(
            CalendarUpFallbackAction.StayInContent,
            calendarUpFallbackAction(
                focusedShelfIndex = 2,
                firstFocusableShelfIndex = 2,
                isReturningToControls = true,
                focusedControlZone = null,
                isRepeat = true,
            ),
        )
    }

    @Test
    fun heldUpFreezesInFlightEvenFromALowerShelf() {
        assertEquals(
            CalendarUpFallbackAction.StayInContent,
            calendarUpFallbackAction(
                focusedShelfIndex = 5,
                firstFocusableShelfIndex = 2,
                isReturningToControls = true,
                focusedControlZone = null,
                isRepeat = true,
            ),
        )
    }

    @Test
    fun aFreshUpDuringAnInFlightReturnStillMovesWithinContent() {
        // Only held repeats are frozen. A deliberate new press is the viewer
        // acting again, not the tail of the press that started the handoff.
        assertEquals(
            CalendarUpFallbackAction.MoveWithinContent,
            calendarUpFallbackAction(
                focusedShelfIndex = 5,
                firstFocusableShelfIndex = 2,
                isReturningToControls = true,
                focusedControlZone = null,
                isRepeat = false,
            ),
        )
    }
}
