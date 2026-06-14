package com.continuum.app.tv.ui.screens.player

import android.app.Activity
import android.content.ComponentName
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
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
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.continuum.app.common.player.AudioCapabilityManager
import com.continuum.app.common.player.ContinuumPlaybackService
import com.continuum.app.common.player.DisplayHdrProbe
import com.continuum.app.common.player.HdrDisplayController
import com.continuum.app.common.player.PlaybackCapabilityDetector
import com.continuum.app.common.player.PlaybackPreflightListener
import com.continuum.app.common.player.SessionState
import com.continuum.app.common.player.SubtitleManager
import com.continuum.app.common.player.VideoPlayerMediaSpec
import com.continuum.app.common.player.backend.VideoPlaybackBackendFactory
import com.continuum.app.common.player.backend.VideoPlaybackBackendRequest
import com.continuum.app.common.player.backend.VideoPlaybackFormFactor
import com.continuum.app.common.player.video.VideoPlayerTrackEntry
import com.continuum.app.model.watchtogether.RoomPlaybackState
import com.continuum.app.tv.R
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
    // Watch Together room binding. When non-null, a [TvRoomSyncController]
    // binds this player to the synced room for the lifetime of the screen.
    roomId: String? = null,
    resumePositionOverride: Double? = null,
    // Scope the ViewModel key by fileId too so switching 4K <-> 1080p on
    // the detail screen and replaying actually spins up a fresh player
    // session instead of reusing the cached one bound to the first fileId.
    viewModel: TvPlayerViewModel = koinViewModel(
        key = "tv-player-$contentId-${preferredFileId ?: "auto"}-${roomId ?: "solo"}-${resumePositionOverride ?: "server"}",
        parameters = {
            parametersOf(
                TvPlayerLaunchArgs(
                    contentId = contentId,
                    preferredFileId = preferredFileId,
                    roomId = roomId,
                    resumePositionOverride = resumePositionOverride,
                ),
            )
        },
    ),
    backendFactory: VideoPlaybackBackendFactory = koinInject(),
    subtitleManager: SubtitleManager = koinInject(),
    audioCapabilityManager: AudioCapabilityManager = koinInject(),
    capabilityDetector: PlaybackCapabilityDetector = koinInject(),
) {
    val state by viewModel.uiState.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()
    val introSkipState by viewModel.introSkipState.collectAsState()
    val subtitleAppearance by viewModel.subtitleAppearance.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val audioDelayMs by viewModel.audioDelayMs.collectAsState()
    val subtitleDelayMs by viewModel.subtitleDelayMs.collectAsState()
    val hdrEnabled by viewModel.hdrEnabled.collectAsState()
    val subtitleSearch by viewModel.subtitleSearch.collectAsState()
    val aiTranslate by viewModel.aiTranslate.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnExit by rememberUpdatedState(onExit)
    val context = LocalContext.current
    val hdrDisplayController = remember { HdrDisplayController() }
    val displayHdr = remember { DisplayHdrProbe.probe(context) }
    val audioCaps by audioCapabilityManager.capabilities.collectAsState()
    val rootFocus = remember { FocusRequester() }
    val exitScope = rememberCoroutineScope()
    var exitRequested by remember { mutableStateOf(false) }
    var requestedHudTab by remember { mutableStateOf(HudTab.Info) }
    // Captured PlayerView reference so subtitleManager.applyAppearance can hit
    // the inflated subtitleView after the AndroidView factory runs. Mirrors
    // the phone PlayerScreen's `playerViewRef` pattern.
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var transportFocusRequest by remember { mutableStateOf(0) }

    // Watch Together binding. Built once per roomId; null for solo playback.
    // The controller owns the room WS connection + RoomSyncEngine for the
    // lifetime of this screen and tears them down on explicit leave.
    val watchTogetherRepository: com.continuum.app.repository.WatchTogetherRepository = koinInject()
    val roomScope = rememberCoroutineScope()
    val roomController = remember(roomId) {
        roomId?.takeIf { it.isNotBlank() }?.let { id ->
            TvRoomSyncController(
                roomId = id,
                repository = watchTogetherRepository,
                viewModel = viewModel,
                scope = roomScope,
            )
        }
    }
    DisposableEffect(roomController) {
        roomController?.start()
        // Repo teardown happens on explicit leave (Leave affordance) or
        // room_closed; the connect scope dies with roomScope on dispose.
        onDispose { }
    }
    val roomSnapshot by (roomController?.room ?: kotlinx.coroutines.flow.MutableStateFlow(null))
        .collectAsState()
    val roomClosedReason by (roomController?.closedReason ?: kotlinx.coroutines.flow.MutableStateFlow(null))
        .collectAsState()
    var showLeaveDialog by remember { mutableStateOf(false) }

    // Connect a MediaController to the ContinuumPlaybackService. Async —
    // downstream effects gate on a non-null controller.
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    val videoBackend = remember(mediaController, backendFactory, contentId, preferredFileId) {
        mediaController?.let { controller ->
            backendFactory.create(
                player = controller,
                request = VideoPlaybackBackendRequest(
                    contentId = contentId,
                    fileId = preferredFileId,
                    formFactor = VideoPlaybackFormFactor.Tv,
                ),
            )
        }
    }
    LaunchedEffect(videoBackend) {
        videoBackend?.let { backend ->
            viewModel.onBackendCapabilities(backend.capabilities)
        }
    }
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
    val applyTvSubtitleSelection: (Int, Boolean) -> Unit = { idx, dismiss ->
        val selectedTrack = state.subtitleTracks
            .firstOrNull { it.index == idx }
            ?.toVideoTrackEntry()
        viewModel.onSubtitleSelectionApplied(idx)
        if (dismiss) viewModel.closeSubtitleMenu()
        if (videoBackend?.selectSubtitle(selectedTrack) != true) {
            Log.w(TAG, "Subtitle selection deferred or failed for index=$idx")
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
            state.showSubtitleMenu -> viewModel.closeSubtitleMenu()
            state.hudOpen -> viewModel.closeHUD()
            showLeaveDialog -> showLeaveDialog = false
            state.showControls -> viewModel.setControlsVisible(false)
            // In a room: Back surfaces the Leave affordance. Host gets a
            // close-confirm dialog (closing tears down the room for everyone);
            // a guest leaves immediately.
            roomController != null && roomSnapshot?.isHost == true -> showLeaveDialog = true
            roomController != null -> {
                roomController.leave(closeRoom = false)
                stopPlaybackAndExit()
            }
            else -> {
                stopPlaybackAndExit()
            }
        }
    }

    val latestPlayerState by rememberUpdatedState(state)
    val latestRoomSnapshot by rememberUpdatedState(roomSnapshot)
    val latestShowLeaveDialog by rememberUpdatedState(showLeaveDialog)
    DisposableEffect(viewModel, roomController) {
        val handler: (KeyEvent) -> Boolean = handler@{ event ->
            val playerState = latestPlayerState
            if (playerState.streamUrl == null || playerState.isLoading || playerState.error != null) {
                return@handler false
            }
            if (playerState.hudOpen || playerState.showSubtitleMenu || latestShowLeaveDialog) {
                return@handler false
            }

            val action = tvPlayerRemoteKeyAction(
                keyCode = event.keyCode,
                action = event.action,
                repeatCount = event.repeatCount,
            )
            when (action) {
                TvPlayerRemoteKeyAction.PlayPause -> {
                    val canPlayPauseInRoom = roomController == null ||
                        tvRoomTransportGate(
                            latestRoomSnapshot,
                            TvTransportIntent.PlayPause,
                        ) == TransportGate.Send
                    if (canPlayPauseInRoom) {
                        if (roomController != null) {
                            roomController.onUserPlayPause()
                        } else {
                            viewModel.onPlayPause()
                        }
                    }
                    viewModel.setControlsVisible(true)
                    transportFocusRequest++
                    true
                }
                TvPlayerRemoteKeyAction.FocusTransport -> {
                    viewModel.setControlsVisible(true)
                    transportFocusRequest++
                    true
                }
                TvPlayerRemoteKeyAction.OpenHud -> {
                    requestedHudTab = HudTab.Info
                    viewModel.openHUD()
                    true
                }
                TvPlayerRemoteKeyAction.ConsumeOnly -> true
                null -> {
                    if (
                        event.action == KeyEvent.ACTION_DOWN &&
                        event.keyCode != KeyEvent.KEYCODE_BACK &&
                        !playerState.showControls
                    ) {
                        viewModel.setControlsVisible(true)
                        transportFocusRequest++
                        true
                    } else {
                        false
                    }
                }
            }
        }
        TvPlayerRemoteKeyBridge.install(handler)
        onDispose { TvPlayerRemoteKeyBridge.clear(handler) }
    }

    // room_closed (TERMINAL only — host left / explicit close) → stop + exit
    // back to detail. Transient server `error` frames never reach here (they
    // flow on the repo's errors stream and do NOT eject the user).
    LaunchedEffect(roomClosedReason) {
        if (roomClosedReason != null && roomController != null) {
            stopPlaybackAndExit()
        }
    }

    // Surface transient Watch Together server rejections (e.g. a guest seek the
    // server refuses) as a brief Toast. These flow on the repo errors stream and
    // do NOT eject the user. Only collected while bound to a room.
    LaunchedEffect(roomController) {
        if (roomController != null) {
            watchTogetherRepository.errors.collect { message ->
                if (message.isNotBlank()) {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Apply capability-aware track selection presets. Re-runs on HDMI
    // hot-plug / AVR power cycle so Atmos and DV stay preferred as the sink
    // reports them. Also re-runs when the user flips the HDR toggle in the
    // HUD so the new preference takes effect on the already-mounted player
    // (A.3d-hdr).
    LaunchedEffect(
        mediaController,
        videoBackend,
        audioCaps,
        state.preferredAudioLanguage,
        state.preferredTextLanguage,
        hdrEnabled,
    ) {
        val backend = videoBackend ?: return@LaunchedEffect
        backend.applyTrackSelection(
            audioCaps = audioCaps,
            displayHdr = displayHdr,
            preferredAudioLanguage = state.preferredAudioLanguage,
            preferredTextLanguage = state.preferredTextLanguage,
            hdrEnabled = hdrEnabled,
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
                    Log.i(
                        TAG,
                        "onTracksChanged subtitles=" + subtitle.joinToString(
                            prefix = "[",
                            postfix = "]",
                        ) { "${it.index}:${it.displayLabel}:selected=${it.isSelected}:lang=${it.language}" },
                    )
                    viewModel.onTracksChanged(audio, subtitle, video)
                }
                override fun onCues(cueGroup: CueGroup) {
                    val sample = cueGroup.cues
                        .take(2)
                        .joinToString(" | ") { cue ->
                            cue.text?.toString()?.take(80).orEmpty()
                        }
                    Log.i(
                        TAG,
                        "onCues count=${cueGroup.cues.size} positionMs=${controller.currentPosition} sample=$sample",
                    )
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
    LaunchedEffect(videoBackend, state.streamUrl, state.sessionId) {
        val backend = videoBackend ?: return@LaunchedEffect
        val url = state.streamUrl ?: return@LaunchedEffect
        val method = state.playMethod ?: return@LaunchedEffect
        val mediaSpec = VideoPlayerMediaSpec(
            streamUrl = url,
            playMethod = method,
            serverUrl = state.serverUrl,
            subtitles = state.subtitleUrls,
            title = state.title.ifBlank { null },
            artworkUrl = state.artworkUrl,
            startPositionSeconds = state.startPosition,
        )
        backend.mount(mediaSpec)
    }

    // Subtitle refresh (search download / AI completion): Media3 cannot add
    // SubtitleConfigurations to a live item, so rebuild the SAME MediaItem —
    // identical stream URL + playback session — with the merged sidecar list
    // and resume at the captured position. Keyed on the refresh nonce so the
    // initial prepare effect above remains the only session-start path.
    LaunchedEffect(videoBackend, state.subtitleRefreshNonce) {
        if (state.subtitleRefreshNonce == 0) return@LaunchedEffect
        val backend = videoBackend ?: return@LaunchedEffect
        val url = state.streamUrl ?: return@LaunchedEffect
        val method = state.playMethod ?: return@LaunchedEffect
        val mediaSpec = VideoPlayerMediaSpec(
            streamUrl = url,
            playMethod = method,
            serverUrl = state.serverUrl,
            subtitles = state.subtitleUrls,
            title = state.title.ifBlank { null },
            artworkUrl = state.artworkUrl,
            startPositionSeconds = state.startPosition,
        )
        backend.refresh(mediaSpec)
    }

    // Auto-select a freshly downloaded/translated subtitle track once the
    // rebuilt item's tracks land (the VM matches by label in onTracksChanged
    // and emits the ordinal text-group index). Mirrors the seekRequests idiom.
    LaunchedEffect(videoBackend) {
        val backend = videoBackend ?: return@LaunchedEffect
        viewModel.subtitleSelectRequests.collect { idx ->
            val selectedTrack = viewModel.uiState.value.subtitleTracks
                .firstOrNull { it.index == idx }
                ?.toVideoTrackEntry()
            if (backend.selectSubtitle(selectedTrack)) {
                viewModel.onSubtitleSelectionApplied(idx)
            }
        }
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
    LaunchedEffect(
        state.showControls,
        state.controlsVisibilityNonce,
        state.isPaused,
        state.hudOpen,
        state.showSubtitleMenu,
    ) {
        if (state.showControls && !state.isPaused && !state.hudOpen && !state.showSubtitleMenu) {
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
                    !state.showControls
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
                            (LayoutInflater.from(ctx).inflate(
                                R.layout.tv_player_view,
                                null,
                                false,
                            ) as PlayerView).apply {
                                useController = false
                                isFocusable = false
                                isFocusableInTouchMode = false
                                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                // Capture the inflated view so the subtitle
                                // appearance LaunchedEffect can target it.
                                playerViewRef = this
                            }
                        },
                        update = { view ->
                            view.player = controller
                            view.resizeMode = when (state.videoFillMode) {
                                VideoFillMode.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                VideoFillMode.Zoom -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            }
                        },
                    )
                }

                if (state.showControls && !state.hudOpen && !state.showSubtitleMenu) {
                    // In a room, transport authority gates what the local
                    // member may drive: a guest who can't seek gets a disabled
                    // scrubber + skip; play/pause only under guest_play_pause.
                    val canSeekInRoom = roomController == null ||
                        tvRoomTransportGate(roomSnapshot, TvTransportIntent.Seek) == TransportGate.Send
                    val canPlayPauseInRoom = roomController == null ||
                        tvRoomTransportGate(roomSnapshot, TvTransportIntent.PlayPause) == TransportGate.Send
                    val bufferedAheadSec = (
                        (mediaController?.bufferedPosition ?: 0L) -
                            (mediaController?.currentPosition ?: 0L)
                    ).coerceAtLeast(0L) / 1000.0
                    TvPlayerIdleOverlay(
                        title = state.title,
                        positionSec = state.position,
                        durationSec = state.duration,
                        isPaused = state.isPaused,
                        isScrubbing = state.isScrubbing,
                        scrubPreviewSec = state.scrubPreviewSec,
                        bufferedAheadSec = bufferedAheadSec,
                        chapters = state.chapters,
                        // In a room, skip/scrub/seek are routed through the
                        // controller (transport_request → server → broadcast
                        // command → engine applies the seek locally). Solo
                        // playback seeks the MediaController directly.
                        transportEnabled = canSeekInRoom,
                        playPauseEnabled = canPlayPauseInRoom,
                        onSkipBack = {
                            val c = mediaController ?: return@TvPlayerIdleOverlay
                            if (!canSeekInRoom) return@TvPlayerIdleOverlay
                            val target = (c.currentPosition - SKIP_SECONDS_MS)
                                .coerceAtLeast(0L)
                            if (roomController != null) {
                                roomController.onUserSeek(target / 1000.0)
                            } else {
                                c.seekTo(target)
                            }
                            viewModel.setControlsVisible(true)
                        },
                        onSkipForward = {
                            val c = mediaController ?: return@TvPlayerIdleOverlay
                            if (!canSeekInRoom) return@TvPlayerIdleOverlay
                            val dur = c.duration.coerceAtLeast(0L)
                            val target = (c.currentPosition + SKIP_SECONDS_MS)
                                .coerceAtMost(dur)
                            if (roomController != null) {
                                roomController.onUserSeek(target / 1000.0)
                            } else {
                                c.seekTo(target)
                            }
                            viewModel.setControlsVisible(true)
                        },
                        onBeginScrub = { viewModel.beginScrub() },
                        onUpdateScrub = { sec -> viewModel.updateScrubPreview(sec) },
                        onCommitScrub = {
                            val targetSec = viewModel.commitScrub()
                            if (!canSeekInRoom) return@TvPlayerIdleOverlay
                            if (roomController != null) {
                                roomController.onUserSeek(targetSec)
                            } else {
                                mediaController?.seekTo((targetSec * 1000).toLong())
                            }
                            viewModel.setControlsVisible(true)
                        },
                        onCancelScrub = { viewModel.cancelScrub() },
                        transportFocusRequest = transportFocusRequest,
                        onPlayPause = {
                            if (!canPlayPauseInRoom) return@TvPlayerIdleOverlay
                            if (roomController != null) {
                                roomController.onUserPlayPause()
                            } else {
                                viewModel.onPlayPause()
                            }
                            viewModel.setControlsVisible(true)
                        },
                        onOpenTracks = {
                            viewModel.openSubtitleMenu()
                        },
                        onOpenHUD = {
                            requestedHudTab = HudTab.Info
                            viewModel.openHUD()
                        },
                        onClose = {
                            when {
                                roomController != null && roomSnapshot?.isHost == true ->
                                    showLeaveDialog = true
                                roomController != null -> {
                                    roomController.leave(closeRoom = false)
                                    stopPlaybackAndExit()
                                }
                                else -> stopPlaybackAndExit()
                            }
                        },
                    )
                }

                if (state.hudOpen) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        TvPlayerHud(
                            title = state.title,
                            positionSec = state.position,
                            durationSec = state.duration,
                            audioTracks = state.audioTracks,
                            videoTracks = state.videoTracks,
                            stats = state.stats,
                            videoFillMode = state.videoFillMode,
                            onSelectAudio = { idx ->
                                val selectedTrack = state.audioTracks
                                    .firstOrNull { it.index == idx }
                                    ?.toVideoTrackEntry()
                                if (selectedTrack != null) {
                                    videoBackend?.selectAudioTrack(selectedTrack)
                                }
                            },
                            onSelectVideo = { _ ->
                                // Selecting a specific video track on a single-stream
                                // playback rarely matters (tracks are equivalent
                                // resolution variants of the same stream); MediaController
                                // doesn't expose a direct setter the way audio/subtitle do,
                                // so we surface the picker for visibility but no-op on tap.
                            },
                            onVideoFillModeChanged = viewModel::onVideoFillModeChanged,
                            audioDelayMs = audioDelayMs,
                            onAudioDelayChanged = viewModel::onAudioDelayChanged,
                            hdrEnabled = hdrEnabled,
                            onHdrEnabledChanged = viewModel::onSetHdrEnabled,
                            chapters = state.chapters,
                            onSelectChapter = { idx ->
                                viewModel.onSeekToChapter(idx)?.let { sec ->
                                    mediaController?.seekTo((sec * 1000).toLong())
                                }
                            },
                            onDismiss = { viewModel.closeHUD() },
                            initialTab = requestedHudTab,
                            modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd),
                        )
                    }
                }

                if (state.showSubtitleMenu) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        TvSubtitleMenu(
                            subtitleTracks = state.subtitleTracks,
                            subtitleDelayMs = subtitleDelayMs,
                            onSelectSubtitle = { idx -> applyTvSubtitleSelection(idx, true) },
                            onSubtitleDelayChanged = viewModel::onSubtitleDelayChanged,
                            onPaneShown = viewModel::onSubtitlesPaneShown,
                            onSearchSubtitles = if (state.mediaFileId != null) {
                                {
                                    viewModel.closeSubtitleMenu()
                                    viewModel.openSubtitleSearchDialog()
                                }
                            } else {
                                null
                            },
                            onTranslateWithAi = if (
                                state.mediaFileId != null &&
                                (aiTranslate.status.enabled || aiTranslate.status.transcribeEnabled)
                            ) {
                                {
                                    viewModel.closeSubtitleMenu()
                                    viewModel.openAiTranslateDialog()
                                }
                            } else {
                                null
                            },
                            onDismiss = viewModel::closeSubtitleMenu,
                            modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd),
                        )
                    }
                }

                if (state.showSubtitleSearchDialog) {
                    TvSubtitleSearchDialog(
                        state = subtitleSearch,
                        onLanguageChanged = viewModel::setSubtitleSearchLanguage,
                        onSearch = viewModel::searchSubtitles,
                        onDownload = viewModel::downloadSubtitle,
                        onDismiss = viewModel::closeSubtitleSearchDialog,
                    )
                }

                if (state.showAiTranslateDialog) {
                    // Translate sources = the session's sidecar subtitle list,
                    // filtered with mobile/web parity (isTranslatableSource):
                    // embedded → any non-bitmap codec (ffmpeg-extractable);
                    // external/downloaded → only server-parseable text formats
                    // (external ASS is rejected by the server). source_index for
                    // the server is PlayerSubtitleInfo.index (the session's
                    // combined subtitle index).
                    val translatableSubtitleSources = remember(state.subtitleUrls) {
                        state.subtitleUrls.filter { isTranslatableSource(it) }
                    }
                    TvAiTranslateDialog(
                        aiState = aiTranslate,
                        subtitleSources = translatableSubtitleSources,
                        audioSources = state.audioTracks,
                        defaultTargetLanguage = state.preferredTextLanguage
                            ?.takeIf { it.isNotBlank() } ?: "en",
                        onSubmit = viewModel::submitAiTranslate,
                        onCancelJob = viewModel::cancelAiTranslateJob,
                        onClearError = viewModel::clearAiTranslateError,
                        onDismiss = viewModel::closeAiTranslateDialog,
                    )
                }
            }
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

        // Watch Together room indicator (top-end so it doesn't collide with
        // the top-start lifecycle notice). Member count, a "Waiting for
        // members…" pill while the room is on the wait barrier, and the join
        // code for the host. Only shown while the idle overlay is up.
        val snapshot = roomSnapshot
        if (roomController != null && snapshot != null && state.showControls && !state.hudOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 32.dp, end = 32.dp),
                contentAlignment = Alignment.TopEnd,
            ) {
                TvRoomIndicator(
                    memberCount = snapshot.memberCount,
                    waiting = snapshot.playbackState == RoomPlaybackState.Waiting,
                    joinCode = snapshot.code.takeIf { snapshot.selfCanManageRoom && it.isNotBlank() },
                )
            }
        }

        // Host close-confirm dialog. Closing tears the room down for everyone
        // (server emits room_closed → every member exits). Cancel resumes.
        if (showLeaveDialog && roomController != null) {
            TvRoomCloseConfirmDialog(
                onClose = {
                    showLeaveDialog = false
                    roomController.leave(closeRoom = true)
                    stopPlaybackAndExit()
                },
                onCancel = { showLeaveDialog = false },
            )
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
    bufferedAheadSec: Double,
    chapters: List<com.continuum.app.model.catalog.VersionChapter>,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onBeginScrub: () -> Unit,
    onUpdateScrub: (Double) -> Unit,
    onCommitScrub: () -> Unit,
    onCancelScrub: () -> Unit,
    transportFocusRequest: Int,
    onOpenHUD: () -> Unit,
    onOpenTracks: () -> Unit,
    onClose: () -> Unit,
    // Watch Together transport authority. Solo playback leaves both true.
    // A guest who can't seek gets a no-op scrubber/skip; a guest who can't
    // play/pause (host_only policy) gets a no-op play/pause.
    transportEnabled: Boolean = true,
    playPauseEnabled: Boolean = true,
) {
    val scrubberFocus = remember { FocusRequester() }
    val playPauseFocus = remember { FocusRequester() }
    var currentRate by remember { mutableStateOf(0) }
    LaunchedEffect(transportFocusRequest) { runCatching { playPauseFocus.requestFocus() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                when (
                    tvPlayerRemoteKeyAction(
                        keyCode = event.nativeKeyEvent.keyCode,
                        action = event.nativeKeyEvent.action,
                        repeatCount = event.nativeKeyEvent.repeatCount,
                    )
                ) {
                    TvPlayerRemoteKeyAction.PlayPause -> {
                        onPlayPause()
                        true
                    }
                    TvPlayerRemoteKeyAction.FocusTransport -> {
                        runCatching { playPauseFocus.requestFocus() }
                        true
                    }
                    TvPlayerRemoteKeyAction.OpenHud -> {
                        onOpenHUD()
                        true
                    }
                    TvPlayerRemoteKeyAction.ConsumeOnly -> true
                    null -> false
                }
            },
    ) {
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
            // Interactive scrubber — capsule track with chapter ticks, ±10s
            // skip, hold-to-auto-seek, and Select to commit. tvOS spec §4.1.
            TvPlayerScrubber(
                positionSec = positionSec,
                durationSec = durationSec,
                bufferedAheadSec = bufferedAheadSec,
                isScrubbing = isScrubbing,
                scrubPreviewSec = scrubPreviewSec,
                chapters = chapters.map {
                    ChapterInfo(
                        timeSec = it.startSeconds,
                        title = it.title.ifBlank { null },
                    )
                },
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
                onBack = onClose,
                onSkipBack = onSkipBack,
                onPlayPause = onPlayPause,
                onSkipForward = onSkipForward,
                onOpenTracks = onOpenTracks,
                onOpenHUD = onOpenHUD,
                playPauseFocus = playPauseFocus,
                onMoveUpToScrubber = {
                    runCatching { scrubberFocus.requestFocus() }
                },
            )
        }

        if (title.isNotBlank()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 80.dp, top = 72.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.42f))
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                androidx.tv.material3.Text(
                    text = title,
                    color = Color.White,
                    style = androidx.tv.material3.MaterialTheme.typography.titleLarge,
                )
                androidx.tv.material3.Text(
                    text = "Playing",
                    color = Color.White.copy(alpha = 0.70f),
                    style = androidx.tv.material3.MaterialTheme.typography.bodyMedium,
                )
            }
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

/**
 * Top-end Watch Together status pill. Shows the live member count, a "Waiting
 * for members…" line while the room sits on the wait barrier, and the join
 * code for a member who can manage the room (host).
 */
@Composable
private fun TvRoomIndicator(
    memberCount: Int,
    waiting: Boolean,
    joinCode: String?,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        androidx.tv.material3.Text(
            text = "Watch Together · $memberCount in room",
            color = Color.White,
            style = androidx.tv.material3.MaterialTheme.typography.labelLarge,
        )
        if (joinCode != null) {
            androidx.tv.material3.Text(
                text = "Code $joinCode",
                color = Color.White.copy(alpha = 0.80f),
                style = androidx.tv.material3.MaterialTheme.typography.labelMedium,
            )
        }
        if (waiting) {
            androidx.tv.material3.Text(
                text = "Waiting for members…",
                color = Color.White.copy(alpha = 0.80f),
                style = androidx.tv.material3.MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/**
 * Host close-confirm dialog. Uses the [TvDialogActionRow] idiom (shared with
 * the subtitle search dialog). "Close room for everyone" tears the room down
 * for all members (server broadcasts room_closed); "Keep watching" resumes.
 */
@Composable
private fun TvRoomCloseConfirmDialog(
    onClose: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.92f))
                .padding(40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            androidx.tv.material3.Text(
                text = "Close this room?",
                color = Color.White,
                style = androidx.tv.material3.MaterialTheme.typography.titleLarge,
            )
            androidx.tv.material3.Text(
                text = "Closing ends Watch Together for everyone in the room.",
                color = Color.White.copy(alpha = 0.80f),
                style = androidx.tv.material3.MaterialTheme.typography.bodyMedium,
            )
            TvDialogActionRow(
                title = "Close room for everyone",
                onClick = onClose,
                modifier = Modifier.width(360.dp),
            )
            TvDialogActionRow(
                title = "Keep watching",
                onClick = onCancel,
                modifier = Modifier.width(360.dp),
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

private fun PlayerTrackEntry.toVideoTrackEntry(): VideoPlayerTrackEntry =
    VideoPlayerTrackEntry(
        index = index,
        label = label,
        language = language,
        isSelected = isSelected,
    )

private const val TAG = "TvPlayerScreen"

/**
 * Flatten an ExoPlayer [Tracks] object into TV-facing entries. Audio/video
 * keep the legacy group-level mapping. Text tracks flatten every format inside
 * each Media3 group because sidecar subtitles can share one group; their
 * `index` is the flat text-track ordinal expected by [SubtitleManager].
 */
internal fun extractTrackEntries(tracks: Tracks, type: Int): List<PlayerTrackEntry> {
    val result = mutableListOf<PlayerTrackEntry>()
    var groupIndex = 0
    var flatTextIndex = 0
    for (group in tracks.groups) {
        if (group.type != type) continue
        val mediaGroup = group.mediaTrackGroup
        if (type == C.TRACK_TYPE_TEXT) {
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val label = format.label.orEmpty().ifBlank { format.language?.uppercase() ?: "" }
                result.add(
                    PlayerTrackEntry(
                        index = flatTextIndex,
                        label = label,
                        language = format.language,
                        isSelected = group.isTrackSelected(trackIndex),
                        displayLabel = formatSubtitleTrackDisplayLabel(
                            rawLabel = label,
                            language = format.language,
                            codecOrMime = format.sampleMimeType ?: format.codecs,
                            isForced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0,
                            index = flatTextIndex,
                        ),
                    ),
                )
                flatTextIndex++
            }
            continue
        }
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
                displayLabel = label,
            ),
        )
        groupIndex++
    }
    return result
}
