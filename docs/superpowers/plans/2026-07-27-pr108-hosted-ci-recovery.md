# PR 108 Hosted CI Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make stacked PRs #116 and #117 deterministic under the hosted two-worker unit-test schedule without changing production behavior or the approved no-delta Slice G resolution.

**Architecture:** Correct the omitted Slice-E test-harness controls at their source. Tests that deliberately inspect an unresolved publication inject `PlaybackSessionManager.NEVER_SELF_HEAL`, while a dedicated test injects the production timeout and proves abandoned publications recover. Cross-dispatcher event waits remain deadlock guards, but use a 30-second wall-clock budget below `runTest`'s 60-second ceiling.

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

- [ ] **Step 1: Preserve the hosted failure as RED evidence**

Record the #116 artifact assertion: expected one stop each for `s3`/`s4`, observed `s3` twice under the two-worker suite.

- [ ] **Step 2: Add explicit self-heal coverage**

Add a test that creates an unresolved deferred publication, injects
`PENDING_PUBLICATION_SETTLE_TIMEOUT_MS`, starts new content, and asserts the
abandoned replacement is stopped and the new session becomes active.

- [ ] **Step 3: Make waiting tests opt out of virtual-time self-healing**

Add a nullable `pendingPublicationSettleTimeoutMs` harness argument defaulting
to `NEVER_SELF_HEAL`, and pass it to `PlaybackSessionManager`.

- [ ] **Step 4: Run the focused manager class**

Run:
`./gradlew :android-shared:testDebugUnitTest --tests 'org.siloserver.silo.common.player.PlaybackSessionManagerStagedReplanTest' --max-workers=2 --rerun-tasks`

Expected: PASS, including one-stop rollback and explicit self-heal tests.

### Task 2: Make integration event waits load-tolerant

**Files:**
- Modify: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/player/SubtitleTransactionIntegrationTest.kt`

**Interfaces:**
- Consumes: real `Dispatchers.Default`/IO callbacks from publication confirmation and persistence.
- Produces: deterministic deadlock guards that still fail before `runTest`'s global timeout.

- [ ] **Step 1: Preserve the hosted failure as RED evidence**

Record the #117 artifact timeout in `awaitStopped("s1")` after the exact typed
Media3 mount, with 602 tests complete and one failure.

- [ ] **Step 2: Replace five-second cross-dispatcher guards**

Define `EVENT_TIMEOUT_MS = 30_000L` and use it only for manager IO stop and
orphan-drain waits. Keep test-scope replan, adoption, and persistence waits at
five seconds; do not alter adapter or manager production logic.

- [ ] **Step 3: Run the focused integration class**

Run:
`./gradlew :androidTvApp:testDebugUnitTest --tests 'org.siloserver.silo.tv.ui.screens.player.SubtitleTransactionIntegrationTest' --max-workers=2 --rerun-tasks`

Expected: PASS.

### Task 3: Verify, review, restack, and publish

**Files:**
- Modify through git ancestry only: `split/108-f-watch-together`
- Preserve locally: `split/108-g-tv-catalog` and `b653253f`

**Interfaces:**
- Consumes: corrected Slice-E commit.
- Produces: updated #116/#117 heads with unchanged G resolution.

- [ ] **Step 1: Run the CI-equivalent gate**

Run:
`./gradlew -Dorg.gradle.jvmargs="-Xmx4g -Dfile.encoding=UTF-8" testDebugUnitTest --max-workers=2 --rerun-tasks --no-daemon`

Expected: BUILD SUCCESSFUL with zero failing tests.

- [ ] **Step 2: Request independent review**

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
