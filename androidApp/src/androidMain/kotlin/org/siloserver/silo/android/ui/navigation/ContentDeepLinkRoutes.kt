package org.siloserver.silo.android.ui.navigation

import java.net.URI

/**
 * Maps content deep links onto in-app routes — the Android analogue of iOS's
 * `handleDeepLink` (`continuum://` there; this app's scheme is `silo://`).
 * Routing is by URL host with the content id as the first path segment:
 *
 * - `silo://downloads`          → the Downloads tab
 * - `silo://item/<contentId>`   → item detail
 * - `silo://play/<contentId>`   → the player (immediate playback)
 *
 * Auth gating is inherited from the pendingExternalRoute mechanism: a link
 * opened before sign-in stays queued until the authenticated graph shows.
 */
internal fun contentDeepLinkRouteOrNull(rawUri: String?): String? {
    val uri = rawUri
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { URI(it) }.getOrNull() }
        ?: return null
    if (!uri.scheme.equals("silo", ignoreCase = true)) return null

    val contentId = uri.path.orEmpty()
        .trim('/')
        .substringBefore('/')
        .trim()

    return when (uri.host?.lowercase()) {
        "downloads" -> Route.Downloads.route
        "item" -> contentId.takeIf { it.isNotBlank() }?.let { "item/$it" }
        "play" -> contentId.takeIf { it.isNotBlank() }?.let { "player/$it" }
        else -> null
    }
}
