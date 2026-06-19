package com.continuum.app.tv.ui.util

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class TvMediaTypeFiltersSourceTest {
    private val filtersSource = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/util/TvMediaTypeFilters.kt",
    ).readText()

    private val mediaCardSource = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/components/TvMediaCard.kt",
    ).readText()

    private val mediaRowSource = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/components/TvMediaRow.kt",
    ).readText()

    private val catalogGridSource = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/components/TvCatalogGrid.kt",
    ).readText()

    private val libraryDetailSource = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/library/TvLibraryDetailScreen.kt",
    ).readText()

    @Test
    fun audiobookArtworkAspectRatioIsCentralizedAcrossTvCards() {
        assertTrue(
            filtersSource.contains("fun tvArtworkAspectRatioForMediaType(type: String?): Float?"),
            "TV should expose a single media-type artwork aspect-ratio helper.",
        )
        assertTrue(
            filtersSource.contains("if (isAudiobookMediaType(type)) 1f else null"),
            "Audiobooks should map to square artwork and other media should keep the default poster shape.",
        )
        assertTrue(
            mediaCardSource.contains("mediaType: String? = null") &&
                mediaCardSource.contains("tvArtworkAspectRatioForMediaType(mediaType)"),
            "TvMediaCard should apply the centralized audiobook square rule itself.",
        )
        assertTrue(
            mediaRowSource.contains("mediaType = item.type") &&
                catalogGridSource.contains("artworkAspectRatioForItem: (BrowseItem) -> Float? = { item ->") &&
                catalogGridSource.contains("tvArtworkAspectRatioForMediaType(item.type)") &&
                libraryDetailSource.contains("mediaType = item.type"),
            "Rows, grids, and direct library cards should pass the item type into the shared card.",
        )
    }
}
