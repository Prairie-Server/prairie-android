package com.continuum.app.tv.ui.screens.player

import com.continuum.app.model.catalog.VersionChapter
import kotlin.test.Test
import kotlin.test.assertEquals

class TvPlayerHudTabsTest {

    @Test
    fun emptyStatsAndNoChaptersHideDataOnlyTabs() {
        val tabs = visibleHudTabs(
            stats = PlayerStatsSnapshot(),
            chapters = emptyList(),
        )

        assertEquals(
            listOf(HudTab.Info, HudTab.Video, HudTab.Audio),
            tabs,
        )
    }

    @Test
    fun statsTabShowsWhenStatsHaveRenderableData() {
        val tabs = visibleHudTabs(
            stats = PlayerStatsSnapshot(videoCodec = "video/avc"),
            chapters = emptyList(),
        )

        assertEquals(
            listOf(HudTab.Info, HudTab.Stats, HudTab.Video, HudTab.Audio),
            tabs,
        )
    }

    @Test
    fun chaptersTabShowsWhenSelectedVersionHasChapters() {
        val tabs = visibleHudTabs(
            stats = PlayerStatsSnapshot(),
            chapters = listOf(
                VersionChapter(index = 0, title = "Opening", startSeconds = 0.0),
            ),
        )

        assertEquals(
            listOf(HudTab.Info, HudTab.Video, HudTab.Audio, HudTab.Chapters),
            tabs,
        )
    }

    @Test
    fun statsAndChaptersShowInStableHudOrderWhenBothHaveData() {
        val tabs = visibleHudTabs(
            stats = PlayerStatsSnapshot(audioUnderruns = 1),
            chapters = listOf(
                VersionChapter(index = 0, title = "Opening", startSeconds = 0.0),
            ),
        )

        assertEquals(
            listOf(
                HudTab.Info,
                HudTab.Stats,
                HudTab.Video,
                HudTab.Audio,
                HudTab.Chapters,
            ),
            tabs,
        )
    }
}
