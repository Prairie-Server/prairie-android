package org.prairieserver.prairie.model.admin

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminClientPolicyTest {

    @Test
    fun `acting admins see the client admin surface`() {
        // Product decision 2026-07-07: the stats dashboard (Apple parity
        // surface) is exposed to acting admins.
        assertTrue(shouldShowClientAdminSurface(isActingAdmin = true))
    }

    @Test
    fun `client admin surfaces stay hidden for non admins`() {
        assertFalse(shouldShowClientAdminSurface(isActingAdmin = false))
    }
}
