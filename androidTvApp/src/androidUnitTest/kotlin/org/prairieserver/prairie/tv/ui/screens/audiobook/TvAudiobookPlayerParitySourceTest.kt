package org.prairieserver.prairie.tv.ui.screens.audiobook

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvAudiobookPlayerParitySourceTest {
    private val playerSource = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/audiobook/TvAudiobookPlayerScreen.kt",
    ).readText()
    private val transportSource = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/audiobook/TvAudiobookTransportRow.kt",
    ).readText()

    @Test
    fun playerStartsWithChaptersClosedAndOnlyOpensFromChaptersButton() {
        assertTrue(
            playerSource.contains("var activePanel by remember { mutableStateOf(AudiobookPanel.None) }"),
            "Audiobook player must open with the chapter list closed.",
        )
        assertFalse(
            playerSource.contains("mutableStateOf(AudiobookPanel.Chapters)"),
            "Audiobook player must never default to the chapter panel.",
        )
        assertTrue(
            playerSource.contains("label = \"Chapters\"") &&
                playerSource.contains("onChapters = { activePanel = AudiobookPanel.Chapters }"),
            "Chapters should open only from the explicit Chapters secondary control.",
        )
    }

    @Test
    fun secondaryControlsStayQuietWithOverflowForLessCommonActions() {
        val controlsBlock = playerSource
            .substringAfter("TvAudiobookSecondaryControls(")
            .substringBefore("when (activePanel)")

        assertTrue(controlsBlock.contains("speedLabel = tvAudiobookSpeedLabel(state.playbackSpeed)"))
        assertTrue(controlsBlock.contains("sleepLabel = tvAudiobookSleepLabel("))
        assertTrue(controlsBlock.contains("showChapters = hasChapters"))
        assertTrue(controlsBlock.contains("onStop = {"))
        assertTrue(controlsBlock.contains("onMore = { activePanel = AudiobookPanel.More }"))
        assertFalse(
            controlsBlock.contains("onSkip =") ||
                controlsBlock.contains("onBookmarks =") ||
                controlsBlock.contains("onAbout ="),
            "Skip, bookmarks, and about are useful but too busy for the main player rail.",
        )
        assertTrue(playerSource.contains("AudiobookPanel.More -> TvAudiobookMorePanel("))
        assertTrue(playerSource.contains("onSkip = { activePanel = AudiobookPanel.Skip }"))
        assertTrue(playerSource.contains("onBookmarks = { activePanel = AudiobookPanel.Bookmarks }"))
        assertTrue(playerSource.contains("onAbout = { activePanel = AudiobookPanel.About }"))
    }

    @Test
    fun scrubberUsesRemainingTimeAndChapterTicksLikeTvOS() {
        assertTrue(
            playerSource.contains("remainingLabel = \"-\" + formatAudiobookTime("),
            "Right scrubber label should be remaining time, prefixed with '-'.",
        )
        assertTrue(
            playerSource.contains("chapterMarkers = state.chapters"),
            "Player scrubber should receive chapters for tick marks.",
        )
        assertTrue(
            playerSource.contains("private fun TvAudiobookChapterTicks("),
            "Player scrubber should draw chapter tick marks.",
        )
        assertTrue(
            playerSource.contains(".height(10.dp)"),
            "tvOS audiobook scrubber track is thicker than the old Android 6dp line.",
        )
    }

    @Test
    fun primaryTransportUsesResponsiveTvScale() {
        assertTrue(playerSource.contains("BoxWithConstraints"))
        assertTrue(playerSource.contains("tvAudiobookPlayerMetrics("))
        assertTrue(transportSource.contains("buttonSize: Dp"))
        assertTrue(transportSource.contains("primaryButtonWidth: Dp"))
        assertTrue(transportSource.contains("buttonSpacing: Dp"))
    }

    @Test
    fun shieldDpCanvasKeepsTransportAndUtilityRowsInView() {
        assertTrue(
            playerSource.contains("val metrics = tvAudiobookPlayerMetrics("),
            "Player must derive dimensions from the actual TV dp canvas.",
        )
        assertTrue(
            playerSource.contains("coverSizeDp = 218") &&
                playerSource.contains("horizontalPaddingDp = 44") &&
                playerSource.contains("transportButtonSizeDp = 68") &&
                playerSource.contains("utilityButtonHeightDp = 42"),
            "Shield-sized 960x540dp canvas needs compact metrics that fit all controls.",
        )
    }
}
