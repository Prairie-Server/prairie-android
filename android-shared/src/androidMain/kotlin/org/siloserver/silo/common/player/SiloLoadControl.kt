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
    @Volatile private var depthMs: Int = policy.minBufferMs

    /** The forward buffer the memory budget currently affords, in ms. */
    internal fun currentDepthMs(): Int = depthMs

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
        val result =
            computeBufferSizing(
                selectedBitrateBps = selectedBitrateBps,
                desiredDepthMs = policy.minBufferMs,
                minimumDepthMs = PlaybackBufferPolicy.MIN_DEPTH_MS,
                budgetBytes = policy.targetBufferBytes,
                minimumBytes = MIN_TARGET_BUFFER_BYTES,
                unknownBitrateFallbackBytes = fallback,
            )
        depthMs = result.depth.ms
        return result.target.bytes
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
 * The budget is authoritative: when it affords less than [minimumDepthMs],
 * that shortfall is reported honestly rather than padded up to a floor the
 * loader cannot actually hold — a false-but-round number is worse than an
 * honest one `currentDepthMs()` can be trusted to reflect. [minimumDepthMs]
 * only bounds the *unknown-bitrate* branch below, where there is no bitrate
 * to derive a number from at all.
 *
 * The division by 115/100 mirrors the same overhead margin
 * [calculateBitrateTargetBufferBytes] multiplies back in when it turns a
 * depth into bytes. Without it, a budget-derived depth still produces a byte
 * figure that overshoots the budget once that margin is applied, silently
 * clamps back down to the ceiling, and erases the depth's effect on the byte
 * target — the two must agree, or reducing the depth changes nothing.
 */
internal fun affordableDepthMs(
    desiredDepthMs: Int,
    selectedBitrateBps: Long?,
    budgetBytes: Int,
    minimumDepthMs: Int,
): Int {
    val bitrate = selectedBitrateBps?.takeIf { it > 0L }
        ?: return desiredDepthMs.coerceAtLeast(minimumDepthMs)
    val affordableMs = budgetBytes.toLong() * 8L * 1_000L * 100L / (bitrate * 115L)
    return affordableMs.coerceAtMost(desiredDepthMs.toLong()).toInt()
}

/** A forward-buffer depth, in milliseconds. Wrapped so it cannot be confused with [BufferTargetBytes]. */
@JvmInline
internal value class BufferDepthMs(val ms: Int)

/** A load-control byte target. Wrapped so it cannot be confused with [BufferDepthMs]. */
@JvmInline
internal value class BufferTargetBytes(val bytes: Int)

/**
 * The composed result of sizing the buffer: the depth chosen and the bytes it
 * maps to. Both are wrapped value classes rather than bare `Int`s so that
 * assigning the wrong one to the wrong destination — e.g. storing the byte
 * target where the depth belongs — is a compile error, not a bug only a test
 * exercising the Media3 override could catch.
 */
internal data class BufferSizingResult(val depth: BufferDepthMs, val target: BufferTargetBytes)

/**
 * Composes [affordableDepthMs] and [calculateBitrateTargetBufferBytes] into the
 * single decision `calculateTargetBufferBytes` needs: how deep a buffer the
 * budget affords, and how many bytes that depth costs at this bitrate.
 *
 * Kept separate from the Media3 override so it can be tested directly without
 * constructing track selections — the override is a thin adapter over this.
 */
internal fun computeBufferSizing(
    selectedBitrateBps: Long?,
    desiredDepthMs: Int,
    minimumDepthMs: Int,
    budgetBytes: Int,
    minimumBytes: Int,
    unknownBitrateFallbackBytes: Int,
): BufferSizingResult {
    val depthMs =
        affordableDepthMs(
            desiredDepthMs = desiredDepthMs,
            selectedBitrateBps = selectedBitrateBps,
            budgetBytes = budgetBytes,
            minimumDepthMs = minimumDepthMs,
        )
    val targetBytes =
        calculateBitrateTargetBufferBytes(
            selectedBitrateBps = selectedBitrateBps,
            desiredForwardBufferMs = depthMs,
            // calculateBitrateTargetBufferBytes requires maximumBytes >=
            // minimumBytes. That holds today only because
            // MIN_MEMORY_BUDGET_BYTES and MIN_TARGET_BUFFER_BYTES are both
            // exactly 16 MiB — a coincidence a future change to either
            // constant could break, throwing IllegalArgumentException on the
            // playback thread during track selection. The relation is
            // restored by lowering the floor rather than raising the ceiling,
            // so the memory budget stays the binding limit: a device whose
            // budget is under the nominal floor gets a smaller buffer, never
            // one that overruns the heap it was allowed.
            minimumBytes = minimumBytes.coerceIn(1, budgetBytes.coerceAtLeast(1)),
            maximumBytes = budgetBytes.coerceAtLeast(1),
            unknownBitrateFallbackBytes = unknownBitrateFallbackBytes,
        )
    return BufferSizingResult(depth = BufferDepthMs(depthMs), target = BufferTargetBytes(targetBytes))
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
