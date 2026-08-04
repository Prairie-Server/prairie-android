package org.siloserver.silo.tv.ui.components

import kotlinx.coroutines.test.runTest
import org.siloserver.silo.tv.ui.focus.TvObservedFocusResult
import kotlin.test.Test
import kotlin.test.assertEquals

class TvDialogInitialFocusTest {
    @Test
    fun unobservedDialogFocusStopsAtTheFixedBudget() = runTest {
        var attempts = 0

        val result = requestTvDialogInitialFocus(
            awaitAttempt = {},
            isOverlayFocused = { false },
            requestFocus = { true.also { attempts++ } },
        )

        assertEquals(TvObservedFocusResult.Exhausted, result)
        assertEquals(TvDialogInitialFocusMaxAttempts, attempts)
    }

    @Test
    fun focusOnAnyDialogChildStopsTargetRequests() = runTest {
        var overlayFocused = false
        var attempts = 0

        val result = requestTvDialogInitialFocus(
            awaitAttempt = {
                if (attempts == 1) overlayFocused = true
            },
            isOverlayFocused = { overlayFocused },
            requestFocus = { false.also { attempts++ } },
        )

        assertEquals(TvObservedFocusResult.Focused, result)
        assertEquals(1, attempts)
    }
}
