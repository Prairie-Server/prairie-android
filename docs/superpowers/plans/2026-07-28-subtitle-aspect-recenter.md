# Subtitle Aspect-Mode Recentring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the Android phone and TV subtitle canvas aligned with the final visible video viewport when switching among Fit, Fill/Zoom, and Stretch.

**Architecture:** `SubtitleManager` remains the only subtitle-geometry owner. A pure mode-aware selector will reject stale fitted content-frame geometry for modes whose video fills the viewport, and the existing per-`PlayerView` synchronizer will perform one lifecycle-owned post-layout reconciliation after each explicit sync request.

**Tech Stack:** Kotlin 2.1, Android Views, Media3 `PlayerView`/`AspectRatioFrameLayout`, Robolectric/JUnit, Gradle 8.12.

## Global Constraints

- Apply the shared correction to Android phone and Android TV; phone is the confirmed reproduction.
- Fit aligns the subtitle canvas with the fitted video rectangle.
- Phone Fill/Media3 Zoom and phone Stretch/Media3 Fill align the canvas with the full visible player viewport.
- Preserve authored ASS/SSA and PGS positions relative to the canvas; do not rewrite individual cue coordinates.
- Preserve existing letterbox detection, title-safe insets, subtitle appearance, timing, track selection, playback state, networking, and persisted settings.
- Do not add polling, arbitrary delays, a second renderer, server changes, protocol changes, or transcoding changes.
- A delayed reconciliation must not mutate a detached or replaced `PlayerView`.
- Do not install on the Shield without a separate explicit request.

---

### Task 1: Make subtitle canvas selection resize-mode aware

**Files:**
- Modify: `android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/SubtitleManager.kt:445-492,662-673`
- Test: `android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/player/SubtitleManagerAppearanceTest.kt:143-251`

**Interfaces:**
- Consumes: `SubtitleVideoRect`, Media3 resize-mode constants, `displayedSubtitleVideoRect(...)`, and the current content-frame rectangle.
- Produces: `internal fun selectSubtitleCanvasRect(resizeMode: Int, contentFrameRect: SubtitleVideoRect?, displayedVideoRect: SubtitleVideoRect): SubtitleVideoRect`.

- [ ] **Step 1: Add failing stale-frame regression tests**

Add these tests to `SubtitleManagerAppearanceTest`:

```kotlin
@Test
fun zoomIgnoresStaleFittedContentFrameAndUsesFullViewport() {
    val staleFit = SubtitleVideoRect(left = 0, top = 236, width = 2404, height = 1352)
    val fullViewport = SubtitleVideoRect(left = 0, top = 0, width = 2404, height = 1080)

    assertEquals(
        fullViewport,
        selectSubtitleCanvasRect(
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            contentFrameRect = staleFit,
            displayedVideoRect = fullViewport,
        ),
    )
}

@Test
fun stretchIgnoresStaleFittedContentFrameAndUsesFullViewport() {
    val staleFit = SubtitleVideoRect(left = 240, top = 0, width = 1920, height = 1080)
    val fullViewport = SubtitleVideoRect(left = 0, top = 0, width = 2400, height = 1080)

    assertEquals(
        fullViewport,
        selectSubtitleCanvasRect(
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL,
            contentFrameRect = staleFit,
            displayedVideoRect = fullViewport,
        ),
    )
}

@Test
fun fitContinuesToUsePostLayoutContentFrame() {
    val fittedFrame = SubtitleVideoRect(left = 0, top = 0, width = 1920, height = 1080)
    val computedFallback = SubtitleVideoRect(left = 240, top = 0, width = 1920, height = 1080)

    assertEquals(
        fittedFrame,
        selectSubtitleCanvasRect(
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
            contentFrameRect = fittedFrame,
            displayedVideoRect = computedFallback,
        ),
    )
}

@Test
fun repeatedModeSelectionDoesNotRetainPreviousCanvas() {
    val fit = SubtitleVideoRect(left = 240, top = 0, width = 1920, height = 1080)
    val full = SubtitleVideoRect(left = 0, top = 0, width = 2400, height = 1080)

    val fill = selectSubtitleCanvasRect(
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        fit,
        full,
    )
    val stretch = selectSubtitleCanvasRect(
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
        fit,
        full,
    )
    val restoredFit = selectSubtitleCanvasRect(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        fit,
        fit,
    )

    assertEquals(full, fill)
    assertEquals(full, stretch)
    assertEquals(fit, restoredFit)
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest \
  --tests org.siloserver.silo.common.player.SubtitleManagerAppearanceTest \
  --max-workers=2 --no-daemon
```

Expected: compilation fails because `selectSubtitleCanvasRect` does not exist.

- [ ] **Step 3: Implement the minimal mode-aware selector**

Add beside `displayedSubtitleVideoRect`:

```kotlin
internal fun selectSubtitleCanvasRect(
    resizeMode: Int,
    contentFrameRect: SubtitleVideoRect?,
    displayedVideoRect: SubtitleVideoRect,
): SubtitleVideoRect = when (resizeMode) {
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    AspectRatioFrameLayout.RESIZE_MODE_FILL,
    -> displayedVideoRect
    else -> contentFrameRect ?: displayedVideoRect
}
```

Change `SubtitleVideoRectSync.applyRect` to compute both inputs before applying
letterbox and title-safe insets:

```kotlin
val resizeMode = playerView.resizeMode
val displayedVideoRect = displayedSubtitleVideoRect(
    viewWidth = playerView.width,
    viewHeight = playerView.height,
    videoWidth = videoSize.width,
    videoHeight = videoSize.height,
    videoPixelWidthHeightRatio = videoSize.pixelWidthHeightRatio,
    resizeMode = resizeMode,
)
val rect = selectSubtitleCanvasRect(
    resizeMode = resizeMode,
    contentFrameRect = playerView.contentFrameSubtitleRect(),
    displayedVideoRect = displayedVideoRect,
).insetByLetterbox(letterbox).insetByTitleSafe(titleSafeFraction)
```

This deliberately selects the already-full `displayedVideoRect` for Zoom and
Fill even when the content frame has not completed its next layout.

- [ ] **Step 4: Run the focused class and verify GREEN**

Run the Step 2 command.

Expected: `SubtitleManagerAppearanceTest` passes with zero failures.

- [ ] **Step 5: Commit the independently testable geometry correction**

```bash
git add \
  android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/SubtitleManager.kt \
  android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/player/SubtitleManagerAppearanceTest.kt
git commit -m "fix(subtitles): recenter canvas for fill modes"
```

---

### Task 2: Reconcile once after layout and cancel stale callbacks

**Files:**
- Modify: `android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/SubtitleManager.kt:270-282,563-704`
- Test: `android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/player/SubtitleManagerAppearanceTest.kt`

**Interfaces:**
- Consumes: Task 1's `selectSubtitleCanvasRect(...)`.
- Produces: `SubtitleVideoRectSync.updateAndReconcileAfterLayout()`; at most one posted callback per `PlayerView`, removed during disposal.

- [ ] **Step 1: Add failing lifecycle regression tests**

Add Robolectric tests that mount a real `PlayerView` in an `Activity`, invoke
`SubtitleManager.syncSubtitleVideoBounds`, and inspect the private synchronizer
through the manager's `videoRectSyncs` field:

```kotlin
@Test
fun explicitSyncQueuesOnlyOnePostLayoutReconciliation() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    val playerView = PlayerView(activity)
    activity.setContentView(playerView)
    playerView.layout(0, 0, 2400, 1080)
    val manager = SubtitleManager()

    manager.syncSubtitleVideoBounds(playerView)
    manager.syncSubtitleVideoBounds(playerView)

    val sync = manager.subtitleRectSyncForTest(playerView)
    assertTrue(sync.postLayoutPendingForTest())
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    assertFalse(sync.postLayoutPendingForTest())
}

@Test
fun detachCancelsPendingPostLayoutReconciliation() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    val playerView = PlayerView(activity)
    activity.setContentView(playerView)
    playerView.layout(0, 0, 2400, 1080)
    val manager = SubtitleManager()

    manager.syncSubtitleVideoBounds(playerView)
    val sync = manager.subtitleRectSyncForTest(playerView)
    activity.setContentView(FrameLayout(activity))

    assertTrue(sync.isDisposedForTest())
    assertFalse(sync.postLayoutPendingForTest())
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    assertTrue(sync.isDisposedForTest())
}
```

Keep reflection helpers private to the test file. They must expose existing
objects only; do not add production `ForTest` methods:

```kotlin
private fun SubtitleManager.subtitleRectSyncForTest(playerView: PlayerView): Any {
    val field = SubtitleManager::class.java.getDeclaredField("videoRectSyncs")
    field.isAccessible = true
    val syncs = field.get(this) as Map<*, *>
    return requireNotNull(syncs[playerView])
}

private fun Any.postLayoutPendingForTest(): Boolean {
    val field = javaClass.getDeclaredField("postLayoutPending")
    field.isAccessible = true
    return field.getBoolean(this)
}

private fun Any.isDisposedForTest(): Boolean {
    val field = javaClass.getDeclaredField("isDisposed")
    field.isAccessible = true
    return field.getBoolean(this)
}
```

The test file imports `android.app.Activity`, `android.os.Looper`,
`android.widget.FrameLayout`, `androidx.media3.ui.PlayerView`,
`org.robolectric.Robolectric`, `org.robolectric.Shadows`,
`kotlin.test.assertFalse`, and `kotlin.test.assertTrue`.

- [ ] **Step 2: Run the focused class and verify RED**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest \
  --tests org.siloserver.silo.common.player.SubtitleManagerAppearanceTest \
  --max-workers=2 --no-daemon
```

Expected: the test cannot find `postLayoutPending`, proving the bounded
reconciliation is absent.

- [ ] **Step 3: Implement one lifecycle-owned post-layout callback**

In `SubtitleVideoRectSync`, add:

```kotlin
private var postLayoutPending = false
private val postLayoutUpdate = Runnable {
    postLayoutPending = false
    if (!isDisposed) update()
}

fun updateAndReconcileAfterLayout() {
    update()
    val playerView = playerViewRef.get() ?: return
    if (isDisposed || postLayoutPending) return
    postLayoutPending = true
    playerView.postOnAnimation(postLayoutUpdate)
}
```

Change `SubtitleManager.syncSubtitleVideoBounds` to call:

```kotlin
sync.updateAndReconcileAfterLayout()
```

In `dispose`, remove the callback before clearing listeners:

```kotlin
playerView?.removeCallbacks(postLayoutUpdate)
postLayoutPending = false
```

Do not post from ordinary layout/video-size callbacks; those continue calling
`update()` directly. This keeps the extra reconciliation bounded to explicit
screen sync requests.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the Step 2 command.

Expected: all `SubtitleManagerAppearanceTest` tests pass.

- [ ] **Step 5: Run neighboring subtitle geometry tests**

```bash
./gradlew :android-shared:testDebugUnitTest \
  --tests org.siloserver.silo.common.player.SubtitleManagerAppearanceTest \
  --tests org.siloserver.silo.common.player.LetterboxInsetTest \
  --tests org.siloserver.silo.common.player.TitleSafeInsetTest \
  --max-workers=2 --no-daemon
```

Expected: zero failures.

- [ ] **Step 6: Commit the lifecycle correction**

```bash
git add \
  android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/SubtitleManager.kt \
  android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/player/SubtitleManagerAppearanceTest.kt
git commit -m "fix(subtitles): reconcile canvas after aspect layout"
```

---

### Task 3: Lock phone/TV wiring and verify release behaviour

**Files:**
- Create: `androidApp/src/androidUnitTest/kotlin/org/siloserver/silo/android/ui/screens/player/SubtitleAspectModeWiringSourceTest.kt`
- Create: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/player/TvSubtitleAspectModeWiringSourceTest.kt`
- Verify: `androidApp/src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/player/PlayerScreen.kt:1085-1123`
- Verify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/player/TvPlayerScreen.kt:1775-1793,3088-3113`

**Interfaces:**
- Consumes: existing `SubtitleManager.syncSubtitleVideoBounds(PlayerView)`, phone resize-mode mapping, and TV `applyPlayerViewVideoFillMode`.
- Produces: platform source-contract tests ensuring each resize update is immediately followed by shared subtitle reconciliation.

- [ ] **Step 1: Add phone and TV source-contract tests**

Phone:

```kotlin
class SubtitleAspectModeWiringSourceTest {
    private fun source(path: String): String {
        val moduleRelative = File("src/androidMain/kotlin/$path")
        val projectRelative = File("androidApp/src/androidMain/kotlin/$path")
        return (moduleRelative.takeIf(File::exists) ?: projectRelative).readText()
    }

    @Test
    fun playerViewReconcilesSubtitlesAfterResizeModeUpdate() {
        val source = source(
            "org/siloserver/silo/android/ui/screens/player/PlayerScreen.kt"
        )
        val update = source.substringAfter("update = { view ->")
            .substringBefore("modifier = Modifier")

        assertTrue(update.contains("view.resizeMode = resizeMode"))
        assertTrue(update.contains("subtitleManager.syncSubtitleVideoBounds(view)"))
        assertTrue(
            update.indexOf("view.resizeMode = resizeMode") <
                update.indexOf("subtitleManager.syncSubtitleVideoBounds(view)")
        )
    }
}
```

TV:

```kotlin
class TvSubtitleAspectModeWiringSourceTest {
    private fun source(path: String): String {
        val moduleRelative = File("src/androidMain/kotlin/$path")
        val projectRelative = File("androidTvApp/src/androidMain/kotlin/$path")
        return (moduleRelative.takeIf(File::exists) ?: projectRelative).readText()
    }

    @Test
    fun playerViewReconcilesSubtitlesAfterFillModeUpdate() {
        val source = source(
            "org/siloserver/silo/tv/ui/screens/player/TvPlayerScreen.kt"
        )
        val update = source.substringAfter("update = { view ->")
            .substringBefore("if (!isInPictureInPictureMode")

        val aspectCall = "applyPlayerViewVideoFillMode(view, state.videoFillMode)"
        val subtitleCall = "subtitleManager.syncSubtitleVideoBounds(view)"
        assertTrue(update.contains(aspectCall))
        assertTrue(update.contains(subtitleCall))
        assertTrue(update.indexOf(aspectCall) < update.indexOf(subtitleCall))
    }
}
```

Both files import `java.io.File`, `kotlin.test.Test`, and
`kotlin.test.assertTrue`.

- [ ] **Step 2: Prove the source tests detect reversed ordering**

Temporarily reverse each extracted ordering assertion (`<` to `>`) and run:

```bash
./gradlew \
  :androidApp:testDebugUnitTest \
  :androidTvApp:testDebugUnitTest \
  --tests '*SubtitleAspectModeWiringSourceTest' \
  --max-workers=2 --no-daemon
```

Expected: both tests fail on their ordering assertion. Restore `<` before
continuing.

- [ ] **Step 3: Run the source tests GREEN**

Run the Step 2 command after restoring the intended assertions.

Expected: both tests pass.

- [ ] **Step 4: Run the complete relevant feature gate**

```bash
./gradlew \
  :android-shared:testDebugUnitTest \
  :androidApp:testDebugUnitTest \
  :androidTvApp:testDebugUnitTest \
  --tests '*SubtitleManagerAppearanceTest' \
  --tests '*LetterboxInsetTest' \
  --tests '*TitleSafeInsetTest' \
  --tests '*SubtitleAspectModeWiringSourceTest' \
  --max-workers=2 --no-daemon
```

Expected: zero failures.

- [ ] **Step 5: Run full debug unit tests**

```bash
./gradlew testDebugUnitTest --max-workers=2 --no-daemon
```

Expected: build succeeds with zero test failures.

- [ ] **Step 6: Run supply-chain and release compilation gates**

```bash
./scripts/test-check-build-supply-chain.sh
./scripts/check-build-supply-chain.sh
./gradlew \
  :androidApp:assembleRelease \
  :androidTvApp:assembleRelease \
  -PallowDebugReleaseSigning=true \
  --max-workers=2 --no-daemon
```

Expected: policy scripts exit zero and both minified release assemblies succeed.

- [ ] **Step 7: Verify on the physical Pixel only**

First confirm serial `58211FDCQ000CU`, compare the candidate and installed
package/version/signing certificate, and stop if the signer differs. Then use
only:

```bash
adb -s 58211FDCQ000CU install -r \
  androidApp/build/outputs/apk/release/androidApp-universal-release.apk
adb -s 58211FDCQ000CU shell am start -W \
  -n org.siloserver.silo/org.siloserver.silo.android.MainActivity
```

With Bluetooth earbuds disconnected, play a title containing centred text
subtitles and switch Fit → Fill → Stretch → Fit. Capture screenshots after
layout settles and verify:

- Fill and Stretch centre the subtitle canvas in the full visible viewport.
- Returning to Fit restores the fitted-video canvas.
- repeated switching does not retain an earlier offset;
- subtitle timing and vertical position remain stable;
- no immediate fatal exception, ANR, or player error appears in Pixel logcat.

Do not issue any ADB command to the Shield or an emulator.

- [ ] **Step 8: Request independent focused review**

Review only the branch diff against:

- mode-aware stale-frame rejection;
- authored cue preservation;
- bounded callback ownership and detach cancellation;
- phone/TV wiring;
- absence of unrelated playback changes.

Address every substantive finding test-first and rerun Tasks 1-3's focused
gates.

- [ ] **Step 9: Commit verification contracts**

```bash
git add \
  androidApp/src/androidUnitTest/kotlin/org/siloserver/silo/android/ui/screens/player/SubtitleAspectModeWiringSourceTest.kt \
  androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/player/TvSubtitleAspectModeWiringSourceTest.kt
git commit -m "test(subtitles): lock aspect recenter wiring"
```

- [ ] **Step 10: Final diff and branch verification**

```bash
git diff --check origin/main...HEAD
git status --short
git log --oneline origin/main..HEAD
```

Expected: no whitespace errors, clean worktree, and only the approved spec,
plan, shared geometry fix, lifecycle reconciliation, and platform tests.
