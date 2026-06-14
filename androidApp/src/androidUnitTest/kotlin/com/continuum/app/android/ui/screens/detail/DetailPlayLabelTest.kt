package com.continuum.app.android.ui.screens.detail

import com.continuum.app.model.catalog.ItemDetail
import com.continuum.app.model.catalog.LeafItemUserData
import kotlin.test.Test
import kotlin.test.assertEquals

class DetailPlayLabelTest {

    @Test
    fun episodeWithProgressShowsResumeLabelBeforeSeasonEpisodeLabel() {
        val detail = ItemDetail(
            contentId = "episode-1",
            type = "episode",
            title = "The Thief",
            seriesTitle = "The Rookie",
            seasonNumber = 6,
            episodeNumber = 4,
            userData = LeafItemUserData(
                played = false,
                positionSeconds = 318.0,
            ),
        )

        assertEquals("Resume 5:18", computePlayLabel(detail))
    }

    @Test
    fun episodeWithoutProgressShowsSeasonEpisodeLabel() {
        val detail = ItemDetail(
            contentId = "episode-1",
            type = "episode",
            title = "The Thief",
            seriesTitle = "The Rookie",
            seasonNumber = 6,
            episodeNumber = 4,
        )

        assertEquals("Play S6·E4", computePlayLabel(detail))
    }
}
