package com.continuum.app.common.player.subtitle

import java.util.concurrent.atomic.AtomicLong

/**
 * Shared mutable container for the current subtitle offset in microseconds.
 * Read by [OffsetSubtitleParserFactory] on each cue; mutated by
 * `ContinuumPlaybackService` from the per-profile `subtitleSyncMsFlow`.
 *
 * Positive offset → cues appear later (delay subtitles).
 * Negative offset → cues appear earlier (advance subtitles).
 * Range is clamped to ±10000ms by the `setSubtitleSyncMs` setter (mirrors
 * iOS's `subtitleSyncMs` range — `iosApp/Screens/Player/Sheets/PlayerSettingsSheet.swift:285`);
 * this holder doesn't re-clamp.
 */
class SubtitleOffsetHolder {
    private val offsetUs = AtomicLong(0L)

    fun setOffsetMs(ms: Int) {
        offsetUs.set(ms.toLong() * 1_000L)
    }

    fun getOffsetUs(): Long = offsetUs.get()
    fun getOffsetMs(): Int = (offsetUs.get() / 1_000L).toInt()
}
