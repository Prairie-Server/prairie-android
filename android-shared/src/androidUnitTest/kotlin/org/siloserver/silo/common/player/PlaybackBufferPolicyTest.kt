package org.siloserver.silo.common.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    // The idle window is expressed in MEDIA time, but a proxy's send_timeout
    // measures WALL CLOCK time, and DefaultLoadControl only scales
    // minBufferUs for speeds ABOVE 1.0 — not below it. Audiobooks share this
    // load control and the UI offers rates down to
    // PlaybackBufferPolicy.SLOWEST_PLAYBACK_SPEED (0.5x), where one
    // media-time second of idle window takes two wall-clock seconds. This
    // asserts the window still fits inside the assumed proxy timeout once
    // stretched by that slowest rate, not just at 1.0x.
    //
    // The comparison is strict on purpose. Equality is not "fits" — a socket
    // that goes quiet for exactly the timeout is a race the proxy wins about
    // as often as we do. It also matters for what this test is FOR: with a
    // non-strict comparison, reverting MAX_LOAD_IDLE_MS to the pre-fix 30_000
    // gives 60_000 <= 60_000 and the guard passes, silently readmitting the
    // exact bug this work removed.
    @Test
    fun `idle window still fits the proxy timeout once stretched by the slowest playback speed`() {
        val stretchedWallClockMs =
            PlaybackBufferPolicy.MAX_LOAD_IDLE_MS / PlaybackBufferPolicy.SLOWEST_PLAYBACK_SPEED

        assertTrue(
            stretchedWallClockMs < PlaybackBufferPolicy.ASSUMED_PROXY_SEND_TIMEOUT_MS,
            "idle window ($stretchedWallClockMs ms wall clock at " +
                "${PlaybackBufferPolicy.SLOWEST_PLAYBACK_SPEED}x) should still fit inside the " +
                "assumed proxy timeout (${PlaybackBufferPolicy.ASSUMED_PROXY_SEND_TIMEOUT_MS} ms)",
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

    @Test
    fun `a small heap gets exactly half its heap as a buffer budget`() {
        // Product ruling: half the heap, not a quarter. A quarter-heap rule
        // gives a 96 MB heap only 24 MiB — the exact fixed floor this policy
        // replaced, not an improvement on it.
        val smallHeap = PlaybackBufferDeviceProfile(memoryClassMb = 96, isLowRamDevice = false)
        val budgetBytes = PlaybackBufferPolicy.memoryBudgetBytes(smallHeap)
        val halfHeapBytes = (smallHeap.memoryClassMb * 1024 * 1024) / 2

        assertEquals(halfHeapBytes, budgetBytes)
    }

    @Test
    fun `NVIDIA Shield measured memoryClass gets half its heap, not a quarter`() {
        // Measured via adb: the Shield reports memoryClass=192MB and is not
        // flagged low-RAM. Under a quarter-heap rule it would get 48 MiB —
        // LESS than the 96 MiB it shipped with before this policy existed.
        val shield = PlaybackBufferDeviceProfile(memoryClassMb = 192, isLowRamDevice = false)

        assertEquals(96 * 1024 * 1024, PlaybackBufferPolicy.memoryBudgetBytes(shield))
    }

    @Test
    fun `Google TV Streamer measured memoryClass hits the ceiling at half its heap`() {
        // Measured via adb: the Streamer reports memoryClass=384MB and is
        // not flagged low-RAM. Half of that is exactly the 192 MiB ceiling.
        val streamer = PlaybackBufferDeviceProfile(memoryClassMb = 384, isLowRamDevice = false)

        assertEquals(192 * 1024 * 1024, PlaybackBufferPolicy.memoryBudgetBytes(streamer))
    }

    @Test
    fun `a bigger heap gets a bigger budget than a smaller one`() {
        // Proportional scaling means the ceiling grows with the device
        // instead of two devices past the old 384 MB tier boundary sharing
        // the same flat 160 MiB cap.
        val midHeap = PlaybackBufferDeviceProfile(memoryClassMb = 256, isLowRamDevice = false)
        val bigHeap = PlaybackBufferDeviceProfile(memoryClassMb = 512, isLowRamDevice = false)

        val midBudget = PlaybackBufferPolicy.memoryBudgetBytes(midHeap)
        val bigBudget = PlaybackBufferPolicy.memoryBudgetBytes(bigHeap)

        assertTrue(
            bigBudget > midBudget,
            "a 512 MB heap ($bigBudget) should get more budget than a 256 MB heap ($midBudget)",
        )
    }

    @Test
    fun `a low-RAM device with an unknown heap gets the conservative fixed fallback`() {
        val unknownHeapLowRam = PlaybackBufferDeviceProfile(memoryClassMb = 0, isLowRamDevice = true)

        assertEquals(24 * 1024 * 1024, PlaybackBufferPolicy.memoryBudgetBytes(unknownHeapLowRam))
    }

    @Test
    fun `a low-RAM device with a small known heap gets the smaller of the flat fallback and its proportional share`() {
        // A low-RAM stick reporting a small but genuinely known memoryClass
        // must not have that number thrown away in favor of the flat 24 MiB
        // fallback — that would be the exact flaw (a fixed value ignoring
        // what the device actually reports) the proportional rule exists to
        // remove. 48MB is a real memoryClass a low-RAM device could report;
        // half of it (24 MiB) ties the flat fallback, so use a heap small
        // enough that the proportional share is strictly smaller.
        val smallKnownHeapLowRam =
            PlaybackBufferDeviceProfile(memoryClassMb = 32, isLowRamDevice = true)
        val proportionalBytes = (32 * 1024 * 1024) / 2

        assertTrue(proportionalBytes < 24 * 1024 * 1024, "test heap must undercut the flat fallback")
        assertEquals(
            proportionalBytes,
            PlaybackBufferPolicy.memoryBudgetBytes(smallKnownHeapLowRam),
        )
    }

    @Test
    fun `a low-RAM device with a larger known heap is still capped at the flat fallback`() {
        // The flat 24 MiB fallback must still act as a ceiling on the
        // low-RAM path: a low-RAM device reporting a heap large enough that
        // half of it exceeds 24 MiB must not get more than the conservative
        // fallback just because isLowRamDevice happened to be paired with a
        // roomier-looking memoryClass.
        val largerKnownHeapLowRam =
            PlaybackBufferDeviceProfile(memoryClassMb = 96, isLowRamDevice = true)

        assertEquals(
            24 * 1024 * 1024,
            PlaybackBufferPolicy.memoryBudgetBytes(largerKnownHeapLowRam),
        )
    }

    @Test
    fun `constructing a policy with a wider idle window than MAX_LOAD_IDLE_MS throws`() {
        assertFailsWith<IllegalArgumentException> {
            PlaybackBufferPolicy(
                minBufferMs = 50_000,
                maxBufferMs = 120_000,
                bufferForPlaybackMs = 2_000,
                bufferForPlaybackAfterRebufferMs = 5_000,
                targetBufferBytes = 16 * 1024 * 1024,
                prioritizeTimeOverSizeThresholds = false,
            )
        }
    }
}
