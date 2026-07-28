package org.siloserver.silo.tv.ui.screens.library

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class TvLibraryReviewWiringSourceTest {
    private val detailScreen = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/library/TvLibraryDetailScreen.kt",
    ).readText()
    private val mainShell = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvMainShell.kt",
    ).readText()

    @Test
    fun alphabetAndCalendarForwardContentUpFallbackToTheShell() {
        val alphabetTab = detailScreen
            .substringAfter("TvLibraryTab.Alphabet -> LibraryTab(")
            .substringBefore("TvLibraryTab.RecentlyAdded ->")
        val calendarScreen = mainShell
            .substringAfter("TvCalendarScreen(")
            .substringBefore("composable(TvMainRoute.Search.route)")

        assertTrue(alphabetTab.contains("onContentUpFallbackChanged = onContentUpFallbackChanged"))
        assertTrue(calendarScreen.contains("onContentUpFallbackChanged = onContentUpFallback"))
    }
}
