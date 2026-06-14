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
    private val subtitleMenuSource = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvSubtitleMenu.kt",
    ).takeIf { it.exists() }?.readText().orEmpty()
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
    fun idleOverlayDefaultsToPlayPauseInACenteredDock() {
        assertTrue(screenSource.contains("LaunchedEffect(transportFocusRequest) { runCatching { playPauseFocus.requestFocus() } }"))
        assertTrue(clusterSource.contains("Arrangement.Center"))
        assertTrue(clusterSource.contains("Icons.AutoMirrored.Filled.ArrowBack"))
        assertTrue(clusterSource.contains("Icons.Filled.Subtitles"))
        assertTrue(clusterSource.contains("Icons.Filled.MoreHoriz"))
        assertFalse(clusterSource.contains("Spacer(modifier = Modifier.weight(1f))"))
    }

    @Test
    fun morePanelIsRightAnchoredInsteadOfTopHud() {
        assertTrue(screenSource.contains("modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd)"))
        assertTrue(hudSource.contains("PlayerSidePanel"))
        assertTrue(hudSource.contains(".width(560.dp)"))
        assertFalse(hudSource.contains(".padding(top = 48.dp)"))
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
    fun subtitleButtonOpensDedicatedMenuInsteadOfMoreHud() {
        assertTrue(screenSource.contains("viewModel.openSubtitleMenu()"))
        assertTrue(screenSource.contains("state.showSubtitleMenu"))
        assertFalse(
            screenSource.contains(
                "onOpenTracks = {\n                            requestedHudTab = HudTab.Subtitles\n                            viewModel.openHUD()\n                        }",
            ),
        )
    }

    @Test
    fun moreHudDoesNotContainSubtitleTab() {
        assertFalse(hudSource.contains("add(HudTab.Subtitles)"))
        assertFalse(hudSource.contains("HudTab.Subtitles -> HudSubtitlesPane"))
    }

    @Test
    fun dedicatedSubtitleMenuClosesAfterTrackSelection() {
        assertTrue(subtitleMenuSource.contains("fun TvSubtitleMenu("))
        assertTrue(screenSource.contains("viewModel.closeSubtitleMenu()"))
        assertTrue(screenSource.contains("applyTvSubtitleSelection"))
    }

    @Test
    fun subtitleSelectionUpdatesUiBeforeBackendTrackApply() {
        val selectionBlock = screenSource
            .substringAfter("val applyTvSubtitleSelection")
            .substringBefore("DisposableEffect(context)")
        val uiUpdateIndex = selectionBlock.indexOf("viewModel.onSubtitleSelectionApplied(idx)")
        val closeIndex = selectionBlock.indexOf("if (dismiss) viewModel.closeSubtitleMenu()")
        val backendIndex = selectionBlock.indexOf("videoBackend?.selectSubtitle(selectedTrack)")

        assertTrue(uiUpdateIndex >= 0, "selection should optimistically update the checkmark")
        assertTrue(closeIndex >= 0, "selection should dismiss the dedicated subtitle menu")
        assertTrue(backendIndex >= 0, "selection should still apply the Media3 subtitle track")
        assertTrue(
            uiUpdateIndex < backendIndex,
            "UI state must not wait for Media3 track selection to succeed",
        )
        assertTrue(
            closeIndex < backendIndex,
            "the subtitle menu must close immediately after a remote select",
        )
    }

    @Test
    fun dedicatedSubtitleMenuTakesInitialRemoteFocus() {
        assertTrue(subtitleMenuSource.contains("FocusRequester"))
        assertTrue(subtitleMenuSource.contains("optionFocusRequesters[initiallyFocusedId]?.requestFocus()"))
        assertTrue(subtitleMenuSource.contains("Modifier.focusRequester(focusRequester)"))
    }

    @Test
    fun dedicatedSubtitleMenuRowsUseTvClickableSurfacesForRemoteSelect() {
        assertTrue(subtitleMenuSource.contains("ClickableSurfaceDefaults"))
        assertTrue(subtitleMenuSource.contains("Surface("))
    }

    @Test
    fun dedicatedSubtitleMenuListStaysInAFocusGroup() {
        assertTrue(subtitleMenuSource.contains(".focusGroup()"))
    }

    @Test
    fun dedicatedSubtitleMenuRowsPinVerticalFocusTraversal() {
        assertTrue(subtitleMenuSource.contains(".focusProperties"))
        assertTrue(subtitleMenuSource.contains("upFocusRequester"))
        assertTrue(subtitleMenuSource.contains("downFocusRequester"))
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
}
