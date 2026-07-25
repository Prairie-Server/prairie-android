package org.prairieserver.prairie.tv.ui.screens.home

import org.prairieserver.prairie.model.calendar.CalendarItem
import org.prairieserver.prairie.model.calendar.CalendarItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvUpcomingViewModelMappingTest {

    private fun episode(
        contentId: String = "ep-1",
        seriesId: String? = "series-1",
        title: String = "Episode Title",
        seasonNumber: Int? = 2,
        episodeNumber: Int? = 5,
        posterUrl: String? = "https://example.com/poster.jpg",
        posterThumbhash: String? = "abc123",
    ) = CalendarItem(
        contentId = contentId,
        type = "episode",
        title = title,
        seriesId = seriesId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        airDate = "2026-06-15",
        localAirDate = "2026-06-15",
        posterUrl = posterUrl,
        posterThumbhash = posterThumbhash,
    )

    private fun movie(
        contentId: String = "movie-1",
        title: String = "Movie Title",
        posterUrl: String? = "https://example.com/movie.jpg",
        posterThumbhash: String? = "xyz789",
    ) = CalendarItem(
        contentId = contentId,
        type = "movie",
        title = title,
        airDate = "2026-06-15",
        localAirDate = "2026-06-15",
        posterUrl = posterUrl,
        posterThumbhash = posterThumbhash,
    )

    @Test
    fun episodeMapsToSeriesDetailContentId() {
        val item = episode(contentId = "ep-1", seriesId = "series-42")
        val result = item.toSectionItem()
        // Episodes should route to the series detail, not the episode itself.
        assertEquals("series-42", result.contentId)
    }

    @Test
    fun episodeWithNoSeriesIdFallsBackToContentId() {
        val item = episode(contentId = "ep-99", seriesId = null)
        val result = item.toSectionItem()
        assertEquals("ep-99", result.contentId)
    }

    @Test
    fun episodeMapsTypeToSeries() {
        val result = episode().toSectionItem()
        assertEquals("series", result.type)
    }

    @Test
    fun movieMapsToMovieType() {
        val result = movie().toSectionItem()
        assertEquals(CalendarItemType.Movie, result.type)
    }

    @Test
    fun movieUsesOwnContentId() {
        val item = movie(contentId = "movie-7")
        val result = item.toSectionItem()
        assertEquals("movie-7", result.contentId)
    }

    @Test
    fun posterFieldsArePropagated() {
        val item = episode(posterUrl = "https://img/p.jpg", posterThumbhash = "hash1")
        val result = item.toSectionItem()
        assertEquals("https://img/p.jpg", result.posterUrl)
        assertEquals("hash1", result.posterThumbhash)
    }

    @Test
    fun nullPosterFieldsStayNull() {
        val item = movie(posterUrl = null, posterThumbhash = null)
        val result = item.toSectionItem()
        assertNull(result.posterUrl)
        assertNull(result.posterThumbhash)
    }

    @Test
    fun seasonAndEpisodeNumberArePropagated() {
        val item = episode(seasonNumber = 3, episodeNumber = 7)
        val result = item.toSectionItem()
        assertEquals(3, result.seasonNumber)
        assertEquals(7, result.episodeNumber)
    }

    @Test
    fun titleIsPropagated() {
        val item = movie(title = "Interstellar")
        val result = item.toSectionItem()
        assertEquals("Interstellar", result.title)
    }
}
