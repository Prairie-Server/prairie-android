package org.siloserver.silo.tv.ui.screens.recommendations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class TvRecommendationsTopAnchorTest {

    @Test
    fun delayedRelocationAfterAnInitiallyCorrectTopIsReanchored() = runTest {
        var current = ForYouListPosition(0, 0)
        var corrections = 0

        maintainForYouTopAnchor(
            positionEvents = flow {
                emit(current)
                current = ForYouListPosition(1, 24)
                emit(current)
            },
            isFirstRowFocused = { true },
            awaitRelocation = {},
            currentPosition = { current },
            scrollToTop = {
                corrections += 1
                current = ForYouListPosition(0, 0)
            },
        )

        assertEquals(1, corrections)
    }

    @Test
    fun topOnlyPositionEventsDoNotScroll() = runTest {
        var corrections = 0

        maintainForYouTopAnchor(
            positionEvents = flowOf(ForYouListPosition(0, 0)),
            isFirstRowFocused = { true },
            awaitRelocation = {},
            currentPosition = { ForYouListPosition(0, 0) },
            scrollToTop = { corrections += 1 },
        )

        assertEquals(0, corrections)
    }

    @Test
    fun focusLossPreventsPendingCorrection() = runTest {
        var focused = true
        var corrections = 0
        val displaced = ForYouListPosition(1, 24)

        maintainForYouTopAnchor(
            positionEvents = flowOf(displaced),
            isFirstRowFocused = { focused },
            awaitRelocation = { focused = false },
            currentPosition = { displaced },
            scrollToTop = { corrections += 1 },
        )

        assertEquals(0, corrections)
    }
}
