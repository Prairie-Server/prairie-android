package org.siloserver.silo.android.ui.screens.detail

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class SeasonInitialSelectionWiringSourceTest {
    @Test
    fun phoneSeasonLoadingUsesSharedInitialSelection() {
        val source = File(
            "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/detail/ItemDetailViewModel.kt",
        ).readText()

        assertTrue(
            source.contains(
                "val selectedSeason = seasons.initialSeasonForDisplay(initialSeasonNumber)",
            ),
        )
    }
}
