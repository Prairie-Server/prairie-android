package org.siloserver.silo.common.player

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.DefaultAllocator

/**
 * Media3 load control with a bitrate-scaled, heap-bounded allocation target.
 *
 * Time thresholds still decide startup/rebuffer behavior. The byte target is a
 * safety rail: low-bitrate video is no longer forced to allocate a 4K-sized
 * buffer, while a high-bitrate remux can grow up to the device-class cap and
 * never consume the entire app heap trying to satisfy 50 seconds literally.
 */
@UnstableApi
class SiloLoadControl(
    private val policy: PlaybackBufferPolicy,
) : DefaultLoadControl(
    DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
    policy.minBufferMs,
    policy.minBufferMs,
    policy.maxBufferMs,
    policy.maxBufferMs,
    policy.bufferForPlaybackMs,
    policy.bufferForPlaybackMs,
    policy.bufferForPlaybackAfterRebufferMs,
    policy.bufferForPlaybackAfterRebufferMs,
    C.LENGTH_UNSET,
    policy.prioritizeTimeOverSizeThresholds,
    policy.prioritizeTimeOverSizeThresholds,
    0,
    false,
) {
    override fun calculateTargetBufferBytes(
        parameters: LoadControl.Parameters,
        trackSelections: Array<out ExoTrackSelection?>,
    ): Int {
        val selectedBitrateBps =
            selectBufferSizingBitrateBps(
                trackSelections.mapNotNull { selection ->
                    selection?.let {
                        BufferSizingTrackBitrates(
                            averageBitrateBps = it.selectedFormat.averageBitrate,
                            peakBitrateBps = it.selectedFormat.peakBitrate,
                            latestNetworkEstimateBps = it.latestBitrateEstimate,
                        )
                    }
                },
            )
        val fallback = super.calculateTargetBufferBytes(parameters, trackSelections)
        return calculateBitrateTargetBufferBytes(
            selectedBitrateBps = selectedBitrateBps,
            desiredForwardBufferMs = policy.minBufferMs,
            minimumBytes = MIN_TARGET_BUFFER_BYTES,
            maximumBytes = policy.targetBufferBytes,
            unknownBitrateFallbackBytes = fallback,
        )
    }

    companion object {
        internal const val MIN_TARGET_BUFFER_BYTES = 16 * 1024 * 1024
    }
}

internal data class BufferSizingTrackBitrates(
    val averageBitrateBps: Int,
    val peakBitrateBps: Int,
    val latestNetworkEstimateBps: Long,
)

internal fun selectBufferSizingBitrateBps(
    tracks: List<BufferSizingTrackBitrates>,
): Long? {
    val mediaBitrateBps =
        tracks.mapNotNull { track ->
            track.averageBitrateBps.takeIf { it > 0 }?.toLong()
                ?: track.peakBitrateBps.takeIf { it > 0 }?.toLong()
        }

    if (mediaBitrateBps.isNotEmpty()) {
        return mediaBitrateBps.sum()
    }

    return tracks
        .maxOfOrNull { it.latestNetworkEstimateBps }
        ?.takeIf { it > 0L }
}

/**
 * The forward buffer this device can actually hold at this bitrate.
 *
 * Memory is finite, so a high-bitrate stream genuinely cannot be buffered as
 * deeply as a low-bitrate one. Computing that reduction here — rather than
 * letting the byte clamp truncate the buffer wherever it happens to land —
 * means the resulting depth is a number the code chose and can be reasoned
 * about, and it keeps maxBufferMs one idle window above a depth that is real.
 *
 * An unknown bitrate leaves the request untouched; the byte clamp still
 * applies downstream.
 */
internal fun affordableDepthMs(
    desiredDepthMs: Int,
    selectedBitrateBps: Long?,
    budgetBytes: Int,
    minimumDepthMs: Int,
): Int {
    val bitrate = selectedBitrateBps?.takeIf { it > 0L } ?: return desiredDepthMs
    val affordableMs = budgetBytes.toLong() * 8L * 1_000L / bitrate
    return affordableMs
        .coerceIn(minimumDepthMs.toLong(), desiredDepthMs.toLong())
        .toInt()
}

internal fun calculateBitrateTargetBufferBytes(
    selectedBitrateBps: Long?,
    desiredForwardBufferMs: Int,
    minimumBytes: Int,
    maximumBytes: Int,
    unknownBitrateFallbackBytes: Int,
): Int {
    require(minimumBytes > 0)
    require(maximumBytes >= minimumBytes)
    val desiredBytes = selectedBitrateBps?.takeIf { it > 0L }?.let { bitrate ->
        // 15% allows for container/segment overhead and ordinary bitrate
        // variance without turning a stream's nominal bitrate into a promise.
        try {
            Math.multiplyExact(
                Math.multiplyExact(bitrate, desiredForwardBufferMs.toLong()),
                115L,
            ) / (8L * 1_000L * 100L)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }
    } ?: unknownBitrateFallbackBytes.toLong()
    return desiredBytes.coerceIn(minimumBytes.toLong(), maximumBytes.toLong()).toInt()
}
