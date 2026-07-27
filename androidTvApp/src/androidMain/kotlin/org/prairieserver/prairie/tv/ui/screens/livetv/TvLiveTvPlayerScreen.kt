package org.prairieserver.prairie.tv.ui.screens.livetv

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.prairieserver.prairie.common.player.PrairiePlayerFactory
import org.prairieserver.prairie.model.playback.PlayMethod
import org.prairieserver.prairie.network.TokenManager
import org.prairieserver.prairie.viewmodel.LiveTvPlayerViewModel

@OptIn(UnstableApi::class)
@Composable
fun TvLiveTvPlayerScreen(
    channelId: String,
    channelName: String,
    onBack: () -> Unit,
    viewModel: LiveTvPlayerViewModel = koinViewModel(),
    playerFactory: PrairiePlayerFactory = koinInject(),
    tokenManager: TokenManager = koinInject(),
) {
    val state by viewModel.uiState.collectAsState()
    val onStop = rememberUpdatedState(newValue = { viewModel.stop() })

    LaunchedEffect(channelId) {
        viewModel.start(channelId, channelName)
    }

    DisposableEffect(Unit) {
        onDispose { onStop.value() }
    }

    BackHandler {
        viewModel.stop()
        onBack()
    }

    val player = remember {
        playerFactory.createPlayer().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    LaunchedEffect(state.session?.sessionId, state.session?.playableUrl, state.session?.transport) {
        val session = state.session ?: return@LaunchedEffect
        val url = session.playableUrl
        if (url.isBlank()) return@LaunchedEffect
        val serverUrl = tokenManager.getServerUrl()
        val mediaItem = if (session.isHls) {
            playerFactory.buildMediaItem(
                streamUrl = url,
                playMethod = PlayMethod.TRANSCODE,
                serverUrl = serverUrl,
                title = channelName.ifBlank { "Live TV" },
            )
        } else {
            playerFactory.buildMediaItem(
                streamUrl = url,
                playMethod = PlayMethod.DIRECT,
                container = "mpegts",
                serverUrl = serverUrl,
                title = channelName.ifBlank { "Live TV" },
            )
        }
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when {
            state.isStarting && state.session == null -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                )
            }
            state.error != null && state.session == null -> {
                Text(
                    text = state.error ?: "Playback failed",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
            }
            state.session != null -> {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = true
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            this.player = player
                        }
                    },
                    update = { view -> view.player = player },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
