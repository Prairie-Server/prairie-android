# Ebook Reader — Phase 6: Reading-Time Estimates — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Add "~X min left in chapter" and "~X min left in book" estimates to the reader. The math lives in a pure, fully unit-tested `ReadingEstimator` in `shared/commonMain`; the reader chrome surfaces the estimate next to the existing progress line. An optional, simple observed-pace tracker refines the words-per-minute (WPM) value, falling back to a sane default when there is not enough data.

**Architecture:** A pure Kotlin estimator (no Android, no coroutines, no IO) takes a `ReadingContentLength` (the unit each format already knows — words for reflowable/EPUB+text, pages for PDF/comic) plus a `ReadingSpeed` and produces a `ReadingTimeEstimate` (whole minutes for chapter-remaining and book-remaining, plus a formatted label). Format readers already report `onPageCountKnown` and current position to `ReaderViewModel`; we extend the VM to also receive a per-format content-length signal, run the estimator, and expose the result in `ReaderUiState`. `ReaderScreen` renders the label. Pace observation is an in-memory rolling sample in the VM that feeds a refined WPM back into the estimator; insufficient samples ⇒ default WPM.

**Tech Stack:** Kotlin (KMP `commonMain`/`commonTest`), kotlinx.coroutines (VM only — the estimator itself is pure), JUnit via `kotlin.test` (`./gradlew :shared:testDebugUnitTest`).

This plan assumes Phase 1 (`ReaderLocator` + generic progress) and Phase 2 (epub.js with accurate locations/word counts) shipped, per the design spec §8. Where Phase 2 word counts are not yet wired for a given format, the estimator degrades gracefully to a page-based estimate. Commands assume the repository root (`prairie-android`) is the cwd.

---

## File Structure

New files:

- `shared/src/commonMain/kotlin/com/continuum/app/model/reader/ReadingEstimator.kt`
  Pure estimator: `ReadingContentLength`, `ReadingSpeed`, `ReadingTimeEstimate`, `ReadingEstimator` object. No Android / coroutine / IO imports.
- `shared/src/commonMain/kotlin/com/continuum/app/model/reader/ReadingPaceTracker.kt`
  Pure, immutable rolling-sample pace tracker that turns observed (words-read, elapsed-ms) samples into a refined `ReadingSpeed` or `null` (insufficient data ⇒ caller uses default).
- `shared/src/commonTest/kotlin/com/continuum/app/model/reader/ReadingEstimatorTest.kt`
  TDD coverage for the estimator (words→minutes, chapter vs book, default-WPM fallback, rounding/formatting, zero/last-page edge cases).
- `shared/src/commonTest/kotlin/com/continuum/app/model/reader/ReadingPaceTrackerTest.kt`
  TDD coverage for the pace tracker (insufficient-data fallback, rolling window, clamp to sane WPM bounds).

Modified files:

- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModel.kt`
  Hold content-length per format, compute the estimate, observe pace, expose `readingEstimate` in `ReaderUiState`.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt`
  Render the estimate label next to the existing progress text (~lines 99-105). Pass content-length callbacks to readers.
- The five format readers (only where a content-length signal beyond page count is cheap to add):
  - `EpubReader.kt`, `TextReader.kt`, `FictionBookReader.kt` — report words/chars when available.
  - `PdfReader.kt`, `ComicReader.kt` — page-based length already flows via `onPageCountKnown`; no new content signal, estimator uses pages.

---

## Task 1 — Pure `ReadingEstimator` model + types (TDD)

**Files:**
- `shared/src/commonMain/kotlin/com/continuum/app/model/reader/ReadingEstimator.kt` (new)
- `shared/src/commonTest/kotlin/com/continuum/app/model/reader/ReadingEstimatorTest.kt` (new)

Design notes (ground rules the tests encode):
- `ReadingContentLength` is a sealed hierarchy so each format expresses what it actually knows:
  - `Words(total: Int)` — reflowable formats (EPUB via epub.js, text, FB2) once word counts exist.
  - `Pages(total: Int, wordsPerPage: Int = DEFAULT_WORDS_PER_PAGE)` — fixed-layout (PDF, comic) or reflowable before word counts wire up. Converts to words via `wordsPerPage`.
  - `Characters(total: Int, charsPerWord: Int = DEFAULT_CHARS_PER_WORD)` — raw text fallback (TextReader char count). Converts to words via `charsPerWord`.
- `ReadingSpeed(wordsPerMinute: Double)` with `companion DEFAULT = ReadingSpeed(238.0)` (a widely cited adult silent-reading average; one source of truth, referenced by tests).
- `ReadingTimeEstimate(chapterMinutesRemaining: Int, bookMinutesRemaining: Int, chapterLabel: String, bookLabel: String)`.
- `ReadingEstimator.estimate(...)` signature:
  ```
  fun estimate(
      chapterRemaining: ReadingContentLength,
      bookRemaining: ReadingContentLength,
      speed: ReadingSpeed = ReadingSpeed.DEFAULT,
  ): ReadingTimeEstimate
  ```
- Minutes = `ceil(remainingWords / wpm)`, clamped to `>= 0`. Zero remaining words ⇒ 0 minutes.
- Label formatting via `ReadingEstimator.formatLabel(minutes: Int)`:
  - `minutes <= 0` ⇒ `"Done"`.
  - `minutes < 60` ⇒ `"~$minutes min left"`.
  - `minutes >= 60` ⇒ `"~${h}h ${m}m left"` where `h = minutes / 60`, `m = minutes % 60` (omit `" 0m"` when `m == 0`, e.g. `"~2h left"`).
- Constants: `DEFAULT_WORDS_PER_PAGE = 280`, `DEFAULT_CHARS_PER_WORD = 6` (5 letters + 1 space, standard "word" definition). These are `internal const` in the file, referenced by tests so magic numbers stay single-sourced.

Steps:
- [ ] Write `ReadingEstimatorTest.kt` first (red). Cover, with `kotlin.test` (`assertEquals`):
  - `Words(476)` at default WPM (238) ⇒ exactly 2 minutes (476/238 = 2.0, ceil = 2).
  - `Words(477)` ⇒ 3 minutes (ceil(2.004…) = 3) — proves `ceil`, not round/floor.
  - `Words(0)` ⇒ 0 minutes and `formatLabel(0) == "Done"`.
  - `Pages(total = 10, wordsPerPage = 280)` remaining ⇒ 2800 words ⇒ `ceil(2800/238) = 12` ⇒ label `"~12 min left"`.
  - `Characters(total = 6000, charsPerWord = 6)` ⇒ 1000 words ⇒ `ceil(1000/238) = 5` min.
  - `formatLabel(59) == "~59 min left"`, `formatLabel(60) == "~1h left"`, `formatLabel(75) == "~1h 15m left"`, `formatLabel(120) == "~2h left"`.
  - `estimate(...)` returns distinct chapter vs book values: chapter `Words(238)` ⇒ 1 min, book `Words(2380)` ⇒ 10 min, asserting both fields independently.
  - Default-WPM fallback: calling `estimate(...)` without a `speed` arg yields the same result as passing `ReadingSpeed.DEFAULT`.
  - Negative/garbage guard: `Words(-5)` is treated as 0 words ⇒ 0 minutes (no negative labels).
  - Custom slow speed: `Words(100)` at `ReadingSpeed(100.0)` ⇒ 1 min; `Words(101)` ⇒ 2 min.
- [ ] Run the tests, confirm they fail to compile / fail (red): `./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.reader.ReadingEstimatorTest"`
- [ ] Implement `ReadingEstimator.kt` with the sealed `ReadingContentLength`, `ReadingSpeed`, `ReadingTimeEstimate`, the `ReadingEstimator` object (`estimate`, `formatLabel`, private `wordsRemaining(...)` that normalizes each variant to a non-negative word count), and the `internal const` constants.
- [ ] Run the test again, confirm green (same `--tests` filter).
- [ ] Self-review vs spec §5: estimator is pure (grep the file for `android`, `kotlinx.coroutines`, `import java` — there should be none); chapter-remaining and book-remaining are independent inputs; default WPM is the documented fallback.

## Task 2 — Pure `ReadingPaceTracker` (TDD, optional refinement)

**Files:**
- `shared/src/commonMain/kotlin/com/continuum/app/model/reader/ReadingPaceTracker.kt` (new)
- `shared/src/commonTest/kotlin/com/continuum/app/model/reader/ReadingPaceTrackerTest.kt` (new)

Design notes:
- Immutable value type:
  ```
  data class ReadingPaceTracker(
      private val samples: List<Sample> = emptyList(),
  ) {
      data class Sample(val wordsRead: Int, val elapsedMs: Long)
      fun record(wordsRead: Int, elapsedMs: Long): ReadingPaceTracker
      fun observedSpeed(): ReadingSpeed?
      companion object {
          const val MIN_SAMPLES = 3
          const val MAX_SAMPLES = 20
          const val MIN_WPM = 80.0
          const val MAX_WPM = 800.0
          const val MIN_SAMPLE_ELAPSED_MS = 3_000L
      }
  }
  ```
- `record` drops samples with `wordsRead <= 0` or `elapsedMs < MIN_SAMPLE_ELAPSED_MS` (too short to be a real reading interval — e.g. a fast skim/jump), then keeps only the last `MAX_SAMPLES` (rolling window).
- `observedSpeed()` returns `null` until at least `MIN_SAMPLES` valid samples exist (caller then uses `ReadingSpeed.DEFAULT`). Otherwise computes total-words / total-minutes across the window, clamps to `[MIN_WPM, MAX_WPM]`, returns `ReadingSpeed`.
- Keep it simple: no decay weighting, no persistence. Pace lives only for the reading session.

Steps:
- [ ] Write `ReadingPaceTrackerTest.kt` first (red):
  - New tracker `observedSpeed()` is `null`.
  - After 2 valid samples (still `< MIN_SAMPLES`) ⇒ still `null`.
  - After 3 samples of `(wordsRead = 300, elapsedMs = 60_000)` ⇒ `observedSpeed()!!.wordsPerMinute == 300.0`.
  - A sample with `elapsedMs = 1_000` (`< MIN_SAMPLE_ELAPSED_MS`) is ignored: three such + nothing else ⇒ `null`.
  - A `wordsRead = 0` sample is ignored.
  - Clamp high: 3 samples of `(2000 words, 60_000 ms)` (=2000 WPM) ⇒ clamped to `MAX_WPM` (800.0).
  - Clamp low: 3 samples of `(20 words, 60_000 ms)` (=20 WPM) ⇒ clamped to `MIN_WPM` (80.0).
  - Rolling window: recording `MAX_SAMPLES + 5` samples keeps `samples.size == MAX_SAMPLES` (assert via a test-visible `sampleCount()` accessor or by behavior — prefer adding `internal fun sampleCount() = samples.size`).
  - Immutability: `record` returns a new instance; the original still reports its prior `observedSpeed()`.
- [ ] Run tests, confirm red: `./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.reader.ReadingPaceTrackerTest"`
- [ ] Implement `ReadingPaceTracker.kt`.
- [ ] Run tests, confirm green.
- [ ] Self-review: pure (no Android/coroutine/IO imports); insufficient data ⇒ `null` ⇒ default WPM at the call site (spec §5 "keep it simple; default WPM if insufficient data").

## Task 3 — Wire content-length + estimate into `ReaderViewModel`

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModel.kt` (modify)

The VM currently knows `pageCount`, `currentPage`, and `progressPercent`. Add a content-length channel and recompute the estimate whenever position or length changes.

Steps:
- [ ] Add imports for `com.continuum.app.model.reader.ReadingContentLength`, `ReadingEstimator`, `ReadingSpeed`, `ReadingTimeEstimate`, `ReadingPaceTracker`.
- [ ] Add to `ReaderUiState`:
  ```
  val readingEstimate: ReadingTimeEstimate? = null,
  ```
  (placed after `progressPercent`).
- [ ] Add a private field for the content-length unit reported by the active reader:
  ```
  private var bookContentLength: ReadingContentLength? = null
  private var paceTracker = ReadingPaceTracker()
  private var lastPaceMarkMs: Long = 0L
  private var lastPaceWords: Int? = null
  ```
- [ ] Add `fun onContentLengthKnown(length: ReadingContentLength)` that stores `bookContentLength = length` and calls a private `recomputeEstimate()`.
- [ ] Implement `private fun recomputeEstimate()`:
  - Read current `progressPercent` and `bookContentLength`. If length is null, set `readingEstimate = null` and return.
  - Compute `bookRemaining` by scaling the total length by `(1.0 - progressPercent)`:
    - For `Words(total)` ⇒ `Words((total * (1 - p)).toInt())`.
    - For `Pages(total, wpp)` ⇒ `Pages(ceil(total * (1 - p)).toInt(), wpp)`.
    - For `Characters(total, cpw)` ⇒ `Characters((total * (1 - p)).toInt(), cpw)`.
    Implement this as a small private `ReadingContentLength.scaledRemaining(fraction: Double)` helper in the VM file (keep the estimator itself agnostic of progress).
  - Compute `chapterRemaining`: if `sections` are known, approximate the current chapter span as `totalLength / sections.size` and scale by the within-chapter fraction; when chapter granularity is unavailable, fall back to `chapterRemaining = bookRemaining` (the estimator handles both inputs; book label stays meaningful, chapter label degrades to whole-book until Phase 2 chapter word counts land). Keep the chapter approximation in a private helper with a one-line comment explaining the fallback.
  - `val speed = paceTracker.observedSpeed() ?: ReadingSpeed.DEFAULT`
  - `val estimate = ReadingEstimator.estimate(chapterRemaining, bookRemaining, speed)`
  - `_uiState.update { it.copy(readingEstimate = estimate) }`
- [ ] In `onPageChanged(...)`, after the `_uiState.update { … progressPercent … }` block, record a pace sample and recompute:
  - On each page change, if `lastPaceWords != null` and `bookContentLength != null`, compute `wordsRead = wordsBetween(previousPercent, newPercent, bookContentLength)` and `elapsedMs = now - lastPaceMarkMs`, then `paceTracker = paceTracker.record(wordsRead, elapsedMs)` (the tracker drops too-short/invalid samples itself).
  - Update `lastPaceMarkMs = now` and `lastPaceWords` bookkeeping.
  - Call `recomputeEstimate()`.
  - Keep this guarded so the initial-position suppression path (`shouldSuppressInitialPageChange`) does NOT record a pace sample (no real reading happened).
- [ ] Also call `recomputeEstimate()` at the end of `onPageCountKnown(...)` so a page-based length estimate appears even before any reflowable word count arrives. For PDF/comic, synthesize `bookContentLength = ReadingContentLength.Pages(count)` inside `onPageCountKnown` only when the active `format` is fixed-layout (PDF/CBZ) AND no richer `bookContentLength` was already set by `onContentLengthKnown`.
- [ ] Build the module to confirm it compiles: `./gradlew :androidApp:compileDebugKotlin`

## Task 4 — Feed content length from the format readers

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt` (modify — thread a new callback)
- `androidApp/.../reader/EpubReader.kt`, `TextReader.kt`, `FictionBookReader.kt` (modify)

Steps:
- [ ] In `ReaderScreen.kt`, in each reader invocation that can report words, pass `onContentLengthKnown = viewModel::onContentLengthKnown`. Add the parameter to the three reflowable readers' signatures (`EpubReader`, `TextReader`, `FictionBookReader`) with a default of `{ }` so call sites that do not yet compute words stay valid.
- [ ] `TextReader.kt`: after the text loads (`result.getOrThrow()`), in the existing `LaunchedEffect`, report `onContentLengthKnown(ReadingContentLength.Characters(text.length))`. Use the loaded text length; this is the cheapest accurate signal for plain text/markdown.
- [ ] `FictionBookReader.kt`: report `ReadingContentLength.Characters(totalBodyTextLength)` using the already-parsed body text (FB2 readers parse text to render it; reuse that string's length). If the current FB2 reader does not retain a combined text length, compute it once from the parsed sections.
- [ ] `EpubReader.kt`: Phase 2 (epub.js) is assumed to expose accurate word counts via its JS bridge. Where that bridge reports a total word count, call `onContentLengthKnown(ReadingContentLength.Words(totalWords))`. Until the epub.js word-count bridge is wired, leave the EPUB path on the page-based (`onPageCountKnown` ⇒ spine size) estimate — explicitly note this as the deferred item in code with a `// TODO(phase2): replace spine-count estimate with epub.js word count` comment. Do NOT block this phase on epub.js plumbing.
- [ ] PDF (`PdfReader.kt`) and Comic (`ComicReader.kt`): no change — `onPageCountKnown` already drives the page-based estimate via Task 3.
- [ ] Build: `./gradlew :androidApp:compileDebugKotlin`

## Task 5 — Surface the estimate in the reader chrome

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt` (modify, ~lines 99-105)

Steps:
- [ ] Replace the single progress `Text` (currently rendering `"${pct}% · Page N of M"`) with a `Row` that keeps the existing progress text on the left and adds the reading-time label on the right when available:
  ```kotlin
  Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
      Text(
          text = "${(state.progressPercent * 100).toInt()}% · Page ${state.currentPage + 1}" +
              state.pageCount?.let { " of $it" }.orEmpty(),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.weight(1f))
      state.readingEstimate?.let { estimate ->
          Text(
              text = estimate.bookLabel,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
      }
  }
  ```
- [ ] Show the chapter estimate as supporting detail only when it differs from the book label (avoid duplicate "~X min left" when chapter == book under the whole-book fallback): below the row, when `state.readingEstimate != null && estimate.chapterLabel != estimate.bookLabel`, render a small `Text(estimate.chapterLabel + " in chapter")` in `labelSmall`.
- [ ] Confirm the existing `syncError` / `isSyncing` text block below remains intact and unchanged.
- [ ] Build: `./gradlew :androidApp:compileDebugKotlin`

## Task 6 — Verify

**Files:** none (verification only)

Steps:
- [ ] Run the shared unit tests (estimator + pace tracker): `./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.reader.*"` — confirm all green.
- [ ] Run the full shared + androidApp unit suites to catch regressions: `./gradlew :shared:testDebugUnitTest :androidApp:testDebugUnitTest`
- [ ] Lint/format gate per repo conventions: `./gradlew ktlintCheck` (or the project's configured Kotlin lint task; if absent, skip and note it).
- [ ] Manual on-device verification (spec §10 "reading-time sanity"):
  - [ ] Build + install: `./gradlew :androidApp:installDebug`
  - [ ] Launch the reader on a TXT or EPUB book: `adb shell am start -n <applicationId>/.MainActivity` (resolve the real `applicationId` from `androidApp/build.gradle.kts`; e.g. `adb shell monkey -p <applicationId> -c android.intent.category.LAUNCHER 1` to cold-launch), then navigate to a book and open it.
  - [ ] Confirm a `"~X min left"` label appears on the right of the progress row. Page forward several times and confirm the remaining-minutes value decreases monotonically (allowing for whole-minute rounding).
  - [ ] Open a PDF and confirm a page-based estimate still appears (proves the fixed-layout fallback).
  - [ ] Capture logcat during the session to confirm no estimator-related crashes: `adb logcat -d | /usr/bin/grep -iE "ReadingEstimator|ReaderViewModel|AndroidRuntime" | tail -40`

## Self-review against spec §5 / §8

- [ ] Pure `ReadingEstimator` in `shared/commonMain` computing "~X min left in chapter" and "~X min left in book" from remaining content length × reading speed — Tasks 1 + 3. ✓
- [ ] Default WPM with optional observed-pace refinement, simple, default fallback when data is thin — Task 2 + Task 3 wiring (`observedSpeed() ?: ReadingSpeed.DEFAULT`). ✓
- [ ] Surfaced in reader chrome near the progress display — Task 5 (Row beside the existing progress text at lines 99-105). ✓
- [ ] Handles every format: words (EPUB/text/FB2), characters (plain text), pages (PDF/comic) via the sealed `ReadingContentLength`. ✓
- [ ] Edge cases tested: zero/last-page (0 words ⇒ "Done"), rounding (ceil), default-WPM fallback, hour formatting. ✓

**Deferred (out of this phase, noted in code):**
- EPUB accurate per-chapter and total word counts via the epub.js JS bridge (Phase 2 dependency). Until wired, EPUB uses the spine-count/page-based estimate. `// TODO(phase2)` in `EpubReader.kt`.
- Persisting observed pace across sessions (kept in-memory only, per "keep it simple").
- Per-chapter precise spans for EPUB (chapter label falls back to whole-book until Phase 2 chapter word counts land).
