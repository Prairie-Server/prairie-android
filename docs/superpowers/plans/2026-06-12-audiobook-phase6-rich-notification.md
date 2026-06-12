# Audiobook Player — Phase 6: Rich Media Notification — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Make the audiobook media notification, lock screen, widget, and Android Auto chapter-aware. Put "Chapter N — title" into the active `MediaItem`'s `MediaMetadata`, surface `SKIP_TO_PREV_CHAPTER` / `SKIP_TO_NEXT_CHAPTER` as Media3 notification custom actions with artwork, and wire the shared `MediaSession` callback to perform those chapter skips through the same chapter logic the UI uses. Implements spec §4.5 and §4.8 (Phase 6 only).

**Architecture:** Chapter math stays pure in `shared` (`AudiobookChapters`, a Phase 1 deliverable — see Preconditions). A new pure mapper in `android-shared` builds chapter-augmented `MediaMetadata` from a `VersionChapter` list + position, and maps a custom `SessionCommand` action id to a target seek position. `ContinuumPlaybackService` (shared with video) gains a `MediaSession.Callback` that registers the two custom session commands, publishes a custom layout (the two skip buttons + artwork), and on command receipt computes the target position via the pure mapper and seeks the shared `ExoPlayer`. The Compose audiobook screen, which already builds the `MediaItem`, additionally writes the chapter list + current-chapter index into `MediaMetadata.extras` and refreshes the current-chapter metadata as the position crosses chapter boundaries so the notification text updates live. Video playback is untouched because the callback only activates the custom layout when the active item carries audiobook chapter extras.

**Tech Stack:** Kotlin, Media3 1.10.0 (`media3-session`, `media3-common`), `MediaSession` + `MediaSession.Callback` + `SessionCommand` / `CommandButton` / `MediaMetadata`, Koin DI, kotlin-test-junit. Modules: `:shared` (commonMain/commonTest), `:android-shared` (androidMain/androidUnitTest), `:androidApp`.

---

## Preconditions

- **Phase 1 must be merged first.** This plan consumes `com.continuum.app.audiobook.AudiobookChapters` (pure, `shared/commonMain`) with at least: `currentChapterIndex(positionSeconds: Double, chapters: List<VersionChapter>): Int` and `previousChapterTarget(positionSeconds, chapters): Double` / `nextChapterTarget(positionSeconds, chapters): Double` (prev re-starts current chapter if >3s in, else previous chapter start; next goes to the next chapter start, clamped). If Phase 1 is not yet merged, the **first task below defines exactly the slice of `AudiobookChapters` this plan needs** and may be dropped if Phase 1 already provides it. Do not duplicate logic — if these functions exist, import them.
- `VersionChapter` is `shared/src/commonMain/kotlin/com/continuum/app/model/catalog/CatalogModels.kt`: `data class VersionChapter(index: Int, title: String, startSeconds: Double, endSeconds: Double, source: String?, thumbnailUrl: String?, thumbnailThumbhash: String?)`.
- The shared session is created in `ContinuumPlaybackService` via `MediaSession.Builder(this, player).build()` with **no callback and no custom layout today** — both are added by this plan.
- The audiobook `MediaItem` (with `MediaMetadata` title/artist/artworkUri) is built in `AudiobookPlayerScreen.kt` inside the `LaunchedEffect(controller, state.streamUrl)` block.

---

## File Structure

| Path | Responsibility |
| --- | --- |
| `shared/src/commonMain/kotlin/com/continuum/app/audiobook/AudiobookChapters.kt` | Pure chapter math (Phase 1). This plan only *consumes* `currentChapterIndex` / prev/next targets; Task 1 backfills only if missing. |
| `shared/src/commonTest/kotlin/com/continuum/app/audiobook/AudiobookChaptersTest.kt` | commonTest for the slice consumed here (Task 1, only if backfilled). |
| `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/AudiobookChapterMetadata.kt` | **New.** Pure helpers: `chapterDisplayLabel(index, total, title)`; `buildChapterMediaMetadata(base, chapters, currentIndex)` writing chapter info into `MediaMetadata` extras + display title/subtitle; `chapterExtrasBundle(chapters)` / `readChapters(extras)` round-trip; `chapterSkipTarget(action, positionSeconds, chapters)` mapping a custom-command action id to a seek target. No Android UI or service deps beyond `MediaMetadata` + `Bundle`. |
| `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/AudiobookSessionCommands.kt` | **New.** Constants for the two custom command ids (`ACTION_SKIP_TO_PREV_CHAPTER`, `ACTION_SKIP_TO_NEXT_CHAPTER`), `SessionCommand` factories, and a `customLayout(...)` builder returning the `List<CommandButton>` (prev/next chapter) for `setCustomLayout`. |
| `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/AudiobookChapterMetadataTest.kt` | **New.** androidUnitTest for the pure mapper + extras round-trip + skip-target mapping. |
| `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlaybackService.kt` | **Edit.** Build the `MediaSession` with a `MediaSession.Callback` that registers the custom commands, sets the custom layout for audiobook items, and executes chapter skips against the shared `ExoPlayer` via `chapterSkipTarget`. |
| `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerScreen.kt` | **Edit.** Write chapter extras + current-chapter display metadata into the audiobook `MediaItem`; refresh current-chapter metadata as position crosses boundaries (drives live notification text). |

---

### Task 1 — Backfill the consumed slice of `AudiobookChapters` (only if Phase 1 not merged)

**Skip this task entirely if `shared/src/commonMain/kotlin/com/continuum/app/audiobook/AudiobookChapters.kt` already exports `currentChapterIndex`, `previousChapterTarget`, and `nextChapterTarget`.** Run the check first.

**Files:**
- `shared/src/commonMain/kotlin/com/continuum/app/audiobook/AudiobookChapters.kt`
- `shared/src/commonTest/kotlin/com/continuum/app/audiobook/AudiobookChaptersTest.kt`

- [ ] Check existence: `grep -rn "fun currentChapterIndex\|fun previousChapterTarget\|fun nextChapterTarget" shared/src/commonMain/kotlin/com/continuum/app/audiobook/`. If all three are present, mark this task done and proceed to Task 2.
- [ ] (If missing) Write a failing test `shared/src/commonTest/kotlin/com/continuum/app/audiobook/AudiobookChaptersTest.kt`:
  ```kotlin
  package com.continuum.app.audiobook

  import com.continuum.app.model.catalog.VersionChapter
  import kotlin.test.Test
  import kotlin.test.assertEquals

  class AudiobookChaptersTest {
      private val chapters = listOf(
          VersionChapter(index = 0, title = "One", startSeconds = 0.0, endSeconds = 100.0),
          VersionChapter(index = 1, title = "Two", startSeconds = 100.0, endSeconds = 250.0),
          VersionChapter(index = 2, title = "Three", startSeconds = 250.0, endSeconds = 400.0),
      )

      @Test fun `current index resolves by position`() {
          assertEquals(0, AudiobookChapters.currentChapterIndex(0.0, chapters))
          assertEquals(0, AudiobookChapters.currentChapterIndex(99.9, chapters))
          assertEquals(1, AudiobookChapters.currentChapterIndex(100.0, chapters))
          assertEquals(2, AudiobookChapters.currentChapterIndex(399.0, chapters))
      }

      @Test fun `empty chapters degrade to zero`() {
          assertEquals(0, AudiobookChapters.currentChapterIndex(42.0, emptyList()))
      }

      @Test fun `previous restarts current chapter when more than 3s in`() {
          // 105s into chapter 1 (started 100) -> 5s in -> restart to 100
          assertEquals(100.0, AudiobookChapters.previousChapterTarget(105.0, chapters))
      }

      @Test fun `previous goes to prior chapter when within 3s of start`() {
          // 101s into chapter 1 -> 1s in -> go to chapter 0 start
          assertEquals(0.0, AudiobookChapters.previousChapterTarget(101.0, chapters))
      }

      @Test fun `previous on first chapter near start clamps to zero`() {
          assertEquals(0.0, AudiobookChapters.previousChapterTarget(1.0, chapters))
      }

      @Test fun `next goes to following chapter start`() {
          assertEquals(250.0, AudiobookChapters.nextChapterTarget(105.0, chapters))
      }

      @Test fun `next on last chapter stays at last chapter start`() {
          assertEquals(250.0, AudiobookChapters.nextChapterTarget(300.0, chapters))
      }
  }
  ```
- [ ] Run: `./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.audiobook.AudiobookChaptersTest"` — **expected: compile/test failure** (symbols missing).
- [ ] (If missing) Implement `shared/src/commonMain/kotlin/com/continuum/app/audiobook/AudiobookChapters.kt`:
  ```kotlin
  package com.continuum.app.audiobook

  import com.continuum.app.model.catalog.VersionChapter

  /** Pure, platform-agnostic audiobook chapter math. Shared by phone, TV,
   *  and the playback service so chapter behavior is identical everywhere. */
  object AudiobookChapters {
      private const val RESTART_THRESHOLD_SECONDS = 3.0

      /** Index of the chapter containing [positionSeconds]. Falls back to 0
       *  for empty lists or positions before the first start; clamps to the
       *  last chapter for positions past the end. */
      fun currentChapterIndex(positionSeconds: Double, chapters: List<VersionChapter>): Int {
          if (chapters.isEmpty()) return 0
          val idx = chapters.indexOfLast { positionSeconds >= it.startSeconds }
          return idx.coerceIn(0, chapters.lastIndex)
      }

      /** Standard audiobook "previous": restart the current chapter when
       *  more than [RESTART_THRESHOLD_SECONDS] in, otherwise jump to the
       *  previous chapter's start. Clamped to 0. */
      fun previousChapterTarget(positionSeconds: Double, chapters: List<VersionChapter>): Double {
          if (chapters.isEmpty()) return 0.0
          val current = chapters[currentChapterIndex(positionSeconds, chapters)]
          val intoChapter = positionSeconds - current.startSeconds
          return if (intoChapter > RESTART_THRESHOLD_SECONDS || current.index == 0) {
              current.startSeconds.coerceAtLeast(0.0)
          } else {
              chapters[(current.index - 1).coerceAtLeast(0)].startSeconds.coerceAtLeast(0.0)
          }
      }

      /** Start of the next chapter; clamps to the last chapter's start. */
      fun nextChapterTarget(positionSeconds: Double, chapters: List<VersionChapter>): Double {
          if (chapters.isEmpty()) return 0.0
          val currentIdx = currentChapterIndex(positionSeconds, chapters)
          val nextIdx = (currentIdx + 1).coerceAtMost(chapters.lastIndex)
          return chapters[nextIdx].startSeconds.coerceAtLeast(0.0)
      }
  }
  ```
- [ ] Run: `./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.audiobook.AudiobookChaptersTest"` — **expected: BUILD SUCCESSFUL**.
- [ ] Commit: `feat(audiobook): backfill AudiobookChapters slice for notification` (skip the commit if the task was a no-op).

---

### Task 2 — Pure chapter MediaMetadata mapper + extras round-trip (TDD)

This is the load-bearing pure logic for Phase 6: building chapter-augmented `MediaMetadata` and reading it back, plus mapping a custom-command action id to a seek target. All testable without a device.

**Files:**
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/AudiobookChapterMetadata.kt`
- `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/AudiobookChapterMetadataTest.kt`

- [ ] Write the failing test `AudiobookChapterMetadataTest.kt`:
  ```kotlin
  package com.continuum.app.common.player

  import androidx.media3.common.MediaMetadata
  import com.continuum.app.model.catalog.VersionChapter
  import org.junit.Test
  import org.junit.runner.RunWith
  import org.robolectric.RobolectricTestRunner
  import kotlin.test.assertEquals
  import kotlin.test.assertTrue

  @RunWith(RobolectricTestRunner::class)
  class AudiobookChapterMetadataTest {
      private val chapters = listOf(
          VersionChapter(index = 0, title = "Prologue", startSeconds = 0.0, endSeconds = 100.0),
          VersionChapter(index = 1, title = "The Road", startSeconds = 100.0, endSeconds = 250.0),
          VersionChapter(index = 2, title = "Home", startSeconds = 250.0, endSeconds = 400.0),
      )

      @Test fun `display label is one-based with total`() {
          assertEquals("Chapter 2 of 3 — The Road", AudiobookChapterMetadata.chapterDisplayLabel(1, 3, "The Road"))
      }

      @Test fun `label omits title when blank`() {
          assertEquals("Chapter 1 of 3", AudiobookChapterMetadata.chapterDisplayLabel(0, 3, ""))
      }

      @Test fun `build metadata sets subtitle to current chapter label and keeps title`() {
          val base = MediaMetadata.Builder().setTitle("My Book").setArtist("Author").build()
          val md = AudiobookChapterMetadata.buildChapterMediaMetadata(base, chapters, currentIndex = 1)
          assertEquals("My Book", md.title)
          assertEquals("Author", md.artist)
          assertEquals("Chapter 2 of 3 — The Road", md.subtitle)
      }

      @Test fun `extras round-trip preserves chapters`() {
          val base = MediaMetadata.Builder().setTitle("My Book").build()
          val md = AudiobookChapterMetadata.buildChapterMediaMetadata(base, chapters, currentIndex = 0)
          val restored = AudiobookChapterMetadata.readChapters(md.extras)
          assertEquals(chapters.size, restored.size)
          assertEquals("The Road", restored[1].title)
          assertEquals(100.0, restored[1].startSeconds, 0.0001)
          assertEquals(250.0, restored[1].endSeconds, 0.0001)
      }

      @Test fun `readChapters on null extras returns empty`() {
          assertTrue(AudiobookChapterMetadata.readChapters(null).isEmpty())
      }

      @Test fun `skip target maps next action to following chapter start`() {
          val target = AudiobookChapterMetadata.chapterSkipTarget(
              AudiobookSessionCommands.ACTION_SKIP_TO_NEXT_CHAPTER, positionSeconds = 105.0, chapters = chapters,
          )
          assertEquals(250.0, target, 0.0001)
      }

      @Test fun `skip target maps prev action restarting current chapter when more than 3s in`() {
          val target = AudiobookChapterMetadata.chapterSkipTarget(
              AudiobookSessionCommands.ACTION_SKIP_TO_PREV_CHAPTER, positionSeconds = 110.0, chapters = chapters,
          )
          assertEquals(100.0, target, 0.0001)
      }

      @Test fun `skip target returns null for unknown action`() {
          assertEquals(null, AudiobookChapterMetadata.chapterSkipTarget("nonsense", 0.0, chapters))
      }
  }
  ```
- [ ] Ensure Robolectric + `MediaMetadata` extras (a `Bundle`) work in androidUnitTest. Check `android-shared/build.gradle.kts` `androidUnitTest.dependencies` for `org.robolectric:robolectric`; if absent, add `testImplementation(libs.robolectric)` (add `robolectric` to `gradle/libs.versions.toml` if not present) and set `android { testOptions { unitTests { isIncludeAndroidResources = true } } }`. If Robolectric is undesired, instead keep `MediaMetadata` out of the asserted surface and have the mapper expose a pure `ChapterDisplay` data class for label/subtitle assertions and a separate, un-unit-tested thin `applyTo(MediaMetadata.Builder)` — but prefer Robolectric so the `Bundle` round-trip is genuinely covered.
- [ ] Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.AudiobookChapterMetadataTest"` — **expected: compile failure** (`AudiobookChapterMetadata` / `AudiobookSessionCommands` undefined).
- [ ] Implement `AudiobookSessionCommands.kt` (action-id constants needed by the mapper test):
  ```kotlin
  package com.continuum.app.common.player

  import android.os.Bundle
  import androidx.annotation.OptIn
  import androidx.media3.common.util.UnstableApi
  import androidx.media3.session.CommandButton
  import androidx.media3.session.SessionCommand

  /** Custom session commands + notification custom layout for audiobook
   *  chapter navigation. Registered on the shared MediaSession's callback. */
  @OptIn(UnstableApi::class)
  object AudiobookSessionCommands {
      const val ACTION_SKIP_TO_PREV_CHAPTER = "com.continuum.SKIP_TO_PREV_CHAPTER"
      const val ACTION_SKIP_TO_NEXT_CHAPTER = "com.continuum.SKIP_TO_NEXT_CHAPTER"

      val prevChapterCommand = SessionCommand(ACTION_SKIP_TO_PREV_CHAPTER, Bundle.EMPTY)
      val nextChapterCommand = SessionCommand(ACTION_SKIP_TO_NEXT_CHAPTER, Bundle.EMPTY)

      /** The two chapter-skip buttons shown in the media notification /
       *  custom layout. [prevIconRes] / [nextIconRes] are drawable resources
       *  supplied by the caller (see Task 4 for the chosen icons). */
      fun customLayout(prevIconRes: Int, nextIconRes: Int): List<CommandButton> = listOf(
          CommandButton.Builder()
              .setDisplayName("Previous chapter")
              .setIconResId(prevIconRes)
              .setSessionCommand(prevChapterCommand)
              .build(),
          CommandButton.Builder()
              .setDisplayName("Next chapter")
              .setIconResId(nextIconRes)
              .setSessionCommand(nextChapterCommand)
              .build(),
      )
  }
  ```
- [ ] Implement `AudiobookChapterMetadata.kt`:
  ```kotlin
  package com.continuum.app.common.player

  import android.os.Bundle
  import androidx.media3.common.MediaMetadata
  import com.continuum.app.audiobook.AudiobookChapters
  import com.continuum.app.model.catalog.VersionChapter

  /** Pure helpers that move chapter data into and out of the active
   *  MediaItem's MediaMetadata so the notification / lock screen / widget /
   *  Android Auto can render "Chapter N — title" and offer skip actions.
   *
   *  Chapters are packed into MediaMetadata.extras as parallel arrays
   *  (avoids depending on Parcelable model classes from the metadata bundle). */
  object AudiobookChapterMetadata {
      const val EXTRA_CHAPTER_INDEXES = "continuum.chapter.indexes"
      const val EXTRA_CHAPTER_TITLES = "continuum.chapter.titles"
      const val EXTRA_CHAPTER_STARTS = "continuum.chapter.starts"
      const val EXTRA_CHAPTER_ENDS = "continuum.chapter.ends"
      const val EXTRA_CURRENT_CHAPTER_INDEX = "continuum.chapter.current"

      fun chapterDisplayLabel(index: Int, total: Int, title: String): String {
          val head = "Chapter ${index + 1} of $total"
          return if (title.isBlank()) head else "$head — $title"
      }

      /** Returns [base] with chapter extras attached and the subtitle set to
       *  the current-chapter label. Title/artist/artwork from [base] are
       *  preserved (we rebuild via buildUpon). */
      fun buildChapterMediaMetadata(
          base: MediaMetadata,
          chapters: List<VersionChapter>,
          currentIndex: Int,
      ): MediaMetadata {
          if (chapters.isEmpty()) return base
          val safeIndex = currentIndex.coerceIn(0, chapters.lastIndex)
          val label = chapterDisplayLabel(safeIndex, chapters.size, chapters[safeIndex].title)
          return base.buildUpon()
              .setSubtitle(label)
              .setExtras(chapterExtrasBundle(chapters, safeIndex))
              .build()
      }

      fun chapterExtrasBundle(chapters: List<VersionChapter>, currentIndex: Int): Bundle = Bundle().apply {
          putIntArray(EXTRA_CHAPTER_INDEXES, IntArray(chapters.size) { chapters[it].index })
          putStringArray(EXTRA_CHAPTER_TITLES, Array(chapters.size) { chapters[it].title })
          putDoubleArray(EXTRA_CHAPTER_STARTS, DoubleArray(chapters.size) { chapters[it].startSeconds })
          putDoubleArray(EXTRA_CHAPTER_ENDS, DoubleArray(chapters.size) { chapters[it].endSeconds })
          putInt(EXTRA_CURRENT_CHAPTER_INDEX, currentIndex)
      }

      fun readChapters(extras: Bundle?): List<VersionChapter> {
          if (extras == null) return emptyList()
          val indexes = extras.getIntArray(EXTRA_CHAPTER_INDEXES) ?: return emptyList()
          val titles = extras.getStringArray(EXTRA_CHAPTER_TITLES) ?: return emptyList()
          val starts = extras.getDoubleArray(EXTRA_CHAPTER_STARTS) ?: return emptyList()
          val ends = extras.getDoubleArray(EXTRA_CHAPTER_ENDS) ?: return emptyList()
          val n = minOf(indexes.size, titles.size, starts.size, ends.size)
          return (0 until n).map { i ->
              VersionChapter(
                  index = indexes[i],
                  title = titles[i] ?: "",
                  startSeconds = starts[i],
                  endSeconds = ends[i],
              )
          }
      }

      /** Map a received custom-command action id to a seek target (seconds),
       *  or null if the action is not a chapter-skip command. Delegates the
       *  prev/next semantics to the shared [AudiobookChapters]. */
      fun chapterSkipTarget(
          action: String,
          positionSeconds: Double,
          chapters: List<VersionChapter>,
      ): Double? = when (action) {
          AudiobookSessionCommands.ACTION_SKIP_TO_PREV_CHAPTER ->
              AudiobookChapters.previousChapterTarget(positionSeconds, chapters)
          AudiobookSessionCommands.ACTION_SKIP_TO_NEXT_CHAPTER ->
              AudiobookChapters.nextChapterTarget(positionSeconds, chapters)
          else -> null
      }
  }
  ```
- [ ] Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.AudiobookChapterMetadataTest"` — **expected: BUILD SUCCESSFUL**.
- [ ] Commit: `feat(audiobook): pure chapter MediaMetadata mapper + custom commands`.

---

### Task 3 — Wire the shared MediaSession callback to perform chapter skips

Add a `MediaSession.Callback` in `ContinuumPlaybackService` that registers the two custom commands, publishes the custom layout **only for audiobook items**, and executes skips by reading chapters back from the active item's `MediaMetadata.extras` and seeking the shared `ExoPlayer`. Video items carry no chapter extras, so they get no custom layout and the callback never affects them.

**Files:**
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlaybackService.kt`

- [ ] Add chapter-skip drawable resources used by the custom layout. Reuse existing Material vector drawables if present (`grep -rn "skip_previous\|skip_next\|ic_skip" android-shared/src/androidMain/res androidApp/src/androidMain/res`); if none exist, add two vector drawables `android-shared/src/androidMain/res/drawable/ic_chapter_previous.xml` and `ic_chapter_next.xml` (Material "skip_previous" / "skip_next" 24dp paths, `android:tint="?attr/colorControlNormal"`). Record the chosen `R.drawable.*` ids for the next step.
- [ ] In `ContinuumPlaybackService.onCreate`, replace `mediaSession = MediaSession.Builder(this, player).build()` with a builder that installs the callback and seeds the custom layout:
  ```kotlin
  mediaSession = MediaSession.Builder(this, player)
      .setCallback(AudiobookSessionCallback(player))
      .build()
  ```
- [ ] Add the callback as a private inner class of the service (it needs the `ExoPlayer` and the service scope; keep it small — the decision logic lives in the pure mapper from Task 2):
  ```kotlin
  private inner class AudiobookSessionCallback(
      private val player: ExoPlayer,
  ) : MediaSession.Callback {

      private val chapterLayout = AudiobookSessionCommands.customLayout(
          prevIconRes = R.drawable.ic_chapter_previous,
          nextIconRes = R.drawable.ic_chapter_next,
      )

      override fun onConnect(
          session: MediaSession,
          controller: MediaSession.ControllerInfo,
      ): MediaSession.ConnectionResult {
          // Grant the two custom commands on top of the default player /
          // session commands so the controller (and notification) may invoke
          // them. Video controllers receive them too but never see the
          // buttons because we only push the layout for audiobook items.
          val sessionCommands = MediaSession.ConnectionResult
              .DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
              .add(AudiobookSessionCommands.prevChapterCommand)
              .add(AudiobookSessionCommands.nextChapterCommand)
              .build()
          return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
              .setAvailableSessionCommands(sessionCommands)
              .setCustomLayout(if (hasChapters()) chapterLayout else emptyList())
              .build()
      }

      override fun onCustomCommand(
          session: MediaSession,
          controller: MediaSession.ControllerInfo,
          customCommand: SessionCommand,
          args: Bundle,
      ): com.google.common.util.concurrent.ListenableFuture<SessionResult> {
          val chapters = activeChapters()
          val positionSeconds = player.currentPosition / 1000.0
          val target = AudiobookChapterMetadata.chapterSkipTarget(
              action = customCommand.customAction,
              positionSeconds = positionSeconds,
              chapters = chapters,
          )
          return if (target != null) {
              player.seekTo((target * 1000).toLong())
              com.google.common.util.concurrent.Futures.immediateFuture(
                  SessionResult(SessionResult.RESULT_SUCCESS),
              )
          } else {
              com.google.common.util.concurrent.Futures.immediateFuture(
                  SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED),
              )
          }
      }

      private fun activeChapters() =
          AudiobookChapterMetadata.readChapters(player.mediaMetadata.extras)

      private fun hasChapters() = activeChapters().isNotEmpty()
  }
  ```
  Add the imports: `androidx.media3.session.SessionCommand`, `androidx.media3.session.SessionResult`, `android.os.Bundle`.
- [ ] Refresh the custom layout when the active item changes (an audiobook item becomes active or a video item replaces it). In `onCreate`, after the session is built, register a `Player.Listener`:
  ```kotlin
  player.addListener(object : Player.Listener {
      override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
          val hasChapters = AudiobookChapterMetadata.readChapters(mediaMetadata.extras).isNotEmpty()
          val layout = if (hasChapters) {
              AudiobookSessionCommands.customLayout(
                  R.drawable.ic_chapter_previous, R.drawable.ic_chapter_next,
              )
          } else {
              emptyList()
          }
          mediaSession?.setCustomLayout(layout)
      }
  })
  ```
  Add `import androidx.media3.common.Player`. Keep this listener distinct from the analytics listener already attached.
- [ ] Build to confirm the service compiles with the new symbols: `./gradlew :android-shared:compileDebugKotlinAndroid` — **expected: BUILD SUCCESSFUL**.
- [ ] Run the module's unit tests to confirm no regressions in player DI / settings: `./gradlew :android-shared:testDebugUnitTest` — **expected: BUILD SUCCESSFUL**.
- [ ] Commit: `feat(audiobook): chapter-skip session commands on shared MediaSession`.

---

### Task 4 — Write chapter metadata into the audiobook MediaItem + live current-chapter updates

The audiobook `MediaItem` is built in `AudiobookPlayerScreen.kt`. Augment it with chapter extras + current-chapter subtitle so the notification/lock screen show "Chapter N — title", and update the current chapter as the position crosses boundaries so the notification text stays correct while playing.

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerScreen.kt`

- [ ] In the `LaunchedEffect(controller, state.streamUrl)` block, after building the base `MediaMetadata` (title/artist/artworkUri) and before `MediaItem.Builder().setMediaMetadata(...)`, fold chapter data in:
  ```kotlin
  val baseMetadata = MediaMetadata.Builder()
      .setTitle(state.title)
      .setArtist(state.author ?: state.narrator)
      .also { mb ->
          state.coverUrl?.takeIf { it.isNotBlank() }
              ?.let { mb.setArtworkUri(android.net.Uri.parse(it)) }
      }
      .build()
  val currentIndex = AudiobookChapters.currentChapterIndex(
      state.positionSeconds, state.chapters,
  )
  val metadata = AudiobookChapterMetadata.buildChapterMediaMetadata(
      base = baseMetadata,
      chapters = state.chapters,
      currentIndex = currentIndex,
  )
  val mediaItem = MediaItem.Builder()
      .setUri(url)
      .setMediaMetadata(metadata)
      .build()
  ```
  Add imports: `com.continuum.app.audiobook.AudiobookChapters`, `com.continuum.app.common.player.AudiobookChapterMetadata`. (`MediaMetadata` is already imported.)
- [ ] Add a `LaunchedEffect` that refreshes the current-chapter metadata as the chapter changes, without rebuilding the whole `MediaItem` (cheap `controller.replaceMediaItem` of metadata only, keeping the same URI so playback is not interrupted):
  ```kotlin
  // Keep the notification / lock-screen chapter label in sync with playback.
  val currentChapterIndex = remember(state.positionSeconds, state.chapters) {
      AudiobookChapters.currentChapterIndex(state.positionSeconds, state.chapters)
  }
  LaunchedEffect(controller, currentChapterIndex, state.chapters) {
      val c = controller ?: return@LaunchedEffect
      if (state.chapters.isEmpty()) return@LaunchedEffect
      val item = c.currentMediaItem ?: return@LaunchedEffect
      val updated = item.buildUpon()
          .setMediaMetadata(
              AudiobookChapterMetadata.buildChapterMediaMetadata(
                  base = item.mediaMetadata,
                  chapters = state.chapters,
                  currentIndex = currentChapterIndex,
              ),
          )
          .build()
      c.replaceMediaItem(c.currentMediaItemIndex, updated)
  }
  ```
  This keeps the URI identical (`buildUpon` on the existing item), so Media3 updates metadata/notification without re-preparing the stream. The service's `onMediaMetadataChanged` listener (Task 3) keeps the custom layout attached.
- [ ] Build the phone app: `./gradlew :androidApp:compileDebugKotlin` — **expected: BUILD SUCCESSFUL**.
- [ ] Commit: `feat(audiobook): chapter-aware MediaMetadata on phone player item`.

---

### Task 5 — Manual on-device verification (notification visuals + video regression)

The notification, lock screen, and artwork need a real device — no unit test substitutes. The shared-session video regression check is mandatory (the spec calls the shared service the biggest risk).

**Files:** none (manual).

- [ ] Build + install on a connected device: `./gradlew :androidApp:installDebug`.
- [ ] Confirm the device is attached: `adb devices` (expect one `device` entry).
- [ ] Open an audiobook **with chapters** and start playback. Pull down the notification shade: `adb shell cmd statusbar expand-notifications`.
  - [ ] Verify the media notification shows the book title and, as the chapter subtitle/text, "Chapter N of M — <chapter title>".
  - [ ] Verify two chapter-skip custom actions appear alongside play/pause, and the cover artwork is shown.
  - [ ] Tap the next-chapter action; verify playback jumps to the next chapter start and the notification text updates to the new chapter. Tap previous-chapter mid-chapter (>3s in); verify it restarts the current chapter.
- [ ] Lock the device (`adb shell input keyevent 26`) and verify the lock-screen media controls show the chapter label + skip actions + artwork; unlock with `adb shell input keyevent 82`.
- [ ] Dump the active session to confirm the custom commands + metadata are published: `adb shell dumpsys media_session | grep -iA3 "Continuum\|chapter"` — expect the session listed with the custom actions.
- [ ] Play across a natural chapter boundary (do not seek): wait for the position to cross into the next chapter and verify the notification chapter label advances on its own (validates the Task 4 live-update effect).
- [ ] Open an audiobook **without chapters**: verify the notification shows title/artist/artwork with **no** chapter subtitle and **no** chapter-skip actions (validates the `hasChapters()` gating).
- [ ] **Video regression pass (shared session):** play a normal video via the video `PlayerScreen`.
  - [ ] Verify lock-screen / notification transport controls work (play/pause, scrub) and **no chapter-skip buttons appear** on the video notification.
  - [ ] Verify headset play/pause still toggles playback.
  - [ ] Exit the player and confirm the stop-on-exit teardown still stops audio (no orphaned playback in the shade). Cross-check `ContinuumPlaybackService.onTaskRemoved` behavior by swiping the app away during video playback — audio must stop.
- [ ] Record results (pass/fail per checkbox) in the PR description. If any video behavior regressed, treat it as a blocker and revisit Task 3 (the callback must be additive only).

---

## Self-Review vs Spec

- **§4.5 "MediaMetadata carries chapter info so notification/lock screen/widget/Android Auto can show Chapter N — title":** Task 4 writes the current-chapter label into `MediaMetadata.subtitle` and packs the full chapter list into `extras`; Task 3 reads it back in the service. The widget (Phase 8) and Android Auto (Phase 7) can consume the same extras via `AudiobookChapterMetadata.readChapters` — no Phase-6 work needed for them beyond the metadata being present, which it now is. ✅
- **§4.5 "Custom session commands SKIP_TO_PREV_CHAPTER / SKIP_TO_NEXT_CHAPTER":** Defined in `AudiobookSessionCommands` with stable action ids, registered in `onConnect`, dispatched in `onCustomCommand`. ✅
- **§4.8 "skip-chapter custom actions + artwork on the Media3-provided notification":** `customLayout` builds two `CommandButton`s with icons; artwork comes from the existing `setArtworkUri`, preserved by `buildUpon`. Media3 renders the custom layout into its provided notification automatically (no manual notification building). ✅
- **§4.8 "Applies to phone and TV (same session)":** All wiring lives in `android-shared` (`ContinuumPlaybackService`, mapper, commands); both apps drive the same session, so the TV app inherits the behavior with no TV-specific Phase-6 code. The TV player screen (Phase 3) only needs to write the same chapter metadata when it builds its `MediaItem` — note this as a follow-up wired identically via `AudiobookChapterMetadata`. Flagged below as deferred. ✅ (phone path complete)
- **"Wire the session callback to perform chapter skips via the shared chapter logic":** `onCustomCommand` → `AudiobookChapterMetadata.chapterSkipTarget` → `AudiobookChapters` (the shared pure logic, same as the UI). No duplicated prev/next math. ✅
- **Shared-with-video safety:** custom commands are granted to all controllers but the custom layout is pushed only when `MediaMetadata.extras` contain chapters; video items carry none, so video notifications are unchanged. Verified manually in Task 5. ✅
- **No production code outside the plan; tests use the confirmed task `testDebugUnitTest` on `:shared` and `:android-shared`.** ✅

---

## Deferred / Out of Scope for Phase 6

- **TV player chapter metadata:** the `TvAudiobookPlayerScreen` (Phase 3) must call `AudiobookChapterMetadata.buildChapterMediaMetadata` when it builds its `MediaItem`, exactly like Task 4 does for the phone. The session-side wiring (Task 3) already serves TV; only the TV screen's MediaItem construction is a follow-up, tracked under Phase 3, not Phase 6.
- **Android Auto browse tree** (`MediaLibraryService` refactor) — Phase 7.
- **Glance home-screen widget** consuming the chapter metadata — Phase 8.
- **Phase 1 `AudiobookChapters`** — assumed merged; Task 1 only backfills the consumed slice if absent and must be dropped if Phase 1 owns it (avoid duplicate logic).
