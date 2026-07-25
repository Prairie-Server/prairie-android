package org.prairieserver.prairie.tv.ui.screens.detail

import org.prairieserver.prairie.model.audiobook.AudiobookMetadata
import org.prairieserver.prairie.model.catalog.AudioTrack
import org.prairieserver.prairie.model.catalog.FileVersion
import org.prairieserver.prairie.model.catalog.ItemDetail
import org.prairieserver.prairie.model.catalog.SubtitleTrack
import org.prairieserver.prairie.model.catalog.VideoTrack
import org.prairieserver.prairie.model.ebook.MediaPerson
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class TvDetailMetadataTest {
    @Test
    fun episodeFactsPutAirDateBeforeRuntime() {
        val detail = ItemDetail(
            contentId = "e1",
            type = "episode",
            title = "Episode",
            year = 2026,
            airDate = "2026-03-30T00:00:00Z",
            runtime = 52,
        )

        assertEquals(
            listOf(
                TvHeroFactToken.TextToken("Mar 30, 2026"),
                TvHeroFactToken.TextToken("52 min"),
            ),
            TvDetailMetadata.factsLine(detail, zone = ZoneId.of("UTC")),
        )
    }

    @Test
    fun airDateRendersInViewerZoneLikeTvOs() {
        // tvOS parses the timestamp as a UTC instant and formats the LOCAL
        // calendar date, so a midnight-UTC air date shows the previous day for
        // viewers west of UTC. The Android hero mirrors that.
        assertEquals(
            "Mar 29, 2026",
            TvDetailMetadata.abbreviatedDate(
                "2026-03-30T00:00:00Z",
                zone = ZoneId.of("America/Los_Angeles"),
            ),
        )
        // Bare full dates are parsed at UTC midnight too (Apple's
        // `.withFullDate` ISO8601 parser defaults to GMT).
        assertEquals(
            "Mar 29, 2026",
            TvDetailMetadata.abbreviatedDate(
                "2026-03-30",
                zone = ZoneId.of("America/Los_Angeles"),
            ),
        )
    }

    @Test
    fun audiobookSourceTokensIncludeTypePublisherAndNarrator() {
        val detail = ItemDetail(
            contentId = "a1",
            type = "audiobook",
            title = "Audio",
            audiobook = AudiobookMetadata(
                publisher = "Prairie Press",
                narrators = listOf(MediaPerson(name = "Nia Narrator")),
            ),
        )

        assertEquals(
            listOf("Audiobook", "Prairie Press", "Narrated by Nia Narrator"),
            TvDetailMetadata.sourceTokens(detail),
        )
    }

    @Test
    fun factsLineUsesPreferredQualityForVersionBadges() {
        val detail = ItemDetail(
            contentId = "m1",
            type = "movie",
            title = "Movie",
            versions = listOf(
                FileVersion(fileId = 1080, resolution = "1080p"),
                FileVersion(fileId = 2160, resolution = "2160p", hdr = true),
            ),
        )

        assertEquals(
            listOf(TvHeroFactToken.Chip("HD")),
            TvDetailMetadata.factsLine(detail, preferredQuality = "1080p"),
        )
    }

    @Test
    fun factsLineUsesSelectedFileIdForVersionBadges() {
        val detail = ItemDetail(
            contentId = "m1",
            type = "movie",
            title = "Movie",
            versions = listOf(
                FileVersion(
                    fileId = 1080,
                    resolution = "1080p",
                    audioTracks = listOf(AudioTrack(channels = 2, isDefault = true)),
                ),
                FileVersion(
                    fileId = 2160,
                    resolution = "2160p",
                    hdr = true,
                    audioTracks = listOf(AudioTrack(channels = 6, isDefault = true)),
                    subtitleTracks = listOf(SubtitleTrack(language = "en")),
                ),
            ),
        )

        assertEquals(
            listOf(
                TvHeroFactToken.Chip("4K"),
                TvHeroFactToken.Chip("HDR"),
                TvHeroFactToken.Chip("5.1"),
                TvHeroFactToken.Chip("CC"),
            ),
            TvDetailMetadata.factsLine(
                detail = detail,
                preferredQuality = "1080p",
                selectedFileId = 2160,
            ),
        )
    }

    @Test
    fun factsLineNamesDolbyVisionFromVideoTrackMetadata() {
        val detail = ItemDetail(
            contentId = "m1",
            type = "movie",
            title = "Movie",
            versions = listOf(
                FileVersion(
                    fileId = 2160,
                    resolution = "2160p",
                    hdr = true,
                    videoTracks = listOf(
                        VideoTrack(codec = "hevc", dolbyVision = "Profile 8", hdr = true),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                TvHeroFactToken.Chip("4K"),
                TvHeroFactToken.Chip("DOLBY VISION"),
            ),
            TvDetailMetadata.factsLine(detail),
        )
    }
}
