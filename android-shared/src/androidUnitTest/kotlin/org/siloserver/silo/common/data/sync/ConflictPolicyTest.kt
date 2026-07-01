package org.siloserver.silo.common.data.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConflictPolicyTest {
    @Test
    fun lastWriteWinsByTimestamp() {
        assertTrue(ConflictPolicy.localWins(localUpdatedMs = 200L, serverUpdatedMs = 100L))
        assertFalse(ConflictPolicy.localWins(localUpdatedMs = 100L, serverUpdatedMs = 200L))
    }

    @Test
    fun localWinsWhenServerHasNoTimestamp() {
        assertTrue(ConflictPolicy.localWins(localUpdatedMs = 1L, serverUpdatedMs = null))
    }

    @Test
    fun equalTimestampsKeepLocal() {
        // Tie goes to local so an in-flight optimistic write is not clobbered by
        // an equal-stamped server echo.
        assertTrue(ConflictPolicy.localWins(localUpdatedMs = 100L, serverUpdatedMs = 100L))
    }

    @Test
    fun furthestPositionIsMonotonicMax() {
        assertEquals(300.0, ConflictPolicy.furthest(localSeconds = 300.0, serverSeconds = 250.0))
        assertEquals(400.0, ConflictPolicy.furthest(localSeconds = 120.0, serverSeconds = 400.0))
    }
}
