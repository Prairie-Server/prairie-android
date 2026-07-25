package org.prairieserver.prairie.common.pairing

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompanionPairingNsdBrowserTest {
    @Test
    fun pairingServiceTypeAcceptsAndroidTrailingDotForm() {
        assertTrue(isPairingServiceType("_prairiepair._tcp"))
        assertTrue(isPairingServiceType("_prairiepair._tcp."))
        assertFalse(isPairingServiceType("_prairiecast._tcp."))
    }
}
