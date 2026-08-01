package org.siloserver.silo.tv.ui.screens.player

import org.siloserver.silo.common.player.video.EpisodeSubtitleMode
import org.siloserver.silo.common.player.video.ResolvedEpisodeSelection
import org.siloserver.silo.model.catalog.FileVersion
import org.siloserver.silo.model.playback.PlayerSubtitleInfo
import org.siloserver.silo.model.playback.SubtitleIdentity
import org.siloserver.silo.model.playback.SubtitleMediaIdentity
import org.siloserver.silo.watchtogether.shouldNavigateToLocalNext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TvPlayNextSelectionHandoffTest {
    @Test
    fun nextEpisodeCapturesCurrentSourceAndCommittedSubtitleSemantics() {
        val handoff = captureTvEpisodeSelectionHandoff(
            activeVersion = FileVersion(
                fileId = 42,
                resolution = "2160p",
                codecVideo = "hevc",
                container = "mkv",
            ),
            committedSubtitleIdentity = SubtitleIdentity.ServerSidecar(serverIndex = 9),
            catalogSubtitles = listOf(subtitle(index = 9, language = "nl", codec = "srt")),
            hasExplicitSubtitleSelection = true,
        )

        assertEquals("2160p", handoff.source?.resolution)
        assertEquals("hevc", handoff.source?.videoCodec)
        assertEquals("nl", handoff.subtitle.language)
        assertEquals("subrip", handoff.subtitle.codecFamily)
        assertTrue(handoff.toString().contains("42").not(), "episode-local file IDs must not cross episodes")
        assertTrue(handoff.toString().contains("9").not(), "episode-local subtitle indexes must not cross episodes")
    }

    @Test
    fun nextEpisodeCarriesExplicitOff() {
        val handoff = captureTvEpisodeSelectionHandoff(
            activeVersion = null,
            committedSubtitleIdentity = SubtitleIdentity.Off,
            catalogSubtitles = emptyList(),
            hasExplicitSubtitleSelection = true,
        )

        assertEquals(EpisodeSubtitleMode.OFF, handoff.subtitle.mode)
    }

    @Test
    fun nextEpisodeCarriesAutoWhenNoExplicitSubtitleWasCommitted() {
        val handoff = captureTvEpisodeSelectionHandoff(
            activeVersion = null,
            committedSubtitleIdentity = SubtitleIdentity.ServerSidecar(serverIndex = 9),
            catalogSubtitles = listOf(subtitle(index = 9, language = "nl", codec = "srt")),
            hasExplicitSubtitleSelection = false,
        )

        assertEquals(EpisodeSubtitleMode.AUTO, handoff.subtitle.mode)
    }

    @Test
    fun downloadedPlaybackDropsDownloadIdentityButKeepsMediaSemantics() {
        val handoff = captureTvEpisodeSelectionHandoff(
            activeVersion = FileVersion(fileId = 42, resolution = "1080p", codecVideo = "h264"),
            committedSubtitleIdentity = SubtitleIdentity.Downloaded(
                downloadId = 777,
                media = SubtitleMediaIdentity(language = "nl", codecFamily = "srt"),
            ),
            catalogSubtitles = listOf(subtitle(index = 9, language = "nl", codec = "srt")),
            hasExplicitSubtitleSelection = true,
        )

        assertEquals("1080p", handoff.source?.resolution)
        assertEquals(EpisodeSubtitleMode.AUTO, handoff.subtitle.mode)
        assertTrue(handoff.toString().contains("777").not(), "download identity is episode-local")
    }

    @Test
    fun watchTogetherStillSuppressesSoloAutoAdvance() {
        assertTrue(shouldNavigateToLocalNext(inWatchTogetherRoom = false))
        assertTrue(shouldNavigateToLocalNext(inWatchTogetherRoom = true).not())
    }

    @Test
    fun profileOrServerReplacementDoesNotReuseAnOldHandoff() {
        val slot = TvEpisodeSelectionHandoffSlot(
            captureTvEpisodeSelectionHandoff(
                activeVersion = FileVersion(fileId = 42, resolution = "1080p"),
                committedSubtitleIdentity = SubtitleIdentity.Off,
                catalogSubtitles = emptyList(),
                hasExplicitSubtitleSelection = true,
            ),
        )

        assertEquals(EpisodeSubtitleMode.OFF, slot.takeForStart()?.subtitle?.mode)
        assertNull(slot.takeForStart(), "replacement starts must not reuse a prior episode handoff")
    }

    @Test
    fun resolvedExplicitTargetSubtitleBlocksDurableTargetRestore() {
        val application = resolveTvEpisodeInitialSubtitleSelection(
            episodeSelectionHandoff = captureTvEpisodeSelectionHandoff(
                activeVersion = null,
                committedSubtitleIdentity = SubtitleIdentity.Off,
                catalogSubtitles = emptyList(),
                hasExplicitSubtitleSelection = true,
            ),
            resolvedEpisodeSelection = ResolvedEpisodeSelection(
                fileId = 1080,
                subtitleTrackIndex = null,
                subtitleIntentSpecified = true,
            ),
            existingPendingInitialSubtitleIndex = 4,
        )

        assertNull(application.pendingInitialSubtitleIndex)
        assertTrue(application.suppressDurableSubtitleRestore)
    }

    @Test
    fun resolvedOffAppliesMedia3OffAndAutoPreservesDurableRestore() {
        val off = resolveTvEpisodeInitialSubtitleSelection(
            episodeSelectionHandoff = captureTvEpisodeSelectionHandoff(
                activeVersion = null,
                committedSubtitleIdentity = SubtitleIdentity.Off,
                catalogSubtitles = emptyList(),
                hasExplicitSubtitleSelection = true,
            ),
            resolvedEpisodeSelection = ResolvedEpisodeSelection(
                fileId = null,
                subtitleTrackIndex = -1,
                subtitleIntentSpecified = true,
            ),
            existingPendingInitialSubtitleIndex = null,
        )
        val automatic = resolveTvEpisodeInitialSubtitleSelection(
            episodeSelectionHandoff = captureTvEpisodeSelectionHandoff(
                activeVersion = null,
                committedSubtitleIdentity = SubtitleIdentity.Off,
                catalogSubtitles = emptyList(),
                hasExplicitSubtitleSelection = false,
            ),
            resolvedEpisodeSelection = ResolvedEpisodeSelection(
                fileId = null,
                subtitleTrackIndex = null,
                subtitleIntentSpecified = false,
            ),
            existingPendingInitialSubtitleIndex = null,
        )

        assertEquals(-1, off.pendingInitialSubtitleIndex)
        assertTrue(off.suppressDurableSubtitleRestore)
        assertTrue(automatic.suppressDurableSubtitleRestore.not())
    }

    private fun subtitle(index: Int, language: String, codec: String) = PlayerSubtitleInfo(
        index = index,
        language = language,
        codec = codec,
        url = "https://example.test/$index.$codec",
    )
}
