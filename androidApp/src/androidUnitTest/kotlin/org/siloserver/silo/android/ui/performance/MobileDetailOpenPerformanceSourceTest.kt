package org.siloserver.silo.android.ui.performance

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class MobileDetailOpenPerformanceSourceTest {
    private val viewModel = File(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/detail/ItemDetailViewModel.kt",
    ).readText()

    private val movieDetail = File(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/detail/MovieDetailContent.kt",
    ).readText()

    private val seriesDetail = File(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/detail/SeriesDetailContent.kt",
    ).readText()

    private val detailShared = File(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/detail/DetailSharedComponents.kt",
    ).readText()

    private val castCrew = File(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/detail/CastCrewSection.kt",
    ).readText()

    private val similarRail = File(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/detail/SimilarRail.kt",
    ).readText()

    @Test
    fun itemDetailSeedsCachedDetailBeforeNetworkRefresh() {
        assertTrue(
            viewModel.indexOf("seedCachedDetail()") in
                0 until viewModel.indexOf("catalogRepository.getItemDetail(contentId)"),
            "Opening an item should seed cached detail before waiting on the network refresh.",
        )
    }

    @Test
    fun detailScreensExposeStableContentTypesForOpenAndScroll() {
        assertTrue(
            movieDetail.contains("contentType = \"detail-hero\"") &&
                movieDetail.contains("contentType = \"detail-cast\"") &&
                movieDetail.contains("contentType = \"detail-similar\"") &&
                seriesDetail.contains("contentType = \"detail-hero\"") &&
                seriesDetail.contains("contentType = \"detail-episodes\"") &&
                seriesDetail.contains("contentType = \"detail-similar\"") &&
                detailShared.contains("contentType = { \"season-chip\" }") &&
                castCrew.contains("contentType = { \"cast-member\" }") &&
                similarRail.contains("contentType = { \"similar-item\" }"),
            "Opening an item should keep detail sections and rails composition-reusable.",
        )
    }

    @Test
    fun seriesEpisodeRollupDoesNotCompeteWithInitialOpen() {
        assertTrue(
            viewModel.contains("private var episodeLoadJob") &&
                viewModel.contains("episodeLoadJob?.cancel()") &&
                viewModel.contains("private var allEpisodeFileIdsJob") &&
                viewModel.contains("delay(350)") &&
                viewModel.contains("skipSeasonNumber"),
            "Series open should cancel stale season loads, render the selected season first, and defer the full-series episode roll-up.",
        )
    }
}
