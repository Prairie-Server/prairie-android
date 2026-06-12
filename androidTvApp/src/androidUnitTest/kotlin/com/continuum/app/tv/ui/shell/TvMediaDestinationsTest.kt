package com.continuum.app.tv.ui.shell

import com.continuum.app.model.navigation.MediaMode
import com.continuum.app.model.navigation.MediaModeCapabilities
import com.continuum.app.model.navigation.tvMediaModeCapabilities
import com.continuum.app.model.personal.UserLibrary
import com.continuum.app.tv.ui.navigation.TvMainRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TvMediaDestinationsTest {
    @Test
    fun videoOnlyShowsVideoUtilities() {
        val destinations = visibleTvDestinations(MediaModeCapabilities(listOf(MediaMode.Video)))

        assertEquals(
            listOf(TvRootDestination.Search, TvRootDestination.Video, TvRootDestination.Requests),
            destinations,
        )
    }

    @Test
    fun audioOnlyShowsAudioUtilities() {
        val destinations = visibleTvDestinations(MediaModeCapabilities(listOf(MediaMode.Audio)))

        assertEquals(
            listOf(TvRootDestination.Search, TvRootDestination.Audio, TvRootDestination.Requests),
            destinations,
        )
    }

    @Test
    fun videoAndAudioKeepStableOrder() {
        val destinations = visibleTvDestinations(
            MediaModeCapabilities(listOf(MediaMode.Video, MediaMode.Audio)),
        )

        assertEquals(
            listOf(TvRootDestination.Search, TvRootDestination.Video, TvRootDestination.Audio, TvRootDestination.Requests),
            destinations,
        )
    }

    @Test
    fun readingOnlyDoesNotShowReadingOnTv() {
        val destinations = visibleTvDestinations(MediaModeCapabilities(listOf(MediaMode.Reading)))

        assertEquals(listOf(TvRootDestination.Search, TvRootDestination.Requests), destinations)
        assertFalse(destinations.any { it.name == "Reading" })
    }

    @Test
    fun firstTvContentRoutePrefersVideoThenAudioThenSearch() {
        assertEquals(
            TvMainRoute.Video.route,
            firstTvRoute(MediaModeCapabilities(listOf(MediaMode.Video, MediaMode.Audio))),
        )
        assertEquals(
            TvMainRoute.Audio.route,
            firstTvRoute(MediaModeCapabilities(listOf(MediaMode.Audio))),
        )
        assertEquals(
            TvMainRoute.Search.route,
            firstTvRoute(MediaModeCapabilities(listOf(MediaMode.Reading))),
        )
    }

    @Test
    fun audiobookOnlyLibraryShowsAudioOnTv() {
        val capabilities = listOf(userLibrary(type = "audiobook")).tvMediaModeCapabilities()

        assertEquals(listOf(MediaMode.Audio), capabilities.modes)
        assertEquals(
            listOf(TvRootDestination.Search, TvRootDestination.Audio, TvRootDestination.Requests),
            visibleTvDestinations(capabilities),
        )
        assertEquals(TvMainRoute.Audio.route, firstTvRoute(capabilities))
    }

    @Test
    fun ebookOnlyLibraryFallsBackToSearchOnTv() {
        val capabilities = listOf(userLibrary(type = "ebook")).tvMediaModeCapabilities()

        assertEquals(emptyList(), capabilities.modes)
        assertEquals(
            listOf(TvRootDestination.Search, TvRootDestination.Requests),
            visibleTvDestinations(capabilities),
        )
        assertEquals(TvMainRoute.Search.route, firstTvRoute(capabilities))
    }

    private fun userLibrary(type: String): UserLibrary =
        UserLibrary(
            id = 1,
            name = "Library",
            type = type,
        )
}
