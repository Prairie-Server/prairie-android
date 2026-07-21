package org.siloserver.silo.tv.ui.screens.detail

import org.siloserver.silo.model.catalog.EpisodeListItem
import org.siloserver.silo.model.catalog.ItemDetail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvDetailHeroArtworkTest {
    @Test
    fun episodicDetailFallsBackToLandscapeEpisodeStill() {
        val detail = ItemDetail(
            contentId = "series-1",
            type = "series",
            title = "Series",
            posterUrl = "portrait-poster",
        )
        val nextUp = EpisodeListItem(
            contentId = "episode-1",
            seasonNumber = 1,
            episodeNumber = 2,
            stillUrl = "landscape-still",
            stillThumbhash = "still-hash",
        )

        assertEquals(
            TvDetailHeroArtwork("landscape-still", "still-hash"),
            resolveTvDetailHeroArtwork(detail, nextUp),
        )
    }

    @Test
    fun explicitBackdropAlwaysWins() {
        val detail = ItemDetail(
            contentId = "series-1",
            type = "series",
            title = "Series",
            backdropUrl = "backdrop",
            backdropThumbhash = "backdrop-hash",
            posterUrl = "portrait-poster",
        )
        val nextUp = EpisodeListItem(
            contentId = "episode-1",
            seasonNumber = 1,
            episodeNumber = 1,
            stillUrl = "landscape-still",
        )

        assertEquals(
            TvDetailHeroArtwork("backdrop", "backdrop-hash"),
            resolveTvDetailHeroArtwork(detail, nextUp),
        )
    }

    @Test
    fun portraitPosterIsNotStretchedAcrossNonEpisodicHero() {
        val detail = ItemDetail(
            contentId = "movie-1",
            type = "movie",
            title = "Movie",
            posterUrl = "portrait-poster",
        )

        val artwork = resolveTvDetailHeroArtwork(detail, null)

        assertNull(artwork.url)
        assertNull(artwork.thumbhash)
    }
}
