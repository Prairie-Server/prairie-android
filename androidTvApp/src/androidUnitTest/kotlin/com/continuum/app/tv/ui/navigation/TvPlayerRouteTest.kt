package com.continuum.app.tv.ui.navigation

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvPlayerRouteTest {
    private val detailSource = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvItemDetailScreen.kt",
    ).readText()

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

    @Test
    fun startOverPassesExplicitZeroResumePosition() {
        assertTrue(
            detailSource.contains("onPlay(detail.contentId, selectedFileId, detail.type, 0.0)"),
            "Start Over must pass an explicit 0.0 override; null falls back to stored progress",
        )
    }
}
