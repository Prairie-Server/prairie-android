package com.continuum.app.common.player

import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.continuum.app.common.BuildConfig
import org.koin.android.ext.android.inject
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unified playback service for phone + TV. Owns the single [ExoPlayer] for the
 * process and exposes it through a [MediaSession] so both UIs can drive the
 * player via a `MediaController` — which is what gives us lock-screen controls,
 * Assistant "pause"/"play", headset buttons, and (on TV) a session entry in
 * `dumpsys media_session`.
 *
 * Keep this class free of UI-layer state; the surface towards the UI is the
 * `Player` / `MediaSession` contract plus the [positionMs] flow below.
 */
@UnstableApi
class ContinuumPlaybackService : MediaSessionService() {

    companion object {
        /**
         * Debug-only counter so we can assert "exactly one ExoPlayer per
         * process" in tests / logcat. Read via adb logcat on tag [TAG].
         */
        private val playerInstanceCount = AtomicInteger(0)
        private const val TAG = "ContinuumPlayback"
        private const val POSITION_TICK_MS = 500L
    }

    private val playerFactory: ContinuumPlayerFactory by inject()
    private val analyticsListener: PlaybackAnalyticsListener by inject()

    private var mediaSession: MediaSession? = null
    private lateinit var scope: CoroutineScope
    private var positionJob: Job? = null

    private val _positionMs = MutableStateFlow(0L)

    /**
     * Current player position in ms, ticked every [POSITION_TICK_MS]. UI
     * layers subscribe via `collectAsStateWithLifecycle()` rather than
     * polling `currentPosition` on every recomposition — that keeps
     * render-driven coroutines out of the hot path and matches how the
     * service's own lifecycle bounds the flow.
     */
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        val player = playerFactory.createPlayer()
        player.addAnalyticsListener(analyticsListener)
        val count = playerInstanceCount.incrementAndGet()
        android.util.Log.i(TAG, "ExoPlayer created; live instance count = $count")
        // Triage aid: when diagnosing TRANSCODE vs DIRECT decisions we want
        // the logcat to show the player's FFmpeg-audio mode alongside the
        // analytics listener's `onAudioDecoderInitialized` callback, which
        // reports the specific decoder the renderer selected.
        android.util.Log.i(
            TAG,
            "FFmpeg audio preferred = ${BuildConfig.FFMPEG_AUDIO_ENABLED}, " +
                "extension on classpath = ${FfmpegAudioSupport.isAvailable()}",
        )

        mediaSession = MediaSession.Builder(this, player).build()

        positionJob = scope.launch {
            while (isActive) {
                _positionMs.value = player.currentPosition
                delay(POSITION_TICK_MS)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Continuum is a video player — when the user swipes the app away
        // we want playback (and audio) to end, not continue in the
        // background like a music app. Always tear down regardless of
        // playWhenReady. The previous "only stop when paused" check was
        // the Media3 music-app default and left audio orphaned.
        mediaSession?.player?.run {
            playWhenReady = false
            stop()
            clearMediaItems()
        }
        stopSelf()
    }

    override fun onDestroy() {
        positionJob?.cancel()
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        val count = playerInstanceCount.decrementAndGet()
        android.util.Log.i(TAG, "ExoPlayer released; live instance count = $count")
        super.onDestroy()
    }
}
