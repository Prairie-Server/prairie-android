package org.siloserver.silo.android.ui.navigation

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.siloserver.silo.android.ui.screens.player.MobilePlayerRouteIntent
import org.siloserver.silo.android.ui.screens.player.MobilePlayerRouteTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExternalRouteNavigationTest {
    @Test
    fun contentRouteWaitsForAuthenticationAndIsDeliveredOnce() = runTest {
        val events = mutableListOf<String>()
        val destinations = flow {
            emit(Route.Login.route)
            emit(Route.ProfileSelection.route)
            emit(Route.Home.route)
            error("external-route collector remained active after the eligible destination")
        }

        consumeExternalRouteOnce(
            pendingExternalRoute = ExternalRouteRequest(
                generation = 1,
                route = "player/movie-tmdb-463015",
            ),
            currentDestinationRoutes = destinations,
            navigate = { route -> events += "navigate:$route" },
            onConsumed = { request -> events += "consumed:${request.generation}" },
        )

        assertEquals(
            listOf("navigate:player/movie-tmdb-463015", "consumed:1"),
            events,
        )
    }

    @Test
    fun inviteClaimCanNavigateFromTheSignedOutGraph() = runTest {
        val navigated = mutableListOf<String>()

        consumeExternalRouteOnce(
            pendingExternalRoute = ExternalRouteRequest(
                generation = 1,
                route = "invite_claim?server=example&token=test-token",
            ),
            currentDestinationRoutes = flowOf(Route.Login.route),
            navigate = navigated::add,
            onConsumed = {},
        )

        assertEquals(listOf("invite_claim?server=example&token=test-token"), navigated)
    }

    @Test
    fun blankRouteDoesNotSubscribeOrNavigate() = runTest {
        var navigations = 0

        consumeExternalRouteOnce(
            pendingExternalRoute = ExternalRouteRequest(generation = 1, route = " "),
            currentDestinationRoutes = flow { error("blank route must not collect destinations") },
            navigate = { navigations++ },
            onConsumed = { error("blank route must not be consumed") },
        )

        assertEquals(0, navigations)
    }

    @Test
    fun repeatedIdenticalRoutesReceiveDistinctMonotonicIdentities() {
        val requests = ExternalRouteRequestFactory()

        val first = requests.create("player/movie-tmdb-463015")
        val second = requests.create("player/movie-tmdb-463015")

        assertEquals(first.route, second.route)
        assertNotEquals(first.generation, second.generation)
        assertEquals(first.generation + 1, second.generation)
    }

    @Test
    fun playerTargetProviderIsInvokedOnlyForItsOwningBackStackEntry() {
        var providerCalls = 0
        val registration = PlayerTargetProviderRegistration(
            backStackEntryId = "player-a",
            target = {
                providerCalls += 1
                null
            },
        )

        assertNull(
            currentPlayerTargetOrNull(
                currentBackStackEntryId = "player-b",
                registration = registration,
            ),
        )
        assertEquals(0, providerCalls)

        assertNull(
            currentPlayerTargetOrNull(
                currentBackStackEntryId = "player-a",
                registration = registration,
            ),
        )
        assertEquals(1, providerCalls)
    }

    @Test
    fun staleConsumptionDoesNotClearANewerRequest() {
        val requests = ExternalRouteRequestFactory()
        val first = requests.create("player/movie-tmdb-463015")
        val second = requests.create("player/movie-tmdb-463015")

        assertEquals(
            second,
            clearConsumedExternalRouteRequest(
                pendingRequest = second,
                consumedRequest = first,
            ),
        )
        assertNull(
            clearConsumedExternalRouteRequest(
                pendingRequest = second,
                consumedRequest = second,
            ),
        )
    }

    @Test
    fun exactCurrentTargetIsConsumedWithoutRenavigating() = runTest {
        val request = ExternalRouteRequest(
            generation = 4,
            route = "player/movie-tmdb-463015",
        )
        val events = mutableListOf<String>()

        consumeExternalRouteOnce(
            pendingExternalRoute = request,
            currentDestinationRoutes = flowOf(Route.Player.ROUTE),
            isAlreadyAtRoute = { route -> route == request.route },
            navigate = { route -> events += "navigate:$route" },
            onConsumed = { consumed -> events += "consumed:${consumed.generation}" },
        )

        assertEquals(listOf("consumed:4"), events)
    }

    @Test
    fun differentPlayerTargetReplacesTheCurrentPlayerEntry() {
        assertEquals(
            true,
            shouldReplaceCurrentPlayer(
                currentDestinationRoute = Route.Player.ROUTE,
                targetRoute = "player/movie-tmdb-463015?quality=original",
            ),
        )
        assertEquals(
            false,
            shouldReplaceCurrentPlayer(
                currentDestinationRoute = Route.Home.route,
                targetRoute = "player/movie-tmdb-463015?quality=original",
            ),
        )
    }

    @Test
    fun canonicalPlayerTargetParsesEveryPlaybackChoice() {
        assertEquals(
            MobilePlayerRouteIntent(
                contentId = "movie-1",
                fileId = 121,
                quality = "original",
                audioTrackIndex = 2,
                subtitleTrackIndex = -1,
                resumePositionSeconds = 42.0,
            ),
            playerRouteIntentOrNull(
                "player/movie-1?fileId=121&quality=original&audioTrackIndex=2&subtitleTrackIndex=-1&resumePosition=42.0",
            ),
        )
        assertNull(playerRouteIntentOrNull("item/movie-1"))
        assertNull(playerRouteIntentOrNull("player/movie-1?fileId=invalid"))
        assertNull(playerRouteIntentOrNull("player/movie-1?resumePosition=invalid"))
        assertNull(playerRouteIntentOrNull("player/movie-1?roomId=room-1"))
    }

    @Test
    fun bareRouteRemainsExactAfterItsInitialAutomaticResolution() {
        val intent = MobilePlayerRouteIntent(contentId = "movie-1")
        val current = target(
            intent = intent,
            contentId = "movie-1",
            fileId = 121,
            quality = "auto",
            audioTrackIndex = 1,
            subtitleTrackIndex = -1,
        )

        assertTrue(requireNotNull(playerRouteIntentOrNull("player/movie-1")).matches(current))
    }

    @Test
    fun bareRouteStopsMatchingAfterAnExplicitVersionSwitch() {
        val intent = MobilePlayerRouteIntent(contentId = "movie-1", fileId = 222)
        val current = target(intent = intent, contentId = "movie-1", fileId = 222)

        assertFalse(requireNotNull(playerRouteIntentOrNull("player/movie-1")).matches(current))
        assertTrue(requireNotNull(playerRouteIntentOrNull("player/movie-1?fileId=222")).matches(current))
        assertFalse(requireNotNull(playerRouteIntentOrNull("player/movie-1?fileId=121")).matches(current))
    }

    @Test
    fun liveContentQualityAndTracksMustMatchExplicitRequest() {
        val intent = MobilePlayerRouteIntent(
            contentId = "episode-2",
            fileId = 222,
            quality = "720p",
            audioTrackIndex = 1,
            subtitleTrackIndex = -1,
        )
        val current = target(
            intent = intent,
            contentId = "episode-2",
            fileId = 222,
            quality = "720p",
            audioTrackIndex = 1,
            subtitleTrackIndex = -1,
        )

        assertTrue(
            requireNotNull(
                playerRouteIntentOrNull(
                    "player/episode-2?fileId=222&quality=720p&audioTrackIndex=1&subtitleTrackIndex=-1",
                ),
            ).matches(current),
        )
        assertFalse(requireNotNull(playerRouteIntentOrNull("player/episode-1?fileId=222")).matches(current))
        assertFalse(requireNotNull(playerRouteIntentOrNull("player/episode-2?quality=1080p")).matches(current))
        assertFalse(requireNotNull(playerRouteIntentOrNull("player/episode-2?audioTrackIndex=0")).matches(current))
        assertFalse(requireNotNull(playerRouteIntentOrNull("player/episode-2?subtitleTrackIndex=0")).matches(current))
    }

    @Test
    fun resumePositionIsExactIntentRatherThanTheAdvancingPlaybackClock() {
        val resumed = target(
            intent = MobilePlayerRouteIntent(
                contentId = "movie-1",
                resumePositionSeconds = 42.0,
            ),
            contentId = "movie-1",
            resumePositionSeconds = 42.0,
        )
        val fromStart = target(
            intent = MobilePlayerRouteIntent(
                contentId = "movie-1",
                resumePositionSeconds = 0.0,
            ),
            contentId = "movie-1",
            resumePositionSeconds = 0.0,
        )

        assertTrue(
            requireNotNull(playerRouteIntentOrNull("player/movie-1?resumePosition=42.0"))
                .matches(resumed),
        )
        assertFalse(
            requireNotNull(playerRouteIntentOrNull("player/movie-1?resumePosition=43.0"))
                .matches(resumed),
        )
        assertFalse(requireNotNull(playerRouteIntentOrNull("player/movie-1")).matches(resumed))
        assertFalse(requireNotNull(playerRouteIntentOrNull("player/movie-1")).matches(fromStart))
    }

    private fun target(
        intent: MobilePlayerRouteIntent,
        contentId: String,
        fileId: Int? = null,
        quality: String? = null,
        audioTrackIndex: Int? = null,
        subtitleTrackIndex: Int? = null,
        resumePositionSeconds: Double? = null,
    ) = MobilePlayerRouteTarget(
        intent = intent,
        contentId = contentId,
        fileId = fileId,
        quality = quality,
        audioTrackIndex = audioTrackIndex,
        subtitleTrackIndex = subtitleTrackIndex,
        resumePositionSeconds = resumePositionSeconds,
    )

    /**
     * A notification PendingIntent can be tapped days after it was posted, and
     * several profile switches later. Its route means something different — or
     * nothing — under another identity, so the scope is re-checked at delivery
     * and a mismatch must not navigate.
     */
    @Test
    fun aRequestWhoseServerNoLongerMatchesIsNotDelivered() = runTest {
        val request = ExternalRouteRequestFactory()
            .create(
                route = "inbox",
                scope = ExternalRouteScope.Identity(serverId = "server-b", profileId = "kids"),
            )
        var navigated: String? = null
        var consumed = 0

        consumeExternalRouteOnce(
            pendingExternalRoute = request,
            currentDestinationRoutes = flowOf("home"),
            isStillValidForScope = { false },
            navigate = { navigated = it },
            onConsumed = { consumed++ },
        )

        assertNull(navigated, "a notification must never act on a different profile's session")
        // Still consumed: leaving it queued would only let it fire later, at an
        // equally wrong moment.
        assertEquals(1, consumed)
    }

    @Test
    fun aRequestWhoseServerStillMatchesIsDelivered() = runTest {
        val request = ExternalRouteRequestFactory()
            .create(
                route = "inbox",
                scope = ExternalRouteScope.Identity(serverId = "server-b", profileId = "kids"),
            )
        var navigated: String? = null
        var consumed = 0

        consumeExternalRouteOnce(
            pendingExternalRoute = request,
            currentDestinationRoutes = flowOf("home"),
            isStillValidForScope = { true },
            navigate = { navigated = it },
            onConsumed = { consumed++ },
        )

        assertEquals("inbox", navigated)
        assertEquals(1, consumed)
    }

    /** An unscoped request must not be gated on any server. */
    @Test
    fun anUnscopedRequestIgnoresTheServerCheck() = runTest {
        val request = ExternalRouteRequestFactory().create(route = "item/abc")
        var navigated: String? = null

        consumeExternalRouteOnce(
            pendingExternalRoute = request,
            currentDestinationRoutes = flowOf("home"),
            isStillValidForScope = { scope ->
                assertEquals(ExternalRouteScope.Unscoped, scope)
                true
            },
            navigate = { navigated = it },
            onConsumed = { },
        )

        assertEquals("item/abc", navigated)
    }

    // --- identity scope matching ---

    /**
     * A link that arrived with a server but no profile — configured server,
     * nobody signed in — must still deliver once a profile IS chosen. Requiring
     * the profile to still be null dropped exactly the link the user was
     * signing in to open.
     */
    @Test
    fun aScopeCapturedBeforeSignInStillMatchesAfterIt() {
        val scope = ExternalRouteScope.Identity(serverId = "server-a", profileId = null)

        assertTrue(scope.matches(serverId = "server-a", profileId = "profile-1"))
        assertTrue(scope.matches(serverId = "server-a", profileId = null))
    }

    @Test
    fun aScopeDoesNotMatchAnotherServer() {
        val scope = ExternalRouteScope.Identity(serverId = "server-a", profileId = null)

        assertFalse(scope.matches(serverId = "server-b", profileId = "profile-1"))
    }

    /** A fully-specified notification scope must match both components. */
    @Test
    fun aFullyPinnedScopeRequiresBothComponents() {
        val scope = ExternalRouteScope.Identity(serverId = "server-a", profileId = "kids")

        assertTrue(scope.matches(serverId = "server-a", profileId = "kids"))
        assertFalse(scope.matches(serverId = "server-a", profileId = "adults"))
        assertFalse(scope.matches(serverId = "server-b", profileId = "kids"))
    }

    /** Nothing known constrains nothing — the signed-out arrival case. */
    @Test
    fun anEmptyScopeMatchesAnything() {
        val scope = ExternalRouteScope.Identity(serverId = null, profileId = null)

        assertTrue(scope.matches(serverId = "server-a", profileId = "kids"))
    }
}
