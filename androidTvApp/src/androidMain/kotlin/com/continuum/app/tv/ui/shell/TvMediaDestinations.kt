package com.continuum.app.tv.ui.shell

import com.continuum.app.model.navigation.MediaMode
import com.continuum.app.model.navigation.MediaModeCapabilities
import com.continuum.app.tv.ui.navigation.TvMainRoute

fun visibleTvDestinations(capabilities: MediaModeCapabilities): List<TvRootDestination> = buildList {
    add(TvRootDestination.Search)
    capabilities.tvModes().forEach { mode ->
        when (mode) {
            MediaMode.Video -> add(TvRootDestination.Video)
            MediaMode.Audio -> add(TvRootDestination.Audio)
            MediaMode.Reading -> Unit
        }
    }
    add(TvRootDestination.Requests)
}

fun firstTvRoute(capabilities: MediaModeCapabilities): String =
    when (capabilities.firstTvMode()) {
        MediaMode.Video -> TvMainRoute.Video.route
        MediaMode.Audio -> TvMainRoute.Audio.route
        MediaMode.Reading,
        null -> TvMainRoute.Search.route
    }

fun TvRootDestination.isVisibleIn(destinations: List<TvRootDestination>): Boolean =
    this in destinations
