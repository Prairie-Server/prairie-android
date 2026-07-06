package org.siloserver.silo.android.ui.screens.detail

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class EpisodeSpoilerBlurSourceTest {
    private val root = "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/detail"

    @Test
    fun seriesDetailExposesSpoilerToggleForEpisodeStills() {
        val source = File("$root/SeriesDetailContent.kt").readText()

        assertTrue(
            source.contains("rememberSaveable(detail.contentId)") &&
                source.contains("hideUnwatchedEpisodeStills") &&
                source.contains("Icons.Outlined.VisibilityOff") &&
                source.contains("blurUnwatchedEpisodeStills = hideUnwatchedEpisodeStills"),
            "Series detail should expose a per-series spoiler toggle and pass it into EpisodeList.",
        )
    }

    @Test
    fun episodeListBlursAndVeilsUnwatchedStillsWithManualReveal() {
        val source = File("$root/EpisodeList.kt").readText()

        assertTrue(
            source.contains("blurUnwatchedEpisodeStills: Boolean") &&
                source.contains("rememberSaveable(episode.contentId)") &&
                source.contains("Build.VERSION.SDK_INT >= Build.VERSION_CODES.S") &&
                source.contains("Modifier.blur(12.dp)") &&
                source.contains("Spoiler hidden") &&
                source.contains("isStillRevealed = true"),
            "Episode stills should blur/veil unwatched artwork and support manual per-episode reveal.",
        )
    }
}
