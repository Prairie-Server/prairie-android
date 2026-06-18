package com.continuum.app.tv.ui.screens.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvPlayerControlsUsabilityTest {
    private val screenSource = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt",
    ).readText()
    private val clusterSource = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerTransportCluster.kt",
    ).readText()
    private val hudSource = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt",
    ).readText()
    private val viewModelSource = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerViewModel.kt",
    ).readText()
    private val activitySource = File(
        "src/androidMain/kotlin/com/continuum/app/tv/MainTvActivity.kt",
    ).readText()
    private val remoteKeyBridgeSource = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerRemoteKeyBridge.kt",
    ).takeIf { it.exists() }?.readText().orEmpty()

    @Test
    fun idleOverlayDefaultsToPlayPauseInTransportDock() {
        assertTrue(screenSource.contains("LaunchedEffect(transportFocusRequest) { runCatching { playPauseFocus.requestFocus() } }"))
        // Primary group pinned left + secondary group pushed right.
        assertTrue(clusterSource.contains("Arrangement.SpaceBetween"))
        assertTrue(clusterSource.contains("Icons.Filled.Replay10"))
        assertTrue(clusterSource.contains("Icons.Filled.Forward30"))
        assertTrue(clusterSource.contains("Icons.Filled.Tune"))
        assertTrue(clusterSource.contains("Icons.Filled.Close"))
    }

    @Test
    fun transportDropsBackAndSubtitlesButtons() {
        // No Back button, no separate subtitles button — close uses xmark and
        // options (Tune) opens the floating HUD whose Subtitles tab owns tracks.
        assertFalse(clusterSource.contains("Icons.AutoMirrored.Filled.ArrowBack"))
        assertFalse(clusterSource.contains("Icons.Filled.Subtitles"))
        assertFalse(clusterSource.contains("Icons.Filled.MoreHoriz"))
        assertFalse(clusterSource.contains("Icons.Filled.Forward10"))
    }

    @Test
    fun hudIsFloatingTopCenterCardInsteadOfRightDrawer() {
        assertTrue(screenSource.contains("Alignment.TopCenter"))
        assertTrue(hudSource.contains(".widthIn(max = 1100.dp)"))
        assertTrue(hudSource.contains(".height(380.dp)"))
        assertFalse(hudSource.contains("PlayerSidePanel"))
        assertFalse(hudSource.contains(".width(560.dp)"))
    }

    @Test
    fun timelineShowsBufferedProgress() {
        assertTrue(screenSource.contains("bufferedAheadSec = bufferedAheadSec"))
        assertTrue(screenSource.contains("val bufferedAheadSec ="))
    }

    @Test
    fun timelineLabelsTimeAndChapterMarkers() {
        val scrubberSource = File(
            "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScrubber.kt",
        ).readText()

        assertTrue(scrubberSource.contains("formatScrubberTime(positionSec)"))
        assertTrue(scrubberSource.contains("formatRemainingTime(durationSec - positionSec)"))
        assertTrue(scrubberSource.contains("Chapter marker"))
        assertTrue(scrubberSource.contains("alpha = 0.45f"))
    }

    @Test
    fun subtitleTrackRowsCommitOnSelectInsteadOfFocusTraversal() {
        assertTrue(hudSource.contains("onSelect(opt.id)"))
        assertTrue(hudSource.contains(".clickable(interactionSource = interactionSource, indication = null)"))
        assertFalse(hudSource.contains("option = opt,\n                onFocused"))
    }

    @Test
    fun optionsButtonOpensFloatingHudWithSubtitleTab() {
        // The retired separate subtitles button + drawer are gone; subtitles are
        // now a HUD tab reached through the options (Tune) button.
        assertTrue(hudSource.contains("add(HudTab.Subtitles)"))
        assertTrue(hudSource.contains("HudTab.Subtitles -> HudSubtitlesPane"))
        assertTrue(screenSource.contains("onOpenHUD = onOpenHUD"))
        assertFalse(screenSource.contains("viewModel.openSubtitleMenu()"))
    }

    @Test
    fun subtitleTabAlwaysAvailableButAudioStillGated() {
        // Subtitles tab is unconditional — it hosts the Android-only Search /
        // AI-Translate / style controls that must stay reachable even when a
        // title carries no subtitle tracks (there is no separate subtitles
        // button anymore). Audio still hides when empty.
        assertTrue(hudSource.contains("add(HudTab.Subtitles)"))
        assertFalse(hudSource.contains("if (subtitleTracks.isNotEmpty()) add(HudTab.Subtitles)"))
        assertTrue(hudSource.contains("if (audioTracks.isNotEmpty()) add(HudTab.Audio)"))
    }

    @Test
    fun subtitleTabKeepsTrackListDelayStepperAndAppearance() {
        assertTrue(hudSource.contains("fun HudSubtitlesPane("))
        assertTrue(hudSource.contains("onSelectSubtitle(opt.id)"))
        assertTrue(hudSource.contains("DelayStepperRow(valueMs = subtitleDelayMs"))
        assertTrue(hudSource.contains("StyleSection(\"Text size\")"))
        assertTrue(hudSource.contains("StyleSection(\"Position\")"))
    }

    @Test
    fun subtitleTabKeepsAndroidOnlySearchAndAiRows() {
        assertTrue(hudSource.contains("Search subtitles"))
        assertTrue(hudSource.contains("Translate with AI"))
        assertTrue(screenSource.contains("viewModel.openSubtitleSearchDialog()"))
        assertTrue(screenSource.contains("viewModel.openAiTranslateDialog()"))
    }

    @Test
    fun subtitleSelectionUpdatesUiBeforeBackendTrackApply() {
        val selectionBlock = screenSource
            .substringAfter("val applyTvSubtitleSelection")
            .substringBefore("DisposableEffect(context)")
        val uiUpdateIndex = selectionBlock.indexOf("viewModel.onSubtitleSelectionApplied(idx)")
        val backendIndex = selectionBlock.indexOf("videoBackend?.selectSubtitle(selectedTrack)")

        assertTrue(uiUpdateIndex >= 0, "selection should optimistically update the checkmark")
        assertTrue(backendIndex >= 0, "selection should still apply the Media3 subtitle track")
        assertTrue(
            uiUpdateIndex < backendIndex,
            "UI state must not wait for Media3 track selection to succeed",
        )
    }

    @Test
    fun activityDispatchesRemoteKeysToMountedPlayerWhenComposeFocusIsLost() {
        assertTrue(activitySource.contains("@SuppressLint(\"RestrictedApi\")"))
        assertTrue(activitySource.contains("override fun dispatchKeyEvent(event: KeyEvent): Boolean"))
        assertTrue(activitySource.contains("TvPlayerRemoteKeyBridge.dispatch(event)"))
        assertTrue(remoteKeyBridgeSource.contains("fun install(handler: (KeyEvent) -> Boolean)"))
        assertTrue(remoteKeyBridgeSource.contains("fun dispatch(event: KeyEvent): Boolean"))
    }

    @Test
    fun mountedPlayerRegistersRemoteKeyBridgeAndRefocusesTransport() {
        assertTrue(screenSource.contains("TvPlayerRemoteKeyBridge.install(handler)"))
        assertTrue(screenSource.contains("TvPlayerRemoteKeyBridge.clear(handler)"))
        assertTrue(screenSource.contains("transportFocusRequest++"))
        assertTrue(screenSource.contains("LaunchedEffect(transportFocusRequest)"))
    }

    @Test
    fun showingAlreadyVisibleControlsRefreshesAutoHideTimer() {
        assertTrue(viewModelSource.contains("controlsVisibilityNonce"))
        assertTrue(viewModelSource.contains("controlsVisibilityNonce = if (visible)"))
        assertTrue(screenSource.contains("state.controlsVisibilityNonce"))
    }

    @Test
    fun tvPlayerDetectsDirectStartupStallsAndUsesExistingFallbackPath() {
        assertTrue(screenSource.contains("PlaybackStartupStallDetector"))
        assertTrue(screenSource.contains("startupStallDetector.onMounted("))
        assertTrue(screenSource.contains("startupStallDetector.sample("))
        assertTrue(screenSource.contains("viewModel.onUnsupportedPlayback(reason)"))
        assertTrue(viewModelSource.contains("Playability.StartupStalled"))
    }
}
