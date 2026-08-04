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

    /**
     * `discoverRowSectionKey` returns an empty kind for row types it does not
     * recognise, and both identity fields are `omitempty`, so unrecognised rows
     * — and every row from a server predating `section_kind` — fall back to
     * type+label. Section IDs key a LazyColumn, where a duplicate is a crash,
     * so collisions must resolve rather than propagate.
     */
    @Test
    fun collidingSectionIdentitiesStayUniqueForLazyListKeys() {
        val keyless = { contentId: String ->
            DiscoverRow(
                type = "server_row_this_client_does_not_know",
                label = "Handpicked",
                items = listOf(SectionItem(contentId = contentId, type = "movie", title = contentId)),
            )
        }

        val ids = listOf(keyless("movie-a"), keyless("movie-b"), keyless("movie-c"))
            .toResolvedSections()
            .map { it.id }

        assertEquals(3, ids.size)
        assertEquals(ids.size, ids.toSet().size, "section ids must be unique: $ids")
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
