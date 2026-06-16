# Phase 1 · Track A — Playback Truth (device-matrix hardening) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete and prove the *existing* dual-engine playback (ExoPlayer + libmpv) so MPV/display-aware playback is selected by a correct Auto policy, fails safe, is observable, and is verified on a real device matrix that establishes the MPV-enable floor.

**Architecture:** Silo already has the dual-engine seam — `MpvPlayer : BasePlayer`, `Media3VideoPlaybackBackend`/`MpvVideoPlaybackBackend`, `VideoPlaybackBackendFactory`, and a pure `VideoPlaybackBackendSelector` with a basic `Auto` policy. This track extends the pure selector with the converged Auto axes (route/session intent + device-class floor), adds a fallback contract and decision observability, implements real HDR-mode switching in `HdrDisplayController`, and gates everything behind a device-matrix findings note. No new seam; we harden the one that exists.

**Tech Stack:** Kotlin, Media3 1.10, libmpv (`dev.jdtech.mpv`), `kotlin.test` + JUnit4 (module `:android-shared`, source set `androidUnitTest`), Robolectric where Android types are unavoidable. Build floor API-24; MPV floor is provisional (API-26 / 64-bit ABI) pending Task 6.

**Test command (whole track):** `./gradlew :android-shared:testDebugUnitTest`
Single class: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.backend.VideoPlaybackBackendSelectorTest"`

---

## File structure

- `android-shared/.../player/backend/VideoPlaybackBackendRequest.kt` — extend with route/session-intent + device-support flags (Tasks 1–2).
- `android-shared/.../player/backend/VideoPlaybackBackendSelector.kt` — extend the pure `Auto` policy (Tasks 1–2).
- `android-shared/.../player/backend/MpvDeviceFloor.kt` — **new**, pure device-class floor decision (Task 2).
- `android-shared/.../player/backend/PlaybackEngineDecision.kt` — **new**, structured decision record for observability (Task 4).
- `android-shared/.../player/backend/PlaybackBackendFallback.kt` — **new**, pure fallback-state reducer (Task 3).
- `android-shared/.../player/HdrDisplayController.kt` — implement real HDR-type selection + restore (Task 5).
- `android-shared/.../player/HdrModeSelection.kt` — **new**, pure HDR-mode selection function (Task 5).
- Tests mirror each under `android-shared/src/androidUnitTest/kotlin/...`.
- `docs/superpowers/notes/2026-06-16-track-a-device-matrix-findings.md` — **new**, the verification deliverable (Task 6).

---

## Task 1: Auto policy — route/session intent forces Media3

**Files:**
- Modify: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendRequest.kt`
- Modify: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendSelector.kt`
- Test: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendSelectorTest.kt`

- [ ] **Step 1: Write the failing tests** (append to the existing `VideoPlaybackBackendSelectorTest`)

```kotlin
    @Test
    fun autoForcesMedia3WhenCasting() {
        val request = VideoPlaybackBackendRequest(isCasting = true, hasHardContainer = true)
        assertEquals(VideoPlaybackBackendKind.Media3, VideoPlaybackBackendSelector.select(request))
    }

    @Test
    fun autoForcesMedia3WhenDrmProtected() {
        val request = VideoPlaybackBackendRequest(isDrmProtected = true, hasStyledSubtitles = true)
        assertEquals(VideoPlaybackBackendKind.Media3, VideoPlaybackBackendSelector.select(request))
    }

    @Test
    fun autoForcesMedia3OnExternalDisplay() {
        val request = VideoPlaybackBackendRequest(isExternalDisplay = true, hasHardContainer = true)
        assertEquals(VideoPlaybackBackendKind.Media3, VideoPlaybackBackendSelector.select(request))
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.backend.VideoPlaybackBackendSelectorTest"`
Expected: FAIL — `isCasting`/`isDrmProtected`/`isExternalDisplay` are not parameters of `VideoPlaybackBackendRequest` (compile error).

- [ ] **Step 3: Add the new request fields**

In `VideoPlaybackBackendRequest.kt`, add to the data class (after `hasStyledSubtitles`):

```kotlin
    // Route/session intent — any of these forces Media3 under Auto, because
    // Cast, DRM, and external/secondary displays are paths where ExoPlayer is
    // the correct/only engine and MPV's direct rendering does not apply.
    val isCasting: Boolean = false,
    val isDrmProtected: Boolean = false,
    val isExternalDisplay: Boolean = false,
```

- [ ] **Step 4: Extend the Auto branch**

In `VideoPlaybackBackendSelector.kt`, replace the `Auto` branch with:

```kotlin
            VideoPlaybackBackendPreference.Auto -> when {
                // Route/session intent: ExoPlayer is the correct engine here.
                request.isCasting -> VideoPlaybackBackendKind.Media3
                request.isDrmProtected -> VideoPlaybackBackendKind.Media3
                request.isExternalDisplay -> VideoPlaybackBackendKind.Media3
                request.playMethod == PlayMethod.TRANSCODE -> VideoPlaybackBackendKind.Media3
                // Fidelity: MPV for hard containers / styled subtitles.
                request.hasHardContainer -> VideoPlaybackBackendKind.Mpv
                request.hasStyledSubtitles -> VideoPlaybackBackendKind.Mpv
                else -> VideoPlaybackBackendKind.Media3
            }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.backend.VideoPlaybackBackendSelectorTest"`
Expected: PASS (all prior tests still green — the new clauses only fire on the new flags).

- [ ] **Step 6: Commit**

```bash
git add android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendRequest.kt \
        android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendSelector.kt \
        android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendSelectorTest.kt
git commit -m "Auto playback policy: route/session intent forces Media3"
```

---

## Task 2: Auto policy — device-class floor gating

**Files:**
- Create: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/MpvDeviceFloor.kt`
- Create: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/MpvDeviceFloorTest.kt`
- Modify: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendRequest.kt`
- Modify: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendSelector.kt`
- Test: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendSelectorTest.kt`

- [ ] **Step 1: Write the failing test for `MpvDeviceFloor`**

```kotlin
package com.continuum.app.common.player.backend

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MpvDeviceFloorTest {
    @Test
    fun supportedOnModern64BitDevice() {
        assertTrue(MpvDeviceFloor.isMpvSupported(sdkInt = 30, supportedAbis = listOf("arm64-v8a")))
    }

    @Test
    fun unsupportedBelowMinSdk() {
        assertFalse(MpvDeviceFloor.isMpvSupported(sdkInt = 24, supportedAbis = listOf("arm64-v8a")))
    }

    @Test
    fun unsupportedOn32BitOnlyDevice() {
        assertFalse(MpvDeviceFloor.isMpvSupported(sdkInt = 30, supportedAbis = listOf("armeabi-v7a")))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.backend.MpvDeviceFloorTest"`
Expected: FAIL — `MpvDeviceFloor` does not exist.

- [ ] **Step 3: Implement `MpvDeviceFloor`** (pure; the Android `Build` read happens at the call site)

```kotlin
package com.continuum.app.common.player.backend

/**
 * Provisional device-class floor for enabling the MPV backend under Auto.
 * Conservative by design: refined by the Phase-1 Track-A device matrix
 * (docs/superpowers/notes/2026-06-16-track-a-device-matrix-findings.md).
 * Pure (primitive inputs) so it is unit-testable without Android.
 */
object MpvDeviceFloor {
    /** Provisional minimum SDK for MPV; the matrix may lower this toward 24. */
    const val MIN_SDK_FOR_MPV = 26

    fun isMpvSupported(sdkInt: Int, supportedAbis: List<String>): Boolean {
        if (sdkInt < MIN_SDK_FOR_MPV) return false
        // Require a 64-bit ABI for the initial rollout; ARMv7-only TV boxes are
        // revisited after the device matrix proves the native libs there.
        return supportedAbis.any { it == "arm64-v8a" || it == "x86_64" }
    }
}
```

- [ ] **Step 4: Run to verify `MpvDeviceFloorTest` passes**

Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.backend.MpvDeviceFloorTest"`
Expected: PASS.

- [ ] **Step 5: Write the failing selector test for the floor**

Append to `VideoPlaybackBackendSelectorTest`:

```kotlin
    @Test
    fun autoFallsBackToMedia3BelowMpvDeviceFloor() {
        val request = VideoPlaybackBackendRequest(
            hasHardContainer = true,
            mpvSupportedOnDevice = false,
        )
        assertEquals(VideoPlaybackBackendKind.Media3, VideoPlaybackBackendSelector.select(request))
    }
```

- [ ] **Step 6: Run to verify it fails**

Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.backend.VideoPlaybackBackendSelectorTest"`
Expected: FAIL — `mpvSupportedOnDevice` not a parameter.

- [ ] **Step 7: Add the field and the floor clause**

In `VideoPlaybackBackendRequest.kt` add:

```kotlin
    // Device-class floor result (computed at the call site from Build.VERSION +
    // Build.SUPPORTED_ABIS via MpvDeviceFloor). Default true so pure/unit call
    // sites keep prior behavior; production call sites pass the real value.
    val mpvSupportedOnDevice: Boolean = true,
```

In `VideoPlaybackBackendSelector.kt`, add as the **first** clause inside `Auto` (before route/session intent), so an unsupported device never selects MPV:

```kotlin
                !request.mpvSupportedOnDevice -> VideoPlaybackBackendKind.Media3
```

- [ ] **Step 8: Run the full selector + floor tests to verify all pass**

Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.backend.*"`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/MpvDeviceFloor.kt \
        android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/MpvDeviceFloorTest.kt \
        android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendRequest.kt \
        android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendSelector.kt \
        android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendSelectorTest.kt
git commit -m "Auto playback policy: device-class floor gates MPV"
```

---

## Task 3: Fallback contract — MPV start failure retries Media3

**Files:**
- Create: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/PlaybackBackendFallback.kt`
- Create: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/PlaybackBackendFallbackTest.kt`

The fallback decision is a pure reducer so it is unit-testable; the wiring that calls it lives where the backend is started (the player surface / playback service) and is covered by Task 6's on-device verification.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.continuum.app.common.player.backend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackBackendFallbackTest {
    @Test
    fun mpvStartFailureFallsBackToMedia3WithReason() {
        val next = PlaybackBackendFallback.onStartFailure(
            attempted = VideoPlaybackBackendKind.Mpv,
            error = "mpv: vo init failed",
        )
        assertEquals(VideoPlaybackBackendKind.Media3, next?.fallbackTo)
        assertEquals("mpv: vo init failed", next?.reason)
    }

    @Test
    fun media3StartFailureHasNoFurtherFallback() {
        val next = PlaybackBackendFallback.onStartFailure(
            attempted = VideoPlaybackBackendKind.Media3,
            error = "decoder init failed",
        )
        assertNull(next)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.backend.PlaybackBackendFallbackTest"`
Expected: FAIL — `PlaybackBackendFallback` does not exist.

- [ ] **Step 3: Implement the reducer**

```kotlin
package com.continuum.app.common.player.backend

/** A single fallback step: which engine to retry on, and why. */
data class PlaybackBackendFallbackStep(
    val fallbackTo: VideoPlaybackBackendKind,
    val reason: String,
)

/**
 * Fallback contract: MPV start failure must retry on Media3 and record the
 * reason; Media3 is the terminal engine (no further fallback). Pure so the
 * contract is unit-tested; the start-failure wiring calls this.
 */
object PlaybackBackendFallback {
    fun onStartFailure(
        attempted: VideoPlaybackBackendKind,
        error: String,
    ): PlaybackBackendFallbackStep? = when (attempted) {
        VideoPlaybackBackendKind.Mpv ->
            PlaybackBackendFallbackStep(VideoPlaybackBackendKind.Media3, error)
        VideoPlaybackBackendKind.Media3 -> null
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.backend.PlaybackBackendFallbackTest"`
Expected: PASS.

- [ ] **Step 5: Wire the reducer at the start-failure site**

In the code that starts a backend from `VideoPlaybackBackendFactory.create(...)` (the playback service / video session coordinator), wrap the MPV start so that a caught start exception calls `PlaybackBackendFallback.onStartFailure(VideoPlaybackBackendKind.Mpv, error)`; if non-null, recreate via the factory forcing `VideoPlaybackBackendPreference.Media3` and emit the decision (Task 4). Add a `// Track A fallback contract` comment at the call site so Task 6 verification and a source test can locate it.

- [ ] **Step 6: Commit**

```bash
git add android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/PlaybackBackendFallback.kt \
        android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/PlaybackBackendFallbackTest.kt
git commit -m "Playback fallback contract: MPV start failure retries Media3 with reason"
```

---

## Task 4: Observability — structured playback-engine decision record

**Files:**
- Create: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/PlaybackEngineDecision.kt`
- Create: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/PlaybackEngineDecisionTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.continuum.app.common.player.backend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackEngineDecisionTest {
    @Test
    fun decisionRecordsSelectedEngineAndDeterminingAxis() {
        val request = VideoPlaybackBackendRequest(hasStyledSubtitles = true)
        val decision = PlaybackEngineDecision.from(request, VideoPlaybackBackendKind.Mpv)
        assertEquals(VideoPlaybackBackendKind.Mpv, decision.selected)
        assertEquals("hasStyledSubtitles", decision.reason)
    }

    @Test
    fun decisionRendersOneLineLog() {
        val request = VideoPlaybackBackendRequest(isCasting = true)
        val line = PlaybackEngineDecision.from(request, VideoPlaybackBackendKind.Media3).toLogLine()
        assertTrue(line.contains("engine=Media3"))
        assertTrue(line.contains("reason=isCasting"))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.backend.PlaybackEngineDecisionTest"`
Expected: FAIL — `PlaybackEngineDecision` does not exist.

- [ ] **Step 3: Implement the decision record** (reason mirrors the selector's clause order)

```kotlin
package com.continuum.app.common.player.backend

import com.continuum.app.model.playback.PlayMethod

/**
 * Structured, loggable record of why a playback engine was chosen. The reason
 * string mirrors VideoPlaybackBackendSelector's Auto clause order so logs and
 * policy never drift. Track-A observability requirement.
 */
data class PlaybackEngineDecision(
    val selected: VideoPlaybackBackendKind,
    val reason: String,
    val contentId: String?,
    val fileId: Int?,
) {
    fun toLogLine(): String =
        "playback-engine engine=$selected reason=$reason contentId=$contentId fileId=$fileId"

    companion object {
        fun from(request: VideoPlaybackBackendRequest, selected: VideoPlaybackBackendKind) =
            PlaybackEngineDecision(
                selected = selected,
                reason = reasonFor(request, selected),
                contentId = request.contentId,
                fileId = request.fileId,
            )

        private fun reasonFor(
            request: VideoPlaybackBackendRequest,
            selected: VideoPlaybackBackendKind,
        ): String = when (request.preference) {
            VideoPlaybackBackendPreference.Media3 -> "preference=Media3"
            VideoPlaybackBackendPreference.Mpv -> "preference=Mpv"
            VideoPlaybackBackendPreference.Auto -> when {
                !request.mpvSupportedOnDevice -> "mpvSupportedOnDevice=false"
                request.isCasting -> "isCasting"
                request.isDrmProtected -> "isDrmProtected"
                request.isExternalDisplay -> "isExternalDisplay"
                request.playMethod == PlayMethod.TRANSCODE -> "transcode"
                request.hasHardContainer -> "hasHardContainer"
                request.hasStyledSubtitles -> "hasStyledSubtitles"
                else -> "default"
            }
        }
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.backend.PlaybackEngineDecisionTest"`
Expected: PASS.

- [ ] **Step 5: Emit the decision** where `VideoPlaybackBackendFactory.create(...)` runs the selector — build `PlaybackEngineDecision.from(request, selected)` and log `decision.toLogLine()` (and the display-mode change/restore + HDR/passthrough outcomes from Task 5). Keep it one structured line per event.

- [ ] **Step 6: Commit**

```bash
git add android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/PlaybackEngineDecision.kt \
        android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/PlaybackEngineDecisionTest.kt
git commit -m "Observability: structured playback-engine decision record"
```

---

## Task 5: Real HDR-mode selection in HdrDisplayController

**Files:**
- Create: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/HdrModeSelection.kt`
- Create: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/HdrModeSelectionTest.kt`
- Modify: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/HdrDisplayController.kt`

The pure selection (given content HDR type + a display's supported HDR types, choose the target) is unit-tested; the `Display.Mode.getSupportedHdrTypes` read + apply/restore is API-34-gated and device-verified in Task 6.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.continuum.app.common.player

import kotlin.test.Test
import kotlin.test.assertEquals

class HdrModeSelectionTest {
    @Test
    fun prefersExactContentHdrTypeWhenDisplaySupportsIt() {
        val result = HdrModeSelection.choose(
            contentHdr = HdrType.HDR10,
            displaySupported = setOf(HdrType.HDR10, HdrType.DOLBY_VISION),
        )
        assertEquals(HdrType.HDR10, result)
    }

    @Test
    fun fallsBackToSdrWhenDisplayLacksContentHdrType() {
        val result = HdrModeSelection.choose(
            contentHdr = HdrType.DOLBY_VISION,
            displaySupported = setOf(HdrType.HDR10),
        )
        assertEquals(HdrType.SDR, result)
    }

    @Test
    fun sdrContentStaysSdr() {
        val result = HdrModeSelection.choose(
            contentHdr = HdrType.SDR,
            displaySupported = setOf(HdrType.HDR10),
        )
        assertEquals(HdrType.SDR, result)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.HdrModeSelectionTest"`
Expected: FAIL — `HdrModeSelection`/`HdrType` do not exist.

- [ ] **Step 3: Implement the pure selection**

```kotlin
package com.continuum.app.common.player

enum class HdrType { SDR, HDR10, HDR10_PLUS, HLG, DOLBY_VISION }

/**
 * Pure HDR target selection: honor the content's HDR type only when the display
 * advertises it; otherwise fall back to SDR (tone-mapped). The Android read of
 * Display.Mode.getSupportedHdrTypes (API-34+) feeds [displaySupported] at the
 * call site; below API-34 the platform negotiates HDR implicitly and this
 * returns SDR so we never force an unsupported mode.
 */
object HdrModeSelection {
    fun choose(contentHdr: HdrType, displaySupported: Set<HdrType>): HdrType = when {
        contentHdr == HdrType.SDR -> HdrType.SDR
        contentHdr in displaySupported -> contentHdr
        else -> HdrType.SDR
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.HdrModeSelectionTest"`
Expected: PASS.

- [ ] **Step 5: Wire into `HdrDisplayController`** — on API-34+, read `display.mode.supportedHdrTypes` (map to `HdrType`), call `HdrModeSelection.choose(contentHdr, supported)`, and factor the chosen HDR type into display-mode selection; preserve the existing `preferredDisplayModeId` apply (`HdrDisplayController.kt:79-105`) and the `originalModeId` restore. Below API-34, keep current implicit negotiation. Log the chosen HDR type + applied/restored mode as one structured line (Task 4).

- [ ] **Step 6: Run the module unit tests to confirm nothing regressed**

Run: `./gradlew :android-shared:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add android-shared/src/androidMain/kotlin/com/continuum/app/common/player/HdrModeSelection.kt \
        android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/HdrModeSelectionTest.kt \
        android-shared/src/androidMain/kotlin/com/continuum/app/common/player/HdrDisplayController.kt
git commit -m "HDR: real content-vs-display HDR-mode selection in HdrDisplayController"
```

---

## Task 6: Device-matrix verification + findings note (the gate)

This task is verification, not TDD: it establishes the empirical MPV-enable floor and Auto thresholds that Tasks 2/5 reference, and is the go/no-go for MPV-as-default.

**Files:**
- Create: `docs/superpowers/notes/2026-06-16-track-a-device-matrix-findings.md`

- [ ] **Step 1: Build and install the debug app** on each device.

Run: `./gradlew :androidApp:installDebug` (phone) and `:androidTvApp:installDebug` (TV).
Devices: **Pixel** (`58211FDCQ000CU`), **NVIDIA SHIELD** (`192.168.1.128:5555`), and — if obtainable — an old ARMv7 / API-24 Android TV box.

- [ ] **Step 2: Run the fixture matrix** per device, forcing each engine via the preference, capturing the structured logs (Task 4) with `adb logcat -s playback-engine`:

| Fixture | Check |
|---|---|
| H.264 mp4, SDR, SRT subs | direct play both engines; baseline |
| HEVC Main10 HDR10, 24p, E-AC3 | MPV direct play; refresh→24Hz + **restore** on stop; HDR on/off behavior |
| Dolby Vision (profile 5 & 8) | engine behavior; HDR-type selection (Task 5) |
| MKV + ASS/SSA styled subs | **MPV libass fidelity** vs Media3 SSA; the differentiator |
| TrueHD / DTS-HD bitstream | audio passthrough to AVR vs PCM downmix |
| Transcode (HLS) | Auto → Media3; never MPV |
| Cast active | Auto → Media3; never MPV |

- [ ] **Step 3: Record per-device results** — for each fixture: engine used, direct-play success, subtitle fidelity, refresh switch + restore, HDR result, audio passthrough result, any crash/black-screen. Note ABI + API + WebView/Cast versions.

- [ ] **Step 4: Derive and record the outputs** in the findings note: the **MPV-enable device floor** (confirm or revise `MpvDeviceFloor.MIN_SDK_FOR_MPV` and the ABI rule) and any **Auto-threshold** adjustments. If the matrix lowers/raises the floor, update `MpvDeviceFloor` (re-run its unit test) and commit that change referencing this note.

- [ ] **Step 5: Go/no-go** — state whether MPV-as-Auto is safe to enable by default, on which device classes, and what (if anything) is deferred. This gates wiring the real `mpvSupportedOnDevice` (from `Build.VERSION.SDK_INT` + `Build.SUPPORTED_ABIS` via `MpvDeviceFloor`) and route/session-intent flags into the production `VideoPlaybackBackendRequest` call sites.

- [ ] **Step 6: Commit the findings note**

```bash
git add docs/superpowers/notes/2026-06-16-track-a-device-matrix-findings.md \
        android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/MpvDeviceFloor.kt
git commit -m "Track A device-matrix findings: MPV-enable floor + Auto thresholds"
```

---

## Self-review notes
- Pure-logic deliverables (selector axes, device floor, fallback reducer, decision record, HDR selection) are full failing-test-first TDD against real existing types (`VideoPlaybackBackendSelector`, `PlayMethod`, `VideoPlaybackBackendKind`) and match the existing `VideoPlaybackBackendSelectorTest` style.
- Backend/Android-integration parts (fallback wiring, HDR apply/restore, production request wiring) are implemented against the existing seam and **proven by the Task 6 device matrix** — consistent with the repo's split of unit-tested logic + on-device verification.
- Type consistency: `mpvSupportedOnDevice`, `isCasting`, `isDrmProtected`, `isExternalDisplay` are defined in Task 1/2 and reused identically in Task 4's `reasonFor`. `MpvDeviceFloor.MIN_SDK_FOR_MPV` is provisional and explicitly revised by Task 6.
