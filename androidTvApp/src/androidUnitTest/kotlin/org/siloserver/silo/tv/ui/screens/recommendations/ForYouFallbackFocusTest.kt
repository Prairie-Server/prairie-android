package org.siloserver.silo.tv.ui.screens.recommendations

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Cover for a For You dead end: returning from a detail screen could leave the
 * screen with no focus owner at all, so the D-pad stopped doing anything.
 *
 * When the retry loop could not reach the launch card it reported `Exhausted`,
 * and the entire recovery was one unchecked `requestFocusSafely` call. A
 * rejected or disposed result was discarded — no retry, no other candidate, and
 * no log, because requestFocusSafely turns the "not initialized" throw into a
 * value rather than letting it surface.
 *
 * The recovery now walks the filter pills, which are composed for the life of
 * the screen, and reports whether anything actually took focus.
 */
class ForYouFallbackFocusTest {

    @Test
    fun `claims the first candidate that accepts focus`() = runTest {
        val tried = mutableListOf<String>()
        val claimed = claimForYouFallbackFocus(
            attempts = 3,
            awaitFrame = {},
            candidates = listOf(
                { tried += "forYou"; true },
                { tried += "watchlist"; true },
            ),
        )
        assertTrue(claimed)
        assertEquals(listOf("forYou"), tried, "later candidates should not be tried once one takes focus")
    }

    @Test
    fun `falls through to a later candidate when earlier ones reject`() = runTest {
        val tried = mutableListOf<String>()
        val claimed = claimForYouFallbackFocus(
            attempts = 1,
            awaitFrame = {},
            candidates = listOf(
                { tried += "forYou"; false },
                { tried += "watchlist"; false },
                { tried += "favorites"; true },
            ),
        )
        assertTrue(claimed)
        assertEquals(listOf("forYou", "watchlist", "favorites"), tried)
    }

    @Test
    fun `retries across frames rather than giving up on the first miss`() = runTest {
        // The pills are not attached on the first frame after the pop; this is
        // the case the old single-shot call lost.
        var frame = 0
        val claimed = claimForYouFallbackFocus(
            attempts = 4,
            awaitFrame = { frame++ },
            candidates = listOf({ frame >= 3 }),
        )
        assertTrue(claimed, "a candidate that attaches on a later frame should still be claimed")
    }

    @Test
    fun `a candidate that throws is treated as not yet attached, not fatal`() = runTest {
        // FocusRequester.requestFocus throws when the requester is not attached
        // to any node; requestFocusSafely maps that to Disposed. It must not end
        // the loop, because the node can attach on a later frame.
        var attempt = 0
        val claimed = claimForYouFallbackFocus(
            attempts = 3,
            awaitFrame = {},
            candidates = listOf({
                attempt++
                if (attempt < 3) error("FocusRequester is not initialized") else true
            }),
        )
        assertTrue(claimed)
        assertEquals(3, attempt)
    }

    @Test
    fun `reports failure when nothing can take focus`() = runTest {
        // The caller logs on false. Silence here is what left the screen dead.
        val claimed = claimForYouFallbackFocus(
            attempts = 3,
            awaitFrame = {},
            candidates = listOf({ false }, { error("not initialized") }),
        )
        assertFalse(claimed)
    }

    @Test
    fun `reports failure when there are no candidates at all`() = runTest {
        assertFalse(claimForYouFallbackFocus(attempts = 3, awaitFrame = {}, candidates = emptyList()))
    }
}
