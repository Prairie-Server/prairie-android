# PR 108 Hosted CI Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make stacked PRs #116 and #117 deterministic under the hosted two-worker unit-test schedule without changing production behavior or the approved no-delta Slice G resolution.

**Architecture:** Correct the omitted Slice-E test-harness controls at their source. Tests that deliberately inspect an unresolved publication inject `PlaybackSessionManager.NEVER_SELF_HEAL`, while a dedicated test injects the production timeout and proves abandoned publications recover. Committed predecessor cleanup keeps the manager-owned IO scope in production, while tests inject their structured scope so completion is awaitable without wider wall-clock guards.

**Tech Stack:** Kotlin 2.1, kotlinx-coroutines-test, JUnit, Gradle 8.12, GitHub Actions.

## Global Constraints

- Make the correction in `split/108-e-subtitles`, then restack F and preserve local unpushed G.
- Do not change production timeout behavior.
- Do not merge any pull request.
- Run the GitHub Actions command locally with `testDebugUnitTest --max-workers=2`.

---

### Task 1: Isolate publication timeout semantics in the manager harness

**Files:**
- Modify: `android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/player/PlaybackSessionManagerStagedReplanTest.kt`

**Interfaces:**
- Consumes: `PlaybackSessionManager.NEVER_SELF_HEAL` and `PENDING_PUBLICATION_SETTLE_TIMEOUT_MS`.
- Produces: deterministic settlement-wait tests plus explicit production self-heal coverage.

- [x] **Step 1: Preserve the hosted failure as RED evidence**

Record the #116 artifact assertion: expected one stop each for `s3`/`s4`, observed `s3` twice under the two-worker suite.

- [x] **Step 2: Add explicit self-heal coverage**

Add a test that creates an unresolved deferred publication, injects
`PENDING_PUBLICATION_SETTLE_TIMEOUT_MS`, starts new content, and asserts the
abandoned replacement is stopped and the new session becomes active.

- [x] **Step 3: Make waiting tests opt out of virtual-time self-healing**

Add a nullable `pendingPublicationSettleTimeoutMs` harness argument defaulting
to `NEVER_SELF_HEAL`, and pass it to `PlaybackSessionManager`.

- [x] **Step 4: Run the focused manager class**

Run:
`./gradlew :android-shared:testDebugUnitTest --tests 'org.siloserver.silo.common.player.PlaybackSessionManagerStagedReplanTest' --max-workers=2 --rerun-tasks`

Expected: PASS, including one-stop rollback and explicit self-heal tests.

### Task 2: Make committed-session cleanup structurally awaitable

**Files:**
- Modify: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/player/SubtitleTransactionIntegrationTest.kt`
- Modify: `android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/PlaybackSessionManager.kt`
- Modify: `android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/player/PlaybackSessionManagerStagedReplanTest.kt`

**Interfaces:**
- Consumes: manager-owned asynchronous predecessor cleanup.
- Produces: an optional caller-owned cleanup scope for deterministic tests while
  retaining the manager's long-lived IO scope as the production default.

- [x] **Step 1: Preserve the hosted failure as RED evidence**

Record the second #117 artifact timeout in `awaitStopped("s1")` after the exact
typed Media3 mount. The failure persisted for 30 seconds while #116 with the
same Slice-E code passed, disproving ordinary five-second hosted load.

- [x] **Step 2: Add a deterministic ownership regression**

Inject the test `backgroundScope`, suspend cleanup once, prove confirmation
returns before cleanup, await entry through the test scheduler, then release it
and prove the predecessor stops exactly once. The exact TV integration harness
injects the same scope.

- [x] **Step 3: Preserve production semantics**

Keep the existing manager-owned IO scope as the default. Route only committed
predecessor cleanup through an optional injected scope; route telemetry
unchanged. Restore the integration deadlock guard to five seconds.

- [x] **Step 4: Run the focused manager and integration classes**

Run:
`./gradlew :android-shared:testDebugUnitTest --tests 'org.siloserver.silo.common.player.PlaybackSessionManagerStagedReplanTest' --max-workers=2 --rerun-tasks`

and:
`./gradlew :androidTvApp:testDebugUnitTest --tests 'org.siloserver.silo.tv.ui.screens.player.SubtitleTransactionIntegrationTest' --max-workers=2 --rerun-tasks`

Expected: PASS.

### Task 3: Verify, review, restack, and publish

**Files:**
- Modify through git ancestry only: `split/108-f-watch-together`
- Preserve locally: `split/108-g-tv-catalog` and `b653253f`

**Interfaces:**
- Consumes: corrected Slice-E commit.
- Produces: updated #116/#117 heads with unchanged G resolution.

- [x] **Step 1: Run the CI-equivalent gate**

Run:
`./gradlew -Dorg.gradle.jvmargs="-Xmx4g -Dfile.encoding=UTF-8" testDebugUnitTest --max-workers=2 --rerun-tasks --no-daemon`

Expected: BUILD SUCCESSFUL with zero failing tests.

- [x] **Step 2: Request independent review**

Review the Slice-E repair diff for timeout masking, lost production self-heal
coverage, coroutine scheduling mistakes, and stack ancestry.

- [ ] **Step 3: Commit and restack**

Commit the Slice-E correction, rebase `split/108-f-watch-together` onto it, and
rebase local `split/108-g-tv-catalog` onto the new F head while retaining its
traceability commit.

- [ ] **Step 4: Push only E and F**

Push `split/108-e-subtitles` and `split/108-f-watch-together`. Do not push G and
do not merge.

- [ ] **Step 5: Confirm hosted green**

Watch the new #116/#117 checks to completion and report exact conclusions.
