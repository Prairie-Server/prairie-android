package com.continuum.app.common.player

import android.content.Intent
import android.os.Build
import androidx.media3.common.Player
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.continuum.app.common.BuildConfig
import com.continuum.app.common.player.audio.DelayAudioProcessor
import com.continuum.app.common.player.subtitle.SubtitleOffsetHolder
import com.continuum.app.common.settings.PlayerSettingsStore
import org.koin.android.ext.android.inject
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unified playback service for phone + TV. Owns the single [Player] for the
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
         * Debug-only counter so we can assert "exactly one playback player per
         * process" in tests / logcat. Read via adb logcat on tag [TAG].
         */
        private val playerInstanceCount = AtomicInteger(0)
        private const val TAG = "ContinuumPlayback"
        private const val POSITION_TICK_MS = 500L
    }

    private val playerFactory: ContinuumPlayerFactory by inject()
    private val analyticsListener: PlaybackAnalyticsListener by inject()
    private val playerSettingsStore: PlayerSettingsStore by inject()
    private val delayProcessor: DelayAudioProcessor by inject()
    private val subtitleOffsetHolder: SubtitleOffsetHolder by inject()

    private var mediaSession: MediaSession? = null
    private lateinit var scope: CoroutineScope
    private var positionJob: Job? = null
    private var audioSyncJob: Job? = null
    private var subtitleSyncJob: Job? = null

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

        val player = createPlaybackPlayer()
        if (player is ExoPlayer) {
            player.addAnalyticsListener(analyticsListener)
        }
        val count = playerInstanceCount.incrementAndGet()
        android.util.Log.i(
            TAG,
            "Playback player created (${player::class.java.simpleName}); live instance count = $count",
        )
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

        // Mirror the per-profile AudioSyncMs preference into the active
        // DelayAudioProcessor. The processor only acts on its pending
        // value at the next flush, so when playback is active we force one
        // via a no-op seekTo(currentPosition) — that's the cheapest way to
        // re-engage the audio pipeline without resetting the renderer.
        audioSyncJob = scope.launch {
            playerSettingsStore.audioSyncMsFlow
                .distinctUntilChanged()
                .collect { delayMs ->
                    val previous = delayProcessor.getActiveDelayMs()
                    delayProcessor.setDelayMs(delayMs)
                    if (previous != delayMs && player.isPlaying) {
                        player.seekTo(player.currentPosition)
                    }
                }
        }

        // Mirror the per-profile SubtitleSyncMs preference into the active
        // SubtitleOffsetHolder. OffsetSubtitleParserFactory reads the holder
        // on every parse, but already-emitted cues stay in the text-renderer
        // buffer — a seekTo(currentPosition) drops them so the new offset
        // applies to the next batch.
        subtitleSyncJob = scope.launch {
            playerSettingsStore.subtitleSyncMsFlow
                .distinctUntilChanged()
                .collect { offsetMs ->
                    val previous = subtitleOffsetHolder.getOffsetMs()
                    subtitleOffsetHolder.setOffsetMs(offsetMs)
                    if (previous != offsetMs && player.isPlaying) {
                        player.seekTo(player.currentPosition)
                    }
                }
        }
    }

    private fun createPlaybackPlayer(): Player =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            playerFactory.createMpvPlayer()
        } else {
            playerFactory.createPlayer()
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
        audioSyncJob?.cancel()
        subtitleSyncJob?.cancel()
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        val count = playerInstanceCount.decrementAndGet()
        android.util.Log.i(TAG, "Playback player released; live instance count = $count")
        super.onDestroy()
    }
}
