package org.siloserver.silo.tv.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class TvCascadeSelectorIdentityTest {
    @Test
    fun requesterOwnershipSurvivesLibraryInsertionAndReorder() {
        val requesters = mutableMapOf<Int, Any>()

        val before = stableIdentityValues(
            ids = listOf(11, 22),
            valuesById = requesters,
            create = ::Any,
        )
        val after = stableIdentityValues(
            ids = listOf(33, 22, 11),
            valuesById = requesters,
            create = ::Any,
        )

        assertEquals(listOf(33, 22, 11), after.keys.toList())
        assertSame(before.getValue(11), after.getValue(11))
        assertSame(before.getValue(22), after.getValue(22))
        assertNotSame(after.getValue(33), after.getValue(11))
        assertNotSame(after.getValue(33), after.getValue(22))
    }
}
