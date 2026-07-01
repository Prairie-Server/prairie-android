package org.siloserver.silo.android.ui.screens.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoginServerHostLabelTest {
    @Test
    fun extractsHostFromSavedHttpsServerUrl() {
        assertEquals("lib.strm.cafe", loginServerHostLabel("https://lib.strm.cafe"))
    }

    @Test
    fun stripsSchemePortAndPathFromSavedServerUrl() {
        assertEquals("192.168.1.7", loginServerHostLabel("http://192.168.1.7:8090/library"))
    }

    @Test
    fun blankServerUrlHasNoLabel() {
        assertNull(loginServerHostLabel("  "))
    }
}
