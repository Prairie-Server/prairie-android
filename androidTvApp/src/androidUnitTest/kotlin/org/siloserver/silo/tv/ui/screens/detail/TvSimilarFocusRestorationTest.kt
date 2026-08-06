package org.siloserver.silo.tv.ui.screens.detail

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvSimilarFocusRestorationTest {
    @Test
    fun attachmentTimeoutFallsBackToTheHero() = runTest {
        var fallbackScrolled = false
        var fallbackRequests = 0

        val result = restoreMoreLikeThisFocus(
            awaitTarget = { },
            stillOwned = { true },
            onTargetResolved = { },
            isTargetFocused = { false },
            requestTargetFocus = { },
            awaitFocusAttempt = { awaitCancellation() },
            scrollToFallback = { fallbackScrolled = true },
            requestFallbackFocus = { fallbackRequests += 1 },
            dataTimeoutMillis = 100,
            attachmentTimeoutMillis = 100,
        )

        assertEquals(TvSimilarFocusRestoreResult.Fallback, result)
        assertTrue(fallbackScrolled)
        assertEquals(1, fallbackRequests)
    }

    @Test
    fun ownershipRevokedMidLoopStopsRequestsWithoutFallback() = runTest {
        var owned = true
        var targetRequests = 0
        var fallbackScrolled = false
        var fallbackRequested = false

        val result = restoreMoreLikeThisFocus(
            awaitTarget = { },
            stillOwned = { owned },
            onTargetResolved = { },
            isTargetFocused = { false },
            requestTargetFocus = { targetRequests += 1 },
            awaitFocusAttempt = { owned = false },
            scrollToFallback = { fallbackScrolled = true },
            requestFallbackFocus = { fallbackRequested = true },
            dataTimeoutMillis = 100,
            attachmentTimeoutMillis = 100,
        )

        assertEquals(TvSimilarFocusRestoreResult.Revoked, result)
        assertEquals(1, targetRequests)
        assertFalse(fallbackScrolled)
        assertFalse(fallbackRequested)
    }
}
