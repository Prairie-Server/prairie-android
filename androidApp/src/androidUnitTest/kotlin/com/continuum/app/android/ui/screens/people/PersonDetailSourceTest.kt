package com.continuum.app.android.ui.screens.people

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersonDetailSourceTest {
    private val source = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/people/PersonDetailScreen.kt",
    ).readText()

    private val mediaCardSource = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/components/MediaCard.kt",
    ).readText()

    @Test
    fun usesSharedPresentationPagingAndAudiobookArtworkRules() {
        assertTrue(source.contains("personMetadataBadges("))
        assertTrue(source.contains("personInitials("))
        assertTrue(source.contains("personWorksCountLabel("))
        assertTrue(source.contains("availableFilters = state.availableFilters"))
        assertTrue(source.contains("onLoadMore = viewModel::loadMoreIfNeeded"))
        assertTrue(source.contains("ExternalProfileSection("))
        assertTrue(source.contains("PagingFooter("))
        assertTrue(source.contains("personWorkCardAspectRatio(item)"))
        assertTrue(source.contains("item.type == \"audiobook\""))
        assertTrue(mediaCardSource.contains("artworkAspectRatio: Float = 2f / 3.3f"))
        assertTrue(mediaCardSource.contains(".aspectRatio(artworkAspectRatio)"))
        assertFalse(source.contains("person.birthDate?.takeIf"))
        assertFalse(source.contains("private fun personInitials"))
    }
}
