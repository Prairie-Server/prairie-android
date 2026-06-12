# Downloads Status Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make mobile Downloads rows clearly communicate queued, downloading, completed, failed, cancelled, and unknown states.

**Architecture:** Carry typed `DownloadStatus` into `DownloadItem`, add pure label/color helper functions in `DownloadEntryRows.kt`, and render a status line that includes progress percentages even when bytes are still zero. Keep existing delete/open/read behavior unchanged.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Gradle Android unit tests.

---

### Task 1: Download Status Labels

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadsViewModel.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadEntryRows.kt`
- Test: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadStatusLabelTest.kt`

- [x] **Step 1: Write failing tests**

Assert status labels:
- queued, zero progress -> `Queued`
- downloading, zero progress -> `Downloading`
- downloading, 42% progress -> `Downloading · 42%`
- completed -> `Ready`
- failed -> `Failed`
- cancelled -> `Cancelled`
- unknown -> `Needs attention`

- [x] **Step 2: Run focused test and verify RED**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.downloads.DownloadStatusLabelTest"`

Expected: fail because `downloadStatusLabel` is missing.

- [x] **Step 3: Implement status on item and label helper**

Add `val status: DownloadStatus = DownloadStatus.Unknown` to `DownloadItem`, set it in `DownloadRecord.toItem()`, and implement `internal fun downloadStatusLabel(status: DownloadStatus, progress: Float): String`.

- [x] **Step 4: Render clearer row state**

Replace the current size plus generic `FAILED` badge in `SingleRow` with a line that shows file size plus status label. Failed/cancelled/unknown labels use error color; active and ready labels use normal secondary text.

- [x] **Step 5: Run focused test and verify GREEN**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.downloads.DownloadStatusLabelTest"`

Expected: pass.

- [x] **Step 6: Run full verification**

Run: `git diff --check && ./gradlew :shared:testDebugUnitTest :android-shared:testDebugUnitTest :androidApp:testDebugUnitTest :androidApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest :androidTvApp:compileDebugKotlinAndroid`

Expected: build successful.

- [x] **Step 7: Commit**

Run:
```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadsViewModel.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadEntryRows.kt androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadStatusLabelTest.kt docs/superpowers/plans/2026-06-09-downloads-status-polish.md
git commit -m "fix: clarify download row statuses"
```
