# Ebook Reader — Phase 1: ReaderLocator + Generic Progress — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Introduce a single typed position abstraction, `ReaderLocator`, that every format reader maps to and from, and refactor the progress path (ReaderViewModel → `EbookLocalStateStore` → `EbookReaderApi` models) to carry it as typed JSON while still reading legacy `"page:N"` progress rows. This is Phase 1 of the ebook-reader-enhancements design (`docs/superpowers/specs/2026-06-12-ebook-reader-enhancements-design.md`, §3, §8 item 1). Phases 2–7 (paginated EPUB, fonts/brightness, highlights, search, reading-time, per-format polish) are out of scope here.

**Architecture:** `ReaderLocator` is a `@Serializable` sealed class living in the KMP `shared` module (`commonMain`), so Apple/TV can adopt it later. It serializes to a typed JSON discriminated by a `type` field (`cfi` / `page` / `char_offset` / `page_rect`). A companion `ReaderLocator.parse(String)` accepts BOTH the new typed JSON AND the legacy `"page:N"` string, returning a `Page` locator for the latter — this preserves backward compatibility with every existing local `*.progress.json` row and server `location` value. The wire `location` field stays a `String` end-to-end (no API/DB shape change in Phase 1): readers encode a `ReaderLocator` to its string form before saving and decode on load. Each format reader keeps emitting integer page indices through the existing `onPageChanged(Int)` callback; the ViewModel is the single place that converts page index ⇄ `ReaderLocator` for now, mapping per format (EPUB→Page, PDF→Page, comic→Page, text/FB2→Page), with `Cfi`, `CharOffset`, and `PageRect` defined and round-trip-tested for use by later phases.

**Tech Stack:** Kotlin (KMP, `shared` commonMain + `android-shared` androidMain), kotlinx.serialization (`ContinuumJson` config from `ContinuumHttpClientImpl.kt`), JUnit (kotlin-test in `shared/commonTest`, JUnit4 + kotlin-test in `android-shared/androidUnitTest`).

---

## File Structure

Created:

- `shared/src/commonMain/kotlin/com/continuum/app/model/reader/ReaderLocator.kt` — the sealed `ReaderLocator` type (`Cfi`, `Page`, `CharOffset`, `PageRect`), its `@Serializable` polymorphic config, and `encodeToString()` / `parse(String)` helpers that round-trip typed JSON and parse the legacy `"page:N"` string.
- `shared/src/commonTest/kotlin/com/continuum/app/model/reader/ReaderLocatorTest.kt` — round-trip unit tests for each variant plus legacy `"page:N"` backward-compat parse tests.

Modified:

- `shared/src/commonMain/kotlin/com/continuum/app/model/ebook/EbookVersionSelection.kt` — add `readerLocatorPageIndex(...)` / `progressLocationForPage(...)` helpers built on `ReaderLocator`; keep the existing `ebookPageNumberFromProgressLocation` delegating to the new locator parse so legacy callers stay valid.
- `androidApp/.../ui/screens/reader/ReaderViewModel.kt` — convert page index ⇄ `ReaderLocator` when reading/writing progress and bookmarks instead of building raw `"page:N"` strings inline.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/EbookLocalStateStore.kt` — `ProgressSnapshot.location` continues to persist the locator's string form; no schema change, verified by a backward-compat read test.
- `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/EbookLocalStateStoreTest.kt` — add a test proving an old `page:N` `*.progress.json` still reads.

Unchanged in Phase 1 (documented for clarity): `EbookReaderModels.kt` / `EbookReaderApi.kt` `location` fields remain `String`; the typed-locator-on-the-wire generalization is deferred to Phase 4 server coordination (§6).

---

### Task 1 — Add `ReaderLocator` sealed type with typed-JSON + legacy `page:N` parsing

Define the model and prove its serialization contract with tests first.

**Files:**

- Create `shared/src/commonMain/kotlin/com/continuum/app/model/reader/ReaderLocator.kt`
- Create `shared/src/commonTest/kotlin/com/continuum/app/model/reader/ReaderLocatorTest.kt`

Steps:

- [ ] Write the failing test first. Create `ReaderLocatorTest.kt` with the following exact content:

  ```kotlin
  package com.continuum.app.model.reader

  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertNull
  import kotlin.test.assertTrue

  class ReaderLocatorTest {

      private fun roundTrip(locator: ReaderLocator) {
          val encoded = locator.encodeToString()
          assertEquals(locator, ReaderLocator.parse(encoded))
      }

      @Test
      fun cfiRoundTrips() {
          roundTrip(ReaderLocator.Cfi("epubcfi(/6/4[chap01]!/4/2/2/1:0)"))
      }

      @Test
      fun pageRoundTrips() {
          roundTrip(ReaderLocator.Page(index = 12))
      }

      @Test
      fun charOffsetRoundTrips() {
          roundTrip(ReaderLocator.CharOffset(offset = 4096))
      }

      @Test
      fun pageRectRoundTrips() {
          roundTrip(ReaderLocator.PageRect(page = 3, rectN = ReaderLocator.NormalizedRect(0.1, 0.2, 0.3, 0.4)))
      }

      @Test
      fun typedJsonIsDiscriminatedByType() {
          val encoded = ReaderLocator.Page(index = 7).encodeToString()
          assertTrue(encoded.contains("\"type\""), "expected a discriminated type field in: $encoded")
          assertTrue(encoded.contains("\"page\""), "expected the page discriminator in: $encoded")
      }

      @Test
      fun legacyPageStringParsesToPage() {
          assertEquals(ReaderLocator.Page(index = 5), ReaderLocator.parse("page:5"))
      }

      @Test
      fun legacyPageStringWithWhitespaceParses() {
          assertEquals(ReaderLocator.Page(index = 0), ReaderLocator.parse("  page:0  "))
      }

      @Test
      fun blankOrNonsenseParsesToNull() {
          assertNull(ReaderLocator.parse(""))
          assertNull(ReaderLocator.parse("   "))
          assertNull(ReaderLocator.parse("page:-1"))
          assertNull(ReaderLocator.parse("not a locator"))
      }

      @Test
      fun pageLocatorToLegacyStringIsStable() {
          assertEquals("page:9", ReaderLocator.Page(index = 9).toLegacyString())
      }
  }
  ```

- [ ] Run the test and confirm it FAILS to compile (the `ReaderLocator` symbol does not yet exist):

  ```
  cd /Users/dev/projects/silo/prairie-android && ./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.reader.ReaderLocatorTest"
  ```

  Expected: build failure / unresolved reference `ReaderLocator`.

- [ ] Write the minimal real implementation. Create `ReaderLocator.kt` with the following exact content:

  ```kotlin
  package com.continuum.app.model.reader

  import kotlinx.serialization.SerialName
  import kotlinx.serialization.Serializable
  import kotlinx.serialization.json.Json

  /**
   * A single reading-position abstraction every format maps to and from
   * (design §3). Reflowable formats (EPUB/FB2/TXT/MD) can anchor on a
   * [Cfi] or [CharOffset]; fixed-layout formats anchor on a [Page]
   * (comic/PDF) or [PageRect] (PDF region). Serializes to a typed JSON
   * discriminated by a `type` field and supersedes the legacy `"page:N"`
   * string, which [parse] still accepts for backward compatibility.
   */
  @Serializable
  sealed class ReaderLocator {

      @Serializable
      @SerialName("cfi")
      data class Cfi(val value: String) : ReaderLocator()

      @Serializable
      @SerialName("page")
      data class Page(val index: Int) : ReaderLocator()

      @Serializable
      @SerialName("char_offset")
      data class CharOffset(val offset: Int) : ReaderLocator()

      @Serializable
      @SerialName("page_rect")
      data class PageRect(val page: Int, val rectN: NormalizedRect) : ReaderLocator()

      /** Normalized [0,1] rectangle on a page (PDF highlight region). */
      @Serializable
      data class NormalizedRect(val left: Double, val top: Double, val right: Double, val bottom: Double)

      /** Encode to typed JSON for storage on the `location` wire field. */
      fun encodeToString(): String = JSON.encodeToString(serializer(), this)

      /**
       * The legacy `"page:N"` form, valid only for [Page]. Used while the
       * wire `location` field is still consumed by older readers/servers.
       */
      fun toLegacyString(): String? = (this as? Page)?.let { "page:${it.index}" }

      companion object {
          // Mirrors ContinuumJson (ContinuumHttpClientImpl.kt) so locator
          // JSON round-trips identically to every other API payload.
          private val JSON = Json {
              ignoreUnknownKeys = true
              isLenient = true
              encodeDefaults = true
              explicitNulls = false
              coerceInputValues = true
              classDiscriminator = "type"
          }

          /**
           * Parse a stored `location` value. Accepts the new typed JSON
           * AND the legacy `"page:N"` string. Returns null for blank or
           * unrecognized input so callers fall back to a default position.
           */
          fun parse(raw: String?): ReaderLocator? {
              val trimmed = raw?.trim().orEmpty()
              if (trimmed.isEmpty()) return null
              parseLegacyPage(trimmed)?.let { return it }
              return runCatching { JSON.decodeFromString(serializer(), trimmed) }.getOrNull()
          }

          private fun parseLegacyPage(trimmed: String): Page? =
              trimmed
                  .takeIf { it.startsWith("page:") }
                  ?.removePrefix("page:")
                  ?.trim()
                  ?.toIntOrNull()
                  ?.takeIf { it >= 0 }
                  ?.let { Page(it) }
      }
  }
  ```

- [ ] Run the test and confirm it PASSES:

  ```
  cd /Users/dev/projects/silo/prairie-android && ./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.reader.ReaderLocatorTest"
  ```

  Expected: `BUILD SUCCESSFUL`, all 9 test methods green.

- [ ] Commit:

  ```
  cd /Users/dev/projects/silo/prairie-android && git add shared/src/commonMain/kotlin/com/continuum/app/model/reader/ReaderLocator.kt shared/src/commonTest/kotlin/com/continuum/app/model/reader/ReaderLocatorTest.kt && git commit -m "feat(reader): add ReaderLocator sealed type with typed JSON + legacy page:N parse"
  ```

---

### Task 2 — Route page ⇄ locator conversion through shared helpers

Replace the scattered string handling with locator-backed helpers so every reader and the ViewModel share one conversion path. Keep the existing `ebookPageNumberFromProgressLocation` name working (it is called from `ReaderScreen.kt` and `ReaderViewModel.kt`) by delegating it to `ReaderLocator.parse`.

**Files:**

- Modify `shared/src/commonMain/kotlin/com/continuum/app/model/ebook/EbookVersionSelection.kt`
- Modify `shared/src/commonTest/kotlin/com/continuum/app/model/ebook/EbookVersionSelectionTest.kt`

Steps:

- [ ] Write the failing test first. Append these test methods to the existing `EbookVersionSelectionTest` class (inside the class body, before its closing brace), keeping the file's current imports and adding `import com.continuum.app.model.reader.ReaderLocator` to the import block:

  ```kotlin
      @Test
      fun legacyPageLocationStillResolvesToPageIndex() {
          assertEquals(12, ebookPageNumberFromProgressLocation("page:12"))
          assertNull(ebookPageNumberFromProgressLocation("page:-1"))
          assertNull(ebookPageNumberFromProgressLocation(null))
      }

      @Test
      fun typedPageLocatorJsonResolvesToPageIndex() {
          val json = ReaderLocator.Page(index = 8).encodeToString()
          assertEquals(8, ebookPageNumberFromProgressLocation(json))
      }

      @Test
      fun progressLocationForPageEncodesTypedLocator() {
          val location = progressLocationForPage(3)
          assertEquals(ReaderLocator.Page(index = 3), ReaderLocator.parse(location))
      }
  ```

  (If `assertNull` is not already imported in that test file, add `import kotlin.test.assertNull`.)

- [ ] Run the test and confirm it FAILS (unresolved `progressLocationForPage`, and `ebookPageNumberFromProgressLocation` does not yet understand typed JSON):

  ```
  cd /Users/dev/projects/silo/prairie-android && ./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.ebook.EbookVersionSelectionTest"
  ```

  Expected: compile failure on `progressLocationForPage` (and, once that is added, a failing assertion on the typed-JSON case until the body is updated).

- [ ] Update `EbookVersionSelection.kt`. Add the import `import com.continuum.app.model.reader.ReaderLocator`, then replace the existing `ebookPageNumberFromProgressLocation` body and add `progressLocationForPage`:

  ```kotlin
  /**
   * Page index for a stored progress `location`. Accepts both the typed
   * [ReaderLocator] JSON and the legacy `"page:N"` string; only [Page]
   * locators have a page index, so reflowable [Cfi]/[CharOffset] forms
   * return null (callers keep their current page in that case).
   */
  fun ebookPageNumberFromProgressLocation(location: String?): Int? =
      (ReaderLocator.parse(location) as? ReaderLocator.Page)?.index

  /** Typed-JSON `location` string for a fixed-layout page index. */
  fun progressLocationForPage(page: Int): String =
      ReaderLocator.Page(index = page.coerceAtLeast(0)).encodeToString()
  ```

- [ ] Run the test and confirm it PASSES:

  ```
  cd /Users/dev/projects/silo/prairie-android && ./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.ebook.EbookVersionSelectionTest"
  ```

  Expected: `BUILD SUCCESSFUL`, including the three new methods and all pre-existing `EbookVersionSelectionTest` cases.

- [ ] Commit:

  ```
  cd /Users/dev/projects/silo/prairie-android && git add shared/src/commonMain/kotlin/com/continuum/app/model/ebook/EbookVersionSelection.kt shared/src/commonTest/kotlin/com/continuum/app/model/ebook/EbookVersionSelectionTest.kt && git commit -m "feat(reader): resolve progress location via ReaderLocator (typed JSON + legacy page:N)"
  ```

---

### Task 3 — Prove `EbookLocalStateStore` reads legacy and typed locator rows

`EbookLocalStateStore.ProgressSnapshot.location` is a plain `String`; it already persists whatever the ViewModel writes. Phase 1 changes the *content* of that string from `"page:N"` to typed-locator JSON, so add a regression test proving (a) an existing on-disk `"page:N"` row still round-trips, and (b) a typed-locator JSON value persists and re-reads byte-for-byte. No production change to the store is expected — if both tests pass without editing `EbookLocalStateStore.kt`, that is the desired outcome (the store is locator-agnostic).

**Files:**

- Modify `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/EbookLocalStateStoreTest.kt`
- (Only if a test fails) Modify `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/EbookLocalStateStore.kt`

Steps:

- [ ] Write the test first. Add these two methods to `EbookLocalStateStoreTest` (add `import com.continuum.app.model.reader.ReaderLocator` to the imports):

  ```kotlin
      @Test
      fun `legacy page location progress row still reads`() {
          val store = EbookLocalStateStore(tmp.newFolder("filesDir"))
          val legacy = EbookLocalStateStore.ProgressSnapshot(
              fileId = 7,
              location = "page:12",
              progress = 0.25,
              updatedAtMs = 42L,
          )

          store.writeProgress("srv", "prof", "book", legacy)

          val read = store.readProgress("srv", "prof", "book")
          assertEquals(legacy, read)
          assertEquals(ReaderLocator.Page(index = 12), ReaderLocator.parse(read?.location))
      }

      @Test
      fun `typed locator location progress row round trips`() {
          val store = EbookLocalStateStore(tmp.newFolder("filesDir"))
          val typed = EbookLocalStateStore.ProgressSnapshot(
              fileId = 7,
              location = ReaderLocator.Page(index = 30).encodeToString(),
              progress = 0.5,
              updatedAtMs = 99L,
          )

          store.writeProgress("srv", "prof", "book", typed)

          val read = store.readProgress("srv", "prof", "book")
          assertEquals(typed, read)
          assertEquals(ReaderLocator.Page(index = 30), ReaderLocator.parse(read?.location))
      }
  ```

- [ ] Run the test:

  ```
  cd /Users/dev/projects/silo/prairie-android && ./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.ebook.EbookLocalStateStoreTest"
  ```

  Expected: `BUILD SUCCESSFUL`. (`android-shared` already depends on `:shared` via `implementation(project(":shared"))`, so the `ReaderLocator` import resolves. If it does not compile, that signals the store module is missing the shared dependency — it is not — so the expected outcome is green with no production edit.)

- [ ] If and only if a test fails: fix the cause (do not weaken the test) and re-run until green. Otherwise no production edit is needed.

- [ ] Commit:

  ```
  cd /Users/dev/projects/silo/prairie-android && git add android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/EbookLocalStateStoreTest.kt && git commit -m "test(reader): assert EbookLocalStateStore reads legacy and typed locator progress rows"
  ```

---

### Task 4 — Carry `ReaderLocator` through the ViewModel progress + bookmark path

`ReaderViewModel.onPageChanged` and `addBookmark` build `"page:$normalizedPage"` strings by hand. Replace those with the `progressLocationForPage` helper so progress and bookmarks persist typed-locator JSON, while load still tolerates legacy rows (via `ebookPageNumberFromProgressLocation`, already locator-backed from Task 2). This is the per-format mapping for Phase 1: EPUB/PDF/comic/text/FB2 all flow integer page indices through `onPageChanged`, and the ViewModel is the single conversion point (EPUB→`Page` for now, per design §3 "or Page"; richer `Cfi`/`PageRect`/`CharOffset` anchors arrive with their renderers in later phases).

**Files:**

- Modify `androidApp/.../ui/screens/reader/ReaderViewModel.kt`

Steps:

- [ ] Note: the `androidApp` reader composables are Compose UI with no existing unit-test harness for `ReaderViewModel` (the `androidApp/src/androidUnitTest` tree holds util/navigation tests, not VM tests). The locator conversion logic itself is already unit-tested at its source in Tasks 1–3 (`progressLocationForPage`, `ebookPageNumberFromProgressLocation`, store round-trips). This task is a mechanical refactor to call those tested helpers; verify it by a clean compile of `androidApp` plus the full shared/android-shared suites, not by a new VM test.

- [ ] Add the import to `ReaderViewModel.kt`:

  ```kotlin
  import com.continuum.app.model.ebook.progressLocationForPage
  ```

- [ ] In `onPageChanged`, replace the inline string build:

  ```kotlin
          val location = "page:$normalizedPage"
  ```

  with:

  ```kotlin
          val location = progressLocationForPage(normalizedPage)
  ```

- [ ] In `addBookmark`, replace the inline fallback:

  ```kotlin
          val location = _uiState.value.progressLocation ?: "page:${_uiState.value.currentPage}"
  ```

  with:

  ```kotlin
          val location = _uiState.value.progressLocation ?: progressLocationForPage(_uiState.value.currentPage)
  ```

- [ ] Confirm `ReaderScreen.kt` needs no change: it already jumps via `ebookPageNumberFromProgressLocation(bookmark.location)?.let(viewModel::jumpToPage)` and `ebookPageNumberFromProgressLocation(section.location)`, both now locator-backed and tolerant of typed JSON and legacy `page:N`. The `BookmarkSheet` headline `Text(bookmark.location ?: "Saved bookmark")` will now display typed JSON for new bookmarks; leave the user-facing label improvement to Phase 4 (highlights UI) — note it here, do not gold-plate.

- [ ] Compile `androidApp` to confirm the refactor type-checks:

  ```
  cd /Users/dev/projects/silo/prairie-android && ./gradlew :androidApp:compileDebugKotlin
  ```

  Expected: `BUILD SUCCESSFUL`.

- [ ] Run the full affected module suites to confirm nothing regressed:

  ```
  cd /Users/dev/projects/silo/prairie-android && ./gradlew :shared:testDebugUnitTest :android-shared:testDebugUnitTest :androidApp:testDebugUnitTest
  ```

  Expected: `BUILD SUCCESSFUL` across all three.

- [ ] Commit:

  ```
  cd /Users/dev/projects/silo/prairie-android && git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModel.kt && git commit -m "refactor(reader): persist progress + bookmarks as typed ReaderLocator JSON"
  ```

---

### Task 5 — Self-review against the spec and lint

- [ ] Re-read design §3 and §8 item 1. Confirm Phase 1 deliverables are met:
  - `ReaderLocator` sealed type with all four variants (`Cfi`, `Page`, `CharOffset`, `PageRect`) — Task 1.
  - Serializable to typed JSON — Task 1 (`encodeToString`, discriminated by `type`).
  - Parser also accepts legacy `"page:N"` — Task 1 (`parse`), Task 2 (delegation), Tasks 1/3 (tests).
  - Progress path (ViewModel + store + models) carries a `ReaderLocator` serialized to typed JSON while still reading old `page:N` rows — Tasks 2, 3, 4.
  - Each format reader maps its position to/from `ReaderLocator`: all formats flow page indices through `onPageChanged`, converted to `Page` locators centrally in the ViewModel (Task 4); `Cfi`/`PageRect`/`CharOffset` are defined and round-trip-tested (Task 1) for the renderers that arrive in Phases 2 and 7.

- [ ] Confirm the deferred boundary is honored: `EbookReaderModels.kt` / `EbookReaderApi.kt` `location` fields stay `String` (no wire/DB shape change), since the typed-locator-on-the-wire + annotation generalization is design §6 / Phase 4 server-coordinated work. If any task touched those files, revert that portion.

- [ ] Verify no inline `"page:` string literals remain in the reader path except the legacy-parse branch inside `ReaderLocator.parse`:

  ```
  cd /Users/dev/projects/silo/prairie-android && /usr/bin/grep -rn "page:" androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader shared/src/commonMain/kotlin/com/continuum/app/model
  ```

  Expected: the only `"page:"` producers are `EpubReader.kt`'s `ReaderSection(location = "page:$index")` (section TOC anchors, untouched in Phase 1 — acceptable, parsed by the locator) and the legacy branch in `ReaderLocator.kt` / `EbookVersionSelection` comments. Note any others and fix if they bypass the helper.

- [ ] Run lint on changed Kotlin (the repo's standard pre-MR gate):

  ```
  cd /Users/dev/projects/silo/prairie-android && ./gradlew :shared:lintKotlinCommonMain :androidApp:lintDebug
  ```

  If these Gradle lint tasks are not configured in this repo, fall back to the project's documented lint command and run that instead; do not invent a task name. Fix any reported issues inline.

- [ ] Final full run to confirm everything is green together:

  ```
  cd /Users/dev/projects/silo/prairie-android && ./gradlew :shared:testDebugUnitTest :android-shared:testDebugUnitTest :androidApp:testDebugUnitTest
  ```

  Expected: `BUILD SUCCESSFUL`.

- [ ] If self-review surfaced any fix, commit it:

  ```
  cd /Users/dev/projects/silo/prairie-android && git add -A && git commit -m "chore(reader): phase 1 self-review fixes for ReaderLocator"
  ```

---

## Deferred to later phases (NOT in this plan)

- Typed `ReaderLocator` JSON on the API/DB wire (`EbookReaderModels` / `EbookReaderApi` `location` + `cfiRange` generalization) and the Goose migration — design §6, Phase 4 (server-coordinated).
- Real CFI locators from a paginated EPUB renderer (epub.js) — Phase 2.
- `CharOffset` emission from a paginated text/FB2 renderer and `PageRect` from PDF highlight overlays — Phases 2 and 7 (the variants are defined and tested now so those phases just wire them up).
- Font family + brightness, highlights/notes + selection UI, in-text search, reading-time estimates — Phases 3–6.
