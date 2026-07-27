package org.prairieserver.prairie.model.audiobook

import org.prairieserver.prairie.model.ebook.MediaPerson
import org.prairieserver.prairie.model.ebook.MediaRelatedContent
import org.prairieserver.prairie.model.ebook.MediaRelatedItem
import org.prairieserver.prairie.model.ebook.MediaSeriesGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AudiobookMetadataTest {
    @Test
    fun narrationAndMetadataExposeDisplayNamesAndRelatedContent() {
        val narration = AudiobookNarration(
            contentId = "book-2",
            title = "Alternate Narration",
            year = 2026,
            narrators = listOf("Narrator C"),
        )
        val metadata = AudiobookMetadata(
            authors = listOf(MediaPerson(name = "Author A"), MediaPerson(name = " ")),
            narrators = listOf(MediaPerson(name = "Narrator A"), MediaPerson(name = "Narrator B")),
            publisher = "Prairie Audio",
            totalDurationSeconds = 3_600,
            series = MediaSeriesGroup(
                name = "Series",
                entries = listOf(MediaRelatedItem(contentId = "book-1", title = "Book 1", seriesIndex = 1.0)),
            ),
            otherNarrations = listOf(narration),
            related = MediaRelatedContent(
                alsoByAuthor = listOf(MediaRelatedItem(contentId = "book-3", title = "Related")),
                similar = listOf(MediaRelatedItem(contentId = "book-4", title = "Similar")),
            ),
        )

        assertEquals("Author A", metadata.authorNames)
        assertEquals("Narrator A, Narrator B", metadata.narratorNames)
        assertEquals("Alternate Narration", metadata.otherNarrations.single().title)
        assertEquals("Series", metadata.series?.name)
        assertEquals("Related", metadata.related.alsoByAuthor.single().title)
    }

    @Test
    fun blankAuthorAndNarratorNamesReturnNull() {
        val metadata = AudiobookMetadata(
            authors = listOf(MediaPerson(name = " ")),
            narrators = emptyList(),
        )

        assertNull(metadata.authorNames)
        assertNull(metadata.narratorNames)
    }
}
