package org.prairieserver.prairie.tv.ui.screens.library

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvLibraryDetailSourceTest {
    private val source = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/library/TvLibraryDetailScreen.kt",
    ).readText()

    @Test
    fun tvLibraryTabsDoNotDuplicateTopBarLibraryChrome() {
        assertTrue(source.contains("top-bar cascade owns library switching"))
        assertTrue(source.contains("TvSkylineSectionFeed("))
        assertTrue(source.contains("!it.featured && it.items.isNotEmpty()"))
        assertFalse(source.contains("LibrarySwitcherPill("))
        assertFalse(source.contains("private fun LibraryHeader("))
        assertFalse(source.contains("private fun TabSlider("))
        assertFalse(source.contains("TvHomeHeroCarousel("))
        assertFalse(source.contains("splitFeatured("))
        assertFalse(source.contains("item(key = \"header\")"))
    }
}
