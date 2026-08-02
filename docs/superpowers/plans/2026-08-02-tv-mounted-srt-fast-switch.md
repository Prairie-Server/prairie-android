# TV Mounted SRT Fast Switching Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Switch an already-mounted Android TV SRT sidecar through Media3 track selection without staging a new playback session or re-preparing video.

**Architecture:** Extend the transaction adapter's locally mountable identity gate to admit `ServerSidecar`. The injected typed mounted-track resolver remains authoritative: a sidecar takes the shortcut only when the exact identity exists in the live Media3 snapshot, while all other cases retain the server-replan path.

**Tech Stack:** Kotlin 2.1, Android Media3, Kotlin coroutines/Flow, JUnit/Kotlin Test, Gradle.

## Global Constraints

- Do not change phone playback behavior.
- Do not change server burn-in or subtitle conversion decisions.
- Do not replace the active `MediaItem` for an already-mounted SRT switch.
- Preserve acknowledgement, rollback, persistence, supersession, and coupled audio/quality/output-route behavior.
- Keep unmounted or unresolved sidecars on the staged server-replan path.
- Build the ARM64 TV debug APK and install it on `192.168.1.128:5555` without launching it.

---

### Task 1: Route Mounted Server Sidecars Through Local Confirmation

**Files:**
- Modify: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/player/TvSubtitleTransactionAdapterTest.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/player/TvSubtitleTransactionAdapter.kt`

**Interfaces:**
- Consumes: `TvSubtitleTransactionAdapter(isLocallyMountable: (SubtitleIdentity) -> Boolean)` and `SubtitleIdentity.ServerSidecar`.
- Produces: `requiresLocalMountConfirmation(): Boolean` returns `true` for `ServerSidecar`; exact mounted-track resolution still decides whether `commitLocallyMountableSelection` succeeds.

- [ ] **Step 1: Add a failing mounted-sidecar regression test**

Add near the embedded local-selection tests:

```kotlin
@Test
fun `a server sidecar the player already exposes stays local`() = runTest {
    val target = sidecar(4)
    val harness = harness(
        backgroundScope,
        isLocallyMountable = { identity -> identity == target },
    )

    harness.adapter.select(target)
    runCurrent()

    assertTrue(
        harness.port.requests.isEmpty(),
        "an already-mounted sidecar must not ask the server to replan",
    )
    assertEquals(target, harness.adapter.snapshot.localMountIdentity)
    assertEquals(sidecar(3), harness.adapter.snapshot.committedIdentity)

    harness.adapter.reportMountedSelection(
        identity = target,
        selected = true,
        snapshotKey = "mounted-sidecar-selected",
        settled = true,
    )
    runCurrent()

    assertEquals(target, harness.adapter.snapshot.committedIdentity)
    assertNull(harness.adapter.snapshot.pendingIdentity)
    assertEquals(listOf(target), harness.persistence.persisted.map { it.identity })
}
```

- [ ] **Step 2: Add an unmounted-sidecar fallback regression test**

```kotlin
@Test
fun `a server sidecar the player cannot expose is staged to the server`() = runTest {
    val harness = harness(backgroundScope, isLocallyMountable = { false })

    harness.adapter.select(sidecar(4))
    runCurrent()

    assertEquals(
        listOf(4),
        harness.port.requests.map { it.subtitleTrackIndex },
        "an unmounted sidecar must retain the staged replan fallback",
    )
    assertNull(harness.adapter.snapshot.localMountIdentity)
}
```

Change the test harness default from an unconditional local result to a realistic non-sidecar default:

```kotlin
isLocallyMountable: (SubtitleIdentity) -> Boolean = { identity ->
    identity !is SubtitleIdentity.ServerSidecar
},
```

- [ ] **Step 3: Run the focused test and verify RED**

```bash
./gradlew :androidTvApp:testDebugUnitTest \
  --tests 'org.siloserver.silo.tv.ui.screens.player.TvSubtitleTransactionAdapterTest'
```

Expected: the mounted-sidecar test fails because it produces a staged request and no `localMountIdentity`. The fallback test passes.

- [ ] **Step 4: Implement the minimal production change**

```kotlin
private fun SubtitleIdentity.requiresLocalMountConfirmation(): Boolean =
    this is SubtitleIdentity.ServerSidecar ||
        this is SubtitleIdentity.LocalMedia3 ||
        this is SubtitleIdentity.Downloaded ||
        this is SubtitleIdentity.Embedded
```

Do not change `isClientOwnedSubtitle`, `serverTrackIndex`, staged validation, or media mounting.

- [ ] **Step 5: Run the focused test and verify GREEN**

Run the command from Step 3. Expected: all `TvSubtitleTransactionAdapterTest` tests pass.

- [ ] **Step 6: Run adjacent transaction tests**

```bash
./gradlew :androidTvApp:testDebugUnitTest \
  --tests 'org.siloserver.silo.tv.ui.screens.player.TvSubtitleSettlementOwnershipTest' \
  --tests 'org.siloserver.silo.tv.ui.screens.player.TvSubtitleFinalRollbackTest' \
  --tests 'org.siloserver.silo.tv.ui.screens.player.TvSubtitleMountDeadlineTest' \
  --tests 'org.siloserver.silo.tv.ui.screens.player.SubtitleTransactionIntegrationTest'
```

Expected: all transaction, rollback, timeout, and integration tests pass unchanged.

- [ ] **Step 7: Commit the tested behavior**

```bash
git add \
  androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/player/TvSubtitleTransactionAdapter.kt \
  androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/player/TvSubtitleTransactionAdapterTest.kt
git commit -m "fix(tv): switch mounted SRT subtitles without rebuffering"
```

---

### Task 2: Verify, Build, and Install Without Launching

**Files:**
- Verify: all repository sources and tests
- Build output: `androidTvApp/build/outputs/apk/debug/androidTvApp-arm64-v8a-debug.apk`

**Interfaces:**
- Consumes: the mounted-sidecar fast path from Task 1.
- Produces: a tested ARM64 debug APK installed on the Shield, with `org.siloserver.silo` force-stopped.

- [ ] **Step 1: Run complete verification**

```bash
./gradlew test :androidTvApp:assembleDebug
```

Expected: `BUILD SUCCESSFUL`; all tests pass and the TV debug APK is assembled.

- [ ] **Step 2: Check final repository state**

```bash
git diff --check
git status --short --branch
git log -4 --oneline
```

Expected: no whitespace errors or uncommitted implementation changes.

- [ ] **Step 3: Install the ARM64 debug APK**

```bash
adb -s 192.168.1.128:5555 install -r \
  androidTvApp/build/outputs/apk/debug/androidTvApp-arm64-v8a-debug.apk
```

Expected: `Success`. Do not issue `am start`, `monkey`, D-pad input, or navigation.

- [ ] **Step 4: Force-stop and verify the app remains closed**

```bash
adb -s 192.168.1.128:5555 shell am force-stop org.siloserver.silo
adb -s 192.168.1.128:5555 shell \
  'pidof org.siloserver.silo >/dev/null; code=$?; echo pidof_exit=$code; exit 0'
```

Expected: `pidof_exit=1`.

- [ ] **Step 5: Verify installed metadata**

```bash
adb -s 192.168.1.128:5555 shell dumpsys package org.siloserver.silo \
  | rg 'primaryCpuAbi=|versionCode=|versionName=|DEBUGGABLE'
```

Expected: `primaryCpuAbi=arm64-v8a` and `DEBUGGABLE`, with the current project version.

- [ ] **Step 6: Report completion**

Report focused/full verification, installed version and ABI, stopped-process proof, commit hashes, local upstream divergence, and that nothing was pushed.
