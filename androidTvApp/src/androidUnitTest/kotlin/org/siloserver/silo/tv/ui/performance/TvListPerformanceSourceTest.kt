package org.siloserver.silo.tv.ui.performance

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class TvListPerformanceSourceTest {
    private val mediaRow = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/components/TvMediaRow.kt",
    ).readText()

    private val catalogGrid = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/components/TvCatalogGrid.kt",
    ).readText()

    private val homeScreen = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/home/TvHomeScreen.kt",
    ).readText()

    private val skylineSectionFeed = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/components/TvSkylineSectionFeed.kt",
    ).readText()

    private val libraryDetail = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/library/TvLibraryDetailScreen.kt",
    ).readText()

    private val recommendations = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/recommendations/TvRecommendationsScreen.kt",
    ).readText()

    private val cardActionsHelpers = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/components/TvCardActionsHelpers.kt",
    ).readText()

    private val optionDialog = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/components/TvOptionDialog.kt",
    ).readText()

    private val playerHud = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/player/TvPlayerHud.kt",
    ).readText()

    private val searchScreen = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/search/TvSearchScreen.kt",
    ).readText()

    private val heroCarousel = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/components/TvHomeHeroCarousel.kt",
    ).readText()

    private val requestsScreen = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/requests/TvRequestsScreen.kt",
    ).readText()

    @Test
    fun hotTvRowsUseStableItemModelsAndContentTypes() {
        assertTrue(
            mediaRow.contains("remember(items, showProgress, style, cardLayout)") &&
                mediaRow.contains("items = rowItems") &&
                mediaRow.contains("contentType = { _, rowItem -> rowItem.contentType }"),
            "TV rails should precompute per-card display state and provide LazyRow content types.",
        )
    }

    @Test
    fun tvCatalogGridProvidesContentTypesForCardReuse() {
        assertTrue(
            catalogGrid.contains("contentType = { _, item -> item.type }"),
            "TV catalog grids should give Compose stable content types for card reuse.",
        )
    }

    @Test
    fun tvHomeSectionListProvidesSectionContentType() {
        assertTrue(
            skylineSectionFeed.contains("contentType = { _, _ -> \"skyline-section-row\" }"),
            "TV skyline section lists should expose a content type for better item reuse.",
        )
    }

    @Test
    fun tvLibraryBrowseGridProvidesContentTypesForCardReuse() {
        assertTrue(
            libraryDetail.contains("contentType = { _, item -> item.type }"),
            "TV library browse grids should expose item content types in the hot browse path.",
        )
    }

    @Test
    fun tvCollectionAndRecommendationListsExposeContentTypes() {
        assertTrue(
            libraryDetail.contains("contentType = { _, collection -> \"collection\" }") &&
                recommendations.contains("contentType = { \"recommendation-section-row\" }"),
            "TV collections and recommendations should expose stable content types.",
        )
    }

    @Test
    fun recommendationsReserveTheFixedFilterBand() {
        assertTrue(recommendations.contains("RecommendationsFilterBandHeight"))
        assertTrue(
            recommendations.contains(
                "top = TvTopMenuLayout.contentTopInset + RecommendationsFilterBandHeight",
            ),
        )
    }

    @Test
    fun tvCardActionObjectsAreRememberedPerContentId() {
        assertTrue(
            cardActionsHelpers.contains("val actions = remember(item.contentId, coordinator, scope)"),
            "TV card action objects should not be reallocated on every card recomposition.",
        )
    }

    @Test
    fun secondaryHotTvListsExposeContentTypes() {
        assertTrue(
                optionDialog.contains("contentType = { \"dialog-option\" }") &&
                playerHud.contains("contentType = { _, _ -> \"hud-chapter\" }") &&
                // The small picker deliberately fully composes its options so
                // every below-fold D-pad target remains in the focus graph;
                // contentType applies only to lazy list items.
                playerHud.contains("options.forEachIndexed") &&
                searchScreen.contains("contentType = { _, _ -> \"media-type-chip\" }") &&
                heroCarousel.contains("contentType = { _, _ -> \"hero-card\" }") &&
                requestsScreen.contains("contentType = \"request-notice\"") &&
                requestsScreen.contains("contentType = \"request-search-results\"") &&
                requestsScreen.contains("contentType = { \"request-section\" }") &&
                requestsScreen.contains("contentType = { _, _ -> \"request-result\" }"),
            "TV dialogs, player lists, search chips, hero carousel, and requests rows should expose stable content types.",
        )
    }
}
