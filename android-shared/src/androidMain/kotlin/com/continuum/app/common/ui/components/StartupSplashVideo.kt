package com.continuum.app.common.ui.components

import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.continuum.app.common.R
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Initial app-loading animation shown before the root navigation graph is ready.
 */
@OptIn(UnstableApi::class)
@Composable
fun StartupSplashVideo(
    modifier: Modifier = Modifier,
    resizeMode: StartupSplashResizeMode = StartupSplashResizeMode.Fit,
    backgroundColor: Color = Color(0xFF050505),
    onPlaybackComplete: () -> Unit = {},
) {
    val context = LocalContext.current
    val currentOnPlaybackComplete = rememberUpdatedState(onPlaybackComplete)
    val completionDispatched = remember { AtomicBoolean(false) }
    val player = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            volume = 0f
            setMediaItem(
                MediaItem.fromUri(
                    RawResourceDataSource.buildRawResourceUri(R.raw.startup_splash),
                ),
            )
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    completionDispatched.dispatchOnce(currentOnPlaybackComplete.value)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                completionDispatched.dispatchOnce(currentOnPlaybackComplete.value)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.resizeMode = resizeMode.toPlayerViewResizeMode()
                    setShutterBackgroundColor(AndroidColor.TRANSPARENT)
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    this.player = player
                }
            },
            update = { view ->
                view.player = player
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

enum class StartupSplashResizeMode {
    Fit,
    Crop,
}

@OptIn(UnstableApi::class)
private fun StartupSplashResizeMode.toPlayerViewResizeMode(): Int =
    when (this) {
        StartupSplashResizeMode.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        StartupSplashResizeMode.Crop -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    }

private fun AtomicBoolean.dispatchOnce(block: () -> Unit) {
    if (compareAndSet(false, true)) block()
}
