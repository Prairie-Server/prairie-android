package com.continuum.app.android.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.continuum.app.common.player.SessionState
import com.continuum.app.common.player.SleepTimerState

/**
 * Full-screen overlay composable that layers gesture handling, transport controls,
 * and contextual buttons (skip intro, next episode) on top of the video surface.
 *
 * Also manages bottom sheet display for subtitle, audio, and quality selection.
 */
@Composable
fun PlayerOverlay(
    state: PlayerViewModel.PlayerUiState,
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Double) -> Unit,
    onToggleControls: () -> Unit,
    onNextEpisode: () -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    onSelectAudio: (Int) -> Unit,
    onSelectVersion: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSettings by remember { mutableStateOf(false) }
    var showSubtitleSelector by remember { mutableStateOf(false) }
    var showAudioSelector by remember { mutableStateOf(false) }
    var showQualitySelector by remember { mutableStateOf(false) }
    var settingsSheetVisible by remember { mutableStateOf(false) }
    var subtitleStyleVisible by remember { mutableStateOf(false) }
    var sleepTimerVisible by remember { mutableStateOf(false) }

    val introSkipState by viewModel.introSkipState.collectAsState()
    val sleepTimerState by viewModel.sleepTimerState.collectAsState()
    val sleepTimerDefault by viewModel.sleepTimerDefaultMinutes.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        // Gesture layer (always active, underneath controls)
        PlayerGestureHandler(
            position = state.position,
            duration = state.duration,
            onToggleControls = onToggleControls,
            onSeek = onSeek,
            onSkipForward = { onSeek((state.position + 10.0).coerceAtMost(state.duration)) },
            onSkipBackward = { onSeek((state.position - 10.0).coerceAtLeast(0.0)) },
        )

        // Buffering indicator. Shown during ExoPlayer buffering AND during outage
        // recovery — the lifecycle's Reconnecting state isn't visible to the player,
        // so we surface the spinner ourselves so the screen doesn't appear frozen.
        if (state.isBuffering || sessionState is SessionState.Reconnecting) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.Center),
                color = Color.White,
                strokeWidth = 3.dp,
            )
        }

        // Notice overlay (top-left). Driven by PlaybackSessionLifecycle.notice — surfaces
        // server-reconnecting / suspend warnings as a transient toast. Stacks above the
        // buffering spinner; fine to obscure briefly during Reconnecting (the spinner is
        // a redundant signal at that point).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp, start = 16.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            PlayerNoticeOverlay(notice = notice)
        }

        // Transport controls (shown/hidden with animation)
        AnimatedVisibility(
            visible = state.showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            PlayerControls(
                title = state.title,
                subtitle = state.subtitle,
                isPlaying = state.isPlaying,
                isPaused = state.isPaused,
                position = state.position,
                duration = state.duration,
                onBack = onBack,
                onPlayPause = onPlayPause,
                onSeek = onSeek,
                onSkipForward = { onSeek((state.position + 10.0).coerceAtMost(state.duration)) },
                onSkipBackward = { onSeek((state.position - 10.0).coerceAtLeast(0.0)) },
                onSettingsClick = { showSettings = !showSettings },
                onOpenSettingsSheet = { settingsSheetVisible = true },
            )
        }

        // Settings panel (subtitle, audio, quality buttons)
        AnimatedVisibility(
            visible = showSettings && state.showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 100.dp, end = 16.dp),
        ) {
            SettingsPanel(
                hasSubtitles = state.subtitleTracks.isNotEmpty(),
                hasMultipleAudio = state.audioTracks.size > 1,
                hasMultipleVersions = state.versions.size > 1,
                onSubtitleClick = {
                    showSettings = false
                    showSubtitleSelector = true
                },
                onAudioClick = {
                    showSettings = false
                    showAudioSelector = true
                },
                onQualityClick = {
                    showSettings = false
                    showQualitySelector = true
                },
            )
        }

        // Intro auto-skip banner (Hidden / ShowingButton / CountingDown)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 120.dp, end = 24.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            IntroAutoSkipBanner(
                state = introSkipState,
                onSkipNow = viewModel::onSkipIntroNow,
                onCancelCountdown = viewModel::onCancelIntroAutoSkip,
            )
        }

        // Next Episode overlay
        AnimatedVisibility(
            visible = state.showNextEpisode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 120.dp, end = 24.dp),
        ) {
            Button(
                onClick = onNextEpisode,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Next Episode")
            }
        }

        // Sleep timer chip — top-right, fades in only while a timer is active.
        // The chip stays visible regardless of `state.showControls` so users
        // know a sleep timer is still running even when the controls have
        // auto-hidden.
        AnimatedVisibility(
            visible = sleepTimerState is SleepTimerState.Active,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp),
        ) {
            val active = sleepTimerState as? SleepTimerState.Active
            if (active != null) {
                SleepTimerChip(remainingSeconds = active.remainingSeconds)
            }
        }
    }

    // Bottom sheet selectors (rendered outside the overlay Box so they layer correctly)
    if (showSubtitleSelector) {
        SubtitleSelector(
            subtitles = state.subtitleTracks,
            selectedIndex = state.selectedSubtitleIndex,
            onSelect = onSelectSubtitle,
            onDismiss = { showSubtitleSelector = false },
        )
    }

    if (showAudioSelector) {
        AudioTrackSelector(
            audioTracks = state.audioTracks,
            selectedIndex = state.selectedAudioIndex,
            onSelect = onSelectAudio,
            onDismiss = { showAudioSelector = false },
        )
    }

    if (showQualitySelector) {
        QualitySelector(
            versions = state.versions,
            selectedIndex = state.selectedVersionIndex,
            onSelect = onSelectVersion,
            onDismiss = { showQualitySelector = false },
        )
    }

    // Glass-style playback settings sheet (speed / aspect / HDR / auto-skip / auto-play)
    PlayerSettingsSheet(
        isVisible = settingsSheetVisible,
        onDismiss = { settingsSheetVisible = false },
        playbackSpeed = viewModel.playbackSpeed.collectAsState().value,
        onSetPlaybackSpeed = viewModel::onSetPlaybackSpeed,
        videoGravity = viewModel.videoGravity.collectAsState().value,
        onSetVideoGravity = viewModel::onSetVideoGravity,
        autoSkipIntroEnabled = viewModel.autoSkipIntroEnabled.collectAsState().value,
        onSetAutoSkipIntro = viewModel::onSetAutoSkipIntro,
        autoPlayNextEnabled = viewModel.autoPlayNextEnabled.collectAsState().value,
        onSetAutoPlayNext = viewModel::onSetAutoPlayNext,
        hdrEnabled = viewModel.hdrEnabled.collectAsState().value,
        onSetHdrEnabled = viewModel::onSetHdrEnabled,
        onOpenSubtitleStyle = {
            settingsSheetVisible = false
            subtitleStyleVisible = true
        },
        onOpenSleepTimer = {
            settingsSheetVisible = false
            sleepTimerVisible = true
        },
        sleepTimerState = sleepTimerState,
    )

    // Subtitle styling sheet — opened from the "Subtitle Style" row in
    // PlayerSettingsSheet. Material 3 sheets can't nest, so the parent sheet
    // dismisses itself before we open this one.
    SubtitleStyleSheet(
        isVisible = subtitleStyleVisible,
        appearance = viewModel.subtitleAppearance.collectAsState().value,
        onUpdate = viewModel::onSetSubtitleAppearance,
        onDismiss = { subtitleStyleVisible = false },
    )

    // Sleep timer picker — opened from the "Sleep Timer" row in
    // PlayerSettingsSheet. Same nested-sheet caveat as Subtitle Style above.
    SleepTimerSheet(
        isVisible = sleepTimerVisible,
        activeState = sleepTimerState,
        defaultMinutes = sleepTimerDefault,
        onStart = viewModel::onStartSleepTimer,
        onCancel = viewModel::onCancelSleepTimer,
        onDismiss = { sleepTimerVisible = false },
    )
}

/**
 * Sleep-timer status pill anchored top-right. Shows clock icon + "23m 17s"
 * remaining countdown. Non-interactive in v1 — cancel via the bottom sheet.
 */
@Composable
private fun SleepTimerChip(remainingSeconds: Int) {
    Row(
        modifier = Modifier
            .background(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Bedtime,
            contentDescription = "Sleep timer active",
            tint = Color.White,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = formatRemaining(remainingSeconds),
            color = Color.White,
            fontSize = 13.sp,
        )
    }
}

/**
 * Small floating panel with buttons for subtitle, audio, and quality settings.
 */
@Composable
private fun SettingsPanel(
    hasSubtitles: Boolean,
    hasMultipleAudio: Boolean,
    hasMultipleVersions: Boolean,
    onSubtitleClick: () -> Unit,
    onAudioClick: () -> Unit,
    onQualityClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .background(
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (hasSubtitles) {
            TextButton(onClick = onSubtitleClick) {
                Text("Subtitles", color = Color.White)
            }
        }
        if (hasMultipleAudio) {
            TextButton(onClick = onAudioClick) {
                Text("Audio", color = Color.White)
            }
        }
        if (hasMultipleVersions) {
            TextButton(onClick = onQualityClick) {
                Text("Quality", color = Color.White)
            }
        }
    }
}
