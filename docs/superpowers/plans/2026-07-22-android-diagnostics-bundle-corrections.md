# Android Diagnostics Bundle Corrections Implementation Plan

> Execute test-first in the existing `feat/android-client-diagnostics` worktree. Preserve the privacy constraints in the approved correction design and the parent diagnostics design.

**Goal:** Make Android diagnostic bundles accurately report safe playback, log, and network evidence without broadening captured private data.

**Architecture:** Add narrow typed metadata at its source, retain only bounded playback-session correlation state, and derive manifest summaries from the final sanitized log artifact. Keep all fallback behavior fail-closed: unknown reasons and routes become generic, and malformed log lines contribute no inferred categories.

**Tech stack:** Kotlin 2.1, kotlinx.serialization, Koin, JUnit/Kotlin test, Gradle.

---

## Task 1: Make token and route redaction precise

**Files:**

- Modify: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/diagnostics/DiagnosticsRedactor.kt`
- Modify: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/diagnostics/DiagnosticsInstrumentation.kt`
- Test: `android-shared/src/androidUnitTest/kotlin/org/prairieserver/prairie/common/diagnostics/DiagnosticsRedactorTest.kt`
- Test: `android-shared/src/androidUnitTest/kotlin/org/prairieserver/prairie/common/diagnostics/DiagnosticsInstrumentationTest.kt`

1. Add failing tests proving a structurally valid JWT is redacted, `c2.android.aac.decoder` is preserved, known static playback routes remain visible, dynamic IDs become `{id}`, and unknown/query-bearing routes fail closed.
2. Run the two focused test classes and confirm the new assertions fail.
3. Replace substring-only JWT matching with candidate matching plus base64url JSON-object validation for header and payload.
4. Replace the coarse resource sanitizer with ordered, allowlisted route templates that preserve only known literal segments.
5. Re-run the focused tests and commit the passing change.

## Task 2: Derive exact log summaries

**Files:**

- Create: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/diagnostics/DiagnosticsLogSummaryBuilder.kt`
- Modify: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/diagnostics/DiagnosticsManualCapture.kt`
- Modify: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/diagnostics/DiagnosticsBundleBuilder.kt`
- Create: `android-shared/src/androidUnitTest/kotlin/org/prairieserver/prairie/common/diagnostics/DiagnosticsLogSummaryBuilderTest.kt`
- Modify: `android-shared/src/androidUnitTest/kotlin/org/prairieserver/prairie/common/diagnostics/DiagnosticsBundleBuilderTest.kt`
- Modify: `android-shared/src/androidUnitTest/kotlin/org/prairieserver/prairie/common/diagnostics/DiagnosticsCoordinatorTest.kt`

1. Add failing tests for distinct categories, line counts including malformed lines, non-zero gzip size, empty input, and bundle-time recomputation after final redaction.
2. Run the focused diagnostics tests and confirm the new assertions fail.
3. Implement a pure summary builder over redacted JSONL with bounded parsing and gzip measurement.
4. Use it during manual capture and recompute against the final sanitized `logs.jsonl` during bundle construction.
5. Re-run the focused tests and commit the passing change.

## Task 3: Retain privacy-scoped playback session IDs

**Files:**

- Create: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/diagnostics/DiagnosticsPlaybackSessionTracker.kt`
- Modify: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/diagnostics/DiagnosticsModule.kt`
- Modify: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/diagnostics/DiagnosticsManualCapture.kt`
- Modify: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/diagnostics/DiagnosticsRuntime.kt`
- Modify: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/di/PlayerInfraModule.kt`
- Modify: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/player/PlaybackSessionLifecycle.kt`
- Create: `android-shared/src/androidUnitTest/kotlin/org/prairieserver/prairie/common/diagnostics/DiagnosticsPlaybackSessionTrackerTest.kt`
- Modify: relevant capture, runtime, and lifecycle tests.

1. Add failing tests for deduplication, newest-20 bounds, 128-character bounds, manual/crash snapshot inclusion, session recording on start/adoption, and clearing at the diagnostics privacy gate.
2. Run the focused tests and confirm the new assertions fail.
3. Implement the thread-safe tracker and inject it into capture/runtime/lifecycle paths through narrow recording and snapshot interfaces.
4. Clear the tracker before closing an identity or consent gate and populate both manual manifests and pre-rendered crash state from its snapshot.
5. Re-run the focused tests and commit the passing change.

## Task 4: Surface safe playback failure codes

**Files:**

- Modify: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/player/video/VideoPlaybackStartResult.kt`
- Modify: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/player/video/VideoPlaybackSessionCoordinator.kt`
- Modify: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/diagnostics/DiagnosticsInstrumentation.kt`
- Modify: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/diagnostics/PrairieLog.kt`
- Modify: `androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/player/MobileVideoPlaybackStarter.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvVideoPlaybackStarter.kt`
- Modify: `android-shared/src/androidUnitTest/kotlin/org/prairieserver/prairie/common/player/video/VideoPlaybackSessionCoordinatorTest.kt`
- Modify or create focused starter tests as needed.

1. Add failing tests proving a terminal reason reaches a registered `failure_code` attribute, while user/server prose and exception messages never enter diagnostic output.
2. Run the focused tests and confirm the new assertions fail.
3. Add an optional typed diagnostics code to `VideoPlaybackStartResult.Error`, populate it from allowlisted client constants or normalized server terminal reasons, and expose it through a narrow playback logger method.
4. Keep the existing UI-facing error message behavior unchanged.
5. Re-run the focused tests and commit the passing change.

## Task 5: Capture whole-client and playback performance

**Files:**

- Create: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/diagnostics/DiagnosticsPerformanceRecorder.kt`
- Modify: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/diagnostics/DiagnosticsInstrumentation.kt`
- Modify: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/diagnostics/PrairieLog.kt`
- Modify: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/diagnostics/DiagnosticsManualCapture.kt`
- Modify: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/diagnostics/DiagnosticsModule.kt`
- Modify: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/player/PlaybackAnalyticsListener.kt`
- Modify: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/player/PlayerStatsSnapshot.kt`
- Modify: phone and TV application/activity startup wiring as required.
- Create: `android-shared/src/androidUnitTest/kotlin/org/prairieserver/prairie/common/diagnostics/DiagnosticsPerformanceRecorderTest.kt`
- Modify: player statistics and instrumentation tests.

1. Add failing pure tests for frame histogram aggregation, refresh-aware slow-frame classification, main-thread stall aggregation, capture gating, startup timing retention, resource bounds, and playback startup/rebuffer/final summary reduction.
2. Run the focused tests and confirm the new assertions fail.
3. Implement the recorder with native Android lifecycle/frame/memory/thermal APIs, aggregate on a background handler, and emit numeric snapshots every ten seconds only during foreground detailed capture.
4. Extend the Media3 analytics reducer and registered playback attributes with startup, buffer, rebuffer, bandwidth, dropped-frame, and underrun aggregates; emit a final bounded snapshot on playback end only when detailed capture remains enabled.
5. Wire the recorder into both applications and capture gate transitions, then re-run the focused tests and commit the passing change.

## Task 6: Regression verification and PR update

1. Run diagnostics and player unit tests for `android-shared`.
2. Run the full Android shared unit-test task.
3. Build both debug APK variants without installing or launching either app.
4. Run `git diff --check`, inspect the complete branch diff, and confirm no generated files, logs, credentials, URLs, or bundle artifacts are tracked.
5. Push the branch to update PR #94 without triggering any separate manual build workflow.
