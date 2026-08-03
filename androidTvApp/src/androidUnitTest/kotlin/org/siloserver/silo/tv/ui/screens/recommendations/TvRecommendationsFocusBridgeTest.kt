package org.siloserver.silo.tv.ui.screens.recommendations

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvRecommendationsFocusBridgeTest {

    private val target = ForYouFocusTarget("because-you-watched", "movie-b", 1, 2)

    @Test
    fun exactReturnTargetUsesStableIdsAfterReorder() {
        val resolved = resolveForYouReturnTarget(
            target,
            listOf(
                ForYouFocusRow("because-you-watched", listOf("movie-c", "movie-b", "movie-a")),
                ForYouFocusRow("trending", listOf("movie-d")),
            ),
        )

        assertEquals(ResolvedForYouFocusTarget(0, 1, true), resolved)
    }

    @Test
    fun missingCardUsesClosestIndexInSameSection() {
        val resolved = resolveForYouReturnTarget(
            target,
            listOf(ForYouFocusRow("because-you-watched", listOf("movie-a", "movie-c"))),
        )

        assertEquals(ResolvedForYouFocusTarget(0, 1, false), resolved)
    }

    @Test
    fun missingSectionUsesClosestRowFirstCard() {
        val resolved = resolveForYouReturnTarget(
            target,
            listOf(
                ForYouFocusRow("row-a", listOf("a")),
                ForYouFocusRow("row-b", listOf("b")),
            ),
        )

        assertEquals(ResolvedForYouFocusTarget(1, 0, false), resolved)
    }

    @Test
    fun emptyFeedHasNoCardReturnTarget() {
        assertEquals(null, resolveForYouReturnTarget(target, emptyList()))
    }

    @Test
    fun emptyFeedFallsBackToForYouFilter() {
        assertTrue(shouldFallbackForYouReturnToFilter(resolveForYouReturnTarget(target, emptyList())))
        assertFalse(
            shouldFallbackForYouReturnToFilter(
                ResolvedForYouFocusTarget(rowIndex = 0, cardIndex = 0, exact = true),
            ),
        )
    }

    @Test
    fun handoffCrossesRowRestorerBeforeTargetingFirstCard() = runTest {
        val events = mutableListOf<String>()

        val handled = requestRecommendationRowFocus(
            requestRowContainer = { events += "row"; true },
            awaitFrame = { events += "frame" },
            requestFirstCard = { events += "card"; true },
        )

        assertTrue(handled)
        assertEquals(listOf("row", "frame", "card"), events)
    }

    @Test
    fun rejectedRowHopDoesNotTargetCard() = runTest {
        val events = mutableListOf<String>()

        val handled = requestRecommendationRowFocus(
            requestRowContainer = { events += "row"; false },
            awaitFrame = { events += "frame" },
            requestFirstCard = { events += "card"; true },
        )

        assertFalse(handled)
        assertEquals(listOf("row"), events)
    }

    @Test
    fun rejectedCardRequestCanBeRetried() = runTest {
        val handled = requestRecommendationRowFocus(
            requestRowContainer = { true },
            awaitFrame = {},
            requestFirstCard = { false },
        )
        assertFalse(handled)
    }

    @Test
    fun forYouWithVisibleRowsUsesTheBridge() {
        assertTrue(
            shouldBridgeRecommendationsDown(
                showingRecommendations = true,
                hasVisibleRecommendations = true,
            ),
        )
    }

    @Test
    fun savedListsKeepTheirExistingGridNavigation() {
        assertFalse(
            shouldBridgeRecommendationsDown(
                showingRecommendations = false,
                hasVisibleRecommendations = true,
            ),
        )
    }

    @Test
    fun loadingOrEmptyForYouDoesNotTargetAnAbsentRow() {
        assertFalse(
            shouldBridgeRecommendationsDown(
                showingRecommendations = true,
                hasVisibleRecommendations = false,
            ),
        )
    }
}
