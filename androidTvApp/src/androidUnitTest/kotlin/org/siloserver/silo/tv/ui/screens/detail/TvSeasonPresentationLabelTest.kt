package org.siloserver.silo.tv.ui.screens.detail

import org.siloserver.silo.model.catalog.ItemDetail
import org.siloserver.silo.model.catalog.Season
import kotlin.test.Test
import kotlin.test.assertEquals

class TvSeasonPresentationLabelTest {
    @Test
    fun pickerLabelsSeasonZeroWithoutSpecialsFlagAsSpecials() {
        assertEquals(
            "Specials",
            tvSeasonPickerLabel(Season(contentId = "specials", seasonNumber = 0)),
        )
    }

    @Test
    fun pickerLabelsNonzeroSeasonWithSpecialsFlagAsSpecials() {
        assertEquals(
            "Specials",
            tvSeasonPickerLabel(
                Season(contentId = "bonus", seasonNumber = 99, isSpecials = true),
            ),
        )
    }

    @Test
    fun explicitSpecialsEpisodeUsesSpecialsHeader() {
        val detail = ItemDetail(
            contentId = "episode-special",
            type = "episode",
            title = "Bonus",
            seasonNumber = 0,
        )

        assertEquals("Specials", episodeEyebrowLabel(detail, TvItemDetailUiState()))
    }

    @Test
    fun specialsOnlySeriesUsesSpecialsHeaderAndPickerLabel() {
        val specials = Season(contentId = "specials", seasonNumber = 0)
        val detail = ItemDetail(
            contentId = "series",
            type = "series",
            title = "Series",
        )
        val state = TvItemDetailUiState(
            seasons = listOf(specials),
            selectedSeason = 0,
        )

        assertEquals("Specials", episodeEyebrowLabel(detail, state))
        assertEquals("Specials", tvSeasonPickerLabel(specials))
    }
}
