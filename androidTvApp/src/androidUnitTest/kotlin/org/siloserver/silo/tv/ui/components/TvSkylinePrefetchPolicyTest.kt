package org.siloserver.silo.tv.ui.components

import org.siloserver.silo.model.section.SectionItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TvSkylinePrefetchPolicyTest {
    private val items = listOf("a", "b", "c", "d", "e").map(::item)

    @Test
    fun rapidFocusBeforeMarqueeSettlementStartsNoNeighborWork() {
        assertTrue(
            settledPrefetchItems(
                items = items,
                rawFocusedContentId = "d",
                settledContentId = "b",
            ).isEmpty(),
        )
    }

    @Test
    fun settledFocusReturnsOnlyTwoNeighborsPerSide() {
        assertEquals(
            listOf("a", "b", "d", "e"),
            settledPrefetchItems(
                items = items,
                rawFocusedContentId = "c",
                settledContentId = "c",
            ).map { it.contentId },
        )
    }

    @Test
    fun firstCardReturnsOnlyFollowingNeighbors() {
        assertEquals(
            listOf("b", "c"),
            settledPrefetchItems(
                items = items,
                rawFocusedContentId = "a",
                settledContentId = "a",
            ).map { it.contentId },
        )
    }

    @Test
    fun missingSettledIdentityStartsNoNeighborWork() {
        assertTrue(
            settledPrefetchItems(
                items = items,
                rawFocusedContentId = "missing",
                settledContentId = "missing",
            ).isEmpty(),
        )
    }

    @Test
    fun sameContentInDifferentRowsHasDifferentSettledIdentity() {
        assertEquals(
            TvSkylineSettledFocus(rowIndex = 0, contentId = "a"),
            settledFocusIdentity(
                rawRowIndex = 0,
                rawFocusedContentId = "a",
                settledContentId = "a",
            ),
        )
        assertEquals(
            TvSkylineSettledFocus(rowIndex = 1, contentId = "a"),
            settledFocusIdentity(
                rawRowIndex = 1,
                rawFocusedContentId = "a",
                settledContentId = "a",
            ),
        )
    }

    @Test
    fun unsettledFocusHasNoPrefetchIdentity() {
        assertEquals(
            null,
            settledFocusIdentity(
                rawRowIndex = 1,
                rawFocusedContentId = "b",
                settledContentId = "a",
            ),
        )
    }

    private fun item(id: String) = SectionItem(
        contentId = id,
        type = "movie",
        title = id,
    )
}
