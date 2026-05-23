package com.continuum.app.android.ui.screens.player

import android.app.Activity
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import com.continuum.app.common.player.AudioCapabilityManager
import com.continuum.app.common.player.ContinuumPlaybackService
import com.continuum.app.common.player.ContinuumPlayerFactory
import com.continuum.app.common.player.DisplayHdrProbe
import com.continuum.app.common.player.PlaybackCapabilityDetector
import com.continuum.app.common.player.PlaybackPreflightListener
import com.continuum.app.common.player.RefreshRateMatcher
import com.continuum.app.common.player.SubtitleManager
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.koin.compose.koinInject

/**
 * Full-screen video player screen.
 *
 * The actual ExoPlayer lives inside [ContinuumPlaybackService]; this screen
 * drives it via a [MediaController] so the system sees a single session
 * (lock-screen controls, Assistant, headset buttons). The controls overlay
 * is [PlayerOverlay].
 *
 * @param contentId The content ID to play (passed via navigation argument)
 * @param initialFileId Optional explicit file selection from the detail screen
 * @param initialAudioTrackIndex Optional explicit audio selection from the detail screen
 * @param initialSubtitleTrackIndex Optional explicit subtitle selection from the detail screen
 * @param navController Navigation controller for back navigation
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    contentId: String,
    initialFileId: Int? = null,
    initialAudioTrackIndex: Int? = null,
    initialSubtitleTrackIndex: Int? = null,
    navController: NavHostController,
    viewModel: PlayerViewModel = koinInject(),
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val playerFactory: ContinuumPlayerFactory = koinInject()
    val audioCapabilityManager: AudioCapabilityManager = koinInject()
    val capabilityDetector: PlaybackCapabilityDetector = koinInject()
    val subtitleManager = remember { SubtitleManager() }
    val displayHdr = remember { DisplayHdrProbe.probe(context) }
    val refreshRateMatcher = remember { RefreshRateMatcher() }
    val audioCaps by audioCapabilityManager.capabilities.collectAsState()

    // MediaController connection is async — it returns a ListenableFuture that
    // resolves when the service binds. We hold the controller in a compose
    // state so downstream effects can re-run once it's ready.
    var mediaController by remember { mutableStateOf<MediaController?>(null) }

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
            // Stop and clear media items before releasing the controller —
            // releasing the controller alone leaves the service's player
            // running, so audio keeps playing after the screen exits.
            // Mirrors TvPlayerScreen.stopPlaybackAndExit semantics.
            mediaController?.let { controller ->
                runCatching {
                    controller.pause()
                    controller.stop()
                    controller.clearMediaItems()
                }
                controller.release()
            }
            mediaController = null
            if (!future.isDone) future.cancel(true)
        }
    }

    val hdrEnabled by viewModel.hdrEnabled.collectAsState()

    // Apply capability-aware track selection presets. Re-runs on capability
    // or profile-language change so a mid-session HDMI hot-plug / Bluetooth
    // pair rebuilds the preferred-MIME ordering. When the user has disabled
    // HDR via the playback settings sheet, we hand the presets builder an
    // empty HdrCapabilities so the track selector stops preferring HDR
    // tracks (and the refresh-rate matcher's HDR branches stay quiet).
    LaunchedEffect(
        mediaController,
        audioCaps,
        uiState.preferredAudioLanguage,
        uiState.preferredTextLanguage,
        hdrEnabled,
    ) {
        val controller = mediaController ?: return@LaunchedEffect
        playerFactory.applyTrackSelectionPresets(
            player = controller,
            audioCaps = audioCaps,
            displayHdr = if (hdrEnabled) displayHdr else com.continuum.app.model.playback.HdrCapabilities(),
            preferredAudioLanguage = uiState.preferredAudioLanguage,
            preferredTextLanguage = uiState.preferredTextLanguage,
        )
    }

    // Mirror the user's preferred playback speed onto the live MediaController.
    // Re-runs whenever the controller binds (so the value is applied after
    // service rebind) or the user picks a new speed in PlayerSettingsSheet.
    LaunchedEffect(mediaController) {
        val controller = mediaController ?: return@LaunchedEffect
        viewModel.playbackSpeed.collect { speed ->
            controller.setPlaybackSpeed(speed.toFloat())
        }
    }

    // Load content on first composition
    LaunchedEffect(contentId, initialFileId, initialAudioTrackIndex, initialSubtitleTrackIndex) {
        viewModel.loadContent(
            contentId = contentId,
            preferredFileId = initialFileId,
            initialAudioTrackIndex = initialAudioTrackIndex,
            initialSubtitleTrackIndex = initialSubtitleTrackIndex,
        )
    }

    // Immersive mode: hide system bars
    DisposableEffect(activity) {
        if (activity != null) {
            val window = activity.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            val originalOrientation = activity.requestedOrientation
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            refreshRateMatcher.attach(activity)

            onDispose {
                refreshRateMatcher.restore()
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = originalOrientation
            }
        } else {
            onDispose { }
        }
    }

    // Set up the media item when stream URL becomes available
    LaunchedEffect(mediaController, uiState.streamUrl, uiState.playMethod) {
        val controller = mediaController ?: return@LaunchedEffect
        val streamUrl = uiState.streamUrl ?: return@LaunchedEffect
        val playMethod = uiState.playMethod ?: return@LaunchedEffect
        val serverUrl = uiState.serverUrl
        if (serverUrl.isEmpty()) return@LaunchedEffect

        val mediaItem = playerFactory.buildMediaItem(
            streamUrl = streamUrl,
            playMethod = playMethod,
            serverUrl = serverUrl,
            subtitles = uiState.subtitleTracks,
        )

        controller.setMediaItem(mediaItem)
        controller.prepare()

        val startMs = (uiState.startPosition * 1000).toLong()
        if (startMs > 0) controller.seekTo(startMs)

        controller.playWhenReady = true
    }

    // Sync play/pause from ViewModel to player
    LaunchedEffect(mediaController, uiState.isPaused) {
        mediaController?.playWhenReady = !uiState.isPaused
    }

    // Preflight listener: evaluates the resolved Tracks and triggers the
    // transcode fallback when the selected track combo can't actually be
    // played on this device.
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

    // Player event listener to feed state back to ViewModel + track video size for PiP
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
                    viewModel.onBufferingChanged(playbackState == Player.STATE_BUFFERING)
                    if (playbackState == Player.STATE_ENDED) {
                        viewModel.onPlayingChanged(false)
                    }
                }

                override fun onVideoSizeChanged(size: VideoSize) {
                    if (size.width > 0 && size.height > 0) {
                        // Pull frame rate off the selected video track; phone
                        // panels with multiple refresh rates switch to
                        // content-matching (seamless only — see ExoPlayer's
                        // VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS).
                        val frameRate = controller.currentTracks.groups
                            .firstOrNull {
                                it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO && it.isSelected
                            }
                            ?.let { g ->
                                val mg = g.mediaTrackGroup
                                if (mg.length > 0) mg.getFormat(0).frameRate else 0f
                            } ?: 0f
                        if (frameRate > 0f) {
                            refreshRateMatcher.applyForFrameRate(frameRate)
                        }
                    }
                }
            }
            controller.addListener(listener)
            onDispose { controller.removeListener(listener) }
        }
    }

    // Lifecycle-bounded position ticker. Cancels when the screen pauses or
    // the controller disconnects, so we don't hit a released Player.
    LaunchedEffect(mediaController, lifecycleOwner) {
        val controller = mediaController ?: return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                if (controller.isPlaying || controller.playbackState == Player.STATE_BUFFERING) {
                    viewModel.onPositionChanged(controller.currentPosition, controller.duration)
                }
                delay(500)
            }
        }
    }

    // Handle seek commands from ViewModel
    LaunchedEffect(mediaController, uiState.position) {
        val controller = mediaController ?: return@LaunchedEffect
        val playerPosSec = controller.currentPosition / 1000.0
        val requestedPos = uiState.position
        if (kotlin.math.abs(playerPosSec - requestedPos) > 2.0) {
            controller.seekTo((requestedPos * 1000).toLong())
        }
    }

    // Handle subtitle selection
    LaunchedEffect(mediaController, uiState.selectedSubtitleIndex) {
        val controller = mediaController ?: return@LaunchedEffect
        subtitleManager.selectSubtitle(controller, uiState.selectedSubtitleIndex)
    }

    // Notify the ViewModel we're leaving the screen.
    DisposableEffect(Unit) {
        onDispose { viewModel.onExit() }
    }

    // UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else if (uiState.error != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = uiState.error ?: "Unknown error",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            val controller = mediaController
            val videoGravity by viewModel.videoGravity.collectAsState()
            val resizeMode = when (videoGravity) {
                "fill" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                "stretch" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
            val subtitleAppearance by viewModel.subtitleAppearance.collectAsState()

            // Re-apply user subtitle styling whenever the PlayerView mounts or the
            // appearance flow emits a new value. The PlayerView's `subtitleView`
            // is a child added on first inflation, so the apply must happen at
            // least once after the AndroidView factory runs.
            LaunchedEffect(playerViewRef, subtitleAppearance) {
                val pv = playerViewRef ?: return@LaunchedEffect
                subtitleManager.applyAppearance(pv, subtitleAppearance)
            }

            if (controller != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            this.resizeMode = resizeMode
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            playerViewRef = this
                        }
                    },
                    update = { view ->
                        view.player = controller
                        view.resizeMode = resizeMode
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            PlayerOverlay(
                state = uiState,
                viewModel = viewModel,
                onBack = {
                    viewModel.onExit()
                    navController.popBackStack()
                },
                onPlayPause = { viewModel.onPlayPause() },
                onSeek = { position ->
                    viewModel.onSeek(position)
                    mediaController?.seekTo((position * 1000).toLong())
                },
                onToggleControls = { viewModel.onToggleControls() },
                onNextEpisode = { viewModel.onNextEpisode() },
                onSelectSubtitle = { viewModel.onSelectSubtitle(it) },
                onSelectAudio = { viewModel.onSelectAudio(it) },
                onSelectVersion = { viewModel.onSelectVersion(it) },
            )
        }
    }
}
