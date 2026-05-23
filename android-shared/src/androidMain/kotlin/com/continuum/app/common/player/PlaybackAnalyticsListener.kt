package com.continuum.app.common.player

import android.util.Log
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * `AnalyticsListener` that logs the handful of signals we actually triage
 * playback issues with — decoder init names, dropped-frame counts, audio
 * underruns, load errors, and bandwidth estimates — and re-emits them to an
 * in-process [SharedFlow] so the debug overlay (or a future server-side
 * telemetry POST) can subscribe without another listener registration.
 *
 * Output is `Log.i` on [TAG] only; no network I/O. Server-side telemetry
 * ingestion is deferred to a follow-up — the flow hook here is the seam.
 */
@UnstableApi
class PlaybackAnalyticsListener : AnalyticsListener {

    companion object {
        private const val TAG = "Media3Analytics"
    }

    sealed class Event {
        data class VideoDecoderInitialized(val decoderName: String) : Event()
        data class AudioDecoderInitialized(val decoderName: String) : Event()
        data class VideoFormatChanged(val format: Format) : Event()
        data class AudioFormatChanged(val format: Format) : Event()
        data class DroppedFrames(val count: Int, val elapsedMs: Long) : Event()
        object AudioUnderrun : Event()
        data class LoadError(val throwable: Throwable) : Event()
        data class BandwidthEstimate(val bitrateBps: Long) : Event()
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 32)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    override fun onVideoDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        Log.i(TAG, "Video decoder: $decoderName (init ${initializationDurationMs}ms)")
        _events.tryEmit(Event.VideoDecoderInitialized(decoderName))
    }

    override fun onAudioDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        Log.i(TAG, "Audio decoder: $decoderName (init ${initializationDurationMs}ms)")
        _events.tryEmit(Event.AudioDecoderInitialized(decoderName))
    }

    override fun onVideoInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?,
    ) {
        Log.i(TAG, "Video format: ${format.sampleMimeType} ${format.width}x${format.height}@${format.frameRate} codecs=${format.codecs}")
        _events.tryEmit(Event.VideoFormatChanged(format))
    }

    override fun onAudioInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?,
    ) {
        Log.i(TAG, "Audio format: ${format.sampleMimeType} ch=${format.channelCount} sr=${format.sampleRate}")
        _events.tryEmit(Event.AudioFormatChanged(format))
    }

    override fun onDroppedVideoFrames(
        eventTime: AnalyticsListener.EventTime,
        droppedFrames: Int,
        elapsedRealtimeMs: Long,
    ) {
        if (droppedFrames > 0) {
            Log.w(TAG, "Dropped $droppedFrames video frame(s) in ${elapsedRealtimeMs}ms")
        }
        _events.tryEmit(Event.DroppedFrames(droppedFrames, elapsedRealtimeMs))
    }

    override fun onAudioUnderrun(
        eventTime: AnalyticsListener.EventTime,
        bufferSize: Int,
        bufferSizeMs: Long,
        elapsedSinceLastFeedMs: Long,
    ) {
        Log.w(TAG, "Audio underrun (buffer=${bufferSizeMs}ms, gap=${elapsedSinceLastFeedMs}ms)")
        _events.tryEmit(Event.AudioUnderrun)
    }

    override fun onLoadError(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
        error: java.io.IOException,
        wasCanceled: Boolean,
    ) {
        Log.w(TAG, "Load error (${mediaLoadData.dataType}): ${error.message}")
        _events.tryEmit(Event.LoadError(error))
    }

    override fun onBandwidthEstimate(
        eventTime: AnalyticsListener.EventTime,
        totalLoadTimeMs: Int,
        totalBytesLoaded: Long,
        bitrateEstimate: Long,
    ) {
        _events.tryEmit(Event.BandwidthEstimate(bitrateEstimate))
    }
}
