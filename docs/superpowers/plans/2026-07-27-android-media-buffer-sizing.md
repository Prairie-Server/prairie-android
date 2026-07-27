# Android Media-Aware Buffer Sizing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Size Android phone and TV byte buffers from encoded media bitrate metadata instead of fast-network delivery capacity whenever media metadata is available.

**Architecture:** Add one pure internal bitrate-selection function beside `SiloLoadControl`, represented by a small immutable per-track input. Production maps each selected Media3 track into that input; the function chooses average bitrate, then peak, sums known media rates, and only falls back to the largest network estimate when every media rate is unknown. The existing target-byte calculation, time thresholds, and retry data source remain unchanged.

**Tech Stack:** Kotlin 2.1, AndroidX Media3 1.10.1, JUnit 4, Gradle, Android phone and TV application modules.

## Global Constraints

- Limit production behavior changes to shared `android-shared/SiloLoadControl`.
- Do not change Silo Server, Apple clients, or production proxy configuration.
- Preserve issue #80 HTTP Range resume/retry behavior.
- Preserve existing startup, rebuffer, and back-buffer time thresholds.
- Preserve the 16 MiB byte floor, device-specific byte caps, and 15 percent byte overhead.
- Use positive `Format.averageBitrate` first and positive `Format.peakBitrate` only when average is absent or invalid.
- Do not use `Format.bitrate` as an independent input.
- Use raw network throughput only when no selected track has valid media metadata.
- Do not claim observed-consumption adaptation because Media3 exposes no reliable encoded-consumption signal at this boundary.

---

### Task 1: Characterize the approved bitrate-selection contract

**Files:**
- Create: `android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/player/SiloLoadControlTest.kt`
- Modify later in Task 2: `android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/SiloLoadControl.kt`

**Interfaces:**
- Consumes: proposed `BufferSizingTrackBitrates(averageBitrateBps: Int, peakBitrateBps: Int, latestNetworkEstimateBps: Long)`
- Produces: regression expectations for `selectBufferSizingBitrateBps(tracks: List<BufferSizingTrackBitrates>): Long?`

- [ ] **Step 1: Add failing average/peak precedence tests**

Create `SiloLoadControlTest.kt` with literal expectations:

```kotlin
package org.siloserver.silo.common.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
```

- [ ] **Step 2: Add failing media aggregation and no-inflation tests**

Extend the same test class:

```kotlin
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
```

- [ ] **Step 3: Add failing last-resort and unknown tests**

Extend the same test class:

```kotlin
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
```

- [ ] **Step 4: Run the focused test to prove RED**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest \
  --tests 'org.siloserver.silo.common.player.SiloLoadControlTest' \
  --max-workers=2
```

Expected: compilation fails because `BufferSizingTrackBitrates` and `selectBufferSizingBitrateBps` do not exist.

- [ ] **Step 5: Commit the RED tests**

```bash
git add android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/player/SiloLoadControlTest.kt
git commit -m "test(android): specify media-aware buffer bitrate selection"
```

### Task 2: Implement media-metadata-first selection

**Files:**
- Modify: `android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/SiloLoadControl.kt`
- Test: `android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/player/SiloLoadControlTest.kt`

**Interfaces:**
- Consumes: Media3 `ExoTrackSelection.selectedFormat` and `latestBitrateEstimate`
- Produces: `internal data class BufferSizingTrackBitrates` and `internal fun selectBufferSizingBitrateBps(List<BufferSizingTrackBitrates>): Long?`

- [ ] **Step 1: Add the minimal pure selector**

Add beside the existing target-byte helper:

```kotlin
internal data class BufferSizingTrackBitrates(
    val averageBitrateBps: Int,
    val peakBitrateBps: Int,
    val latestNetworkEstimateBps: Long,
)

internal fun selectBufferSizingBitrateBps(
    tracks: List<BufferSizingTrackBitrates>,
): Long? {
    val mediaBitrateBps =
        tracks
            .mapNotNull { track ->
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
```

- [ ] **Step 2: Route selected Media3 tracks through the selector**

Replace the per-selection maximum with one selection-wide call:

```kotlin
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
```

Delete the old private `ExoTrackSelection.selectedBitrateBps()` helper. Do not read `Format.bitrate`.

- [ ] **Step 3: Run the focused selector and policy tests**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest \
  --tests 'org.siloserver.silo.common.player.SiloLoadControlTest' \
  --tests 'org.siloserver.silo.common.player.PlaybackBufferPolicyTest' \
  --max-workers=2
```

Expected: all tests pass. Existing floor/cap tests demonstrate the byte calculation is unchanged.

- [ ] **Step 4: Perform the mutation check**

Temporarily reason through these mutations without retaining source changes:

- choosing peak before average fails `average bitrate takes precedence over peak bitrate`;
- adding network estimates to known media rates fails both no-inflation tests;
- summing shared network estimates fails `largest network estimate is the last resort`;
- accepting zero as known metadata fails the last-resort test;
- returning zero instead of `null` fails both unknown tests.

- [ ] **Step 5: Commit the implementation**

```bash
git add android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/SiloLoadControl.kt
git commit -m "fix(android): size playback buffer from media bitrate"
```

### Task 3: Verify issue #80 compatibility and Android builds

**Files:**
- No production files expected
- Inspect: `android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/player/ProgressiveDirectPlayResumeIntegrationTest.kt`

**Interfaces:**
- Consumes: completed load-control change
- Produces: test and build evidence; no new API

- [ ] **Step 1: Run the progressive Range-resume integration test**

```bash
./gradlew :android-shared:testDebugUnitTest \
  --tests 'org.siloserver.silo.common.player.ProgressiveDirectPlayResumeIntegrationTest' \
  --max-workers=2
```

Expected: all resume/retry cases pass without changes.

- [ ] **Step 2: Run the complete shared Android unit-test suite**

```bash
./gradlew :android-shared:testDebugUnitTest --max-workers=2
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run phone and TV debug compilation**

```bash
./gradlew \
  :android-app:compileDebugKotlin \
  :android-app-tv:compileDebugKotlin \
  --max-workers=2
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run phone and TV release assembly**

```bash
./gradlew \
  :android-app:assembleRelease \
  :android-app-tv:assembleRelease \
  --max-workers=2
```

Expected: `BUILD SUCCESSFUL` with dependency verification intact.

- [ ] **Step 5: Run repository formatting/static checks applicable to the changed Kotlin**

Inspect available Gradle verification tasks and run the repository's configured Kotlin lint/format checks. At minimum run:

```bash
./gradlew check --max-workers=2
```

Expected: `BUILD SUCCESSFUL`.

### Task 4: Perform focused canary and independent review

**Files:**
- Modify only if a deterministic defect is found: the two files from Tasks 1–2
- Record review or canary notes in the draft PR description rather than production code

**Interfaces:**
- Consumes: green implementation branch
- Produces: review verdict and bounded runtime evidence

- [ ] **Step 1: Exercise a short-timeout local canary if the existing playback harness can do so safely**

Use an ephemeral local endpoint or the existing playback harness with deliberately short idle timeouts. Do not change production proxy settings. Confirm that a Range-capable direct-play request resumes or retries using the existing issue #80 path.

If the fixture cannot create genuine socket backpressure, record exactly that limitation and rely on the integration test plus target-selection regression tests; do not substitute bandwidth or allocator growth as a proxy.

- [ ] **Step 2: Request an independent code review**

Ask a fresh reviewer to inspect:

- compliance with the approved average-then-peak order;
- absence of `Format.bitrate` as an independent input;
- network fallback only when all media metadata is absent;
- integer overflow or malformed-metadata handling;
- preservation of load-control floors, caps, thresholds, and issue #80 retry code;
- whether tests would fail under each realistic wrong branch.

- [ ] **Step 3: Apply only verified corrections test-first**

For each actionable defect, first add or adjust a test that fails for that defect, run it to prove RED, make the smallest production correction, then rerun the focused and complete gates from Task 3.

- [ ] **Step 4: Run final clean verification**

From a clean worktree, rerun:

```bash
./gradlew \
  :android-shared:testDebugUnitTest \
  :android-app:compileDebugKotlin \
  :android-app-tv:compileDebugKotlin \
  :android-app:assembleRelease \
  :android-app-tv:assembleRelease \
  --max-workers=2
git diff --check
git status --short
```

Expected: Gradle and diff checks succeed; status contains no uncommitted implementation changes.

### Task 5: Publish a separate draft pull request

**Files:**
- No code changes expected

**Interfaces:**
- Consumes: reviewed, green `fix/android-buffer-sizing` branch
- Produces: a draft GitHub pull request targeting current `main`

- [ ] **Step 1: Review the branch diff and commits**

```bash
git log --oneline origin/main..HEAD
git diff --stat origin/main...HEAD
git diff --check origin/main...HEAD
```

Expected: only the spec, plan, focused tests, and shared load-control implementation are present.

- [ ] **Step 2: Push the branch**

```bash
git push -u origin fix/android-buffer-sizing
```

- [ ] **Step 3: Open a draft pull request**

Create a draft PR targeting `main`. Summarize the media-metadata-first rule, why observed adaptation is deferred, preserved issue #80 behavior, exact verification commands, independent review verdict, and short-timeout canary evidence or limitation. Do not merge.

- [ ] **Step 4: Confirm hosted checks**

Watch the PR checks to terminal state. If any hosted job fails, inspect the exact logs, reproduce systematically, correct at the lowest responsible boundary test-first, repush, and wait for green before reporting completion.
