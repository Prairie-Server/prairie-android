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
        // Picker-dialog options commit on explicit Select (click), not focus
        // traversal — so D-pad-scrolling the option list doesn't change the
        // value until the user presses Select.
        assertTrue(hudSource.contains("presentation.onSelect(option.id)"))
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
    fun subtitleTabKeepsTrackSelectionDelayAndAppearance() {
        // Track selection, delay, size, and position are now drill-in setting
        // rows (HudFocusedSettingRow) that open a centered HudPickerDialog —
        // matching the tvOS row→picker-dialog model.
        assertTrue(hudSource.contains("fun HudSubtitlesPane("))
        assertTrue(hudSource.contains("onSelectSubtitle(id.toIntOrNull() ?: -1)"))
        assertTrue(hudSource.contains("delayPicker("))
        assertTrue(hudSource.contains("title = \"Subtitle Delay\""))
        assertTrue(hudSource.contains("title = \"Subtitle Size\""))
        assertTrue(hudSource.contains("title = \"Subtitle Position\""))
        // The shared row + dialog primitives exist.
        assertTrue(hudSource.contains("fun HudFocusedSettingRow("))
        assertTrue(hudSource.contains("fun HudPickerDialog("))
    }

    @Test
    fun videoPaneUsesRowDialogModelWithQualityPicker() {
        // The Video pane drives Quality / Speed / Aspect through the row→dialog
        // model; Quality is new and derived from the available video variants.
        assertTrue(hudSource.contains("title = \"Quality\""))
        assertTrue(hudSource.contains("title = \"Playback Speed\""))
        assertTrue(hudSource.contains("title = \"Aspect\""))
        // Speed presets aligned to tvOS (0.75 / 1.0 / 1.25 / 1.5 / 2.0).
        assertTrue(hudSource.contains("listOf(0.75, 1.0, 1.25, 1.5, 2.0)"))
        // The old inline "Fill mode" label is renamed to "Aspect".
        assertFalse(hudSource.contains("text = \"Fill mode\""))
    }

    @Test
    fun qualityRowAppliesRealVideoTrackOverrideAndDisablesWhenNoChoice() {
        // Quality must be a genuine Media3 video-track override, not a silent
        // no-op that just closes the dialog. The screen wires onSelectVideoQuality
        // to selectVideoQuality on the live player, which sets/clears an override.
        assertTrue(screenSource.contains("selectVideoQuality(it, id)"))
        assertTrue(screenSource.contains("setOverrideForType("))
        assertTrue(screenSource.contains("clearOverridesOfType(C.TRACK_TYPE_VIDEO)"))
        // The old no-op onSelectVideo wiring is gone.
        assertFalse(screenSource.contains("but no-op on tap"))
        // Quality options come from the real per-format variants (resolution /
        // bitrate), and the row disables when there is no genuine choice.
        assertTrue(screenSource.contains("fun extractVideoQualityOptions("))
        assertTrue(screenSource.contains("fun formatVideoQualityLabel("))
        assertTrue(hudSource.contains("val hasQualityChoice = videoQualities.size > 2"))
        assertTrue(hudSource.contains("enabled = enabled && hasQualityChoice"))
        // Quality is no longer keyed off the group-level videoTracks.size count.
        assertFalse(hudSource.contains("videoTracks.size > 1"))
    }

    @Test
    fun speedOptionIdsAreLocaleIndependent() {
        // The speed option id must round-trip on comma-decimal locales — build
        // and parse it with Locale.ROOT (not the default-locale "%.2f".format),
        // otherwise the selection silently no-ops on e.g. nl-NL.
        assertTrue(hudSource.contains("fun speedOptionId("))
        assertTrue(hudSource.contains("java.util.Locale.ROOT, \"%.2f\""))
        // The old default-locale id + toDoubleOrNull parse is gone.
        assertFalse(hudSource.contains("selectedId = \"%.2f\".format(playbackSpeed)"))
        assertFalse(hudSource.contains("id.toDoubleOrNull()?.let(onPlaybackSpeedChanged)"))
        // Commit matches by id, not by re-parsing a localized string.
        assertTrue(hudSource.contains("PLAYBACK_SPEED_OPTIONS.firstOrNull { speedOptionId(it) == id }"))
    }

    @Test
    fun backClosesOnlyThePickerWhilePickerOpen() {
        // Back must close the active picker first (consumed by the HUD), not the
        // whole HUD. The HUD's own key handler closes the picker; the screen-
        // level BackHandler defers (disabled) while a picker is open.
        assertTrue(hudSource.contains("if (activePicker != null) {"))
        assertTrue(hudSource.contains("activePicker = null"))
        assertTrue(hudSource.contains("onPickerOpenChanged(pickerOpen)"))
        assertTrue(screenSource.contains("BackHandler(enabled = !(state.hudOpen && hudPickerOpen))"))
        assertTrue(screenSource.contains("onPickerOpenChanged = { hudPickerOpen = it }"))
    }

    @Test
    fun pickerDialogIsAFocusTrap() {
        // The picker is a modal: D-pad must stay inside it. Focus is contained
        // with a focusGroup + cancelled exit, and the panes/tabs are blocked
        // from focus while a picker is open.
        assertTrue(hudSource.contains(".focusGroup()"))
        assertTrue(hudSource.contains("exit = { FocusRequester.Cancel }"))
        assertTrue(hudSource.contains("enabled = activePicker == null"))
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
