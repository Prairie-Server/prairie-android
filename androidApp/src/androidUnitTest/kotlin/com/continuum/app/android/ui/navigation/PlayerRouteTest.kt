package com.continuum.app.android.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerRouteTest {

    @Test
    fun playerRouteIncludesResumePositionWhenProvided() {
        assertEquals(
            "player/episode-1?resumePosition=318.5",
            Route.Player(
                contentId = "episode-1",
                resumePositionSeconds = 318.5,
            ).route,
        )
    }

    @Test
    fun playerRouteKeepsResumePositionWithOtherSelections() {
        assertEquals(
            "player/movie-1?fileId=7&audioTrackIndex=1&subtitleTrackIndex=2&resumePosition=42.0",
            Route.Player(
                contentId = "movie-1",
                fileId = 7,
                audioTrackIndex = 1,
                subtitleTrackIndex = 2,
                resumePositionSeconds = 42.0,
            ).route,
        )
    }
}
