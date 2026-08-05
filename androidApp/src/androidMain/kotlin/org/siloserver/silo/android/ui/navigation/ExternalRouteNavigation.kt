package org.siloserver.silo.android.ui.navigation

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.siloserver.silo.android.ui.screens.player.MobilePlayerRouteIntent
import org.siloserver.silo.android.ui.screens.player.MobilePlayerRouteTarget
import org.siloserver.silo.common.player.video.VideoPlayerRouteArgs

/**
 * The identity an external request is only meaningful against.
 *
 * External requests wait: a link can arrive before sign-in and sit through
 * setup, login and profile selection, and the identity it was created for may
 * not be the one active when it finally fires. Every route that means something
 * different under a different server or profile has to say so and be re-checked
 * at delivery, or it acts on whoever happens to be signed in by then.
 */
sealed interface ExternalRouteScope {
    /** Meaningful under any identity — e.g. "open the Downloads tab". */
    data object Unscoped : ExternalRouteScope

    /**
     * Valid only under this server and profile. Used by notifications (which
     * are generated for one profile's inbox) and by content links (whose ids
     * are server-local). Null components mean "was not signed in when this was
     * created", which constrains nothing.
     */
    data class Identity(val serverId: String?, val profileId: String?) : ExternalRouteScope {
        /**
         * Each component constrains only if it was known. A link that arrived
         * with a server but no profile yet — configured server, nobody signed
         * in — must still deliver once a profile IS chosen; requiring the
         * profile to still be null would drop exactly the link the user was
         * signing in to open.
         */
        fun matches(serverId: String?, profileId: String?): Boolean =
            (this.serverId == null || this.serverId == serverId) &&
                (this.profileId == null || this.profileId == profileId)
    }
}

/** A single external-navigation delivery, distinct even when its route repeats. */
class ExternalRouteRequest internal constructor(
    val generation: Long,
    val route: String,
    val scope: ExternalRouteScope = ExternalRouteScope.Unscoped,
)

internal class ExternalRouteRequestFactory {
    private var latestGeneration = 0L

    fun create(
        route: String,
        scope: ExternalRouteScope = ExternalRouteScope.Unscoped,
    ): ExternalRouteRequest =
        ExternalRouteRequest(
            generation = ++latestGeneration,
            route = route,
            scope = scope,
        )
}

internal fun clearConsumedExternalRouteRequest(
    pendingRequest: ExternalRouteRequest?,
    consumedRequest: ExternalRouteRequest,
): ExternalRouteRequest? =
    if (pendingRequest?.generation == consumedRequest.generation) null else pendingRequest

internal fun shouldReplaceCurrentPlayer(
    currentDestinationRoute: String?,
    targetRoute: String,
): Boolean =
    currentDestinationRoute == Route.Player.ROUTE && targetRoute.startsWith("player/")

/** Parses the canonical in-app player route carried by an external request. */
internal fun playerRouteIntentOrNull(route: String): MobilePlayerRouteIntent? {
    if (!route.startsWith("player/")) return null
    // Route.Player percent-encodes the content id, so decode it back here —
    // this value is compared against the live player's target, and an encoded
    // id would never match a decoded one, making an already-showing player look
    // like a different request and restart it.
    val contentId = route
        .substringAfter("player/")
        .substringBefore('?')
        .takeIf { it.isNotBlank() }
        ?.let { runCatching { URLDecoder.decode(it.replace("+", "%2B"), StandardCharsets.UTF_8.name()) }.getOrNull() }
        ?.takeIf { it.isNotBlank() }
        ?: return null
    val query = route
        .substringAfter('?', "")
        .split('&')
        .filter(String::isNotBlank)
        .associate { part -> part.substringBefore('=') to part.substringAfter('=', "") }
    // This provider describes solo playback only. A room-scoped target must be
    // handled by Watch Together even if every media choice happens to match.
    if ("roomId" in query) return null

    val fileId = query["fileId"]?.toIntOrNull()
    if ("fileId" in query && (fileId == null || fileId <= 0)) return null
    val quality = query[VideoPlayerRouteArgs.QUALITY]
        ?.let(VideoPlayerRouteArgs::normalizeQuality)
    if (VideoPlayerRouteArgs.QUALITY in query && quality == null) return null
    val audioTrackIndex = query["audioTrackIndex"]?.toIntOrNull()
    if ("audioTrackIndex" in query && (audioTrackIndex == null || audioTrackIndex < 0)) return null
    val subtitleTrackIndex = query["subtitleTrackIndex"]?.toIntOrNull()
    if ("subtitleTrackIndex" in query && (subtitleTrackIndex == null || subtitleTrackIndex < -1)) return null
    val resumePositionSeconds = query[VideoPlayerRouteArgs.RESUME_POSITION]
        ?.let(VideoPlayerRouteArgs::parseResumePosition)
    if (VideoPlayerRouteArgs.RESUME_POSITION in query && resumePositionSeconds == null) return null

    return MobilePlayerRouteIntent(
        contentId = contentId,
        fileId = fileId,
        quality = quality,
        audioTrackIndex = audioTrackIndex,
        subtitleTrackIndex = subtitleTrackIndex,
        resumePositionSeconds = resumePositionSeconds,
        fileIsExplicit = "fileId" in query,
        qualityIsExplicit = VideoPlayerRouteArgs.QUALITY in query,
        audioTrackIsExplicit = "audioTrackIndex" in query,
        subtitleTrackIsExplicit = "subtitleTrackIndex" in query,
    )
}

/**
 * Tests an incoming route against both the current route intent and live
 * playback values. An omitted parameter remains exact only while the player is
 * still using automatic selection; an explicit parameter compares to the live
 * resolved value.
 */
internal fun MobilePlayerRouteIntent.matches(target: MobilePlayerRouteTarget): Boolean =
    contentId == target.contentId &&
        resumePositionSeconds == target.resumePositionSeconds &&
        optionalTargetMatches(
            requestedIsExplicit = fileIsExplicit,
            requestedValue = fileId,
            currentIsExplicit = target.intent.fileIsExplicit,
            liveValue = target.fileId,
        ) &&
        optionalTargetMatches(
            requestedIsExplicit = qualityIsExplicit,
            requestedValue = quality,
            currentIsExplicit = target.intent.qualityIsExplicit,
            liveValue = target.quality,
        ) &&
        optionalTargetMatches(
            requestedIsExplicit = audioTrackIsExplicit,
            requestedValue = audioTrackIndex,
            currentIsExplicit = target.intent.audioTrackIsExplicit,
            liveValue = target.audioTrackIndex,
        ) &&
        optionalTargetMatches(
            requestedIsExplicit = subtitleTrackIsExplicit,
            requestedValue = subtitleTrackIndex,
            currentIsExplicit = target.intent.subtitleTrackIsExplicit,
            liveValue = target.subtitleTrackIndex,
        )

private fun <T> optionalTargetMatches(
    requestedIsExplicit: Boolean,
    requestedValue: T?,
    currentIsExplicit: Boolean,
    liveValue: T?,
): Boolean = if (requestedIsExplicit) {
    requestedValue != null && requestedValue == liveValue
} else {
    !currentIsExplicit
}

private val preAuthenticationDestinationRoutes = setOf(
    Route.Login.route,
    Route.ServerSetup.route,
    Route.ServerList.route,
    Route.Setup.route,
    Route.Signup.route,
    Route.ProfileSelection.route,
    Route.CreateProfile.route,
    Route.EditProfile.ROUTE,
    Route.PairDevice.ROUTE,
    Route.InviteClaim.ROUTE,
    Route.OnboardingTour.route,
)

/**
 * Waits until [pendingExternalRoute] is allowed from the current graph, then
 * delivers it exactly once. [first] ends the back-stack subscription before
 * [navigate] can emit the destination it just added; navigating from inside a
 * long-lived collector otherwise feeds that new entry back into the same route.
 */
internal suspend fun consumeExternalRouteOnce(
    pendingExternalRoute: ExternalRouteRequest?,
    currentDestinationRoutes: Flow<String?>,
    isAlreadyAtRoute: (String) -> Boolean = { false },
    /**
     * Whether the request's [ExternalRouteScope] still matches the live
     * identity. Evaluated AFTER the wait, not before it.
     */
    isStillValidForScope: suspend (ExternalRouteScope) -> Boolean = { true },
    navigate: (String) -> Unit,
    onConsumed: (ExternalRouteRequest) -> Unit,
) {
    val request = pendingExternalRoute ?: return
    val route = request.route.takeIf { it.isNotBlank() } ?: return
    val isPreAuthenticationTarget = route.startsWith("invite_claim")

    currentDestinationRoutes.first { currentRoute ->
        isPreAuthenticationTarget || currentRoute !in preAuthenticationDestinationRoutes
    }
    val scopeStillValid = isStillValidForScope(request.scope)
    if (scopeStillValid && !isAlreadyAtRoute(route)) {
        navigate(route)
    }
    // Consumed either way: a request whose identity no longer matches must not
    // sit in the queue waiting to fire at some later, equally wrong moment.
    onConsumed(request)
}
