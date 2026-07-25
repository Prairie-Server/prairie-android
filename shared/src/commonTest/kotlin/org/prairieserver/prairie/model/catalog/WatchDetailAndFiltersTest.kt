package org.prairieserver.prairie.model.catalog

import org.prairieserver.prairie.network.PrairieJson
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals

class WatchDetailAndFiltersTest {
    @Test
    fun roundTripsWatchDetailAndFilters() {
        val detail = WatchDetail(
            contentId = "m1",
            type = "movie",
            title = "M",
            year = 2024,
            overview = "o",
            posterUrl = "p",
        )
        assertEquals(detail, PrairieJson.decodeFromString(PrairieJson.encodeToString(detail)))
        val filters = CatalogFiltersResponse(
            genres = listOf("Action"),
            studios = listOf("A24"),
            networks = listOf("HBO"),
        )
        assertEquals(filters, PrairieJson.decodeFromString(PrairieJson.encodeToString(filters)))
    }
}
