package org.prairieserver.prairie.tv.ui.screens.home

import org.prairieserver.prairie.model.section.ResolvedSection
import org.prairieserver.prairie.model.section.SectionItem
import org.prairieserver.prairie.tv.ui.components.TvRowStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TvHomeSectionsTest {
    @Test
    fun mixedContinueRowsSplitAudiobooksIntoContinueListening() {
        val sections = listOf(
            ResolvedSection(
                id = "continue",
                sectionType = "continue_watching",
                title = "Continue Watching",
                items = listOf(
                    SectionItem(contentId = "m1", type = "movie", title = "Movie"),
                    SectionItem(contentId = "a1", type = "audiobook", title = "Audio"),
                    SectionItem(contentId = "e1", type = "ebook", title = "Book"),
                ),
            ),
        )

        val normalized = sections.normalizeTvHomeSections()

        assertEquals(listOf("Continue Watching", "Continue Listening"), normalized.map { it.title })
        assertEquals(listOf("m1"), normalized[0].items.map { it.contentId })
        assertEquals(listOf("a1"), normalized[1].items.map { it.contentId })
        assertTrue(normalized[1].isTvAudioProgressSection())
        assertEquals(TvRowStyle.Poster, normalized[1].tvHomeRowStyle())
    }

    @Test
    fun audiobookOnlyContinueRowIsRetitledAndKeptAsProgress() {
        val sections = listOf(
            ResolvedSection(
                id = "continue",
                sectionType = "continue_watching",
                title = "Continue Watching",
                items = listOf(
                    SectionItem(contentId = "a1", type = "audiobook", title = "Audio"),
                ),
            ),
        )

        val normalized = sections.normalizeTvHomeSections()

        assertEquals(listOf("Continue Listening"), normalized.map { it.title })
        assertEquals("continue_listening", normalized.single().sectionType)
        assertTrue(normalized.single().isTvAudioProgressSection())
        assertEquals(TvRowStyle.Poster, normalized.single().tvHomeRowStyle())
    }
}
