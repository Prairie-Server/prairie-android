package org.prairieserver.prairie.common.player.subtitle

import java.util.concurrent.atomic.AtomicLong

/**
 * Shared mutable container for subtitle timing adjustments in microseconds.
 * Read by [OffsetSubtitleParserFactory] on each cue; mutated by
 * `PrairiePlaybackService` from the per-profile `subtitleSyncMsFlow` and by the
 * video mounter when a server-reanchored playback timeline is installed.
 *
 * Positive offset → cues appear later (delay subtitles).
 * Negative offset → cues appear earlier (advance subtitles).
 * Range is clamped to ±10000ms by the `setSubtitleSyncMs` setter (mirrors
 * iOS's `subtitleSyncMs` range — `iosApp/Screens/Player/Sheets/PlayerSettingsSheet.swift:285`);
 * this holder doesn't re-clamp.
 */
class SubtitleOffsetHolder {
    private val userOffsetUs = AtomicLong(0L)
    private val timelineOffsetUs = AtomicLong(0L)

    fun setOffsetMs(ms: Int) {
        userOffsetUs.set(ms.toLong() * 1_000L)
    }

    fun setTimelineOffsetSeconds(seconds: Double) {
        val finiteSeconds = seconds.takeIf { it.isFinite() } ?: 0.0
        timelineOffsetUs.set((finiteSeconds * 1_000_000.0).toLong())
    }

    /** Combined parser adjustment: user sync minus the source/player timeline delta. */
    fun getOffsetUs(): Long = userOffsetUs.get() - timelineOffsetUs.get()

    /** User sync alone, for embedded cues already timestamped on the player timeline. */
    fun getUserOffsetUs(): Long = userOffsetUs.get()

    /** The user-facing sync preference excludes the internal playback timeline adjustment. */
    fun getOffsetMs(): Int = (userOffsetUs.get() / 1_000L).toInt()
}
