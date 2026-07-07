package org.siloserver.silo.tv.ui.screens.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvServerSetupCleartextWarningTest {

    @Test
    fun explicitHttpPrefixWarns() {
        assertTrue(serverSetupUsesCleartext("http://192.168.1.50:8096"))
    }

    @Test
    fun httpsPrefixIsClean() {
        assertFalse(serverSetupUsesCleartext("https://media.example.com"))
    }

    @Test
    fun bareHostDoesNotPreWarn() {
        // A bare host probes HTTPS then HTTP; not a definite-cleartext connection.
        assertFalse(serverSetupUsesCleartext("192.168.1.50"))
    }

    @Test
    fun blankDoesNotWarn() {
        assertFalse(serverSetupUsesCleartext(""))
    }
}
