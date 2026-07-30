package org.siloserver.silo.common.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackBufferPolicyTest {

    private val roomy = PlaybackBufferDeviceProfile(memoryClassMb = 512, isLowRamDevice = false)
    private val lowRam = PlaybackBufferDeviceProfile(memoryClassMb = 96, isLowRamDevice = true)

    // The load control stops reading the socket once the buffer reaches
    // maxBufferMs and does not resume until it drains below minBufferMs, so
    // this gap IS how long the connection sits idle. An upstream proxy with a
    // 60s send timeout drops it if the gap approaches that. This is the
    // property the whole design exists to guarantee.
    @Test
    fun `idle window is bounded for every device profile`() {
        listOf(roomy, lowRam, PlaybackBufferDeviceProfile.Unknown).forEach { profile ->
            val policy = PlaybackBufferPolicy.forConditions(profile)
            assertEquals(
                PlaybackBufferPolicy.MAX_LOAD_IDLE_MS,
                policy.maxBufferMs - policy.minBufferMs,
                "idle window for $profile",
            )
        }
    }

    @Test
    fun `idle window stays well under the proxy send timeout it guards against`() {
        assertTrue(
            PlaybackBufferPolicy.MAX_LOAD_IDLE_MS * 2 <=
                PlaybackBufferPolicy.ASSUMED_PROXY_SEND_TIMEOUT_MS,
            "idle window should keep a wide margin below the assumed timeout",
        )
    }

    @Test
    fun `playback starts on a small cushion and recovers quickly after a stall`() {
        val policy = PlaybackBufferPolicy.forConditions(roomy)
        assertEquals(2_000, policy.bufferForPlaybackMs)
        assertEquals(5_000, policy.bufferForPlaybackAfterRebufferMs)
    }

    @Test
    fun `depth stays within the declared floor and ceiling`() {
        listOf(roomy, lowRam, PlaybackBufferDeviceProfile.Unknown).forEach { profile ->
            val policy = PlaybackBufferPolicy.forConditions(profile)
            assertTrue(policy.minBufferMs >= PlaybackBufferPolicy.MIN_DEPTH_MS, "floor for $profile")
            assertTrue(policy.minBufferMs <= PlaybackBufferPolicy.MAX_DEPTH_MS, "ceiling for $profile")
        }
    }

    @Test
    fun `startup thresholds never exceed the depth the policy asks for`() {
        listOf(roomy, lowRam, PlaybackBufferDeviceProfile.Unknown).forEach { profile ->
            val policy = PlaybackBufferPolicy.forConditions(profile)
            assertTrue(policy.bufferForPlaybackMs <= policy.minBufferMs, "start for $profile")
            assertTrue(
                policy.bufferForPlaybackAfterRebufferMs <= policy.minBufferMs,
                "rebuffer for $profile",
            )
        }
    }
}
