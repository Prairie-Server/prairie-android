# Audiobook Player — Phase 1: Shared Engine — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Deliver the Phase 1 "shared engine" from the audiobook redesign spec (`docs/superpowers/specs/2026-06-12-audiobook-player-redesign-design.md`, §4.0 / §4.2 / §6.1): add a pure, unit-tested `AudiobookChapters` to `shared/commonMain`, and relocate `AudiobookPlayerViewModel` from `androidApp` into `android-shared` so both the phone and (future) TV apps can consume it. No UI redesign, no behavior change in the move.

**Architecture:** Pure chapter math lives in `shared` (commonMain, platform-agnostic, `commonTest`-covered). The Android `ViewModel` (Media3 / DataStore / coroutine-bound) lives in `android-shared` (androidMain). The phone Compose screen stays in `androidApp` and only updates its import + Koin wiring. The VM delegates all chapter math to `AudiobookChapters` instead of computing it inline.

**Tech Stack:** Kotlin Multiplatform (androidTarget only on both `shared` and `android-shared`), Gradle, `kotlin-test` (commonTest) + `kotlin-test-junit` (androidUnitTest), Koin DI, Media3, kotlinx-coroutines. Commands assume the repository root (`prairie-android`) is the cwd.

---

## File Structure

| File | Status | Responsibility |
| --- | --- | --- |
| `shared/src/commonMain/kotlin/com/continuum/app/audiobook/AudiobookChapters.kt` | Create | Pure chapter math: current index from position + start times, chapter progress 0..1, chapter-count label, prev/next navigation target (incl. ">3s restart current" rule), single-chapter degrade. No Android / Media3 / coroutine deps. |
| `shared/src/commonTest/kotlin/com/continuum/app/audiobook/AudiobookChaptersTest.kt` | Create | Full unit coverage: boundaries, position exactly on a boundary, empty / one-chapter degrade, >3s prev rule, progress clamping, count label. |
| `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/AudiobookPlayerViewModel.kt` | Create (relocated) | The audiobook `ViewModel`, moved from `androidApp`. Delegates chapter math to `AudiobookChapters`. Otherwise byte-for-byte behavior-preserving. |
| `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/SleepTimerChoice.kt` | Create (relocated) | The `SleepTimerChoice` sealed class, moved out of the androidApp UI file so the relocated VM can reference it without depending on `androidApp`. |
| `android-shared/build.gradle.kts` | Modify | Add explicit `lifecycle.viewmodel.kmp` dependency to `androidMain` (shared declares it `implementation`, so it is not transitively visible). |
| `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerViewModel.kt` | Delete | Removed; replaced by the android-shared copy. |
| `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookSleepTimerSheet.kt` | Modify | Delete the `SleepTimerChoice` declaration; import it from `com.continuum.app.common.player`. |
| `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerScreen.kt` | Modify | Add `import com.continuum.app.common.player.AudiobookPlayerViewModel`. (`SleepTimerChoice` was same-package; now imported — but the screen does not name it directly, only via `viewModel::applySleepTimer`, so verify the build.) |
| `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt` | Modify | Repoint the `viewModel { ... }` registration to `com.continuum.app.common.player.AudiobookPlayerViewModel`. Constructor args unchanged. |

---

### Task 1: Pure `AudiobookChapters` in `shared` (TDD)

Pure chapter math, no Android types. Models a chapter as `(startSeconds, endSeconds)` derived from the server `VersionChapter` (`shared/.../model/catalog/CatalogModels.kt:200`, fields `startSeconds: Double`, `endSeconds: Double`, `index: Int`, `title: String`). The util takes plain `Double` start times so it stays free of the serialization model and is reusable by Apple later.

**Files:**
- Create: `shared/src/commonMain/kotlin/com/continuum/app/audiobook/AudiobookChapters.kt`
- Test: `shared/src/commonTest/kotlin/com/continuum/app/audiobook/AudiobookChaptersTest.kt`

Steps:

- [ ] Write the failing test first. Create `shared/src/commonTest/kotlin/com/continuum/app/audiobook/AudiobookChaptersTest.kt`:

  ```kotlin
  package com.continuum.app.audiobook

  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertNull

  class AudiobookChaptersTest {

      // Three chapters: [0,100), [100,250), [250,400)
      private val chapters = listOf(
          AudiobookChapter(startSeconds = 0.0, endSeconds = 100.0),
          AudiobookChapter(startSeconds = 100.0, endSeconds = 250.0),
          AudiobookChapter(startSeconds = 250.0, endSeconds = 400.0),
      )

      @Test
      fun `current index within a chapter`() {
          assertEquals(0, AudiobookChapters.currentIndex(chapters, 0.0))
          assertEquals(0, AudiobookChapters.currentIndex(chapters, 50.0))
          assertEquals(1, AudiobookChapters.currentIndex(chapters, 150.0))
          assertEquals(2, AudiobookChapters.currentIndex(chapters, 399.9))
      }

      @Test
      fun `position exactly on a boundary belongs to the later chapter`() {
          // 100.0 is the end of ch0 and the start of ch1 -> ch1 (start-inclusive).
          assertEquals(1, AudiobookChapters.currentIndex(chapters, 100.0))
          assertEquals(2, AudiobookChapters.currentIndex(chapters, 250.0))
      }

      @Test
      fun `position past the last chapter end clamps to last index`() {
          assertEquals(2, AudiobookChapters.currentIndex(chapters, 500.0))
      }

      @Test
      fun `negative position clamps to first index`() {
          assertEquals(0, AudiobookChapters.currentIndex(chapters, -5.0))
      }

      @Test
      fun `empty chapters degrade to index 0`() {
          assertEquals(0, AudiobookChapters.currentIndex(emptyList(), 42.0))
      }

      @Test
      fun `chapter progress is position within the current chapter`() {
          // ch1 = [100,250), span 150. At 175 -> (175-100)/150 = 0.5
          assertEquals(0.5, AudiobookChapters.chapterProgress(chapters, 175.0), 1e-9)
          assertEquals(0.0, AudiobookChapters.chapterProgress(chapters, 100.0), 1e-9)
      }

      @Test
      fun `chapter progress clamps to 0_1 outside bounds`() {
          assertEquals(0.0, AudiobookChapters.chapterProgress(chapters, -10.0), 1e-9)
          assertEquals(1.0, AudiobookChapters.chapterProgress(chapters, 500.0), 1e-9)
      }

      @Test
      fun `chapter progress of a zero-length chapter is 0`() {
          val degenerate = listOf(AudiobookChapter(10.0, 10.0))
          assertEquals(0.0, AudiobookChapters.chapterProgress(degenerate, 10.0), 1e-9)
      }

      @Test
      fun `count label is one-based current of total`() {
          assertEquals("Chapter 2 of 3", AudiobookChapters.countLabel(chapters, 150.0))
          assertEquals("Chapter 1 of 3", AudiobookChapters.countLabel(chapters, 0.0))
      }

      @Test
      fun `count label degrades to null for single or empty chapters`() {
          assertNull(AudiobookChapters.countLabel(emptyList(), 0.0))
          assertNull(AudiobookChapters.countLabel(listOf(AudiobookChapter(0.0, 100.0)), 10.0))
      }

      @Test
      fun `next chapter target is the next chapter start`() {
          assertEquals(100.0, AudiobookChapters.nextChapterTarget(chapters, 50.0))
          assertEquals(250.0, AudiobookChapters.nextChapterTarget(chapters, 150.0))
      }

      @Test
      fun `next chapter target on the last chapter stays at last chapter start`() {
          assertEquals(250.0, AudiobookChapters.nextChapterTarget(chapters, 300.0))
      }

      @Test
      fun `previous chapter restarts current when more than 3s in`() {
          // In ch1 (start 100) at 110 -> 10s in -> restart ch1 at 100.0
          assertEquals(100.0, AudiobookChapters.previousChapterTarget(chapters, 110.0))
      }

      @Test
      fun `previous chapter goes to prior chapter when 3s or less in`() {
          // In ch1 (start 100) at 102 -> 2s in -> previous chapter ch0 start 0.0
          assertEquals(0.0, AudiobookChapters.previousChapterTarget(chapters, 102.0))
          // Exactly 3s in -> still "within threshold" -> previous chapter.
          assertEquals(0.0, AudiobookChapters.previousChapterTarget(chapters, 103.0))
      }

      @Test
      fun `previous chapter on the first chapter restarts at its start`() {
          // In ch0 (start 0) at 1.0 -> 1s in (<=3) but no prior chapter -> 0.0
          assertEquals(0.0, AudiobookChapters.previousChapterTarget(chapters, 1.0))
          // In ch0 at 50 -> >3s in -> restart ch0 at 0.0
          assertEquals(0.0, AudiobookChapters.previousChapterTarget(chapters, 50.0))
      }

      @Test
      fun `navigation targets degrade safely for empty chapters`() {
          assertEquals(0.0, AudiobookChapters.nextChapterTarget(emptyList(), 5.0))
          assertEquals(0.0, AudiobookChapters.previousChapterTarget(emptyList(), 5.0))
      }
  }
  ```

- [ ] Run the test and confirm it FAILS to compile (the symbols don't exist yet):

  ```
  ./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.audiobook.AudiobookChaptersTest"
  ```

  Expected: compilation error — `Unresolved reference: AudiobookChapter` / `AudiobookChapters`.

- [ ] Write the minimal real implementation. Create `shared/src/commonMain/kotlin/com/continuum/app/audiobook/AudiobookChapters.kt`:

  ```kotlin
  package com.continuum.app.audiobook

  /**
   * Platform-agnostic view of one audiobook chapter: a half-open
   * [startSeconds, endSeconds) span on the playable file. Built from the
   * server's `VersionChapter` (com.continuum.app.model.catalog.VersionChapter)
   * but kept free of the serialization model so this math is reusable by the
   * Apple clients and unit-testable in commonTest with no Android deps.
   */
  data class AudiobookChapter(
      val startSeconds: Double,
      val endSeconds: Double,
  )

  /**
   * Pure chapter math for the audiobook player. The Android
   * `AudiobookPlayerViewModel` delegates here so chapter logic is shared and
   * tested once. Position is "current playback position in seconds"; chapters
   * are assumed sorted ascending by start (the server normalizes/sorts them).
   *
   * Boundary rule: a chapter owns `[startSeconds, endSeconds)`. A position
   * exactly on a boundary belongs to the *later* chapter (start-inclusive).
   * Degrade rules: empty list -> index 0, progress 0, no count label; a single
   * chapter hides the count label (chapter-only chrome is suppressed by the UI).
   */
  object AudiobookChapters {

      /** Threshold (seconds) for the "restart current chapter" prev rule. */
      const val PREV_RESTART_THRESHOLD_SECONDS = 3.0

      /**
       * Index of the chapter containing [positionSeconds]. Clamps below the
       * first chapter to 0 and at/after the last chapter end to the last index.
       * Empty list degrades to 0.
       */
      fun currentIndex(chapters: List<AudiobookChapter>, positionSeconds: Double): Int {
          if (chapters.isEmpty()) return 0
          // Last chapter whose start is <= position (start-inclusive boundary).
          var idx = 0
          for (i in chapters.indices) {
              if (positionSeconds >= chapters[i].startSeconds) idx = i else break
          }
          return idx
      }

      /** The current chapter, or null when there are none. */
      fun currentChapter(
          chapters: List<AudiobookChapter>,
          positionSeconds: Double,
      ): AudiobookChapter? =
          chapters.getOrNull(currentIndex(chapters, positionSeconds))

      /**
       * Progress within the current chapter in 0..1. Zero-length and
       * out-of-range positions clamp; empty list returns 0.
       */
      fun chapterProgress(chapters: List<AudiobookChapter>, positionSeconds: Double): Double {
          val ch = currentChapter(chapters, positionSeconds) ?: return 0.0
          val span = ch.endSeconds - ch.startSeconds
          if (span <= 0.0) return 0.0
          val raw = (positionSeconds - ch.startSeconds) / span
          return raw.coerceIn(0.0, 1.0)
      }

      /**
       * "Chapter N of M" label, one-based. Null when there are 0 or 1 chapters
       * (single-chapter / chapterless books hide the chapter header).
       */
      fun countLabel(chapters: List<AudiobookChapter>, positionSeconds: Double): String? {
          if (chapters.size < 2) return null
          val n = currentIndex(chapters, positionSeconds) + 1
          return "Chapter $n of ${chapters.size}"
      }

      /**
       * Seek target (seconds) for "next chapter": the start of the chapter
       * after the current one, or — when already on the last chapter — the
       * current chapter's start (no-op-ish clamp). Empty list -> 0.
       */
      fun nextChapterTarget(chapters: List<AudiobookChapter>, positionSeconds: Double): Double {
          if (chapters.isEmpty()) return 0.0
          val current = currentIndex(chapters, positionSeconds)
          val target = (current + 1).coerceAtMost(chapters.lastIndex)
          return chapters[target].startSeconds
      }

      /**
       * Seek target (seconds) for "previous chapter", standard audiobook
       * behavior: if more than [PREV_RESTART_THRESHOLD_SECONDS] into the
       * current chapter, restart the current chapter; otherwise jump to the
       * previous chapter's start. On the first chapter, always its start.
       * Empty list -> 0.
       */
      fun previousChapterTarget(chapters: List<AudiobookChapter>, positionSeconds: Double): Double {
          if (chapters.isEmpty()) return 0.0
          val current = currentIndex(chapters, positionSeconds)
          val currentStart = chapters[current].startSeconds
          val intoChapter = positionSeconds - currentStart
          return if (intoChapter > PREV_RESTART_THRESHOLD_SECONDS || current == 0) {
              currentStart
          } else {
              chapters[current - 1].startSeconds
          }
      }
  }
  ```

- [ ] Run the test and confirm it PASSES:

  ```
  ./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.audiobook.AudiobookChaptersTest"
  ```

  Expected: `BUILD SUCCESSFUL`, all `AudiobookChaptersTest` cases green.

- [ ] Commit:

  ```
  git add shared/src/commonMain/kotlin/com/continuum/app/audiobook/AudiobookChapters.kt \
          shared/src/commonTest/kotlin/com/continuum/app/audiobook/AudiobookChaptersTest.kt
  git commit -m "feat(audiobook): add pure AudiobookChapters chapter math to shared"
  ```

---

### Task 2: Relocate `SleepTimerChoice` to `android-shared`

The VM references `SleepTimerChoice`, currently declared inside `AudiobookSleepTimerSheet.kt` (`androidApp`, lines 34-38). It is a plain UI-agnostic sealed class. Moving the VM to `android-shared` would create a dependency from `android-shared` -> `androidApp` (illegal). So move `SleepTimerChoice` to `android-shared` first; the sheet imports it from there.

**Files:**
- Create: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/SleepTimerChoice.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookSleepTimerSheet.kt`

Steps:

- [ ] Create `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/SleepTimerChoice.kt` with the moved declaration (identical body, new package + kdoc preserved):

  ```kotlin
  package com.continuum.app.common.player

  /**
   * Sleep timer choices. [EndOfChapter] is a special value the VM
   * resolves to a duration based on the current chapter's remaining
   * time; [Off] cancels any active timer.
   */
  sealed class SleepTimerChoice {
      data object Off : SleepTimerChoice()
      data class Minutes(val minutes: Int) : SleepTimerChoice()
      data object EndOfChapter : SleepTimerChoice()
  }
  ```

- [ ] In `androidApp/.../audiobook/AudiobookSleepTimerSheet.kt`, delete the local `sealed class SleepTimerChoice { ... }` block (lines 29-38, kdoc + declaration) and add the import. Replace:

  ```kotlin
  import androidx.compose.ui.unit.dp

  /**
   * Sleep timer choices. [EndOfChapter] is a special value the VM
   * resolves to a duration based on the current chapter's remaining
   * time; [Off] cancels any active timer.
   */
  sealed class SleepTimerChoice {
      data object Off : SleepTimerChoice()
      data class Minutes(val minutes: Int) : SleepTimerChoice()
      data object EndOfChapter : SleepTimerChoice()
  }
  ```

  with:

  ```kotlin
  import androidx.compose.ui.unit.dp
  import com.continuum.app.common.player.SleepTimerChoice
  ```

  (Keep the rest of the file unchanged; `SleepTimerChoice.Off` / `.Minutes` / `.EndOfChapter` references now resolve via the import.)

- [ ] Compile-verify the phone app still builds with the moved type (the VM has not moved yet, so it still references the androidApp package — this step proves only the sheet move is clean):

  ```
  ./gradlew :androidApp:compileDebugKotlin
  ```

  Expected: `BUILD SUCCESSFUL`. (At this point `AudiobookPlayerViewModel.kt` in androidApp still uses `SleepTimerChoice` — now resolved via the same-package re-export? No: it is no longer same-package. The VM file is in `...screens.audiobook` and `SleepTimerChoice` moved out of that package. Add the import in the next sub-step before compiling.)

- [ ] Before running the compile above, add the import to the *current* androidApp VM so it still builds in this interim step. In `androidApp/.../audiobook/AudiobookPlayerViewModel.kt`, add under the existing imports:

  ```kotlin
  import com.continuum.app.common.player.SleepTimerChoice
  ```

  Then run `./gradlew :androidApp:compileDebugKotlin` and confirm `BUILD SUCCESSFUL`.

- [ ] Commit:

  ```
  git add android-shared/src/androidMain/kotlin/com/continuum/app/common/player/SleepTimerChoice.kt \
          androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookSleepTimerSheet.kt \
          androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerViewModel.kt
  git commit -m "refactor(audiobook): move SleepTimerChoice into android-shared player package"
  ```

---

### Task 3: Add the lifecycle-viewmodel dependency to `android-shared`

The relocated VM extends `androidx.lifecycle.ViewModel` and uses `viewModelScope` + `SavedStateHandle`. `shared` declares `lifecycle.viewmodel.kmp` as `implementation` (not `api`, see `shared/build.gradle.kts:27`), so it is NOT transitively visible to `android-shared`. Add it explicitly before moving the VM, or the move will fail to compile.

**Files:**
- Modify: `android-shared/build.gradle.kts`

Steps:

- [ ] In `android-shared/build.gradle.kts`, inside `androidMain.dependencies { ... }`, add after the `// DI` block (the `koin.android` line):

  ```kotlin
              // Lifecycle ViewModel (KMP). The audiobook ViewModel lives here so
              // both the phone and TV apps can consume it; `shared` pulls this in
              // only as `implementation`, so it is not transitively visible.
              implementation(libs.lifecycle.viewmodel.kmp)
  ```

- [ ] Verify the module still configures and compiles:

  ```
  ./gradlew :android-shared:compileDebugKotlin
  ```

  Expected: `BUILD SUCCESSFUL` (no source change yet — this only confirms the new dependency resolves).

- [ ] Commit:

  ```
  git add android-shared/build.gradle.kts
  git commit -m "build(android-shared): add lifecycle-viewmodel for relocated audiobook VM"
  ```

---

### Task 4: Relocate `AudiobookPlayerViewModel` into `android-shared` and delegate chapter math

Move the VM file from `androidApp/.../ui/screens/audiobook/` to `android-shared/.../common/player/`, change its package, and replace the two inline chapter-math sites with calls into `AudiobookChapters`. No behavior change. All of the VM's collaborators (`CatalogRepository`, `ProfileRepository`, `ServerRegistry` in `shared`; `PlaybackSessionManager`, `PlaybackCapabilityDetector`, `OfflineMediaResolver`, `DownloadEnqueuer`, `AudiobookBookmarksStore`, `AudiobookPositionStore` in `android-shared`) are already visible from `android-shared`, so no other imports change except the package line and the new `AudiobookChapters` import.

**Files:**
- Create: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/AudiobookPlayerViewModel.kt`
- Delete: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerViewModel.kt`

Steps:

- [ ] Move the file with git (preserves history), then edit the moved copy:

  ```
  git mv androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerViewModel.kt \
         android-shared/src/androidMain/kotlin/com/continuum/app/common/player/AudiobookPlayerViewModel.kt
  ```

- [ ] Change the package declaration at the top of the moved file from:

  ```kotlin
  package com.continuum.app.android.ui.screens.audiobook
  ```

  to:

  ```kotlin
  package com.continuum.app.common.player
  ```

- [ ] Update the imports block. Remove the now-redundant `SleepTimerChoice` import added in Task 2 (it is same-package now), remove the three already-present same-package `com.continuum.app.common.player.*` imports if any become redundant, and add the `AudiobookChapters` import. Specifically:
  - Remove: `import com.continuum.app.common.player.PlaybackCapabilityDetector`, `import com.continuum.app.common.player.PlaybackSessionManager`, `import com.continuum.app.common.player.resolvePlaybackStreamUrl` (these are now same-package — keep the function import `resolvePlaybackStreamUrl` only if it is a top-level function in a different file of the same package; same-package top-level declarations need no import, so remove it).
  - Remove: `import com.continuum.app.common.player.SleepTimerChoice` (same package now).
  - Add (alphabetically with the other `com.continuum.app` imports):

    ```kotlin
    import com.continuum.app.audiobook.AudiobookChapter
    import com.continuum.app.audiobook.AudiobookChapters
    ```

- [ ] Replace the inline "current chapter" math in `applySleepTimer` (the `SleepTimerChoice.EndOfChapter` branch). Replace:

  ```kotlin
              SleepTimerChoice.EndOfChapter -> {
                  val state = _uiState.value
                  val chapter = state.chapters.firstOrNull {
                      state.positionSeconds >= it.startSeconds && state.positionSeconds < it.endSeconds
                  } ?: state.chapters.lastOrNull()
                  val remaining = chapter?.let { it.endSeconds - state.positionSeconds }
                      ?.toInt()?.coerceAtLeast(60) ?: (15 * 60)
                  remaining
              }
  ```

  with the `AudiobookChapters`-delegated form (same result: remaining seconds to the current chapter's end, floored at 60, default 15 min when no chapters):

  ```kotlin
              SleepTimerChoice.EndOfChapter -> {
                  val state = _uiState.value
                  val chapter = AudiobookChapters.currentChapter(
                      state.chapters.toAudiobookChapters(),
                      state.positionSeconds,
                  )
                  val remaining = chapter?.let { it.endSeconds - state.positionSeconds }
                      ?.toInt()?.coerceAtLeast(60) ?: (15 * 60)
                  remaining
              }
  ```

- [ ] Replace the inline "current chapter" math in `addBookmark`. Replace:

  ```kotlin
          val chapter = state.chapters.firstOrNull {
              state.positionSeconds >= it.startSeconds && state.positionSeconds < it.endSeconds
          } ?: state.chapters.lastOrNull()?.takeIf { state.positionSeconds >= it.startSeconds }

          val bookmark = AudiobookBookmark(
              id = generateBookmarkId(),
              positionSeconds = state.positionSeconds,
              chapterTitle = chapter?.title,
  ```

  with an index-based lookup so the resolved `VersionChapter.title` is preserved (note: `AudiobookChapter` carries no title, so use `currentIndex` to look back into the original `VersionChapter` list):

  ```kotlin
          val chapterIndex = AudiobookChapters.currentIndex(
              state.chapters.toAudiobookChapters(),
              state.positionSeconds,
          )
          val chapter = state.chapters.getOrNull(chapterIndex)
              ?.takeIf { state.positionSeconds >= it.startSeconds }

          val bookmark = AudiobookBookmark(
              id = generateBookmarkId(),
              positionSeconds = state.positionSeconds,
              chapterTitle = chapter?.title,
  ```

- [ ] Replace the `jumpToChapter` body to keep using the model directly (no change needed — it already seeks to `chapter.startSeconds`), and confirm it stays:

  ```kotlin
      fun jumpToChapter(chapter: VersionChapter) {
          seekTo(chapter.startSeconds)
      }
  ```

- [ ] Add a private mapping extension at the bottom of the file (inside the file, top-level, after the class) translating the serialization model to the pure model used by `AudiobookChapters`:

  ```kotlin
  /** Project the server chapter list onto the pure [AudiobookChapter] span
   *  model that [AudiobookChapters] math operates on. */
  private fun List<VersionChapter>.toAudiobookChapters(): List<AudiobookChapter> =
      map { AudiobookChapter(startSeconds = it.startSeconds, endSeconds = it.endSeconds) }
  ```

- [ ] Compile-verify `android-shared`:

  ```
  ./gradlew :android-shared:compileDebugKotlin
  ```

  Expected: `BUILD SUCCESSFUL`. If it reports unresolved `resolvePlaybackStreamUrl` or `PlaybackSessionManager`, confirm they are top-level / same-package in `com.continuum.app.common.player` and remove their now-redundant imports (or restore an import if a symbol is actually in a different package).

- [ ] Commit:

  ```
  git add android-shared/src/androidMain/kotlin/com/continuum/app/common/player/AudiobookPlayerViewModel.kt \
          androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerViewModel.kt
  git commit -m "refactor(audiobook): relocate AudiobookPlayerViewModel to android-shared and delegate chapter math"
  ```

---

### Task 5: Repoint the Koin registration and the phone screen import

The Koin module and the phone screen still reference the old package. Update both so the phone app resolves the relocated VM. Constructor arguments are unchanged, so the `viewModel { ... }` body is identical apart from the type's package.

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerScreen.kt`

Steps:

- [ ] In `androidApp/.../di/AndroidModule.kt`, repoint the audiobook VM registration. Replace:

  ```kotlin
      viewModel {
          com.continuum.app.android.ui.screens.audiobook.AudiobookPlayerViewModel(
              catalogRepository = get(),
              playbackSessionManager = get(),
              capabilityDetector = get(),
              bookmarksStore = get(),
              positionStore = get(),
              serverRegistry = get(),
              profileRepository = get(),
              offlineMediaResolver = get(),
              savedStateHandle = get(),
          )
      }
  ```

  with:

  ```kotlin
      viewModel {
          com.continuum.app.common.player.AudiobookPlayerViewModel(
              catalogRepository = get(),
              playbackSessionManager = get(),
              capabilityDetector = get(),
              bookmarksStore = get(),
              positionStore = get(),
              serverRegistry = get(),
              profileRepository = get(),
              offlineMediaResolver = get(),
              savedStateHandle = get(),
          )
      }
  ```

- [ ] In `androidApp/.../audiobook/AudiobookPlayerScreen.kt`, add the import for the relocated VM (the screen references `AudiobookPlayerViewModel` at the `viewModel: AudiobookPlayerViewModel = koinViewModel()` parameter; it was same-package before). Add alongside the existing `com.continuum.app.common.player.ContinuumPlaybackService` import:

  ```kotlin
  import com.continuum.app.common.player.AudiobookPlayerViewModel
  ```

- [ ] Confirm the screen does not directly name `AudiobookPlayerUiState` or `SleepTimerChoice` without an import. `AudiobookPlayerUiState` is declared in the moved VM file (now package `com.continuum.app.common.player`); if the screen references it directly, add `import com.continuum.app.common.player.AudiobookPlayerUiState`. The screen passes `viewModel::applySleepTimer` to `AudiobookSleepTimerSheet`, which already imports `SleepTimerChoice` from `android-shared` (Task 2), so no `SleepTimerChoice` import is needed in the screen. Verify with the compile step below and add the import only if the compiler flags it.

- [ ] Compile-verify the phone app:

  ```
  ./gradlew :androidApp:compileDebugKotlin
  ```

  Expected: `BUILD SUCCESSFUL`.

- [ ] Run the full unit-test suites for both touched modules to prove no regression and that the new math is wired:

  ```
  ./gradlew :shared:testDebugUnitTest :android-shared:testDebugUnitTest
  ```

  Expected: `BUILD SUCCESSFUL`; `AudiobookChaptersTest` green; existing android-shared tests still green.

- [ ] Commit:

  ```
  git add androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt \
          androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerScreen.kt
  git commit -m "refactor(audiobook): point phone screen and Koin DI at relocated VM"
  ```

---

### Task 6: Final verification pass

Prove the whole Phase 1 slice assembles and lints, with no behavior change to the phone player.

**Files:** none (verification only).

Steps:

- [ ] Assemble the phone app to catch any cross-module wiring issues the per-module compile missed:

  ```
  ./gradlew :androidApp:assembleDebug
  ```

  Expected: `BUILD SUCCESSFUL`.

- [ ] Run lint/format as the repo requires before a merge request (mirrors `make lint`):

  ```
  ./gradlew ktlintCheck
  ```

  (If the project uses a different lint task, run the one configured in the root `build.gradle.kts`; expected: no new violations in the touched files.)

- [ ] Self-check the moved VM has NO remaining reference to the old `com.continuum.app.android.ui.screens.audiobook` package and no leftover inline `firstOrNull { ... startSeconds ... endSeconds }` chapter scans:

  ```
  /usr/bin/grep -n "startSeconds && .*endSeconds" android-shared/src/androidMain/kotlin/com/continuum/app/common/player/AudiobookPlayerViewModel.kt || echo "no inline chapter scans remain"
  ```

  Expected: `no inline chapter scans remain`.

- [ ] Confirm the old VM file is gone and nothing still imports it:

  ```
  /usr/bin/grep -rn "ui.screens.audiobook.AudiobookPlayerViewModel" androidApp android-shared || echo "no stale references"
  ```

  Expected: `no stale references`.

---

## Self-review vs. the spec

- **§4.0 / §6.1 "pure `AudiobookChapters` in `shared` with `commonTest`":** Task 1 — created in `shared/commonMain`, no Android deps, fully covered in `commonTest` (boundaries, exactly-on-boundary, empty/one-chapter degrade, >3s prev rule, progress clamp, count label). ✔
- **§4.2 chapter computation (currentChapterIndex, chapterProgress, chapterCountLabel):** `currentIndex`, `chapterProgress`, `countLabel` cover these; the VM now derives them via `AudiobookChapters`. ✔ The spec also lists `skipToPreviousChapter()` / `skipToNextChapter()` and end-of-chapter sleep *watcher* as VM responsibilities — those are **Phase 2+ behavior additions** (new transport + watcher), out of scope for Phase 1's "no behavior change" relocation. The pure targets (`nextChapterTarget` / `previousChapterTarget`) are added now so Phase 2 can wire them with zero new math. Deferred intentionally; noted below.
- **§4.2 / §6.1 relocate the VM to `android-shared`, update Koin + phone import, no behavior change, delegate chapter math:** Tasks 2–5 — VM moved, `SleepTimerChoice` moved to keep the module boundary legal, lifecycle dep added, Koin + screen repointed, two inline chapter sites delegated to `AudiobookChapters` with identical results. ✔
- **Module boundary correctness:** Verified every VM collaborator already lives in `shared` or `android-shared`; the only androidApp-local symbol was `SleepTimerChoice`, handled in Task 2. ✔
- **Test task names:** Both `shared` and `android-shared` are androidTarget-only, so `:shared:testDebugUnitTest` and `:android-shared:testDebugUnitTest` are the correct unit-test tasks. ✔

**Deferred to later phases (not Phase 1):** `skipToPreviousChapter()` / `skipToNextChapter()` transport methods and the end-of-chapter sleep *watcher* (Phase 2/4 behavior), the phone UI decomposition (Phase 2), the TV player (Phase 3), `AudiobookSettingsStore` (Phase 4). The pure navigation targets are pre-built here so those phases add no new chapter math.
