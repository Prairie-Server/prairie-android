package com.continuum.app.tv.ui.navigation

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Pure routing decision: audiobook-type items go to the audiobook player route,
 * everything else to the video player route. Mirrors the catalog's
 * `isAudiobookItemType` taxonomy (case/whitespace-insensitive, singular "audiobook").
 */
class TvAudiobookRoutingTest {

    @Test
    fun audiobookTypeRoutesToAudiobookPlayer() {
        assertEquals(
            TvRoute.AudiobookPlayer("ab-1", fileId = 7).route,
            tvPlayDestinationFor(itemType = "audiobook", contentId = "ab-1", fileId = 7),
        )
    }

    @Test
    fun audiobookTypeIsCaseAndWhitespaceInsensitive() {
        assertEquals(
            TvRoute.AudiobookPlayer("ab-2", fileId = null).route,
            tvPlayDestinationFor(itemType = "  AudioBook ", contentId = "ab-2", fileId = null),
        )
    }

    @Test
    fun movieTypeRoutesToVideoPlayer() {
        assertEquals(
            TvRoute.Player("m-1", fileId = 3).route,
            tvPlayDestinationFor(itemType = "movie", contentId = "m-1", fileId = 3),
        )
    }

    @Test
    fun nullTypeRoutesToVideoPlayer() {
        assertEquals(
            TvRoute.Player("x-1", fileId = null).route,
            tvPlayDestinationFor(itemType = null, contentId = "x-1", fileId = null),
        )
    }

    @Test
    fun audiobookRouteCarriesExplicitStartPosition() {
        val route = tvPlayDestinationFor(
            itemType = "audiobook",
            contentId = "ab-3",
            fileId = 9,
            resumePositionSeconds = 1234.5,
        )

        assertContains(route, "audiobook/ab-3")
        assertContains(route, "fileId=9")
        assertContains(route, "startPosition=1234.5")
    }

    @Test
    fun audiobookRouteOmitsInvalidStartPosition() {
        val route = TvRoute.AudiobookPlayer(
            contentId = "ab-4",
            startPositionSeconds = Double.NaN,
        ).route

        assertFalse(route.contains("startPosition="))
    }
}
