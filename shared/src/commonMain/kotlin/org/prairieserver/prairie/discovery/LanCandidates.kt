package org.prairieserver.prairie.discovery

/**
 * Pure LAN candidate URL building for Prairie server discovery.
 *
 * Port of prairie-smarttv `src/discovery/discover.ts` (Litefin-shaped, Prairie-native).
 */

/** Native Prairie liveness + identity (not Jellyfin `/System/Info/Public`). */
const val HEALTH_PATH = "/api/v1/health"

/** Prairie default listen is :8080. Extra ports cover reverse-proxy / TLS setups. */
val DEFAULT_PORTS: List<Int> = listOf(8080, 8443, 443, 80)

/** Deep LAN sweeps use a single port so a /24 finishes in reasonable time. */
val DEEP_SCAN_PORTS: List<Int> = listOf(8080)

/** Same fallback prefixes Litefin uses when the NIC /24 is unknown or empty. */
val COMMON_CIDRS: List<String> = listOf(
    "192.168.0.0/24",
    "192.168.1.0/24",
    "10.0.0.0/24",
)

private val PRIORITY_LAST_OCTETS: List<Int> = listOf(1, 2, 10, 20, 50, 100, 150, 200, 254)

data class HealthIdentity(
    val serverName: String,
    val serverId: String,
)

data class DiscoveryHit(
    val url: String,
    val serverName: String,
    val serverId: String,
)

data class BuildCandidatesOptions(
    val extraCidrs: List<String> = emptyList(),
    val deepScan: Boolean = false,
    val maxHostsPerCidr: Int = 254,
    /** Device IPv4s when the platform can expose them. */
    val localIps: List<String> = emptyList(),
)

data class ParsedCidr(
    val network: List<Int>,
    val prefix: Int,
)

fun parseHealth(status: String?, serverName: String? = null, serverId: String? = null): HealthIdentity? {
    val normalized = status?.lowercase().orEmpty()
    if (normalized != "ok" && normalized != "healthy" && normalized != "up") return null
    return HealthIdentity(
        serverName = serverName.orEmpty(),
        serverId = serverId.orEmpty(),
    )
}

fun ipv4Parts(ip: String): List<Int>? {
    val parts = ip.split('.')
    if (parts.size != 4) return null
    val nums = ArrayList<Int>(4)
    for (part in parts) {
        if (part.isEmpty() || !part.all { it.isDigit() }) return null
        val value = part.toIntOrNull() ?: return null
        if (value !in 0..255) return null
        nums += value
    }
    return nums
}

fun formatIpv4(parts: List<Int>): String =
    "${parts[0]}.${parts[1]}.${parts[2]}.${parts[3]}"

fun parseCidr(cidr: String): ParsedCidr? {
    val trimmed = cidr.trim()
    val slash = trimmed.indexOf('/')
    if (slash < 0) return null
    val ip = trimmed.substring(0, slash)
    val prefix = trimmed.substring(slash + 1).toIntOrNull() ?: return null
    if (prefix !in 24..32) return null
    val parts = ipv4Parts(ip) ?: return null
    return ParsedCidr(network = parts, prefix = prefix)
}

fun subnetCidrForIp(ip: String): String {
    val parts = ipv4Parts(ip) ?: return ""
    return "${parts[0]}.${parts[1]}.${parts[2]}.0/24"
}

/**
 * Normalize a server base URL: trim, strip trailing slash, lowercase scheme + host.
 * Mirrors [org.prairieserver.prairie.network.AndroidServerRegistry.normalizeUrl].
 */
fun normalizeDiscoveryUrl(raw: String): String {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.isEmpty()) return trimmed
    val schemeSeparator = trimmed.indexOf("://")
    if (schemeSeparator < 0) return trimmed
    val scheme = trimmed.substring(0, schemeSeparator).lowercase()
    val rest = trimmed.substring(schemeSeparator + 3)
    val pathStart = rest.indexOf('/')
    val authority = if (pathStart < 0) rest else rest.substring(0, pathStart)
    val path = if (pathStart < 0) "" else rest.substring(pathStart)
    return "$scheme://${authority.lowercase()}$path"
}

private fun pushUnique(list: MutableList<String>, seen: MutableSet<String>, value: String) {
    val key = value.lowercase()
    if (!seen.add(key)) return
    list += value
}

fun urlsForHost(
    host: String,
    ports: List<Int>,
    seen: MutableSet<String>,
    out: MutableList<String>,
) {
    for (port in ports) {
        when (port) {
            443 -> pushUnique(out, seen, "https://$host")
            80 -> pushUnique(out, seen, "http://$host")
            else -> {
                pushUnique(out, seen, "http://$host:$port")
                if (port == 8443) {
                    pushUnique(out, seen, "https://$host:$port")
                }
            }
        }
    }
}

fun priorityHostsForSubnet(base: List<Int>): List<String> =
    PRIORITY_LAST_OCTETS.map { last ->
        formatIpv4(listOf(base[0], base[1], base[2], last))
    }

fun allHostsForCidr(parsed: ParsedCidr, maxHosts: Int = 254): List<String> {
    val hosts = ArrayList<String>()
    val bits = 32 - parsed.prefix
    if (bits == 0) {
        hosts += formatIpv4(parsed.network)
        return hosts
    }
    val count = 1 shl bits
    var startOffset = 1
    var endOffset = count - 2
    if (endOffset < startOffset) {
        startOffset = 0
        endOffset = count - 1
    }
    var added = 0
    var offset = startOffset
    while (offset <= endOffset) {
        if (added >= maxHosts) break
        if (parsed.prefix < 24) break
        val last = parsed.network[3] + offset
        if (last > 255) break
        hosts += formatIpv4(
            listOf(parsed.network[0], parsed.network[1], parsed.network[2], last),
        )
        added += 1
        offset += 1
    }
    return hosts
}

fun collectScanCidrs(
    extraCidrs: List<String> = emptyList(),
    localIps: List<String> = emptyList(),
): List<String> {
    val cidrs = ArrayList<String>()
    val seen = LinkedHashSet<String>()
    for (ip in localIps) {
        val cidr = subnetCidrForIp(ip)
        if (cidr.isNotEmpty()) pushUnique(cidrs, seen, cidr)
    }
    for (cidr in COMMON_CIDRS) {
        pushUnique(cidrs, seen, cidr)
    }
    for (cidr in extraCidrs) {
        val trimmed = cidr.trim()
        if (trimmed.isNotEmpty()) pushUnique(cidrs, seen, trimmed)
    }
    return cidrs
}

/**
 * Build probe URLs (Litefin-shaped, Prairie-native):
 *   1) prairie.local / prairie
 *   2) local NIC /24 + common 192.168.0/1 + 10.0.0 (+ optional extras)
 *   3) deepScan=false → priority hosts on defaultPorts
 *      deepScan=true  → full /24 on deepScanPorts (:8080)
 */
fun buildCandidates(options: BuildCandidatesOptions = BuildCandidatesOptions()): List<String> {
    val out = ArrayList<String>()
    val seen = LinkedHashSet<String>()

    for (host in listOf("prairie.local", "prairie")) {
        urlsForHost(host, DEFAULT_PORTS, seen, out)
    }
    for (ip in options.localIps) {
        if (ipv4Parts(ip) != null) urlsForHost(ip, DEFAULT_PORTS, seen, out)
    }

    val cidrs = collectScanCidrs(options.extraCidrs, options.localIps)
    val hostPorts = if (options.deepScan) DEEP_SCAN_PORTS else DEFAULT_PORTS

    for (cidr in cidrs) {
        val parsed = parseCidr(cidr) ?: continue
        val hosts = if (options.deepScan) {
            allHostsForCidr(parsed, options.maxHostsPerCidr)
        } else {
            priorityHostsForSubnet(parsed.network)
        }
        for (host in hosts) {
            urlsForHost(host, hostPorts, seen, out)
        }
    }

    return out
}

fun mergeHits(
    hits: List<DiscoveryHit>,
    url: String,
    health: HealthIdentity,
): List<DiscoveryHit> {
    val normalized = normalizeDiscoveryUrl(url)
    val mutable = hits.toMutableList()
    val existingIndex = mutable.indexOfFirst { it.url == normalized }
    if (existingIndex >= 0) {
        val existing = mutable[existingIndex]
        mutable[existingIndex] = existing.copy(
            serverName = health.serverName.ifBlank { existing.serverName },
            serverId = health.serverId.ifBlank { existing.serverId },
        )
        return mutable
    }
    mutable += DiscoveryHit(
        url = normalized,
        serverName = health.serverName,
        serverId = health.serverId,
    )
    return mutable
}
