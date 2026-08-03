package org.siloserver.silo.tv.ui.screens.recommendations

import kotlinx.coroutines.test.runTest
import org.siloserver.silo.tv.ui.components.prepareTvMediaRowFocusRestore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    fun rejectedFocusRequestRemainsRetryable() {
        assertEquals(FocusRequestOutcome.Rejected, requestFocusSafely { false })
    }

    @Test
    fun focusRequesterExceptionStopsRetries() {
        assertEquals(
            FocusRequestOutcome.Disposed,
            requestFocusSafely { error("FocusRequester is not initialized") },
        )
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

    @Test
    fun successfulReturnIsConsumedUntilANewDetailReturnBegins() {
        val pending = beginForYouDetailReturn(previousRequestId = 4)
        val rows = listOf(
            ForYouFocusRow("because-you-watched", listOf("movie-a", "movie-b")),
        )

        assertEquals(
            ForYouReturnFocusLocation(
                requestId = 5,
                rowIndex = 0,
                cardIndex = 1,
                sectionId = "because-you-watched",
                contentId = "movie-b",
            ),
            resolvePendingForYouReturnLocation(pending, target, rows),
        )

        val consumed = consumeForYouDetailReturn(pending, completedRequestId = 5)
        assertFalse(consumed.pending)
        val ordinaryFocusTarget = ForYouFocusTarget("trending", "movie-z", 1, 0)
        val refreshedRows = listOf(
            ForYouFocusRow("inserted", listOf("movie-new")),
            ForYouFocusRow("trending", listOf("movie-z")),
            rows.single(),
        )
        assertNull(
            resolvePendingForYouReturnLocation(
                consumed,
                ordinaryFocusTarget,
                refreshedRows,
            ),
        )
        assertEquals(consumed, consumeForYouDetailReturn(consumed, completedRequestId = 5))

        val nextReturn = beginForYouDetailReturn(previousRequestId = consumed.requestId)
        assertTrue(nextReturn.pending)
        assertEquals(6, nextReturn.requestId)
    }

    @Test
    fun staleCompletionCannotConsumeANewerReturn() {
        val newer = beginForYouDetailReturn(previousRequestId = 8)

        assertEquals(
            newer,
            consumeForYouDetailReturn(newer, completedRequestId = 8),
        )
    }

    @Test
    fun offscreenReorderedTargetWaitsForAttachmentThenFocuses() = runTest {
        val reorderedRows = listOf(
            ForYouFocusRow(
                "because-you-watched",
                listOf(
                    "movie-a",
                    "movie-c",
                    "movie-d",
                    "movie-e",
                    "movie-f",
                    "movie-g",
                    "movie-h",
                    "movie-i",
                    "movie-b",
                ),
            ),
        )
        val location = resolvePendingForYouReturnLocation(
            beginForYouDetailReturn(previousRequestId = 0),
            target,
            reorderedRows,
        )
        assertEquals(8, location?.cardIndex)

        val events = mutableListOf<String>()
        val prepared = prepareTvMediaRowFocusRestore(
            requestId = location?.requestId ?: 0,
            restoreFocusIndex = location?.cardIndex ?: -1,
            itemCount = reorderedRows.single().contentIds.size,
            scrollToItem = { index -> events += "scroll:$index" },
        )
        assertTrue(prepared)

        var frames = 0
        var rowRequests = 0
        var cardRequests = 0
        val result = requestPendingForYouReturnFocus(
            maxAttempts = 6,
            awaitFrame = { frames++ },
            targetState = {
                if (frames < 3) ForYouReturnTargetState.NotAttached
                else ForYouReturnTargetState.Attached
            },
            requestRowContainer = {
                rowRequests++
                events += "row"
                FocusRequestOutcome.Handled
            },
            awaitRowFrame = { events += "row-frame" },
            requestCard = {
                cardRequests++
                events += "card"
                FocusRequestOutcome.Handled
            },
        )

        assertEquals(ForYouReturnFocusResult.Focused, result)
        assertEquals(3, frames)
        assertEquals(1, rowRequests)
        assertEquals(1, cardRequests)
        assertEquals(listOf("scroll:8", "row", "row-frame", "card"), events)
    }

    @Test
    fun genuineTargetDisposalStopsBoundedRetries() = runTest {
        var frames = 0
        var focusRequests = 0

        val result = requestPendingForYouReturnFocus(
            maxAttempts = 6,
            awaitFrame = { frames++ },
            targetState = { ForYouReturnTargetState.Disposed },
            requestRowContainer = {
                focusRequests++
                FocusRequestOutcome.Handled
            },
            awaitRowFrame = {},
            requestCard = {
                focusRequests++
                FocusRequestOutcome.Handled
            },
        )

        assertEquals(ForYouReturnFocusResult.Disposed, result)
        assertEquals(1, frames)
        assertEquals(0, focusRequests)
    }
}
