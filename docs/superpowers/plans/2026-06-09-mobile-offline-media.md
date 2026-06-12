# Mobile Offline Media Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let Android mobile play downloaded audiobooks and read downloaded ebooks from local files, with local-first progress/bookmark persistence and best-effort server sync.

**Architecture:** Add an Android-only offline media resolver around `DownloadStorage`, then call it from `AudiobookPlayerViewModel` and `ReaderViewModel` before server-backed playback/read URLs. Keep local playback/read scoped to completed sidecars with bytes present on disk.

**Tech Stack:** Kotlin, Android filesystem, `DownloadStorage`, Compose view models, Media3 `file://` playback, existing reader renderers.

---

### Task 1: Offline Media Resolver

**Files:**
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/offline/OfflineMediaResolver.kt`
- Test: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/offline/OfflineMediaResolverTest.kt`

Steps:
- [ ] Add tests for completed sidecar lookup, requested file preference, missing-byte rejection, and non-completed rejection.
- [ ] Implement `OfflineMediaResolver` with a `findLocalMedia(contentId, requestedFileId, allowFallback)` API.
- [ ] Return `fileId`, `file`, `fileUrl`, `sidecar`, and optional display metadata.
- [ ] Run `./gradlew :androidApp:testDebugUnitTest --tests com.continuum.app.android.offline.OfflineMediaResolverTest`.

### Task 2: Mobile Ebook Offline Read

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModel.kt`

Steps:
- [ ] Inject `OfflineMediaResolver` into `ReaderViewModel`.
- [ ] Before building server read path, resolve a completed local file by `contentId` and requested `fileId`.
- [ ] Use `file://` URL for local reads.
- [ ] Preserve local progress immediately and keep server progress/bookmark sync best-effort.
- [ ] Support opening from downloads when server detail is unavailable by using sidecar metadata.
- [ ] Compile `./gradlew :androidApp:compileDebugKotlinAndroid`.

### Task 3: Mobile Audiobook Offline Playback

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerViewModel.kt`

Steps:
- [ ] Inject `OfflineMediaResolver` into `AudiobookPlayerViewModel`.
- [ ] Resolve local completed audiobook file before server playback session start.
- [ ] Use `file://` stream URL and skip server session start/report/stop for local playback.
- [ ] Keep local audiobook position/bookmarks working.
- [ ] Compile `./gradlew :androidApp:compileDebugKotlinAndroid`.

### Task 4: Verification

Steps:
- [ ] Run `./gradlew :androidApp:testDebugUnitTest :androidApp:compileDebugKotlinAndroid`.
- [ ] Run `./gradlew :shared:testDebugUnitTest`.
- [ ] Run `./gradlew :androidTvApp:testDebugUnitTest :androidTvApp:compileDebugKotlinAndroid`.
- [ ] Check `git status --short --branch`.
