package org.siloserver.silo.android.ui.screens.search

import org.siloserver.silo.model.catalog.BrowseItem
import kotlin.test.Test
import kotlin.test.assertEquals

class MobileSearchMediaTypeFilterTest {
    @Test
    fun readingFilterMatchesSharedReadingTaxonomy() {
        val items = listOf(
            browseItem("a1", "audiobook"),
            browseItem("a2", "audiobooks"),
            browseItem("e1", "ebook"),
            browseItem("e2", "ebooks"),
            browseItem("b1", "book"),
            browseItem("b2", "books"),
            browseItem("c1", "comic"),
            browseItem("c2", "comics"),
            browseItem("manga1", "manga"),
            browseItem("r1", "reading"),
            browseItem("m1", "movie"),
            browseItem("s1", "series"),
            browseItem("mu1", "music"),
        )

        assertEquals(
            listOf("a1", "a2", "e1", "e2", "b1", "b2", "c1", "c2", "manga1", "r1"),
            MobileSearchMediaType.Reading.filterResults(items).map { it.contentId },
        )
    }

    @Test
    fun nonReadingFiltersPassItemsThrough() {
        val items = listOf(browseItem("m1", "movie"), browseItem("e1", "ebook"))

        assertEquals(items, MobileSearchMediaType.All.filterResults(items))
        assertEquals(items, MobileSearchMediaType.Video.filterResults(items))
        assertEquals(items, MobileSearchMediaType.Audio.filterResults(items))
    }

    private fun browseItem(contentId: String, type: String): BrowseItem =
        BrowseItem(contentId = contentId, type = type, title = contentId)
}
