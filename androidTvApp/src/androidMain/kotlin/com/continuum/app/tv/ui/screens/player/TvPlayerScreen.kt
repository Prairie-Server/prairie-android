package com.continuum.app.tv.ui.screens.player

import android.app.Activity
import android.content.ComponentName
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.material3.CircularProgressIndicator
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.continuum.app.common.player.AudioCapabilityManager
import com.continuum.app.common.player.AudioTrackManager
import com.continuum.app.common.player.ContinuumPlaybackService
import com.continuum.app.common.player.ContinuumPlayerFactory
import com.continuum.app.common.player.DisplayHdrProbe
import com.continuum.app.common.player.HdrDisplayController
import com.continuum.app.common.player.PlaybackCapabilityDetector
import com.continuum.app.common.player.PlaybackPreflightListener
import com.continuum.app.common.player.SessionState
import com.continuum.app.common.player.SubtitleManager
import com.continuum.app.tv.ui.components.TvErrorScreen
import com.continuum.app.tv.ui.components.TvLoadingScreen
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val CONTROLS_AUTO_HIDE_MS = 5_000L
private const val SKIP_SECONDS_MS = 10_000L

/**
 * Full-screen TV player. The ExoPlayer itself lives in [ContinuumPlaybackService];
 * we drive it via a [MediaController]. The Compose overlay ([TvPlayerControls])
 * replaces the default [PlayerView] controller so we own focus, skip buttons,
 * and the subtitle / audio menus.
 */
@OptIn(UnstableApi::class)
@Composable
fun TvPlayerScreen(
    contentId: String,
    onExit: () -> Unit,
    preferredFileId: Int? = null,
    // Scope the ViewModel key by fileId too so switching 4K <-> 1080p on
    // the detail screen and replaying actually spins up a fresh player
    // session instead of reusing the cached one bound to the first fileId.
    viewModel: TvPlayerViewModel = koinViewModel(
        key = "tv-player-$contentId-${preferredFileId ?: "auto"}",
        parameters = {
            if (preferredFileId != null) parametersOf(contentId, preferredFileId)
            else parametersOf(contentId)
        },
    ),
    playerFactory: ContinuumPlayerFactory = koinInject(),
    subtitleManager: SubtitleManager = koinInject(),
    audioTrackManager: AudioTrackManager = koinInject(),
    audioCapabilityManager: AudioCapabilityManager = koinInject(),
    capabilityDetector: PlaybackCapabilityDetector = koinInject(),
) {
    val state by viewModel.uiState.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()
    val introSkipState by viewModel.introSkipState.collectAsState()
    val subtitleAppearance by viewModel.subtitleAppearance.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnExit by rememberUpdatedState(onExit)
    val context = LocalContext.current
    val hdrDisplayController = remember { HdrDisplayController() }
    val displayHdr = remember { DisplayHdrProbe.probe(context) }
    val audioCaps by audioCapabilityManager.capabilities.collectAsState()
    val rootFocus = remember { FocusRequester() }
    val exitScope = rememberCoroutineScope()
    var exitRequested by remember { mutableStateOf(false) }
    // Captured PlayerView reference so subtitleManager.applyAppearance can hit
    // the inflated subtitleView after the AndroidView factory runs. Mirrors
    // the phone PlayerScreen's `playerViewRef` pattern.
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    // Connect a MediaController to the ContinuumPlaybackService. Async —
    // downstream effects gate on a non-null controller.
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    val stopPlaybackAndExit = {
        if (!exitRequested) {
            exitRequested = true
            mediaController?.let { controller ->
                viewModel.onPositionChanged(
                    controller.currentPosition,
                    controller.duration.coerceAtLeast(0L),
                )
                controller.pause()
                controller.stop()
                controller.clearMediaItems()
            }
            exitScope.launch {
                viewModel.stopSessionForExit()
                latestOnExit()
            }
        }
    }

    DisposableEffect(context) {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, ContinuumPlaybackService::class.java),
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener(
            {
                if (future.isDone && !future.isCancelled) {
                    mediaController = runCatching { future.get() }.getOrNull()
                }
            },
            MoreExecutors.directExecutor(),
        )
        onDispose {
            mediaController?.release()
            mediaController = null
            if (!future.isDone) future.cancel(true)
        }
    }

    BackHandler(enabled = true) {
        when {
            state.hudOpen -> viewModel.closeHUD()
            state.subtitleMenuOpen -> viewModel.closeSubtitleMenu()
            state.audioMenuOpen -> viewModel.closeAudioMenu()
            state.showControls -> viewModel.setControlsVisible(false)
            else -> {
                stopPlaybackAndExit()
            }
        }
    }

    // Apply capability-aware track selection presets. Re-runs on HDMI
    // hot-plug / AVR power cycle so Atmos and DV stay preferred as the sink
    // reports them.
    LaunchedEffect(mediaController, audioCaps, state.preferredAudioLanguage, state.preferredTextLanguage) {
        val controller = mediaController ?: return@LaunchedEffect
        playerFactory.applyTrackSelectionPresets(
            player = controller,
            audioCaps = audioCaps,
            displayHdr = displayHdr,
            preferredAudioLanguage = state.preferredAudioLanguage,
            preferredTextLanguage = state.preferredTextLanguage,
        )
    }

    // HDR display-mode switching: attach the controller to the activity window
    // so we can drive `preferredDisplayModeId` when video size / frame rate
    // becomes known. Released on composition dispose.
    DisposableEffect(context) {
        (context as? Activity)?.let { hdrDisplayController.attach(it) }
        onDispose { hdrDisplayController.restore() }
    }

    // Lifecycle pausing — send pause to the service when we're backgrounded.
    DisposableEffect(lifecycleOwner, mediaController) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> mediaController?.pause()
                Lifecycle.Event.ON_STOP -> mediaController?.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Preflight listener — falls back to a transcoded stream if the selected
    // Tracks can't be direct-played (DV P7, TrueHD without passthrough, …).
    DisposableEffect(mediaController) {
        val controller = mediaController
        if (controller == null) {
            onDispose { }
        } else {
            val preflight = PlaybackPreflightListener(
                detector = capabilityDetector,
                onUnsupported = { verdict -> viewModel.onUnsupportedPlayback(verdict) },
            )
            controller.addListener(preflight)
            onDispose { controller.removeListener(preflight) }
        }
    }

    // Player listener → ViewModel. Pushes play/pause state, refreshes the
    // track menu state on track changes, and drives HDMI display-mode
    // switching on video size changes.
    DisposableEffect(mediaController) {
        val controller = mediaController
        if (controller == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    viewModel.onPlayingChanged(isPlaying)
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    // Buffering during normal playback flips the centered
                    // spinner. This complements the lifecycle's Reconnecting
                    // state which the player can't observe (server-outage
                    // probe loop runs out-of-band).
                    viewModel.onBufferingChanged(playbackState == Player.STATE_BUFFERING)
                }
                override fun onTracksChanged(tracks: Tracks) {
                    val audio = extractTrackEntries(tracks, C.TRACK_TYPE_AUDIO)
                    val subtitle = extractTrackEntries(tracks, C.TRACK_TYPE_TEXT)
                    val video = extractTrackEntries(tracks, C.TRACK_TYPE_VIDEO)
                    viewModel.onTracksChanged(audio, subtitle, video)
                }
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    // MediaController doesn't expose ExoPlayer's `videoFormat`
                    // accessor, so read the frame rate off the currently
                    // selected video track in `currentTracks`. That's the
                    // same signal — `Format.frameRate` flows through both.
                    val frameRate = controller.currentTracks.groups
                        .firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
                        ?.let { g ->
                            val mg = g.mediaTrackGroup
                            if (mg.length > 0) mg.getFormat(0).frameRate else 0f
                        } ?: 0f
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        hdrDisplayController.applyForMedia(
                            videoWidth = videoSize.width,
                            videoHeight = videoSize.height,
                            frameRateHz = frameRate,
                        )
                    }
                }
            }
            controller.addListener(listener)
            onDispose { controller.removeListener(listener) }
        }
    }

    // Position polling — lifecycle-bounded so it doesn't outlive the screen.
    LaunchedEffect(mediaController, state.sessionId, lifecycleOwner) {
        val controller = mediaController ?: return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive && state.sessionId != null) {
                viewModel.onPositionChanged(
                    controller.currentPosition,
                    controller.duration.coerceAtLeast(0L),
                )
                delay(500)
            }
        }
    }

    // Prepare the player when a stream URL becomes available.
    LaunchedEffect(mediaController, state.streamUrl, state.sessionId) {
        val controller = mediaController ?: return@LaunchedEffect
        val url = state.streamUrl ?: return@LaunchedEffect
        val method = state.playMethod ?: return@LaunchedEffect
        val mediaItem = playerFactory.buildMediaItem(
            streamUrl = url,
            playMethod = method,
            serverUrl = state.serverUrl,
            subtitles = state.subtitleUrls,
        )
        controller.setMediaItem(mediaItem)
        val startMs = (state.startPosition * 1000).toLong()
        if (startMs > 0) controller.seekTo(startMs)
        controller.prepare()
        controller.playWhenReady = true
    }

    // Mirror user-intent pause state into the player. Kept separate from the
    // onPlayingChanged listener so a transient buffering stall can't flip the
    // pause icon or cancel the auto-hide timer.
    LaunchedEffect(mediaController, state.isPaused) {
        mediaController?.playWhenReady = !state.isPaused
    }

    LaunchedEffect(mediaController) {
        val controller = mediaController ?: return@LaunchedEffect
        viewModel.seekRequests.collect { targetSec ->
            controller.seekTo((targetSec * 1000).toLong())
        }
    }

    // Apply per-profile playback speed to the MediaController. Uses
    // PlaybackParameters because MediaController doesn't expose a direct
    // setPlaybackSpeed setter that respects pitch correction defaults.
    LaunchedEffect(mediaController, playbackSpeed) {
        val controller = mediaController ?: return@LaunchedEffect
        controller.playbackParameters = PlaybackParameters(playbackSpeed.toFloat())
    }

    // Apply user subtitle styling whenever the PlayerView mounts or the
    // appearance flow emits a new value. Mirrors the phone PlayerScreen.
    LaunchedEffect(playerViewRef, subtitleAppearance) {
        val pv = playerViewRef ?: return@LaunchedEffect
        subtitleManager.applyAppearance(pv, subtitleAppearance)
    }

    // Ensure the outer Box owns focus when the overlay is hidden so the first
    // remote key press can reach onPreviewKeyEvent.
    LaunchedEffect(state.showControls) {
        if (!state.showControls) {
            runCatching { rootFocus.requestFocus() }
        }
    }

    // Auto-hide the Compose overlay after CONTROLS_AUTO_HIDE_MS.
    LaunchedEffect(state.showControls, state.isPaused) {
        if (state.showControls && !state.isPaused) {
            delay(CONTROLS_AUTO_HIDE_MS)
            viewModel.setControlsVisible(false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    event.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_BACK &&
                    !state.showControls &&
                    !state.subtitleMenuOpen &&
                    !state.audioMenuOpen
                ) {
                    viewModel.setControlsVisible(true)
                }
                false
            },
    ) {
        when {
            state.isLoading -> TvLoadingScreen()
            state.error != null -> TvErrorScreen(
                message = state.error!!,
                onRetry = null,
            )
            state.streamUrl != null -> {
                val controller = mediaController
                if (controller != null) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                // Capture the inflated view so the subtitle
                                // appearance LaunchedEffect can target it.
                                playerViewRef = this
                            }
                        },
                        update = { view -> view.player = controller },
                    )
                }

                if (state.showControls && !state.hudOpen) {
                    TvPlayerIdleOverlay(
                        title = state.title,
                        positionSec = state.position,
                        durationSec = state.duration,
                        isPaused = state.isPaused,
                        isScrubbing = state.isScrubbing,
                        scrubPreviewSec = state.scrubPreviewSec,
                        onSkipBack = {
                            val c = mediaController ?: return@TvPlayerIdleOverlay
                            val target = (c.currentPosition - SKIP_SECONDS_MS)
                                .coerceAtLeast(0L)
                            c.seekTo(target)
                            viewModel.setControlsVisible(true)
                        },
                        onSkipForward = {
                            val c = mediaController ?: return@TvPlayerIdleOverlay
                            val dur = c.duration.coerceAtLeast(0L)
                            val target = (c.currentPosition + SKIP_SECONDS_MS)
                                .coerceAtMost(dur)
                            c.seekTo(target)
                            viewModel.setControlsVisible(true)
                        },
                        onBeginScrub = { viewModel.beginScrub() },
                        onUpdateScrub = { sec -> viewModel.updateScrubPreview(sec) },
                        onCommitScrub = {
                            val targetSec = viewModel.commitScrub()
                            mediaController?.seekTo((targetSec * 1000).toLong())
                            viewModel.setControlsVisible(true)
                        },
                        onCancelScrub = { viewModel.cancelScrub() },
                        onPlayPause = {
                            viewModel.onPlayPause()
                            viewModel.setControlsVisible(true)
                        },
                        onOpenHUD = { viewModel.openHUD() },
                        onClose = { stopPlaybackAndExit() },
                    )
                }

                if (state.hudOpen) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        TvPlayerHud(
                            title = state.title,
                            positionSec = state.position,
                            durationSec = state.duration,
                            audioTracks = state.audioTracks,
                            subtitleTracks = state.subtitleTracks,
                            videoTracks = state.videoTracks,
                            onSelectAudio = { idx ->
                                mediaController?.let { audioTrackManager.selectAudioTrack(it, idx) }
                            },
                            onSelectSubtitle = { idx ->
                                mediaController?.let { subtitleManager.selectSubtitle(it, idx) }
                            },
                            onSelectVideo = { _ ->
                                // Selecting a specific video track on a single-stream
                                // playback rarely matters (tracks are equivalent
                                // resolution variants of the same stream); MediaController
                                // doesn't expose a direct setter the way audio/subtitle do,
                                // so we surface the picker for visibility but no-op on tap.
                            },
                            onDismiss = { viewModel.closeHUD() },
                            modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter),
                        )
                    }
                }
            }
        }

        if (state.subtitleMenuOpen) {
            TvSubtitleMenu(
                tracks = state.subtitleTracks,
                onSelect = { index ->
                    mediaController?.let { subtitleManager.selectSubtitle(it, index) }
                    viewModel.closeSubtitleMenu()
                },
                onDismiss = { viewModel.closeSubtitleMenu() },
            )
        }
        if (state.audioMenuOpen) {
            TvAudioTrackMenu(
                tracks = state.audioTracks,
                onSelect = { index ->
                    mediaController?.let { audioTrackManager.selectAudioTrack(it, index) }
                    viewModel.closeAudioMenu()
                },
                onDismiss = { viewModel.closeAudioMenu() },
            )
        }

        // Lifecycle-driven notice toast (top-start). Slides in for outage
        // recovery, fades out when the lifecycle clears the notice.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 32.dp, start = 32.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            TvPlayerNoticeOverlay(notice = notice)
        }

        // Intro auto-skip banner (bottom-end, above the transport cluster).
        // Only shown when the idle overlay is up — otherwise the banner would
        // float on top of unrelated chrome (HUD, menus). The banner itself
        // owns its visibility (Hidden = empty Spacer), but gating it on the
        // overlay means the banner won't steal focus while the user is
        // navigating menus. Bottom inset (200dp) clears the transport
        // cluster + scrubber column.
        if (state.showControls && !state.hudOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 200.dp, end = 32.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                TvIntroAutoSkipBanner(
                    state = introSkipState,
                    onSkipNow = {
                        val target = viewModel.onSkipIntroNow()
                        if (target != null) {
                            mediaController?.seekTo((target * 1000).toLong())
                        }
                    },
                    onCancelCountdown = viewModel::onCancelIntroAutoSkip,
                )
            }
        }

        // Buffering / outage spinner. Shows during native ExoPlayer buffering
        // (state.isBuffering) OR while the lifecycle is in Reconnecting (the
        // server-outage probe loop, which the player itself can't observe).
        val showSpinner = state.isBuffering || sessionState is SessionState.Reconnecting
        if (showSpinner) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(64.dp),
                )
            }
        }
    }
}

/**
 * Idle controls overlay — bottom-anchored gradient scrim with title + time +
 * progress bar above the transport cluster. Mirrors the tvOS idle overlay
 * pattern (spec §4.1) without yet wiring the full interactive scrubber, which
 * needs an ExoPlayer-backed scrub state machine that's a separate concern.
 */
@Composable
private fun TvPlayerIdleOverlay(
    title: String,
    positionSec: Double,
    durationSec: Double,
    isPaused: Boolean,
    isScrubbing: Boolean,
    scrubPreviewSec: Double,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onBeginScrub: () -> Unit,
    onUpdateScrub: (Double) -> Unit,
    onCommitScrub: () -> Unit,
    onCancelScrub: () -> Unit,
    onOpenHUD: () -> Unit,
    onClose: () -> Unit,
) {
    val scrubberFocus = remember { FocusRequester() }
    val playPauseFocus = remember { FocusRequester() }
    var currentRate by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { runCatching { scrubberFocus.requestFocus() } }

    Box(modifier = Modifier.fillMaxSize()) {
        // Bottom gradient scrim — 240dp tall, ~0.55 black at the bottom edge,
        // fading to transparent so video content above stays visible.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Transparent,
                        0.40f to Color.Black.copy(alpha = 0.30f),
                        1.00f to Color.Black.copy(alpha = 0.55f),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 80.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Title row.
            if (title.isNotBlank()) {
                androidx.tv.material3.Text(
                    text = title,
                    color = Color.White,
                    style = androidx.tv.material3.MaterialTheme.typography.titleLarge,
                )
            }

            // Interactive scrubber — capsule track with chapter ticks, ±10s
            // skip, hold-to-auto-seek, and Select to commit. tvOS spec §4.1.
            TvPlayerScrubber(
                positionSec = positionSec,
                durationSec = durationSec,
                bufferedAheadSec = 0.0,
                isScrubbing = isScrubbing,
                scrubPreviewSec = scrubPreviewSec,
                chapters = emptyList(),
                cancelOnBlur = false,
                onSkipBack = onSkipBack,
                onSkipForward = onSkipForward,
                onBeginScrub = onBeginScrub,
                onUpdateScrub = onUpdateScrub,
                onCommitScrub = onCommitScrub,
                onCancelScrub = onCancelScrub,
                onRequestFocus = scrubberFocus,
                onMoveDownToTransport = {
                    runCatching { playPauseFocus.requestFocus() }
                },
                onExitWhenIdle = onClose,
                onRateChanged = { currentRate = it },
            )

            Spacer(modifier = Modifier.height(8.dp))

            TvPlayerTransportCluster(
                isPlaying = !isPaused,
                onSkipBack = onSkipBack,
                onPlayPause = onPlayPause,
                onSkipForward = onSkipForward,
                onOpenHUD = onOpenHUD,
                onClose = onClose,
                playPauseFocus = playPauseFocus,
                onMoveUpToScrubber = {
                    runCatching { scrubberFocus.requestFocus() }
                },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            TvHoldSeekIndicator(
                isVisible = currentRate != 0,
                rate = currentRate,
                previewTimeSec = scrubPreviewSec,
                durationSec = durationSec,
            )
        }
    }
}

private fun formatPlayerTime(seconds: Double): String {
    if (seconds <= 0 || seconds.isNaN()) return "0:00"
    val total = seconds.toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * Flatten an ExoPlayer [Tracks] object into TV-facing entries. Each unique
 * [C.TRACK_TYPE_AUDIO] or [C.TRACK_TYPE_TEXT] group becomes one entry whose
 * `index` is its ordinal among same-type groups — that's what
 * [AudioTrackManager.selectAudioTrack] and [SubtitleManager.selectSubtitle]
 * expect as their "which track" argument.
 */
private fun extractTrackEntries(tracks: Tracks, type: Int): List<PlayerTrackEntry> {
    val result = mutableListOf<PlayerTrackEntry>()
    var groupIndex = 0
    for (group in tracks.groups) {
        if (group.type != type) continue
        val mediaGroup = group.mediaTrackGroup
        val format = if (mediaGroup.length > 0) mediaGroup.getFormat(0) else null
        val label = format?.label.orEmpty().ifBlank { format?.language?.uppercase() ?: "" }
        val language = format?.language
        val selected = group.isSelected
        result.add(
            PlayerTrackEntry(
                index = groupIndex,
                label = label,
                language = language,
                isSelected = selected,
            ),
        )
        groupIndex++
    }
    return result
}
