package com.continuum.app.android.ui.shared

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MobileSharedFollowupsSourceTest {
    private val root = "src/androidMain/kotlin/com/continuum/app/android"

    @Test
    fun watchedBadgeHasOneSharedImplementation() {
        val mediaCard = File("$root/ui/components/MediaCard.kt").readText()
        val watchedBadge = File("$root/ui/components/WatchedBadge.kt").readText()

        assertTrue(
            watchedBadge.contains(".size(20.dp)") &&
                watchedBadge.contains("Modifier.size(10.dp)") &&
                watchedBadge.contains("MaterialTheme.colorScheme.onSurface") &&
                watchedBadge.contains("MaterialTheme.colorScheme.background"),
            "The shared watched badge should keep the iOS white 20dp circle with a 10dp dark check.",
        )
        assertTrue(
            mediaCard.contains("WatchedBadge("),
            "MediaCard should use the shared watched badge instead of duplicating badge drawing.",
        )
        assertFalse(
            mediaCard.contains("Icons.Default.Check"),
            "The watched checkmark icon should be owned by WatchedBadge only.",
        )
    }

    @Test
    fun posterGridMathUsesSharedIosDefaults() {
        val mediaCard = File("$root/ui/components/MediaCard.kt").readText()
        val catalogGrid = File("$root/ui/screens/browse/CatalogGrid.kt").readText()
        val searchResults = File("$root/ui/screens/search/SearchResults.kt").readText()
        val personalGrid = File("$root/ui/screens/personal/PersonalMediaGridContent.kt").readText()
        val collectionDetail = File("$root/ui/screens/collections/CollectionDetailScreen.kt").readText()
        val libraryCollections = File("$root/ui/screens/collections/LibraryCollectionsScreen.kt").readText()
        val libraries = File("$root/ui/screens/libraries/LibrariesScreen.kt").readText()

        assertTrue(
            mediaCard.contains("object MediaGridDefaults") &&
                mediaCard.contains("val PosterGridMinWidth = 110.dp") &&
                mediaCard.contains("val PosterGridHorizontalSpacing = 12.dp") &&
                mediaCard.contains("val PosterGridVerticalSpacing = 16.dp"),
            "Shared poster-grid defaults should live beside MediaCard so mobile grids stay aligned to iOS.",
        )

        listOf(
            catalogGrid,
            searchResults,
            personalGrid,
            collectionDetail,
            libraryCollections,
            libraries,
        ).forEach { source ->
            assertTrue(
                source.contains("GridCells.Adaptive(MediaGridDefaults.PosterGridMinWidth)") &&
                    source.contains("Arrangement.spacedBy(MediaGridDefaults.PosterGridHorizontalSpacing)") &&
                    source.contains("Arrangement.spacedBy(MediaGridDefaults.PosterGridVerticalSpacing)"),
                "Mobile poster grids should use shared adaptive 110dp / 12dp / 16dp iOS grid math.",
            )
            assertFalse(
                source.contains("GridCells.Fixed(3)"),
                "Mobile poster grids should adapt from a 110dp minimum instead of freezing at three columns.",
            )
        }

        assertTrue(
            catalogGrid.contains("GridItemSpan(maxLineSpan)") &&
                searchResults.contains("GridItemSpan(maxLineSpan)"),
            "Full-width loading/count rows should span adaptive grids with maxLineSpan.",
        )
    }
}
