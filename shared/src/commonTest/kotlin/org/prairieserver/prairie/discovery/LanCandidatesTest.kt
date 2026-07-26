package org.prairieserver.prairie.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LanCandidatesTest {

    @Test
    fun parseHealthAcceptsOkHealthyUpAndRejectsDown() {
        assertEquals(
            HealthIdentity(serverName = "Prairie Home", serverId = "abc"),
            parseHealth("ok", "Prairie Home", "abc"),
        )
        assertEquals(
            HealthIdentity(serverName = "", serverId = ""),
            parseHealth("healthy"),
        )
        assertEquals(
            HealthIdentity(serverName = "", serverId = ""),
            parseHealth("UP"),
        )
        assertNull(parseHealth("down"))
        assertNull(parseHealth(null))
        assertEquals(HEALTH_PATH, "/api/v1/health")
        assertEquals(8080, DEEP_SCAN_PORTS.first())
    }

    @Test
    fun cidrHelpersMatchLitefinStyleFallbacks() {
        assertEquals(24, parseCidr("192.168.1.0/24")?.prefix)
        assertNull(parseCidr("10.0.0.0/16"))
        assertEquals("192.168.1.0/24", subnetCidrForIp("192.168.1.50"))
        val hosts = priorityHostsForSubnet(listOf(192, 168, 1, 0))
        assertEquals("192.168.1.1", hosts.first())
        assertEquals("192.168.0.0/24", COMMON_CIDRS.first())
    }

    @Test
    fun buildCandidatesIncludesCommonCidrsPriorityHostsAndPrairiePorts() {
        val candidates = buildCandidates(
            BuildCandidatesOptions(
                extraCidrs = listOf("192.168.2.0/24"),
                deepScan = false,
                maxHostsPerCidr = 16,
            ),
        )
        val joined = candidates.joinToString(",")
        assertTrue(joined.contains("prairie.local"))
        assertTrue(joined.contains("192.168.2.1"))
        assertTrue(joined.contains("192.168.0.1"))
        assertTrue(joined.contains("192.168.1.1"))
        assertTrue(joined.contains("10.0.0.1"))
        assertTrue(joined.contains(":8080"))
        assertFalse(joined.contains("/System/Info"))
    }

    @Test
    fun deepScanExpandsSlash24OnPrairieListenPortOnly() {
        val deep = buildCandidates(
            BuildCandidatesOptions(
                extraCidrs = listOf("192.168.9.0/24"),
                deepScan = true,
                maxHostsPerCidr = 12,
            ),
        )
        val joined = deep.joinToString(",")
        assertTrue(joined.contains("192.168.9.1:8080"))
        assertTrue(joined.contains("192.168.9.12:8080"))
        assertFalse(joined.contains("https://192.168.9.1:8080"))
        assertEquals(
            3,
            allHostsForCidr(ParsedCidr(network = listOf(192, 168, 9, 0), prefix = 24), maxHosts = 3).size,
        )
    }

    @Test
    fun mergeHitsDedupesByNormalizedUrl() {
        var hits = mergeHits(
            emptyList(),
            "https://prairie.example.com/",
            HealthIdentity(serverName = "One", serverId = "1"),
        )
        hits = mergeHits(
            hits,
            "https://prairie.example.com",
            HealthIdentity(serverName = "Two", serverId = "1"),
        )
        assertEquals(1, hits.size)
        assertEquals("Two", hits.first().serverName)
        assertEquals("https://prairie.example.com", hits.first().url)
    }

    @Test
    fun buildCandidatesProbesLocalDeviceIpWhenProvided() {
        val candidates = buildCandidates(
            BuildCandidatesOptions(
                localIps = listOf("10.0.0.42"),
                deepScan = false,
                maxHostsPerCidr = 4,
                extraCidrs = emptyList(),
            ),
        )
        assertTrue(candidates.any { it.contains("10.0.0.42") })
        val cidrs = collectScanCidrs(localIps = listOf("10.0.0.42"))
        assertTrue(cidrs.contains("10.0.0.0/24"))
    }

    @Test
    fun urlsForHostMapsSpecialPorts() {
        val out = ArrayList<String>()
        val seen = LinkedHashSet<String>()
        urlsForHost("example.local", listOf(443, 80, 8080, 8443), seen, out)
        assertTrue(out.contains("https://example.local"))
        assertTrue(out.contains("http://example.local"))
        assertTrue(out.contains("http://example.local:8080"))
        assertTrue(out.contains("http://example.local:8443"))
        assertTrue(out.contains("https://example.local:8443"))
    }

    @Test
    fun ipv4PartsRejectsMalformed() {
        assertNotNull(ipv4Parts("192.168.1.1"))
        assertNull(ipv4Parts("192.168.1"))
        assertNull(ipv4Parts("192.168.1.256"))
        assertNull(ipv4Parts("192.168.1.a"))
        assertEquals("1.2.3.4", formatIpv4(listOf(1, 2, 3, 4)))
    }
}
