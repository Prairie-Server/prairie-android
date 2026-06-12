package com.continuum.app.tv.ui.screens.audiobook

import android.content.ComponentName
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.continuum.app.common.player.AudiobookPlayerViewModel
import com.continuum.app.common.player.ContinuumPlaybackService
import com.continuum.app.tv.ui.components.TvErrorScreen
import com.continuum.app.tv.ui.components.TvPoster
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

private enum class AudiobookPanel { None, Chapters, Speed, Sleep, Skip }

/**
 * 10-foot, D-pad audiobook player for Android TV. A thin focus/layout view over
 * the SHARED [AudiobookPlayerViewModel] (android-shared) — no chapter / sleep /
 * speed / playback logic is duplicated here. Binds a Media3 [MediaController] to
 * the shared [ContinuumPlaybackService] exactly like the phone screen and the
 * video [com.continuum.app.tv.ui.screens.player.TvPlayerScreen].
 */
@Composable
fun TvAudiobookPlayerScreen(
    onExit: () -> Unit,
    viewModel: AudiobookPlayerViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val currentChapterIndex by viewModel.currentChapterIndex.collectAsState()
    val chapterCountLabel by viewModel.chapterCountLabel.collectAsState()
    val context = LocalContext.current

    var controller by remember { mutableStateOf<MediaController?>(null) }
    var activePanel by remember { mutableStateOf(AudiobookPanel.None) }
    val playPauseFocus = remember { FocusRequester() }
    val speedChipFocus = remember { FocusRequester() }

    // MediaController binds async to the shared service — the same one the video
    // player uses, so a single MediaSession owns foreground audio.
    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, ContinuumPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                if (future.isDone && !future.isCancelled) {
                    controller = runCatching { future.get() }.getOrNull()
                }
            },
            MoreExecutors.directExecutor(),
        )
        onDispose {
            controller?.let { c ->
                runCatching { c.pause(); c.stop(); c.clearMediaItems() }
                c.release()
            }
            controller = null
            if (!future.isDone) future.cancel(true)
        }
    }

    // Wire the stream URL into Media3 once the controller + URL resolve; rebuild
    // on URL change. Resume-on-open seeks to the saved position before play.
    LaunchedEffect(controller, state.streamUrl) {
        val c = controller ?: return@LaunchedEffect
        val url = state.streamUrl ?: return@LaunchedEffect
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(state.title)
                    .setArtist(state.author ?: state.narrator)
                    .also { mb ->
                        state.coverUrl?.takeIf { it.isNotBlank() }?.let { mb.setArtworkUri(Uri.parse(it)) }
                    }
                    .build(),
            )
            .build()
        // Resume position goes on the media item's start position, not a
        // post-prepare seekTo (which is dropped before the timeline window is
        // known, leaving the book at 0:00).
        val resume = viewModel.resumePositionSeconds.value
        val startMs = ((resume ?: 0.0).coerceAtLeast(0.0) * 1000).toLong()
        c.setMediaItem(mediaItem, startMs)
        c.prepare()
        if (resume != null && resume > 0) viewModel.consumeResumePosition()
        c.playWhenReady = true
    }

    LaunchedEffect(controller, state.isPaused) {
        if (state.isPaused) viewModel.flushPosition()
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.flushPosition()
            viewModel.stopPlaybackSession()
        }
    }

    DisposableEffect(controller) {
        val c = controller
        if (c == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    viewModel.onPlayingChanged(isPlaying)
                }

                // Pause intent — distinct from transient buffering. Without
                // this a seek's rebuffer would latch the book paused.
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    viewModel.onPauseStateChanged(!playWhenReady)
                }
            }
            c.addListener(listener)
            onDispose { runCatching { c.removeListener(listener) } }
        }
    }

    // 4 Hz position poll (MediaController doesn't push position updates).
    LaunchedEffect(controller) {
        while (true) {
            controller?.let { viewModel.onPositionChanged(it.currentPosition / 1000.0) }
            delay(250)
        }
    }
    LaunchedEffect(controller, state.isPaused) { controller?.playWhenReady = !state.isPaused }
    LaunchedEffect(controller, state.playbackSpeed) {
        controller?.playbackParameters = PlaybackParameters(state.playbackSpeed)
    }
    val pendingSeek by viewModel.pendingSeekToSeconds.collectAsState()
    LaunchedEffect(controller, pendingSeek) {
        val seconds = pendingSeek ?: return@LaunchedEffect
        controller?.seekTo((seconds * 1000).toLong())
        viewModel.consumePendingSeek()
    }

    // Initial / restored focus lands on play/pause when no panel is open.
    LaunchedEffect(state.isLoading, activePanel) {
        if (!state.isLoading && activePanel == AudiobookPanel.None) {
            runCatching { playPauseFocus.requestFocus() }
        }
    }

    BackHandler(enabled = true) {
        if (activePanel != AudiobookPanel.None) activePanel = AudiobookPanel.None else onExit()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
            state.error != null -> TvErrorScreen(message = state.error!!)
            else -> {
                val hasChapters = state.chapters.size > 1
                Row(modifier = Modifier.fillMaxSize().padding(64.dp)) {
                    TvPoster(
                        imageUrl = state.coverUrl,
                        contentDescription = state.title,
                        modifier = Modifier.size(340.dp),
                        cornerRadius = 12.dp,
                    )
                    Spacer(Modifier.width(56.dp))
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(state.title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                        (state.author ?: state.narrator)?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.75f),
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        if (hasChapters && chapterCountLabel.isNotBlank()) {
                            Text(
                                text = chapterCountLabel,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White.copy(alpha = 0.85f),
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        TvAudiobookProgressBar(
                            fraction = (state.positionSeconds / state.durationSeconds.coerceAtLeast(1.0)).toFloat(),
                            positionLabel = formatAudiobookTime(state.positionSeconds),
                            durationLabel = formatAudiobookTime(state.durationSeconds),
                        )
                        Spacer(Modifier.height(28.dp))
                        TvAudiobookTransportRow(
                            modifier = Modifier.focusProperties { down = speedChipFocus },
                            // Pause intent, not transient isPlaying, so the icon
                            // stays stable through a seek's rebuffer.
                            isPlaying = !state.isPaused,
                            chaptersEnabled = hasChapters,
                            skipBackSeconds = state.skipBackSeconds,
                            skipForwardSeconds = state.skipForwardSeconds,
                            onPrevChapter = { viewModel.skipToPreviousChapter() },
                            onSkipBack = { viewModel.skipBack() },
                            onPlayPause = { viewModel.togglePlay() },
                            onSkipForward = { viewModel.skipForward() },
                            onNextChapter = { viewModel.skipToNextChapter() },
                            playPauseFocus = playPauseFocus,
                        )
                        Spacer(Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.focusProperties { up = playPauseFocus },
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            TvAudiobookChip(
                                label = "${state.playbackSpeed}×",
                                focusRequester = speedChipFocus,
                                onClick = { activePanel = AudiobookPanel.Speed },
                            )
                            TvAudiobookChip(
                                label = "${state.skipBackSeconds}s",
                                onClick = { activePanel = AudiobookPanel.Skip },
                            )
                            TvAudiobookChip(
                                label = "Sleep",
                                onClick = { activePanel = AudiobookPanel.Sleep },
                            )
                            if (hasChapters) {
                                TvAudiobookChip(
                                    label = "Chapters",
                                    onClick = { activePanel = AudiobookPanel.Chapters },
                                )
                            }
                        }
                    }
                }
            }
        }

        when (activePanel) {
            AudiobookPanel.Chapters -> TvAudiobookChaptersPanel(
                chapters = state.chapters,
                currentChapterIndex = currentChapterIndex,
                onSelectChapter = { idx ->
                    state.chapters.getOrNull(idx)?.let { viewModel.jumpToChapter(it) }
                    activePanel = AudiobookPanel.None
                },
            )
            AudiobookPanel.Speed -> TvAudiobookSpeedPanel(
                currentSpeed = state.playbackSpeed,
                // Fine-adjust / presets apply live and stay open so the user
                // can keep tuning; "Set as default" persists and closes.
                onSelectSpeed = { viewModel.setSpeed(it) },
                onSetDefault = { viewModel.setDefaultSpeed(it); activePanel = AudiobookPanel.None },
            )
            AudiobookPanel.Skip -> TvAudiobookSkipIntervalPanel(
                skipBackSeconds = state.skipBackSeconds,
                skipForwardSeconds = state.skipForwardSeconds,
                onSelectSkipBack = { viewModel.setSkipBackSeconds(it) },
                onSelectSkipForward = { viewModel.setSkipForwardSeconds(it) },
            )
            AudiobookPanel.Sleep -> {
                val sleepChoice by viewModel.sleepTimerChoice.collectAsState()
                TvAudiobookSleepPanel(
                    currentChoice = sleepChoice,
                    onSelectSleep = { viewModel.applySleepTimer(it); activePanel = AudiobookPanel.None },
                )
            }
            AudiobookPanel.None -> Unit
        }
    }
}

@Composable
private fun TvAudiobookProgressBar(
    fraction: Float,
    positionLabel: String,
    durationLabel: String,
) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.25f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(positionLabel, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
            Text(durationLabel, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun TvAudiobookChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val bg = if (isFocused) Color.White else Color.White.copy(alpha = 0.12f)
    val fg = if (isFocused) Color.Black else Color.White
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .let { mod -> if (focusRequester != null) mod.focusRequester(focusRequester) else mod }
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { onClick(); true }
                    else -> false
                }
            }
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

