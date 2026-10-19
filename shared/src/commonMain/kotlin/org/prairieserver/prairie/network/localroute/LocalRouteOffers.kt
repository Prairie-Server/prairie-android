package org.prairieserver.prairie.network.localroute

import org.prairieserver.prairie.network.PrairieJson
import org.prairieserver.prairie.network.api.ServerConnectionsV2

/** The local-route part of a `GET /api/v2/system/connections` body. */
fun decodeLocalRouteOffer(body: String): LocalRouteOffer {
    val document = runCatching { PrairieJson.decodeFromString(ServerConnectionsV2.serializer(), body) }.getOrNull()
        ?: return LocalRouteOffer.Unreadable
    val serverId = document.serverId.trim()
    if (!document.isAvailable || serverId.isEmpty()) return LocalRouteOffer.Unreadable
    val pin = document.localRoute?.spkiSha256?.trim().orEmpty()
    if (pin.isEmpty()) return LocalRouteOffer.NotOffered(serverId)
    return LocalRouteOffer.Offered(
        serverId = serverId,
        pin = pin,
        url = document.localRoute?.url?.trim()?.takeIf { it.isNotEmpty() },
    )
}
