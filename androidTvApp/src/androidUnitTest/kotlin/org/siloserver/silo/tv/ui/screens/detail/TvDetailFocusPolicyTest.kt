package org.siloserver.silo.tv.ui.screens.detail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvDetailFocusPolicyTest {
    @Test
    fun castRailRestoresLastCard() {
        assertEquals(5, restoredRailIndex(5, 8))
        assertEquals(2, restoredRailIndex(5, 3))
        assertNull(restoredRailIndex(0, 0))
    }
}
