package org.prairieserver.prairie.model.recommendation

import org.prairieserver.prairie.model.section.SectionItem
import org.prairieserver.prairie.network.PrairieJson
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals

class RecommendationModelsTest {
    @Test
    fun roundTripsDiscoverAndTaste() {
        val row = DiscoverRow(
            type = "for_you",
            label = "For You",
            items = listOf(SectionItem(contentId = "m1", type = "movie", title = "M")),
        )
        val discover = DiscoverResponse(rows = listOf(row))
        assertEquals(discover, PrairieJson.decodeFromString(PrairieJson.encodeToString(discover)))
        val taste = TasteProfile(topGenres = listOf("scifi"), favoriteDirectors = listOf("Nolan"), signalCounts = mapOf("play" to 3))
        assertEquals(taste, PrairieJson.decodeFromString(PrairieJson.encodeToString(taste)))
        val scored = ScoredItemsResponse(items = listOf(ScoredItemRef(mediaItemId = "m1", score = 0.9, reason = "like")))
        assertEquals("m1", PrairieJson.decodeFromString<ScoredItemsResponse>(PrairieJson.encodeToString(scored)).items.single().mediaItemId)
    }
}
