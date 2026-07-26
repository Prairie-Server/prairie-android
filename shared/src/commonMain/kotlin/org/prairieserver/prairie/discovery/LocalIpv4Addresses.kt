package org.prairieserver.prairie.discovery

/**
 * Best-effort local IPv4 discovery for LAN candidate expansion.
 * Android reads NIC addresses; other platforms may return an empty list and
 * fall back to [COMMON_CIDRS] only.
 */
expect fun localIpv4Addresses(): List<String>
