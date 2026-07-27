package org.siloserver.silo.android.ui.screens.watchtogether

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class SuggestToRoomSurfaceSourceTest {
    private val entrySheet = File(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/watchtogether/WatchTogetherEntrySheet.kt",
    ).readText()
    private val itemDetail = File(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/detail/ItemDetailScreen.kt",
    ).readText()
    private val movieDetail = File(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/detail/MovieDetailContent.kt",
    ).readText()
    private val seriesDetail = File(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/detail/SeriesDetailContent.kt",
    ).readText()

    @Test
    fun activeRoomExposesSuggestionActionForMoviesAndSeries() {
        assertTrue(itemDetail.contains("SuggestToRoomViewModel"))
        assertTrue(itemDetail.contains("suggestViewModel.suggest("))
        assertTrue(movieDetail.contains("Suggest to Watch Together"))
        assertTrue(seriesDetail.contains("Suggest to Watch Together"))
    }

    @Test
    fun seriesSuggestionUsesItsPlayableEpisodeIdentity() {
        assertTrue(itemDetail.contains("contentType = \"episode\""))
        assertTrue(itemDetail.contains("nextEpisode.stillUrl ?: detail.posterUrl"))
    }

    @Test
    fun inFlightRoomMutationCannotBeDismissed() {
        assertTrue(entrySheet.contains("canDismissRoomEntry(state.busy)"))
        assertTrue(entrySheet.contains("confirmValueChange"))
    }
}
