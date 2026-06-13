package com.continuum.app.tv.ui.navigation

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class TvPlayerRouteTest {
    @Test
    fun playerRouteIncludesResumePositionWhenPresent() {
        val route = TvRoute.Player(
            contentId = "movie-123",
            fileId = 44,
            roomId = "room one",
            resumePositionSeconds = 1887.25,
        ).route

        assertContains(route, "player/movie-123")
        assertContains(route, "fileId=44")
        assertContains(route, "roomId=room%20one")
        assertContains(route, "resumePosition=1887.25")
    }

    @Test
    fun playerRouteOmitsInvalidResumePosition() {
        val route = TvRoute.Player(
            contentId = "movie-123",
            resumePositionSeconds = Double.NaN,
        ).route

        assertFalse(route.contains("resumePosition="))
    }
}
