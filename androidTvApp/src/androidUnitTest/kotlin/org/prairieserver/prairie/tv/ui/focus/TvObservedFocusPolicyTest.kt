package org.prairieserver.prairie.tv.ui.focus

import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals

class TvObservedFocusPolicyTest {
    @Test
    fun requestOutcomeDistinguishesRejectionAcceptanceAndObservation() {
        assertEquals(
            TvFocusRequestOutcome.Rejected,
            observeTvFocusRequest(requestAccepted = false, isFocused = false),
        )
        assertEquals(
            TvFocusRequestOutcome.AcceptedUnobserved,
            observeTvFocusRequest(requestAccepted = true, isFocused = false),
        )
        assertEquals(
            TvFocusRequestOutcome.Focused,
            observeTvFocusRequest(requestAccepted = true, isFocused = true),
        )
    }

    @Test
    fun rejectedAndThrowingRequestsRetryUntilFocusIsObserved() = runTest {
        var requests = 0
        var focused = false

        val result = requestFocusUntilObserved(
            maxAttempts = 5,
            awaitAttempt = {},
            targetState = { TvFocusTargetState.Ready },
            requestFocus = {
                requests++
                when (requests) {
                    1 -> false
                    2 -> error("detached")
                    else -> true.also { focused = true }
                }
            },
            isFocused = { focused },
        )

        assertEquals(TvObservedFocusResult.Focused, result)
        assertEquals(3, requests)
    }

    @Test
    fun acceptedButUnobservedRequestsExhaustTheBudget() = runTest {
        var requests = 0

        val result = requestFocusUntilObserved(
            maxAttempts = 4,
            awaitAttempt = {},
            targetState = { TvFocusTargetState.Ready },
            requestFocus = { true.also { requests++ } },
            isFocused = { false },
        )

        assertEquals(TvObservedFocusResult.Exhausted, result)
        assertEquals(4, requests)
    }

    @Test
    fun notReadyTargetsWaitWithoutRequesting() = runTest {
        var frames = 0
        var requests = 0
        var focused = false

        val result = requestFocusUntilObserved(
            maxAttempts = 5,
            awaitAttempt = { frames++ },
            targetState = {
                if (frames < 3) TvFocusTargetState.NotReady else TvFocusTargetState.Ready
            },
            requestFocus = {
                requests++
                true.also { focused = true }
            },
            isFocused = { focused },
        )

        assertEquals(TvObservedFocusResult.Focused, result)
        assertEquals(1, requests)
        assertEquals(3, frames)
    }

    @Test
    fun disposedTargetStopsWithoutRequestingAgain() = runTest {
        var frames = 0
        var requests = 0

        val result = requestFocusUntilObserved(
            maxAttempts = 5,
            awaitAttempt = { frames++ },
            targetState = {
                if (frames < 2) TvFocusTargetState.Ready else TvFocusTargetState.Disposed
            },
            requestFocus = { false.also { requests++ } },
            isFocused = { false },
        )

        assertEquals(TvObservedFocusResult.Disposed, result)
        assertEquals(1, requests)
    }

    @Test
    fun existingObservedFocusCompletesWithoutRequesting() = runTest {
        var requests = 0

        val result = requestFocusUntilObserved(
            maxAttempts = 3,
            awaitAttempt = {},
            targetState = { TvFocusTargetState.Ready },
            requestFocus = { true.also { requests++ } },
            isFocused = { true },
        )

        assertEquals(TvObservedFocusResult.Focused, result)
        assertEquals(0, requests)
    }

    @Test
    fun cancellationFromFocusRequestEscapes() = runTest {
        val cancellation = CancellationException("cancelled")

        val thrown = try {
            requestFocusUntilObserved(
                maxAttempts = 1,
                awaitAttempt = {},
                targetState = { TvFocusTargetState.Ready },
                requestFocus = { throw cancellation },
                isFocused = { false },
            )
            null
        } catch (thrown: CancellationException) {
            thrown
        }

        assertEquals(cancellation, thrown)
    }
}
