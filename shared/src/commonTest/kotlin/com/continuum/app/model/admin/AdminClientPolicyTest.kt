package com.continuum.app.model.admin

import kotlin.test.Test
import kotlin.test.assertFalse

class AdminClientPolicyTest {

    @Test
    fun `client admin surfaces are disabled even for acting admins`() {
        assertFalse(shouldShowClientAdminSurface(isActingAdmin = true))
    }

    @Test
    fun `client admin surfaces stay hidden for non admins`() {
        assertFalse(shouldShowClientAdminSurface(isActingAdmin = false))
    }
}
