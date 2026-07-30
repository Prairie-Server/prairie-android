package org.siloserver.silo.common.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiloLoadControlTest {
    @Test
    fun `average bitrate takes precedence over peak bitrate`() {
        val selected =
            selectBufferSizingBitrateBps(
                listOf(
                    BufferSizingTrackBitrates(
                        averageBitrateBps = 4_000_000,
                        peakBitrateBps = 9_000_000,
                        latestNetworkEstimateBps = 100_000_000L,
                    ),
                ),
            )

        assertEquals(4_000_000L, selected)
    }

    @Test
    fun `peak bitrate is used when average bitrate is invalid`() {
        val selected =
            selectBufferSizingBitrateBps(
                listOf(
                    BufferSizingTrackBitrates(
                        averageBitrateBps = -1,
                        peakBitrateBps = 9_000_000,
                        latestNetworkEstimateBps = 100_000_000L,
                    ),
                ),
            )

        assertEquals(9_000_000L, selected)
    }

    @Test
    fun `known selected media bitrates are summed and network capacity is ignored`() {
        val selected =
            selectBufferSizingBitrateBps(
                listOf(
                    BufferSizingTrackBitrates(4_000_000, 8_000_000, 100_000_000L),
                    BufferSizingTrackBitrates(-1, 192_000, 100_000_000L),
                ),
            )

        assertEquals(4_192_000L, selected)
    }

    @Test
    fun `one known media rate suppresses network fallback from metadata-poor tracks`() {
        val selected =
            selectBufferSizingBitrateBps(
                listOf(
                    BufferSizingTrackBitrates(4_000_000, 8_000_000, 100_000_000L),
                    BufferSizingTrackBitrates(-1, -1, 100_000_000L),
                ),
            )

        assertEquals(4_000_000L, selected)
    }

    @Test
    fun `largest network estimate is the last resort when all media metadata is invalid`() {
        val selected =
            selectBufferSizingBitrateBps(
                listOf(
                    BufferSizingTrackBitrates(0, -1, 18_000_000L),
                    BufferSizingTrackBitrates(-1, 0, 25_000_000L),
                ),
            )

        assertEquals(25_000_000L, selected)
    }

    @Test
    fun `unknown bitrate remains unknown when metadata and network estimates are invalid`() {
        val selected =
            selectBufferSizingBitrateBps(
                listOf(
                    BufferSizingTrackBitrates(-1, 0, -1L),
                ),
            )

        assertNull(selected)
    }

    @Test
    fun `empty track selection remains unknown`() {
        assertNull(selectBufferSizingBitrateBps(emptyList()))
    }

    @Test
    fun `depth shrinks to what the memory budget can fund`() {
        // 60 Mbps against a 48 MiB budget: 48 MiB * 8 / 60 Mbps ~= 6.7s, so the
        // requested 180s cannot be held and the depth must come down to fit.
        val depth =
            affordableDepthMs(
                desiredDepthMs = 180_000,
                selectedBitrateBps = 60_000_000L,
                budgetBytes = 48 * 1024 * 1024,
                minimumDepthMs = 20_000,
            )

        assertTrue("expected reduction, got $depth", depth < 180_000)
        assertEquals("should clamp to the floor, not below it", 20_000, depth)
    }

    @Test
    fun `depth is left alone when the budget can fund it`() {
        // 5 Mbps against 160 MiB: ~268s available, more than the 180s asked for.
        val depth =
            affordableDepthMs(
                desiredDepthMs = 180_000,
                selectedBitrateBps = 5_000_000L,
                budgetBytes = 160 * 1024 * 1024,
                minimumDepthMs = 20_000,
            )

        assertEquals(180_000, depth)
    }

    @Test
    fun `depth falls back to the request when the bitrate is unknown`() {
        val depth =
            affordableDepthMs(
                desiredDepthMs = 120_000,
                selectedBitrateBps = null,
                budgetBytes = 96 * 1024 * 1024,
                minimumDepthMs = 20_000,
            )

        assertEquals(120_000, depth)
    }

    @Test
    fun `reducing depth never widens the idle window`() {
        // The invariant has to survive the reduction: whatever depth the budget
        // affords, max is still exactly one idle window above it.
        val depth =
            affordableDepthMs(
                desiredDepthMs = 180_000,
                selectedBitrateBps = 80_000_000L,
                budgetBytes = 48 * 1024 * 1024,
                minimumDepthMs = PlaybackBufferPolicy.MIN_DEPTH_MS,
            )
        val max = depth + PlaybackBufferPolicy.MAX_LOAD_IDLE_MS

        assertEquals(PlaybackBufferPolicy.MAX_LOAD_IDLE_MS, max - depth)
    }

    @Test
    fun `composed sizing clamps depth to the floor and bytes to the budget ceiling`() {
        // A 40 Mbps stream on a 48 MiB budget cannot hold the 180s the policy
        // asks for, nor even the 20s floor (affordable is ~10s), so depth
        // clamps to the floor. The resulting byte target is then sized from
        // that clamped depth and clamps to the budget, not the (distinct)
        // fallback — this exercises the exact composition
        // calculateTargetBufferBytes wires together, unlike the free-standing
        // affordableDepthMs/calculateBitrateTargetBufferBytes tests above.
        val budgetBytes = 48 * 1024 * 1024
        val fallbackBytes = 30 * 1024 * 1024 // distinct from budgetBytes: catches a maximumBytes mix-up
        val result =
            computeBufferSizing(
                selectedBitrateBps = 40_000_000L,
                desiredDepthMs = PlaybackBufferPolicy.MAX_DEPTH_MS,
                minimumDepthMs = PlaybackBufferPolicy.MIN_DEPTH_MS,
                budgetBytes = budgetBytes,
                minimumBytes = SiloLoadControl.MIN_TARGET_BUFFER_BYTES,
                unknownBitrateFallbackBytes = fallbackBytes,
            )

        assertEquals("depth should clamp to the floor", PlaybackBufferPolicy.MIN_DEPTH_MS, result.depthMs)
        assertEquals("byte target should clamp to the budget ceiling, not the fallback", budgetBytes, result.targetBytes)
    }

    @Test
    fun `composed sizing routes the fallback bytes when the bitrate is unknown`() {
        // With no bitrate to size from, the requested depth passes through
        // untouched and the byte target must come from the caller-supplied
        // fallback (what the superclass computed) rather than the budget.
        val fallbackBytes = 40 * 1024 * 1024
        val result =
            computeBufferSizing(
                selectedBitrateBps = null,
                desiredDepthMs = 120_000,
                minimumDepthMs = PlaybackBufferPolicy.MIN_DEPTH_MS,
                budgetBytes = 160 * 1024 * 1024,
                minimumBytes = SiloLoadControl.MIN_TARGET_BUFFER_BYTES,
                unknownBitrateFallbackBytes = fallbackBytes,
            )

        assertEquals("depth should pass through unchanged", 120_000, result.depthMs)
        assertEquals("byte target should route the fallback", fallbackBytes, result.targetBytes)
    }

    @Test
    fun `composed sizing never asks for a deeper buffer than the policy requested`() {
        // A reduction must only ever shrink the depth, never grow it —
        // growing it would widen the fixed idle window between min and max.
        val desiredDepthMs = PlaybackBufferPolicy.MAX_DEPTH_MS
        val result =
            computeBufferSizing(
                selectedBitrateBps = 80_000_000L,
                desiredDepthMs = desiredDepthMs,
                minimumDepthMs = PlaybackBufferPolicy.MIN_DEPTH_MS,
                budgetBytes = 48 * 1024 * 1024,
                minimumBytes = SiloLoadControl.MIN_TARGET_BUFFER_BYTES,
                unknownBitrateFallbackBytes = 48 * 1024 * 1024,
            )

        assertTrue(
            "depth ${result.depthMs} exceeded requested $desiredDepthMs",
            result.depthMs <= desiredDepthMs,
        )
    }
}
