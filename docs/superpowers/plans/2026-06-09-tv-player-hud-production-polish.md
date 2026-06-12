# TV Player HUD Production Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Android TV player HUD feel production-grade by hiding data-only tabs when they have no backing data while keeping useful control tabs available.

**Architecture:** Add a pure `visibleHudTabs()` helper in `TvPlayerHud.kt` and drive the composable tab list from it. Keep Info, Video, Audio, and Subtitles visible because they expose controls; show Stats only when `PlayerStatsSnapshot` has renderable rows, and Chapters only when the selected version has chapters.

**Tech Stack:** Kotlin, Jetpack Compose for TV, Media3-driven player state, Gradle Android unit tests.

---

### Task 1: Capability-Aware HUD Tabs

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHudTabsTest.kt`

- [ ] **Step 1: Write failing tests**

Create `TvPlayerHudTabsTest.kt` with tests that assert:
- Empty stats and no chapters produce tabs `Info, Video, Audio, Subtitles`.
- Populated stats add `Stats`.
- Non-empty chapters add `Chapters`.
- Both populated data sets produce `Info, Stats, Video, Audio, Subtitles, Chapters`.

- [ ] **Step 2: Run focused test and verify RED**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests "com.continuum.app.tv.ui.screens.player.TvPlayerHudTabsTest"`

Expected: fail because `visibleHudTabs` is not defined or not accessible.

- [ ] **Step 3: Implement helper and wire HUD**

In `TvPlayerHud.kt`:
- Make `HudTab` internal.
- Add `internal fun visibleHudTabs(stats: PlayerStatsSnapshot, chapters: List<VersionChapter>): List<HudTab>`.
- Replace the fixed `HUD_TABS` usage with the helper.
- If selected tab disappears after data changes, select the first visible tab.
- Update comments to remove stale placeholder language.

- [ ] **Step 4: Run focused test and verify GREEN**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests "com.continuum.app.tv.ui.screens.player.TvPlayerHudTabsTest"`

Expected: pass.

- [ ] **Step 5: Run full verification**

Run: `git diff --check && ./gradlew :shared:testDebugUnitTest :android-shared:testDebugUnitTest :androidApp:testDebugUnitTest :androidApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest :androidTvApp:compileDebugKotlinAndroid`

Expected: build successful.

- [ ] **Step 6: Commit**

Run:
```bash
git add androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHudTabsTest.kt docs/superpowers/plans/2026-06-09-tv-player-hud-production-polish.md
git commit -m "fix: hide empty tv player hud tabs"
```
