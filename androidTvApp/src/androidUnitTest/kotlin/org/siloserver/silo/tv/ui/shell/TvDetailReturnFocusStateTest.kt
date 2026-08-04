package org.siloserver.silo.tv.ui.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvDetailReturnFocusStateTest {
    @Test
    fun requestedHomeRetryKeepsCardFallbackPending() {
        val state = beginHomeDetailReturnRetry(previousRequestId = 7, needsRetry = true)

        assertEquals(8, state.requestId)
        assertTrue(state.needsRetry)
        assertTrue(state.fallbackPending)
    }

    @Test
    fun completedHomeRetryClearsRetryAndFallback() {
        val completed = completeHomeDetailReturnRetry(
            HomeDetailReturnFocusState(requestId = 8, needsRetry = true, fallbackPending = true),
        )

        assertEquals(8, completed.requestId)
        assertFalse(completed.needsRetry)
        assertFalse(completed.fallbackPending)
    }

    @Test
    fun explicitHomeSelectionResetsReturnState() {
        assertEquals(
            HomeDetailReturnFocusState(),
            resetHomeDetailReturnFocus(),
        )
    }

    @Test
    fun nonHomeRetryDoesNotArmHomeFallback() {
        val state = beginHomeDetailReturnRetryIfHome(
            previousState = HomeDetailReturnFocusState(),
            isHomeDetailReturn = false,
            needsRetry = true,
        )

        assertEquals(0, state.requestId)
        assertFalse(state.needsRetry)
        assertFalse(state.fallbackPending)
    }
}
