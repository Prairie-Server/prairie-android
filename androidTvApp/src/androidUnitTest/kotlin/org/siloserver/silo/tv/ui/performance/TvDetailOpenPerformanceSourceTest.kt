package org.siloserver.silo.tv.ui.performance

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class TvDetailOpenPerformanceSourceTest {
    private val viewModel = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvItemDetailViewModel.kt",
    ).readText()

    private val screen = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvItemDetailScreen.kt",
    ).readText()

    private val episodeRail = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvDetailEpisodeRail.kt",
    ).readText()

    private val seasonPicker = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvSeasonPicker.kt",
    ).readText()

    private val castCrew = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvCastCrewSection.kt",
    ).readText()

    @Test
    fun itemDetailSeedsCachedDetailBeforeNetworkRefresh() {
        assertTrue(
            viewModel.indexOf("catalogRepository.getCachedItemDetail(contentId)") in
                0 until viewModel.indexOf("catalogRepository.getItemDetail(contentId)"),
            "Opening a TV item should seed cached detail before waiting on the network refresh.",
        )
    }

    @Test
    fun detailScreenRendersCachedDetailWhileRefreshing() {
        assertTrue(
            screen.contains("state.isLoading && state.detail == null"),
            "TV detail should render cached content while a background refresh is still loading.",
        )
    }

    @Test
    fun detailScreenAndRailsExposeStableContentTypes() {
        assertTrue(
            screen.contains("item(key = \"hero\", contentType = \"detail-hero\")") &&
                screen.contains("item(key = \"body\", contentType = \"detail-body\")") &&
                episodeRail.contains("contentType = { \"episode-card\" }") &&
                seasonPicker.contains("contentType = { \"season-chip\" }") &&
                castCrew.contains("contentType = { _, _ -> \"cast-member\" }"),
            "TV detail sections and rails should expose stable content types while opening and navigating.",
        )
    }

    @Test
    fun relatedShelfFetchIsDeferredBehindPrimaryDetailWork() {
        assertTrue(
            viewModel.contains("private var moreLikeThisJob") &&
                viewModel.contains("delay(300)"),
            "TV detail should let primary detail, seasons, and episodes settle before fetching the related shelf.",
        )
    }
}
