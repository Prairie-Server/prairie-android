package org.siloserver.silo.android.ui.navigation

/**
 * A notification's navigation request, accepted only when it says whose it is.
 *
 * The identity is validated HERE as well as stamped at post time, because the
 * delivery side must not trust an Intent it merely received. Two ways an
 * unattributed one can arrive: a notification posted by a build before the
 * extras existed, and an explicit Intent crafted against the exported Activity.
 * Both used to produce `Identity(null, null)`, which matches every identity —
 * so the route ran against whoever happened to be signed in.
 */
fun notificationExternalRouteOrNull(
    route: String?,
    serverId: String?,
    profileId: String?,
): Pair<String, ExternalRouteScope>? {
    val usableRoute = route?.takeIf { it.isNotBlank() } ?: return null
    val usableServerId = serverId?.takeIf { it.isNotBlank() } ?: return null
    val usableProfileId = profileId?.takeIf { it.isNotBlank() } ?: return null
    return usableRoute to ExternalRouteScope.Identity(
        serverId = usableServerId,
        profileId = usableProfileId,
    )
}
