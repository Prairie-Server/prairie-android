package com.continuum.app.common.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import com.continuum.app.common.BuildConfig
import com.continuum.app.model.playback.AudioPassthroughCapabilities
import com.continuum.app.model.playback.HdrCapabilities
import com.continuum.app.model.playback.PlayMethod
import com.continuum.app.model.playback.PlayerSubtitleInfo
import com.continuum.app.network.TokenManager

/**
 * Factory for ExoPlayer instances. Each factory owns exactly one
 * [AuthenticatedDataSourceFactory], which in turn reuses the pooled
 * media [okhttp3.OkHttpClient] injected via Koin — so TLS sessions and
 * HTTP/2 connections survive across playback sessions.
 *
 * Form-factor (TV vs. phone) is resolved lazily from the [Context] via
 * [TvModeDetector] — callers never pass an `isTv` flag.
 *
 * Track-selection presets are applied via [applyTrackSelectionPresets]
 * so callers can re-apply them on capability changes (HDMI hot-plug,
 * Bluetooth pair, profile language change) without rebuilding the player.
 */
@UnstableApi
class ContinuumPlayerFactory(
    private val context: Context,
    private val tokenManager: TokenManager,
    private val subtitleManager: SubtitleManager,
    okHttpClient: okhttp3.OkHttpClient,
) {
    val isTv: Boolean = TvModeDetector.isTv(context)

    private var serverUrl: String = ""

    private val dataSourceFactory = AuthenticatedDataSourceFactory(
        okHttpClient = okHttpClient,
        tokenManager = tokenManager,
        serverUrlProvider = { serverUrl },
    )

    private val extractorsFactory = DefaultExtractorsFactory()
        // Media3 1.10 expects parsed cue samples by default. Forcing raw
        // subtitle payloads here makes SRT/ASS tracks crash the text renderer
        // on Android TV with "Legacy decoding is disabled".
        .setSubtitleParserFactory(DefaultSubtitleParserFactory())

    fun createPlayer(
        preferFfmpegAudio: Boolean = BuildConfig.FFMPEG_AUDIO_ENABLED,
    ): ExoPlayer {
        // When the flag is on (default), extension renderers (FFmpeg audio)
        // beat platform renderers for any MIME both can handle, which means
        // on devices without native E-AC-3/TrueHD/DTS decoders the audio
        // flows through FFmpeg instead of silently failing back to server
        // transcode.
        //
        // When the flag is off (compile-time bisect), we set _MODE_OFF
        // rather than _MODE_ON so extension renderers are *not even
        // instantiated* — that's the clean kill switch. With _MODE_ON,
        // FFmpeg would still fill gaps where the platform has no decoder,
        // making it impossible to cleanly bisect an FFmpeg-specific
        // regression against a pre-FFmpeg baseline.
        val extensionMode = if (preferFfmpegAudio) {
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
        } else {
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
        }
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(extensionMode)
            .setEnableDecoderFallback(true)

        val trackSelector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)
                .build()
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory)
            .setDataSourceFactory(dataSourceFactory)

        // 4K HDR bitrates can chew through default buffers on the first
        // few segments — 60 s min / 120 s max gives headroom, and
        // prioritizing time over size keeps the pre-buffer bounded when
        // the network briefly spikes.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 60_000,
                /* maxBufferMs = */ 120_000,
                /* bufferForPlaybackMs = */ 2_500,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val builder = ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .setSuppressPlaybackOnUnsuitableOutput(true)

        // Always prefer seamless frame-rate changes. Non-seamless mode
        // switches cause a short black frame and are jarring on both TVs
        // (HDMI renegotiation) and phones (panel reset); seamless-only
        // stays safe everywhere and is a strict subset of the previous
        // default.
        builder.setVideoChangeFrameRateStrategy(
            C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS,
        )

        return builder.build()
    }

    /**
     * Apply capability-aware track selection presets to [player]. Call at
     * player construction and again whenever [AudioPassthroughCapabilities]
     * changes (HDMI hot-plug, BT pair, user-toggled "force Atmos" setting).
     */
    fun applyTrackSelectionPresets(
        player: Player,
        audioCaps: AudioPassthroughCapabilities,
        displayHdr: HdrCapabilities = HdrCapabilities(),
        preferredAudioLanguage: String? = null,
        preferredTextLanguage: String? = null,
    ) {
        val base = player.trackSelectionParameters
        val next = if (isTv) {
            TrackSelectionPresets.buildTvParameters(
                context = context,
                base = base,
                audioCaps = audioCaps,
                displayHdr = displayHdr,
                preferredAudioLanguage = preferredAudioLanguage,
                preferredTextLanguage = preferredTextLanguage,
            )
        } else {
            TrackSelectionPresets.buildPhoneParameters(
                context = context,
                base = base,
                audioCaps = audioCaps,
                spatializerOn = audioCaps.spatializerEnabled,
                preferredAudioLanguage = preferredAudioLanguage,
                preferredTextLanguage = preferredTextLanguage,
            )
        }
        player.trackSelectionParameters = next
    }

    /**
     * Build a [MediaItem] the player can consume directly via `setMediaItem`.
     * The MIME type hint on the item is what lets [DefaultMediaSourceFactory]
     * pick HLS vs. progressive without requiring the stream URL to carry the
     * right extension — Silo's transcode URLs don't always end in .m3u8.
     *
     * Sidecar subtitles are attached via `setSubtitleConfigurations`; the
     * default media source factory then wires them through the merging source
     * automatically.
     */
    fun buildMediaItem(
        streamUrl: String,
        playMethod: PlayMethod,
        serverUrl: String,
        subtitles: List<PlayerSubtitleInfo> = emptyList(),
    ): MediaItem {
        this.serverUrl = serverUrl
        val absoluteUrl = buildAbsoluteUrl(serverUrl, streamUrl)
        val subtitleConfigurations =
            subtitleManager.buildSubtitleConfigurations(subtitles, serverUrl)

        val builder = MediaItem.Builder()
            .setUri(absoluteUrl)
            .setSubtitleConfigurations(subtitleConfigurations)

        when (playMethod) {
            PlayMethod.TRANSCODE -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            PlayMethod.DIRECT, PlayMethod.REMUX -> Unit
        }

        return builder.build()
    }

    private fun buildAbsoluteUrl(serverUrl: String, streamUrl: String): String {
        val base = serverUrl.trimEnd('/')
        return if (streamUrl.startsWith("http://") || streamUrl.startsWith("https://")) {
            streamUrl
        } else {
            "$base/api/v1$streamUrl"
        }
    }
}
