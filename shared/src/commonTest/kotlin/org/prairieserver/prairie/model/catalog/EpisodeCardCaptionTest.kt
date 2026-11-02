package org.prairieserver.prairie.model.catalog

import org.prairieserver.prairie.model.section.SectionItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpisodeCardCaptionTest {
    private fun episode(title: String?) =
        EpisodeListItem(contentId = "e1", seasonNumber = 1, episodeNumber = 2, title = title)

    @Test
    fun titleIsZeroPaddedCodeThenTitle() {
        assertEquals("S01E02 · Pilot", EpisodeCardCaption.title(episode(" Pilot ")))
        assertEquals("S01E02", EpisodeCardCaption.title(episode("  ")))
        assertEquals("S01E02", EpisodeCardCaption.title(episode(null)))
        assertEquals(
            "S12E104 · Late",
            EpisodeCardCaption.title(EpisodeListItem(contentId = "e", seasonNumber = 12, episodeNumber = 104, title = "Late")),
        )
    }

    @Test
    fun metadataJoinsAirDateAndRuntime() {
        assertEquals("Feb 18, 2026  ·  52m", EpisodeCardCaption.metadata("Feb 18, 2026", 52))
        assertEquals("1h 5m", EpisodeCardCaption.metadata(" ", 65))
        assertEquals("Feb 18, 2026", EpisodeCardCaption.metadata("Feb 18, 2026", 0))
        assertNull(EpisodeCardCaption.metadata(null, -1))
    }
}

class EpisodeParentArtworkTest {
    @Test
    fun episodesUseSeriesBackdropAndSeasonThenSeriesPoster() {
        val section = SectionItem(
            contentId = "e1", type = "Episode", title = "Pilot",
            seriesBackdropUrl = "/sb.jpg", seriesBackdropThumbhash = "sbh",
            seasonPosterUrl = "/sp.jpg", seasonPosterThumbhash = "sph",
            seriesPosterUrl = "/p.jpg", seriesPosterThumbhash = "ph",
        )
        assertEquals(ArtworkRef("/sb.jpg", "sbh"), section.seriesHeroBackdrop())
        assertEquals(ArtworkRef("/sp.jpg", "sph"), section.parentPoster())
        assertEquals(ArtworkRef("/p.jpg", "ph"), section.copy(seasonPosterUrl = " ").parentPoster())
        assertNull(section.copy(seasonPosterUrl = null, seriesPosterUrl = null).parentPoster())
        assertNull(section.copy(seriesBackdropUrl = "").seriesHeroBackdrop())
        assertNull(section.copy(type = "movie").seriesHeroBackdrop())
        assertNull(section.copy(type = "movie").parentPoster())

        val browse = BrowseItem(contentId = "e1", type = "episode", title = "Pilot", seriesBackdropUrl = "/sb.jpg")
        assertEquals(ArtworkRef("/sb.jpg", null), browse.seriesHeroBackdrop())
        assertNull(browse.copy(type = "series").seriesHeroBackdrop())

        val detail = ItemDetail(
            contentId = "e1", type = "episode", title = "Pilot",
            seriesBackdropUrl = "/sb.jpg", seriesPosterUrl = "/p.jpg",
        )
        assertEquals(ArtworkRef("/sb.jpg", null), detail.seriesHeroBackdrop())
        assertEquals(ArtworkRef("/p.jpg", null), detail.parentPoster())
        assertNull(detail.copy(type = "movie").parentPoster())
    }
}
