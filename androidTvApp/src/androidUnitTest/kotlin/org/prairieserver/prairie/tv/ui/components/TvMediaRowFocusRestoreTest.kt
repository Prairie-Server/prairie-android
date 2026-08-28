package org.prairieserver.prairie.tv.ui.components

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvMediaRowFocusRestoreTest {
    @Test
    fun pendingOffscreenReturnScrollsToResolvedCardIndex() = runTest {
        var scrolledTo: Int? = null

        val prepared = prepareTvMediaRowFocusRestore(
            requestId = 3,
            restoreFocusIndex = 8,
            itemCount = 10,
            scrollToItem = { scrolledTo = it },
        )

        assertTrue(prepared)
        assertEquals(8, scrolledTo)
    }

    @Test
    fun ordinaryRowRenderingPreservesHorizontalPosition() = runTest {
        var scrollCalls = 0

        val prepared = prepareTvMediaRowFocusRestore(
            requestId = 0,
            restoreFocusIndex = 8,
            itemCount = 10,
            scrollToItem = { scrollCalls++ },
        )

        assertFalse(prepared)
        assertEquals(0, scrollCalls)
    }
}
