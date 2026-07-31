# Android Client Review Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix all 13 confirmed correctness bugs and apply all verified cleanup findings (duplication, dead code, performance) from the 2026-06-12 branch review of `feature/android-parity-and-media-surfaces`.

**Architecture:** Tasks are grouped by subsystem — TV player key handling, downloads, readers, TV app wiring, shared taxonomy/models, and requests/dedup cleanup — and each task is independently committable. Duplicated mobile/TV policy logic moves into `shared/`; per-reader file caching is unified behind one atomic tmp+rename resolver; the TV app gets the same hand-rolled WorkerFactory recipe the phone app already uses.

**Tech Stack:** Kotlin Multiplatform (shared), Jetpack Compose (androidApp/androidTvApp), Media3, WorkManager, Koin, kotlinx.serialization, DataStore. Unit tests use kotlin-test (JUnit runner) in `androidUnitTest`/`commonTest` source sets; run with `./gradlew :<module>:testDebugUnitTest`.

**Context:** Two findings live in UNCOMMITTED working-tree changes (TvPlayerScreen.kt + the new TvPlayerRemoteKeyAction.kt/test) — Tasks 1–2 build on the working tree as-is and commit those files. All other findings are on committed branch code.

---

## Section A: TV player remote key handling (uncommitted work)

### Task A1: Fix double play/pause toggle — act on key DOWN, consume key UP

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerRemoteKeyAction.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerRemoteKeyActionTest.kt`

- [ ] **Step 1: Write the failing test**

Replace the full contents of `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerRemoteKeyActionTest.kt` with:

```kotlin
package com.continuum.app.tv.ui.screens.player

import android.view.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvPlayerRemoteKeyActionTest {

    @Test
    fun mediaPlayPauseKeysTogglePlaybackOnKeyDown() {
        listOf(
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        ).forEach { keyCode ->
            assertEquals(
                TvPlayerRemoteKeyAction.PlayPause,
                tvPlayerRemoteKeyAction(
                    keyCode = keyCode,
                    action = KeyEvent.ACTION_DOWN,
                    controlsVisible = true,
                    hudOpen = false,
                ),
            )
        }
    }

    @Test
    fun mediaPlayPauseKeyUpIsConsumedWithoutTogglingPlayback() {
        listOf(
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        ).forEach { keyCode ->
            assertEquals(
                TvPlayerRemoteKeyAction.ConsumeOnly,
                tvPlayerRemoteKeyAction(
                    keyCode = keyCode,
                    action = KeyEvent.ACTION_UP,
                    controlsVisible = true,
                    hudOpen = false,
                ),
            )
        }
    }

    @Test
    fun downMovesFocusToTransportAndMenuOpensHudFromIdleOverlay() {
        assertEquals(
            TvPlayerRemoteKeyAction.FocusTransport,
            tvPlayerRemoteKeyAction(
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN,
                action = KeyEvent.ACTION_DOWN,
                controlsVisible = true,
                hudOpen = false,
            ),
        )
        assertEquals(
            TvPlayerRemoteKeyAction.OpenHud,
            tvPlayerRemoteKeyAction(
                keyCode = KeyEvent.KEYCODE_MENU,
                action = KeyEvent.ACTION_UP,
                controlsVisible = true,
                hudOpen = false,
            ),
        )
    }

    @Test
    fun nonMatchingActionsAndUnhandledKeysFallThrough() {
        assertNull(
            tvPlayerRemoteKeyAction(
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN,
                action = KeyEvent.ACTION_UP,
                controlsVisible = true,
                hudOpen = false,
            ),
        )
        assertNull(
            tvPlayerRemoteKeyAction(
                keyCode = KeyEvent.KEYCODE_MENU,
                action = KeyEvent.ACTION_DOWN,
                controlsVisible = true,
                hudOpen = false,
            ),
        )
        assertNull(
            tvPlayerRemoteKeyAction(
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
                action = KeyEvent.ACTION_DOWN,
                controlsVisible = true,
                hudOpen = false,
            ),
        )
    }

    @Test
    fun idleOverlayShortcutsDoNotFireWhileHudIsOpenOrOverlayHidden() {
        assertNull(
            tvPlayerRemoteKeyAction(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                action = KeyEvent.ACTION_UP,
                controlsVisible = false,
                hudOpen = false,
            ),
        )
        assertNull(
            tvPlayerRemoteKeyAction(
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                action = KeyEvent.ACTION_UP,
                controlsVisible = true,
                hudOpen = true,
            ),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests "com.continuum.app.tv.ui.screens.player.TvPlayerRemoteKeyActionTest"
```

Expected failure: the build fails at `:androidTvApp:compileDebugUnitTestKotlinAndroid` with `Unresolved reference 'ConsumeOnly'` (the enum value does not exist yet). This compile failure is the failing-test signal for the contract change; after the enum gains `ConsumeOnly` but before the DOWN/UP logic flips, `mediaPlayPauseKeysTogglePlaybackOnKeyDown` would fail with `expected: PlayPause, actual: null`.

- [ ] **Step 3: Implementation**

Full new content of `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerRemoteKeyAction.kt`:

```kotlin
package com.continuum.app.tv.ui.screens.player

import android.view.KeyEvent

internal enum class TvPlayerRemoteKeyAction {
    PlayPause,
    FocusTransport,
    OpenHud,
    ConsumeOnly,
}

internal fun tvPlayerRemoteKeyAction(
    keyCode: Int,
    action: Int,
    controlsVisible: Boolean,
    hudOpen: Boolean,
): TvPlayerRemoteKeyAction? {
    if (!controlsVisible || hudOpen) return null

    return when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        -> if (action == KeyEvent.ACTION_DOWN) {
            TvPlayerRemoteKeyAction.PlayPause
        } else {
            TvPlayerRemoteKeyAction.ConsumeOnly
        }

        KeyEvent.KEYCODE_DPAD_DOWN ->
            if (action == KeyEvent.ACTION_DOWN) TvPlayerRemoteKeyAction.FocusTransport else null

        KeyEvent.KEYCODE_MENU,
        KeyEvent.KEYCODE_SETTINGS,
        -> if (action == KeyEvent.ACTION_UP) TvPlayerRemoteKeyAction.OpenHud else null

        else -> null
    }
}
```

In `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt` (inside `TvPlayerIdleOverlay`, the `onPreviewKeyEvent` `when` at lines 606-619), change:

Before:
```kotlin
                    TvPlayerRemoteKeyAction.OpenHud -> {
                        onOpenHUD()
                        true
                    }
                    null -> false
```

After:
```kotlin
                    TvPlayerRemoteKeyAction.OpenHud -> {
                        onOpenHUD()
                        true
                    }
                    TvPlayerRemoteKeyAction.ConsumeOnly -> true
                    null -> false
```

This makes the overlay perform play/pause on ACTION_DOWN (matching framework media-session behavior) and return `true` for the matching ACTION_UP, so neither half of the key press leaks to the system media-key fallback that was toggling the Media3 MediaSession a second time.

- [ ] **Step 4: Run tests to verify pass**

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests "com.continuum.app.tv.ui.screens.player.TvPlayerRemoteKeyActionTest"
```

Expected: `BUILD SUCCESSFUL`, all 5 tests pass.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerRemoteKeyAction.kt androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerRemoteKeyActionTest.kt && git commit -m "fix(tv): toggle play/pause on key down and consume key up in player idle overlay

Media play/pause keys previously acted only on ACTION_UP, so the
unhandled ACTION_DOWN leaked to the system media-key fallback and
toggled the Media3 MediaSession too — a double toggle per press.
Act on DOWN and consume the matching UP via a new ConsumeOnly result.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Task A2: Remove dead controlsVisible/hudOpen guard from tvPlayerRemoteKeyAction

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerRemoteKeyAction.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerRemoteKeyActionTest.kt`

The guard is dead code: the only call site passes literal `controlsVisible = true, hudOpen = false`, because `TvPlayerIdleOverlay` is only composed when `state.showControls && !state.hudOpen` (TvPlayerScreen.kt line 421).

- [ ] **Step 1: Write the failing test**

Replace the full contents of `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerRemoteKeyActionTest.kt` with (drops the two guard params from every call and deletes the now-meaningless `idleOverlayShortcutsDoNotFireWhileHudIsOpenOrOverlayHidden` test — visibility gating is enforced structurally by composition, not by this function):

```kotlin
package com.continuum.app.tv.ui.screens.player

import android.view.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvPlayerRemoteKeyActionTest {

    @Test
    fun mediaPlayPauseKeysTogglePlaybackOnKeyDown() {
        listOf(
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        ).forEach { keyCode ->
            assertEquals(
                TvPlayerRemoteKeyAction.PlayPause,
                tvPlayerRemoteKeyAction(
                    keyCode = keyCode,
                    action = KeyEvent.ACTION_DOWN,
                ),
            )
        }
    }

    @Test
    fun mediaPlayPauseKeyUpIsConsumedWithoutTogglingPlayback() {
        listOf(
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        ).forEach { keyCode ->
            assertEquals(
                TvPlayerRemoteKeyAction.ConsumeOnly,
                tvPlayerRemoteKeyAction(
                    keyCode = keyCode,
                    action = KeyEvent.ACTION_UP,
                ),
            )
        }
    }

    @Test
    fun downMovesFocusToTransportAndMenuOpensHudFromIdleOverlay() {
        assertEquals(
            TvPlayerRemoteKeyAction.FocusTransport,
            tvPlayerRemoteKeyAction(
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN,
                action = KeyEvent.ACTION_DOWN,
            ),
        )
        assertEquals(
            TvPlayerRemoteKeyAction.OpenHud,
            tvPlayerRemoteKeyAction(
                keyCode = KeyEvent.KEYCODE_MENU,
                action = KeyEvent.ACTION_UP,
            ),
        )
    }

    @Test
    fun nonMatchingActionsAndUnhandledKeysFallThrough() {
        assertNull(
            tvPlayerRemoteKeyAction(
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN,
                action = KeyEvent.ACTION_UP,
            ),
        )
        assertNull(
            tvPlayerRemoteKeyAction(
                keyCode = KeyEvent.KEYCODE_MENU,
                action = KeyEvent.ACTION_DOWN,
            ),
        )
        assertNull(
            tvPlayerRemoteKeyAction(
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
                action = KeyEvent.ACTION_DOWN,
            ),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests "com.continuum.app.tv.ui.screens.player.TvPlayerRemoteKeyActionTest"
```

Expected failure: build fails at `:androidTvApp:compileDebugUnitTestKotlinAndroid` with `No value passed for parameter 'controlsVisible'` (and `'hudOpen'`) — the production function still requires the dead params.

- [ ] **Step 3: Implementation**

Full new content of `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerRemoteKeyAction.kt`:

```kotlin
package com.continuum.app.tv.ui.screens.player

import android.view.KeyEvent

internal enum class TvPlayerRemoteKeyAction {
    PlayPause,
    FocusTransport,
    OpenHud,
    ConsumeOnly,
}

internal fun tvPlayerRemoteKeyAction(
    keyCode: Int,
    action: Int,
): TvPlayerRemoteKeyAction? = when (keyCode) {
    KeyEvent.KEYCODE_MEDIA_PLAY,
    KeyEvent.KEYCODE_MEDIA_PAUSE,
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
    -> if (action == KeyEvent.ACTION_DOWN) {
        TvPlayerRemoteKeyAction.PlayPause
    } else {
        TvPlayerRemoteKeyAction.ConsumeOnly
    }

    KeyEvent.KEYCODE_DPAD_DOWN ->
        if (action == KeyEvent.ACTION_DOWN) TvPlayerRemoteKeyAction.FocusTransport else null

    KeyEvent.KEYCODE_MENU,
    KeyEvent.KEYCODE_SETTINGS,
    -> if (action == KeyEvent.ACTION_UP) TvPlayerRemoteKeyAction.OpenHud else null

    else -> null
}
```

In `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt` (the `onPreviewKeyEvent` in `TvPlayerIdleOverlay`), change:

Before:
```kotlin
            .onPreviewKeyEvent { event ->
                when (
                    tvPlayerRemoteKeyAction(
                        keyCode = event.nativeKeyEvent.keyCode,
                        action = event.nativeKeyEvent.action,
                        controlsVisible = true,
                        hudOpen = false,
                    )
                ) {
```

After:
```kotlin
            .onPreviewKeyEvent { event ->
                when (
                    tvPlayerRemoteKeyAction(
                        keyCode = event.nativeKeyEvent.keyCode,
                        action = event.nativeKeyEvent.action,
                    )
                ) {
```

The rest of the `when` branches (added in Task 1, including `TvPlayerRemoteKeyAction.ConsumeOnly -> true`) are unchanged.

- [ ] **Step 4: Run tests to verify pass**

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests "com.continuum.app.tv.ui.screens.player.TvPlayerRemoteKeyActionTest"
```

Expected: `BUILD SUCCESSFUL`, all 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerRemoteKeyAction.kt androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerRemoteKeyActionTest.kt && git commit -m "refactor(tv): drop dead visibility guard from tvPlayerRemoteKeyAction

The only call site is composed exclusively when showControls && !hudOpen,
so controlsVisible/hudOpen were always true/false literals.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

Notes verified against the repo: module is `:androidTvApp` (settings.gradle.kts), unit tests live in the `androidUnitTest` source set with kotlin-test-junit (androidTvApp/build.gradle.kts lines 66-69), and `./gradlew :androidTvApp:testDebugUnitTest` was confirmed via `--dry-run` to be a real task. `android.view.KeyEvent` constants resolve in plain JVM unit tests via `isReturnDefaultValues = true` (build.gradle.kts line 114), as the existing test already relies on.

## Section B: Download subsystem

### Task B1: DownloadWorker — stop treating cancellation as failure

**Files:**
- Modify: `android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadWorker.kt:26-29` (imports), `138-162` (catch chain)
- Test: none (manual verification — see Step 1)

- [ ] **Step 1: Write the failing test** — No unit test feasible: `DownloadWorker.doWork()` requires real `WorkerParameters` and a running WorkManager to exercise `setProgress`/`setForeground` cancellation; the repo has neither `androidx.work:work-testing` nor Robolectric in `android-shared`'s `androidUnitTest` dependencies, and `CoroutineWorker` cannot be constructed by hand. Manual check instead (Step 2 of verification below). Context verified: `DownloadEnqueuer.cancel(downloadId)` (DownloadEnqueuer.kt:304-307) only calls `WorkManager.cancelAllWorkByTag` — it does **not** clean up partial files or sidecars, so the worker's cancellation handler must drop the partial file itself (safe for the constraint-stop path too, because `DownloadStorage.prepareWrite` deletes any existing file before the retry attempt starts streaming). Crucially, the handler must NOT write `status=Failed` to the repository or sidecar: a constraint-stop needs the existing `downloading` state intact for the WorkManager retry, and a user cancel is finalized by the record-delete path (Tasks 3/4 of this section), not the worker.

- [ ] **Step 2: Run test to verify it fails** — N/A (no unit test). Pre-fix repro to confirm the bug exists: start a multi-GB download on a Wi-Fi-only constraint, then toggle Wi-Fi off mid-download (or tap the notification's Cancel action). Observe in logcat: `DownloadWorker: doWork fatal` with a `JobCancellationException`/`CancellationException` stack, the Downloads row flips to the red Failed badge, and the sidecar JSON under `filesDir/downloads/<serverId>/<profileId>/<fileId>.record.json` reads `"status": "failed"`.

- [ ] **Step 3: Implementation** — Add two imports and a `CancellationException` catch ahead of the existing catches.

  Imports block (lines 26-29) becomes:
  ```kotlin
  import kotlinx.coroutines.CancellationException
  import kotlinx.coroutines.Dispatchers
  import kotlinx.coroutines.NonCancellable
  import kotlinx.coroutines.withContext
  import java.io.IOException
  import kotlin.math.abs
  ```

  Complete new catch chain (replacing lines 138-162; the `try` body is unchanged):
  ```kotlin
          } catch (e: CancellationException) {
              // Worker stopped — user cancel (notification action /
              // DownloadEnqueuer.cancel → cancelAllWorkByTag) or a
              // constraint / quota stop. Not a failure: drop the partial
              // bytes (prepareWrite starts clean on the next attempt
              // anyway) but leave the repo record + sidecar status alone.
              // A constraint-stop must keep "downloading" state so the
              // WorkManager retry restarts cleanly; a user cancel is
              // finalized by the record-delete path, not here. Writing
              // Failed here is what used to paint cancelled / paused
              // downloads with a red badge and delete-then-fail them.
              Log.i(TAG, "doWork cancelled id=$downloadId")
              withContext(NonCancellable) {
                  runCatching { storage.deleteUri(target.uriString) }
              }
              throw e
          } catch (e: IOException) {
              // Transient — let WorkManager retry. Drop the partial file so the
              // next attempt starts fresh (no resume in v1). Sidecar stays so
              // the row is visible in the UI as "downloading" awaiting retry.
              Log.w(TAG, "doWork IO error id=$downloadId → retry", e)
              runCatching { storage.deleteUri(target.uriString) }
              Result.retry()
          } catch (e: Throwable) {
              // Permanent — clean up local file and let the user retry manually.
              Log.e(TAG, "doWork fatal id=$downloadId", e)
              runCatching { storage.deleteUri(target.uriString) }
              // Best-effort: publish failed state into the repo + sidecar.
              val record = repository.recordForFile(fileId)
              if (record != null) {
                  repository.upsertLocal(record.copy(status = DownloadStatus.Failed.wire))
              }
              updateSidecarStatus(
                  serverId, profileId, fileId,
                  status = DownloadStatus.Failed.wire,
                  bytesSent = 0,
                  fileSize = 0,
                  localUri = target.uriString,
              )
              Result.failure()
          }
  ```

- [ ] **Step 4: Run tests** — `./gradlew :android-shared:testDebugUnitTest` (regression sweep; expect all existing tests green). Manual verification: repeat the Step 2 repro — logcat must show `doWork cancelled id=…` with no `doWork fatal`, the row must NOT show the Failed badge, the sidecar must still say `downloading`, and after Wi-Fi returns the constraint-stopped work must restart and complete.

- [ ] **Step 5: Commit**
  ```bash
  git add android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadWorker.kt
  git commit -m "$(cat <<'EOF'
  Rethrow worker cancellation instead of failing the download

  Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
  EOF
  )"
  ```

### Task B2: DownloadWorker — time-only progress throttle, percent-gated setForeground

**Files:**
- Create: `android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadProgressThrottle.kt`
- Modify: `android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadWorker.kt:29` (drop `kotlin.math.abs` import), `31-43` (kdoc), `73-119` (copy loop), `236-239` (companion constants)
- Test: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/downloads/DownloadProgressThrottleTest.kt`

- [ ] **Step 1: Write the failing test** — New file `DownloadProgressThrottleTest.kt`:
  ```kotlin
  package com.continuum.app.common.downloads

  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertFalse
  import kotlin.test.assertTrue

  class DownloadProgressThrottleTest {

      @Test
      fun `first bytes report and update the foreground notification`() {
          val throttle = DownloadProgressThrottle(intervalMs = 200)
          val decision = throttle.onBytes(nowMs = 0, written = 1, total = 1000)
          assertTrue(decision.report)
          assertTrue(decision.updateForeground)
          assertEquals(0, decision.percent)
      }

      @Test
      fun `large byte deltas inside the interval do not report`() {
          // Regression: the old gate also fired on every 1 MB of bytes, which
          // on a fast network meant ~100 notification rebuilds per second.
          val throttle = DownloadProgressThrottle(intervalMs = 200)
          throttle.onBytes(nowMs = 0, written = 1, total = 10_000_000_000)
          for (mb in 1..50) {
              val decision = throttle.onBytes(nowMs = 100, written = mb * 1_048_576L, total = 10_000_000_000)
              assertFalse(decision.report)
              assertFalse(decision.updateForeground)
          }
      }

      @Test
      fun `report after the interval without a percent change skips the foreground update`() {
          val throttle = DownloadProgressThrottle(intervalMs = 200)
          throttle.onBytes(nowMs = 0, written = 100, total = 1_000_000)                     // 0%
          val decision = throttle.onBytes(nowMs = 250, written = 200, total = 1_000_000)    // still 0%
          assertTrue(decision.report)
          assertFalse(decision.updateForeground)
      }

      @Test
      fun `report after the interval with a percent change updates the foreground`() {
          val throttle = DownloadProgressThrottle(intervalMs = 200)
          throttle.onBytes(nowMs = 0, written = 0, total = 1000)                            // 0%
          val decision = throttle.onBytes(nowMs = 250, written = 370, total = 1000)         // 37%
          assertTrue(decision.report)
          assertTrue(decision.updateForeground)
          assertEquals(37, decision.percent)
      }

      @Test
      fun `unknown total reports zero percent with a single foreground update`() {
          val throttle = DownloadProgressThrottle(intervalMs = 200)
          val first = throttle.onBytes(nowMs = 0, written = 500, total = -1)
          assertTrue(first.updateForeground)
          assertEquals(0, first.percent)
          val second = throttle.onBytes(nowMs = 300, written = 5_000_000, total = -1)
          assertTrue(second.report)
          assertFalse(second.updateForeground)
      }

      @Test
      fun `percent clamps and tolerates zero total`() {
          assertEquals(100, DownloadProgressThrottle.percentOf(written = 2000, total = 1000))
          assertEquals(0, DownloadProgressThrottle.percentOf(written = 2000, total = 0))
      }
  }
  ```

- [ ] **Step 2: Run test to verify it fails** — `./gradlew :android-shared:testDebugUnitTest --tests com.continuum.app.common.downloads.DownloadProgressThrottleTest` — expected failure: compilation error `Unresolved reference: DownloadProgressThrottle` (the class does not exist yet).

- [ ] **Step 3: Implementation**

  New file `DownloadProgressThrottle.kt`:
  ```kotlin
  package com.continuum.app.common.downloads

  /**
   * Pure time-based throttle for [DownloadWorker] progress reporting.
   *
   * - [Decision.report] gates the per-tick work (`setProgress` + the
   *   repository upsert): at most once per [intervalMs].
   * - [Decision.updateForeground] gates the expensive work (rebuilding the
   *   notification + `createCancelPendingIntent` via `setForeground`):
   *   only when the integer percent actually changed since the last
   *   foreground update.
   *
   * The old gate also fired on every 1 MB of bytes, which on a fast
   * network meant ~100 notification rebuilds per second.
   */
  class DownloadProgressThrottle(
      private val intervalMs: Long = DEFAULT_INTERVAL_MS,
  ) {

      data class Decision(
          val report: Boolean,
          val updateForeground: Boolean,
          val percent: Int,
      )

      private var lastReportedMs = -intervalMs
      private var lastForegroundPercent = -1

      fun onBytes(nowMs: Long, written: Long, total: Long): Decision {
          if (nowMs - lastReportedMs < intervalMs) {
              return Decision(report = false, updateForeground = false, percent = lastForegroundPercent)
          }
          lastReportedMs = nowMs
          val percent = percentOf(written, total)
          val updateForeground = percent != lastForegroundPercent
          if (updateForeground) lastForegroundPercent = percent
          return Decision(report = true, updateForeground = updateForeground, percent = percent)
      }

      companion object {
          const val DEFAULT_INTERVAL_MS = 200L

          fun percentOf(written: Long, total: Long): Int =
              if (total > 0) ((written * 100) / total).toInt().coerceIn(0, 100) else 0
      }
  }
  ```

  In `DownloadWorker.kt`: delete `import kotlin.math.abs`; change the class kdoc line `* location via [DownloadStorage], reporting progress to WorkManager every` / `* ~200ms or every 1 MB (whichever first).` to:
  ```kotlin
   * location via [DownloadStorage], reporting progress to WorkManager at
   * most every ~200ms; the foreground notification is rebuilt only when
   * the integer percent actually changes.
  ```
  Replace the streaming/progress section inside the `execute { response -> ... }` block (lines 75-118) with:
  ```kotlin
                  val total = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
                  val channel = response.bodyAsChannel()
                  var written = 0L
                  val throttle = DownloadProgressThrottle()

                  val buf = ByteArray(BUFFER_BYTES)
                  // Ktor 3.x ByteReadChannel → java.io.InputStream bridge.
                  // Avoids version-fragile ByteReadChannel read APIs and keeps
                  // the streaming copy + progress reporting on the same thread.
                  channel.toInputStream().use { input ->
                      target.openOutputStream().use { out ->
                          while (true) {
                              val n = input.read(buf)
                              if (n < 0) break
                              out.write(buf, 0, n)
                              written += n

                              val decision = throttle.onBytes(System.currentTimeMillis(), written, total)
                              if (decision.report) {
                                  setProgress(workDataOf(KEY_BYTES_WRITTEN to written, KEY_TOTAL_BYTES to total))
                                  // Rebuilding the notification (and its cancel
                                  // PendingIntent) is the expensive part — only
                                  // do it when the visible percent changed.
                                  if (decision.updateForeground) {
                                      setForeground(buildForegroundInfo(downloadId, displayTitle, progress = decision.percent, indeterminate = total <= 0))
                                  }
                                  // Push progress into the shared repo so any
                                  // currently-foregrounded UI re-renders without
                                  // a round-trip GET /downloads.
                                  repository.recordForFile(fileId)?.let { existing ->
                                      repository.upsertLocal(
                                          existing.copy(
                                              bytesSent = written,
                                              fileSize = if (total > 0) total else existing.fileSize,
                                              status = DownloadStatus.Downloading.wire,
                                          ),
                                      )
                                  }
                              }
                          }
                      }
                  }
  ```
  In the companion object, replace lines 237-239 with just:
  ```kotlin
          private const val BUFFER_BYTES = 64 * 1024
  ```
  (`PROGRESS_REPORT_INTERVAL_MS` and `PROGRESS_REPORT_BYTE_DELTA` are deleted; the interval now lives in `DownloadProgressThrottle.DEFAULT_INTERVAL_MS`.)

  NOTE FOR IMPLEMENTER: the loop body above mirrors the existing streaming copy in the worker — diff it against the current lines 75-118 before replacing and keep any existing details (e.g. the exact `buildForegroundInfo` signature and the channel-to-stream bridge already in use). The behavioral delta is ONLY: (a) `throttle.onBytes(...)` replaces the `now - lastReportedMs >= 200 || byteDelta >= 1MB` gate, and (b) `setForeground` runs only when `decision.updateForeground`.

- [ ] **Step 4: Run tests** — `./gradlew :android-shared:testDebugUnitTest --tests com.continuum.app.common.downloads.DownloadProgressThrottleTest` then the full module sweep `./gradlew :android-shared:testDebugUnitTest`.

- [ ] **Step 5: Commit**
  ```bash
  git add android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadProgressThrottle.kt android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadWorker.kt android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/downloads/DownloadProgressThrottleTest.kt
  git commit -m "$(cat <<'EOF'
  Throttle download progress by time and gate setForeground on percent change

  Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
  EOF
  )"
  ```

### Task B3: DownloadsViewModel — cancel the in-flight worker before deleting a record

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadsViewModel.kt:171-176` (constructor), `248-277` (removeRecords)
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt:152`
- Test: none (manual verification — see Step 1)

- [ ] **Step 1: Write the failing test** — No unit test feasible: the cancel call goes through `DownloadEnqueuer.cancel` → `WorkManager.cancelAllWorkByTag`. `DownloadEnqueuer` is a final class whose constructor requires an Android `Context` (plus 6 other collaborators), `androidApp`'s `androidUnitTest` source set has no mocking framework (only `kotlin-test`/`kotlin-test-junit`) and no Robolectric, so neither the enqueuer nor a `DownloadsViewModel` holding it can be constructed in a local unit test. Manual check: start a large download from item detail, go to the Downloads tab while it is in `Downloading` state, delete the row, and verify (a) the progress notification disappears immediately, (b) logcat shows `DownloadEnqueuer: cancel: downloadId=…` followed by `DownloadWorker: doWork cancelled id=…` (from Task 1), and (c) no orphaned media file keeps growing under `Movies/Prairie/…`.

- [ ] **Step 2: Run test to verify it fails** — N/A (no unit test). Pre-fix repro: delete a `Downloading` row from the Downloads tab and watch the notification keep ticking to 100% while the row is already gone — the finished multi-GB file is left on disk with no record to delete it.

- [ ] **Step 3: Implementation**

  Constructor (DownloadsViewModel.kt:171-176) becomes:
  ```kotlin
  class DownloadsViewModel(
      private val repository: DownloadsRepository,
      private val storage: DownloadStorage,
      private val serverRegistry: ServerRegistry,
      private val profileRepository: ProfileRepository,
      private val downloadEnqueuer: DownloadEnqueuer,
  ) : ViewModel() {
  ```
  (The `DownloadEnqueuer` import already exists at line 6 — previously used only for its `DEFAULT_SERVER_ID`/`DEFAULT_PROFILE_ID` constants. `DownloadStatus` and `statusEnum` are already imported at lines 11-12.)

  Complete new `removeRecords` (ordering otherwise unchanged in this task; Task 4 of this section reorders it):
  ```kotlin
      private suspend fun removeRecords(ids: List<String>) {
          if (ids.isEmpty()) return
          val serverId = serverRegistry.activeServerId.value ?: DownloadEnqueuer.DEFAULT_SERVER_ID
          val profileId = withContext(Dispatchers.IO) {
              profileRepository.getActiveProfileId()
          } ?: DownloadEnqueuer.DEFAULT_PROFILE_ID

          for (id in ids) {
              val record = repository.records.value.firstOrNull { it.id == id }
              val sidecar = metadataByRecordId[id]
              val fileId = record?.mediaFileId ?: sidecar?.record?.mediaFileId
              Log.i(TAG, "remove($id): record=${record?.status ?: "(missing)"} fileId=$fileId")

              // An active record still has a DownloadWorker streaming bytes.
              // Cancel it first (same as ItemDetailViewModel.onDownloadTapped)
              // so the worker doesn't keep filling a multi-GB file after the
              // row — and the only handle to delete it — is gone.
              val status = record?.statusEnum() ?: sidecar?.record?.statusEnum()
              if (status == DownloadStatus.Queued || status == DownloadStatus.Downloading) {
                  downloadEnqueuer.cancel(id)
              }

              repository.upsertLocalRemove(id)
              if (fileId != null) {
                  withContext(Dispatchers.IO) {
                      storage.delete(serverId, profileId, fileId)
                      storage.deleteSidecar(serverId, profileId, fileId)
                  }
              }
              metadataByRecordId = metadataByRecordId - id

              when (val result = repository.delete(id)) {
                  is ApiResult.Success -> Log.i(TAG, "remove($id): server delete OK")
                  is ApiResult.Error -> Log.w(TAG, "remove($id): server returned ${result.code} ${result.message}")
                  is ApiResult.NetworkError -> Log.w(TAG, "remove($id): network error", result.exception)
              }
          }
          _uiState.update { it.copy(totalBytesUsed = storage.totalBytesUsed()) }
      }
  ```

  AndroidModule.kt:152 becomes:
  ```kotlin
      viewModel { DownloadsViewModel(get(), get(), get(), get(), get()) }
  ```
  (No new `single` needed — `DownloadEnqueuer` is already registered at AndroidModule.kt:110. The TV app does not register `DownloadsViewModel` or `DownloadEnqueuer`, so no `androidTvApp` change.)

  NOTE FOR IMPLEMENTER: diff this `removeRecords` body against the current one before replacing — the only behavioral delta in THIS task is the cancel block; keep everything else exactly as the current code has it.

- [ ] **Step 4: Run tests** — `./gradlew :androidApp:testDebugUnitTest` (regression sweep) plus `./gradlew :androidApp:assembleDebug` to confirm the DI/constructor change compiles, then the manual check from Step 1.

- [ ] **Step 5: Commit**
  ```bash
  git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadsViewModel.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt
  git commit -m "$(cat <<'EOF'
  Cancel in-flight download workers when deleting Downloads rows

  Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
  EOF
  )"
  ```

### Task B4: Delete server record before local bytes; keep everything on server failure

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/repository/DownloadsRepository.kt:124-143` (delete)
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadsViewModel.kt:248-277` (removeRecords, as rewritten in Task 3)
- Test: `shared/src/commonTest/kotlin/com/continuum/app/repository/DownloadsRepositoryTest.kt`

- [ ] **Step 1: Write the failing test** — Append to `DownloadsRepositoryTest.kt` (uses the file's existing `private open class FakeDownloadsApi` and `stubRecord` helpers; adapt the fake-subclass syntax to match the file's existing style if it differs):
  ```kotlin
      @Test
      fun `delete keeps cache row when server delete fails`() = runTest {
          // pendingDelete is in-memory only: if we dropped the row (and bytes)
          // on a failed server delete, an app restart would merge the server
          // record back pointing at deleted files. Failure must be a no-op.
          val a = stubRecord("a", 1)
          val api = object : FakeDownloadsApi(initialList = listOf(a)) {
              override suspend fun delete(id: String): ApiResult<Unit> {
                  deleteCalls += id
                  return ApiResult.NetworkError(IllegalStateException("offline"))
              }
          }
          val repo = DownloadsRepository(api)
          repo.refresh()

          val result = repo.delete("a")
          assertTrue(result is ApiResult.NetworkError)
          assertEquals(listOf(a), repo.records.first())
          // Must NOT be marked pending-delete — the next refresh has to keep it.
          repo.refresh()
          assertEquals(listOf(a), repo.records.first())
      }

      @Test
      fun `delete treats 404 on first call as already removed`() = runTest {
          // Local-only records (server cleaned them up, or they never had a
          // server row) come back 404 — that's a confirmed removal, so the
          // caller may proceed with local file cleanup.
          val a = stubRecord("a", 1)
          val api = object : FakeDownloadsApi(initialList = listOf(a)) {
              override suspend fun delete(id: String): ApiResult<Unit> {
                  deleteCalls += id
                  return ApiResult.Error(code = 404, error = "not_found", message = "gone")
              }
          }
          val repo = DownloadsRepository(api)
          repo.refresh()

          val result = repo.delete("a")
          assertTrue(result is ApiResult.Success)
          assertTrue(repo.records.first().isEmpty())
          assertEquals(1, api.deleteCalls.size)
      }
  ```

- [ ] **Step 2: Run test to verify it fails** — `./gradlew :shared:testDebugUnitTest --tests com.continuum.app.repository.DownloadsRepositoryTest` — expected failures: `delete keeps cache row when server delete fails` fails at `assertEquals(listOf(a), repo.records.first())` (current code drops the row before calling the API), and `delete treats 404 on first call as already removed` fails at `assertTrue(result is ApiResult.Success)` (current code returns the 404 as an Error).

- [ ] **Step 3: Implementation**

  `DownloadsRepository.delete` (replacing lines 124-143) becomes:
  ```kotlin
      /**
       * Two-phase delete to handle the server's "cancel-then-delete" semantics
       * for active records (see class kdoc). The cache row is dropped only
       * once the server confirms the removal — success, or a 404 meaning the
       * server never had / already removed the record (the local-only case).
       * On failure the row stays and is NOT marked pending-delete, so the
       * caller keeps the local bytes and surfaces the error instead of
       * orphaning them: [pendingDelete] is in-memory only, and a restart
       * would otherwise merge the server record back pointing at deleted
       * files. Calls DELETE up to twice — the second call is what actually
       * removes a previously-active record.
       */
      suspend fun delete(id: String): ApiResult<Unit> {
          // First DELETE: may only cancel if record was active.
          val first = api.delete(id)
          if (first is ApiResult.Error && first.code == 404) {
              // Server doesn't know this record — confirmed gone; drop locally.
              markPendingDelete(id)
              _records.update { list -> list.filterNot { it.id == id } }
              return ApiResult.Success(Unit)
          }
          if (first !is ApiResult.Success) return first.mapToUnit()
          markPendingDelete(id)
          _records.update { list -> list.filterNot { it.id == id } }
          // Second DELETE: removes the (now-cancelled) row. 404 is fine — it
          // means the first DELETE already removed it.
          val second = api.delete(id)
          return if (second is ApiResult.Error && second.code == 404) ApiResult.Success(Unit)
          else second.mapToUnit()
      }
  ```
  NOTE FOR IMPLEMENTER: read the current `delete` implementation first — if it is not already two-phase, preserve its single-call semantics and apply only the ordering/404 changes; the key invariants are (1) cache row + pendingDelete only after server confirms, (2) 404 counts as confirmed, (3) failure returns the error unchanged with no cache mutation. If `mapToUnit()` does not exist in ApiResult, use the file's existing error-mapping idiom.

  `DownloadsViewModel.removeRecords` (replacing the Task 3 version in full) — server delete first, local cleanup only on confirmed removal, errors surfaced via the existing `uiState.error` field (the same plumbing `refresh()` uses), and `repository.upsertLocalRemove` dropped (the repo now owns cache removal on success/404):
  ```kotlin
      private suspend fun removeRecords(ids: List<String>) {
          if (ids.isEmpty()) return
          val serverId = serverRegistry.activeServerId.value ?: DownloadEnqueuer.DEFAULT_SERVER_ID
          val profileId = withContext(Dispatchers.IO) {
              profileRepository.getActiveProfileId()
          } ?: DownloadEnqueuer.DEFAULT_PROFILE_ID

          var firstError: String? = null
          for (id in ids) {
              val record = repository.records.value.firstOrNull { it.id == id }
              val sidecar = metadataByRecordId[id]
              val fileId = record?.mediaFileId ?: sidecar?.record?.mediaFileId
              Log.i(TAG, "remove($id): record=${record?.status ?: "(missing)"} fileId=$fileId")

              // An active record still has a DownloadWorker streaming bytes.
              // Cancel it first (same as ItemDetailViewModel.onDownloadTapped)
              // so the worker doesn't keep filling a multi-GB file after the
              // row — and the only handle to delete it — is gone.
              val status = record?.statusEnum() ?: sidecar?.record?.statusEnum()
              if (status == DownloadStatus.Queued || status == DownloadStatus.Downloading) {
                  downloadEnqueuer.cancel(id)
              }

              // Server delete FIRST. Only a confirmed removal (success, or a
              // 404 = the record exists only locally — repository.delete maps
              // that to Success) may drop bytes from disk. pendingDelete is
              // in-memory only: deleting bytes before the server confirms
              // means an app restart merges the server record back pointing
              // at files that no longer exist.
              when (val result = repository.delete(id)) {
                  is ApiResult.Success -> Log.i(TAG, "remove($id): server delete OK")
                  is ApiResult.Error -> {
                      Log.w(TAG, "remove($id): server returned ${result.code} ${result.message}")
                      if (firstError == null) firstError = result.message ?: "Delete failed (${result.code})"
                      continue
                  }
                  is ApiResult.NetworkError -> {
                      Log.w(TAG, "remove($id): network error", result.exception)
                      if (firstError == null) firstError = result.exception.message ?: "Network error"
                      continue
                  }
              }

              if (fileId != null) {
                  withContext(Dispatchers.IO) {
                      storage.delete(serverId, profileId, fileId)
                      storage.deleteSidecar(serverId, profileId, fileId)
                  }
              }
              metadataByRecordId = metadataByRecordId - id
          }
          _uiState.update {
              it.copy(
                  error = firstError,
                  totalBytesUsed = storage.totalBytesUsed(),
              )
          }
      }
  ```

  Note: no `DownloadsViewModel` unit test accompanies the VM half — the constructor now requires `DownloadEnqueuer` (final, `Context`/WorkManager-bound, no mocking framework available in `androidApp` unit tests), so the VM can't be instantiated off-device; the record-keeping semantics are covered by the new repository tests above. Manual check: with the server unreachable (airplane mode), delete a completed download — the row must stay, an error must surface in the Downloads UI, and the media file + sidecar must remain on disk; reconnect and delete again — row, bytes, and sidecar all go.

- [ ] **Step 4: Run tests** — `./gradlew :shared:testDebugUnitTest --tests com.continuum.app.repository.DownloadsRepositoryTest` (all green, including the pre-existing two-phase/ghost-suppression tests), then `./gradlew :shared:testDebugUnitTest :androidApp:testDebugUnitTest`.

- [ ] **Step 5: Commit**
  ```bash
  git add shared/src/commonMain/kotlin/com/continuum/app/repository/DownloadsRepository.kt shared/src/commonTest/kotlin/com/continuum/app/repository/DownloadsRepositoryTest.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadsViewModel.kt
  git commit -m "$(cat <<'EOF'
  Confirm server delete before removing downloaded bytes

  Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
  EOF
  )"
  ```

### Task B5: DownloadsViewModel — build sections on IO and stop re-walking the filesystem per record

**Files:**
- Modify: `android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadStorage.kt:157-171` (listAllSidecars)
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadsViewModel.kt:181-228` (fields/init/refresh), `248-302` (removeRecords/bootstrapFromDisk/toItem)
- Test: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/downloads/DownloadStorageTest.kt`

- [ ] **Step 1: Write the failing test** — Append to `DownloadStorageTest.kt` (after the existing sidecar round-trip tests; reuses the file's `stubSidecar` and storage-construction helpers — adapt helper names to what the file actually defines):
  ```kotlin
      @Test
      fun `listAllSidecarsWithScope reports the serverId and profileId each sidecar lives under`() {
          val storage = newStorage()
          storage.writeSidecar("srv1", "profA", stubSidecar(1))
          storage.writeSidecar("srv2", "profB", stubSidecar(2))

          val scoped = storage.listAllSidecarsWithScope()
              .associateBy { (_, _, sidecar) -> sidecar.record.mediaFileId }

          assertEquals(2, scoped.size)
          assertEquals("srv1" to "profA", scoped[1]?.let { it.first to it.second })
          assertEquals("srv2" to "profB", scoped[2]?.let { it.first to it.second })
      }

      @Test
      fun `listAllSidecars matches the scoped walk`() {
          val storage = newStorage()
          storage.writeSidecar("srv1", "profA", stubSidecar(1))
          storage.writeSidecar("srv1", "profB", stubSidecar(2))

          assertEquals(
              storage.listAllSidecarsWithScope().map { it.third }.toSet(),
              storage.listAllSidecars().toSet(),
          )
      }
  ```

- [ ] **Step 2: Run test to verify it fails** — `./gradlew :android-shared:testDebugUnitTest --tests com.continuum.app.common.downloads.DownloadStorageTest` — expected failure: compilation error `Unresolved reference: listAllSidecarsWithScope`.

- [ ] **Step 3: Implementation**

  In `DownloadStorage.kt`, replace `listAllSidecars` (lines 157-171) with:
  ```kotlin
      /**
       * Walks every sidecar under the downloads tree (across all servers /
       * profiles) and returns the parsed contents. Silently skips files that
       * fail to decode (forward-compat with future sidecar shape changes).
       */
      fun listAllSidecars(): List<DownloadSidecar> =
          listAllSidecarsWithScope().map { it.third }

      /**
       * Like [listAllSidecars] but preserves the `(serverId, profileId)`
       * scope derived from each sidecar's path
       * (`<downloadsRoot>/<serverId>/<profileId>/<fileId>.record.json`).
       * One walk serves callers that need both the sidecar and where it
       * lives, instead of a per-fileId [locateSidecarByFileId] re-walk.
       */
      fun listAllSidecarsWithScope(): List<Triple<String, String, DownloadSidecar>> {
          val root = downloadsRoot
          if (!root.exists()) return emptyList()
          return root.walkTopDown()
              .filter { it.isFile && it.name.endsWith(".record.json") }
              .mapNotNull { file ->
                  val sidecar = runCatching { JSON.decodeFromString<DownloadSidecar>(file.readText()) }.getOrNull()
                      ?: return@mapNotNull null
                  val profileDir = file.parentFile ?: return@mapNotNull null
                  val serverDir = profileDir.parentFile ?: return@mapNotNull null
                  Triple(serverDir.name, profileDir.name, sidecar)
              }
              .toList()
      }
  ```
  NOTE FOR IMPLEMENTER: match the existing `listAllSidecars` body's actual property/constant names (`downloadsRoot`, `JSON`, the `.record.json` suffix) — keep whatever the current code uses.

  In `DownloadsViewModel.kt` — fields, init, refresh, and the metadata reload (replacing lines 181-228 and deleting `bootstrapFromDisk`):
  ```kotlin
      /** Sidecar map keyed by record id. Refreshed on every records
       *  emission via [reloadSidecarMetadata]. */
      @Volatile private var metadataByRecordId: Map<String, DownloadSidecar> = emptyMap()

      /** `(serverId, profileId)` scope each fileId's sidecar lives under,
       *  captured during the same walk that loads [metadataByRecordId] so
       *  [toItem] doesn't re-walk the filesystem per record per emission. */
      @Volatile private var scopeByFileId: Map<Int, Pair<String, String>> = emptyMap()

      init {
          viewModelScope.launch {
              // Bootstrap: backfill + initial sidecar read.
              withContext(Dispatchers.IO) { reloadSidecarMetadata() }
              val seeded = metadataByRecordId.values.toList()
              repository.seedFromSidecars(seeded.map { it.record })

              val keep = seeded.map { it.record.id }.toSet()
              launch { repository.refresh(keepIdsAbsentFromServer = keep) }

              repository.records.collect { records ->
                  // Section building reads sidecars + walks file sizes — keep
                  // it off the main dispatcher; the worker emits every ~200ms
                  // during an active download.
                  val (sections, bytesUsed) = withContext(Dispatchers.IO) {
                      // Refresh sidecars so newly-enqueued records get their
                      // metadata into the UI without waiting for a restart.
                      if (records.any { it.id !in metadataByRecordId }) {
                          reloadSidecarMetadata()
                      }
                      records.toSections() to storage.totalBytesUsed()
                  }
                  _uiState.update {
                      it.copy(
                          isLoading = false,
                          error = null,
                          sections = sections,
                          totalBytesUsed = bytesUsed,
                      )
                  }
              }
          }
      }

      fun refresh() {
          viewModelScope.launch {
              withContext(Dispatchers.IO) { reloadSidecarMetadata() }
              val keep = metadataByRecordId.keys
              when (val r = repository.refresh(keepIdsAbsentFromServer = keep)) {
                  is ApiResult.Success -> _uiState.update { it.copy(error = null) }
                  is ApiResult.Error -> _uiState.update { it.copy(error = r.message) }
                  is ApiResult.NetworkError -> _uiState.update { it.copy(error = r.exception.message) }
              }
          }
      }

      /** One filesystem walk loads both lookup maps. Call on Dispatchers.IO. */
      private fun reloadSidecarMetadata() {
          val scoped = runCatching { storage.listAllSidecarsWithScope() }.getOrElse { emptyList() }
          metadataByRecordId = scoped.associate { (_, _, sidecar) -> sidecar.record.id to sidecar }
          scopeByFileId = scoped.associate { (serverId, profileId, sidecar) ->
              sidecar.record.mediaFileId to (serverId to profileId)
          }
      }
  ```
  NOTE FOR IMPLEMENTER: diff against the current init/refresh — preserve the existing `seedFromSidecars`/`refresh(keepIdsAbsentFromServer=...)` call signatures and any logic not named here. The behavioral deltas are ONLY: section building moves inside `withContext(Dispatchers.IO)`, and the metadata maps load via one `listAllSidecarsWithScope()` walk.

  In `toItem()` (line 292-294), replace the `located` lookup with the cached-scope version:
  ```kotlin
          val located = scopeByFileId[mediaFileId]?.let { (serverId, profileId) ->
              storage.locateLocalMedia(serverId, profileId, mediaFileId)
          }
  ```

  In `removeRecords` (the Task 4 version), two deltas — drop the deleted fileId from the scope map and move the final `totalBytesUsed` walk to IO. The local-cleanup block becomes:
  ```kotlin
              if (fileId != null) {
                  withContext(Dispatchers.IO) {
                      storage.delete(serverId, profileId, fileId)
                      storage.deleteSidecar(serverId, profileId, fileId)
                  }
                  scopeByFileId = scopeByFileId - fileId
              }
              metadataByRecordId = metadataByRecordId - id
  ```
  and the trailing state update becomes:
  ```kotlin
          val bytesUsed = withContext(Dispatchers.IO) { storage.totalBytesUsed() }
          _uiState.update {
              it.copy(
                  error = firstError,
                  totalBytesUsed = bytesUsed,
              )
          }
  ```

  Observable behavior is unchanged: `uiState` keeps the same `StateFlow<DownloadsUiState>` shape and the same field semantics — only where the work runs (IO) and how the per-record scope is found (cached map vs. per-record `walkTopDown`) changes. `DownloadStorage.locateSidecarByFileId` stays (still used by `OfflineMediaResolver`).

- [ ] **Step 4: Run tests** — `./gradlew :android-shared:testDebugUnitTest --tests com.continuum.app.common.downloads.DownloadStorageTest`, then the full sweeps `./gradlew :android-shared:testDebugUnitTest :androidApp:testDebugUnitTest`. Manual sanity: with an active download running, scroll the Downloads tab — no jank; rows still show posters, progress, and the storage total.

- [ ] **Step 5: Commit**
  ```bash
  git add android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadStorage.kt android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/downloads/DownloadStorageTest.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadsViewModel.kt
  git commit -m "$(cat <<'EOF'
  Build Downloads sections on IO with a single sidecar walk

  Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
  EOF
  )"
  ```

### Task B6: PlayerViewModel — resolve offline media via the shared OfflineMediaResolver

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerViewModel.kt:21` (import), `55-70` (constructor), `959-1042` (tryLocalPlayback)
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt:124`
- Test: none (manual verification — see Step 1)

- [ ] **Step 1: Write the failing test** — No unit test feasible for the wiring itself: `PlayerViewModel` takes 12 collaborators including `PlaybackSessionManager`, `PlaybackSessionLifecycle`, `PlaybackCapabilityDetector`, and `SleepTimerController` (Media3/Android-bound), none of which can be constructed or faked in `androidApp`'s local unit tests. The cross-scope resolution behavior being adopted is already locked down by `android-shared`'s existing `OfflineMediaResolverTest` (`findLocalMedia returns completed original-named download` resolves from `("srv1", "profA")` — a scope that is not the active-server default — plus the incomplete-download and fallback tests). Manual check: complete a download, then reproduce the legacy-scope condition (e.g. sign out/in so the active `(serverId, profileId)` differs from the one the download was made under), go to airplane mode, and play the title — it must play the local file (logcat: `PlayerViewModel: tryLocalPlayback: serving <name> (<bytes>B) for content=…`) instead of erroring/streaming.

- [ ] **Step 2: Run test to verify it fails** — N/A (no new unit test). Pre-fix repro: with a completed download whose sidecar lives under a non-active scope, airplane-mode playback fails (or, online, silently streams from the server) because `tryLocalPlayback` finds the sidecar via the cross-scope `listAllSidecars` walk but then resolves bytes only under `activeServerId/activeProfileId`.

- [ ] **Step 3: Implementation**

  THE CORE CHANGE — keep the existing function's metadata/UiState population exactly as it is today; the ONLY behavioral replacement is HOW the local file is found. Replace the current inline lookup (sidecar walk + completed filter + preferred-fileId fallback + `locateLocalMedia(activeServerId ?: DEFAULT_SERVER_ID, activeProfileId ?: DEFAULT_PROFILE_ID, fileId)`) at the top of `tryLocalPlayback` with:

  ```kotlin
          val media = withContext(Dispatchers.IO) {
              offlineMediaResolver.findLocalMedia(contentId, requestedFileId = preferredFileId)
          } ?: return false
          val sidecar = media.sidecar
          val fileId = media.fileId
  ```

  and then feed `media.uriString`, `sidecar`, and `fileId` into the SAME downstream code the function already has (title/subtitle/versions/startPosition/UiState update). Read the current implementation first and only swap the resolution block — the resolver's default `allowFallback = true` reproduces the old `matches.firstOrNull { fileId match } ?: matches.firstOrNull()` fallback, and it filters to `DownloadStatus.Completed` exactly as before. Check `OfflineMediaResolver.findLocalMedia`'s actual return type field names (`sidecar`, `fileId`, `uriString`, `displayName`, `sizeBytes`) against `android-shared/.../downloads/OfflineMediaResolver.kt` and adapt.

  Constructor: replace the download-lookup collaborators that become unused (`downloadStorage`, `serverRegistry`, and — if its only use was the old lookup — `downloadsRepository`) with one `private val offlineMediaResolver: OfflineMediaResolver` (import `com.continuum.app.common.downloads.OfflineMediaResolver`). Grep the file for each removed param's remaining usages first; keep any that are still used elsewhere (`profileRepository` IS still used by session/start paths — keep it).

  AndroidModule.kt: update the `PlayerViewModel` registration's `get()` count to match the new constructor (OfflineMediaResolver is already registered as a `single` at AndroidModule.kt:109). No other construction sites exist.

  Remove the import `com.continuum.app.model.download.statusEnum` if its only use was the old lookup.

- [ ] **Step 4: Run tests** — `./gradlew :android-shared:testDebugUnitTest --tests com.continuum.app.common.downloads.OfflineMediaResolverTest` (resolver contract still green), `./gradlew :androidApp:testDebugUnitTest`, and `./gradlew :androidApp:assembleDebug` to confirm the DI change compiles. Manual check from Step 1.

- [ ] **Step 5: Commit**
  ```bash
  git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerViewModel.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt
  git commit -m "$(cat <<'EOF'
  Resolve offline video playback through OfflineMediaResolver

  Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
  EOF
  )"
  ```

## Section C: Reader subsystem

### Task C1: Shared reader file cache with atomic tmp/rename downloads + migrate PdfReader

**Files:**
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderFileCache.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PdfReader.kt`
- Test: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderFileCacheTest.kt`

Background: `resolveToLocalFile` (PdfReader.kt:223-250), `resolveComicFile`, `resolveFictionBookFile`, and `resolveEpubFile` are pattern-identical and all write the download directly to the final cache path; the cache-hit check is `target.exists() && target.length() > 0`, so a transfer that dies mid-stream leaves a truncated file that is served forever. There are also five identical private `sha1` helpers. MockWebServer is NOT on the test classpath (androidUnitTest deps are only `kotlin("test")` + `kotlin("test-junit")`), so the cache-fill core takes a `fetch: (OutputStream) -> Unit` seam and is tested with fake fetchers; the HTTP/content:// wiring stays thin and untested. Note: none of the readers add per-request auth headers — they build bare `Request.Builder().url(...).build()` against the Koin-injected `OkHttpClient` (auth lives in that client's interceptors), so the shared resolver does exactly the same.

- [ ] **Step 1: Write the failing test**

Create `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderFileCacheTest.kt`:

```kotlin
package com.continuum.app.android.ui.screens.reader

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ReaderFileCacheTest {

    @Test
    fun `successful fetch lands at the final path with no tmp residue`() {
        val cacheDir = newCacheDir()

        val result = cacheReaderFile(cacheDir, "abc.pdf") { out ->
            out.write("pdf-bytes".toByteArray())
        }

        assertEquals(File(cacheDir, "abc.pdf"), result)
        assertEquals("pdf-bytes", result.readText())
        assertFalse(File(cacheDir, "abc.pdf.tmp").exists())
    }

    @Test
    fun `failed fetch leaves no cached file and no tmp residue`() {
        val cacheDir = newCacheDir()

        assertFailsWith<IOException> {
            cacheReaderFile(cacheDir, "abc.pdf") { out ->
                out.write("trunc".toByteArray())
                throw IOException("connection reset")
            }
        }

        assertFalse(
            File(cacheDir, "abc.pdf").exists(),
            "truncated download must not poison the cache",
        )
        assertFalse(File(cacheDir, "abc.pdf.tmp").exists())
    }

    @Test
    fun `existing non-empty cache entry short-circuits without fetching`() {
        val cacheDir = newCacheDir()
        File(cacheDir, "abc.pdf").writeText("cached")

        val result = cacheReaderFile(cacheDir, "abc.pdf") {
            throw AssertionError("fetch must not run on cache hit")
        }

        assertEquals("cached", result.readText())
    }

    @Test
    fun `empty cache entry is refetched`() {
        val cacheDir = newCacheDir()
        File(cacheDir, "abc.pdf").writeText("")

        val result = cacheReaderFile(cacheDir, "abc.pdf") { out ->
            out.write("refetched".toByteArray())
        }

        assertEquals("refetched", result.readText())
    }

    @Test
    fun `cache key is the sha1 hex of the url`() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", readerCacheKey("abc"))
    }

    private fun newCacheDir(): File =
        Files.createTempDirectory("reader-cache").toFile().apply { deleteOnExit() }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.ReaderFileCacheTest"
```

Expected: compilation failure of the unit-test compilation — `Unresolved reference: cacheReaderFile` / `Unresolved reference: readerCacheKey`.

- [ ] **Step 3: Implementation**

Create `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderFileCache.kt`:

```kotlin
package com.continuum.app.android.ui.screens.reader

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.security.MessageDigest

/** SHA-1 cache key for a reader URL — the one shared copy of the helper
 *  the readers previously duplicated five times. */
internal fun readerCacheKey(url: String): String {
    val md = MessageDigest.getInstance("SHA-1")
    return md.digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
}

/**
 * Fill `<cacheDir>/<fileName>` atomically. [fetch] writes into a `.tmp`
 * sibling which is renamed to the final name only when it completes
 * without throwing, so a truncated transfer can never satisfy the
 * `exists() && length() > 0` cache-hit check and get served forever.
 * The tmp file is deleted on any failure. An existing non-empty target
 * short-circuits without invoking [fetch].
 */
internal fun cacheReaderFile(
    cacheDir: File,
    fileName: String,
    fetch: (OutputStream) -> Unit,
): File {
    cacheDir.mkdirs()
    val target = File(cacheDir, fileName)
    if (target.exists() && target.length() > 0) return target
    val tmp = File(cacheDir, "$fileName.tmp")
    try {
        FileOutputStream(tmp).use(fetch)
    } catch (throwable: Throwable) {
        tmp.delete()
        throw throwable
    }
    if (!tmp.renameTo(target)) {
        try {
            tmp.copyTo(target, overwrite = true)
        } catch (throwable: Throwable) {
            target.delete()
            throw throwable
        } finally {
            tmp.delete()
        }
    }
    return target
}

/**
 * Resolve a reader URL to a local [File]:
 *   - `file://`    → used as-is (no copy).
 *   - `content://` → copied once into `<cacheDir>/readers/`.
 *   - http(s) or server-relative → fetched via [okHttp] (auth comes
 *     from the injected client's interceptors, same as before) into
 *     `<cacheDir>/readers/<sha1(url)>.<extension>`.
 * Downloads go through [cacheReaderFile], so failures never poison the
 * cache.
 */
internal suspend fun resolveReaderFile(
    context: Context,
    okHttp: OkHttpClient,
    url: String,
    serverUrl: String,
    extension: String,
): File = withContext(Dispatchers.IO) {
    if (url.startsWith("file://")) return@withContext File(url.removePrefix("file://"))
    val cacheDir = File(context.cacheDir, "readers")
    val fileName = "${readerCacheKey(url)}.$extension"
    if (url.startsWith("content://")) {
        return@withContext cacheReaderFile(cacheDir, fileName) { out ->
            context.contentResolver.openInputStream(Uri.parse(url))?.use { input ->
                input.copyTo(out)
            } ?: error("Could not open $url")
        }
    }
    val requestUrl = resolveReaderRequestUrl(url, serverUrl)
    cacheReaderFile(cacheDir, fileName) { out ->
        val req = Request.Builder().url(requestUrl).build()
        okHttp.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code} fetching $requestUrl")
            val body = resp.body ?: error("Empty body for $requestUrl")
            body.byteStream().copyTo(out)
        }
    }
}
```

(`resolveReaderRequestUrl` is the existing URL-joining helper in `ReaderFileResolver.kt` — check its actual name/signature and use it. Extensions stay `pdf`/`cbz`/`fb`/`epub`/`txt` per reader, so existing warm cache entries keep their keys.)

In `PdfReader.kt`, replace the `localFileResult` producer (lines 76-82):

```kotlin
    // Resolve to a local File. produceState + the suspend resolver keep
    // the IO off the main thread and emit the result into composition.
    val localFileResult by produceState<Result<File>?>(initialValue = null, fileUrl) {
        value = runCatching {
            resolveReaderFile(context, okHttp, fileUrl, tokenManager.getServerUrl(), "pdf")
        }
    }
```

Delete `resolveToLocalFile` (lines 216-250 including its KDoc) and `sha1` (lines 252-255). Remove these now-unused imports from PdfReader.kt: `android.content.Context`, `android.net.Uri`, `java.io.FileOutputStream`, `java.security.MessageDigest`, `okhttp3.Request`. Keep `kotlinx.coroutines.withContext`, `kotlinx.coroutines.Dispatchers`, `java.io.File`, and `okhttp3.OkHttpClient` (still used).

- [ ] **Step 4: Run tests**

```bash
./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.*"
```

- [ ] **Step 5: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderFileCache.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PdfReader.kt androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderFileCacheTest.kt && git commit -m "Add shared reader file cache with atomic downloads

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Task C2: Migrate Comic/Epub/FictionBook/Text readers to the shared resolver

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicReader.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/EpubReader.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/FictionBookReader.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/TextReader.kt`
- Test: existing suite at `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/` (no new test file)

- [ ] **Step 1: Write the failing test**

Not unit-testable beyond Task 1's coverage: this task is pure call-site migration inside composables (no new logic — the tmp/rename semantics are already covered by `ReaderFileCacheTest`, and `loadFictionBookText` / `loadComicArchivePages` behavior is unchanged and already covered by `FictionBookReaderTest` / `ComicArchiveLoaderTest`). Manual check after Step 3: open one book of each format (CBZ, EPUB, FB2, TXT) from the server, then re-open each (should be instant from cache); kill the network mid-download of a large CBZ, retry — the retry must re-download instead of opening a truncated archive.

- [ ] **Step 2: Run test to verify it fails**

Not applicable (no new test). Establish a green baseline instead:

```bash
./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.*"
```

Expected: all pass.

- [ ] **Step 3: Implementation**

**ComicReader.kt** — replace the `localFileResult` producer (lines 70-74) with:

```kotlin
    val localFileResult by produceState<Result<File>?>(initialValue = null, fileUrl) {
        value = runCatching {
            resolveReaderFile(context, okHttp, fileUrl, tokenManager.getServerUrl(), "cbz")
        }
    }
```

Delete `resolveComicFile` (lines 222-242) and `sha1Comic` (lines 244-247). Remove imports: `android.content.Context`, `android.net.Uri`, `java.io.FileOutputStream`, `java.security.MessageDigest`, `okhttp3.Request`. (`kotlinx.coroutines.withContext` and `kotlinx.coroutines.Dispatchers` are still used by the archive/page producers — keep them.)

**EpubReader.kt** — replace the `bookResult` producer (lines 71-78) with:

```kotlin
    val bookResult by produceState<Result<EpubBook>?>(initialValue = null, fileUrl) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = resolveReaderFile(context, okHttp, fileUrl, tokenManager.getServerUrl(), "epub")
                EpubBook.open(file, context.cacheDir)
            }
        }
    }
```

In `EpubBook.open`, replace `val key = sha1Epub(epub.absolutePath)` (line 205) with:

```kotlin
            val key = readerCacheKey(epub.absolutePath)
```

Delete the companion's `sha1Epub` (lines 251-254), and delete `resolveEpubFile` (lines 258-278) and `sha1EpubUrl` (lines 280-283). Remove imports: `android.content.Context`, `android.net.Uri`, `java.security.MessageDigest`, `okhttp3.Request`. Keep `java.io.FileOutputStream` (still used by the unpack loop) and `java.util.zip.ZipFile`.

**FictionBookReader.kt** — replace the `documentResult` producer (lines 49-54) with (note: the old code had no `runCatching` around the resolve, so a download failure crashed the produceState coroutine — fold it into the error result):

```kotlin
    val documentResult by produceState<FictionBookLoadResult?>(initialValue = null, fileUrl) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = resolveReaderFile(context, okHttp, fileUrl, tokenManager.getServerUrl(), "fb")
                loadFictionBookText(file)
            }.getOrElse { throwable ->
                FictionBookLoadResult.Error(
                    throwable.message?.takeIf { it.isNotBlank() } ?: "Could not open this FictionBook file.",
                )
            }
        }
    }
```

Delete `resolveFictionBookFile` (lines 194-213) and `sha1FictionBook` (lines 215-218). Remove imports: `android.content.Context`, `android.net.Uri`, `java.io.FileOutputStream`, `java.security.MessageDigest`, `okhttp3.Request`. Keep `java.io.File`, `java.io.FileInputStream`, `java.io.InputStream`.

**TextReader.kt** — replace the `textResult` producer (lines 45-49) with (behavior note: `content://` text now goes through the cache file then `readText()`, same as the other schemes):

```kotlin
    val textResult by produceState<Result<String>?>(initialValue = null, fileUrl) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                resolveReaderFile(context, okHttp, fileUrl, tokenManager.getServerUrl(), "txt").readText()
            }
        }
    }
```

Delete `resolveTextFile` (lines 118-131). Remove imports: `android.content.Context`, `android.net.Uri`, `okhttp3.Request`, `java.io.File`. Keep `okhttp3.OkHttpClient`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.withContext`.

- [ ] **Step 4: Run tests**

```bash
./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.*"
```

Then build to confirm no stale references: `./gradlew :androidApp:assembleDebug`. Perform the manual check from Step 1.

- [ ] **Step 5: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicReader.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/EpubReader.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/FictionBookReader.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/TextReader.kt && git commit -m "Migrate remaining readers to shared file cache

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Task C3: Honor the declared XML encoding when parsing FB2

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/FictionBookReader.kt`
- Test: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/FictionBookReaderTest.kt`

Background: `parseFictionBookText` (FictionBookReader.kt:103-107) does `parse(InputSource(StringReader(input.bufferedReader().use { it.readText() })))`. `bufferedReader()` force-decodes the bytes as UTF-8 *before* the XML parser ever sees the prolog, and an `InputSource` built from a `Reader` ignores the prolog's `encoding` attribute — so a `windows-1251` FB2 (very common for Russian-language FictionBooks) becomes U+FFFD mojibake. Handing the parser the raw `InputStream` lets it sniff and honor the declared encoding.

- [ ] **Step 1: Write the failing test**

Add to `FictionBookReaderTest.kt` (inside the class, after the `empty fb2 reports empty` test):

```kotlin
    @Test
    fun `honors declared xml encoding for non utf8 fb2`() {
        val fb2 = """
            <?xml version="1.0" encoding="windows-1251"?>
            <FictionBook>
              <description>
                <title-info>
                  <author>
                    <first-name>Лев</first-name>
                    <last-name>Толстой</last-name>
                  </author>
                  <book-title>Война и мир</book-title>
                </title-info>
              </description>
              <body>
                <section>
                  <p>Привет, мир.</p>
                </section>
              </body>
            </FictionBook>
        """.trimIndent()
        val bytes = fb2.toByteArray(charset("windows-1251"))

        val result = parseFictionBookText(bytes.inputStream())

        val loaded = assertIs<FictionBookLoadResult.Loaded>(result)
        assertTrue("Война и мир" in loaded.text)
        assertTrue("Лев Толстой" in loaded.text)
        assertTrue("Привет, мир." in loaded.text)
    }
```

(Adapt the entry point name if the parse function visible to tests differs — match how the existing tests in this file invoke parsing.)

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.FictionBookReaderTest"
```

Expected: `honors declared xml encoding for non utf8 fb2` fails on the `"Война и мир" in loaded.text` assertion — the windows-1251 Cyrillic bytes are invalid UTF-8, so the text comes through as U+FFFD replacement characters.

- [ ] **Step 3: Implementation**

In `FictionBookReader.kt`, change the parse call in `parseFictionBookText` (lines 103-107) to:

```kotlin
internal fun parseFictionBookText(input: InputStream): FictionBookLoadResult =
    runCatching {
        // Hand the parser the raw byte stream so it sniffs and honors
        // the XML prolog's declared encoding (windows-1251 FB2s are
        // common); pre-decoding via bufferedReader() forced UTF-8.
        val document = secureDocumentBuilderFactory()
            .newDocumentBuilder()
            .parse(InputSource(input))
```

(The rest of the function body — from `val root = document.documentElement` through the trailing `getOrElse` — is unchanged. Match the actual builder-factory helper name used in the current file.) Remove the now-unused import `java.io.StringReader`.

- [ ] **Step 4: Run tests**

```bash
./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.*"
```

(All pre-existing FictionBook tests must stay green — UTF-8/prolog-less samples still parse via byte-stream sniffing.)

- [ ] **Step 5: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/FictionBookReader.kt androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/FictionBookReaderTest.kt && git commit -m "Honor declared FB2 encoding when parsing

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Task C4: Serialize PdfRenderer close against rendering and open it off-main

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PdfReader.kt`
- Test: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/SerializedCloseableTest.kt` (create)

Background: two coupled problems. (a) `DisposableEffect`'s `onDispose { runCatching { renderer.close() } }` (PdfReader.kt:111-113) runs on the main thread without taking `renderMutex`, while `renderPdfPageBitmap` (lines 194-214) may be blocked inside native `page.render()` on Dispatchers.IO — close-during-render is an ISE or native crash. (b) `remember(localFile) { openRendererResult(localFile) }` (line 99) opens the ParcelFileDescriptor + PdfRenderer synchronously in composition on the main thread. Fix: a `SerializedCloseable<T>` wrapper that routes every renderer touch *and* close through one mutex with a closed flag (unit-testable with a fake `AutoCloseable`), and folding renderer opening into the existing `produceState`/`Dispatchers.IO` step, closing from `awaitDispose` via a detached IO coroutine (the composable's own scope is already cancelled at dispose time; the mutex guarantees close waits out any in-flight render).

- [ ] **Step 1: Write the failing test**

The Compose wiring (produceState/awaitDispose) is not unit-testable here, but the serialization primitive is. Create `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/SerializedCloseableTest.kt`:

```kotlin
package com.continuum.app.android.ui.screens.reader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SerializedCloseableTest {

    private class FakeResource : AutoCloseable {
        @Volatile var closeCount = 0
        val closed: Boolean get() = closeCount > 0
        override fun close() { closeCount++ }
    }

    @Test
    fun `close waits for in-flight use instead of closing underneath it`(): Unit = runBlocking {
        val resource = FakeResource()
        val handle = SerializedCloseable(resource)
        val useStarted = CountDownLatch(1)
        val releaseUse = CountDownLatch(1)

        val user = launch(Dispatchers.Default) {
            handle.withResource {
                useStarted.countDown()
                releaseUse.await(5, TimeUnit.SECONDS)
                // The resource must still be open while a use holds the lock.
                assertFalse(resource.closed)
            }
        }
        assertTrue(useStarted.await(5, TimeUnit.SECONDS))

        val closer = launch(Dispatchers.Default) { handle.close() }
        releaseUse.countDown()
        user.join()
        closer.join()
        assertTrue(resource.closed)
    }

    @Test
    fun `use after close fails fast without touching the resource`(): Unit = runBlocking {
        val resource = FakeResource()
        val handle = SerializedCloseable(resource)
        handle.close()

        assertFailsWith<IllegalStateException> {
            handle.withResource { }
        }
    }

    @Test
    fun `double close only closes the resource once`(): Unit = runBlocking {
        val resource = FakeResource()
        val handle = SerializedCloseable(resource)
        handle.close()
        handle.close()
        assertEquals(1, resource.closeCount)
    }
}
```

(kotlinx-coroutines is on the androidUnitTest classpath via the associated androidMain compilation. If `runBlocking` fails to resolve, add `implementation(libs.kotlinx.coroutines.test)` to the `androidUnitTest.dependencies` block of androidApp/build.gradle.kts — the catalog already declares it.)

The composable-side fix itself is not unit-testable; manual check: open a large PDF and immediately press back while pages are still rendering (repeat ~10x) — no `IllegalStateException: Already closed` or native abort in logcat; also confirm no jank/frame skip warning when first opening a PDF (renderer now opens on IO).

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.SerializedCloseableTest"
```

Expected: compilation failure — `Unresolved reference: SerializedCloseable`.

- [ ] **Step 3: Implementation**

In `PdfReader.kt` (state after Task 1 of this section):

1. Adjust imports: remove `androidx.compose.runtime.DisposableEffect`; add `kotlinx.coroutines.CoroutineScope` and `kotlinx.coroutines.launch` (keep `kotlinx.coroutines.sync.Mutex`/`withLock` — now used by the wrapper).

2. Add the primitive and a small handle (place them above `openRenderer`, replacing the deleted `openRendererResult`):

```kotlin
/**
 * Serializes every touch of an [AutoCloseable] resource — including
 * close — through one mutex. PdfRenderer is not thread-safe and
 * crashes (ISE or native abort) when closed while a page render is in
 * flight, so close waits for the active render to release the lock and
 * any later use fails fast instead of reaching native code.
 */
internal class SerializedCloseable<T : AutoCloseable>(private val resource: T) {
    private val mutex = Mutex()
    private var closed = false

    suspend fun <R> withResource(block: (T) -> R): R = mutex.withLock {
        check(!closed) { "Resource is closed" }
        block(resource)
    }

    suspend fun close() {
        mutex.withLock {
            if (closed) return
            closed = true
            runCatching { resource.close() }
        }
    }
}

/** Renderer plus its page count, captured at open time so the UI never
 *  needs the render lock just to size the pager. */
internal class PdfDocumentHandle(
    val renderer: SerializedCloseable<PdfRenderer>,
    val pageCount: Int,
)
```

3. Replace the body of the `PdfReader` composable between the Koin injections and the "Empty PDF" check — i.e. delete the old `localFileResult` producer, the null/error branches for it, `val localFile = ...`, the `remember(localFile) { openRendererResult(localFile) }` line and its error branch, `val renderer = ...`, `val renderMutex = ...`, and the `DisposableEffect` — with:

```kotlin
    // Resolve the file AND open the renderer in one IO step so the
    // ParcelFileDescriptor + PdfRenderer construction never runs in
    // composition on the main thread.
    val handleResult by produceState<Result<PdfDocumentHandle>?>(initialValue = null, fileUrl) {
        val produced = withContext(Dispatchers.IO) {
            runCatching {
                val file = resolveReaderFile(context, okHttp, fileUrl, tokenManager.getServerUrl(), "pdf")
                val renderer = openRenderer(file)
                PdfDocumentHandle(SerializedCloseable(renderer), renderer.pageCount)
            }
        }
        value = produced
        // Close under the render mutex from a scope that outlives this
        // composition: a render blocked in native page.render() holds
        // the mutex, so close waits for it instead of yanking the
        // renderer away mid-render (the old onDispose race).
        awaitDispose {
            produced.getOrNull()?.let { handle ->
                CoroutineScope(Dispatchers.IO).launch { handle.renderer.close() }
            }
        }
    }

    val result = handleResult
    if (result == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    result.exceptionOrNull()?.let { throwable ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(readerLoadErrorMessage(throwable), modifier = Modifier.padding(32.dp))
        }
        return
    }
    val handle = result.getOrThrow()
```

(Match the existing loading/error composable idioms in this file — if it uses a different error-message helper than `readerLoadErrorMessage`, keep the file's existing one.)

4. Replace every remaining `renderer.pageCount` in the composable with `handle.pageCount` (the empty check, `onPageCountKnown`, both `LaunchedEffect` keys/bodies, `rememberPagerState`), and change the pager content to:

```kotlin
    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) { page ->
        PdfPage(handle = handle, pageIndex = page)
    }
```

5. Replace `PdfPage` and `renderPdfPageBitmap` (delete `openRendererResult`; keep `openRenderer` unchanged):

```kotlin
@Composable
private fun PdfPage(handle: PdfDocumentHandle, pageIndex: Int) {
    var bitmapResult by remember(pageIndex) { mutableStateOf<Result<Bitmap>?>(null) }
    LaunchedEffect(pageIndex) {
        bitmapResult = withContext(Dispatchers.IO) { renderPdfPageBitmap(handle, pageIndex) }
    }
    Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
        when (val result = bitmapResult) {
            null -> CircularProgressIndicator()
            else -> result.fold(
                onSuccess = { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Page ${pageIndex + 1}",
                        modifier = Modifier
                            .fillMaxSize()
                            .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat()),
                        contentScale = ContentScale.Fit,
                    )
                },
                onFailure = { throwable ->
                    Text(readerLoadErrorMessage(throwable), modifier = Modifier.padding(32.dp))
                },
            )
        }
    }
}

private suspend fun renderPdfPageBitmap(
    handle: PdfDocumentHandle,
    pageIndex: Int,
): Result<Bitmap> =
    runCatching {
        handle.renderer.withResource { renderer ->
            renderer.openPage(pageIndex).use { page ->
                // Render at 2x for sharper text on high-DPI; cap at
                // 2000px width to keep memory in check.
                val targetWidth = page.width * 2
                val widthCap = targetWidth.coerceAtMost(2000)
                val scale = widthCap.toFloat() / page.width
                val targetHeight = (page.height * scale).toInt()
                val bmp = Bitmap.createBitmap(widthCap, targetHeight, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    }
```

(Preserve the current render-scaling math from the existing `renderPdfPageBitmap` if it differs from the above — the change is the `handle.renderer.withResource` wrapping, not the scaling.)

- [ ] **Step 4: Run tests**

```bash
./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.*" && ./gradlew :androidApp:assembleDebug
```

Then the manual check from Step 1 (open large PDF, back out mid-render repeatedly).

- [ ] **Step 5: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PdfReader.kt androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/SerializedCloseableTest.kt && git commit -m "Serialize PdfRenderer close against page rendering

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Task C5: One ZipFile per comic reader lifetime + downsampled page decode

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicReader.kt`
- Test: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ComicArchiveLoaderTest.kt`

Background: `decodeComicPageBitmap` (ComicReader.kt:170-178) re-opens the `ZipFile` (full central-directory re-parse) for every page swipe and decodes with no `BitmapFactory.Options` — full-res ARGB_8888, easily 40+ MB per page for high-res scans. Fix: open one `ZipFile` for the reader's lifetime (closed via `awaitDispose`; `java.util.zip.ZipFile` is internally synchronized so concurrent decodes from pager pre-composition are safe) and two-pass decode (`inJustDecodeBounds` then `inSampleSize`) targeted at the device's larger screen dimension. The sample-size math is pure and unit-testable; the ZipFile lifetime and `BitmapFactory` calls are not (android.jar stubs).

- [ ] **Step 1: Write the failing test**

Add to `ComicArchiveLoaderTest.kt` (inside the class):

```kotlin
    @Test
    fun `sample size halves dimensions until under the target`() {
        assertEquals(1, comicSampleSize(width = 1000, height = 1500, targetMaxDimension = 1920))
        assertEquals(2, comicSampleSize(width = 4000, height = 3000, targetMaxDimension = 1920))
        assertEquals(2, comicSampleSize(width = 3840, height = 1080, targetMaxDimension = 1920))
        assertEquals(4, comicSampleSize(width = 8000, height = 6000, targetMaxDimension = 1920))
    }

    @Test
    fun `sample size degrades to full resolution on unusable inputs`() {
        assertEquals(1, comicSampleSize(width = 0, height = 0, targetMaxDimension = 1920))
        assertEquals(1, comicSampleSize(width = -1, height = 100, targetMaxDimension = 1920))
        assertEquals(1, comicSampleSize(width = 100, height = 100, targetMaxDimension = 0))
    }
```

The ZipFile-lifetime change is not unit-testable; manual check: swipe rapidly through a 100+ page CBZ — swipes stay smooth (no per-page archive re-open), memory stays bounded for high-res scans, and navigating away then back re-opens the comic cleanly (no "zip file closed" errors on-screen).

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.ComicArchiveLoaderTest"
```

Expected: compilation failure — `Unresolved reference: comicSampleSize`.

- [ ] **Step 3: Implementation**

In `ComicReader.kt` (state after Task 2 of this section):

1. Imports: add `android.graphics.Bitmap`; everything else already present.

2. In the `ComicReader` composable, after the resolved `file` is available, replace the `archiveState` producer and `pages` extraction (old lines 90-113) with:

```kotlin
    // Decode pages at roughly the display size instead of full-res.
    val targetMaxDimension = remember(context) {
        val metrics = context.resources.displayMetrics
        maxOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1080)
    }

    // One ZipFile per reader lifetime: every page decodes from this
    // handle instead of re-opening (and re-parsing the central
    // directory of) the archive on each swipe.
    val archiveState by produceState<OpenedComicArchive?>(initialValue = null, file) {
        val opened = withContext(Dispatchers.IO) {
            runCatching { ZipFile(file) }.fold(
                onSuccess = { zip -> OpenedComicArchive(zip, listComicArchivePages(zip)) },
                onFailure = { throwable ->
                    OpenedComicArchive(
                        zip = null,
                        result = ComicArchiveLoadResult.Error(
                            throwable.message?.takeIf { it.isNotBlank() } ?: "Could not open comic archive.",
                        ),
                    )
                },
            )
        }
        value = opened
        awaitDispose {
            opened.zip?.let { zip -> runCatching { zip.close() } }
        }
    }
    val archive = archiveState
    val pages = when (val result = archive?.result) {
        null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }
        is ComicArchiveLoadResult.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Could not open this comic archive.")
            }
            return
        }
        ComicArchiveLoadResult.Empty -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No readable images found in this comic archive.")
            }
            return
        }
        is ComicArchiveLoadResult.Loaded -> result.pages
    }
    val zip = archive?.zip ?: return
```

(`OpenedComicArchive` needs a secondary-constructor-free shape — define it as shown below with `result` defaulting from the listing; adapt to keep the existing `ComicArchiveLoadResult` handling idioms in the file.)

3. Change the pager content block to:

```kotlin
    ) { pageIndex ->
        ComicPage(zip = zip, entryName = pages[pageIndex].entryName, targetMaxDimension = targetMaxDimension)
    }
```

4. Replace `ComicPage` and `decodeComicPageBitmap`, add the holder + new helpers, and rewrite `loadComicArchivePages` to delegate (the existing tests keep passing through the file-based entry point):

```kotlin
@Composable
private fun ComicPage(zip: ZipFile, entryName: String, targetMaxDimension: Int) {
    var bitmapResult by remember(entryName) { mutableStateOf<Result<Bitmap>?>(null) }
    LaunchedEffect(entryName) {
        bitmapResult = withContext(Dispatchers.IO) {
            decodeComicPageBitmap(zip, entryName, targetMaxDimension)
        }
    }
    Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
        when (val result = bitmapResult) {
            null -> CircularProgressIndicator()
            else -> result.fold(
                onSuccess = { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = entryName,
                        modifier = Modifier
                            .fillMaxSize()
                            .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat()),
                        contentScale = ContentScale.Fit,
                    )
                },
                onFailure = { throwable ->
                    Text(readerLoadErrorMessage(throwable), modifier = Modifier.padding(32.dp))
                },
            )
        }
    }
}

/** The open archive handle paired with its page listing; the zip stays
 *  open for the reader's lifetime and is closed on dispose. */
private class OpenedComicArchive(
    val zip: ZipFile?,
    val result: ComicArchiveLoadResult,
)

/** Power-of-two BitmapFactory.inSampleSize so the decoded bitmap's max
 *  dimension stays at or just above [targetMaxDimension]. */
internal fun comicSampleSize(width: Int, height: Int, targetMaxDimension: Int): Int {
    if (width <= 0 || height <= 0 || targetMaxDimension <= 0) return 1
    var sampleSize = 1
    val maxDimension = maxOf(width, height)
    while (maxDimension / (sampleSize * 2) >= targetMaxDimension) {
        sampleSize *= 2
    }
    return sampleSize
}

private fun decodeComicPageBitmap(
    zip: ZipFile,
    entryName: String,
    targetMaxDimension: Int,
): Result<Bitmap> =
    requiredReaderLoadResult("Could not decode comic page.") {
        val entry = zip.getEntry(entryName) ?: return@requiredReaderLoadResult null
        // Pass 1: bounds only, so we can downsample to roughly the
        // display size instead of decoding full-res ARGB_8888.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        zip.getInputStream(entry).use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = comicSampleSize(bounds.outWidth, bounds.outHeight, targetMaxDimension)
        }
        zip.getInputStream(entry).use { stream -> BitmapFactory.decodeStream(stream, null, options) }
    }
```

```kotlin
internal fun loadComicArchivePages(file: File): ComicArchiveLoadResult =
    runCatching { ZipFile(file).use(::listComicArchivePages) }
        .getOrElse { throwable ->
            ComicArchiveLoadResult.Error(
                throwable.message?.takeIf { it.isNotBlank() } ?: "Could not open comic archive.",
            )
        }

/** Image entries inside a CBZ, sorted lexicographically. */
internal fun listComicArchivePages(zip: ZipFile): ComicArchiveLoadResult {
    val exts = setOf("jpg", "jpeg", "png", "webp", "gif")
    val entries = zip.entries().toList()
        .filter { !it.isDirectory && it.name.substringAfterLast('.', "").lowercase() in exts }
        .map { it.name }
        .sorted()
    return if (entries.isEmpty()) {
        ComicArchiveLoadResult.Empty
    } else {
        ComicArchiveLoadResult.Loaded(
            entries.mapIndexed { index, entryName ->
                ComicArchivePage(index = index, entryName = entryName)
            },
        )
    }
}
```

NOTE FOR IMPLEMENTER: `loadComicArchivePages`/`listComicArchivePages` above must preserve the CURRENT extension set, sorting, and `ComicArchivePage` shape — diff against the existing implementation and keep its exact filtering/sorting semantics; only the zip-handle plumbing and two-pass decode are new. If `requiredReaderLoadResult` doesn't exist, use the file's existing decode-error idiom. (`ComicArchivePage` and `ComicArchiveLoadResult` are unchanged. A decode racing the dispose-time `zip.close()` surfaces as an `IOException` → a failed `Result` on a screen that is already gone, not a crash.)

- [ ] **Step 4: Run tests**

```bash
./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.*" && ./gradlew :androidApp:assembleDebug
```

Then the manual check from Step 1 (rapid swiping through a large CBZ).

- [ ] **Step 5: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicReader.kt androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ComicArchiveLoaderTest.kt && git commit -m "Reuse comic ZipFile and downsample page decodes

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

### Task C6: Load EPUB chapter HTML off the main thread and stop redundant WebView reloads

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/EpubReader.kt`
- Test: none (Compose/WebView; see Step 1)

Background: `EpubChapter`'s `AndroidView` `update` lambda (EpubReader.kt:150-158) runs `book.readChapterHtml` (a `File.readText`) + regex CSS injection + `loadDataWithBaseURL` on the main thread on recomposition, and unconditionally reloading resets the WebView's scroll position even when the content is identical. Fix: produce the styled HTML via `produceState` on Dispatchers.IO keyed on `(book, href, settings)` (`ReaderDisplaySettings` is a data class, so structural equality works as a key), and in `update` only call `loadDataWithBaseURL` when the HTML actually differs from the last-loaded string, tracked in the view's `tag`.

- [ ] **Step 1: Write the failing test**

Not unit-testable because the change lives entirely in `AndroidView`/`WebView` Compose wiring. Manual check: (1) open an EPUB, scroll partway down a chapter, then trigger unrelated recompositions (swipe to the next chapter and back) — the scroll position must no longer reset to the top; (2) toggle reader settings (theme, text size) — the chapter restyles, with the file read happening off-main (no jank); (3) a spine href pointing at a missing file shows "Could not load this chapter." instead of a permanently blank WebView.

- [ ] **Step 2: Run test to verify it fails**

Not applicable (no unit test). Green baseline:

```bash
./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.*"
```

- [ ] **Step 3: Implementation**

In `EpubReader.kt`, replace the `EpubChapter` composable (lines 137-160) with:

```kotlin
/** Styled chapter HTML; null [html] means the spine entry's file is
 *  missing from the archive. */
private class EpubChapterContent(val html: String?)

@Composable
private fun EpubChapter(book: EpubBook, chapterIndex: Int, settings: ReaderDisplaySettings) {
    val href = book.spine.getOrNull(chapterIndex)
    // Read + style the chapter off the main thread; re-runs only when
    // the chapter or display settings actually change, not on every
    // recomposition like the old AndroidView update lambda did.
    val content by produceState<EpubChapterContent?>(initialValue = null, book, href, settings) {
        value = withContext(Dispatchers.IO) {
            EpubChapterContent(href?.let { book.readChapterHtml(it)?.withReaderCss(settings) })
        }
    }
    val loaded = content
    if (loaded == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val html = loaded.html
    if (html == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Could not load this chapter.", modifier = Modifier.padding(32.dp))
        }
        return
    }
    AndroidView(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                this.settings.javaScriptEnabled = false  // chapters are static HTML
                this.settings.allowFileAccess = true
                this.settings.allowContentAccess = true
                this.settings.builtInZoomControls = true
                this.settings.displayZoomControls = false
            }
        },
        update = { web ->
            // Only reload when the produced HTML actually changed —
            // update runs on every recomposition, and an unconditional
            // loadDataWithBaseURL resets the WebView scroll position.
            if (web.tag != html) {
                web.tag = html
                // baseUrl lets relative <img src> / <link rel='stylesheet'>
                // refs inside the chapter resolve against the unpacked epub
                // root on disk.
                val base = "file://${book.unpackedRoot.absolutePath}/"
                web.loadDataWithBaseURL(base, html, "text/html", "utf-8", null)
            }
        },
    )
}
```

NOTE FOR IMPLEMENTER: diff against the current `EpubChapter` first — preserve the exact existing WebView settings (factory block), the existing CSS-injection helper name (`withReaderCss` or whatever the file calls it), and the existing baseUrl construction. The behavioral deltas are ONLY: HTML produced off-main via produceState, and the `web.tag != html` reload guard. No import changes expected: `produceState`, `getValue`, `withContext`, `Dispatchers`, `Box`, `Alignment`, `CircularProgressIndicator`, and `Text` are already imported in this file.

- [ ] **Step 4: Run tests**

```bash
./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.*" && ./gradlew :androidApp:assembleDebug
```

Then the manual check from Step 1 (scroll retention + settings toggle in an EPUB).

- [ ] **Step 5: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/EpubReader.kt && git commit -m "Load EPUB chapter HTML off the main thread

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

## Section D: TV app wiring (Watch Next worker, legacy prefs migration, backdrop tint)

### Task D1: Fix WatchNextSyncWorker instantiation (TvWorkerFactory + manifest opt-out + explicit WorkManager init)

**Files:**
- Create: `/Users/dev/projects/prairie/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/watchnext/TvWorkerFactory.kt`
- Modify: `/Users/dev/projects/prairie/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ContinuumTvApplication.kt`
- Modify: `/Users/dev/projects/prairie/prairie-android/androidTvApp/src/androidMain/AndroidManifest.xml`
- Modify: `/Users/dev/projects/prairie/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt`
- Modify: `/Users/dev/projects/prairie/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/watchnext/WatchNextSyncWorker.kt` (doc comment only)
- Test: none (manual verification — see Step 1)

- [ ] **Step 1: Write the failing test**

Not unit-testable: the bug lives in the WorkManager init race (androidx.startup auto-init vs `Application.onCreate`) plus Koin's `KoinWorkerFactory` silently returning null on WM 2.10 + Koin 4.1.0 — none of which is reachable from a JVM unit test, and the phone app's identical `AppWorkerFactory` fix (`androidApp/src/androidMain/kotlin/com/continuum/app/android/downloads/AppWorkerFactory.kt`) ships without a unit test either (androidApp's unit-test suite has no worker-factory test).

Manual check instead:

*Before the fix (reproduce):* install the current debug build on a TV device/emulator, sign in and select a profile (this triggers `WatchNextSeeder.seedNow()` from `TvAppNavigation.kt:137`), then:
```bash
adb logcat -d -s WM-WorkerFactory:* WM-WorkerWrapper:*
```
Expected output contains:
```
Could not instantiate com.continuum.app.tv.watchnext.WatchNextSyncWorker
java.lang.NoSuchMethodException: com.continuum.app.tv.watchnext.WatchNextSyncWorker.<init> [class android.content.Context, class androidx.work.WorkerParameters]
```

*After the fix (verify):*
```bash
adb logcat -d -s TvWorkerFactory:* ContinuumTvApplication:*
```
Expected: `TvWorkerFactory constructed`, `WorkManager.initialize called with TvWorkerFactory`, `createWorker called for com.continuum.app.tv.watchnext.WatchNextSyncWorker`, `Building WatchNextSyncWorker via Koin`, and NO `Could not instantiate` lines from `WM-WorkerFactory`. Confirm the launcher row landed:
```bash
adb shell content query --uri content://android.media.tv/watch_next_program
```
Expected: rows with `package_name=com.continuum.app.tv` (after sign-in + profile select with continue-watching content on the server).

- [ ] **Step 2: Run test to verify it fails**

Run the *before* manual check above on the current branch build (`./gradlew :androidTvApp:assembleDebug`, install the universal debug APK from `androidTvApp/build/outputs/apk/debug/`). Expected failure: the `Could not instantiate ... WatchNextSyncWorker` / `NoSuchMethodException` logcat lines appear and the Watch Next content-provider query returns no rows for `com.continuum.app.tv`.

- [ ] **Step 3: Implementation**

**3a. Create `/Users/dev/projects/prairie/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/watchnext/TvWorkerFactory.kt`:**

```kotlin
package com.continuum.app.tv.watchnext

import android.content.Context
import android.util.Log
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.continuum.app.repository.SectionRepository
import org.koin.core.context.GlobalContext

/**
 * Hand-rolled WorkerFactory that constructs DI-dependent workers via Koin.
 *
 * TV twin of androidApp's `AppWorkerFactory`: koin-androidx-workmanager's
 * `workManagerFactory()` / `KoinWorkerFactory` silently returns null on
 * this codebase (WM 2.10 + Koin 4.1.0), forcing WorkerFactory to fall back
 * to reflection — which crashes because [WatchNextSyncWorker] takes
 * injected dependencies, not the default `(Context, WorkerParameters)`
 * constructor.
 *
 * Add a `when` branch per new worker class.
 */
class TvWorkerFactory : WorkerFactory() {
    init {
        Log.i(TAG, "TvWorkerFactory constructed")
    }

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        Log.i(TAG, "createWorker called for $workerClassName")
        val koin = GlobalContext.get()
        return when (workerClassName) {
            WatchNextSyncWorker::class.java.name -> {
                Log.i(TAG, "Building WatchNextSyncWorker via Koin")
                WatchNextSyncWorker(
                    appContext = appContext,
                    params = workerParameters,
                    sectionRepository = koin.get<SectionRepository>(),
                    repository = koin.get<WatchNextRepository>(),
                )
            }
            else -> {
                Log.w(TAG, "No factory match for $workerClassName — returning null")
                null
            }
        }
    }

    companion object {
        private const val TAG = "TvWorkerFactory"
    }
}
```

**3b. Replace the entire contents of `/Users/dev/projects/prairie/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ContinuumTvApplication.kt`:**

```kotlin
package com.continuum.app.tv

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.continuum.app.common.di.playerInfraModule
import com.continuum.app.common.di.playerModule
import com.continuum.app.di.sharedModules
import com.continuum.app.tv.di.androidTvModule
import com.continuum.app.tv.watchnext.TvWorkerFactory
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Implements `Configuration.Provider` rather than installing Koin's
 * `workManagerFactory()` inside `startKoin { … }`. The Koin path loses the
 * race against WorkManager's androidx.startup auto-init (which runs before
 * `Application.onCreate` on many devices), silently leaving WorkManager on
 * its reflection-based factory — which cannot construct
 * [com.continuum.app.tv.watchnext.WatchNextSyncWorker]'s injected
 * constructor. Mirrors the phone app's ContinuumApplication +
 * AppWorkerFactory recipe (see that file for the full history).
 */
class ContinuumTvApplication : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ContinuumTvApplication)
            modules(sharedModules() + playerModule + playerInfraModule + androidTvModule)
        }
        // The androidx.startup WorkManagerInitializer is opted out in the
        // manifest; force-initialise explicitly with TvWorkerFactory now
        // that Koin is up. runCatching guards the "already initialised"
        // IllegalStateException in case anything else got there first.
        runCatching {
            WorkManager.initialize(this, workManagerConfiguration)
            android.util.Log.i("ContinuumTvApplication", "WorkManager.initialize called with TvWorkerFactory")
        }.onFailure {
            android.util.Log.w("ContinuumTvApplication", "WorkManager.initialize failed (already initialised?)", it)
        }
    }

    override val workManagerConfiguration: Configuration
        get() {
            android.util.Log.i("ContinuumTvApplication", "workManagerConfiguration accessed; installing TvWorkerFactory")
            return Configuration.Builder()
                .setWorkerFactory(TvWorkerFactory())
                .build()
        }
}
```

**3c. In `/Users/dev/projects/prairie/prairie-android/androidTvApp/src/androidMain/AndroidManifest.xml`** — add the `tools` namespace to the root element:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
```

and insert this block after the `</service>` closing tag of `ContinuumPlaybackService`, before `</application>` (mirrors `androidApp/src/androidMain/AndroidManifest.xml` lines 63–71):

```xml
        <!-- Disable WorkManager's androidx.startup auto-init so our explicit
             `WorkManager.initialize(this, workManagerConfiguration)` call in
             ContinuumTvApplication.onCreate wins with TvWorkerFactory wired
             in. Otherwise auto-init runs first with the default reflection
             factory and WatchNextSyncWorker can never be instantiated. -->
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup"
                tools:node="remove" />
        </provider>
```

**3d. In `/Users/dev/projects/prairie/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt`** — delete these two imports:

```kotlin
import com.continuum.app.tv.watchnext.WatchNextSyncWorker
import org.koin.androidx.workmanager.dsl.worker
```

and replace the Watch Next registration block (lines 97–102):

```kotlin
    // Watch Next launcher integration (TV-only). Repository wraps the
    // TvProvider ContentResolver; the worker is constructed by Koin's
    // WorkerFactory — see workManagerFactory() in ContinuumTvApplication.
    single { WatchNextRepository(androidContext()) }
    single { WatchNextSeeder(androidContext(), get()) }
    worker { WatchNextSyncWorker(androidContext(), get(), get(), get()) }
```

with:

```kotlin
    // Watch Next launcher integration (TV-only). Repository wraps the
    // TvProvider ContentResolver; the worker is constructed by
    // TvWorkerFactory, installed via WorkManager.initialize in
    // ContinuumTvApplication (Koin's worker DSL is not used — see
    // TvWorkerFactory for why).
    single { WatchNextRepository(androidContext()) }
    single { WatchNextSeeder(androidContext(), get()) }
```

**3e. In `/Users/dev/projects/prairie/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/watchnext/WatchNextSyncWorker.kt`** — replace the stale doc-comment lines:

```kotlin
 * Constructed by Koin's [org.koin.androidx.workmanager.factory.KoinWorkerFactory]
 * — see the `worker { ... }` registration in `AndroidTvModule`.
```

with:

```kotlin
 * Constructed by [TvWorkerFactory], installed via `WorkManager.initialize`
 * in `ContinuumTvApplication` (KoinWorkerFactory was silently returning
 * null on WM 2.10 + Koin 4.1.0 — see androidApp's AppWorkerFactory).
```

No changes needed to `WatchNextSeeder.kt` — it only uses `WorkManager.getInstance(context)` + request builders, which work unchanged against the explicitly-initialized instance.

- [ ] **Step 4: Run tests**

```bash
./gradlew :androidTvApp:assembleDebug :androidTvApp:testDebugUnitTest
```
Then re-run the *after* manual check from Step 1 on a device/emulator.

- [ ] **Step 5: Commit**

```bash
cd /Users/dev/projects/prairie/prairie-android && git add androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/watchnext/TvWorkerFactory.kt androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ContinuumTvApplication.kt androidTvApp/src/androidMain/AndroidManifest.xml androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/watchnext/WatchNextSyncWorker.kt && git commit -m "$(cat <<'EOF'
Fix WatchNextSyncWorker instantiation on TV

Mirror the phone app's WorkManager recipe: hand-rolled TvWorkerFactory,
manifest opt-out of androidx.startup auto-init, and explicit
WorkManager.initialize in ContinuumTvApplication. Koin's
workManagerFactory() lost the auto-init race and KoinWorkerFactory
silently returned null on WM 2.10 + Koin 4.1.0, so the Watch Next sync
worker could never be constructed.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

### Task D2: Reinstate one-shot legacy tv_prefs migration (playback settings + library selection)

**Files:**
- Create: `/Users/dev/projects/prairie/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/data/preferences/LegacyTvPrefsMigration.kt`
- Modify: `/Users/dev/projects/prairie/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/settings/TvSettingsViewModel.kt`
- Modify: `/Users/dev/projects/prairie/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/libraries/TvLibrariesViewModel.kt`
- Modify: `/Users/dev/projects/prairie/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt`
- Modify: `/Users/dev/projects/prairie/prairie-android/androidTvApp/build.gradle.kts` (add `kotlinx-coroutines-test` to androidUnitTest deps)
- Test: `/Users/dev/projects/prairie/prairie-android/androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/data/preferences/LegacyTvPrefsMigrationTest.kt`

Design notes (decided after reading main's `TvPreferences.kt` / `TvSettingsViewModel.kt`):
- Exact legacy keys in the `tv_prefs` DataStore: `playback_quality` (string wire value), `subtitle_size` (string label `Small`/`Medium`/`Large`), `auto_play_next` (bool, default true), `auto_skip_intro` (bool, default false), `auto_skip_credits` (bool, default false), `libraries_selected_library_id` (int).
- Main's migration ran inside `TvSettingsViewModel.loadSettings`, gated by `AndroidServerSettingsCache.isMigrationComplete(serverUrl, "android-tv-settings")`, and only pushed each legacy value when `getEffectiveSettings(...)` reported `hasDeviceOverride != true` for that key, then called `flushPendingDeviceSettings()` and marked the sentinel. We reuse the exact same scope string so devices already migrated on main never rerun.
- Library-selection mapping (old value was global; new `TvLibrarySelectionStore` is per-profile): seed the *currently active* profile's selection with the legacy id, only if that profile has no stored selection yet; gated by its own sentinel scope `"android-tv-library-selection"` which is deferred (not marked) until a profile is active. Other profiles fall back to `TvLibrariesViewModel`'s existing first-visible-library default. Called from `TvLibrariesViewModel.load()` *before* the first `getSelectedLibraryId()` read (which otherwise immediately writes a resolved value, making the seed unreachable), and from `TvSettingsViewModel.loadSettings` to match main's behavior.

- [ ] **Step 1: Write the failing test**

First add the missing coroutines-test dependency. In `/Users/dev/projects/prairie/prairie-android/androidTvApp/build.gradle.kts`, change:

```kotlin
        androidUnitTest.dependencies {
            implementation(kotlin("test"))
            implementation(kotlin("test-junit"))
        }
```

to:

```kotlin
        androidUnitTest.dependencies {
            implementation(kotlin("test"))
            implementation(kotlin("test-junit"))
            implementation(libs.kotlinx.coroutines.test)
        }
```

Create `/Users/dev/projects/prairie/prairie-android/androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/data/preferences/LegacyTvPrefsMigrationTest.kt`:

```kotlin
package com.continuum.app.tv.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.continuum.app.common.settings.AndroidServerSettingsCache
import com.continuum.app.common.settings.PlayerSettingsStore
import com.continuum.app.model.settings.EffectiveSetting
import com.continuum.app.model.settings.PlaybackSettingsKeys
import com.continuum.app.model.settings.SubtitleAppearance
import com.continuum.app.model.settings.SubtitleFontSizePreset
import com.continuum.app.network.TokenManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegacyTvPrefsMigrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val serverUrl = "https://tv.example"
    private val profileId = "profile-1"

    // Sentinel scopes — must match LegacyTvPrefsMigration's companion
    // ("android-tv-settings" is main's historical scope).
    private val playbackScope = "android-tv-settings"
    private val libraryScope = "android-tv-library-selection"

    // Exact legacy key strings from main's TvPreferences.
    private val legacyQualityKey = stringPreferencesKey("playback_quality")
    private val legacySubtitleSizeKey = stringPreferencesKey("subtitle_size")
    private val legacyAutoPlayNextKey = booleanPreferencesKey("auto_play_next")
    private val legacyAutoSkipIntroKey = booleanPreferencesKey("auto_skip_intro")
    private val legacyAutoSkipCreditsKey = booleanPreferencesKey("auto_skip_credits")
    private val legacySelectedLibraryIdKey = intPreferencesKey("libraries_selected_library_id")

    private lateinit var fakePlayerStore: FakePlayerSettingsStore
    private lateinit var fakeCache: FakeSettingsCache
    private lateinit var tokenManager: FakeTokenManager
    private lateinit var selectionStore: TvLibrarySelectionStore

    @Before
    fun setup() {
        fakePlayerStore = FakePlayerSettingsStore()
        fakeCache = FakeSettingsCache()
        tokenManager = FakeTokenManager(serverUrl = serverUrl, profileId = profileId)
        selectionStore = TvLibrarySelectionStore(
            context = mockContextStub(),
            tokenManager = tokenManager,
            dataStoreFactory = { id ->
                PreferenceDataStoreFactory.create(
                    produceFile = { File(tempFolder.root, "lib_$id.preferences_pb") },
                )
            },
        )
    }

    private fun legacyStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { File(tempFolder.root, "tv_prefs.preferences_pb") },
        )

    private fun newMigration(
        legacy: DataStore<Preferences>?,
        effective: Map<String, EffectiveSetting> = emptyMap(),
    ): LegacyTvPrefsMigration = LegacyTvPrefsMigration(
        context = mockContextStub(),
        settingsCache = fakeCache,
        playerSettingsStore = fakePlayerStore,
        librarySelectionStore = selectionStore,
        getServerUrl = { tokenManager.getServerUrl() },
        getProfileId = { tokenManager.getProfileId() },
        getEffectiveSettings = { effective },
        legacyStoreProvider = { legacy },
    )

    @Test
    fun `imports legacy playback settings once and marks sentinel`() = runTest {
        val legacy = legacyStore()
        legacy.edit { prefs ->
            prefs[legacyQualityKey] = "1080p"
            prefs[legacyAutoPlayNextKey] = false
            prefs[legacyAutoSkipIntroKey] = true
            prefs[legacyAutoSkipCreditsKey] = true
            prefs[legacySubtitleSizeKey] = "Large"
        }
        val migration = newMigration(legacy)
        migration.migrateIfNeeded()

        assertEquals("1080p", fakePlayerStore.preferredQualityFlow.value)
        assertEquals(false, fakePlayerStore.autoPlayNextFlow.value)
        assertEquals(true, fakePlayerStore.autoSkipIntroFlow.value)
        assertEquals(true, fakePlayerStore.autoSkipCreditsFlow.value)
        assertEquals(
            SubtitleFontSizePreset.Large,
            fakePlayerStore.subtitleAppearanceFlow.value.fontSize,
        )
        assertTrue(fakePlayerStore.flushCount >= 1)
        assertTrue(fakeCache.isMigrationComplete(serverUrl, playbackScope))

        // Second run is a no-op.
        val callsAfterFirstRun = fakePlayerStore.setterCalls.size
        migration.migrateIfNeeded()
        assertEquals(callsAfterFirstRun, fakePlayerStore.setterCalls.size)
    }

    @Test
    fun `existing server device override is not clobbered`() = runTest {
        val legacy = legacyStore()
        legacy.edit { prefs ->
            prefs[legacyQualityKey] = "720p"
            prefs[legacyAutoSkipIntroKey] = true
        }
        val effective = mapOf(
            PlaybackSettingsKeys.PreferredQuality to EffectiveSetting(
                key = PlaybackSettingsKeys.PreferredQuality,
                effectiveValue = "1080p",
                source = "device",
                hasDeviceOverride = true,
            ),
        )
        newMigration(legacy, effective).migrateIfNeeded()

        assertFalse(fakePlayerStore.setterCalls.contains("setPreferredQuality"))
        assertEquals("auto", fakePlayerStore.preferredQualityFlow.value)
        // Keys without a server override still import.
        assertEquals(true, fakePlayerStore.autoSkipIntroFlow.value)
    }

    @Test
    fun `pre-existing playback sentinel skips playback import but library still migrates`() = runTest {
        fakeCache.markMigrationComplete(serverUrl, playbackScope)
        val legacy = legacyStore()
        legacy.edit { prefs ->
            prefs[legacyQualityKey] = "720p"
            prefs[legacySelectedLibraryIdKey] = 42
        }
        newMigration(legacy).migrateIfNeeded()

        assertTrue(fakePlayerStore.setterCalls.isEmpty())
        assertEquals(42, selectionStore.getSelectedLibraryId())
    }

    @Test
    fun `seeds active profile library selection from legacy global key`() = runTest {
        val legacy = legacyStore()
        legacy.edit { prefs -> prefs[legacySelectedLibraryIdKey] = 42 }
        newMigration(legacy).migrateIfNeeded()

        assertEquals(42, selectionStore.getSelectedLibraryId())
        assertTrue(fakeCache.isMigrationComplete(serverUrl, libraryScope))
    }

    @Test
    fun `does not overwrite an existing per-profile selection`() = runTest {
        selectionStore.setSelectedLibraryId(7)
        val legacy = legacyStore()
        legacy.edit { prefs -> prefs[legacySelectedLibraryIdKey] = 42 }
        newMigration(legacy).migrateIfNeeded()

        assertEquals(7, selectionStore.getSelectedLibraryId())
        assertTrue(fakeCache.isMigrationComplete(serverUrl, libraryScope))
    }

    @Test
    fun `library migration waits for an active profile`() = runTest {
        tokenManager.profileId = null
        val legacy = legacyStore()
        legacy.edit { prefs -> prefs[legacySelectedLibraryIdKey] = 42 }
        val migration = newMigration(legacy)
        migration.migrateIfNeeded()

        assertTrue(fakeCache.isMigrationComplete(serverUrl, playbackScope))
        assertFalse(fakeCache.isMigrationComplete(serverUrl, libraryScope))

        // A profile becomes active — the next call seeds and completes.
        tokenManager.profileId = profileId
        migration.migrateIfNeeded()
        assertEquals(42, selectionStore.getSelectedLibraryId())
        assertTrue(fakeCache.isMigrationComplete(serverUrl, libraryScope))
    }

    @Test
    fun `missing legacy file marks both sentinels without importing`() = runTest {
        newMigration(legacy = null).migrateIfNeeded()

        assertTrue(fakePlayerStore.setterCalls.isEmpty())
        assertTrue(fakeCache.isMigrationComplete(serverUrl, playbackScope))
        assertTrue(fakeCache.isMigrationComplete(serverUrl, libraryScope))
    }

    @Test
    fun `blank server url defers migration entirely`() = runTest {
        tokenManager.serverUrl = ""
        val legacy = legacyStore()
        legacy.edit { prefs -> prefs[legacyQualityKey] = "1080p" }
        newMigration(legacy).migrateIfNeeded()

        assertTrue(fakePlayerStore.setterCalls.isEmpty())
        assertFalse(fakeCache.isMigrationComplete(serverUrl, playbackScope))
        assertFalse(fakeCache.isMigrationComplete(serverUrl, libraryScope))
    }

    /**
     * Context is only dereferenced by the default legacyStoreProvider /
     * dataStoreFactory; tests inject their own, so a null-wrapped stub
     * fails loudly if anything ever touches it. Mirrors
     * android-shared's AndroidPlayerSettingsStoreTest.
     */
    private fun mockContextStub(): android.content.Context =
        object : android.content.ContextWrapper(null) {}
}

/** Records setter calls and mirrors them into MutableStateFlows. */
private class FakePlayerSettingsStore : PlayerSettingsStore {
    val setterCalls = mutableListOf<String>()
    var flushCount = 0

    override val autoSkipIntroFlow = MutableStateFlow(false)
    override val autoSkipCreditsFlow = MutableStateFlow(false)
    override val autoPlayNextFlow = MutableStateFlow(true)
    override val hdrEnabledFlow = MutableStateFlow(true)
    override val dvProfile7HDR10FallbackFlow = MutableStateFlow(false)
    override val downloadsWifiOnlyFlow = MutableStateFlow(true)
    override val playbackSpeedFlow = MutableStateFlow(1.0)
    override val audioSyncMsFlow = MutableStateFlow(0)
    override val subtitleSyncMsFlow = MutableStateFlow(0)
    override val nextUpPromptSecondsFlow = MutableStateFlow(30)
    override val sleepTimerDefaultMinutesFlow = MutableStateFlow(0)
    override val preferredQualityFlow = MutableStateFlow("auto")
    override val audioLanguageFlow = MutableStateFlow("")
    override val videoGravityFlow = MutableStateFlow("fit")
    override val orientationModeFlow = MutableStateFlow("auto")
    override val subtitleAppearanceFlow = MutableStateFlow(SubtitleAppearance.DEFAULT)
    override val subtitleUsesDeviceOverrideFlow = MutableStateFlow(false)

    override suspend fun setAutoSkipIntro(value: Boolean) {
        setterCalls += "setAutoSkipIntro"; autoSkipIntroFlow.value = value
    }
    override suspend fun setAutoSkipCredits(value: Boolean) {
        setterCalls += "setAutoSkipCredits"; autoSkipCreditsFlow.value = value
    }
    override suspend fun setAutoPlayNext(value: Boolean) {
        setterCalls += "setAutoPlayNext"; autoPlayNextFlow.value = value
    }
    override suspend fun setHdrEnabled(value: Boolean) {
        setterCalls += "setHdrEnabled"; hdrEnabledFlow.value = value
    }
    override suspend fun setDvProfile7HDR10Fallback(value: Boolean) {
        setterCalls += "setDvProfile7HDR10Fallback"; dvProfile7HDR10FallbackFlow.value = value
    }
    override suspend fun setDownloadsWifiOnly(value: Boolean) {
        setterCalls += "setDownloadsWifiOnly"; downloadsWifiOnlyFlow.value = value
    }
    override suspend fun setPlaybackSpeed(value: Double) {
        setterCalls += "setPlaybackSpeed"; playbackSpeedFlow.value = value
    }
    override suspend fun setAudioSyncMs(value: Int) {
        setterCalls += "setAudioSyncMs"; audioSyncMsFlow.value = value
    }
    override suspend fun setSubtitleSyncMs(value: Int) {
        setterCalls += "setSubtitleSyncMs"; subtitleSyncMsFlow.value = value
    }
    override suspend fun setNextUpPromptSeconds(value: Int) {
        setterCalls += "setNextUpPromptSeconds"; nextUpPromptSecondsFlow.value = value
    }
    override suspend fun setSleepTimerDefaultMinutes(value: Int) {
        setterCalls += "setSleepTimerDefaultMinutes"; sleepTimerDefaultMinutesFlow.value = value
    }
    override suspend fun setPreferredQuality(value: String) {
        setterCalls += "setPreferredQuality"; preferredQualityFlow.value = value
    }
    override suspend fun setAudioLanguage(value: String) {
        setterCalls += "setAudioLanguage"; audioLanguageFlow.value = value
    }
    override suspend fun setVideoGravity(value: String) {
        setterCalls += "setVideoGravity"; videoGravityFlow.value = value
    }
    override suspend fun setOrientationMode(value: String) {
        setterCalls += "setOrientationMode"; orientationModeFlow.value = value
    }
    override suspend fun setSubtitleAppearance(value: SubtitleAppearance) {
        setterCalls += "setSubtitleAppearance"; subtitleAppearanceFlow.value = value
    }

    override suspend fun refreshFromServer() {}
    override suspend fun setSubtitleDeviceOverrideEnabled(enabled: Boolean) {}
    override suspend fun resetDeviceSetting(key: String) {}
    override suspend fun resetAllDeviceSettings() {}
    override suspend fun flushPendingDeviceSettings() {
        flushCount++
    }
}

/**
 * In-memory sentinel store — bypasses the SharedPreferences-backed base
 * implementation that requires a real Context. Pattern copied from
 * android-shared's AndroidPlayerSettingsStoreTest FakeLegacyCache.
 */
private class FakeSettingsCache : AndroidServerSettingsCache(stubContext()) {
    private val sentinels = mutableSetOf<String>()

    override fun isMigrationComplete(serverUrl: String, scope: String): Boolean =
        migrationKey(serverUrl, scope) in sentinels

    override fun markMigrationComplete(serverUrl: String, scope: String) {
        sentinels += migrationKey(serverUrl, scope)
    }

    companion object {
        fun stubContext(): android.content.Context =
            object : android.content.ContextWrapper(null) {
                override fun getSharedPreferences(
                    name: String?,
                    mode: Int,
                ): android.content.SharedPreferences = StubPrefs()
            }
    }
}

/** Minimal SharedPreferences stub for the cache's super constructor. */
private class StubPrefs : android.content.SharedPreferences {
    override fun getAll(): MutableMap<String, *> = mutableMapOf<String, Any>()
    override fun getString(p0: String?, p1: String?): String? = p1
    override fun getStringSet(p0: String?, p1: MutableSet<String>?): MutableSet<String>? = p1
    override fun getInt(p0: String?, p1: Int): Int = p1
    override fun getLong(p0: String?, p1: Long): Long = p1
    override fun getFloat(p0: String?, p1: Float): Float = p1
    override fun getBoolean(p0: String?, p1: Boolean): Boolean = p1
    override fun contains(p0: String?): Boolean = false
    override fun edit(): android.content.SharedPreferences.Editor = StubEditor()
    override fun registerOnSharedPreferenceChangeListener(p0: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(p0: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
}

private class StubEditor : android.content.SharedPreferences.Editor {
    override fun putString(p0: String?, p1: String?): android.content.SharedPreferences.Editor = this
    override fun putStringSet(p0: String?, p1: MutableSet<String>?): android.content.SharedPreferences.Editor = this
    override fun putInt(p0: String?, p1: Int): android.content.SharedPreferences.Editor = this
    override fun putLong(p0: String?, p1: Long): android.content.SharedPreferences.Editor = this
    override fun putFloat(p0: String?, p1: Float): android.content.SharedPreferences.Editor = this
    override fun putBoolean(p0: String?, p1: Boolean): android.content.SharedPreferences.Editor = this
    override fun remove(p0: String?): android.content.SharedPreferences.Editor = this
    override fun clear(): android.content.SharedPreferences.Editor = this
    override fun commit(): Boolean = true
    override fun apply() {}
}

/** Mutable-field fake covering the full TokenManager surface. */
private class FakeTokenManager(
    var serverUrl: String,
    var profileId: String?,
) : TokenManager {
    override val sessionExpired: SharedFlow<Unit> = MutableSharedFlow()
    override suspend fun getAccessToken(): String? = null
    override suspend fun getRefreshToken(): String? = null
    override suspend fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Long) {}
    override suspend fun clearTokens() {}
    override suspend fun invalidateSession() {}
    override suspend fun getProfileId(): String? = profileId
    override suspend fun setProfileId(profileId: String?) {
        this.profileId = profileId
    }
    override suspend fun getProfileToken(): String? = null
    override suspend fun setProfileToken(token: String?) {}
    override suspend fun getServerUrl(): String = serverUrl
    override suspend fun setServerUrl(url: String) {
        serverUrl = url
    }
    override suspend fun getCurrentServerId(): String? = null
    override suspend fun switchActiveServer(serverId: String?) {}
    override suspend fun signOutCurrentServer() {}
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /Users/dev/projects/prairie/prairie-android && ./gradlew :androidTvApp:testDebugUnitTest --tests "com.continuum.app.tv.data.preferences.LegacyTvPrefsMigrationTest"
```
Expected failure: compilation error in `compileDebugUnitTestKotlinAndroid` — `e: ... LegacyTvPrefsMigrationTest.kt: ... Unresolved reference 'LegacyTvPrefsMigration'` (the class does not exist yet).

- [ ] **Step 3: Implementation**

**3a. Create `/Users/dev/projects/prairie/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/data/preferences/LegacyTvPrefsMigration.kt`:**

```kotlin
package com.continuum.app.tv.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.continuum.app.common.settings.AndroidServerSettingsCache
import com.continuum.app.common.settings.PlayerSettingsStore
import com.continuum.app.model.settings.EffectiveSetting
import com.continuum.app.model.settings.PlaybackSettingsKeys
import com.continuum.app.model.settings.SubtitleAppearance
import com.continuum.app.model.settings.SubtitleFontSizePreset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One-shot import of the legacy `tv_prefs` DataStore (owned by the removed
 * `TvPreferences` class) into the stores that replaced it:
 *
 * - Playback settings (`playback_quality`, `auto_play_next`,
 *   `auto_skip_intro`, `auto_skip_credits`, `subtitle_size`) →
 *   [PlayerSettingsStore] device overrides — but only for keys the server
 *   reports no existing device override for, exactly like the migration
 *   main's TvSettingsViewModel ran. Reuses main's sentinel scope
 *   ("android-tv-settings") so devices that already migrated never rerun.
 *
 * - `libraries_selected_library_id` (legacy *global* int) → the currently
 *   active profile's slot in [TvLibrarySelectionStore] (the new model is
 *   per-profile). Seeded once, only when that profile has no stored
 *   selection; other profiles fall back to TvLibrariesViewModel's default
 *   (first visible library). Gated by its own sentinel
 *   ("android-tv-library-selection"), which stays unmarked — and the pass
 *   is retried on a later call — until a profile is active.
 *
 * The legacy DataStore file is only opened if it exists on disk; fresh
 * installs mark both sentinels immediately. A blank server URL (pre-auth)
 * defers everything to a later call. Safe to call from multiple
 * ViewModels — sentinel-gated, and a [Mutex] serializes concurrent calls.
 */
class LegacyTvPrefsMigration(
    private val context: Context,
    private val settingsCache: AndroidServerSettingsCache,
    private val playerSettingsStore: PlayerSettingsStore,
    private val librarySelectionStore: TvLibrarySelectionStore,
    private val getServerUrl: suspend () -> String,
    private val getProfileId: suspend () -> String?,
    private val getEffectiveSettings: suspend (keys: List<String>) -> Map<String, EffectiveSetting>,
    private val legacyStoreProvider: (Context) -> DataStore<Preferences>? = { ctx ->
        val file = ctx.preferencesDataStoreFile(LEGACY_STORE_NAME)
        if (file.exists()) {
            PreferenceDataStoreFactory.create(produceFile = { file })
        } else {
            null
        }
    },
) {

    private val mutex = Mutex()

    // Resolved at most once — creating two DataStores over the same file
    // throws IllegalStateException, so cache the (possibly null) handle.
    private var legacyStore: DataStore<Preferences>? = null
    private var legacyStoreResolved = false

    suspend fun migrateIfNeeded() {
        mutex.withLock {
            val serverUrl = getServerUrl()
            if (serverUrl.isBlank()) return

            val playbackDone = settingsCache.isMigrationComplete(serverUrl, PLAYBACK_SCOPE)
            val libraryDone = settingsCache.isMigrationComplete(serverUrl, LIBRARY_SCOPE)
            if (playbackDone && libraryDone) return

            val store = resolveLegacyStore()
            if (store == null) {
                // Fresh install — nothing to import. Mark complete so future
                // calls short-circuit before the file-existence check.
                settingsCache.markMigrationComplete(serverUrl, PLAYBACK_SCOPE)
                settingsCache.markMigrationComplete(serverUrl, LIBRARY_SCOPE)
                return
            }

            val prefs = store.data.first()
            if (!playbackDone) migratePlaybackSettings(serverUrl, prefs)
            if (!libraryDone) migrateLibrarySelection(serverUrl, prefs)
        }
    }

    private fun resolveLegacyStore(): DataStore<Preferences>? {
        if (!legacyStoreResolved) {
            legacyStore = legacyStoreProvider(context)
            legacyStoreResolved = true
        }
        return legacyStore
    }

    private suspend fun migratePlaybackSettings(serverUrl: String, prefs: Preferences) {
        val legacyQuality = PlaybackQuality.fromWire(prefs[LegacyPlaybackQualityKey]).wireValue
        val legacySubtitleSize = SubtitleSize.fromLabel(prefs[LegacySubtitleSizeKey])
        val legacyAutoPlayNext = prefs[LegacyAutoPlayNextKey] ?: true
        val legacyAutoSkipIntro = prefs[LegacyAutoSkipIntroKey] ?: false
        val legacyAutoSkipCredits = prefs[LegacyAutoSkipCreditsKey] ?: false

        // Push each legacy value only when the server reports no existing
        // device override for the same key — same guard main's migration
        // used, so state written by another session wins over stale local
        // prefs. Lookup failures resolve to an empty map upstream, which
        // means "no overrides" (also main's behavior).
        val effective = getEffectiveSettings(
            listOf(
                PlaybackSettingsKeys.PreferredQuality,
                PlaybackSettingsKeys.AutoPlayNext,
                PlaybackSettingsKeys.AutoSkipIntro,
                PlaybackSettingsKeys.AutoSkipCredits,
                PlaybackSettingsKeys.SubtitleAppearance,
            ),
        )

        if (effective[PlaybackSettingsKeys.PreferredQuality]?.hasDeviceOverride != true) {
            playerSettingsStore.setPreferredQuality(legacyQuality)
        }
        if (effective[PlaybackSettingsKeys.AutoPlayNext]?.hasDeviceOverride != true) {
            playerSettingsStore.setAutoPlayNext(legacyAutoPlayNext)
        }
        if (effective[PlaybackSettingsKeys.AutoSkipIntro]?.hasDeviceOverride != true) {
            playerSettingsStore.setAutoSkipIntro(legacyAutoSkipIntro)
        }
        if (effective[PlaybackSettingsKeys.AutoSkipCredits]?.hasDeviceOverride != true) {
            playerSettingsStore.setAutoSkipCredits(legacyAutoSkipCredits)
        }
        if (effective[PlaybackSettingsKeys.SubtitleAppearance]?.hasDeviceOverride != true) {
            playerSettingsStore.setSubtitleAppearance(
                SubtitleAppearance.DEFAULT.copy(fontSize = legacySubtitleSize.toFontSizePreset()),
            )
        }

        // Make sure the writes hit the server even if the user backs out
        // before the store's debounce fires.
        playerSettingsStore.flushPendingDeviceSettings()
        settingsCache.markMigrationComplete(serverUrl, PLAYBACK_SCOPE)
    }

    private suspend fun migrateLibrarySelection(serverUrl: String, prefs: Preferences) {
        // The per-profile store needs an active profile; leave the sentinel
        // unmarked so a later call (post profile-select) retries.
        getProfileId() ?: return
        val legacyId = prefs[LegacySelectedLibraryIdKey]
        if (legacyId != null && librarySelectionStore.getSelectedLibraryId() == null) {
            librarySelectionStore.setSelectedLibraryId(legacyId)
        }
        settingsCache.markMigrationComplete(serverUrl, LIBRARY_SCOPE)
    }

    private fun SubtitleSize.toFontSizePreset(): SubtitleFontSizePreset = when (this) {
        SubtitleSize.Small -> SubtitleFontSizePreset.Small
        SubtitleSize.Medium -> SubtitleFontSizePreset.Medium
        SubtitleSize.Large -> SubtitleFontSizePreset.Large
    }

    companion object {
        const val LEGACY_STORE_NAME = "tv_prefs"

        // Identical to main's TvSettingsViewModel.MIGRATION_SCOPE — devices
        // that already ran main's migration must not rerun this one.
        private const val PLAYBACK_SCOPE = "android-tv-settings"
        private const val LIBRARY_SCOPE = "android-tv-library-selection"

        // Exact key strings from main's TvPreferences.Keys.
        private val LegacyPlaybackQualityKey = stringPreferencesKey("playback_quality")
        private val LegacySubtitleSizeKey = stringPreferencesKey("subtitle_size")
        private val LegacyAutoPlayNextKey = booleanPreferencesKey("auto_play_next")
        private val LegacyAutoSkipIntroKey = booleanPreferencesKey("auto_skip_intro")
        private val LegacyAutoSkipCreditsKey = booleanPreferencesKey("auto_skip_credits")
        private val LegacySelectedLibraryIdKey = intPreferencesKey("libraries_selected_library_id")
    }
}
```

**3b. In `/Users/dev/projects/prairie/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/settings/TvSettingsViewModel.kt`** — add the import:

```kotlin
import com.continuum.app.tv.data.preferences.LegacyTvPrefsMigration
```

change the class doc + constructor from:

```kotlin
/**
 * ViewModel for the TV settings screen. Server-managed device settings
 * flow exclusively through [PlayerSettingsStore] (mirror of iOS
 * `PlayerSettings.shared`); profile-level subtitle prefs still go via
 * [profileRepository].
 *
 * Sign-out and switch-profile operations emit a one-shot [NavAction]
 * signal that the screen collects and forwards to the top-level NavHost.
 */
class TvSettingsViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val tokenManager: TokenManager,
    private val playerSettingsStore: PlayerSettingsStore,
    private val libraryPlaybackPrefsStore: LibraryPlaybackPrefsStore,
) : ViewModel() {
```

to:

```kotlin
/**
 * ViewModel for the TV settings screen. Server-managed device settings
 * flow exclusively through [PlayerSettingsStore] (mirror of iOS
 * `PlayerSettings.shared`); profile-level subtitle prefs still go via
 * [profileRepository]. [LegacyTvPrefsMigration] runs the one-time legacy
 * `tv_prefs` → server import on first boot (sentinel-gated no-op after).
 *
 * Sign-out and switch-profile operations emit a one-shot [NavAction]
 * signal that the screen collects and forwards to the top-level NavHost.
 */
class TvSettingsViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val tokenManager: TokenManager,
    private val playerSettingsStore: PlayerSettingsStore,
    private val libraryPlaybackPrefsStore: LibraryPlaybackPrefsStore,
    private val legacyTvPrefsMigration: LegacyTvPrefsMigration,
) : ViewModel() {
```

and change `loadSettings()` from:

```kotlin
    private fun loadSettings() {
        viewModelScope.launch {
            val serverUrl = tokenManager.getServerUrl()
            _uiState.update { it.copy(serverUrl = serverUrl) }

            // Pull effective device settings (cascade user → device → default).
```

to:

```kotlin
    private fun loadSettings() {
        viewModelScope.launch {
            val serverUrl = tokenManager.getServerUrl()
            _uiState.update { it.copy(serverUrl = serverUrl) }

            // One-shot import of pre-server-sync TvPreferences values.
            // Idempotent — sentinel-gated inside the migration.
            legacyTvPrefsMigration.migrateIfNeeded()

            // Pull effective device settings (cascade user → device → default).
```

(rest of the function unchanged).

**3c. In `/Users/dev/projects/prairie/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/libraries/TvLibrariesViewModel.kt`** — add the import:

```kotlin
import com.continuum.app.tv.data.preferences.LegacyTvPrefsMigration
```

change the constructor from:

```kotlin
class TvLibrariesViewModel(
    private val personalDataRepository: PersonalDataRepository,
    private val librarySelectionStore: TvLibrarySelectionStore,
) : ViewModel() {
```

to:

```kotlin
class TvLibrariesViewModel(
    private val personalDataRepository: PersonalDataRepository,
    private val librarySelectionStore: TvLibrarySelectionStore,
    private val legacyTvPrefsMigration: LegacyTvPrefsMigration,
) : ViewModel() {
```

and change the top of `load()` from:

```kotlin
    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val storedLibraryId = librarySelectionStore.getSelectedLibraryId()
```

to:

```kotlin
    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            // Seed the per-profile selection from the legacy global
            // `tv_prefs` key BEFORE the first read — the resolve below
            // writes a value, which would make the seed unreachable.
            // Sentinel-gated no-op after the first run.
            legacyTvPrefsMigration.migrateIfNeeded()
            val storedLibraryId = librarySelectionStore.getSelectedLibraryId()
```

**3d. In `/Users/dev/projects/prairie/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt`** — add imports:

```kotlin
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.SettingsRepository
import com.continuum.app.tv.data.preferences.LegacyTvPrefsMigration
```

below the existing `single { TvLibrarySelectionStore(androidContext(), get()) }` line, add:

```kotlin
    // One-shot legacy `tv_prefs` import (playback settings → server device
    // overrides; selected-library id → active profile's selection store).
    // Sentinel-gated; invoked from TvSettingsViewModel.loadSettings and
    // TvLibrariesViewModel.load. Lambda-injected lookups follow the
    // AndroidPlayerSettingsStore wiring pattern in PlayerInfraModule.
    single {
        LegacyTvPrefsMigration(
            context = androidContext(),
            settingsCache = get(),
            playerSettingsStore = get(),
            librarySelectionStore = get(),
            getServerUrl = { get<TokenManager>().getServerUrl() },
            getProfileId = { get<TokenManager>().getProfileId() },
            getEffectiveSettings = { keys ->
                when (val result = get<SettingsRepository>().getEffectiveSettings(keys)) {
                    is ApiResult.Success -> result.data
                    is ApiResult.Error, is ApiResult.NetworkError -> emptyMap()
                }
            },
        )
    }
```

change the libraries ViewModel registration from:

```kotlin
    viewModel { TvLibrariesViewModel(get(), get()) }
```

to:

```kotlin
    viewModel { TvLibrariesViewModel(get(), get(), get()) }
```

and the settings ViewModel registration from:

```kotlin
    // Settings.
    viewModel {
        TvSettingsViewModel(
            authRepository = get(),
            profileRepository = get(),
            tokenManager = get(),
            playerSettingsStore = get(),
            libraryPlaybackPrefsStore = get(),
        )
    }
```

to:

```kotlin
    // Settings.
    viewModel {
        TvSettingsViewModel(
            authRepository = get(),
            profileRepository = get(),
            tokenManager = get(),
            playerSettingsStore = get(),
            libraryPlaybackPrefsStore = get(),
            legacyTvPrefsMigration = get(),
        )
    }
```

- [ ] **Step 4: Run tests**

```bash
cd /Users/dev/projects/prairie/prairie-android && ./gradlew :androidTvApp:testDebugUnitTest --tests "com.continuum.app.tv.data.preferences.LegacyTvPrefsMigrationTest" && ./gradlew :androidTvApp:testDebugUnitTest :androidTvApp:assembleDebug
```
Expected: all 8 new tests pass, the rest of the TV suite stays green, and the app module compiles (verifies the DI/ViewModel wiring).

- [ ] **Step 5: Commit**

```bash
cd /Users/dev/projects/prairie/prairie-android && git add androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/data/preferences/LegacyTvPrefsMigration.kt androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/data/preferences/LegacyTvPrefsMigrationTest.kt androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/settings/TvSettingsViewModel.kt androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/libraries/TvLibrariesViewModel.kt androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt androidTvApp/build.gradle.kts && git commit -m "$(cat <<'EOF'
Restore one-shot legacy tv_prefs migration on TV

The branch dropped main's first-boot import of the legacy tv_prefs
DataStore. Reinstate it as a self-contained, sentinel-gated
LegacyTvPrefsMigration: playback settings flow into PlayerSettingsStore
device overrides (skipping keys the server already overrides, same
"android-tv-settings" sentinel as main), and the legacy global
libraries_selected_library_id seeds the active profile's
TvLibrarySelectionStore slot on first read.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

### Task D3: Constrain Palette extraction decode size in AmbientBackdropTint

**Files:**
- Modify: `/Users/dev/projects/prairie/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/components/AmbientBackdropTint.kt`
- Test: none (existing `/Users/dev/projects/prairie/prairie-android/androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/components/AmbientBackdropTintStateTest.kt` continues to cover the state holder)

- [ ] **Step 1: Write the failing test**

Not unit-testable: the change is one builder call inside a Coil `ImageRequest` constructed and executed in a `LaunchedEffect`'s IO block — exercising it requires the Coil image pipeline and a Compose host, neither of which is available in this module's pure-JVM test setup (build.gradle.kts explicitly notes the suite is pure JVM, no Robolectric). Verified API: Coil 3.1.0's `ImageRequest.Builder` has a `size(int)` member (confirmed via `javap` on `coil3.request.ImageRequest$Builder` in `coil-core-android-3.1.0`), so this compiles without new imports.

Manual check: build, install, open Home on a TV device, and D-pad across hero carousel items — the ambient backdrop tint must still change per focused item (accent extraction still works at 128px). Optionally confirm reduced decode cost via `adb logcat -d -s coil3:*` (request size logged at verbose) or GPU/heap profiler before/after when focusing several heroes.

- [ ] **Step 2: Run test to verify it fails**

Not applicable (no new unit test). Baseline instead:
```bash
cd /Users/dev/projects/prairie/prairie-android && ./gradlew :androidTvApp:testDebugUnitTest
```
Expected: current suite green before the change (so any post-change failure is attributable to this edit).

- [ ] **Step 3: Implementation**

In `/Users/dev/projects/prairie/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/components/AmbientBackdropTint.kt`, replace lines 93–98.

Before:

```kotlin
                val result = loader.execute(
                    ImageRequest.Builder(context)
                        .data(url)
                        .allowHardware(false) // Palette requires a software bitmap.
                        .build(),
                )
```

After:

```kotlin
                val result = loader.execute(
                    ImageRequest.Builder(context)
                        .data(url)
                        .allowHardware(false) // Palette requires a software bitmap.
                        // Cap the decode at 128px: this request runs per hero
                        // focus change and allowHardware(false) forces a
                        // software bitmap, so an unconstrained request decodes
                        // the full-res backdrop on every D-pad move. Palette
                        // resizes to a small bitmap before quantizing anyway,
                        // so 128px loses nothing for accent extraction.
                        .size(128)
                        .build(),
                )
```

No import changes — `size(int)` is a member of `coil3.request.ImageRequest.Builder`.

- [ ] **Step 4: Run tests**

```bash
cd /Users/dev/projects/prairie/prairie-android && ./gradlew :androidTvApp:testDebugUnitTest :androidTvApp:assembleDebug
```
Expected: suite green (including `AmbientBackdropTintStateTest`), module compiles. Then run the Step 1 manual check on a device.

- [ ] **Step 5: Commit**

```bash
cd /Users/dev/projects/prairie/prairie-android && git add androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/components/AmbientBackdropTint.kt && git commit -m "$(cat <<'EOF'
Cap ambient backdrop Palette decode at 128px

The accent-extraction ImageRequest used allowHardware(false) with no
size constraint, software-decoding the full-resolution backdrop on
every hero focus change. Palette downsamples before quantizing, so a
128px decode produces the same accent at a fraction of the CPU/memory.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

## Section E: Shared taxonomy, search, models, dead code

### Task E1: Cap auto-advancing pagination in filtered mobile search

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/search/SearchViewModel.kt`
- Test: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/search/SearchFilteredPaginationTest.kt`

- [ ] **Step 1: Write the failing test**

Create `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/search/SearchFilteredPaginationTest.kt` (kotlin.test style, matching `MobileMediaTabsTest.kt`):

```kotlin
package com.continuum.app.android.ui.screens.search

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchFilteredPaginationTest {
    @Test
    fun advancesWhileFilteredPagesAreEmptyAndUnderCap() {
        (1 until MAX_FILTERED_SEARCH_PAGES).forEach { page ->
            assertTrue(
                shouldAutoAdvanceFilteredSearchPage(
                    isClientFiltered = true,
                    visibleItemCount = 0,
                    hasMore = true,
                    pagesFetched = page,
                ),
                "page $page should auto-advance",
            )
        }
    }

    @Test
    fun stopsAtPageCapEvenWhenServerHasMore() {
        assertFalse(
            shouldAutoAdvanceFilteredSearchPage(
                isClientFiltered = true,
                visibleItemCount = 0,
                hasMore = true,
                pagesFetched = MAX_FILTERED_SEARCH_PAGES,
            ),
        )
        assertFalse(
            shouldAutoAdvanceFilteredSearchPage(
                isClientFiltered = true,
                visibleItemCount = 0,
                hasMore = true,
                pagesFetched = MAX_FILTERED_SEARCH_PAGES + 3,
            ),
        )
    }

    @Test
    fun stopsWhenVisibleResultsExist() {
        assertFalse(
            shouldAutoAdvanceFilteredSearchPage(
                isClientFiltered = true,
                visibleItemCount = 1,
                hasMore = true,
                pagesFetched = 1,
            ),
        )
    }

    @Test
    fun stopsWhenServerHasNoMore() {
        assertFalse(
            shouldAutoAdvanceFilteredSearchPage(
                isClientFiltered = true,
                visibleItemCount = 0,
                hasMore = false,
                pagesFetched = 1,
            ),
        )
    }

    @Test
    fun neverAdvancesUnfilteredSearches() {
        assertFalse(
            shouldAutoAdvanceFilteredSearchPage(
                isClientFiltered = false,
                visibleItemCount = 0,
                hasMore = true,
                pagesFetched = 1,
            ),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.search.SearchFilteredPaginationTest"
```
Expected failure: compilation error — `Unresolved reference: MAX_FILTERED_SEARCH_PAGES` / `shouldAutoAdvanceFilteredSearchPage` (they do not exist yet).

- [ ] **Step 3: Implementation**

In `SearchViewModel.kt`, insert the extracted decision (internal so the unit test can see it) directly above `private val MobileSearchMediaType.isClientFiltered` (after `filterResults`, line ~57):

```kotlin
internal const val MAX_FILTERED_SEARCH_PAGES = 5

/**
 * Decides whether a client-filtered search should silently fetch the next
 * page because no visible results have accumulated yet. Capped at
 * [MAX_FILTERED_SEARCH_PAGES] pages per search so a query matching only
 * non-literary items cannot serially crawl the entire catalog; `hasMore`
 * stays true in the UI state, so [SearchViewModel.loadMore] continues from
 * [SearchUiState.nextOffset] on the next scroll-to-end.
 */
internal fun shouldAutoAdvanceFilteredSearchPage(
    isClientFiltered: Boolean,
    visibleItemCount: Int,
    hasMore: Boolean,
    pagesFetched: Int,
): Boolean =
    isClientFiltered &&
        visibleItemCount == 0 &&
        hasMore &&
        pagesFetched < MAX_FILTERED_SEARCH_PAGES
```

In `performSearch`, declare a page counter before the loop. Replace:

```kotlin
        var offset = if (reset) 0 else currentState.nextOffset
```
with:
```kotlin
        var offset = if (reset) 0 else currentState.nextOffset
        var pagesFetched = 0
```

Then replace the start of the `ApiResult.Success` branch (lines 220–234). Old:

```kotlin
                is ApiResult.Success -> {
                    val response = result.data
                    val rawCount = response.items.size
                    val pageVisibleItems = requestedMediaType.filterResults(response.items)

                    visibleItems += pageVisibleItems
                    offset += rawCount
                    hasMore = response.hasMore && rawCount > 0
                    total = response.total

                    val shouldAdvanceFilteredPage = requestedMediaType.isClientFiltered &&
                        pageVisibleItems.isEmpty() &&
                        visibleItems.isEmpty() &&
                        hasMore
                    if (shouldAdvanceFilteredPage) continue
```

New (note: `pageVisibleItems.isEmpty() && visibleItems.isEmpty()` collapses to `visibleItems.isEmpty()` because `visibleItems` only accumulates `pageVisibleItems` and the loop returns on any non-continue path — behavior is identical):

```kotlin
                is ApiResult.Success -> {
                    val response = result.data
                    val rawCount = response.items.size
                    val pageVisibleItems = requestedMediaType.filterResults(response.items)

                    pagesFetched += 1
                    visibleItems += pageVisibleItems
                    offset += rawCount
                    hasMore = response.hasMore && rawCount > 0
                    total = response.total

                    val shouldAdvanceFilteredPage = shouldAutoAdvanceFilteredSearchPage(
                        isClientFiltered = requestedMediaType.isClientFiltered,
                        visibleItemCount = visibleItems.size,
                        hasMore = hasMore,
                        pagesFetched = pagesFetched,
                    )
                    if (shouldAdvanceFilteredPage) continue
```

The rest of the branch (the `_uiState.update` + `return`) is unchanged: when the cap is hit the state is published with `hasMore` still true and `nextOffset = offset`, so `loadMore()` resumes from where the cap stopped.

- [ ] **Step 4: Run tests**

```bash
./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.search.SearchFilteredPaginationTest"
```

- [ ] **Step 5: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/search/SearchViewModel.kt androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/search/SearchFilteredPaginationTest.kt
git commit -m "$(cat <<'EOF'
Cap auto-advancing pages in filtered mobile search

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

### Task E2: Replace hand-rolled literarySearchTypes with shared reading taxonomy

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/search/SearchViewModel.kt`
- Test: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/search/MobileSearchMediaTypeFilterTest.kt`

Background: `literarySearchTypes` (SearchViewModel.kt:41) omits `"reading"`, which the shared taxonomy (`ebookLikeLibraryTypes`, MediaMode.kt:49) includes. The search Reading filter intentionally includes audiobook types, so the semantically matching shared predicate is `isReadingLibraryType` (MediaMode.kt:97 = ebook-like ∪ audiobook-like). It also trims/lowercases, matching the current `it.type.trim().lowercase()`.

- [ ] **Step 1: Write the failing test**

To let the test compile against the currently-`private` helper, this step includes one enabling visibility change in `SearchViewModel.kt` — change line 53 from `private fun MobileSearchMediaType.filterResults(...)` to `internal fun MobileSearchMediaType.filterResults(...)` (no logic change). Then create `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/search/MobileSearchMediaTypeFilterTest.kt`:

```kotlin
package com.continuum.app.android.ui.screens.search

import com.continuum.app.model.catalog.BrowseItem
import kotlin.test.Test
import kotlin.test.assertEquals

class MobileSearchMediaTypeFilterTest {
    @Test
    fun readingFilterMatchesSharedReadingTaxonomy() {
        val items = listOf(
            browseItem("a1", "audiobook"),
            browseItem("a2", "audiobooks"),
            browseItem("e1", "ebook"),
            browseItem("e2", "ebooks"),
            browseItem("b1", "book"),
            browseItem("b2", "books"),
            browseItem("c1", "comic"),
            browseItem("c2", "comics"),
            browseItem("manga1", "manga"),
            browseItem("r1", "reading"),
            browseItem("m1", "movie"),
            browseItem("s1", "series"),
            browseItem("mu1", "music"),
        )

        assertEquals(
            listOf("a1", "a2", "e1", "e2", "b1", "b2", "c1", "c2", "manga1", "r1"),
            MobileSearchMediaType.Reading.filterResults(items).map { it.contentId },
        )
    }

    @Test
    fun nonReadingFiltersPassItemsThrough() {
        val items = listOf(browseItem("m1", "movie"), browseItem("e1", "ebook"))

        assertEquals(items, MobileSearchMediaType.All.filterResults(items))
        assertEquals(items, MobileSearchMediaType.Video.filterResults(items))
        assertEquals(items, MobileSearchMediaType.Audio.filterResults(items))
    }

    private fun browseItem(contentId: String, type: String): BrowseItem =
        BrowseItem(contentId = contentId, type = type, title = contentId)
}
```

(Adapt the `MobileSearchMediaType` case names and `BrowseItem` constructor to what the code actually defines.)

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.search.MobileSearchMediaTypeFilterTest"
```
Expected failure: `readingFilterMatchesSharedReadingTaxonomy` fails with an `assertEquals` diff — actual list is missing `"r1"` (the `"reading"` type that `literarySearchTypes` omits).

- [ ] **Step 3: Implementation**

In `SearchViewModel.kt`:

1. Add the import:
```kotlin
import com.continuum.app.model.navigation.isReadingLibraryType
```
2. Delete the entire `literarySearchTypes` declaration (lines 41–51).
3. Replace `filterResults` with:
```kotlin
internal fun MobileSearchMediaType.filterResults(items: List<BrowseItem>): List<BrowseItem> =
    when (this) {
        MobileSearchMediaType.Reading -> items.filter { isReadingLibraryType(it.type) }
        else -> items
    }
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.search.*" --tests "com.continuum.app.android.ui.navigation.MobileMediaTabsTest"
```

- [ ] **Step 5: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/search/SearchViewModel.kt androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/search/MobileSearchMediaTypeFilterTest.kt
git commit -m "$(cat <<'EOF'
Use shared reading taxonomy for mobile search Reading filter

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

### Task E3: Delegate TV media-type filters to the shared taxonomy

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/util/TvMediaTypeFilters.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/util/TvMediaTypeFiltersTest.kt`

Set diff (verified): `isTvHiddenMediaType`'s set == `ebookLikeLibraryTypes` (MediaMode.kt:49) exactly, including `"reading"`; `isAudiobookMediaType`'s set == `audiobookLikeLibraryTypes` exactly. The ONLY behavior delta after delegating is that the shared predicates also `trim()` input — an intentional, strictly-more-robust normalization. All existing test expectations stay valid.

- [ ] **Step 1: Write the failing test**

Add to `TvMediaTypeFiltersTest.kt` (after `identifiesAudiobookTypesForTv`):

```kotlin
    @Test
    fun normalizesWhitespaceLikeSharedTaxonomy() {
        assertTrue(isTvHiddenMediaType(" ebook "))
        assertTrue(isAudiobookMediaType(" audiobook "))
        assertFalse(isTvHiddenMediaType(null))
        assertFalse(isTvHiddenMediaType("  "))
        assertFalse(isAudiobookMediaType(null))
    }
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests "com.continuum.app.tv.ui.util.TvMediaTypeFiltersTest"
```
Expected failure: `normalizesWhitespaceLikeSharedTaxonomy` — `assertTrue(isTvHiddenMediaType(" ebook "))` fails (current implementation lowercases but never trims).

- [ ] **Step 3: Implementation**

In `TvMediaTypeFilters.kt`, add imports and replace the two predicates (lines 8–21). New top of file:

```kotlin
package com.continuum.app.tv.ui.util

import com.continuum.app.model.catalog.BrowseItem
import com.continuum.app.model.navigation.isAudiobookLikeLibraryType
import com.continuum.app.model.navigation.isEbookLikeLibraryType
import com.continuum.app.model.personal.UserLibrary
import com.continuum.app.model.section.ResolvedSection
import kotlin.jvm.JvmName

/**
 * TV hides ebook-like content (no reader on TV) but keeps audiobooks.
 * Both predicates delegate to the shared reading taxonomy in MediaMode.kt
 * so the type sets cannot drift between platforms; only the TV-specific
 * hide/keep decision lives here.
 */
fun isTvHiddenMediaType(type: String?): Boolean = isEbookLikeLibraryType(type)

fun isAudiobookMediaType(type: String?): Boolean = isAudiobookLikeLibraryType(type)
```

(Keep the file's actual existing import list for the unchanged lower half; everything from `@JvmName("browseItemsVisibleOnTv")` down, including `tvCatalogMediaTypeFor`, is unchanged. If `isAudiobookLikeLibraryType`/`isEbookLikeLibraryType` are not yet public functions in MediaMode.kt, expose them there as thin wrappers over the existing sets — same normalization as `isReadingLibraryType`.)

- [ ] **Step 4: Run tests**

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests "com.continuum.app.tv.ui.util.TvMediaTypeFiltersTest"
```
All existing tests must still pass unmodified (behavior intentionally unchanged apart from trimming).

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/util/TvMediaTypeFilters.kt androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/util/TvMediaTypeFiltersTest.kt shared/src/commonMain/kotlin/com/continuum/app/model/navigation/MediaMode.kt
git commit -m "$(cat <<'EOF'
Delegate TV media-type filters to shared reading taxonomy

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

### Task E4: Shared item-type predicates for detail-screen dispatch

**Files:**
- Create: `shared/src/commonMain/kotlin/com/continuum/app/model/catalog/ItemTypes.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/ItemDetailScreen.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvItemDetailScreen.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvItemDetailViewModel.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvDetailMetadata.kt`
- Test: `shared/src/commonTest/kotlin/com/continuum/app/model/catalog/ItemTypesTest.kt`

Placement decision: the predicates classify `ItemDetail.type` (catalog item types, singular: `audiobook`/`book`/`ebook`/`comic`/`manga`), not library types, so they live in `model/catalog` next to `ItemDetail`, not in `model/navigation/MediaMode.kt` (which owns plural library-type taxonomy). TV sites keep their narrower dispatch (no reader on TV) — they only stop hand-spelling `"audiobook"`. No screen's rendering changes.

- [ ] **Step 1: Write the failing test**

Create `shared/src/commonTest/kotlin/com/continuum/app/model/catalog/ItemTypesTest.kt`:

```kotlin
package com.continuum.app.model.catalog

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ItemTypesTest {
    @Test
    fun identifiesAudiobookItemTypes() {
        assertTrue(isAudiobookItemType("audiobook"))
        assertTrue(isAudiobookItemType("AUDIOBOOK"))
        assertTrue(isAudiobookItemType(" audiobook "))

        assertFalse(isAudiobookItemType("audiobooks"))
        assertFalse(isAudiobookItemType("book"))
        assertFalse(isAudiobookItemType("movie"))
        assertFalse(isAudiobookItemType(""))
        assertFalse(isAudiobookItemType(null))
    }

    @Test
    fun identifiesBookLikeItemTypes() {
        listOf("book", "ebook", "comic", "manga").forEach { type ->
            assertTrue(isBookLikeItemType(type), "$type should be book-like")
            assertTrue(isBookLikeItemType(type.uppercase()), "${type.uppercase()} should be book-like")
        }

        // Item types are singular, unlike the plural library-type taxonomy.
        assertFalse(isBookLikeItemType("books"))
        assertFalse(isBookLikeItemType("ebooks"))
        assertFalse(isBookLikeItemType("audiobook"))
        assertFalse(isBookLikeItemType("movie"))
        assertFalse(isBookLikeItemType(null))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.catalog.ItemTypesTest"
```
Expected failure: compilation error — `Unresolved reference: isAudiobookItemType` / `isBookLikeItemType`.

- [ ] **Step 3: Implementation**

1. Create `shared/src/commonMain/kotlin/com/continuum/app/model/catalog/ItemTypes.kt`:

```kotlin
package com.continuum.app.model.catalog

/**
 * Predicates over [ItemDetail.type] (catalog item types, always singular)
 * so detail screens stop hand-spelling type literals. Library-level
 * taxonomy (plural forms, `reading`, mode mapping) lives in
 * model/navigation/MediaMode.kt.
 */
private val bookLikeItemTypes = setOf(
    "book",
    "ebook",
    "comic",
    "manga",
)

private fun normalizedItemType(type: String?): String? =
    type?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

fun isAudiobookItemType(type: String?): Boolean =
    normalizedItemType(type) == "audiobook"

fun isBookLikeItemType(type: String?): Boolean =
    normalizedItemType(type) in bookLikeItemTypes
```

2. `ItemDetailScreen.kt` — add imports:
```kotlin
import com.continuum.app.model.catalog.isAudiobookItemType
import com.continuum.app.model.catalog.isBookLikeItemType
```
Convert the subject `when` to a subjectless `when` (rendering per branch is untouched):
   - Line 132: `when (detail.type) {` → `when {`
   - Line 133: `"audiobook" -> {` → `isAudiobookItemType(detail.type) -> {`
   - Line 167: `"book", "ebook", "comic", "manga" -> {` → `isBookLikeItemType(detail.type) -> {`
   - Line 206: `"series" -> {` → `detail.type == "series" -> {`
   - The `else -> {` branch (line 246) is unchanged.

3. `TvItemDetailScreen.kt` — add import `import com.continuum.app.model.catalog.isAudiobookItemType` and replace line 147's audiobook check with:
```kotlin
    val isAudiobook = isAudiobookItemType(detail.type)
```

4. `TvItemDetailViewModel.kt` — add import `import com.continuum.app.model.catalog.isAudiobookItemType` and replace line 240:
```kotlin
        val mediaType = detail.type.takeIf { it in setOf("movie", "series", "episode") || isAudiobookItemType(it) }
```

5. `TvDetailMetadata.kt` — add import `import com.continuum.app.model.catalog.isAudiobookItemType`. Replace `sourceTokens` (lines 14–32) with:

```kotlin
    fun sourceTokens(detail: ItemDetail): List<String> = when {
        detail.type.equals("episode", ignoreCase = true) -> buildList {
            episodeNumberLabel(detail)?.let { add(it) }
            detail.genres.firstOrNull { it.isNotBlank() }?.let { add(it) }
        }
        detail.type.equals("series", ignoreCase = true) -> buildList {
            add("TV Show")
            detail.genres.filter { it.isNotBlank() }.take(2).forEach { add(it) }
        }
        isAudiobookItemType(detail.type) -> listOfNotNull(
            "Audiobook",
            detail.audiobook?.publisher,
            detail.audiobook?.narratorNames?.let { "Narrated by $it" },
        )
        else -> buildList {
            add(typeLabel(detail))
            detail.genres.filter { it.isNotBlank() }.take(2).forEach { add(it) }
        }
    }
```

Replace `typeLabel` (lines 78–85) with:

```kotlin
    private fun typeLabel(detail: ItemDetail): String = when {
        isAudiobookItemType(detail.type) -> "Audiobook"
        else -> when (detail.type.lowercase()) {
            "movie" -> "Movie"
            "series" -> "Series"
            "season" -> "Season"
            "episode" -> "Episode"
            else -> detail.type.replaceFirstChar { it.titlecase() }
        }
    }
```

NOTE FOR IMPLEMENTER: diff `sourceTokens`/`typeLabel` against the current implementations and preserve their exact existing token text/ordering — the only change is replacing the `"audiobook"` literal comparisons with `isAudiobookItemType`.

- [ ] **Step 4: Run tests**

```bash
./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.catalog.ItemTypesTest" :androidApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest :androidTvApp:compileDebugKotlinAndroid
```

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/model/catalog/ItemTypes.kt shared/src/commonTest/kotlin/com/continuum/app/model/catalog/ItemTypesTest.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/ItemDetailScreen.kt androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvItemDetailScreen.kt androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvItemDetailViewModel.kt androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvDetailMetadata.kt
git commit -m "$(cat <<'EOF'
Add shared item-type predicates for detail dispatch

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

### Task E5: Remove dead MediaMode functions

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/model/navigation/MediaMode.kt`
- Test: `shared/src/commonTest/kotlin/com/continuum/app/model/navigation/MediaModeTest.kt`

Caller audit (verified via grep across shared/, androidApp/, androidTvApp/):
- `mobileMediaModeForLibraryType` — production caller at MediaMode.kt:125 (`mobileMediaModeCapabilities`). **KEEP.**
- `mediaModeForLibraryType` — callers are only MediaModeTest.kt:11, 146, 153–154. Body is byte-identical to `mobileMediaModeForLibraryType`. **DELETE; redirect its useful test callers.**
- `firstMobileMode()` (MediaMode.kt:26) — zero callers anywhere. **DELETE.**
- `mediaModeCapabilities()` (MediaMode.kt:130) — only caller is MediaModeTest.kt:177. **DELETE with its test.**
- `mobileModes()` — used by MobileMediaTabs.kt:19 and SearchScreen.kt:66/70. **KEEP.**

- [ ] **Step 1: Write the failing test** — not unit-testable (pure deletion of dead code); the verification is (a) the redirected tests still pass and (b) a post-deletion grep proves zero remaining references:
```bash
grep -rn "mediaModeForLibraryType\|firstMobileMode\|mediaModeCapabilities()" shared/src androidApp/src androidTvApp/src --include="*.kt" | grep -v build | grep -v "mobileMediaModeForLibraryType\|tvMediaModeForLibraryType\|mobileMediaModeCapabilities\|tvMediaModeCapabilities"
```
Expected after Step 3: no output.

- [ ] **Step 2: Run test to verify it fails** — n/a (deletion task). Baseline instead: `./gradlew :shared:testDebugUnitTest` must be green before starting.

- [ ] **Step 3: Implementation**

In `MediaMode.kt`:

1. Delete line 26: `fun firstMobileMode(): MediaMode? = mobileModes().firstOrNull()`
2. Delete the whole `mediaModeForLibraryType` function (lines 68–76) plus its three preceding comment lines ("Compatibility mapper for older callers..."); `mobileMediaModeForLibraryType` directly below stays.
3. Delete lines 130–131:
```kotlin
fun Iterable<UserLibrary>.mediaModeCapabilities(): MediaModeCapabilities =
    mobileMediaModeCapabilities()
```

In `MediaModeTest.kt`:

1. Redirect `mapsKnownVideoTypesToVideo` (line 11): `.mapNotNull(::mediaModeForLibraryType)` → `.mapNotNull(::mobileMediaModeForLibraryType)`
2. Redirect `ignoresBlankAndUnknownTypes` (line 146): same substitution.
3. Delete the now-dead test `legacyMapperUsesMobileAudiobookBehavior` (lines 151–155) — its coverage already exists in `mobileMapsAudiobooksToAudio`.
4. Delete the now-dead test `compatibilityAliasUsesMobileCapabilities` (lines 171–179).

Then run the Step 1 grep — expect no output.

- [ ] **Step 4: Run tests**

```bash
./gradlew :shared:testDebugUnitTest :androidApp:compileDebugKotlinAndroid :androidTvApp:compileDebugKotlinAndroid
```

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/model/navigation/MediaMode.kt shared/src/commonTest/kotlin/com/continuum/app/model/navigation/MediaModeTest.kt
git commit -m "$(cat <<'EOF'
Remove dead MediaMode compatibility functions

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

### Task E6: Make MediaRelatedItem decode-tolerant of incomplete entries

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/model/ebook/EbookMetadata.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/book/BookDetailContent.kt`
- Test: `shared/src/commonTest/kotlin/com/continuum/app/model/ebook/EbookMetadataSerializationTest.kt`

Consumer audit: `MediaRelatedItem` is consumed by (a) `BookDetailContent.kt:240` — renders `alsoByAuthor` in a LazyColumn with `key = { it.contentId }`, where a blank/duplicate key crashes Compose at runtime, so this site gets the blank-contentId filter; (b) `AudiobookDetailContent.kt:346` `joinToRelatedTitles()` — already drops blank titles, no change needed; (c) `MediaSeriesGroup.entries` rendering — already guarded by `hasDisplayableContent()`, no change needed. `AudiobookMetadata` reuses these same types, so the fix covers audiobook `related`/`series` too.

- [ ] **Step 1: Write the failing test**

Create `shared/src/commonTest/kotlin/com/continuum/app/model/ebook/EbookMetadataSerializationTest.kt`:

```kotlin
package com.continuum.app.model.ebook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

class EbookMetadataSerializationTest {
    // Mirrors ContinuumJson (ContinuumHttpClientImpl.kt). Note that
    // coerceInputValues only substitutes defaults for null/invalid input
    // when a default exists — it cannot repair missing non-nullable
    // Strings, which is why MediaRelatedItem needs explicit defaults.
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun decodesRelatedContentWithIncompleteEntries() {
        val related = json.decodeFromString<MediaRelatedContent>(
            """
            {
              "also_by_author": [
                { "content_id": "b-1", "title": "Complete Entry", "year": 2024 },
                { "year": 2023 }
              ],
              "similar": [
                { "content_id": null, "title": null }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(2, related.alsoByAuthor.size)
        assertEquals("b-1", related.alsoByAuthor[0].contentId)
        assertEquals("", related.alsoByAuthor[1].contentId)
        assertEquals("", related.alsoByAuthor[1].title)
        assertEquals(2023, related.alsoByAuthor[1].year)
        assertEquals("", related.similar.single().contentId)
        assertEquals("", related.similar.single().title)
    }

    @Test
    fun decodesSeriesGroupWithIncompleteEntry() {
        val series = json.decodeFromString<MediaSeriesGroup>(
            """
            { "name": "Prairie Stories", "entries": [ { "series_index": 2.0 } ] }
            """.trimIndent(),
        )

        assertEquals("Prairie Stories", series.name)
        assertEquals("", series.entries.single().contentId)
        assertEquals(2.0, series.entries.single().seriesIndex)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.ebook.EbookMetadataSerializationTest"
```
Expected failure: both tests throw `kotlinx.serialization.MissingFieldException` (fields `content_id`/`title` are required and missing/null) instead of decoding.

- [ ] **Step 3: Implementation**

1. In `EbookMetadata.kt`, replace `MediaRelatedItem` (lines 14–21) with:

```kotlin
@Serializable
data class MediaRelatedItem(
    @SerialName("content_id") val contentId: String = "",
    val title: String = "",
    val year: Int? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("series_index") val seriesIndex: Double? = null,
)
```

(Preserve any additional fields the current declaration has — the change is adding `= ""` defaults to `contentId` and `title`.)

2. In `BookDetailContent.kt`, guard the LazyColumn key against blank/duplicate contentIds — replace line 240:

```kotlin
        ebook?.related?.alsoByAuthor?.takeIf { it.isNotEmpty() }?.let { related ->
```
with:
```kotlin
        ebook?.related?.alsoByAuthor
            ?.filter { it.contentId.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?.let { related ->
```
(The body of the `let` — header `Text` plus `items(related, key = { it.contentId })` — is unchanged.)

- [ ] **Step 4: Run tests**

```bash
./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.ebook.*" --tests "com.continuum.app.model.catalog.MediaSurfaceContractSerializationTest" :androidApp:compileDebugKotlinAndroid
```

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/model/ebook/EbookMetadata.kt shared/src/commonTest/kotlin/com/continuum/app/model/ebook/EbookMetadataSerializationTest.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/book/BookDetailContent.kt
git commit -m "$(cat <<'EOF'
Tolerate incomplete related-item entries in detail decode

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

### Task E7: Delete the orphaned admin chain

**Files:**
- Delete: `shared/src/commonMain/kotlin/com/continuum/app/viewmodel/AdminViewModel.kt`
- Delete: `shared/src/commonMain/kotlin/com/continuum/app/repository/AdminRepository.kt`
- Delete: `shared/src/commonMain/kotlin/com/continuum/app/network/api/AdminApi.kt`
- Delete: `shared/src/commonMain/kotlin/com/continuum/app/model/admin/AdminModels.kt` (sole file in model/admin/)
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt`
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/di/NetworkModule.kt`
- Test: full module suites (no admin tests exist — verified: `find shared/src -iname "*admin*"` matches only the four production files; grep for `Admin` in androidApp/src and androidTvApp/src returns nothing)

- [ ] **Step 1: Write the failing test** — not unit-testable (pure deletion); the verification is the post-deletion reference grep in Step 3 plus full compile+test of all modules in Step 4.

- [ ] **Step 2: Run test to verify it fails** — n/a. Baseline: `./gradlew :shared:testDebugUnitTest` green before starting.

- [ ] **Step 3: Implementation**

1. Delete the files:

```bash
git rm shared/src/commonMain/kotlin/com/continuum/app/viewmodel/AdminViewModel.kt \
       shared/src/commonMain/kotlin/com/continuum/app/repository/AdminRepository.kt \
       shared/src/commonMain/kotlin/com/continuum/app/network/api/AdminApi.kt \
       shared/src/commonMain/kotlin/com/continuum/app/model/admin/AdminModels.kt
```

2. `RepositoryModule.kt` — remove the import `import com.continuum.app.repository.AdminRepository` (line 6) and the registration line `single { AdminRepository(get()) }` (line 48, between `RequestsRepository` and `SettingsRepository`).

3. `NetworkModule.kt` — remove the registration line `single { AdminApi(get()) }` (line 22, between `DefaultRequestsApi` and `HealthApi`; the `com.continuum.app.network.api.*` wildcard import stays).

4. Prove zero remaining references:
```bash
grep -rn "Admin" shared/src androidApp/src androidTvApp/src --include="*.kt" | grep -v "/build/"
```
Expected: no output.

- [ ] **Step 4: Run tests** (full suites — deletion task)

```bash
./gradlew :shared:testDebugUnitTest :android-shared:testDebugUnitTest :androidApp:testDebugUnitTest :androidApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest :androidTvApp:compileDebugKotlinAndroid
```

- [ ] **Step 5: Commit**

```bash
git add -A shared/src/commonMain/kotlin/com/continuum/app/
git commit -m "$(cat <<'EOF'
Delete orphaned admin viewmodel, repository, API, and models

The admin screens were removed on this branch; the shared chain
(AdminViewModel -> AdminRepository -> AdminApi -> model/admin) had no
remaining callers outside its own DI registrations.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
```

## Section F: Requests policy and cross-cutting cleanup

### Task F1: Shared request presentation policy (mobile + TV)

**Files:**
- Create: /Users/dev/projects/prairie/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/model/request/RequestPresentation.kt
- Test: /Users/dev/projects/prairie/prairie-android/shared/src/commonTest/kotlin/com/continuum/app/model/request/RequestPresentationTest.kt
- Modify: /Users/dev/projects/prairie/prairie-android/androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/requests/RequestComponents.kt
- Modify: /Users/dev/projects/prairie/prairie-android/androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/requests/RequestDetailScreen.kt
- Modify: /Users/dev/projects/prairie/prairie-android/androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/requests/MyRequestsScreen.kt
- Modify: /Users/dev/projects/prairie/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/requests/TvRequestComponents.kt
- Modify: /Users/dev/projects/prairie/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/requests/TvMyRequestsScreen.kt

Background (verified): `requestImageUrl`/`requestPosterUrl` and the cancel predicate are byte-identical in `RequestComponents.kt` (237–248, 270–271) and `TvRequestComponents.kt` (294–306). Badge precedence is duplicated with drift: mobile `badgeText()` (262–268) returns prettified text directly; TV `cardChipText()` (308–314) returns raw lowercase tokens that `TvRequestStatusChip` later prettifies via `requestLabel()` (321–331). `targetSummary` drift: mobile truncates with `"…"` (275), TV with `"..."` (335). The unification below keeps TV's rendered output identical (its chip still receives raw tokens and prettifies them), and unifies truncation on `"…"`.

`TvRequestPresentationTest.kt` only tests the `filterTv*` helpers in `TvRequestPresentation.kt` — none of the moved functions — so it needs **no changes**.

- [ ] **Step 1: Write the failing test**

Create `shared/src/commonTest/kotlin/com/continuum/app/model/request/RequestPresentationTest.kt`:

```kotlin
package com.continuum.app.model.request

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestPresentationTest {

    @Test
    fun `poster url builds tmdb w500 path for relative paths`() {
        assertEquals("https://image.tmdb.org/t/p/w500/poster.jpg", requestPosterUrl("/poster.jpg"))
    }

    @Test
    fun `backdrop url builds tmdb w780 path for relative paths`() {
        assertEquals("https://image.tmdb.org/t/p/w780/backdrop.jpg", requestBackdropUrl("/backdrop.jpg"))
    }

    @Test
    fun `image urls pass through absolute urls and reject blanks`() {
        assertEquals("https://example.com/p.jpg", requestPosterUrl("https://example.com/p.jpg"))
        assertEquals("http://example.com/p.jpg", requestPosterUrl("http://example.com/p.jpg"))
        assertEquals("relative.jpg", requestPosterUrl("relative.jpg"))
        assertNull(requestPosterUrl(null))
        assertNull(requestPosterUrl("   "))
    }

    @Test
    fun `badge status prefers availability then status then requestable then reason`() {
        assertEquals(RequestAvailability.Available, result(availability = RequestAvailability.Available).badgeStatus())
        assertEquals(RequestStatus.Pending, result(requestStatus = RequestStatus.Pending).badgeStatus())
        assertEquals("request", result(requestable = true).badgeStatus())
        assertEquals("not allowed", result(reason = "not allowed").badgeStatus())
        assertEquals(RequestAvailability.Missing, result().badgeStatus())
    }

    @Test
    fun `display label maps known tokens and title-cases the rest`() {
        assertEquals("In Library", RequestAvailability.Available.requestDisplayLabel())
        assertEquals("Missing", RequestAvailability.Missing.requestDisplayLabel())
        assertEquals("Request", "request".requestDisplayLabel())
        assertEquals("Movie", RequestMediaType.Movie.requestDisplayLabel())
        assertEquals("Series", RequestMediaType.Series.requestDisplayLabel())
        assertEquals("All", RequestMediaType.All.requestDisplayLabel())
        assertEquals("Pending", RequestStatus.Pending.requestDisplayLabel())
        assertEquals("Partially Available", "partially_available".requestDisplayLabel())
    }

    @Test
    fun `can cancel only while active and pending`() {
        assertTrue(request(status = RequestStatus.Pending, outcome = RequestOutcome.Active).canCancel())
        assertFalse(request(status = RequestStatus.Downloading, outcome = RequestOutcome.Active).canCancel())
        assertFalse(request(status = RequestStatus.Pending, outcome = RequestOutcome.Cancelled).canCancel())
    }

    @Test
    fun `target summary joins fields and truncates with ellipsis after two targets`() {
        assertNull(request().targetSummary())

        val summary = request(
            targets = listOf(
                target(id = 1, instanceName = "Radarr 4K", quality = "2160p", status = "queued"),
                target(id = 2, instanceName = "Radarr", quality = "1080p", status = "queued"),
                target(id = 3, instanceName = "Backup", quality = "720p", status = "queued"),
            ),
        ).targetSummary()

        assertEquals("Radarr 4K • 2160p • queued, Radarr • 1080p • queued, …", summary)
    }

    private fun result(
        availability: String = RequestAvailability.Missing,
        requestStatus: String? = null,
        requestable: Boolean = false,
        reason: String = "",
    ): RequestMediaResult = RequestMediaResult(
        mediaType = RequestMediaType.Movie,
        tmdbId = 1,
        title = "Stub",
        availability = availability,
        request = RequestState(status = requestStatus, requestable = requestable, reason = reason),
    )

    private fun request(
        status: String = RequestStatus.Pending,
        outcome: String = RequestOutcome.Active,
        targets: List<RequestTarget> = emptyList(),
    ): MediaRequest = MediaRequest(
        id = "request-1",
        mediaType = RequestMediaType.Movie,
        tmdbId = 1,
        title = "Stub",
        status = status,
        outcome = outcome,
        targets = targets,
        createdAt = "2026-06-12T00:00:00Z",
        updatedAt = "2026-06-12T00:00:00Z",
    )

    private fun target(
        id: Long,
        instanceName: String,
        quality: String,
        status: String,
    ): RequestTarget = RequestTarget(
        id = id,
        requestId = "request-1",
        instanceName = instanceName,
        quality = quality,
        status = status,
        createdAt = "2026-06-12T00:00:00Z",
        updatedAt = "2026-06-12T00:00:00Z",
    )
}
```

- [ ] **Step 2: Run test to verify it fails**

```
./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.request.RequestPresentationTest"
```
Expected: compilation failure — `unresolved reference: requestPosterUrl` / `badgeStatus` / `requestDisplayLabel` / `canCancel` / `targetSummary` (the file doesn't exist yet).

- [ ] **Step 3: Implementation**

**3a.** Create `shared/src/commonMain/kotlin/com/continuum/app/model/request/RequestPresentation.kt`:

```kotlin
package com.continuum.app.model.request

/**
 * Presentation policy shared by the mobile and TV request surfaces:
 * TMDB image URL building, badge/chip status precedence, status
 * labelling, cancellability, and the per-target summary line.
 */

fun requestPosterUrl(path: String?): String? = requestImageUrl(path, "w500")

fun requestBackdropUrl(path: String?): String? = requestImageUrl(path, "w780")

private fun requestImageUrl(path: String?, size: String): String? {
    val value = path?.takeIf { it.isNotBlank() } ?: return null
    return when {
        value.startsWith("http://") || value.startsWith("https://") -> value
        value.startsWith("/") -> "https://image.tmdb.org/t/p/$size$value"
        else -> value
    }
}

/**
 * Raw status token for a discover/search card badge, in precedence
 * order: in-library beats request status beats requestability beats
 * the server-provided reason. Render with [requestDisplayLabel].
 */
fun RequestMediaResult.badgeStatus(): String = when {
    availability == RequestAvailability.Available -> RequestAvailability.Available
    request.status?.isNotBlank() == true -> request.status.orEmpty()
    request.requestable -> "request"
    request.reason.isNotBlank() -> request.reason
    else -> RequestAvailability.Missing
}

/** Human label for a request status/outcome/availability/media-type token. */
fun String.requestDisplayLabel(): String = when (lowercase()) {
    RequestMediaType.Movie -> "Movie"
    RequestMediaType.Series -> "Series"
    RequestMediaType.All -> "All"
    RequestAvailability.Available -> "In Library"
    RequestAvailability.Missing -> "Missing"
    "request" -> "Request"
    else -> split('_', '-', ' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { token -> token.replaceFirstChar { it.uppercase() } }
        .ifBlank { this }
}

fun MediaRequest.canCancel(): Boolean =
    outcome == RequestOutcome.Active && status == RequestStatus.Pending

fun MediaRequest.targetSummary(): String? {
    if (targets.isEmpty()) return null
    return targets.joinToString(limit = 2, truncated = "…") { target ->
        listOf(target.instanceName, target.quality, target.status, target.externalStatus, target.lastError)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
    }
}
```

**3b.** `androidApp/.../requests/RequestComponents.kt` — delete the local helpers `requestPosterUrl` (237), `requestBackdropUrl` (239), `requestImageUrl` (241–248), `String.label()` (250–255), `RequestMediaResult.badgeText()` (262–268), `MediaRequest.canCancel()` (270–271), `MediaRequest.targetSummary()` (273–280). Keep the private `String.icon()`. Import changes:

```kotlin
// remove (now unused):
import com.continuum.app.model.request.RequestAvailability
import com.continuum.app.model.request.RequestOutcome
import com.continuum.app.model.request.RequestStatus
// add:
import com.continuum.app.model.request.badgeStatus
import com.continuum.app.model.request.requestDisplayLabel
import com.continuum.app.model.request.requestPosterUrl
import com.continuum.app.model.request.targetSummary
```

Call-site edits in the same file:

```kotlin
// RequestMediaCard, line 70:
                text = item.badgeText(),
// becomes:
                text = item.badgeStatus().requestDisplayLabel(),

// RequestListItem badges, lines 142-147:
                    request.status.takeIf { it.isNotBlank() }?.let {
                        RequestBadge(text = it.replaceFirstChar { c -> c.uppercase() })
                    }
                    request.outcome.takeIf { it.isNotBlank() }?.let {
                        RequestBadge(text = it.replaceFirstChar { c -> c.uppercase() })
                    }
// becomes:
                    request.status.takeIf { it.isNotBlank() }?.let {
                        RequestBadge(text = it.requestDisplayLabel())
                    }
                    request.outcome.takeIf { it.isNotBlank() }?.let {
                        RequestBadge(text = it.requestDisplayLabel())
                    }

// RequestMetaLine, line 211:
            text = listOfNotNull(mediaType.label(), year?.toString()).joinToString(" • "),
// becomes:
            text = listOfNotNull(mediaType.requestDisplayLabel(), year?.toString()).joinToString(" • "),
```

**3c.** `androidApp/.../requests/RequestDetailScreen.kt` — add imports (the functions were previously same-package):

```kotlin
import com.continuum.app.model.request.requestBackdropUrl
import com.continuum.app.model.request.requestDisplayLabel
import com.continuum.app.model.request.requestPosterUrl
```

and replace the two `label()` calls:

```kotlin
// line 313:
        ?: detail.availability.label()
// becomes:
        ?: detail.availability.requestDisplayLabel()

// line 330:
    return facts.joinToString(" • ").ifBlank { availability.label() }
// becomes:
    return facts.joinToString(" • ").ifBlank { availability.requestDisplayLabel() }
```

**3d.** `androidApp/.../requests/MyRequestsScreen.kt` — add `import com.continuum.app.model.request.canCancel` (call site at line 92 is unchanged).

**3e.** `androidTvApp/.../requests/TvRequestComponents.kt` — delete `canCancelOnTv` (294–295), `requestPosterUrl` (297), `requestImageUrl` (299–306), `cardChipText` (308–314), `requestLabel` (321–331), `targetSummary` (333–338), `summaryText` (340–343). Keep `canOpenLibraryDetail`, `canRequest`, and the private `String.icon()`. Import changes:

```kotlin
// remove (now unused):
import com.continuum.app.model.request.RequestTarget
// add:
import com.continuum.app.model.request.badgeStatus
import com.continuum.app.model.request.requestDisplayLabel
import com.continuum.app.model.request.requestPosterUrl
import com.continuum.app.model.request.targetSummary
```

Call-site edits:

```kotlin
// TvRequestCard, line 85:
                    status = result.cardChipText(),
// becomes:
                    status = result.badgeStatus(),

// TvRequestStatusChip, line 213:
            text = normalized.requestLabel(),
// becomes:
            text = normalized.requestDisplayLabel(),

// RequestMetaLine, line 279:
            text = listOfNotNull(mediaType.requestLabel(), year?.toString()).joinToString(" • "),
// becomes:
            text = listOfNotNull(mediaType.requestDisplayLabel(), year?.toString()).joinToString(" • "),
```

**3f.** `androidTvApp/.../requests/TvMyRequestsScreen.kt` — line 109: `request.canCancelOnTv()` → `request.canCancel()`; add `import com.continuum.app.model.request.canCancel`.

Intentional, noted visual deltas (all minor):
- TV `targetSummary` truncation changes `"..."` → `"…"` (matches mobile).
- Mobile card badge for a non-requestable reason string is now title-cased per token (was raw); multi-token statuses like `partially_available` now render "Partially Available" on mobile (was "Partially_available").
- Mobile `RequestDetailScreen` availability fallback now renders "In Library" instead of "Available", matching the badge vocabulary used everywhere else.
- TV rendering is unchanged: `badgeStatus()` returns the exact tokens `cardChipText()` did, and `requestDisplayLabel()` is `requestLabel()` plus an `All` case that can never reach the chip.

- [ ] **Step 4: Run tests**

```
./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.request.RequestPresentationTest" :androidApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest :androidTvApp:compileDebugKotlinAndroid
```

- [ ] **Step 5: Commit**

```
git add -A && git commit -m "Share request presentation policy across mobile and TV

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task F2: ApiResult.errorMessage — collapse 14 duplicated error branches in RequestsViewModels

**Files:**
- Modify: /Users/dev/projects/prairie/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/network/ApiResult.kt
- Modify: /Users/dev/projects/prairie/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/viewmodel/RequestsViewModels.kt
- Test: /Users/dev/projects/prairie/prairie-android/shared/src/commonTest/kotlin/com/continuum/app/network/ApiResultErrorMessageTest.kt
- Test (extend): /Users/dev/projects/prairie/prairie-android/shared/src/commonTest/kotlin/com/continuum/app/viewmodel/RequestsViewModelTest.kt

- [ ] **Step 1: Write the failing test**

Create `shared/src/commonTest/kotlin/com/continuum/app/network/ApiResultErrorMessageTest.kt`:

```kotlin
package com.continuum.app.network

import kotlin.test.Test
import kotlin.test.assertEquals

class ApiResultErrorMessageTest {

    @Test
    fun `error uses server message when present`() {
        assertEquals(
            "boom",
            ApiResult.Error(code = 500, error = "internal", message = "boom").errorMessage("fallback"),
        )
    }

    @Test
    fun `error falls back when server message is blank`() {
        assertEquals(
            "fallback",
            ApiResult.Error(code = 500, error = "internal", message = "  ").errorMessage("fallback"),
        )
    }

    @Test
    fun `network error always uses the standard copy`() {
        assertEquals(
            "Network error. Check your connection.",
            ApiResult.NetworkError(IllegalStateException("offline")).errorMessage("fallback"),
        )
    }
}
```

Also add two view-model tests inside the existing `RequestsViewModelTest` class (the `FakeRequestsApi` in that file already supports these):

```kotlin
    @Test
    fun `requests view model falls back to default message for blank discover errors`() = runTest(dispatcher) {
        val api = FakeRequestsApi(
            statusResult = ApiResult.Success(RequestsFeatureStatus(requestsEnabled = true)),
            discoverResult = ApiResult.Error(code = 500, error = "internal", message = ""),
        )

        val viewModel = RequestsViewModel(RequestsRepository(api))

        assertEquals("Failed to load requests", viewModel.uiState.value.error)
    }

    @Test
    fun `requests view model shows network copy when status request cannot reach the server`() = runTest(dispatcher) {
        val api = FakeRequestsApi(statusResult = ApiResult.NetworkError(IllegalStateException("offline")))

        val viewModel = RequestsViewModel(RequestsRepository(api))

        assertEquals("Network error. Check your connection.", viewModel.uiState.value.error)
    }
```

- [ ] **Step 2: Run test to verify it fails**

```
./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.network.ApiResultErrorMessageTest"
```
Expected: compilation failure — `unresolved reference: errorMessage`. (The two view-model tests pass already; they pin behavior across the refactor.)

- [ ] **Step 3: Implementation**

**3a.** Append to `shared/src/commonMain/kotlin/com/continuum/app/network/ApiResult.kt`:

```kotlin
/** Standard copy for failures that never reached the server. */
const val NETWORK_ERROR_MESSAGE = "Network error. Check your connection."

/**
 * User-facing error text for a failed [ApiResult]: the server-provided
 * message when present, [fallback] when it is blank, and the standard
 * network-error copy for [ApiResult.NetworkError]. Total over the sealed
 * type ([fallback] for Success) so it can be called on the when-subject
 * inside a merged `is Error, is NetworkError ->` branch.
 */
fun ApiResult<*>.errorMessage(fallback: String): String = when (this) {
    is ApiResult.Success -> fallback
    is ApiResult.Error -> message.ifBlank { fallback }
    is ApiResult.NetworkError -> NETWORK_ERROR_MESSAGE
}
```

**3b.** `RequestsViewModels.kt` — merge each `is ApiResult.Error` / `is ApiResult.NetworkError` pair into a single branch (the copied fields are identical within every pair except the one noted below). Add `import com.continuum.app.network.errorMessage`. Full new code for the first view model's two functions:

```kotlin
    private suspend fun fetchRequestsHome() {
        when (val status = repository.status()) {
            is ApiResult.Success -> {
                if (!status.data.requestsEnabled) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isEnabled = false,
                            sections = emptyList(),
                            error = "Requests are not enabled on this server.",
                        )
                    }
                    return
                }
                fetchDiscover()
            }
            is ApiResult.Error, is ApiResult.NetworkError -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isEnabled = false,
                        error = status.errorMessage("Failed to load request status"),
                    )
                }
            }
        }
    }

    private suspend fun fetchDiscover() {
        when (val discover = repository.discover()) {
            is ApiResult.Success -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isEnabled = true,
                        sections = discover.data.sections,
                        error = null,
                    )
                }
            }
            is ApiResult.Error, is ApiResult.NetworkError -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isEnabled = true,
                        error = discover.errorMessage("Failed to load requests"),
                    )
                }
            }
        }
    }
```

Representative rewrite in `RequestSearchViewModel.search` (replaces lines 215–232):

```kotlin
                is ApiResult.Error, is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            submittedQuery = query,
                            error = result.errorMessage("Failed to search requests"),
                        )
                    }
                }
```

`RequestDetailViewModel.load` (replaces lines 277–293) — note the one behavior unification: the merged branch clears `detail` on NetworkError too (previously only the Error branch nulled it; a failed retry no longer shows stale detail beside the error):

```kotlin
                is ApiResult.Error, is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            detail = null,
                            error = result.errorMessage("Failed to load request details"),
                        )
                    }
                }
```

The remaining three pairs follow the identical mechanical pattern (merge the two branches, keep the pair's shared `copy` fields, replace the message expression with `result.errorMessage(<existing fallback>)`):
- `RequestDetailViewModel.submitRequest`, lines 328–343 — fields `isSubmitting = false`; fallback `"Failed to submit request"`.
- `MyRequestsViewModel.cancel`, lines 408–423 — fields `actionInFlightId = null`; fallback `"Failed to cancel request"`.
- `MyRequestsViewModel.refreshMine`, lines 439–454 — fields `isLoading = false`; fallback `"Failed to load your requests"`.

- [ ] **Step 4: Run tests**

```
./gradlew :shared:testDebugUnitTest
```

- [ ] **Step 5: Commit**

```
git add -A && git commit -m "Add ApiResult.errorMessage and dedupe request VM error branches

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task F3: One formatBytes for androidApp (5 duplicates)

**Files:**
- Create: /Users/dev/projects/prairie/prairie-android/androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/util/Formatters.kt
- Test: /Users/dev/projects/prairie/prairie-android/androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/util/FormattersTest.kt
- Modify: /Users/dev/projects/prairie/prairie-android/androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadEntryRows.kt
- Modify: /Users/dev/projects/prairie/prairie-android/androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadsScreen.kt
- Modify: /Users/dev/projects/prairie/prairie-android/androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadItemRow.kt
- Modify: /Users/dev/projects/prairie/prairie-android/androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/QualitySelector.kt
- Modify: /Users/dev/projects/prairie/prairie-android/androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/MediaInfoSheet.kt

Verified inventory (corrects one earlier note): the log10/1024 `"%.1f %s"` variant is byte-identical in `DownloadEntryRows.kt:467`, `DownloadsScreen.kt:387`, `DownloadItemRow.kt:105`. **Both** `QualitySelector.kt:141` and `MediaInfoSheet.kt:182` are when-ladder variants (no TB; `"%.1f GB"/"%.0f MB"/"%.0f KB"` and `"%.2f GB"/"%.1f MB"/"%.0f KB"` respectively) — their output changes slightly to the unified `"%.1f <unit>"` format (e.g. "250 MB" → "250.0 MB", "1.25 GB" → "1.2 GB"); acceptable.

The existing `ui/util` package (`DominantColor.kt`) is the conventional spot.

- [ ] **Step 1: Write the failing test**

Create `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/util/FormattersTest.kt` (camelCase test names matching `DownloadStatusLabelTest.kt` in this module):

```kotlin
package com.continuum.app.android.ui.util

import kotlin.test.Test
import kotlin.test.assertEquals

class FormattersTest {

    @Test
    fun zeroAndNegativeBytesShowZero() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("0 B", formatBytes(-42))
    }

    @Test
    fun bytesBelowOneKilobyteStayInBytes() {
        assertEquals("500.0 B", formatBytes(500))
    }

    @Test
    fun kilobytesMegabytesGigabytesUseOneDecimal() {
        assertEquals("1.0 KB", formatBytes(1_024))
        assertEquals("1.5 KB", formatBytes(1_536))
        assertEquals("250.0 MB", formatBytes(262_144_000))
        assertEquals("1.0 GB", formatBytes(1_073_741_824))
    }

    @Test
    fun terabytesAreTheLargestUnit() {
        assertEquals("1.0 TB", formatBytes(1_099_511_627_776))
        assertEquals("1024.0 TB", formatBytes(1_125_899_906_842_624))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.util.FormattersTest"
```
Expected: compilation failure — `unresolved reference: formatBytes`.

- [ ] **Step 3: Implementation**

**3a.** Create `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/util/Formatters.kt`:

```kotlin
package com.continuum.app.android.ui.util

/**
 * Shared user-facing formatters. Single source of truth so the
 * downloads, player, and detail surfaces can't drift apart.
 */

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val index = digitGroups.coerceAtMost(units.size - 1)
    val value = bytes / Math.pow(1024.0, index.toDouble())
    return String.format("%.1f %s", value, units[index])
}
```

**3b.** `DownloadEntryRows.kt` — delete the private `formatBytes` (lines 467–474); add `import com.continuum.app.android.ui.util.formatBytes`. Call sites (157, 229, 432) compile unchanged.

**3c.** `DownloadsScreen.kt` — delete the private `formatBytes` (lines 387–394); add `import com.continuum.app.android.ui.util.formatBytes`. Call sites (204, 247) unchanged.

**3d.** `DownloadItemRow.kt` — delete the private `formatFileSize` (lines 105–112); add `import com.continuum.app.android.ui.util.formatBytes`; change line 81:

```kotlin
                text = formatFileSize(item.fileSizeBytes),
// becomes:
                text = formatBytes(item.fileSizeBytes),
```

**3e.** `QualitySelector.kt` — delete the private `formatFileSize` (lines 141–148); add `import com.continuum.app.android.ui.util.formatBytes`; change line 75:

```kotlin
                            append(formatFileSize(version.fileSize))
// becomes:
                            append(formatBytes(version.fileSize))
```

**3f.** `MediaInfoSheet.kt` — delete the private `formatFileSize` (lines 182–189); add `import com.continuum.app.android.ui.util.formatBytes`; change line 88:

```kotlin
                    InfoRow("File Size", formatFileSize(version.fileSize))
// becomes:
                    InfoRow("File Size", formatBytes(version.fileSize))
```

(Keep `formatBitrate` and `formatDuration` in `MediaInfoSheet.kt` — out of scope.)

- [ ] **Step 4: Run tests**

```
./gradlew :androidApp:testDebugUnitTest :androidApp:compileDebugKotlinAndroid
```

- [ ] **Step 5: Commit**

```
git add -A && git commit -m "Unify byte-size formatting in one androidApp formatter

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task F4: One formatClockTime for androidApp (4 drifted clones)

**Files:**
- Modify: /Users/dev/projects/prairie/prairie-android/androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/util/Formatters.kt
- Test: /Users/dev/projects/prairie/prairie-android/androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/util/FormattersTest.kt
- Modify: /Users/dev/projects/prairie/prairie-android/androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerProgressBar.kt
- Modify: /Users/dev/projects/prairie/prairie-android/androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/ChaptersSheet.kt
- Modify: /Users/dev/projects/prairie/prairie-android/androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerScreen.kt
- Modify: /Users/dev/projects/prairie/prairie-android/androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookBookmarksSheet.kt

Drift being unified: `PlayerProgressBar.formatTime` rounds (`roundToInt`), the other three truncate; only `ChaptersSheet.formatChapterTime` guards NaN. Unified semantics: truncation (`toLong`) + NaN/negative guard. The only visible change is the video player's clock, which may read 1 s lower at half-second boundaries; chapter/audiobook output is bit-identical.

- [ ] **Step 1: Write the failing test**

Append to `FormattersTest`:

```kotlin
    @Test
    fun clockTimeFormatsMinutesAndSeconds() {
        assertEquals("0:00", formatClockTime(0.0))
        assertEquals("0:59", formatClockTime(59.9)) // truncates, never rounds up
        assertEquals("1:05", formatClockTime(65.0))
    }

    @Test
    fun clockTimeFormatsHours() {
        assertEquals("1:00:00", formatClockTime(3600.0))
        assertEquals("2:03:04", formatClockTime(7384.5))
    }

    @Test
    fun clockTimeGuardsNanAndNegatives() {
        assertEquals("0:00", formatClockTime(Double.NaN))
        assertEquals("0:00", formatClockTime(-12.0))
    }
```

- [ ] **Step 2: Run test to verify it fails**

```
./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.util.FormattersTest"
```
Expected: compilation failure — `unresolved reference: formatClockTime`.

- [ ] **Step 3: Implementation**

**3a.** Append to `Formatters.kt`:

```kotlin
/**
 * Formats a duration in seconds as H:MM:SS, or M:SS under an hour.
 * Truncates sub-second values; NaN and negatives render as 0:00.
 */
internal fun formatClockTime(seconds: Double): String {
    val total = if (seconds.isNaN()) 0L else seconds.toLong().coerceAtLeast(0L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
```

**3b.** `PlayerProgressBar.kt` — delete `internal fun formatTime` (lines 85–96, including its KDoc at 82–84) and the now-unused `import kotlin.math.roundToInt` (line 21); add `import com.continuum.app.android.ui.util.formatClockTime`; replace both call sites:

```kotlin
                text = formatTime(displayPosition.toDouble()),   // line 69
                text = formatTime(duration),                     // line 74
// become:
                text = formatClockTime(displayPosition.toDouble()),
                text = formatClockTime(duration),
```

**3c.** `ChaptersSheet.kt` — delete `private fun formatChapterTime` (lines 142–149); add `import com.continuum.app.android.ui.util.formatClockTime`; line 128:

```kotlin
            text = formatChapterTime(chapter.startSeconds),
// becomes:
            text = formatClockTime(chapter.startSeconds),
```

**3d.** `AudiobookPlayerScreen.kt` — delete `private fun formatTime` (lines 440–446); keep `formatSpeed`; add `import com.continuum.app.android.ui.util.formatClockTime`; lines 285–286:

```kotlin
            Text(formatTime(state.positionSeconds), style = MaterialTheme.typography.labelSmall)
            Text(formatTime(state.durationSeconds), style = MaterialTheme.typography.labelSmall)
// become:
            Text(formatClockTime(state.positionSeconds), style = MaterialTheme.typography.labelSmall)
            Text(formatClockTime(state.durationSeconds), style = MaterialTheme.typography.labelSmall)
```

**3e.** `AudiobookBookmarksSheet.kt` — delete `private fun formatTimestamp` (lines 177–183); add `import com.continuum.app.android.ui.util.formatClockTime`; line 144:

```kotlin
                text = formatTimestamp(bookmark.positionSeconds),
// becomes:
                text = formatClockTime(bookmark.positionSeconds),
```

- [ ] **Step 4: Run tests**

```
./gradlew :androidApp:testDebugUnitTest :androidApp:compileDebugKotlinAndroid
```

- [ ] **Step 5: Commit**

```
git add -A && git commit -m "Unify clock-time formatting in one androidApp formatter

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task F5: Collapse SeriesRow/SeasonRow into one ExpandableAggregateRow

**Files:**
- Modify: /Users/dev/projects/prairie/prairie-android/androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadEntryRows.kt
- Test: none new

The sealed `DownloadEntry` base (DownloadsViewModel.kt:65–76) exposes `id/title/subtitle/posterUrl/posterThumbhash/totalBytesUsed/progress/isComplete`, so one composable can serve both; only child selection differs.

- [ ] **Step 1: Write the failing test** — not unit-testable: this is a pure Compose UI restructuring and the repo has no Compose UI-test harness in androidApp. Covered by `./gradlew :androidApp:compileDebugKotlinAndroid` plus the existing suite `./gradlew :androidApp:testDebugUnitTest` (DownloadStatusLabelTest exercises this file's `downloadStatusLabel`).

- [ ] **Step 2: Run test to verify it fails** — N/A (no new test; see Step 1).

- [ ] **Step 3: Implementation**

In `DownloadEntryRows.kt`, replace the `Series`/`Season` branches of `renderEntry` (lines 113–133) with:

```kotlin
        is DownloadEntry.Series -> ExpandableAggregateRow(
            entry = entry,
            // Single-season case: skip the season level — go straight
            // to episode rows under the series. Reduces visual clutter
            // for the common 1-season scenario.
            children = if (entry.seasons.size == 1) entry.seasons.first().episodes else entry.seasons,
            modifier = Modifier.padding(start = leftInset),
            onItemClick = onItemClick,
            onReadEbook = onReadEbook,
            onOpenExternal = onOpenExternal,
            onDeleteSingle = onDeleteSingle,
            onDeleteEntry = onDeleteEntry,
            depth = depth,
        )

        is DownloadEntry.Season -> ExpandableAggregateRow(
            entry = entry,
            children = entry.episodes,
            modifier = Modifier.padding(start = leftInset),
            onItemClick = onItemClick,
            onReadEbook = onReadEbook,
            onOpenExternal = onOpenExternal,
            onDeleteSingle = onDeleteSingle,
            onDeleteEntry = onDeleteEntry,
            depth = depth,
        )
```

Then delete `SeriesRow` (lines 278–336) and `SeasonRow` (lines 338–379) entirely and add in their place:

```kotlin
/**
 * Expandable aggregate row shared by Series and Season entries: an
 * [AggregateRow] header with a remembered expand state, plus the
 * recursively rendered [children] while expanded. Child selection
 * (seasons vs. flattened episodes) is decided at the call site.
 */
@Composable
private fun ExpandableAggregateRow(
    entry: DownloadEntry,
    children: List<DownloadEntry>,
    modifier: Modifier = Modifier,
    onItemClick: (String) -> Unit,
    onReadEbook: (DownloadItem) -> Unit,
    onOpenExternal: (DownloadItem) -> Unit,
    onDeleteSingle: (DownloadItem) -> Unit,
    onDeleteEntry: (DownloadEntry) -> Unit,
    depth: Int,
) {
    var expanded by remember(entry.id) { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        AggregateRow(
            title = entry.title,
            subtitle = entry.subtitle ?: "",
            totalBytesUsed = entry.totalBytesUsed,
            progress = entry.progress,
            isComplete = entry.isComplete,
            posterUrl = entry.posterUrl,
            posterThumbhash = entry.posterThumbhash,
            expanded = expanded,
            onToggleExpand = { expanded = !expanded },
            onDelete = { onDeleteEntry(entry) },
        )
        AnimatedVisibility(visible = expanded) {
            Column {
                children.forEach { child ->
                    renderEntry(
                        entry = child,
                        depth = depth + 1,
                        onItemClick = onItemClick,
                        onReadEbook = onReadEbook,
                        onOpenExternal = onOpenExternal,
                        onDeleteSingle = onDeleteSingle,
                        onDeleteEntry = onDeleteEntry,
                    )
                }
            }
        }
    }
}
```

No import changes needed (everything used is already imported).

- [ ] **Step 4: Run tests**

```
./gradlew :androidApp:testDebugUnitTest :androidApp:compileDebugKotlinAndroid
```

- [ ] **Step 5: Commit**

```
git add -A && git commit -m "Collapse SeriesRow/SeasonRow into ExpandableAggregateRow

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task F6: ScopedJsonFileStore — shared skeleton for the three scoped JSON stores

**Files:**
- Create: /Users/dev/projects/prairie/prairie-android/android-shared/src/androidMain/kotlin/com/continuum/app/common/store/ScopedJsonFileStore.kt
- Test: /Users/dev/projects/prairie/prairie-android/android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/store/ScopedJsonFileStoreTest.kt
- Test: /Users/dev/projects/prairie/prairie-android/android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/audiobook/AudiobookBookmarksStoreTest.kt
- Test (extend): /Users/dev/projects/prairie/prairie-android/android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/EbookLocalStateStoreTest.kt
- Modify: /Users/dev/projects/prairie/prairie-android/android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/EbookLocalStateStore.kt
- Modify: /Users/dev/projects/prairie/prairie-android/android-shared/src/androidMain/kotlin/com/continuum/app/common/audiobook/AudiobookPositionStore.kt
- Modify: /Users/dev/projects/prairie/prairie-android/android-shared/src/androidMain/kotlin/com/continuum/app/common/audiobook/AudiobookBookmarksStore.kt

Notes from reading the code: all three stores re-declare `Json { ignoreUnknownKeys = true }`, the `$serverId/$profileId/$contentId` path builder, runCatching-read-with-`Log.w`, and the tmp+rename write (no fsync). Dedupe drift: ebook `addBookmark` uses `.distinctBy { it.id }`; audiobook `add` appends plain — and additionally returns the *unsorted* appended list while persisting the sorted one. Both get fixed: dedupe-by-id everywhere, and `add` returns exactly what it persists. `android.util.Log` is safe at unit-test scope (`isReturnDefaultValues = true` in android-shared's testOptions).

- [ ] **Step 1: Write the failing test**

Create `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/store/ScopedJsonFileStoreTest.kt` (JUnit4 + TemporaryFolder, matching `EbookLocalStateStoreTest`):

```kotlin
package com.continuum.app.common.store

import kotlinx.serialization.Serializable
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Serializable
private data class Payload(val name: String, val count: Int)

class ScopedJsonFileStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `write then read round trips and creates scoped directories`() {
        val store = ScopedJsonFileStore(tmp.newFolder("root"), tag = "Test")
        val file = store.fileFor("srv", "prof", "content")

        store.write(file, Payload(name = "a", count = 1))

        assertTrue(file.path.endsWith("srv/prof/content.json"))
        assertEquals(Payload(name = "a", count = 1), store.read<Payload>(file))
    }

    @Test
    fun `read returns null for missing or corrupt files`() {
        val store = ScopedJsonFileStore(tmp.newFolder("root"), tag = "Test")
        val file = store.fileFor("srv", "prof", "content")

        assertNull(store.read<Payload>(file))

        file.parentFile?.mkdirs()
        file.writeText("{ not json")
        assertNull(store.read<Payload>(file))
    }

    @Test
    fun `atomic write replaces existing content and leaves no tmp file`() {
        val store = ScopedJsonFileStore(tmp.newFolder("root"), tag = "Test")
        val file = store.fileFor("srv", "prof", "content")

        store.write(file, Payload(name = "a", count = 1))
        store.write(file, Payload(name = "b", count = 2))

        assertEquals(Payload(name = "b", count = 2), store.read<Payload>(file))
        assertFalse(File(file.parentFile, "${file.name}.tmp").exists())
    }
}
```

Create `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/audiobook/AudiobookBookmarksStoreTest.kt`:

```kotlin
package com.continuum.app.common.audiobook

import com.continuum.app.model.audiobook.AudiobookBookmark
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AudiobookBookmarksStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `add dedupes by id keeping the existing bookmark and returns position order`() {
        val store = AudiobookBookmarksStore(tmp.newFolder("filesDir"))
        val late = bookmark(id = "b1", positionSeconds = 120.0)
        val early = bookmark(id = "b2", positionSeconds = 30.0)

        store.add("srv", "prof", "book", late)
        store.add("srv", "prof", "book", early)
        val result = store.add("srv", "prof", "book", late.copy(note = "duplicate"))

        assertEquals(listOf("b2", "b1"), result.map { it.id })
        assertNull(result.first { it.id == "b1" }.note)
        assertEquals(listOf("b2", "b1"), store.list("srv", "prof", "book").map { it.id })
    }

    @Test
    fun `remove deletes only the matching id`() {
        val store = AudiobookBookmarksStore(tmp.newFolder("filesDir"))
        store.add("srv", "prof", "book", bookmark(id = "b1", positionSeconds = 10.0))
        store.add("srv", "prof", "book", bookmark(id = "b2", positionSeconds = 20.0))

        val result = store.remove("srv", "prof", "book", "b1")

        assertEquals(listOf("b2"), result.map { it.id })
    }

    private fun bookmark(id: String, positionSeconds: Double): AudiobookBookmark =
        AudiobookBookmark(id = id, positionSeconds = positionSeconds, createdAtMs = 1L)
}
```

Append to `EbookLocalStateStoreTest` (pins the unified dedupe behavior — ebook bookmark ids derive from `createdAtMs`):

```kotlin
    @Test
    fun `bookmark with duplicate id is not added twice`() {
        val store = EbookLocalStateStore(tmp.newFolder("filesDir"))

        store.addBookmark("srv", "prof", "book", "page:3", createdAtMs = 30L)
        store.addBookmark("srv", "prof", "book", "page:9", createdAtMs = 30L) // same id: "local-30"

        assertEquals(listOf("page:3"), store.listBookmarks("srv", "prof", "book").map { it.location })
    }
```

- [ ] **Step 2: Run test to verify it fails**

```
./gradlew :android-shared:testDebugUnitTest
```
Expected: `ScopedJsonFileStoreTest` fails to compile (`unresolved reference: ScopedJsonFileStore`); after the helper exists, `AudiobookBookmarksStoreTest.add dedupes by id...` fails red against the current append-without-dedupe `add` (returns 3 entries / unsorted).

- [ ] **Step 3: Implementation**

**3a.** Create `android-shared/src/androidMain/kotlin/com/continuum/app/common/store/ScopedJsonFileStore.kt`:

```kotlin
package com.continuum.app.common.store

import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

/**
 * Shared plumbing for the per-(serverId, profileId, contentId) JSON
 * file stores (ebook reading state, audiobook positions/bookmarks):
 * scoped path resolution under a root directory, one lenient [Json]
 * instance, safe reads (missing/corrupt files log and return null),
 * and atomic writes (tmp + fsync + rename) so a crash mid-write can't
 * leave a half-written file that fails to decode next launch.
 */
internal class ScopedJsonFileStore(
    private val root: File,
    internal val tag: String,
) {

    internal fun resolve(relativePath: String): File = File(root, relativePath)

    internal fun fileFor(
        serverId: String,
        profileId: String,
        contentId: String,
        suffix: String = ".json",
    ): File = resolve("$serverId/$profileId/$contentId$suffix")

    internal inline fun <reified T> read(file: File): T? {
        if (!file.isFile) return null
        return runCatching { json.decodeFromString<T>(file.readText()) }
            .onFailure { Log.w(tag, "read failed for ${file.path}", it) }
            .getOrNull()
    }

    internal inline fun <reified T> write(target: File, value: T) {
        writeAtomic(target, json.encodeToString(value))
    }

    internal fun writeAtomic(target: File, text: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        FileOutputStream(tmp).use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        if (!tmp.renameTo(target)) {
            Log.w(tag, "atomic rename failed for ${target.path}")
        }
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    }
}
```

**3b.** Rewrite `EbookLocalStateStore.kt` (data classes and public API unchanged):

```kotlin
package com.continuum.app.common.ebook

import com.continuum.app.common.store.ScopedJsonFileStore
import kotlinx.serialization.Serializable
import java.io.File

class EbookLocalStateStore(baseDir: File) {

    private val store = ScopedJsonFileStore(File(baseDir, "ebook_state"), TAG)

    @Serializable
    data class ProgressSnapshot(
        val fileId: Int,
        val location: String,
        val progress: Double,
        val updatedAtMs: Long,
    )

    @Serializable
    data class BookmarkSnapshot(
        val id: String,
        val location: String,
        val createdAtMs: Long,
    )

    private fun progressFile(serverId: String, profileId: String, contentId: String): File =
        store.fileFor(serverId, profileId, contentId, suffix = ".progress.json")

    private fun bookmarksFile(serverId: String, profileId: String, contentId: String): File =
        store.fileFor(serverId, profileId, contentId, suffix = ".bookmarks.json")

    private fun displaySettingsFile(serverId: String, profileId: String): File =
        store.resolve("$serverId/$profileId/reader-settings.json")

    fun readProgress(serverId: String, profileId: String, contentId: String): ProgressSnapshot? =
        store.read<ProgressSnapshot>(progressFile(serverId, profileId, contentId))

    fun writeProgress(
        serverId: String,
        profileId: String,
        contentId: String,
        snapshot: ProgressSnapshot,
    ) {
        store.write(progressFile(serverId, profileId, contentId), snapshot)
    }

    fun listBookmarks(serverId: String, profileId: String, contentId: String): List<BookmarkSnapshot> =
        store.read<List<BookmarkSnapshot>>(bookmarksFile(serverId, profileId, contentId))
            .orEmpty()
            .sortedBy { it.createdAtMs }

    fun addBookmark(
        serverId: String,
        profileId: String,
        contentId: String,
        location: String,
        createdAtMs: Long = System.currentTimeMillis(),
    ): BookmarkSnapshot {
        val bookmark = BookmarkSnapshot(
            id = "local-$createdAtMs",
            location = location,
            createdAtMs = createdAtMs,
        )
        val updated = (listBookmarks(serverId, profileId, contentId) + bookmark)
            .distinctBy { it.id }
            .sortedBy { it.createdAtMs }
        store.write(bookmarksFile(serverId, profileId, contentId), updated)
        return bookmark
    }

    fun removeBookmark(
        serverId: String,
        profileId: String,
        contentId: String,
        bookmarkId: String,
    ): List<BookmarkSnapshot> {
        val updated = listBookmarks(serverId, profileId, contentId)
            .filterNot { it.id == bookmarkId }
        store.write(bookmarksFile(serverId, profileId, contentId), updated)
        return updated
    }

    fun readDisplaySettings(serverId: String, profileId: String): ReaderDisplaySettings? =
        store.read<ReaderDisplaySettings>(displaySettingsFile(serverId, profileId))?.normalized()

    fun writeDisplaySettings(
        serverId: String,
        profileId: String,
        settings: ReaderDisplaySettings,
    ) {
        store.write(displaySettingsFile(serverId, profileId), settings.normalized())
    }

    companion object { private const val TAG = "EbookLocalStateStore" }
}
```

**3c.** Rewrite `AudiobookPositionStore.kt`:

```kotlin
package com.continuum.app.common.audiobook

import com.continuum.app.common.store.ScopedJsonFileStore
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Per-(serverId, profileId, contentId) position snapshot. One JSON file
 * per book at `<filesDir>/audiobook_positions/<server>/<profile>/<contentId>.json`.
 *
 * Local-only — once the server exposes /sessions for audiobooks, the VM
 * will write to both and reconcile on restore. Until then, the position
 * survives app restart but doesn't roam across devices.
 */
class AudiobookPositionStore(baseDir: File) {

    private val store = ScopedJsonFileStore(File(baseDir, "audiobook_positions"), TAG)

    @Serializable
    data class Snapshot(
        val positionSeconds: Double,
        val durationSeconds: Double,
        val updatedAtMs: Long,
    )

    fun read(serverId: String, profileId: String, contentId: String): Snapshot? =
        store.read<Snapshot>(store.fileFor(serverId, profileId, contentId))

    /** Persists atomically (tmp + fsync + rename) so a crash mid-write
     *  doesn't leave a half-written file that fails to decode next launch. */
    fun write(
        serverId: String,
        profileId: String,
        contentId: String,
        snapshot: Snapshot,
    ) {
        store.write(store.fileFor(serverId, profileId, contentId), snapshot)
    }

    fun delete(serverId: String, profileId: String, contentId: String): Boolean {
        val file = store.fileFor(serverId, profileId, contentId)
        return if (file.exists()) file.delete() else false
    }

    companion object { private const val TAG = "AudiobookPositionStore" }
}
```

**3d.** Rewrite `AudiobookBookmarksStore.kt` (dedupe unified; `add` now returns exactly the persisted, sorted, deduped list — previously it persisted sorted but returned the unsorted append):

```kotlin
package com.continuum.app.common.audiobook

import com.continuum.app.common.store.ScopedJsonFileStore
import com.continuum.app.model.audiobook.AudiobookBookmark
import java.io.File

/**
 * Per-(serverId, profileId, contentId) on-disk bookmark store. Each
 * book gets its own JSON file at
 * `<filesDir>/audiobook_bookmarks/<serverId>/<profileId>/<contentId>.json`,
 * holding an ordered list of [AudiobookBookmark]s.
 *
 * Local-only for now — once the server exposes a /bookmarks endpoint
 * we'll add a merging sync layer; the [AudiobookBookmark.id] field is
 * stable so server round-tripping is straightforward.
 */
class AudiobookBookmarksStore(baseDir: File) {

    private val store = ScopedJsonFileStore(File(baseDir, "audiobook_bookmarks"), TAG)

    fun list(serverId: String, profileId: String, contentId: String): List<AudiobookBookmark> =
        store.read<List<AudiobookBookmark>>(store.fileFor(serverId, profileId, contentId)).orEmpty()

    /** Add a bookmark (deduped by id, existing entry wins), persisting
     *  the full updated list atomically. Returns the persisted list. */
    fun add(
        serverId: String,
        profileId: String,
        contentId: String,
        bookmark: AudiobookBookmark,
    ): List<AudiobookBookmark> {
        val updated = (list(serverId, profileId, contentId) + bookmark)
            .distinctBy { it.id }
            .sortedBy { it.positionSeconds }
        write(serverId, profileId, contentId, updated)
        return updated
    }

    /** Remove a bookmark by id. No-op if missing. */
    fun remove(
        serverId: String,
        profileId: String,
        contentId: String,
        bookmarkId: String,
    ): List<AudiobookBookmark> {
        val updated = list(serverId, profileId, contentId).filterNot { it.id == bookmarkId }
        write(serverId, profileId, contentId, updated)
        return updated
    }

    /** Replace the bookmark with matching [id]'s note. */
    fun updateNote(
        serverId: String,
        profileId: String,
        contentId: String,
        bookmarkId: String,
        note: String?,
    ): List<AudiobookBookmark> {
        val updated = list(serverId, profileId, contentId).map {
            if (it.id == bookmarkId) it.copy(note = note?.takeIf { n -> n.isNotBlank() }) else it
        }
        write(serverId, profileId, contentId, updated)
        return updated
    }

    private fun write(
        serverId: String,
        profileId: String,
        contentId: String,
        bookmarks: List<AudiobookBookmark>,
    ) {
        store.write(store.fileFor(serverId, profileId, contentId), bookmarks)
    }

    companion object { private const val TAG = "AudiobookBookmarksStore" }
}
```

- [ ] **Step 4: Run tests**

```
./gradlew :android-shared:testDebugUnitTest
```
(All of `ScopedJsonFileStoreTest`, `AudiobookBookmarksStoreTest`, and the existing + extended `EbookLocalStateStoreTest` must pass. Then `./gradlew :androidApp:compileDebugKotlinAndroid :androidTvApp:compileDebugKotlinAndroid` to confirm store consumers still compile.)

- [ ] **Step 5: Commit**

```
git add -A && git commit -m "Extract ScopedJsonFileStore and harden atomic writes with fsync

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task F7: ReadingHubUiState.clearingLibraryContent() — centralize the 14-field reset

**Files:**
- Modify: /Users/dev/projects/prairie/prairie-android/androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reading/ReadingHubViewModel.kt
- Test: none new

- [ ] **Step 1: Write the failing test** — not unit-testable at reasonable cost: no `ReadingHubViewModel` test exists (androidApp unit tests cover only downloads/navigation/reader), and one would require fakes for `PersonalDataRepository`, `SectionRepository`, and `CatalogRepository`. This is a behavior-preserving consolidation of `copy(...)` lists; covered by the existing suite plus compilation: `./gradlew :androidApp:testDebugUnitTest :androidApp:compileDebugKotlinAndroid`.

- [ ] **Step 2: Run test to verify it fails** — N/A (see Step 1).

- [ ] **Step 3: Implementation**

Keep the `ReadingHubUiState` shape unchanged (no stored→computed conversion — consumers untouched). Add a private copy-helper at the bottom of `ReadingHubViewModel.kt` (after the class):

```kotlin
/**
 * Clears every piece of per-library content — Recommended sections,
 * Browse catalog + filters, Collections — along with their loading and
 * error flags. Used whenever the effective library selection changes
 * or goes away. Library-list fields (libraries, formats, selection,
 * librariesError) are left for the caller to set.
 */
private fun ReadingHubUiState.clearingLibraryContent(): ReadingHubUiState = copy(
    sections = emptyList(),
    sectionsError = null,
    isLoadingSections = false,
    catalogItems = emptyList(),
    catalogTotal = 0,
    catalogHasMore = false,
    catalogError = null,
    isLoadingCatalog = false,
    isLoadingMoreCatalog = false,
    browseGenres = emptyList(),
    selectedBrowseGenre = null,
    collections = emptyList(),
    collectionsError = null,
    isLoadingCollections = false,
)
```

Complete new code for every changed function:

```kotlin
    fun refresh() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingLibraries = true,
                    librariesError = null,
                )
            }

            when (val result = personalDataRepository.listUserLibraries()) {
                is ApiResult.Success -> {
                    val libraries = result.data
                        .readingLibraries()
                        .sortedBy { library -> library.sortOrder }
                    val availableFormats = libraries.availableReadingFormatFilters()
                    val selectedFormat = _uiState.value.selectedFormat
                        .takeIf { it in availableFormats }
                        ?: availableFormats.firstOrNull()
                        ?: ReadingFormatFilter.All
                    val filteredLibraries = libraries.filterByReadingFormat(selectedFormat)
                    val selectedLibraryId = _uiState.value.selectedLibraryId
                        ?.takeIf { currentId -> filteredLibraries.any { it.id == currentId } }
                        ?: filteredLibraries.firstOrNull()?.id

                    _uiState.update {
                        val base = if (selectedLibraryId == null) it.clearingLibraryContent() else it
                        base.copy(
                            isLoadingLibraries = false,
                            libraries = libraries,
                            selectedFormat = selectedFormat,
                            availableFormats = availableFormats,
                            filteredLibraries = filteredLibraries,
                            selectedLibraryId = selectedLibraryId,
                            librariesError = null,
                        )
                    }

                    if (selectedLibraryId != null) {
                        loadCurrentTab(selectedLibraryId, force = true)
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.clearingLibraryContent().copy(
                            isLoadingLibraries = false,
                            libraries = emptyList(),
                            availableFormats = emptyList(),
                            filteredLibraries = emptyList(),
                            selectedLibraryId = null,
                            librariesError = result.message.ifBlank { "Failed to load reading libraries" },
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.clearingLibraryContent().copy(
                            isLoadingLibraries = false,
                            libraries = emptyList(),
                            availableFormats = emptyList(),
                            filteredLibraries = emptyList(),
                            selectedLibraryId = null,
                            librariesError = "Network error: ${result.exception.message ?: "unknown"}",
                        )
                    }
                }
            }
        }
    }

    fun selectFormat(format: ReadingFormatFilter) {
        val state = _uiState.value
        if (format == state.selectedFormat || format !in state.availableFormats) return

        val filteredLibraries = state.libraries.filterByReadingFormat(format)
        val selectedLibraryId = state.selectedLibraryId
            ?.takeIf { currentId -> filteredLibraries.any { it.id == currentId } }
            ?: filteredLibraries.firstOrNull()?.id
        val libraryChanged = selectedLibraryId != state.selectedLibraryId

        if (libraryChanged) {
            recommendedLoadedLibraryId = null
            browseLoadedLibraryId = null
            collectionsLoadedLibraryId = null
        }

        _uiState.update {
            val base = if (libraryChanged) it.clearingLibraryContent() else it
            base.copy(
                selectedFormat = format,
                filteredLibraries = filteredLibraries,
                selectedLibraryId = selectedLibraryId,
            )
        }

        selectedLibraryId?.let { loadCurrentTab(it, force = libraryChanged) }
    }

    fun selectLibrary(libraryId: Int) {
        if (_uiState.value.filteredLibraries.none { it.id == libraryId }) return
        if (_uiState.value.selectedLibraryId == libraryId) return
        recommendedLoadedLibraryId = null
        browseLoadedLibraryId = null
        collectionsLoadedLibraryId = null
        _uiState.update {
            it.clearingLibraryContent().copy(selectedLibraryId = libraryId)
        }
        loadCurrentTab(libraryId, force = true)
    }
```

Behavior notes: `selectFormat` and `selectLibrary` reset exactly the same 14 fields as before (verified field-by-field against lines 162–181 and 193–211). In `refresh`, the success-with-no-library and error branches previously cleared only `sections`/`catalogItems`/`collections`; they now also clear the per-library errors, loading flags, genre filter, and catalog totals — all of which describe content of a library that no longer exists, so clearing them is the intended correction (stale `catalogHasMore`/`browseGenres` could otherwise survive a failed refresh).

`filteredLibraries`/`availableFormats` remain stored fields by design (derivations stay at their three existing computation sites) — do not convert them to computed properties.

- [ ] **Step 4: Run tests**

```
./gradlew :androidApp:testDebugUnitTest :androidApp:compileDebugKotlinAndroid
```

Final whole-branch verification (repo convention):

```
git diff --check && ./gradlew :shared:testDebugUnitTest :android-shared:testDebugUnitTest :androidApp:testDebugUnitTest :androidApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest :androidTvApp:compileDebugKotlinAndroid
```

- [ ] **Step 5: Commit**

```
git add -A && git commit -m "Centralize ReadingHub per-library reset in one copy-helper

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```
