package org.prairieserver.prairie.discovery

import java.net.NetworkInterface

actual fun localIpv4Addresses(): List<String> {
    val ips = ArrayList<String>()
    val seen = LinkedHashSet<String>()
    return try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
        for (iface in interfaces) {
            if (!iface.isUp || iface.isLoopback) continue
            for (address in iface.inetAddresses) {
                val host = address.hostAddress ?: continue
                // Skip IPv6 and IPv4-mapped forms.
                if (host.contains(':')) continue
                if (ipv4Parts(host) == null) continue
                if (!seen.add(host)) continue
                ips += host
            }
        }
        ips
    } catch (_: Exception) {
        emptyList()
    }
}
