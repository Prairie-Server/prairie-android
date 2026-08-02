package org.siloserver.silo.tv.ui.navigation

import org.siloserver.silo.common.player.video.EpisodeSelectionHandoff
import org.siloserver.silo.common.player.video.EpisodeSourceIntent
import org.siloserver.silo.common.player.video.EpisodeSubtitleIntent
import org.siloserver.silo.common.player.video.EpisodeSubtitleMode
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
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
    fun sameProcessRegistryDeliversHandoffOnceToMatchingContent() {
        val handoff = EpisodeSelectionHandoff(
            source = EpisodeSourceIntent(resolution = "1080p", videoCodec = "h264"),
            subtitle = EpisodeSubtitleIntent(
                mode = EpisodeSubtitleMode.TRACK,
                language = "en",
                codecFamily = "srt",
            ),
        )
        val registry = registryWithNonces("abcdefghijklmnop")
        val nonce = registry.register(targetContentId = "episode-123", handoff = handoff)

        val route = TvRoute.Player(
            contentId = "episode-123",
            episodeSelectionHandoffNonce = nonce,
        ).route

        val routedNonce = route.substringAfter("episodeSelectionHandoffNonce=")
        assertEquals(handoff, registry.claim(routedNonce, targetContentId = "episode-123"))
        assertNull(registry.claim(routedNonce, targetContentId = "episode-123"))
    }

    @Test
    fun playerRouteDoesNotPersistSemanticHandoffData() {
        val handoff = EpisodeSelectionHandoff(
            source = EpisodeSourceIntent(resolution = "1080p", videoCodec = "h264"),
            subtitle = EpisodeSubtitleIntent(
                mode = EpisodeSubtitleMode.TRACK,
                language = "en",
                codecFamily = "srt",
            ),
        )
        val registry = registryWithNonces("abcdefghijklmnop")
        val route = TvRoute.Player(
            contentId = "episode-123",
            episodeSelectionHandoffNonce = registry.register("episode-123", handoff),
        ).route

        assertFalse(route.contains("1080p"), "semantic source intent must not enter saved routes")
        assertFalse(route.contains("h264"), "codec intent must not enter saved routes")
        assertFalse(route.contains("srt"), "subtitle intent must not enter saved routes")
        assertFalse(route.contains("language"), "semantic field names must not enter saved routes")
        assertFalse(route.contains("{"), "handoff JSON must not enter saved routes")
    }

    @Test
    fun playerRouteWithoutHandoffKeepsExistingDefaults() {
        val route = TvRoute.Player(contentId = "episode-123").route

        assertFalse(route.contains("episodeSelectionHandoffNonce="))
        assertFalse(route.contains("fileId="))
        assertFalse(route.contains("subtitleTrackIndex="))
    }

    @Test
    fun contentMismatchRejectsAndConsumesRegistryEntry() {
        val registry = registryWithNonces("abcdefghijklmnop")
        val nonce = registry.register("episode-123", handoff())

        assertNull(registry.claim(nonce, targetContentId = "episode-456"))
        assertNull(registry.claim(nonce, targetContentId = "episode-123"))
    }

    @Test
    fun registryClearModelsProcessRecreation() {
        val registry = registryWithNonces("abcdefghijklmnop")
        val nonce = registry.register("episode-123", handoff())

        registry.clear()

        assertNull(registry.claim(nonce, targetContentId = "episode-123"))
    }

    @Test
    fun malformedAndOversizedNoncesAreIgnoredAndOmitted() {
        val registry = registryWithNonces("abcdefghijklmnop")

        assertNull(registry.claim("not valid!", targetContentId = "episode-123"))
        assertNull(registry.claim("a".repeat(256), targetContentId = "episode-123"))
        assertFalse(
            TvRoute.Player(
                contentId = "episode-123",
                episodeSelectionHandoffNonce = "not valid!",
            ).route.contains("episodeSelectionHandoffNonce="),
        )
        assertFalse(
            TvRoute.Player(
                contentId = "episode-123",
                episodeSelectionHandoffNonce = "a".repeat(256),
            ).route.contains("episodeSelectionHandoffNonce="),
        )
    }

    @Test
    fun registryEvictsOldestEntryAtCapacity() {
        val registry = registryWithNonces(
            "abcdefghijklmnop",
            "bcdefghijklmnopq",
            "cdefghijklmnopqr",
            maxEntries = 2,
        )
        val first = registry.register("episode-1", handoff())
        val second = registry.register("episode-2", handoff())
        val third = registry.register("episode-3", handoff())

        assertNull(registry.claim(first, "episode-1"))
        assertEquals(handoff(), registry.claim(second, "episode-2"))
        assertEquals(handoff(), registry.claim(third, "episode-3"))
    }

    @Test
    fun registryExpiresEntriesAfterBoundedLifetime() {
        var nowMillis = 1_000L
        val registry = TvEpisodeSelectionHandoffRegistry(
            maxEntries = 2,
            ttlMillis = 500L,
            nowMillis = { nowMillis },
            nonceFactory = { "abcdefghijklmnop" },
        )
        val nonce = registry.register("episode-123", handoff())

        nowMillis += 500L

        assertNull(registry.claim(nonce, "episode-123"))
    }

    @Test
    fun startOverPassesExplicitZeroResumePosition() {
        assertTrue(
            detailSource.contains("playType, 0.0,"),
            "Start Over must pass an explicit 0.0 override; null falls back to stored progress",
        )
    }

    private fun registryWithNonces(
        vararg nonces: String,
        maxEntries: Int = 8,
    ): TvEpisodeSelectionHandoffRegistry {
        val iterator = nonces.iterator()
        return TvEpisodeSelectionHandoffRegistry(
            maxEntries = maxEntries,
            ttlMillis = 60_000L,
            nowMillis = { 1_000L },
            nonceFactory = { iterator.next() },
        )
    }

    private fun handoff() = EpisodeSelectionHandoff(
        source = EpisodeSourceIntent(resolution = "1080p", videoCodec = "h264"),
        subtitle = EpisodeSubtitleIntent.off(),
    )
}
