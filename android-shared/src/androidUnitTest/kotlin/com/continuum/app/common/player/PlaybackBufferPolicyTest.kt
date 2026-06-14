package com.continuum.app.common.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackBufferPolicyTest {

    @Test
    fun profilesExposeExpectedStartupAndRebufferTargets() {
        val quick = PlaybackBufferPolicy.forMode(PlaybackBufferMode.QuickStart)
        val balanced = PlaybackBufferPolicy.forMode(PlaybackBufferMode.Balanced)
        val smooth = PlaybackBufferPolicy.forMode(PlaybackBufferMode.SmoothPlayback)

        assertEquals(3_000, quick.bufferForPlaybackMs)
        assertEquals(6_000, quick.bufferForPlaybackAfterRebufferMs)
        assertEquals(7_000, balanced.bufferForPlaybackMs)
        assertEquals(12_000, balanced.bufferForPlaybackAfterRebufferMs)
        assertEquals(5_000, smooth.bufferForPlaybackMs)
        assertEquals(15_000, smooth.bufferForPlaybackAfterRebufferMs)
    }

    @Test
    fun profilesKeepBufferDurationsInValidOrder() {
        PlaybackBufferMode.entries.forEach { mode ->
            val policy = PlaybackBufferPolicy.forMode(mode)
            assertTrue(policy.bufferForPlaybackMs <= policy.minBufferMs, mode.name)
            assertTrue(policy.bufferForPlaybackAfterRebufferMs <= policy.minBufferMs, mode.name)
            assertTrue(policy.minBufferMs <= policy.maxBufferMs, mode.name)
        }
    }

    @Test
    fun smoothPlaybackRemainsMemoryBoundedForTvHlsPlayback() {
        val policy = PlaybackBufferPolicy.forMode(PlaybackBufferMode.SmoothPlayback)

        assertEquals(180_000, policy.maxBufferMs)
        assertFalse(policy.prioritizeTimeOverSizeThresholds)
    }

    @Test
    fun smoothPlaybackStartsQuicklyThenGrowsADeepForwardBuffer() {
        val policy = PlaybackBufferPolicy.forMode(PlaybackBufferMode.SmoothPlayback)

        assertTrue(policy.bufferForPlaybackMs < PlaybackBufferPolicy.forMode(PlaybackBufferMode.Balanced).bufferForPlaybackMs)
        assertTrue(policy.bufferForPlaybackAfterRebufferMs >= policy.bufferForPlaybackMs * 3)
        assertTrue(policy.minBufferMs >= policy.bufferForPlaybackAfterRebufferMs * 6)
        assertTrue(policy.maxBufferMs >= policy.minBufferMs * 2)
    }
}
