package com.continuum.app.tv.ui.screens.people

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvPersonDetailSourceTest {
    private val source = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/people/TvPersonDetailScreen.kt",
    ).readText()

    private val mediaCardSource = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/components/TvMediaCard.kt",
    ).readText()

    private val gridSource = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/components/TvCatalogGrid.kt",
    ).readText()

    private val castSource = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvCastCrewSection.kt",
    ).readText()

    @Test
    fun usesSharedPresentationPaginationAndAudiobookArtworkRules() {
        assertTrue(source.contains("personMetadataBadges("))
        assertTrue(source.contains("personWorksCountLabel("))
        assertTrue(source.contains("availableFilters = state.availableFilters"))
        assertTrue(source.contains("onLoadMore = viewModel::loadMoreIfNeeded"))
        assertTrue(source.contains("hasMore = state.hasMore"))
        assertTrue(source.contains("modifier = Modifier.weight(1f).fillMaxWidth()"))
        assertTrue(source.contains("val width = 300.dp"))
        assertTrue(source.contains("fontSize = 72.sp"))
        assertTrue(source.contains("horizontalArrangement = Arrangement.spacedBy(48.dp)"))
        assertTrue(source.contains("maxLines = 7"))
        assertTrue(source.contains("artworkAspectRatioForItem = ::personWorkCardAspectRatio"))
        assertTrue(source.contains("private fun personWorkCardAspectRatio(item: BrowseItem): Float?"))
        assertTrue(source.contains("item.type == \"audiobook\""))
        assertTrue(mediaCardSource.contains("artworkAspectRatio: Float? = null"))
        assertTrue(mediaCardSource.contains("effectiveAspectRatio"))
        assertTrue(gridSource.contains("artworkAspectRatioForItem: (BrowseItem) -> Float? = { item ->"))
        assertTrue(gridSource.contains("artworkAspectRatio = artworkAspectRatioForItem(item)"))
        assertFalse(source.contains("header = {"))
        assertFalse(source.contains("TvPersonMediaFilter.entries.forEach"))
        assertFalse(source.contains("person.birthDate?.takeIf"))
        assertFalse(castSource.contains("no-op"))
        assertFalse(castSource.contains("person detail isn't wired"))
    }
}
