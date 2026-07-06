package org.siloserver.silo.android.ui.screens.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class MobileSubtitleAutoSelectionSourceTest {
    private val viewModelSource = File(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/player/PlayerViewModel.kt",
    ).readText()

    @Test
    fun mobilePlayerAppliesAutoSubtitleSelectionBeforeDefaultingOff() {
        val startupBody = viewModelSource
            .substringAfter("private suspend fun applyCoordinatorStateToUi(")
            .substringBefore("private fun startIntroAutoSkipObserver")

        assertTrue(
            startupBody.contains("resolveMobileAutoSubtitleSelection("),
            "Mobile startup must apply preferred-language subtitle auto-selection like TV",
        )
        assertTrue(
            startupBody.indexOf("resolveMobileAutoSubtitleSelection(") <
                startupBody.indexOf("val resolvedSubtitleIndex"),
            "Mobile auto-selection must be computed before selectedSubtitleIndex is finalized",
        )
        assertTrue(
            startupBody.contains("preferredLanguage = playbackState.preferredTextLanguage"),
            "Mobile auto-selection must use the server/profile preferred subtitle language",
        )
        assertTrue(
            startupBody.contains("subtitleMode = playbackState.preferredSubtitleMode"),
            "Mobile auto-selection must honor the server/profile subtitle mode",
        )
        assertTrue(
            startupBody.contains("showForcedSubtitles = playbackState.showForcedSubtitles"),
            "Mobile auto-selection must honor the server/profile forced-subtitle setting",
        )
    }
}
