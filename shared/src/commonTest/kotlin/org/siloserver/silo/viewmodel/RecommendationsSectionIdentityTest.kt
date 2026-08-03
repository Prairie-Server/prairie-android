package org.siloserver.silo.viewmodel

import org.siloserver.silo.model.recommendation.DiscoverRow
import org.siloserver.silo.model.section.SectionItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RecommendationsSectionIdentityTest {
    @Test
    fun productionSectionIdsSurviveRowInsertionAndReorder() {
        val returnedFrom = row(
            type = "cluster",
            label = "Because you enjoy Drama",
            sectionKind = "cluster",
            sectionKey = "2",
            contentId = "movie-b",
        )
        val popular = row(
            type = "popular",
            label = "Popular on This Server",
            sectionKind = "popular",
            contentId = "movie-popular",
        )

        val beforeRefresh = listOf(returnedFrom, popular).toResolvedSections()
        val afterRefresh = listOf(
            row(
                type = "cluster",
                label = "Because you enjoy Comedy",
                sectionKind = "cluster",
                sectionKey = "7",
                contentId = "movie-new",
            ),
            popular,
            returnedFrom,
        ).toResolvedSections()

        val originalId = beforeRefresh.single { section ->
            section.items.any { it.contentId == "movie-b" }
        }.id
        val refreshedId = afterRefresh.single { section ->
            section.items.any { it.contentId == "movie-b" }
        }.id
        val insertedId = afterRefresh.single { section ->
            section.items.any { it.contentId == "movie-new" }
        }.id

        assertEquals(originalId, refreshedId)
        assertNotEquals(insertedId, refreshedId)
    }

    private fun row(
        type: String,
        label: String,
        sectionKind: String,
        sectionKey: String? = null,
        contentId: String,
    ) = DiscoverRow(
        type = type,
        label = label,
        sectionKind = sectionKind,
        sectionKey = sectionKey,
        items = listOf(
            SectionItem(
                contentId = contentId,
                type = "movie",
                title = contentId,
            ),
        ),
    )
}
