package org.siloserver.silo.tv.ui.screens.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class TvSubtitleAspectModeWiringSourceTest {
    private fun source(path: String): String {
        val moduleRelative = File("src/androidMain/kotlin/$path")
        val projectRelative = File("androidTvApp/src/androidMain/kotlin/$path")
        return (moduleRelative.takeIf(File::exists) ?: projectRelative).readText()
    }

    @Test
    fun playerViewReconcilesSubtitlesAfterFillModeUpdate() {
        val source = source(
            "org/siloserver/silo/tv/ui/screens/player/TvPlayerScreen.kt"
        )
        val update = source.substringAfter("update = { view ->")
            .substringBefore("if (!isInPictureInPictureMode")

        val aspectCall = "applyPlayerViewVideoFillMode(view, state.videoFillMode)"
        val subtitleCall = "subtitleManager.syncSubtitleVideoBounds(view)"
        assertTrue(update.contains(aspectCall))
        assertTrue(update.contains(subtitleCall))
        assertTrue(update.indexOf(aspectCall) < update.indexOf(subtitleCall))
    }
}
