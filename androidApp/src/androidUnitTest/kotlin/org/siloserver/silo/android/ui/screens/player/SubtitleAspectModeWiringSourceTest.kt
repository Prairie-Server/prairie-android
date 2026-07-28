package org.siloserver.silo.android.ui.screens.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class SubtitleAspectModeWiringSourceTest {
    private fun source(path: String): String {
        val moduleRelative = File("src/androidMain/kotlin/$path")
        val projectRelative = File("androidApp/src/androidMain/kotlin/$path")
        return (moduleRelative.takeIf(File::exists) ?: projectRelative).readText()
    }

    @Test
    fun playerViewReconcilesSubtitlesAfterResizeModeUpdate() {
        val source = source(
            "org/siloserver/silo/android/ui/screens/player/PlayerScreen.kt"
        )
        val update = source.substringAfter("update = { view ->")
            .substringBefore("modifier = Modifier")

        assertTrue(update.contains("view.resizeMode = resizeMode"))
        assertTrue(update.contains("subtitleManager.syncSubtitleVideoBounds(view)"))
        assertTrue(
            update.indexOf("view.resizeMode = resizeMode") <
                update.indexOf("subtitleManager.syncSubtitleVideoBounds(view)")
        )
    }
}
