package org.siloserver.silo.tv.ui.navigation

import org.siloserver.silo.common.player.video.EpisodeSelectionHandoff
import org.siloserver.silo.common.player.video.EpisodeSourceIntent
import org.siloserver.silo.common.player.video.EpisodeSubtitleIntent
import org.siloserver.silo.common.player.video.EpisodeSubtitleMode
import org.siloserver.silo.common.player.video.decodeEpisodeSelectionHandoff
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    fun playerRouteRoundTripsEpisodeSelectionHandoff() {
        val handoff = EpisodeSelectionHandoff(
            source = EpisodeSourceIntent(resolution = "1080p", videoCodec = "h264"),
            subtitle = EpisodeSubtitleIntent(
                mode = EpisodeSubtitleMode.TRACK,
                language = "en",
                codecFamily = "srt",
            ),
        )

        val route = TvRoute.Player(
            contentId = "episode-123",
            episodeSelectionHandoff = handoff,
        ).route

        val payload = route.substringAfter("episodeSelectionHandoff=")
        assertTrue(payload.contains("%"), "handoff JSON must be URL-encoded in the route")
        assertTrue(
            decodeEpisodeSelectionHandoff(java.net.URLDecoder.decode(payload, Charsets.UTF_8)) == handoff,
            "decoded route payload must preserve semantic selection intent",
        )
    }

    @Test
    fun playerRouteWithoutHandoffKeepsExistingDefaults() {
        val route = TvRoute.Player(contentId = "episode-123").route

        assertFalse(route.contains("episodeSelectionHandoff="))
        assertFalse(route.contains("fileId="))
        assertFalse(route.contains("subtitleTrackIndex="))
    }

    @Test
    fun malformedEpisodeHandoffIsIgnored() {
        assertNull(decodeEpisodeSelectionHandoff("{not-json"))
    }

    @Test
    fun startOverPassesExplicitZeroResumePosition() {
        assertTrue(
            detailSource.contains("playType, 0.0,"),
            "Start Over must pass an explicit 0.0 override; null falls back to stored progress",
        )
    }
}
