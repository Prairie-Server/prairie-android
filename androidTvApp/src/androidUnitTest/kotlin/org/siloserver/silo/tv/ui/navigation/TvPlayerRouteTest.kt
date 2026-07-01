package org.siloserver.silo.tv.ui.navigation

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvPlayerRouteTest {
    private val detailSource = java.io.File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvItemDetailScreen.kt",
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
    fun playerRouteIncludesTrackIndexesWhenPresent() {
        val route = TvRoute.Player(
            contentId = "movie-123",
            audioTrackIndex = 2,
            subtitleTrackIndex = -1,
        ).route

        assertContains(route, "audioTrackIndex=2")
        assertContains(route, "subtitleTrackIndex=-1")
    }

    @Test
    fun playerRouteOmitsTrackIndexesWhenAbsent() {
        val route = TvRoute.Player(contentId = "movie-123").route

        assertFalse(route.contains("audioTrackIndex="))
        assertFalse(route.contains("subtitleTrackIndex="))
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
            detailSource.contains("playType, 0.0,"),
            "Start Over must pass an explicit 0.0 override; null falls back to stored progress",
        )
    }
}
