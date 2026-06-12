# Literary Media Navigation Correction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move audiobooks into mobile Reading while keeping Android TV audiobook access under Audio and reserving mobile Audio for music/non-book audio.

**Architecture:** Keep the existing `MediaMode` enum, but split capability derivation by platform. Mobile and TV read the same `/api/v1/user/libraries` response, then apply platform-specific library type mapping before deriving visible navigation and search scopes.

**Tech Stack:** Kotlin Multiplatform shared tests, Android mobile Compose search/nav, Android TV Compose nav/search, existing Gradle verification tasks.

---

## Task 1: Platform-Specific Capability Mapping

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/model/navigation/MediaMode.kt`
- Modify: `shared/src/commonTest/kotlin/com/continuum/app/model/navigation/MediaModeTest.kt`

- [ ] Add tests proving `audiobook` and `audiobooks` map to `Reading` for mobile.
- [ ] Add tests proving `audiobook` and `audiobooks` map to `Audio` for TV.
- [ ] Add tests proving music-like types map to `Audio` on mobile.
- [ ] Add tests proving ebook/comic/manga types are excluded from visible TV modes.
- [ ] Implement `mobileMediaModeCapabilities()` and `tvMediaModeCapabilities()` extension functions on `Iterable<UserLibrary>`.
- [ ] Keep `mediaModeCapabilities()` as a compatibility alias for mobile behavior, or update call sites to use explicit platform functions.
- [ ] Run `./gradlew :shared:testDebugUnitTest --tests com.continuum.app.model.navigation.MediaModeTest`.
- [ ] Commit with `feat: split media capabilities by platform`.

## Task 2: Use Mobile Capabilities For Mobile Nav And Search

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/MainScreen.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/search/SearchScreen.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/search/SearchViewModel.kt`
- Modify: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/navigation/MobileMediaTabsTest.kt`

- [ ] Update mobile library capability calls to use `mobileMediaModeCapabilities()`.
- [ ] Update tab tests so audiobook-only mobile capability results in `Tab.Reading`, not `Tab.Audio`.
- [ ] Ensure mobile `Audio` appears only for music/audio-native capabilities.
- [ ] Update mobile search so a Reading scope can return both ebooks and audiobooks. Until the server has `type=reading`, query broadly and filter visible results to literary media types for Reading.
- [ ] Ensure mobile search does not show `Audio` for audiobook-only accounts.
- [ ] Run `./gradlew :androidApp:testDebugUnitTest --tests com.continuum.app.android.ui.navigation.MobileMediaTabsTest :androidApp:compileDebugKotlinAndroid`.
- [ ] Commit with `feat: treat audiobooks as mobile reading`.

## Task 3: Use TV Capabilities For TV Nav

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/shell/TvMainShell.kt`
- Modify: `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/shell/TvMediaDestinationsTest.kt`

- [ ] Update TV library capability calls to use `tvMediaModeCapabilities()`.
- [ ] Update TV tests so audiobook-only capabilities produce `TvRootDestination.Audio`.
- [ ] Keep ebook-only TV accounts falling back to Search.
- [ ] Confirm TV search still exposes Audiobooks but never Reading/ebooks.
- [ ] Run `./gradlew :androidTvApp:testDebugUnitTest --tests com.continuum.app.tv.ui.shell.TvMediaDestinationsTest :androidTvApp:compileDebugKotlinAndroid`.
- [ ] Commit with `feat: keep tv audiobooks under audio`.

## Final Verification

Run:

```bash
git diff --check && ./gradlew :shared:testDebugUnitTest \
  :androidApp:testDebugUnitTest \
  :androidApp:compileDebugKotlinAndroid \
  :androidTvApp:testDebugUnitTest \
  :androidTvApp:compileDebugKotlinAndroid \
  && git status --short --branch
```

Expected: `BUILD SUCCESSFUL` and a clean worktree.
