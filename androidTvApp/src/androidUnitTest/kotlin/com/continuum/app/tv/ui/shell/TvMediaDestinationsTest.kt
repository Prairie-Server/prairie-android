package com.continuum.app.tv.ui.shell

import com.continuum.app.model.navigation.MediaMode
import com.continuum.app.model.navigation.MediaModeCapabilities
import com.continuum.app.tv.ui.navigation.TvMainRoute
import kotlin.test.Test
import kotlin.test.assertEquals

class TvMediaDestinationsTest {

    // Apple/tvOS-aligned shell: exactly four tabs — Search · Home · Libraries ·
    // For You — independent of which media types the libraries contain. Library
    // content is reached through the Libraries picker; Requests lives in Settings.
    private val expected = listOf(
        TvRootDestination.Search,
        TvRootDestination.Home,
        TvRootDestination.Libraries,
        TvRootDestination.ForYou,
    )

    @Test
    fun destinationsAreFixedRegardlessOfLibraryTypes() {
        assertEquals(expected, visibleTvDestinations(MediaModeCapabilities(listOf(MediaMode.Video))))
        assertEquals(expected, visibleTvDestinations(MediaModeCapabilities(listOf(MediaMode.Audio))))
        assertEquals(
            expected,
            visibleTvDestinations(MediaModeCapabilities(listOf(MediaMode.Video, MediaMode.Audio))),
        )
        assertEquals(expected, visibleTvDestinations(MediaModeCapabilities(emptyList())))
    }

    @Test
    fun firstRouteIsAlwaysHome() {
        assertEquals(
            TvMainRoute.Home.route,
            firstTvRoute(MediaModeCapabilities(listOf(MediaMode.Video, MediaMode.Audio))),
        )
        assertEquals(TvMainRoute.Home.route, firstTvRoute(MediaModeCapabilities(emptyList())))
    }
}
