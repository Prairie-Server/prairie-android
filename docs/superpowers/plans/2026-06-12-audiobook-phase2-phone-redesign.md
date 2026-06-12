# Audiobook Player — Phase 2: Phone Player Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Decompose the monolithic single-`Column` phone audiobook player (`AudiobookPlayerScreen.kt`) into focused composables, move the always-inline chapter list into a `ChaptersSheet` bottom sheet (current-chapter highlight + auto-scroll on open), add a current-chapter header and a book↔chapter progress bar, and add a five-control transport row (prev-chapter, skip-back, play/pause, skip-forward, next-chapter). This is the Phase 2 deliverable from `docs/superpowers/specs/2026-06-12-audiobook-player-redesign-design.md` §4.1 and §6.2.

**Architecture:** Phone-only, `androidApp`. Phase 1 is assumed shipped: the pure chapter math `AudiobookChapters` lives in `:shared` (commonMain), and `AudiobookPlayerViewModel` has been relocated to `:android-shared` at package `com.continuum.app.common.player`, where it now exposes `currentChapterIndex`, `chapterProgress`, `chapterCountLabel`, `skipToPreviousChapter()`, and `skipToNextChapter()`. Phase 2 consumes those exact members and does **not** change VM behavior — it only restructures the Compose UI. New composables all land under the existing package `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/`, mirroring the patterns in the sibling sheets (`AudiobookBookmarksSheet`, `AudiobookSpeedSheet`, `AudiobookSleepTimerSheet`).

**Tech Stack:** Kotlin Multiplatform (androidTarget), Jetpack Compose Material3, Media3 `MediaController` (wiring stays in the screen, unchanged), Koin (`koinViewModel`), JUnit4 via `kotlin("test-junit")` for the one pure-logic unit test. Module test task: `./gradlew :androidApp:testDebugUnitTest`.

---

## File Structure

Created:

- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookCoverHeader.kt` — composable: hero cover (`ThumbhashImage`), title, author, narrator. Pulled out of the screen's top block.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/ChapterProgressBar.kt` — composable: current-chapter header ("Chapter N of M"), a `Slider` showing either book-relative or chapter-relative progress, a book↔chapter toggle, and the two time labels. Also the pure `chapterRelativeSeconds`/`chapterRelativeDuration` helpers that the unit test covers.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookTransport.kt` — composable: the five-control transport row (prev-chapter, skip-back-30, play/pause, skip-forward-30, next-chapter).
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookSecondaryBar.kt` — composable: speed chip, sleep chip, chapters button. Hosts the shared `ChipButton`.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/ChaptersSheet.kt` — `ModalBottomSheet` with a `LazyColumn` of chapters, current-chapter highlight, and auto-scroll to current on open. Replaces the inline chapter `Column`.
- `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/audiobook/ChapterProgressMathTest.kt` — JUnit4 test for the chapter-relative progress helpers in `ChapterProgressBar.kt`.

Modified:

- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerScreen.kt` — update VM import to `com.continuum.app.common.player.AudiobookPlayerViewModel`; remove the inline cover/title block, inline slider+labels, three-button transport row, and inline chapter `Column`; replace with calls to the new composables; add the `ChaptersSheet` toggle + button; delete the now-unused private `ChipButton`/`formatSpeed` (relocated to `AudiobookSecondaryBar.kt`).

Unchanged (referenced): `AudiobookSpeedSheet.kt`, `AudiobookSleepTimerSheet.kt`, `AudiobookBookmarksSheet.kt`, `AudiobookCoverHeader` consumes `ThumbhashImage` from `com.continuum.app.common.ui.components`, `formatClockTime` from `com.continuum.app.android.ui.util`.

---

### Task 1 — Update screen to consume the relocated VM (Phase-1 follow-through)

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerScreen.kt`

This is a tiny, required bridge: Phase 1 moved the VM out of `androidApp`. Confirm the screen imports it from its new home so the rest of Phase 2 compiles.

- [ ] In `AudiobookPlayerScreen.kt`, ensure the VM is imported from `android-shared`. If an `import com.continuum.app.android.ui.screens.audiobook.AudiobookPlayerViewModel` (same-package, implicit) reference exists, replace the type's resolution by adding:
  ```kotlin
  import com.continuum.app.common.player.AudiobookPlayerViewModel
  ```
  (The `SleepTimerChoice` sealed class stays in `AudiobookSleepTimerSheet.kt` in this package, so leave that import implicit.)
- [ ] Verify the `AudiobookPlayerUiState` type referenced by `state` is also resolved from `com.continuum.app.common.player` (Phase 1 moved it with the VM). Add `import com.continuum.app.common.player.AudiobookPlayerUiState` only if the screen names the type explicitly; the `state by viewModel.uiState.collectAsState()` site infers it and needs no import.
- [ ] Build to confirm the relocation compiles before touching UI:
  ```bash
  ./gradlew :androidApp:compileDebugKotlinAndroid
  ```
- [ ] Commit:
  ```bash
  git add -A && git commit -m "refactor(audiobook): point phone screen at relocated VM"
  ```

Unit tests are impractical here (import-only change verified by the compiler). Manual verification is deferred to Task 7's end-to-end pass.

---

### Task 2 — Extract `AudiobookCoverHeader`

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookCoverHeader.kt` (new)
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerScreen.kt` (modified)

Pure presentational composable — no logic to TDD. Manual verify in Task 7.

- [ ] Create `AudiobookCoverHeader.kt`:
  ```kotlin
  package com.continuum.app.android.ui.screens.audiobook

  import androidx.compose.foundation.background
  import androidx.compose.foundation.layout.Box
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.Spacer
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.foundation.layout.height
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.layout.size
  import androidx.compose.foundation.shape.RoundedCornerShape
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.Text
  import androidx.compose.runtime.Composable
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.draw.clip
  import androidx.compose.ui.text.font.FontWeight
  import androidx.compose.ui.text.style.TextAlign
  import androidx.compose.ui.text.style.TextOverflow
  import androidx.compose.ui.unit.dp
  import com.continuum.app.common.ui.components.ThumbhashImage

  /**
   * Hero cover + title / author / narrator. Pulled out of the monolith
   * screen; purely presentational, driven by VM ui-state fields.
   */
  @Composable
  fun AudiobookCoverHeader(
      title: String,
      author: String?,
      narrator: String?,
      coverUrl: String?,
      coverThumbhash: String?,
      modifier: Modifier = Modifier,
  ) {
      Column(modifier = modifier.fillMaxWidth()) {
          Box(
              modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 24.dp),
              contentAlignment = Alignment.Center,
          ) {
              ThumbhashImage(
                  url = coverUrl,
                  thumbhash = coverThumbhash,
                  contentDescription = title,
                  modifier = Modifier
                      .size(280.dp)
                      .clip(RoundedCornerShape(12.dp))
                      .background(MaterialTheme.colorScheme.surfaceVariant),
              )
          }

          Spacer(modifier = Modifier.height(24.dp))

          Text(
              text = title,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.SemiBold,
              textAlign = TextAlign.Center,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.fillMaxWidth(),
          )
          author?.takeIf { it.isNotBlank() }?.let {
              Text(
                  text = it,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center,
                  modifier = Modifier.fillMaxWidth(),
              )
          }
          narrator?.takeIf { it.isNotBlank() }?.let {
              Text(
                  text = "Narrated by $it",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center,
                  modifier = Modifier.fillMaxWidth(),
              )
          }
      }
  }
  ```
- [ ] In `AudiobookPlayerScreen.kt`, replace the inline cover `Box { ThumbhashImage(...) }` + the title `Text` + author `Text` block (between the bookmark row's trailing `Spacer(height = 8.dp)` and the position-slider `Spacer(height = 20.dp)`) with:
  ```kotlin
  AudiobookCoverHeader(
      title = state.title,
      author = state.author,
      narrator = state.narrator,
      coverUrl = state.coverUrl,
      coverThumbhash = state.coverThumbhash,
  )
  ```
- [ ] Remove the now-unused imports from `AudiobookPlayerScreen.kt` only if nothing else uses them. `RoundedCornerShape`, `clip`, `size`, `TextAlign`, `TextOverflow`, `FontWeight` are still used by other inline blocks at this stage — do NOT remove them yet; defer import cleanup to Task 6 after all blocks are extracted.
- [ ] Build:
  ```bash
  ./gradlew :androidApp:compileDebugKotlinAndroid
  ```
- [ ] Commit:
  ```bash
  git add -A && git commit -m "refactor(audiobook): extract AudiobookCoverHeader composable"
  ```

---

### Task 3 — Add `ChapterProgressBar` with TDD'd progress math

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/ChapterProgressBar.kt` (new)
- `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/audiobook/ChapterProgressMathTest.kt` (new)
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerScreen.kt` (modified)

The chapter-relative slider needs pure math (position within a chapter, and that chapter's length). That math is testable without Compose, so TDD it. The Compose wrapper is verified manually.

The VM already exposes `currentChapterIndex: StateFlow<Int>` and `chapterCountLabel: StateFlow<String>` (Phase 1, e.g. "Chapter 7 of 111"); `chapterProgress: StateFlow<Float>` is the book-agnostic chapter fraction. For the chapter-relative slider we additionally need absolute seconds within the current chapter, computed from `state.chapters` + `state.positionSeconds`, which we keep local and pure.

- [ ] Write the failing test first. Create `ChapterProgressMathTest.kt`:
  ```kotlin
  package com.continuum.app.android.ui.screens.audiobook

  import com.continuum.app.model.catalog.VersionChapter
  import kotlin.test.Test
  import kotlin.test.assertEquals

  class ChapterProgressMathTest {
      private val chapters = listOf(
          VersionChapter(index = 0, title = "One", startSeconds = 0.0, endSeconds = 100.0),
          VersionChapter(index = 1, title = "Two", startSeconds = 100.0, endSeconds = 250.0),
          VersionChapter(index = 2, title = "Three", startSeconds = 250.0, endSeconds = 400.0),
      )

      @Test
      fun chapterRelativeSeconds_midChapter() {
          assertEquals(40.0, chapterRelativeSeconds(chapters, currentIndex = 1, positionSeconds = 140.0), 0.001)
      }

      @Test
      fun chapterRelativeSeconds_clampsBelowStart() {
          // Position momentarily behind the chapter start clamps to 0.
          assertEquals(0.0, chapterRelativeSeconds(chapters, currentIndex = 1, positionSeconds = 90.0), 0.001)
      }

      @Test
      fun chapterRelativeDuration_isChapterLength() {
          assertEquals(150.0, chapterRelativeDuration(chapters, currentIndex = 1), 0.001)
      }

      @Test
      fun degradesToWholeBookWhenNoChapters() {
          assertEquals(140.0, chapterRelativeSeconds(emptyList(), currentIndex = 0, positionSeconds = 140.0), 0.001)
          assertEquals(0.0, chapterRelativeDuration(emptyList(), currentIndex = 0), 0.001)
      }

      @Test
      fun outOfRangeIndexIsSafe() {
          assertEquals(0.0, chapterRelativeSeconds(chapters, currentIndex = 9, positionSeconds = 140.0), 0.001)
          assertEquals(0.0, chapterRelativeDuration(chapters, currentIndex = 9), 0.001)
      }
  }
  ```
- [ ] Run it; confirm it fails to compile (helpers not defined yet):
  ```bash
  ./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.audiobook.ChapterProgressMathTest"
  ```
- [ ] Create `ChapterProgressBar.kt` with the pure helpers + the composable:
  ```kotlin
  package com.continuum.app.android.ui.screens.audiobook

  import androidx.compose.foundation.background
  import androidx.compose.foundation.clickable
  import androidx.compose.foundation.layout.Arrangement
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.Row
  import androidx.compose.foundation.layout.Spacer
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.foundation.layout.height
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.shape.RoundedCornerShape
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.Slider
  import androidx.compose.material3.Text
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.getValue
  import androidx.compose.runtime.mutableStateOf
  import androidx.compose.runtime.remember
  import androidx.compose.runtime.saveable.rememberSaveable
  import androidx.compose.runtime.setValue
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.draw.clip
  import androidx.compose.ui.text.font.FontWeight
  import androidx.compose.ui.unit.dp
  import com.continuum.app.android.ui.util.formatClockTime
  import com.continuum.app.model.catalog.VersionChapter

  /** Seconds elapsed within the current chapter, clamped to [0, chapterLength].
   *  Degrades to the whole-book position when there are no chapters. Out-of-range
   *  index yields 0 so a transient bad index never throws. */
  internal fun chapterRelativeSeconds(
      chapters: List<VersionChapter>,
      currentIndex: Int,
      positionSeconds: Double,
  ): Double {
      if (chapters.isEmpty()) return positionSeconds.coerceAtLeast(0.0)
      val chapter = chapters.getOrNull(currentIndex) ?: return 0.0
      val length = (chapter.endSeconds - chapter.startSeconds).coerceAtLeast(0.0)
      return (positionSeconds - chapter.startSeconds).coerceIn(0.0, length)
  }

  /** Length of the current chapter in seconds. 0 when there are no chapters
   *  or the index is out of range. */
  internal fun chapterRelativeDuration(
      chapters: List<VersionChapter>,
      currentIndex: Int,
  ): Double {
      val chapter = chapters.getOrNull(currentIndex) ?: return 0.0
      return (chapter.endSeconds - chapter.startSeconds).coerceAtLeast(0.0)
  }

  /**
   * Current-chapter header + a seek slider that toggles between book-relative
   * and chapter-relative progress. Dragging always seeks an *absolute* book
   * position, so [onSeek] receives book seconds regardless of mode.
   */
  @Composable
  fun ChapterProgressBar(
      chapters: List<VersionChapter>,
      currentChapterIndex: Int,
      chapterCountLabel: String,
      positionSeconds: Double,
      durationSeconds: Double,
      onSeek: (Double) -> Unit,
      modifier: Modifier = Modifier,
  ) {
      val hasChapters = chapters.isNotEmpty()
      // Default to chapter view when chapters exist; survives config change.
      var chapterMode by rememberSaveable(hasChapters) { mutableStateOf(hasChapters) }

      Column(modifier = modifier.fillMaxWidth()) {
          if (hasChapters && chapterCountLabel.isNotBlank()) {
              Text(
                  text = chapterCountLabel,
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.fillMaxWidth(),
              )
              Spacer(modifier = Modifier.height(8.dp))
          }

          val chapter = chapters.getOrNull(currentChapterIndex)
          val relSeconds = chapterRelativeSeconds(chapters, currentChapterIndex, positionSeconds)
          val relDuration = chapterRelativeDuration(chapters, currentChapterIndex)

          val sliderValue: Float
          val sliderMax: Float
          val leftLabel: String
          val rightLabel: String
          if (chapterMode && hasChapters && relDuration > 0.0) {
              sliderMax = relDuration.toFloat().coerceAtLeast(1f)
              sliderValue = relSeconds.toFloat().coerceIn(0f, sliderMax)
              leftLabel = formatClockTime(relSeconds)
              rightLabel = formatClockTime(relDuration)
          } else {
              sliderMax = durationSeconds.toFloat().coerceAtLeast(1f)
              sliderValue = positionSeconds.toFloat().coerceIn(0f, sliderMax)
              leftLabel = formatClockTime(positionSeconds)
              rightLabel = formatClockTime(durationSeconds)
          }

          Slider(
              value = sliderValue,
              valueRange = 0f..sliderMax,
              onValueChange = { v ->
                  // Map the slider's value back to an absolute book position.
                  val book = if (chapterMode && hasChapters && chapter != null && relDuration > 0.0) {
                      chapter.startSeconds + v.toDouble()
                  } else {
                      v.toDouble()
                  }
                  onSeek(book.coerceIn(0.0, durationSeconds.coerceAtLeast(0.0)))
              },
              modifier = Modifier.fillMaxWidth(),
          )
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
          ) {
              Text(leftLabel, style = MaterialTheme.typography.labelSmall)
              if (hasChapters) {
                  Text(
                      text = if (chapterMode) "Chapter" else "Book",
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.SemiBold,
                      color = MaterialTheme.colorScheme.primary,
                      modifier = Modifier
                          .clip(RoundedCornerShape(8.dp))
                          .clickable { chapterMode = !chapterMode }
                          .padding(horizontal = 8.dp, vertical = 2.dp),
                  )
              }
              Text(rightLabel, style = MaterialTheme.typography.labelSmall)
          }
      }
  }
  ```
- [ ] Run the test; confirm green:
  ```bash
  ./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.audiobook.ChapterProgressMathTest"
  ```
- [ ] In `AudiobookPlayerScreen.kt`, collect the new VM chapter flows near the top of the composable (after `val state by viewModel.uiState.collectAsState()`):
  ```kotlin
  val currentChapterIndex by viewModel.currentChapterIndex.collectAsState()
  val chapterCountLabel by viewModel.chapterCountLabel.collectAsState()
  ```
- [ ] Replace the inline position `Slider` + the `Row` of two `formatClockTime` `Text`s (the block bounded by the two `Spacer(height = 20.dp)` around it) with:
  ```kotlin
  ChapterProgressBar(
      chapters = state.chapters,
      currentChapterIndex = currentChapterIndex,
      chapterCountLabel = chapterCountLabel,
      positionSeconds = state.positionSeconds,
      durationSeconds = state.durationSeconds,
      onSeek = { viewModel.seekTo(it) },
  )
  ```
- [ ] Build:
  ```bash
  ./gradlew :androidApp:compileDebugKotlinAndroid
  ```
- [ ] Commit:
  ```bash
  git add -A && git commit -m "feat(audiobook): chapter-aware progress bar with book/chapter toggle"
  ```

---

### Task 4 — Add `AudiobookTransport` (five controls)

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookTransport.kt` (new)
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerScreen.kt` (modified)

Presentational + delegates to VM commands. No pure logic to TDD; manual verify in Task 7. Prev/next-chapter call the Phase-1 VM methods `skipToPreviousChapter()` / `skipToNextChapter()`; the "restart current chapter if >3s in" rule lives in the VM/`AudiobookChapters`, not here.

- [ ] Create `AudiobookTransport.kt`:
  ```kotlin
  package com.continuum.app.android.ui.screens.audiobook

  import androidx.compose.foundation.background
  import androidx.compose.foundation.clickable
  import androidx.compose.foundation.layout.Arrangement
  import androidx.compose.foundation.layout.Box
  import androidx.compose.foundation.layout.Row
  import androidx.compose.foundation.layout.Spacer
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.foundation.layout.size
  import androidx.compose.foundation.shape.CircleShape
  import androidx.compose.material.icons.Icons
  import androidx.compose.material.icons.filled.Forward30
  import androidx.compose.material.icons.filled.Pause
  import androidx.compose.material.icons.filled.PlayArrow
  import androidx.compose.material.icons.filled.Replay30
  import androidx.compose.material.icons.filled.SkipNext
  import androidx.compose.material.icons.filled.SkipPrevious
  import androidx.compose.material3.Icon
  import androidx.compose.material3.IconButton
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.runtime.Composable
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.draw.clip
  import androidx.compose.ui.unit.dp

  /**
   * Five-control audiobook transport: prev-chapter · skip-back · play/pause ·
   * skip-forward · next-chapter. Chapter buttons are hidden (not just disabled)
   * when the book has no chapters, so the layout collapses to the classic
   * skip-back / play / skip-forward triple.
   */
  @Composable
  fun AudiobookTransport(
      isPlaying: Boolean,
      enabled: Boolean,
      hasChapters: Boolean,
      onPrevChapter: () -> Unit,
      onSkipBack: () -> Unit,
      onTogglePlay: () -> Unit,
      onSkipForward: () -> Unit,
      onNextChapter: () -> Unit,
      modifier: Modifier = Modifier,
  ) {
      Row(
          modifier = modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
      ) {
          if (hasChapters) {
              IconButton(
                  onClick = onPrevChapter,
                  enabled = enabled,
                  modifier = Modifier.size(48.dp),
              ) {
                  Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous chapter", modifier = Modifier.size(30.dp))
              }
              Spacer(modifier = Modifier.size(8.dp))
          }
          IconButton(
              onClick = onSkipBack,
              enabled = enabled,
              modifier = Modifier.size(56.dp),
          ) {
              Icon(Icons.Filled.Replay30, contentDescription = "Back 30 seconds", modifier = Modifier.size(36.dp))
          }
          Spacer(modifier = Modifier.size(16.dp))
          Box(
              modifier = Modifier
                  .size(80.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primary)
                  .clickable(enabled = enabled, onClick = onTogglePlay),
              contentAlignment = Alignment.Center,
          ) {
              Icon(
                  imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                  contentDescription = if (isPlaying) "Pause" else "Play",
                  tint = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier.size(44.dp),
              )
          }
          Spacer(modifier = Modifier.size(16.dp))
          IconButton(
              onClick = onSkipForward,
              enabled = enabled,
              modifier = Modifier.size(56.dp),
          ) {
              Icon(Icons.Filled.Forward30, contentDescription = "Forward 30 seconds", modifier = Modifier.size(36.dp))
          }
          if (hasChapters) {
              Spacer(modifier = Modifier.size(8.dp))
              IconButton(
                  onClick = onNextChapter,
                  enabled = enabled,
                  modifier = Modifier.size(48.dp),
              ) {
                  Icon(Icons.Filled.SkipNext, contentDescription = "Next chapter", modifier = Modifier.size(30.dp))
              }
          }
      }
  }
  ```
- [ ] In `AudiobookPlayerScreen.kt`, replace the inline transport `Row` (the one with `Replay30`, the big play/pause `Box`, and `Forward30`) with:
  ```kotlin
  AudiobookTransport(
      isPlaying = state.isPlaying,
      enabled = state.streamUrl != null,
      hasChapters = state.chapters.isNotEmpty(),
      onPrevChapter = { viewModel.skipToPreviousChapter() },
      onSkipBack = { viewModel.seekBy(-30.0) },
      onTogglePlay = { viewModel.togglePlay() },
      onSkipForward = { viewModel.seekBy(30.0) },
      onNextChapter = { viewModel.skipToNextChapter() },
  )
  ```
- [ ] Build:
  ```bash
  ./gradlew :androidApp:compileDebugKotlinAndroid
  ```
- [ ] Commit:
  ```bash
  git add -A && git commit -m "feat(audiobook): five-control transport with prev/next chapter"
  ```

---

### Task 5 — Add `ChaptersSheet` (replaces inline list) + `AudiobookSecondaryBar`

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/ChaptersSheet.kt` (new)
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookSecondaryBar.kt` (new)
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerScreen.kt` (modified)

Two pieces: the bottom sheet (lazy list, highlight current, auto-scroll on open) and the secondary bar that hosts the chapters button + speed/sleep chips (moving the existing `ChipButton` here so the screen no longer owns it). Both are Compose-only; verify on-device in Task 7.

- [ ] Create `AudiobookSecondaryBar.kt`, relocating `ChipButton` and `formatSpeed` from the screen:
  ```kotlin
  package com.continuum.app.android.ui.screens.audiobook

  import androidx.compose.foundation.background
  import androidx.compose.foundation.clickable
  import androidx.compose.foundation.layout.Arrangement
  import androidx.compose.foundation.layout.Row
  import androidx.compose.foundation.layout.Spacer
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.layout.size
  import androidx.compose.foundation.shape.RoundedCornerShape
  import androidx.compose.material.icons.Icons
  import androidx.compose.material.icons.automirrored.filled.List
  import androidx.compose.material.icons.filled.Bedtime
  import androidx.compose.material.icons.filled.Speed
  import androidx.compose.material3.Icon
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.Text
  import androidx.compose.runtime.Composable
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.draw.clip
  import androidx.compose.ui.graphics.vector.ImageVector
  import androidx.compose.ui.unit.dp

  /**
   * Secondary control row beneath the transport: speed chip, sleep chip, and a
   * chapters button that opens [ChaptersSheet]. Bookmarks stays top-right on the
   * screen, so it is intentionally not duplicated here.
   */
  @Composable
  fun AudiobookSecondaryBar(
      speedLabel: String,
      sleepLabel: String,
      onSpeedClick: () -> Unit,
      onSleepClick: () -> Unit,
      onChaptersClick: () -> Unit,
      showChapters: Boolean,
      modifier: Modifier = Modifier,
  ) {
      Row(
          modifier = modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
      ) {
          ChipButton(icon = Icons.Filled.Speed, label = speedLabel, onClick = onSpeedClick)
          ChipButton(icon = Icons.Filled.Bedtime, label = sleepLabel, onClick = onSleepClick)
          if (showChapters) {
              ChipButton(icon = Icons.AutoMirrored.Filled.List, label = "Chapters", onClick = onChaptersClick)
          }
      }
  }

  @Composable
  internal fun ChipButton(
      icon: ImageVector,
      label: String,
      onClick: () -> Unit,
  ) {
      Row(
          modifier = Modifier
              .clip(RoundedCornerShape(16.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .clickable(onClick = onClick)
              .padding(horizontal = 14.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
          Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.size(6.dp))
          Text(label, style = MaterialTheme.typography.labelMedium)
      }
  }

  internal fun formatSpeed(speed: Float): String =
      if (speed % 1f == 0f) speed.toInt().toString() else "%.2f".format(speed).trimEnd('0').trimEnd('.')
  ```
- [ ] Create `ChaptersSheet.kt`:
  ```kotlin
  package com.continuum.app.android.ui.screens.audiobook

  import androidx.compose.foundation.background
  import androidx.compose.foundation.clickable
  import androidx.compose.foundation.layout.Arrangement
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.Row
  import androidx.compose.foundation.layout.Spacer
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.foundation.layout.height
  import androidx.compose.foundation.layout.heightIn
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.layout.width
  import androidx.compose.foundation.lazy.LazyColumn
  import androidx.compose.foundation.lazy.itemsIndexed
  import androidx.compose.foundation.lazy.rememberLazyListState
  import androidx.compose.foundation.shape.RoundedCornerShape
  import androidx.compose.material.icons.Icons
  import androidx.compose.material.icons.filled.GraphicEq
  import androidx.compose.material3.ExperimentalMaterial3Api
  import androidx.compose.material3.Icon
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.ModalBottomSheet
  import androidx.compose.material3.Text
  import androidx.compose.material3.rememberModalBottomSheetState
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.LaunchedEffect
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.draw.clip
  import androidx.compose.ui.text.font.FontWeight
  import androidx.compose.ui.text.style.TextOverflow
  import androidx.compose.ui.unit.dp
  import com.continuum.app.android.ui.util.formatClockTime
  import com.continuum.app.model.catalog.VersionChapter

  /**
   * Chapters bottom sheet. Replaces the always-expanded inline list. Highlights
   * the current chapter and auto-scrolls to it once when the sheet opens so the
   * listener lands on "where they are" even at chapter 90 of 111.
   */
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun ChaptersSheet(
      chapters: List<VersionChapter>,
      currentChapterIndex: Int,
      onJumpTo: (VersionChapter) -> Unit,
      onDismiss: () -> Unit,
  ) {
      val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
      val listState = rememberLazyListState()

      // Auto-scroll to the current chapter once on open; keep it near the top
      // (offset by a couple rows) so context above is visible.
      LaunchedEffect(Unit) {
          val target = (currentChapterIndex - 2).coerceAtLeast(0)
          if (currentChapterIndex in chapters.indices) {
              listState.scrollToItem(target)
          }
      }

      ModalBottomSheet(
          onDismissRequest = onDismiss,
          sheetState = sheetState,
          containerColor = MaterialTheme.colorScheme.surface,
      ) {
          Column(modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth()) {
              Text(
                  text = "Chapters",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.SemiBold,
              )
              Spacer(modifier = Modifier.height(12.dp))
              LazyColumn(
                  state = listState,
                  modifier = Modifier.heightIn(max = 480.dp),
                  verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                  itemsIndexed(chapters, key = { _, c -> c.index }) { idx, chapter ->
                      val isCurrent = idx == currentChapterIndex
                      Row(
                          modifier = Modifier
                              .fillMaxWidth()
                              .clip(RoundedCornerShape(10.dp))
                              .background(
                                  if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                                  else MaterialTheme.colorScheme.surface,
                              )
                              .clickable {
                                  onJumpTo(chapter)
                                  onDismiss()
                              }
                              .padding(horizontal = 12.dp, vertical = 12.dp),
                          verticalAlignment = Alignment.CenterVertically,
                      ) {
                          if (isCurrent) {
                              Icon(
                                  imageVector = Icons.Filled.GraphicEq,
                                  contentDescription = "Now playing",
                                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                  modifier = Modifier.size(width = 28.dp, height = 18.dp).padding(end = 8.dp),
                              )
                          } else {
                              Text(
                                  text = "${chapter.index + 1}.",
                                  style = MaterialTheme.typography.bodySmall,
                                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                                  modifier = Modifier.width(28.dp),
                              )
                          }
                          Text(
                              text = chapter.title,
                              style = MaterialTheme.typography.bodyMedium,
                              fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                              color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                              else MaterialTheme.colorScheme.onSurface,
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis,
                              modifier = Modifier.weight(1f),
                          )
                          Text(
                              text = formatClockTime(chapter.startSeconds),
                              style = MaterialTheme.typography.labelSmall,
                              color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                              else MaterialTheme.colorScheme.onSurfaceVariant,
                          )
                      }
                  }
              }
              Spacer(modifier = Modifier.height(16.dp))
          }
      }
  }
  ```
  Note: `Modifier.size(width = ..., height = ...)` on the `Icon` requires `import androidx.compose.foundation.layout.size`. Add that import (the overload is the one already used in the old screen for the chapter index `Text`).
- [ ] In `AudiobookPlayerScreen.kt`, replace the inline Speed+Sleep chip `Row` (`ChipButton(... Speed ...)` / `ChipButton(... Bedtime ...)`) with the secondary bar, and add a `showChaptersSheet` state next to `showSpeedSheet`/`showSleepSheet`:
  ```kotlin
  var showChaptersSheet by remember { mutableStateOf(false) }
  AudiobookSecondaryBar(
      speedLabel = "${formatSpeed(state.playbackSpeed)}x",
      sleepLabel = state.sleepTimerMinutesLeft?.let { "$it min" } ?: "Sleep",
      onSpeedClick = { showSpeedSheet = true },
      onSleepClick = { showSleepSheet = true },
      onChaptersClick = { showChaptersSheet = true },
      showChapters = state.chapters.isNotEmpty(),
  )
  ```
- [ ] Delete the entire inline "Chapter list" block in `AudiobookPlayerScreen.kt` (the `if (state.chapters.isNotEmpty()) { ... "Chapters" Text ... Column { state.chapters.forEach { ... } } }` block, lines ~370–406 in the original) and instead, alongside the other sheet toggles, add:
  ```kotlin
  if (showChaptersSheet) {
      ChaptersSheet(
          chapters = state.chapters,
          currentChapterIndex = currentChapterIndex,
          onJumpTo = { viewModel.jumpToChapter(it) },
          onDismiss = { showChaptersSheet = false },
      )
  }
  ```
- [ ] Delete the now-relocated private `ChipButton` and `formatSpeed` from `AudiobookPlayerScreen.kt` (they live in `AudiobookSecondaryBar.kt` now).
- [ ] Build:
  ```bash
  ./gradlew :androidApp:compileDebugKotlinAndroid
  ```
- [ ] Commit:
  ```bash
  git add -A && git commit -m "feat(audiobook): chapters bottom sheet + secondary control bar"
  ```

---

### Task 6 — Clean up the screen: imports + final shape

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerScreen.kt`

After Tasks 2–5 the screen body is now: back/bookmark row, `AudiobookCoverHeader`, `ChapterProgressBar`, `AudiobookTransport`, `AudiobookSecondaryBar`, the sheet toggles, and loading/error states. Remove imports that no longer resolve to anything used. Media3 wiring (`MediaController`, the `DisposableEffect`/`LaunchedEffect` blocks, position poller, speed apply, pending-seek apply) is untouched.

- [ ] In `AudiobookPlayerScreen.kt`, remove imports now unused by the slimmed screen. Likely-removable (verify each is truly unreferenced first): `Slider`, `Replay30`, `Forward30`, `Pause`, `PlayArrow`, `Speed`, `Bedtime`, `CircleShape`, `clickable` (only if the play box was the last user), `RoundedCornerShape`, `TextAlign`, `TextOverflow`, `FontWeight`, `formatClockTime`. Keep everything the Media3 `LaunchedEffect`/`DisposableEffect` blocks and the back/bookmark `Row` still use (`Icons.AutoMirrored.Filled.ArrowBack`, `Icons.Filled.Bookmark`, `IconButton`, `Spacer`, `Row`, `Column`, `verticalScroll`, `rememberScrollState`, `MediaItem`, `MediaMetadata`, `PlaybackParameters`, `Player`, `MediaController`, `SessionToken`, `ContinuumPlaybackService`, `MoreExecutors`, `delay`, `koinViewModel`, etc.).
- [ ] Confirm the body's vertical rhythm still uses `Spacer(height = ...)` between sections (cover→progress→transport→secondary) so spacing matches the spec mock.
- [ ] Build clean (no warnings about unused imports from this file):
  ```bash
  ./gradlew :androidApp:compileDebugKotlinAndroid
  ```
- [ ] Run lint to catch unused-import / style drift:
  ```bash
  ./gradlew :androidApp:lintDebug
  ```
  (If the repo gates on `detekt`/`ktlint` instead, run that task; otherwise the compiler + lint above suffice.)
- [ ] Re-run the unit test to confirm nothing regressed:
  ```bash
  ./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.audiobook.ChapterProgressMathTest"
  ```
- [ ] Commit:
  ```bash
  git add -A && git commit -m "refactor(audiobook): prune imports after screen decomposition"
  ```

---

### Task 7 — On-device manual verification (Compose UI is impractical to unit-test)

**Files:** none (verification only).

The redesigned screen is Compose-heavy and Media3-bound; instrumented/unit testing the UI is impractical for this plan, so verify on a real device. App package is `com.continuum.app`. Use an audiobook with many chapters (the spec calls out the 111-chapter case) plus one with no chapters.

- [ ] Build + install the debug APK on a connected Pixel:
  ```bash
  ./gradlew :androidApp:installDebug
  adb shell monkey -p com.continuum.app -c android.intent.category.LAUNCHER 1
  ```
- [ ] Open a multi-chapter audiobook and confirm the new layout: hero cover, title/author/"Narrated by …", a "Chapter N of M" header, the progress slider, the five-control transport, then the Speed / Sleep / Chapters secondary row. Bookmarks icon remains top-right.
- [ ] Verify chapter awareness: let playback advance (or scrub) across a chapter boundary and confirm "Chapter N of M" increments and the chapter-relative slider resets at the boundary.
- [ ] Tap the **Book/Chapter** toggle under the slider; confirm the slider range + the two time labels switch between whole-book and current-chapter values, and dragging in either mode seeks to the correct absolute book position.
- [ ] Tap **prev-chapter** mid-chapter (>3s in) and confirm it restarts the current chapter; tap again immediately (<3s in) and confirm it jumps to the previous chapter. Tap **next-chapter** and confirm forward jump. (These behaviors are owned by the VM; this only confirms the buttons are wired.)
- [ ] Tap the **Chapters** button: confirm the bottom sheet opens, the current chapter row is highlighted (primaryContainer + equalizer icon + bold), and the list is auto-scrolled so the current chapter is near the top (not pinned at index 0). Tap a different chapter and confirm it seeks and the sheet dismisses.
- [ ] Confirm **Speed** and **Sleep** chips still open their existing sheets unchanged, and the chips reflect current speed / remaining minutes.
- [ ] Open a **no-chapters** audiobook: confirm the chapter header is hidden, the toggle is hidden, the transport collapses to skip-back / play / skip-forward (no prev/next-chapter buttons), the Chapters button is hidden, and the slider behaves as whole-book progress.
- [ ] Capture a screenshot of the multi-chapter player + the open chapters sheet for the MR description:
  ```bash
  adb exec-out screencap -p > /tmp/audiobook-phase2-player.png
  ```
- [ ] No code change in this task; if any check fails, fix in the owning composable's task and re-verify before proceeding.

---

### Task 8 — Self-review against the spec, fix inline

**Files:** all Phase 2 files.

- [ ] Re-read spec §4.1 and §6 phase 2 and confirm each deliverable maps to a file: `AudiobookCoverHeader` (Task 2), `ChapterProgressBar` with book↔chapter toggle (Task 3), `AudiobookTransport` five controls (Task 4), `AudiobookSecondaryBar` + `ChaptersSheet` with highlight + auto-scroll (Task 5). Confirm the inline always-expanded list is fully removed.
- [ ] Confirm Phase-2 scope discipline: no `AudiobookSettingsStore`, no skip-interval/speed-preset/end-of-chapter sleep changes (those are Phase 4), no service/notification/Android-Auto/widget changes (Phases 5–8), no TV changes (Phase 3). `AudiobookSpeedSheet`/`AudiobookSleepTimerSheet` are reused as-is, not extended.
- [ ] Confirm VM is consumed, not modified: only `currentChapterIndex`, `chapterCountLabel`, `skipToPreviousChapter()`, `skipToNextChapter()`, plus existing `seekTo`/`seekBy`/`togglePlay`/`jumpToChapter` are called. (`chapterProgress` is available but the chapter-relative slider derives its own seconds for exact time labels; that is intentional and adds no VM coupling.)
- [ ] Final full module check:
  ```bash
  ./gradlew :androidApp:testDebugUnitTest :androidApp:compileDebugKotlinAndroid
  ```
- [ ] If everything passes, follow superpowers:finishing-a-development-branch to open the MR; include the screenshots from Task 7 and the AI-use disclosure per repo guidelines.
