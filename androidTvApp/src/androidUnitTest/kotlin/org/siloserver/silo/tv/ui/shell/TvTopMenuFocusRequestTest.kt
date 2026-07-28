package org.siloserver.silo.tv.ui.shell

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TvTopMenuFocusRequestTest {
    @Test
    fun focusIsRequestedOnlyAfterTheTargetHasHadAFrameToCompose() = runTest {
        val events = mutableListOf<String>()

        requestTopMenuFocusUntilApplied(
            awaitFrame = { events += "frame" },
            requestFocus = { events += "focus"; true },
        )

        assertEquals(listOf("frame", "focus"), events)
    }

    @Test
    fun aTargetThatIsNotAttachedYetIsRetriedOnTheNextFrame() = runTest {
        val events = mutableListOf<String>()
        var attempts = 0

        requestTopMenuFocusUntilApplied(
            awaitFrame = { events += "frame" },
            requestFocus = {
                events += "focus"
                attempts += 1
                attempts == 2
            },
        )

        assertEquals(listOf("frame", "focus", "frame", "focus"), events)
    }

    @Test
    fun retryStopsWhenItsTargetIsNoLongerCurrent() = runTest {
        val events = mutableListOf<String>()
        var targetIsCurrent = true

        requestTopMenuFocusUntilApplied(
            awaitFrame = { events += "frame" },
            isTargetCurrent = { targetIsCurrent },
            requestFocus = {
                events += "focus"
                targetIsCurrent = false
                false
            },
        )

        assertEquals(listOf("frame", "focus"), events)
    }
}
