package com.continuum.app.tv.ui.shell

import com.continuum.app.model.navigation.MediaModeCapabilities
import com.continuum.app.tv.ui.navigation.TvMainRoute

// Apple/tvOS-aligned shell: exactly four tabs — Search · Home · Libraries ·
// For You. There is no per-media-type tab (Home is the curated landing,
// Libraries is one tab with a library picker). Requests is not a tab; it's
// reached from the Settings screen, like the other secondary destinations.
fun visibleTvDestinations(
    @Suppress("UNUSED_PARAMETER") capabilities: MediaModeCapabilities,
): List<TvRootDestination> = listOf(
    TvRootDestination.Search,
    TvRootDestination.Home,
    TvRootDestination.Libraries,
    TvRootDestination.ForYou,
)

fun firstTvRoute(
    @Suppress("UNUSED_PARAMETER") capabilities: MediaModeCapabilities,
): String = TvMainRoute.Home.route

fun TvRootDestination.isVisibleIn(destinations: List<TvRootDestination>): Boolean =
    this in destinations
