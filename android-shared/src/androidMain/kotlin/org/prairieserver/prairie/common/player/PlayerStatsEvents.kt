package org.prairieserver.prairie.common.player

import androidx.media3.common.Player
import org.prairieserver.prairie.playback.stats.PlayerEventSeverity
import org.prairieserver.prairie.playback.stats.StatsRow
import org.prairieserver.prairie.playback.stats.audioChannelsLabel
import org.prairieserver.prairie.playback.stats.bufferHealthLabel
import org.prairieserver.prairie.playback.stats.formatStatsBitrate

/**
 * Prairie stats-for-nerds: turns [PlaybackAnalyticsListener] events into the
 * short, timestamped lines the phone sheet and TV HUD list under "Recent
 * events" (parity with prairie-smarttv #116). Chatty events (bandwidth
 * estimates, track snapshots) are skipped so errors stay in the window.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun describePlayerEvent(
    event: PlaybackAnalyticsListener.Event,
): Pair<PlayerEventSeverity, String>? = when (event) {
    is PlaybackAnalyticsListener.Event.VideoDecoderInitialized ->
        PlayerEventSeverity.INFO to buildString {
            append("Video decoder ").append(event.decoderName)
            event.initializationDurationMs?.let { append(" (").append(it).append(" ms)") }
        }
    is PlaybackAnalyticsListener.Event.AudioDecoderInitialized ->
        PlayerEventSeverity.INFO to "Audio decoder ${event.decoderName}"
    is PlaybackAnalyticsListener.Event.VideoFormatChanged -> {
        val f = event.format
        val size = if (f.width > 0 && f.height > 0) " ${f.width}x${f.height}" else ""
        PlayerEventSeverity.INFO to "Video format ${f.codecs ?: f.sampleMimeType ?: "unknown"}$size"
    }
    is PlaybackAnalyticsListener.Event.AudioFormatChanged -> {
        val f = event.format
        val channels = audioChannelsLabel(f.channelCount.takeIf { it > 0 })?.let { " $it" } ?: ""
        PlayerEventSeverity.INFO to "Audio format ${f.codecs ?: f.sampleMimeType ?: "unknown"}$channels"
    }
    is PlaybackAnalyticsListener.Event.DroppedFrames ->
        PlayerEventSeverity.WARNING to "Dropped ${event.count} frames"
    is PlaybackAnalyticsListener.Event.AudioUnderrun ->
        PlayerEventSeverity.WARNING to "Audio underrun"
    is PlaybackAnalyticsListener.Event.LoadError ->
        PlayerEventSeverity.ERROR to "Load error: ${event.throwable.summary()}"
    is PlaybackAnalyticsListener.Event.PlayerError ->
        PlayerEventSeverity.ERROR to buildString {
            append("Player error ").append(event.error.errorCodeName)
            event.error.message?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
            event.error.cause?.let { append(" (").append(it.summary()).append(')') }
        }
    is PlaybackAnalyticsListener.Event.PlaybackStateChanged -> when (event.state) {
        Player.STATE_BUFFERING -> PlayerEventSeverity.INFO to "Buffering"
        Player.STATE_READY -> PlayerEventSeverity.INFO to "Ready"
        Player.STATE_ENDED -> PlayerEventSeverity.INFO to "Ended"
        else -> null
    }
    is PlaybackAnalyticsListener.Event.FirstFrameRendered -> PlayerEventSeverity.INFO to "First frame"
    is PlaybackAnalyticsListener.Event.SeekStarted -> PlayerEventSeverity.INFO to "Seek"
    // The format-changed line already names the codec; the decoder output
    // format only settles HDR mode, which the Stats rows show.
    is PlaybackAnalyticsListener.Event.VideoOutputFormatChanged,
    is PlaybackAnalyticsListener.Event.BandwidthEstimate,
    is PlaybackAnalyticsListener.Event.TrackSnapshot,
    -> null
}

/**
 * Appends [event] to the snapshot's event log, stamped [nowEpochMs]. Call
 * after [reducePlayerStats] with the wall clock; the reducer itself stays pure.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun PlayerStatsSnapshot.recordPlayerEvent(
    event: PlaybackAnalyticsListener.Event,
    nowEpochMs: Long,
): PlayerStatsSnapshot {
    val (severity, message) = describePlayerEvent(event) ?: return this
    return copy(events = events.append(nowEpochMs, severity, message))
}

/**
 * Player-side health rows shared by phone and TV: media bitrate, buffer
 * ahead, rebuffers and startup time. Complements each app's existing
 * decoder/format rows.
 */
fun PlayerStatsSnapshot.healthRows(): List<StatsRow> = buildList {
    videoBitrateBps?.let { add(StatsRow("Video bitrate", formatStatsBitrate(it))) }
    audioChannelsLabel(audioChannels)?.let { add(StatsRow("Audio channels", it)) }
    bufferHealthLabel(bufferedDurationMs)?.let { add(StatsRow("Buffer ahead", it)) }
    if (rebufferCount > 0) {
        add(StatsRow("Rebuffers", "$rebufferCount (${rebufferTotalMs} ms total, max ${rebufferMaxMs} ms)"))
    }
    startupReadyMs?.let { add(StatsRow("Startup", "$it ms")) }
}

private fun Throwable.summary(): String {
    val name = this::class.java.simpleName.ifBlank { "Error" }
    val text = message?.takeIf { it.isNotBlank() }
    return if (text != null) "$name: $text" else name
}
