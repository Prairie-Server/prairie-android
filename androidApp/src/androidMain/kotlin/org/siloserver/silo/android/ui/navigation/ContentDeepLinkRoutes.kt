package org.siloserver.silo.android.ui.navigation

import org.siloserver.silo.common.player.video.VideoPlayerRouteArgs
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

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

    // Read the RAW path and decode exactly one segment. `URI.path` is already
    // percent-decoded, so taking it and interpolating the result back into a
    // route re-parsed the decoded bytes as route syntax: an id containing an
    // encoded `?` or `/` could truncate the id or inject an argument. The
    // route constructors below re-encode, so this must hand them the decoded
    // id exactly once.
    val contentId = uri.rawPath.orEmpty()
        .trim('/')
        .substringBefore('/')
        .let(::decodePathSegment)
        .orEmpty()
        .trim()

    return when (uri.host?.lowercase()) {
        "downloads" -> Route.Downloads.route
        "item" -> contentId.takeIf { it.isNotBlank() }?.let { Route.ItemDetail(it).route }
        "play" -> contentId.takeIf { it.isNotBlank() }?.let {
            Route.Player(
                contentId = it,
                fileId = uri.queryParameter("fileId")?.toIntOrNull()?.takeIf { id -> id > 0 },
                quality = VideoPlayerRouteArgs.normalizeQuality(uri.queryParameter(VideoPlayerRouteArgs.QUALITY)),
                audioTrackIndex = uri.queryParameter("audioTrackIndex")?.toIntOrNull()?.takeIf { index -> index >= 0 },
                subtitleTrackIndex = uri.queryParameter("subtitleTrackIndex")?.toIntOrNull()?.takeIf { index -> index >= -1 },
            ).route
        }
        else -> null
    }
}

private fun URI.queryParameter(name: String): String? = rawQuery
    ?.split('&')
    ?.asSequence()
    ?.map { part -> part.substringBefore('=') to part.substringAfter('=', "") }
    ?.firstOrNull { (key, _) -> decodeQueryComponent(key) == name }
    ?.second
    ?.let(::decodeQueryComponent)

private fun decodeQueryComponent(value: String): String? = runCatching {
    URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}.getOrNull()

/**
 * Percent-decoding for a PATH segment. Deliberately not [decodeQueryComponent]:
 * `URLDecoder` implements form encoding, where `+` means space — but in a path
 * a `+` is a literal plus, so an id containing one would be corrupted.
 */
private fun decodePathSegment(value: String): String? = runCatching {
    // Escape `+` first: URLDecoder implements form encoding where `+` means
    // space, but in a path a `+` is a literal plus. android.net.Uri.decode
    // would do this correctly, but it is stubbed in plain JVM unit tests.
    URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
}.getOrNull()
