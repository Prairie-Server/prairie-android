package org.prairieserver.prairie.android.ui.screens.detail

import org.prairieserver.prairie.model.catalog.ItemDetail
import org.prairieserver.prairie.model.catalog.LeafItemUserData
import kotlin.test.Test
import kotlin.test.assertEquals

class DetailPlayLabelTest {

    @Test
    fun episodeWithProgressShowsNeutralSeasonEpisodeLabel() {
        // iOS parity: the label stays neutral even with progress; the resume-vs-
        // restart choice is offered in the "Continue Watching?" dialog on tap.
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

        assertEquals("Play S6·E4", computePlayLabel(detail))
    }

    @Test
    fun playedEpisodeWithRewatchProgressShowsNeutralLabel() {
        val detail = ItemDetail(
            contentId = "episode-1",
            type = "episode",
            title = "The Thief",
            seriesTitle = "The Rookie",
            seasonNumber = 6,
            episodeNumber = 4,
            userData = LeafItemUserData(
                played = true,
                positionSeconds = 318.0,
                durationSeconds = 3600.0,
            ),
        )

        assertEquals("Play S6·E4", computePlayLabel(detail))
    }

    @Test
    fun episodeWithStaleCompletedPositionShowsSeasonEpisodeLabel() {
        val detail = ItemDetail(
            contentId = "episode-1",
            type = "episode",
            title = "The Thief",
            seriesTitle = "The Rookie",
            seasonNumber = 6,
            episodeNumber = 4,
            userData = LeafItemUserData(
                played = true,
                positionSeconds = 3600.0,
                durationSeconds = 3600.0,
            ),
        )

        assertEquals("Play S6·E4", computePlayLabel(detail))
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
