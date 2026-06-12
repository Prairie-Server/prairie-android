package com.continuum.app.android.ui.screens.audiobook

import android.content.ComponentName
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.continuum.app.android.ui.util.formatClockTime
import com.continuum.app.common.player.AudiobookPlayerViewModel
import com.continuum.app.common.player.ContinuumPlaybackService
import com.continuum.app.common.ui.components.ThumbhashImage
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

/**
 * Dedicated audiobook player UI. Big square cover up top, then title /
 * author / narrator, then the position slider, then transport row
 * (±30s, big play/pause), then speed + sleep timer chips.
 *
 * Media3 wiring happens here (not in the ViewModel) so the controller
 * lifecycle is tied to the Compose tree — same pattern as the video
 * PlayerScreen. The controller binds to the shared playback service and
 * plays either an offline original file URI or a server playback stream.
 */
@Composable
fun AudiobookPlayerScreen(
    onBackClick: () -> Unit,
    viewModel: AudiobookPlayerViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val currentChapterIndex by viewModel.currentChapterIndex.collectAsState()
    val chapterCountLabel by viewModel.chapterCountLabel.collectAsState()
    val context = LocalContext.current

    // MediaController binds asynchronously to the shared playback service;
    // the same one the video player uses, so a single MediaSession owns
    // foreground audio at any time (system controls / notifications stay
    // consistent).
    var controller by remember { mutableStateOf<MediaController?>(null) }
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

    // Wire the stream URL into Media3 once both the controller and the
    // metadata have resolved. We deliberately rebuild the MediaItem when
    // streamUrl changes so a navigation from one audiobook to another
    // doesn't leave the previous bytes playing. Resume-on-open is
    // applied here too: if the VM resolved a saved position, seek to
    // it before play.
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
                        state.coverUrl?.takeIf { it.isNotBlank() }
                            ?.let { mb.setArtworkUri(android.net.Uri.parse(it)) }
                    }
                    .build(),
            )
            .build()
        c.setMediaItem(mediaItem)
        c.prepare()
        val resume = viewModel.resumePositionSeconds.value
        if (resume != null && resume > 0) {
            c.seekTo((resume * 1000).toLong())
            viewModel.consumeResumePosition()
        }
        c.playWhenReady = true
    }

    // Flush position on every meaningful transition so a process kill
    // loses at most a couple of seconds. Periodic save inside the VM
    // covers the in-flight case.
    LaunchedEffect(controller, state.isPaused) {
        if (state.isPaused) viewModel.flushPosition()
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.flushPosition()
            viewModel.stopPlaybackSession()
        }
    }

    // Listener bridge — Player state changes go back into the VM so the UI
    // is always a function of Media3's truth.
    DisposableEffect(controller) {
        val c = controller
        if (c == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    viewModel.onPlayingChanged(isPlaying)
                }
            }
            c.addListener(listener)
            onDispose { runCatching { c.removeListener(listener) } }
        }
    }

    // Position poller — MediaController doesn't push position updates;
    // sample at 4Hz which is smooth enough for an audiobook slider.
    LaunchedEffect(controller) {
        while (true) {
            controller?.let { c ->
                viewModel.onPositionChanged(c.currentPosition / 1000.0)
            }
            delay(250)
        }
    }

    // Mirror play/pause toggle into the controller.
    LaunchedEffect(controller, state.isPaused) {
        controller?.playWhenReady = !state.isPaused
    }

    // Apply speed when the user cycles.
    LaunchedEffect(controller, state.playbackSpeed) {
        controller?.playbackParameters = PlaybackParameters(state.playbackSpeed)
    }

    // Apply seeks requested by the VM (chapter jumps, ±30s).
    val pendingSeek by viewModel.pendingSeekToSeconds.collectAsState()
    LaunchedEffect(controller, pendingSeek) {
        val seconds = pendingSeek ?: return@LaunchedEffect
        controller?.seekTo((seconds * 1000).toLong())
        viewModel.consumePendingSeek()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // Back row + bookmark icon (top-right).
        var showBookmarksSheet by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { showBookmarksSheet = true }) {
                Icon(Icons.Filled.Bookmark, contentDescription = "Bookmarks")
            }
        }
        if (showBookmarksSheet) {
            val bookmarks by viewModel.bookmarks.collectAsState()
            AudiobookBookmarksSheet(
                bookmarks = bookmarks,
                onJumpTo = { viewModel.jumpToBookmark(it); showBookmarksSheet = false },
                onDelete = { viewModel.removeBookmark(it.id) },
                onAddCurrent = { viewModel.addBookmark() },
                onDismiss = { showBookmarksSheet = false },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        AudiobookCoverHeader(
            title = state.title,
            author = state.author,
            narrator = state.narrator,
            coverUrl = state.coverUrl,
            coverThumbhash = state.coverThumbhash,
        )

        Spacer(modifier = Modifier.height(20.dp))

        ChapterProgressBar(
            chapters = state.chapters,
            currentChapterIndex = currentChapterIndex,
            chapterCountLabel = chapterCountLabel,
            positionSeconds = state.positionSeconds,
            durationSeconds = state.durationSeconds,
            onSeek = { viewModel.seekTo(it) },
        )

        Spacer(modifier = Modifier.height(20.dp))

        AudiobookTransport(
            isPlaying = state.isPlaying,
            enabled = state.streamUrl != null,
            hasChapters = state.chapters.isNotEmpty(),
            onPrevChapter = { viewModel.skipToPreviousChapter() },
            onSkipBack = { viewModel.seekBy(-30.0) },
            onTogglePlay = { viewModel.togglePlay() },
            onSkipForward = { viewModel.seekBy(30.0) },
            onNextChapter = { viewModel.skipToNextChapter() },
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Speed + Sleep chips — both open dedicated bottom sheets.
        var showSpeedSheet by remember { mutableStateOf(false) }
        var showSleepSheet by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ChipButton(
                icon = Icons.Filled.Speed,
                label = "${formatSpeed(state.playbackSpeed)}x",
                onClick = { showSpeedSheet = true },
            )
            ChipButton(
                icon = Icons.Filled.Bedtime,
                label = state.sleepTimerMinutesLeft?.let { "${it} min" } ?: "Sleep",
                onClick = { showSleepSheet = true },
            )
        }

        if (showSpeedSheet) {
            AudiobookSpeedSheet(
                currentSpeed = state.playbackSpeed,
                onSpeedChanged = viewModel::setSpeed,
                onDismiss = { showSpeedSheet = false },
            )
        }
        if (showSleepSheet) {
            val choice by viewModel.sleepTimerChoice.collectAsState()
            AudiobookSleepTimerSheet(
                currentChoice = choice,
                minutesLeft = state.sleepTimerMinutesLeft,
                onChoiceSelected = viewModel::applySleepTimer,
                onDismiss = { showSleepSheet = false },
            )
        }

        // Chapter list — scrolls below the transport.
        if (state.chapters.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Chapters",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Simple Column of chapters; for very long audiobooks the
            // parent screen scroll handles overflow.
            Column {
                state.chapters.forEach { chapter ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.jumpToChapter(chapter) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${chapter.index + 1}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(width = 32.dp, height = 16.dp),
                        )
                        Text(
                            text = chapter.title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Loading / error states.
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading audiobook…")
            }
        }
        state.error?.let { err ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = err, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ChipButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() else "%.2f".format(speed).trimEnd('0').trimEnd('.')
