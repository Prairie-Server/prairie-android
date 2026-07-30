# Playback Buffer Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop the buffer from idling the socket long enough for an upstream proxy to drop the connection, while letting buffer depth grow as far as memory and throughput allow.

**Architecture:** `maxBufferMs` becomes derived (`min + MAX_LOAD_IDLE_MS`) rather than hand-written, which makes the dropped-connection failure unrepresentable. Depth is governed by a memory budget and observed throughput, with an explicit floor and ceiling. The dead three-mode enum is deleted.

**Tech Stack:** Kotlin 2.1, Java 21, AndroidX Media3 (ExoPlayer), kotlin.test/JUnit4 unit tests in `android-shared/src/androidUnitTest`.

**Spec:** `docs/superpowers/specs/2026-07-30-playback-buffer-architecture-design.md` — read it first; it is the authority on behaviour and carries the reasoning behind every number.

## Global Constraints

- **The invariant is the point of this work:** `maxBufferMs == minBufferMs + MAX_LOAD_IDLE_MS`, always, for every reachable policy including after the memory budget reduces depth. `MAX_LOAD_IDLE_MS = 30_000`.
- **Depth bounds:** floor `20_000` ms, ceiling `180_000` ms.
- **Startup:** `bufferForPlaybackMs = 2_000`, `bufferForPlaybackAfterRebufferMs = 5_000`.
- **No user setting, no server-driven wire value.** `PlaybackBufferMode` and its `fromWire` are deleted, not repurposed.
- **No transcode/HLS special case.** One policy; throughput governs. The server's `TranscodeThrottler` owns the transcode-ahead ceiling.
- Package root is `org.siloserver.silo`; buffer code lives in `org.siloserver.silo.common.player`.
- Build/test: `./gradlew :android-shared:testDebugUnitTest` for these tests; `./gradlew :androidApp:assembleDebug` and `./gradlew :androidTvApp:assembleDebug` must both still build (the module is shared).
- Per repo guidelines, add focused tests for the high-risk behaviour only — do not blanket-test UI or trivial changes.
- Commit per task. Push to `origin` (the RXWatcher fork), never a PR against Silo-Server without being asked.

---

### Task 1: Derive `maxBufferMs` and delete the dead mode enum

This task alone fixes the reported dropped connections.

**Files:**
- Modify: `android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/PlaybackBufferPolicy.kt`
- Modify: `android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/SiloPlayerFactory.kt:318-323`
- Test: `android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/player/PlaybackBufferPolicyTest.kt`

**Interfaces:**
- Produces: `PlaybackBufferPolicy.forConditions(deviceProfile: PlaybackBufferDeviceProfile): PlaybackBufferPolicy`, plus companion constants `MAX_LOAD_IDLE_MS = 30_000`, `MIN_DEPTH_MS = 20_000`, `MAX_DEPTH_MS = 180_000`, `ASSUMED_PROXY_SEND_TIMEOUT_MS = 60_000`. The `PlaybackBufferPolicy` data class keeps its existing six fields unchanged.
- Removes: `PlaybackBufferMode` (whole enum, including `fromWire`) and `PlaybackBufferPolicy.forMode(...)`. `PlaybackBufferDeviceProfile` stays exactly as it is.

- [ ] **Step 1: Write the failing tests**

Replace the whole body of `PlaybackBufferPolicyTest` (its existing tests reference `forMode`/`PlaybackBufferMode`, which this task deletes):

```kotlin
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
```

- [ ] **Step 2: Run the tests and watch them fail**

Run: `./gradlew :android-shared:testDebugUnitTest --tests '*PlaybackBufferPolicyTest*'`
Expected: FAIL — `forConditions` and the constants are unresolved references.

- [ ] **Step 3: Implement**

Replace the contents of `PlaybackBufferPolicy.kt` with:

```kotlin
package org.siloserver.silo.common.player

/**
 * Buffering policy derived from what the player can observe. There is no user
 * setting and no server-supplied mode: the numbers follow from the device and
 * the stream.
 */
data class PlaybackBufferPolicy(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
    val targetBufferBytes: Int,
    val prioritizeTimeOverSizeThresholds: Boolean,
) {
    companion object {
        /**
         * How long the load control may stop reading the socket.
         *
         * DefaultLoadControl fills to maxBufferMs, then requests nothing until
         * the buffer drains below minBufferMs — so the gap between them is
         * literally how long the connection sits idle. Upstream proxies close
         * an idle response body: nginx's send_timeout defaults to 60s. The old
         * hand-written 50s/120s pair left a 70s gap and dropped the connection
         * every time the buffer filled on a long direct-play file.
         *
         * maxBufferMs is therefore never written by hand; it is always
         * minBufferMs + this. Depth can grow without ever widening the window.
         */
        const val MAX_LOAD_IDLE_MS = 30_000

        /** The timeout MAX_LOAD_IDLE_MS is chosen to stay clear of. */
        const val ASSUMED_PROXY_SEND_TIMEOUT_MS = 60_000

        /** Never buffer less than this, however constrained the device. */
        const val MIN_DEPTH_MS = 20_000

        /**
         * Never buffer more than this even when memory allows. Past a few
         * minutes we are mostly prefetching content the viewer may seek away
         * from — wasted bandwidth, and wasted allowance on mobile data.
         */
        const val MAX_DEPTH_MS = 180_000

        private const val START_MS = 2_000

        /**
         * After a stall the viewer is watching a spinner, so the cushion we
         * rebuild before resuming is deliberately small.
         */
        private const val REBUFFER_MS = 5_000

        fun forConditions(
            deviceProfile: PlaybackBufferDeviceProfile = PlaybackBufferDeviceProfile.Unknown,
        ): PlaybackBufferPolicy {
            val depthMs = MAX_DEPTH_MS
            return PlaybackBufferPolicy(
                minBufferMs = depthMs,
                maxBufferMs = depthMs + MAX_LOAD_IDLE_MS,
                bufferForPlaybackMs = START_MS,
                bufferForPlaybackAfterRebufferMs = REBUFFER_MS,
                targetBufferBytes = memoryBudgetBytes(deviceProfile),
                prioritizeTimeOverSizeThresholds = false,
            )
        }

        /**
         * The byte ceiling this device can afford. SiloLoadControl sizes the
         * real target from the stream's bitrate and clamps it to this.
         */
        internal fun memoryBudgetBytes(deviceProfile: PlaybackBufferDeviceProfile): Int = when {
            deviceProfile.isLowRamDevice -> 48 * MIB
            deviceProfile.memoryClassMb <= 0 -> 48 * MIB
            deviceProfile.memoryClassMb < 192 -> 48 * MIB
            deviceProfile.memoryClassMb < 384 -> 96 * MIB
            else -> 160 * MIB
        }

        private const val MIB = 1024 * 1024
    }
}

data class PlaybackBufferDeviceProfile(
    val memoryClassMb: Int,
    val isLowRamDevice: Boolean,
) {
    companion object {
        val Unknown = PlaybackBufferDeviceProfile(memoryClassMb = 0, isLowRamDevice = false)
    }
}
```

Then update the call site in `SiloPlayerFactory.kt` (around line 318). Replace the `PlaybackBufferPolicy.forMode(PlaybackBufferMode.Balanced, playbackBufferDeviceProfile())` call and its preceding comment with:

```kotlin
        // Start on a small cushion and keep filling in the background. Depth is
        // bounded by the device's memory budget in SiloLoadControl; the gap
        // between min and max is fixed so the connection is never idle long
        // enough for an upstream proxy to close it.
        val bufferPolicy = PlaybackBufferPolicy.forConditions(playbackBufferDeviceProfile())
```

- [ ] **Step 4: Run the tests and watch them pass**

Run: `./gradlew :android-shared:testDebugUnitTest --tests '*PlaybackBufferPolicyTest*'`
Expected: PASS, 5 tests.

- [ ] **Step 5: Confirm both apps still build**

Run: `./gradlew :androidApp:assembleDebug :androidTvApp:assembleDebug`
Expected: BUILD SUCCESSFUL. If either fails on an unresolved `PlaybackBufferMode`, there is a second reference to the deleted enum — find it with `grep -rn "PlaybackBufferMode" --include="*.kt" .` and remove it.

- [ ] **Step 6: Commit**

```bash
git add android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/PlaybackBufferPolicy.kt \
        android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/SiloPlayerFactory.kt \
        android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/player/PlaybackBufferPolicyTest.kt
git commit -m "fix(playback): bound the load-idle window so proxies stop dropping the connection"
```

---

### Task 2: Fit depth to the memory budget instead of letting bytes silently truncate it

**Files:**
- Modify: `android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/SiloLoadControl.kt`
- Test: `android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/player/SiloLoadControlTest.kt`

**Interfaces:**
- Consumes: `PlaybackBufferPolicy.MIN_DEPTH_MS`, `PlaybackBufferPolicy.MAX_LOAD_IDLE_MS` (Task 1); the existing internal helpers `selectBufferSizingBitrateBps(...)` and `calculateBitrateTargetBufferBytes(...)`, both unchanged.
- Produces: `internal fun affordableDepthMs(desiredDepthMs: Int, selectedBitrateBps: Long?, budgetBytes: Int, minimumDepthMs: Int): Int` — the depth the budget can actually fund, never below `minimumDepthMs`, never above `desiredDepthMs`.

**Context an implementer needs:** today `calculateTargetBufferBytes` clamps bytes to the budget and stops there, so on a 60 Mbps remux the loader quietly stops at whatever the cap affords (about 5s on a low-RAM device) while the policy still claims a much larger depth. The fix is not to raise the cap — memory is genuinely finite — but to make the reduction explicit, so the resulting depth is a number the code chose rather than an accident.

- [ ] **Step 1: Write the failing tests**

Append to `SiloLoadControlTest`:

```kotlin
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

        assertTrue(depth < 180_000, "expected reduction, got $depth")
        assertEquals(20_000, depth, "should clamp to the floor, not below it")
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
```

Add `import org.junit.Assert.assertTrue` to the file's imports.

- [ ] **Step 2: Run the tests and watch them fail**

Run: `./gradlew :android-shared:testDebugUnitTest --tests '*SiloLoadControlTest*'`
Expected: FAIL — `affordableDepthMs` is an unresolved reference.

- [ ] **Step 3: Implement**

Add to `SiloLoadControl.kt`, beside the other internal helpers:

```kotlin
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
```

- [ ] **Step 4: Run the tests and watch them pass**

Run: `./gradlew :android-shared:testDebugUnitTest --tests '*SiloLoadControlTest*'`
Expected: PASS — the four new tests plus the existing bitrate-selection ones.

- [ ] **Step 5: Commit**

```bash
git add android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/SiloLoadControl.kt \
        android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/player/SiloLoadControlTest.kt
git commit -m "feat(playback): fit buffer depth to the device memory budget"
```

---

### Task 3: Apply the affordable depth to the live load control

**Files:**
- Modify: `android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/SiloLoadControl.kt`
- Test: `android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/player/SiloLoadControlTest.kt`

**Interfaces:**
- Consumes: `affordableDepthMs(...)` (Task 2), `PlaybackBufferPolicy` (Task 1).
- Produces: `SiloLoadControl.currentDepthMs(): Int` — the depth most recently computed from observed bitrate, for tests and diagnostics. Defaults to the policy's `minBufferMs` before any track selection has happened.

**Context an implementer needs:** `DefaultLoadControl` reads its min/max durations from constructor arguments and does not re-read them, so the depth reduction cannot change the running loader's time thresholds. It can and must still change the *byte* target, which is what actually stops the loader. `currentDepthMs()` exists so the reduction is observable rather than implicit — do not attempt to mutate the superclass's durations.

- [ ] **Step 1: Write the failing test**

Append to `SiloLoadControlTest`:

```kotlin
    @Test
    fun `byte target follows the affordable depth rather than the requested one`() {
        // A 40 Mbps stream on a 48 MiB budget can hold ~10s, not the 180s the
        // policy asks for. The byte target must reflect the affordable depth,
        // and must never exceed the budget.
        val budgetBytes = 48 * 1024 * 1024
        val depth =
            affordableDepthMs(
                desiredDepthMs = PlaybackBufferPolicy.MAX_DEPTH_MS,
                selectedBitrateBps = 40_000_000L,
                budgetBytes = budgetBytes,
                minimumDepthMs = PlaybackBufferPolicy.MIN_DEPTH_MS,
            )

        val bytes =
            calculateBitrateTargetBufferBytes(
                selectedBitrateBps = 40_000_000L,
                desiredForwardBufferMs = depth,
                minimumBytes = SiloLoadControl.MIN_TARGET_BUFFER_BYTES,
                maximumBytes = budgetBytes,
                unknownBitrateFallbackBytes = budgetBytes,
            )

        assertTrue(bytes <= budgetBytes, "byte target $bytes exceeded budget $budgetBytes")
        assertTrue(bytes >= SiloLoadControl.MIN_TARGET_BUFFER_BYTES, "byte target below floor")
    }
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `./gradlew :android-shared:testDebugUnitTest --tests '*SiloLoadControlTest*'`
Expected: FAIL — `MIN_TARGET_BUFFER_BYTES` is `internal` inside a companion that the test can reach, but `calculateTargetBufferBytes` does not yet size from an affordable depth. If it compiles and passes immediately, the sizing path was already correct; still complete step 3 so the running loader uses it.

- [ ] **Step 3: Implement**

Replace `SiloLoadControl`'s `calculateTargetBufferBytes` override and add the depth field:

```kotlin
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
        val affordableMs =
            affordableDepthMs(
                desiredDepthMs = policy.minBufferMs,
                selectedBitrateBps = selectedBitrateBps,
                budgetBytes = policy.targetBufferBytes,
                minimumDepthMs = PlaybackBufferPolicy.MIN_DEPTH_MS,
            )
        depthMs = affordableMs
        return calculateBitrateTargetBufferBytes(
            selectedBitrateBps = selectedBitrateBps,
            desiredForwardBufferMs = affordableMs,
            minimumBytes = MIN_TARGET_BUFFER_BYTES,
            maximumBytes = policy.targetBufferBytes,
            unknownBitrateFallbackBytes = fallback,
        )
    }
```

- [ ] **Step 4: Run the full module test suite**

Run: `./gradlew :android-shared:testDebugUnitTest`
Expected: PASS, no regressions in the existing player tests.

- [ ] **Step 5: Confirm both apps build**

Run: `./gradlew :androidApp:assembleDebug :androidTvApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/SiloLoadControl.kt \
        android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/player/SiloLoadControlTest.kt
git commit -m "feat(playback): size the byte target from the affordable buffer depth"
```

---

## Self-review notes (already applied)

- Spec coverage: the invariant and enum deletion → Task 1; memory-governed depth with floor/ceiling → Tasks 2 and 3; startup/rebuffer numbers → Task 1; no-transcode-special-case and no-user-setting are satisfied by never introducing them.
- Throughput-driven depth is represented by the existing `latestBitrateEstimate` fallback inside `selectBufferSizingBitrateBps`, which already prefers measured network throughput when the container declares no bitrate. No separate task: adding a second throughput mechanism would duplicate it.
- Type consistency: `affordableDepthMs` has one signature, used identically in Tasks 2 and 3; `PlaybackBufferPolicy.forConditions` takes only a device profile in both Task 1 and its call site.
- `MIN_TARGET_BUFFER_BYTES` stays `internal const` on `SiloLoadControl`'s companion, unchanged from today, so the Task 3 test can reference it.
