package com.continuum.app.android.ui.performance

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class MobileListPerformanceSourceTest {
    private val mediaRow = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/components/MediaRow.kt",
    ).readText()

    private val catalogGrid = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/browse/CatalogGrid.kt",
    ).readText()

    private val homeScreen = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/home/HomeScreen.kt",
    ).readText()

    private val readingHub = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/reading/ReadingHubScreen.kt",
    ).readText()

    private val librariesScreen = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/libraries/LibrariesScreen.kt",
    ).readText()

    private val personalGrid = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/personal/PersonalMediaGridContent.kt",
    ).readText()

    private val cardActionsHelpers = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/components/CardActionsHelpers.kt",
    ).readText()

    private val searchResults = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/search/SearchResults.kt",
    ).readText()

    private val requestsScreen = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/requests/RequestsScreen.kt",
    ).readText()

    private val downloadsScreen = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadsScreen.kt",
    ).readText()

    private val downloadEntryRows = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadEntryRows.kt",
    ).readText()

    private val audiobookDetail = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookDetailContent.kt",
    ).readText()

    private val audioTrackSelector = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/AudioTrackSelector.kt",
    ).readText()

    private val qualitySelector = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/QualitySelector.kt",
    ).readText()

    private val chaptersSheet = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/ChaptersSheet.kt",
    ).readText()

    @Test
    fun hotHomeRowsUseStableItemModelsAndContentTypes() {
        assertTrue(
            mediaRow.contains("remember(items, showProgress, cardStyle)") &&
                mediaRow.contains("items = rowItems") &&
                mediaRow.contains("contentType = { rowItem -> rowItem.contentType }"),
            "Home/media rails should precompute per-card display state and provide LazyRow content types.",
        )
    }

    @Test
    fun catalogGridProvidesContentTypesForCardReuse() {
        assertTrue(
            catalogGrid.contains("contentType = { item -> item.type }"),
            "Catalog grids should give Compose stable content types for card reuse.",
        )
    }

    @Test
    fun homeSectionListProvidesSectionContentType() {
        assertTrue(
            homeScreen.contains("contentType = { \"section-row\" }"),
            "Home's vertical section list should expose a content type for better item reuse.",
        )
    }

    @Test
    fun homeChromeDoesNotAnimateEveryScrollOffsetTick() {
        assertTrue(
            !homeScreen.contains("animateFloatAsState") &&
                homeScreen.contains("0.32f * scrollProgress"),
            "Home chrome opacity should be directly scroll-linked, not a new animation target on each scroll tick.",
        )
    }

    @Test
    fun readingAndLibraryListsExposeContentTypes() {
        assertTrue(
            readingHub.contains("contentType = { \"reading-section-row\" }") &&
                readingHub.contains("contentType = { \"reading-collection\" }") &&
                librariesScreen.contains("contentType = { \"library-collection\" }"),
            "Reading/library lazy containers should expose stable content types.",
        )
    }

    @Test
    fun personalGridProvidesContentTypesForCardReuse() {
        assertTrue(
            personalGrid.contains("contentType = { item -> item.type }"),
            "Personal media grids should give Compose stable content types for card reuse.",
        )
    }

    @Test
    fun cardActionObjectsAreRememberedPerContentId() {
        assertTrue(
            cardActionsHelpers.contains("val actions = remember(item.contentId, coordinator, scope)"),
            "Card action objects should not be reallocated on every card recomposition.",
        )
    }

    @Test
    fun secondaryHotListsExposeContentTypes() {
        assertTrue(
            searchResults.contains("contentType = { item -> item.type }") &&
                requestsScreen.contains("contentType = { \"request-section\" }") &&
                requestsScreen.contains("contentType = { item -> item.mediaType }") &&
                downloadsScreen.contains("contentType = \"downloads-top-padding\"") &&
                downloadsScreen.contains("contentType = \"downloads-total\"") &&
                downloadEntryRows.contains("contentType = \"download-section-header\"") &&
                downloadEntryRows.contains("contentType = \"download-entry\"") &&
                audiobookDetail.contains("contentType = \"audiobook-hero\"") &&
                audiobookDetail.contains("contentType = { \"audiobook-chapter\" }") &&
                audioTrackSelector.contains("contentType = { _, _ -> \"audio-track\" }") &&
                qualitySelector.contains("contentType = { _, _ -> \"quality-version\" }") &&
                chaptersSheet.contains("contentType = { _, _ -> \"chapter-row\" }"),
            "Search, requests, downloads, audiobook detail, and player pickers should expose stable lazy content types.",
        )
    }
}
