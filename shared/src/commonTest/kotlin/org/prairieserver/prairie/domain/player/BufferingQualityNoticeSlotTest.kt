package org.prairieserver.prairie.domain.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class BufferingQualityNoticeSlotTest {
    private fun TestScope.slot(free: MutableStateFlow<Boolean>) =
        BufferingQualityNoticeSlot(backgroundScope, free, clock = { testScheduler.currentTime })

    @Test
    fun showsForFiveSecondsThenClears() = runTest {
        val slot = slot(MutableStateFlow(true))
        slot.show(BufferingQualityNotice.LOWERED)
        runCurrent()
        assertEquals(BufferingQualityNotice.LOWERED, slot.notice.value)
        advanceTimeBy(BufferingQualityNoticeSlot.NOTICE_MS - 1)
        assertEquals(BufferingQualityNotice.LOWERED, slot.notice.value)
        advanceTimeBy(2)
        assertNull(slot.notice.value)
    }

    @Test
    fun waitsWhileCoveredAndShowsOnlyTheTimeLeft() = runTest {
        val free = MutableStateFlow(false)
        val slot = slot(free)
        slot.show(BufferingQualityNotice.CANT_LOWER)
        advanceTimeBy(10_000)
        assertNull(slot.notice.value)

        free.value = true
        runCurrent()
        assertEquals(BufferingQualityNotice.CANT_LOWER, slot.notice.value)
        advanceTimeBy(2_000)
        free.value = false
        runCurrent()
        assertNull(slot.notice.value)

        advanceTimeBy(30_000)
        free.value = true
        runCurrent()
        assertEquals(BufferingQualityNotice.CANT_LOWER, slot.notice.value)
        advanceTimeBy(2_999)
        assertEquals(BufferingQualityNotice.CANT_LOWER, slot.notice.value)
        advanceTimeBy(2)
        assertNull(slot.notice.value)
    }

    @Test
    fun aNewerNoticeReplacesAndClearCancels() = runTest {
        val slot = slot(MutableStateFlow(true))
        slot.show(BufferingQualityNotice.LOWERED)
        runCurrent()
        slot.show(BufferingQualityNotice.CANT_LOWER)
        runCurrent()
        assertEquals(BufferingQualityNotice.CANT_LOWER, slot.notice.value)
        slot.clear()
        assertNull(slot.notice.value)
        advanceTimeBy(BufferingQualityNoticeSlot.NOTICE_MS * 2)
        assertNull(slot.notice.value)
    }
}
