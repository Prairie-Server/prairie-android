package com.continuum.app.tv.ui.components

import com.continuum.app.model.catalog.CastMember
import com.continuum.app.model.catalog.ItemDetail
import com.continuum.app.model.section.SectionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * §9 marquee detail-enrichment port of tvOS `TVMarqueeEnrichment` /
 * `TVFocusMarqueeModel.backdropURL`: the aired/cast line applies to every item;
 * the series-backdrop upgrade applies to episodes only.
 */
class TvFocusMarqueeEnrichmentTest {

    private fun detail(
        contentId: String = "c1",
        type: String = "movie",
        airDate: String? = null,
        cast: List<CastMember> = emptyList(),
        backdropUrl: String? = "https://art/series.jpg",
        backdropThumbhash: String? = "hash",
    ): ItemDetail = ItemDetail(
        contentId = contentId,
        type = type,
        title = "Title",
        airDate = airDate,
        cast = cast,
        backdropUrl = backdropUrl,
        backdropThumbhash = backdropThumbhash,
    )

    private fun episodeContent(
        backdropUrl: String? = "https://still/episode.jpg",
    ): TvMarqueeContent = TvMarqueeContent.from(
        item = SectionItem(
            contentId = "c1",
            type = "episode",
            title = "Ep Title",
            seriesTitle = "The Series",
            backdropUrl = backdropUrl,
        ),
        rowTitle = "Row",
    )

    private fun movieContent(): TvMarqueeContent = TvMarqueeContent.from(
        item = SectionItem(
            contentId = "c1",
            type = "movie",
            title = "Movie",
            backdropUrl = "https://still/movie.jpg",
        ),
        rowTitle = "Row",
    )

    @Test
    fun `detail line joins aired date and top-3 cast sorted by order`() {
        val enrichment = TvMarqueeEnrichment.from(
            detail(
                airDate = "2013-01-05",
                cast = listOf(
                    CastMember(name = "Third", order = 2),
                    CastMember(name = "First", order = 0),
                    CastMember(name = "Second", order = 1),
                    CastMember(name = "Fourth", order = 3),
                ),
            ),
        )
        assertEquals("Aired Jan 5, 2013 · First, Second, Third", enrichment.detailLine)
    }

    @Test
    fun `detail line omits aired part when air date is unparseable`() {
        val enrichment = TvMarqueeEnrichment.from(
            detail(airDate = "not-a-date", cast = listOf(CastMember(name = "Only", order = 0))),
        )
        assertEquals("Only", enrichment.detailLine)
    }

    @Test
    fun `detail line is null when both aired and cast are empty`() {
        val enrichment = TvMarqueeEnrichment.from(detail(airDate = null, cast = emptyList()))
        assertNull(enrichment.detailLine)
    }

    @Test
    fun `episode upgrades hero backdrop to series backdrop and gains detail line`() {
        val content = episodeContent()
        val enriched = content.withEnrichment(
            TvMarqueeEnrichment.from(
                detail(
                    type = "series",
                    airDate = "2013-01-05",
                    cast = listOf(CastMember(name = "Star", order = 0)),
                    backdropUrl = "https://art/series.jpg",
                    backdropThumbhash = "seriesHash",
                ),
            ),
        )
        assertEquals("https://art/series.jpg", enriched.heroBackdropUrl)
        assertEquals("seriesHash", enriched.heroBackdropThumbhash)
        assertEquals("Aired Jan 5, 2013 · Star", enriched.detailLine)
    }

    @Test
    fun `non-episode keeps its own backdrop but still gains detail line`() {
        val content = movieContent()
        val enriched = content.withEnrichment(
            TvMarqueeEnrichment.from(
                detail(
                    airDate = "2013-01-05",
                    backdropUrl = "https://art/other.jpg",
                ),
            ),
        )
        assertEquals("https://still/movie.jpg", enriched.heroBackdropUrl)
        assertEquals("Aired Jan 5, 2013", enriched.detailLine)
    }

    @Test
    fun `episode shows no hero backdrop until a series backdrop is enriched in`() {
        // The low-res episode still is the wrong image for the cinematic hero,
        // so episodes stay on the ambient wash (null) rather than flashing the
        // still and then swapping to the series backdrop. With no series
        // backdrop available, the hero simply has no image.
        val content = episodeContent()
        assertEquals(null, content.heroBackdropUrl)
        val enriched = content.withEnrichment(
            TvMarqueeEnrichment.from(detail(backdropUrl = null, backdropThumbhash = null)),
        )
        assertEquals(null, enriched.heroBackdropUrl)
    }
}
