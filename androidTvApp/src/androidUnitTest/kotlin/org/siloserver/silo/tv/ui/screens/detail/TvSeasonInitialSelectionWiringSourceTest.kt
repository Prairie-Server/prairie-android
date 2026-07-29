package org.siloserver.silo.tv.ui.screens.detail

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class TvSeasonInitialSelectionWiringSourceTest {
    @Test
    fun tvSeasonLoadingUsesSharedInitialSelection() {
        val source = File(
            "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvItemDetailViewModel.kt",
        ).readText()

        assertTrue(
            source.contains(
                "val selectedSeason = seasons.initialSeasonForDisplay(preferredSeasonNumber)",
            ),
        )
    }
}
