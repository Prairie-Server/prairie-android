package org.prairieserver.prairie.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LanCandidatesCidrTest {
    @Test
    fun singleHostCidrYieldsOnlyThatAddress() {
        assertEquals(listOf("10.1.2.3"), allHostsForCidr(ParsedCidr(listOf(10, 1, 2, 3), 32)))
    }

    @Test
    fun pointToPointCidrKeepsBothAddresses() {
        // A /31 has no network/broadcast pair to skip, so both hosts are probed.
        assertEquals(listOf("10.1.2.4", "10.1.2.5"), allHostsForCidr(ParsedCidr(listOf(10, 1, 2, 4), 31)))
    }

    @Test
    fun slash30SkipsNetworkAndBroadcastAndHonoursMaxHosts() {
        assertEquals(listOf("192.168.5.9", "192.168.5.10"), allHostsForCidr(ParsedCidr(listOf(192, 168, 5, 8), 30)))
        assertEquals(listOf("192.168.5.1", "192.168.5.2"), allHostsForCidr(ParsedCidr(listOf(192, 168, 5, 0), 24), maxHosts = 2))
    }

    @Test
    fun defaultScanCidrsAreTheCommonHomeSubnets() {
        assertEquals(COMMON_CIDRS, collectScanCidrs())
    }

    @Test
    fun defaultCandidatesProbePrairieHostnamesThenPriorityHosts() {
        val candidates = buildCandidates()
        assertEquals("http://prairie.local:8080", candidates.first())
        assertTrue("https://prairie" in candidates)
        assertTrue("http://192.168.1.254:8080" in candidates)
        assertTrue("https://10.0.0.1:8443" in candidates)
        assertEquals(candidates.size, candidates.toSet().size)
        // Non-deep scan never enumerates every host of a /24.
        assertTrue("http://192.168.0.3:8080" !in candidates)
    }
}
