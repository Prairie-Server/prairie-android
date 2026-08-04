package org.siloserver.silo.tv.ui.focus

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TvContentInitialFocusTest {

    @Test
    fun `a request rejected during placement is retried until focus is observed`() {
        runTest {
            var requests = 0
            var focused = false

            val result = requestTvContentInitialFocus(
                awaitAttempt = {},
                isContentFocused = { focused },
                requestFocus = {
                    requests++
                    // The lazy grid places the first item on the third frame.
                    if (requests < 3) false else true.also { focused = true }
                },
            )

            assertEquals(TvObservedFocusResult.Focused, result)
            assertEquals(3, requests)
        }
    }

    @Test
    fun `a request that is accepted but never observed exhausts rather than latching success`() {
        runTest {
            var requests = 0

            val result = requestTvContentInitialFocus(
                awaitAttempt = {},
                isContentFocused = { false },
                requestFocus = { true.also { requests++ } },
            )

            // The old code treated this exact case as success and latched it.
            assertEquals(TvObservedFocusResult.Exhausted, result)
            assertEquals(TvContentInitialFocusMaxAttempts, requests)
        }
    }

    @Test
    fun `content that already owns focus is never asked again, so refresh cannot steal it`() {
        runTest {
            var requests = 0

            val result = requestTvContentInitialFocus(
                awaitAttempt = {},
                isContentFocused = { true },
                requestFocus = { true.also { requests++ } },
            )

            assertEquals(TvObservedFocusResult.Focused, result)
            assertEquals(0, requests)
        }
    }

    @Test
    fun `a throwing requester does not end acquisition`() {
        runTest {
            var requests = 0
            var focused = false

            val result = requestTvContentInitialFocus(
                awaitAttempt = {},
                isContentFocused = { focused },
                requestFocus = {
                    requests++
                    // A detached requester throws rather than returning false.
                    if (requests == 1) error("not attached")
                    true.also { focused = true }
                },
            )

            assertEquals(TvObservedFocusResult.Focused, result)
            assertEquals(2, requests)
        }
    }

    @Test
    fun `no anchoring pass runs without content`() {
        assertEquals(false, shouldRequestTvContentInitialFocus(contentKey = null, contentHasFocus = false))
        assertEquals(false, shouldRequestTvContentInitialFocus(contentKey = null, contentHasFocus = true))
    }

    @Test
    fun `a viewer already inside the content is never pulled back by a refresh`() {
        assertEquals(false, shouldRequestTvContentInitialFocus(contentKey = "collection-1", contentHasFocus = true))
    }

    @Test
    fun `content that arrives with nothing focused is anchored`() {
        assertEquals(true, shouldRequestTvContentInitialFocus(contentKey = "collection-1", contentHasFocus = false))
    }

    @Test
    fun `returning to content that was focused before is anchored again, not suppressed`() {
        // The wedge an "already acquired" latch would create: after the first
        // item goes away and comes back, or after a different key exhausts in
        // between, nothing holds focus and the pass must still run.
        assertEquals(true, shouldRequestTvContentInitialFocus(contentKey = "collection-1", contentHasFocus = false))
        assertEquals(true, shouldRequestTvContentInitialFocus(contentKey = "collection-2", contentHasFocus = false))
        assertEquals(true, shouldRequestTvContentInitialFocus(contentKey = "collection-1", contentHasFocus = false))
    }

    @Test
    fun `the attempt budget covers the acquisition window`() {
        assertEquals(
            TvFocusAcquisitionBudgetMillis,
            TvContentInitialFocusMaxAttempts * 60L,
        )
    }
}
