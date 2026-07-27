package org.prairieserver.prairie.android.ui.screens.detail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.prairieserver.prairie.model.catalog.EpisodeListItem
import org.prairieserver.prairie.model.catalog.EpisodeFile
import org.prairieserver.prairie.model.catalog.Season

class EpisodeRollupAccumulatorTest {
    @Test
    fun resumedRollupSkipsSeasonsThatAlreadyCompleted() {
        val seasons = (1..3).map { number ->
            Season(contentId = "season-$number", seasonNumber = number)
        }
        val accumulator = EpisodeRollupAccumulator("series-1", seasons.map { it.seasonNumber }.toSet())

        accumulator.recordSeason(1, listOf(episode("s1e1", 101)))
        accumulator.recordSeason(2, listOf(episode("s2e1", 201)))

        assertEquals(listOf(3), accumulator.remainingSeasons(seasons).map { it.seasonNumber })
        assertEquals(listOf(101, 201), accumulator.fileIds)
        assertFalse(accumulator.isComplete)

        accumulator.recordSeason(3, listOf(episode("s3e1", 301)))

        assertTrue(accumulator.isComplete)
        assertEquals(listOf(101, 201, 301), accumulator.fileIds)
    }

    private fun episode(contentId: String, fileId: Int): EpisodeListItem =
        EpisodeListItem(
            contentId = contentId,
            seasonNumber = 1,
            episodeNumber = 1,
            files = listOf(EpisodeFile(fileId = fileId)),
        )
}
