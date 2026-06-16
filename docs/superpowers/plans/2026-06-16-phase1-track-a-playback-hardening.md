# Phase 1 · Track A — Playback Truth (device-matrix hardening) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete and prove the *existing* dual-engine playback (ExoPlayer + libmpv) so MPV/display-aware playback is selected by a correct Auto policy, fails safe, is observable, and is verified on a real device matrix that establishes the MPV-enable floor.

**Architecture:** Silo already has the dual-engine seam — `MpvPlayer : BasePlayer`, `Media3VideoPlaybackBackend`/`MpvVideoPlaybackBackend`, `VideoPlaybackBackendFactory`, and a pure `VideoPlaybackBackendSelector` with a basic `Auto` policy. **But the real engine owner is the MediaSession service `ContinuumPlaybackService`, which today *always* builds ExoPlayer (`createPlaybackPlayer()` → `playerFactory.createPlayer()`), binds it via `MediaSession.Builder(this, player)`, and never calls the existing `ContinuumPlayerFactory.createMpvPlayer()` (zero call sites).** So a perfect selector still never reaches MPV in production. Therefore **Task 0 (engine-ownership/switch boundary) is the keystone and runs first**; the selector/fallback/observability/HDR work (Tasks 1–5) is only real once Task 0 makes the chosen engine actually own — and rebind — the session player. No new seam; we harden and *connect* the one that exists, then prove it on a device matrix.

**Critical sequencing:** Task 0 first. Tasks 1–5 build on it. Task 6 (device matrix) is the go/no-go gate.

**Tech Stack:** Kotlin, Media3 1.10, libmpv (`dev.jdtech.mpv`), `kotlin.test` + JUnit4 (module `:android-shared`, source set `androidUnitTest`), Robolectric where Android types are unavoidable. Build floor API-24; MPV floor is provisional (API-26 / 64-bit ABI) pending Task 6.

**Test command (whole track):** `./gradlew :android-shared:testDebugUnitTest`
Single class: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.backend.VideoPlaybackBackendSelectorTest"`

---

## File structure

- `android-shared/.../player/ContinuumPlaybackService.kt` — **the engine owner**: build the chosen engine from the request, rebind the `MediaSession` player on switch/fallback, emit decisions (Task 0).
- `android-shared/.../player/backend/PlaybackEngineCommand.kt` — **new**, the `SessionCommand` contract carrying a `VideoPlaybackBackendRequest` from the mount path to the service (Task 0).
- `android-shared/.../player/ContinuumPlayerFactory.kt` — already exposes `createPlayer()` (ExoPlayer) + `createMpvPlayer()` (suspend, MPV); Task 0 wires both behind a single engine-kind entry.
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

## Task 0: Engine-ownership / switch boundary (KEYSTONE — do first)

**Why:** `ContinuumPlaybackService` is the real engine owner and today always builds
ExoPlayer; `createMpvPlayer()` is dead code. Until the chosen engine actually owns the
`MediaSession` player — and can be rebound at mount time (when playMethod/container/
subtitles are known) and on fallback — Tasks 1–5 are green tests with no runtime effect.
The service is created (and its session built) *before* the request is known, so the
mechanism is: keep a default ExoPlayer at `onCreate`, then **switch and rebind** the
session player when the mount path sends the request.

**Files:**
- Create: `android-shared/.../player/backend/PlaybackEngineCommand.kt`
- Create: `android-shared/.../player/backend/PlaybackEngineCommandTest.kt`
- Modify: `android-shared/.../player/ContinuumPlaybackService.kt`
- Modify: `androidApp/.../ui/screens/player/PlayerScreen.kt` (send the command at mount, ~`:341`)
- Modify: `androidTvApp/.../ui/screens/player/TvPlayerScreen.kt` (send the command at mount, ~`:523`)
- Test: `android-shared/src/androidUnitTest/.../ContinuumPlaybackServiceEngineSourceTest.kt`

- [ ] **Step 1: Write the failing test for the command (de)serialization** (pure, unit-testable)

```kotlin
package com.continuum.app.common.player.backend

import com.continuum.app.model.playback.PlayMethod
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackEngineCommandTest {
    @Test
    fun requestRoundTripsThroughCommandArgs() {
        val request = VideoPlaybackBackendRequest(
            contentId = "abc", fileId = 7, playMethod = PlayMethod.DIRECT,
            hasHardContainer = true, hasStyledSubtitles = true,
            isCasting = false, isDrmProtected = false, isExternalDisplay = false,
            mpvSupportedOnDevice = true,
        )
        val restored = PlaybackEngineCommand.fromArgs(PlaybackEngineCommand.toArgs(request))
        assertEquals(request, restored)
    }
}
```
(Note: `isCasting`/`isDrmProtected`/`isExternalDisplay`/`mpvSupportedOnDevice` are added
in Tasks 1–2; if executing strictly in order, start this test with the fields that exist
and extend it when those land. The round-trip contract is the point.)

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.backend.PlaybackEngineCommandTest"`
Expected: FAIL — `PlaybackEngineCommand` does not exist.

- [ ] **Step 3: Implement `PlaybackEngineCommand`** — a `SessionCommand` id `"silo.SET_ENGINE"` plus `toArgs(request): Bundle` / `fromArgs(Bundle): VideoPlaybackBackendRequest` putting each field under a stable key. (Bundle keys are plain strings; enums stored by `name`.)

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.backend.PlaybackEngineCommandTest"`
Expected: PASS.

- [ ] **Step 5: Wire the switch boundary into `ContinuumPlaybackService`** (integration; verified on-device in Task 6, plus the source-assertion test in Step 7):
  - Register the custom command in the `MediaSession.Callback` (`onConnect` available-commands + `onCustomCommand`).
  - On `silo.SET_ENGINE`: `val request = PlaybackEngineCommand.fromArgs(args)`; compute
    `mpvSupportedOnDevice` from `MpvDeviceFloor.isMpvSupported(Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.toList())`;
    `val kind = VideoPlaybackBackendSelector.select(request.copy(mpvSupportedOnDevice = …))`.
  - If `kind != currentEngineKind`: build the new player — `ExoPlayer` via `playerFactory.createPlayer()` (sync) or `MpvPlayer` via `playerFactory.createMpvPlayer()` (suspend, on `scope`) — then `mediaSession?.setPlayer(newPlayer)`, transfer media items/position/`playWhenReady`, and **release the previous player**. Re-attach the analytics listener only when the new player `is ExoPlayer`.
  - Emit `PlaybackEngineDecision` (Task 4) with requested/selected/actual at this boundary.

- [ ] **Step 6: Send the command from the mount path** — in `PlayerScreen.kt` (~`:341`) and `TvPlayerScreen.kt` (~`:523`), where `playMethod`/container/subtitles become known, build the `VideoPlaybackBackendRequest` and `mediaController.sendCustomCommand(PlaybackEngineCommand.SET_ENGINE, PlaybackEngineCommand.toArgs(request))`. Do **not** rely on the early factory call (`PlayerScreen.kt:180`) where the policy inputs are still unknown.

- [ ] **Step 7: Source-assertion test** (repo convention — mirrors `ReaderEngineHostSourceTest`):

```kotlin
package com.continuum.app.common.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ContinuumPlaybackServiceEngineSourceTest {
    private val src = File(
        "src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlaybackService.kt",
    ).readText()

    @Test fun servicePicksEngineFromRequestAndCanUseMpv() {
        assertTrue(src.contains("VideoPlaybackBackendSelector.select"))
        assertTrue(src.contains("createMpvPlayer"))
        assertTrue(src.contains("setPlayer("))
    }
}
```

- [ ] **Step 8: Run module unit tests + commit**

Run: `./gradlew :android-shared:testDebugUnitTest`
Expected: PASS.
```bash
git add android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/PlaybackEngineCommand.kt \
        android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/backend/PlaybackEngineCommandTest.kt \
        android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlaybackService.kt \
        android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/ContinuumPlaybackServiceEngineSourceTest.kt \
        androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreen.kt \
        androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt
git commit -m "Engine-ownership boundary: ContinuumPlaybackService builds+rebinds chosen engine"
```

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

- [ ] **Step 5: Wire the reducer at the REAL start-failure sites** (per Codex review)

There are two real failure points, both at the engine-owner boundary from Task 0 — **not** a MediaController wrap (recreating a Media3 backend around the same `MediaController` does not replace an MPV-backed session player):
1. **MPV native create/init failure** in `ContinuumPlayerFactory.createMpvPlayer()` → `MpvPlayer` (`MPVLib.create` `mpv/MpvPlayer.kt:172`, `mpv.init()` `MpvPlayer.kt:217`). Catch in Task 0's switch handler when building the MPV player.
2. **Prepare/load failure** at `mountVideoMedia(...)` (`VideoPlayerMediaMounter.kt`, `player.setMediaItem`/`prepare`/`playWhenReady`), reached via `MpvVideoPlaybackBackend.mount(...)`.

On either, call `PlaybackBackendFallback.onStartFailure(VideoPlaybackBackendKind.Mpv, error)`; if non-null, **rebind the session player to a freshly built ExoPlayer** (`mediaSession.setPlayer(playerFactory.createPlayer())`, transfer media/position, release the MPV player) and emit the decision with the recorded reason (Task 4). Add a `// Track A fallback contract` comment at both sites for the source test + Task 6 to locate.

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
    fun decisionRecordsSelectedAndActualAndDeterminingAxis() {
        val request = VideoPlaybackBackendRequest(hasStyledSubtitles = true)
        // selected == actual: MPV chosen and an MpvPlayer was actually bound.
        val decision = PlaybackEngineDecision.from(request, selected = VideoPlaybackBackendKind.Mpv, actual = VideoPlaybackBackendKind.Mpv)
        assertEquals(VideoPlaybackBackendKind.Mpv, decision.selected)
        assertEquals(VideoPlaybackBackendKind.Mpv, decision.actual)
        assertEquals("hasStyledSubtitles", decision.reason)
    }

    @Test
    fun decisionRecordsDowngradeWhenActualDiffersFromSelected() {
        // Selector said MPV but the factory/owner bound Media3 (downgrade) — the
        // log MUST show both, or logs can claim MPV while Media3 actually plays.
        val request = VideoPlaybackBackendRequest(hasHardContainer = true)
        val line = PlaybackEngineDecision
            .from(request, selected = VideoPlaybackBackendKind.Mpv, actual = VideoPlaybackBackendKind.Media3)
            .toLogLine()
        assertTrue(line.contains("selected=Mpv"))
        assertTrue(line.contains("actual=Media3"))
        assertTrue(line.contains("downgraded=true"))
    }

    @Test
    fun decisionRendersOneLineLog() {
        val request = VideoPlaybackBackendRequest(isCasting = true)
        val line = PlaybackEngineDecision
            .from(request, selected = VideoPlaybackBackendKind.Media3, actual = VideoPlaybackBackendKind.Media3)
            .toLogLine()
        assertTrue(line.contains("actual=Media3"))
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
    val actual: VideoPlaybackBackendKind,
    val reason: String,
    val contentId: String?,
    val fileId: Int?,
) {
    val downgraded: Boolean get() = selected != actual

    fun toLogLine(): String =
        "playback-engine selected=$selected actual=$actual downgraded=$downgraded " +
            "reason=$reason contentId=$contentId fileId=$fileId"

    companion object {
        fun from(
            request: VideoPlaybackBackendRequest,
            selected: VideoPlaybackBackendKind,
            actual: VideoPlaybackBackendKind,
        ) = PlaybackEngineDecision(
            selected = selected,
            actual = actual,
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

- [ ] **Step 5: Emit the decision** at the Task-0 engine-owner boundary (and in the factory, which computes `actual` by downgrading to Media3 unless the bound player `is MpvPlayer`, `VideoPlaybackBackendFactory.kt:23`). Build `PlaybackEngineDecision.from(request, selected, actual)` — where `actual` is the engine actually bound to the session — and log `decision.toLogLine()`. **Logging `selected` alone is a trap** (per Codex): the factory can downgrade MPV→Media3, so a log must show `actual` or it will claim MPV while Media3 plays. Also log display-mode change/restore + HDR/passthrough outcomes (Task 5), one structured line per event.

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

- [ ] **Step 5: Wire into `HdrDisplayController`** — `applyForMedia(...)` currently takes only width/height/fps (`HdrDisplayController.kt:72`) and has **no content-HDR parameter**, so add a `contentHdr: HdrType` parameter and update the caller `TvPlayerScreen.kt:464` (and any phone caller). On API-34+, read `display.mode.supportedHdrTypes` (map to `HdrType`), call `HdrModeSelection.choose(contentHdr, supported)`, and factor the chosen HDR type into mode selection; preserve the existing original-mode capture (`HdrDisplayController.kt:56`), `preferredDisplayModeId` apply (`HdrDisplayController.kt:91`), and `originalModeId` restore (`HdrDisplayController.kt:105`). Below API-34, keep current implicit negotiation (choose() returns SDR so no unsupported mode is forced). Log the chosen HDR type + applied/restored mode as one structured line (Task 4).

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

- [ ] **Step 2b: Cross-cutting playback-integration checks** (per Codex review — required for a real go/no-go, because MPV owns the session under Task 0 and these are where engine-swap regressions hide):
  - **Seek / trickplay:** phone seek (`PlayerScreen.kt:529`) and TV skip/scrub/chapter (`TvPlayerScreen.kt:592,716,816`) against MPV `seekTo`/speed (`mpv/MpvPlayer.kt:822`).
  - **MediaSession / notification / transport:** the service publishes one player via `MediaSession.Builder(this, player)` (`ContinuumPlaybackService.kt:98`); after an MPV switch, verify notification, lock-screen, BT remote, headset, and TV transport controls all drive the MPV-backed session.
  - **Audio focus / noisy / lifecycle:** ExoPlayer handles focus/noisy internally (`ContinuumPlayerFactory.kt:148`) while MPV requests/abandons focus itself (`MpvPlayer.kt:251,789,898`) — test focus loss/gain, BT/headset/unplug (noisy), background→return.
  - **HDMI hotplug / AVR change mid-playback:** capability flow is driven by route changes (`AudioCapabilityManager.kt:19`) and TV reapplies presets on audio/HDR change (`TvPlayerScreen.kt:365`) — test AVR power-cycle, EDID renegotiation, refresh restore, HDR toggle, audio-route change while playing.
  - **Startup-failure injection:** force MPV `init`/load failure and verify exactly one fallback to Media3 with a structured reason (Task 3) and a working session — not merely "no crash."

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
- **Keystone first (Task 0).** Codex's plan review proved the original draft hardened the selector while the MediaSession service (`ContinuumPlaybackService`) always built ExoPlayer and `createMpvPlayer()` was dead code — so MPV was unreachable and tests would pass with no runtime effect. Task 0 (engine-ownership/switch boundary: build the chosen engine, `setPlayer` rebind at mount + on fallback) is now the prerequisite for Tasks 1–5.
- Pure-logic deliverables (selector axes, device floor, fallback reducer, decision record incl. `actual`, HDR selection, command round-trip) are full failing-test-first TDD against real existing types and match the existing `VideoPlaybackBackendSelectorTest` style.
- Integration parts (Task 0 session rebind, fallback wiring at the real sites `createMpvPlayer`/`mountVideoMedia`, HDR `applyForMedia` signature + `TvPlayerScreen` caller, production mount-time request wiring) are implemented against the existing seam, guarded by source-assertion tests (repo convention), and **proven by the expanded Task 6 device matrix** (seek/trickplay, MediaSession/transport, audio-focus/noisy/lifecycle, HDMI/AVR hotplug, startup-failure injection).
- Type consistency: `mpvSupportedOnDevice`, `isCasting`, `isDrmProtected`, `isExternalDisplay` defined in Tasks 1–2 and reused in Task 4's `reasonFor` and Task 0's command round-trip. `PlaybackEngineDecision.from(request, selected, actual)` is used consistently (Task 0 emit + Task 4). `MpvDeviceFloor.MIN_SDK_FOR_MPV` is provisional, revised by Task 6.
