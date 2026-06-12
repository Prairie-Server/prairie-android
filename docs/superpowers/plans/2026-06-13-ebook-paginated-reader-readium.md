# Ebook Reader — Paginated EPUB via Readium — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Supersedes** `docs/superpowers/plans/2026-06-12-ebook-phase2-paginated-epub.md` (the epub.js approach). We chose the **Readium Kotlin toolkit** instead. Do not implement both.

**Goal:** Replace the "bones-level" WebView EPUB renderer (`EpubReader.kt`, a `HorizontalPager` over chapters that scrolls vertically) with a Readium-based paginated reader that delivers real fixed pages, accurate page turns, **pinch-to-zoom font scaling that reflows the pages**, and locator-based progress that resumes exactly where the reader left off — including across devices and re-paginations.

**Architecture:** Adopt the Readium Kotlin toolkit (`org.readium.kotlin-toolkit`). Open the on-disk EPUB as a Readium `Publication`; render it with `EpubNavigatorFragment` hosted inside Compose via an `AndroidView` + `FragmentContainerView`. The navigator owns pagination + reflow; we drive it with `EpubPreferences` (font size, theme, margins) mapped from the app's existing `ReaderDisplaySettings`, and we add a Compose pinch gesture that nudges the font scale. Reading position is a Readium `Locator` serialized to JSON and stored in the existing `progressLocation` string (no new server contract); `progressPercent` continues to carry the book-level fraction. EPUB is the only format that moves to Readium — PDF / CBZ / TXT / FB2 keep their current renderers.

**Tech Stack:** Kotlin, Jetpack Compose (`AndroidView`), AndroidX Fragment (`FragmentContainerView`, `FragmentManager`), **Readium Kotlin toolkit** (`readium-shared`, `readium-streamer`, `readium-navigator`, `readium-adapter-pdfium` not required for EPUB), `kotlinx.serialization` (Locator JSON ↔ progress string), Koin DI, Robolectric (existing androidApp unit-test runner), JUnit/kotlin.test.

Commands assume the repository root (`silo-android`) is the cwd.

---

## Current-state facts this plan is built on (verified in the tree)

- `androidApp/.../ui/screens/reader/EpubReader.kt` is a `HorizontalPager` whose pages are **spine entries (chapters)**; each chapter is a vertically-scrolling `WebView`. There is **no within-chapter pagination**. Its own header comment calls it "bones-level … real fixed-page reflow lands when we wire epub.js or Readium."
- `androidApp/.../ui/screens/reader/ReaderScreen.kt` dispatches by `BookFormat`: `Epub → EpubReader`, `Pdf → PdfReader`, `Cbz → ComicReader`, `Txt/Markdown → TextReader`, `Fb2/Fbz → FictionBookReader`. The EPUB call passes `initialPage: Int`, `settings: ReaderDisplaySettings`, `onPageChanged: (Int) -> Unit`, `onPageCountKnown: (Int) -> Unit`, `onSectionsKnown: (List<ReaderSection>) -> Unit`.
- Reading position today is **page-index based**: `ReaderViewModel` exposes `state.currentPage: Int`, `state.pageCount: Int?`, `state.progressPercent: Double`, and a `progressLocation: String?` (currently `"page:N"`). Local + server reconciliation already exists in `ReaderViewModel.loadReaderState()` (local snapshot first, then `ebookReaderRepository.getProgress()`); progress is persisted via `ebookReaderRepository.saveProgress(contentId, SaveEbookProgressRequest(fileId, location, progress))` **and** `EbookLocalStateStore.writeProgress(...)`. The offline→online `EbookProgressSyncer` (already shipped) pushes `location` + `progress` to the server — it is **location-agnostic**, so switching EPUB's `location` to Locator JSON requires no syncer change.
- `ReaderDisplaySettings` (`shared`/`android-shared` `com.continuum.app.common.ebook`): `theme: ReaderTheme` (`System | Light | Dark | Sepia`), `textScale: Float`, `marginScale: Float`, `fontFamily: ReaderFontFamily`. The settings sheet (`ReaderScreen.ReaderSettingsSheet`) already edits these and `ReaderViewModel.setDisplaySettings` persists them.
- **`ReaderLocator` (a typed sealed locator from the deferred "Phase 1") does NOT exist.** This plan does **not** depend on it. EPUB locators are stored as Readium `Locator` JSON directly in the existing `progressLocation` string; legacy `"page:N"` rows are tolerated (open at the start of the book).
- `MainActivity` (`androidApp/.../MainActivity.kt`) is a **`ComponentActivity`**. `EpubNavigatorFragment` requires a `FragmentActivity` + a `FragmentManager`. **Task 2 changes the host activity** — this is the highest-risk change and is sequenced first after deps.
- App module: `minSdk = 24`, `compileSdk = 36`, `targetSdk = 35` (Readium requires minSdk 21+, fine).

## File Structure

### Created

- `gradle/libs.versions.toml` — **modified**: add a `readium` version + `readium-shared` / `readium-streamer` / `readium-navigator` library aliases.
- `androidApp/build.gradle.kts` — **modified**: add the three Readium deps + `androidx.fragment:fragment-ktx` (if not already present).
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/readium/ReadiumLocatorCodec.kt` — **New.** Pure Kotlin: `Locator → JSON string` / `JSON string → Locator?`, plus `decodeLegacyOrLocator(location: String?)` that tolerates the old `"page:N"`. Unit-tested.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/readium/ReadiumPublicationOpener.kt` — **New.** Opens a local EPUB file as a Readium `Publication` (suspend, IO). Wraps `AssetRetriever` + `PublicationOpener`. Returns a typed `Result`.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/readium/EpubPreferencesMapper.kt` — **New.** Pure Kotlin: `ReaderDisplaySettings (+ systemDark: Boolean) → EpubPreferences`. Unit-tested.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/readium/ReadiumEpubReader.kt` — **New.** The Compose renderer that hosts `EpubNavigatorFragment`, applies preferences, restores the initial locator, reports locator changes, and overlays the pinch-zoom gesture. Replaces `EpubReader` for `BookFormat.Epub`.
- `androidApp/src/androidUnitTest/.../reader/readium/ReadiumLocatorCodecTest.kt` — **New.**
- `androidApp/src/androidUnitTest/.../reader/readium/EpubPreferencesMapperTest.kt` — **New.**

### Modified

- `androidApp/.../MainActivity.kt` — change base class `ComponentActivity` → `androidx.fragment.app.FragmentActivity` (Compose `setContent` works unchanged on `FragmentActivity`).
- `androidApp/.../ui/screens/reader/ReaderScreen.kt` — EPUB branch calls `ReadiumEpubReader` with `initialLocator: String?` + `onLocatorChanged: (locationJson: String, progress: Double) -> Unit`; keep all other format branches untouched.
- `androidApp/.../ui/screens/reader/ReaderViewModel.kt` — add EPUB locator plumbing: expose `state.initialLocator: String?` (from the reconciled progress `location`), add `onLocatorChanged(locationJson, progress)` that updates percent + persists via the existing save path. Page-index callbacks (`onPageChanged`/`onPageCountKnown`) stay for PDF/CBZ/TXT/FB2.
- `androidApp/.../ui/screens/reader/EpubReader.kt` — **deleted** once `ReadiumEpubReader` is wired and verified (its `EpubBook` helper stays only if still used by FB2; verify and remove if orphaned).

---

### Task 1 — Add Readium + Fragment dependencies

**Files:** `gradle/libs.versions.toml`, `androidApp/build.gradle.kts`

- [ ] **Step 1: Pin the Readium version.** In `libs.versions.toml`, add under `[versions]`:
  ```toml
  readium = "3.0.3"   # pin the latest stable org.readium.kotlin-toolkit at implementation time
  ```
  and under `[libraries]`:
  ```toml
  readium-shared = { module = "org.readium.kotlin-toolkit:readium-shared", version.ref = "readium" }
  readium-streamer = { module = "org.readium.kotlin-toolkit:readium-streamer", version.ref = "readium" }
  readium-navigator = { module = "org.readium.kotlin-toolkit:readium-navigator", version.ref = "readium" }
  ```
  > Before pinning, check the toolkit's current latest release (Maven Central `org.readium.kotlin-toolkit`); if `3.0.3` is stale, use the newest stable and adjust the API calls in later tasks to that version's signatures (the Readium API has changed across majors — treat the code in Tasks 4–6 as the shape, and reconcile names against the pinned version's docs).

- [ ] **Step 2: Add the deps.** In `androidApp/build.gradle.kts` `dependencies { }`:
  ```kotlin
  implementation(libs.readium.shared)
  implementation(libs.readium.streamer)
  implementation(libs.readium.navigator)
  implementation(libs.androidx.fragment.ktx) // add a fragment-ktx alias if the catalog lacks one
  ```
- [ ] **Step 3: Verify it resolves + builds.**
  Run: `./gradlew :androidApp:compileDebugKotlinAndroid`
  Expected: BUILD SUCCESSFUL (no code uses Readium yet; this only proves the artifacts resolve).
- [ ] **Step 4: Commit:** `build(reader): add Readium kotlin-toolkit + fragment deps`

---

### Task 2 — Make the host a FragmentActivity

**Files:** `androidApp/.../MainActivity.kt`

`EpubNavigatorFragment` is added to a `FragmentManager`, which a bare `ComponentActivity` does not provide a fragment host for. `FragmentActivity` is the minimal upgrade and keeps Compose `setContent { }` working unchanged.

- [ ] **Step 1: Change the base class.**
  ```kotlin
  // import androidx.fragment.app.FragmentActivity
  class MainActivity : FragmentActivity() {
      // body unchanged — setContent { } is provided by ComponentActivity, which
      // FragmentActivity extends.
  }
  ```
- [ ] **Step 2: Build.** Run: `./gradlew :androidApp:compileDebugKotlinAndroid` (expect PASS).
- [ ] **Step 3: Smoke-test on device — the whole app must still work.** Install and launch; navigate Home → Libraries → open a movie/audiobook. Confirm nothing regressed (Compose, navigation, players). This is the risk checkpoint for the activity change.
  `./gradlew :androidApp:assembleDebug && adb install -r androidApp/build/outputs/apk/debug/androidApp-arm64-v8a-debug.apk`
- [ ] **Step 4: Commit:** `refactor(app): host MainActivity as FragmentActivity for Readium`

---

### Task 3 — Locator ↔ progress-string codec (TDD)

**Files:**
- `androidApp/.../ui/screens/reader/readium/ReadiumLocatorCodec.kt` (create)
- `androidApp/src/androidUnitTest/.../reader/readium/ReadiumLocatorCodecTest.kt` (create)

Readium `Locator` already serializes to/from JSON (`Locator.toJSON()` / `Locator.fromJSON(JSONObject)`). The codec wraps that as `String?`-friendly helpers and isolates the legacy fallback so the renderer never touches JSON parsing directly.

```kotlin
object ReadiumLocatorCodec {
    /** Locator -> compact JSON string for the progress `location` field. */
    fun encode(locator: Locator): String = locator.toJSON().toString()

    /** Stored `location` -> Locator. Returns null for the legacy "page:N"
     *  form or anything unparseable, so the caller opens at the start. */
    fun decode(location: String?): Locator? {
        if (location.isNullOrBlank() || location.startsWith("page:")) return null
        return runCatching { Locator.fromJSON(JSONObject(location)) }.getOrNull()
    }
}
```

- [ ] **Step 1: Failing test** `ReadiumLocatorCodecTest`:
  - `encode` then `decode` round-trips a Locator (href + locations.progression preserved).
  - `decode("page:3")` returns `null`.
  - `decode(null)` / `decode("")` / `decode("{garbage")` return `null`.
- [ ] **Step 2: Run (expect FAIL — symbol undefined):**
  `./gradlew :androidApp:testDebugUnitTest --tests "*ReadiumLocatorCodecTest"`
- [ ] **Step 3: Implement** `ReadiumLocatorCodec` against the pinned Readium `Locator` JSON API.
- [ ] **Step 4: Run (expect PASS).**
- [ ] **Step 5: Commit:** `feat(reader): Readium locator <-> progress-string codec`

---

### Task 4 — Open a local EPUB as a Readium Publication

**Files:** `androidApp/.../ui/screens/reader/readium/ReadiumPublicationOpener.kt` (create)

The reader already resolves a readable local file via `resolveReaderFile(context, okHttp, fileUrl, serverUrl, "epub")` (used by `EpubReader`). Reuse it to get a `File`, then open it with Readium.

```kotlin
class ReadiumPublicationOpener(private val context: Context) {
    private val assetRetriever = AssetRetriever(context.contentResolver, DefaultHttpClient())
    private val publicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(context, assetRetriever.httpClient, assetRetriever, pdfFactory = null),
    )

    /** Open [file] (a local .epub) as a Readium Publication. IO-bound. */
    suspend fun open(file: File): Result<Publication> = withContext(Dispatchers.IO) {
        val asset = assetRetriever.retrieve(file).getOrElse { return@withContext Result.failure(IllegalStateException("asset: $it")) }
        publicationOpener.open(asset, allowUserInteraction = false)
            .map { it }
            .getOrElse { Result.failure(IllegalStateException("open: $it")) }
            .let { Result.success(it) }
    }
}
```

> Reconcile the exact `AssetRetriever` / `PublicationOpener` / `DefaultPublicationParser` constructors against the pinned Readium version — these moved across 2.x→3.x. The contract this plan needs: `suspend fun open(file: File): Result<Publication>`.

- [ ] **Step 1: Implement** the opener with the pinned-version constructors.
- [ ] **Step 2: Register in Koin** (`androidApp/.../di/AndroidModule.kt`): `single { ReadiumPublicationOpener(androidContext()) }`.
- [ ] **Step 3: Build.** `./gradlew :androidApp:compileDebugKotlinAndroid` (PASS).
- [ ] **Step 4: Commit:** `feat(reader): open local EPUB as a Readium Publication`

*(No unit test — this is thin glue over Readium IO; covered by the device pass in Task 10.)*

---

### Task 5 — Map display settings → EpubPreferences (TDD)

**Files:**
- `androidApp/.../ui/screens/reader/readium/EpubPreferencesMapper.kt` (create)
- `androidApp/src/androidUnitTest/.../reader/readium/EpubPreferencesMapperTest.kt` (create)

```kotlin
object EpubPreferencesMapper {
    fun toPreferences(settings: ReaderDisplaySettings, systemDark: Boolean): EpubPreferences {
        val normalized = settings.normalized()
        val theme = when (normalized.theme) {
            ReaderTheme.System -> if (systemDark) Theme.DARK else Theme.LIGHT
            ReaderTheme.Light -> Theme.LIGHT
            ReaderTheme.Dark -> Theme.DARK
            ReaderTheme.Sepia -> Theme.SEPIA
        }
        return EpubPreferences(
            theme = theme,
            fontSize = normalized.textScale.toDouble(),       // 1.0 == 100%
            pageMargins = (normalized.marginScale * 1.0).toDouble(),
            // fontFamily mapping deferred to the fonts phase; leave default.
        )
    }
}
```

- [ ] **Step 1: Failing test** `EpubPreferencesMapperTest`:
  - `ReaderTheme.System` + `systemDark=true` → `Theme.DARK`; `systemDark=false` → `Theme.LIGHT`.
  - `ReaderTheme.Dark` → `Theme.DARK` regardless of `systemDark`; `Sepia` → `Theme.SEPIA`.
  - `textScale = 1.5f` → `fontSize == 1.5`.
- [ ] **Step 2: Run (expect FAIL).** `./gradlew :androidApp:testDebugUnitTest --tests "*EpubPreferencesMapperTest"`
- [ ] **Step 3: Implement** against the pinned Readium `EpubPreferences` / `Theme` symbols (reconcile field names; e.g. `fontSize`, `pageMargins`, `theme`).
- [ ] **Step 4: Run (expect PASS).**
- [ ] **Step 5: Commit:** `feat(reader): map ReaderDisplaySettings to Readium EpubPreferences`

---

### Task 6 — ReadiumEpubReader: host the navigator + restore/report locator

**Files:** `androidApp/.../ui/screens/reader/readium/ReadiumEpubReader.kt` (create)

Hosts `EpubNavigatorFragment` in Compose. Hosting recipe:
1. Require the host to be a `FragmentActivity` (`LocalContext.current` → find activity). If not (shouldn't happen after Task 2), show an error composable.
2. `AndroidView` whose factory inflates a `FragmentContainerView` with a stable generated `id`.
3. On first composition, open the `Publication` (Task 4), build an `EpubNavigatorFactory(publication)`, create the fragment via its `createFragmentFactory(initialLocator = decoded)`, set it on the activity's `supportFragmentManager.fragmentFactory`, and `commitNow` the fragment into the container.
4. Collect the navigator's `currentLocator` `StateFlow` → call `onLocatorChanged(encode(locator), locator.locations.totalProgression ?: 0.0)` (debounced).
5. Re-apply preferences whenever `settings` (or `systemDark`) change: build `EpubPreferences` (Task 5) and submit them to the navigator's preferences editor / `submitPreferences`.

```kotlin
@Composable
fun ReadiumEpubReader(
    fileUrl: String,
    title: String,
    initialLocator: String?,
    settings: ReaderDisplaySettings,
    onLocatorChanged: (locationJson: String, progress: Double) -> Unit,
    onSectionsKnown: (List<ReaderSection>) -> Unit,
) {
    // resolve file (resolveReaderFile, IO) -> opener.open(file) -> Publication
    // EpubNavigatorFactory(publication).createFragmentFactory(
    //     initialLocator = ReadiumLocatorCodec.decode(initialLocator),
    //     initialPreferences = EpubPreferencesMapper.toPreferences(settings, isSystemInDarkTheme()),
    //     listener = ...,
    // )
    // AndroidView { FragmentContainerView(ctx).apply { id = generatedId } }
    //   update: commit fragment once; collect currentLocator; submit preferences on change
    // onSectionsKnown(publication.tableOfContents().toReaderSections())
}
```

> The fragment-in-Compose lifecycle is the delicate part: commit the fragment exactly once (guard with a remembered flag keyed on the container id + publication), and remove it in a `DisposableEffect { onDispose { fm.commitNow { remove(fragment) } } }`. Use `commitNow`/`commitNowAllowingStateLoss` to avoid races with Compose disposal.

- [ ] **Step 1: Implement** `ReadiumEpubReader` per the recipe, against the pinned Readium navigator API (`EpubNavigatorFactory`, `EpubNavigatorFragment`, `currentLocator`, preferences editor).
- [ ] **Step 2: Build.** `./gradlew :androidApp:compileDebugKotlinAndroid` (PASS).
- [ ] **Step 3: Commit:** `feat(reader): Readium EpubNavigatorFragment hosted in Compose`

*(Device verification in Task 10 — Compose+Fragment hosting isn't unit-testable here.)*

---

### Task 7 — Pinch-to-zoom font scaling

**Files:** `androidApp/.../ui/screens/reader/readium/ReadiumEpubReader.kt` (modify)

Overlay a transform-gesture detector above the navigator that converts a pinch into a `textScale` delta, hand it up so `ReaderViewModel.setDisplaySettings` persists it, and let the settings change flow back into `submitPreferences` (Task 6) — the navigator reflows the pages.

```kotlin
// Box(Modifier.pointerInput(Unit) {
//     detectTransformGestures { _, _, zoom, _ ->
//         if (zoom != 1f) onTextScaleNudge(zoom)   // multiply current textScale, clamp 0.6..3.0
//     }
// }) { AndroidView(...) }
```

- [ ] **Step 1: Add `onTextScaleNudge: (Float) -> Unit`** to `ReadiumEpubReader`; in `ReaderScreen`, wire it to `viewModel.nudgeTextScale(zoom)` (new VM method: `setDisplaySettings(current.copy(textScale = (current.textScale * zoom).coerceIn(0.6f, 3.0f)))`, debounced/coalesced so a pinch doesn't spam persistence).
- [ ] **Step 2: Ensure preferences re-apply** on the resulting `settings` change (already wired in Task 6) so the page count + layout reflow.
- [ ] **Step 3: Build.** `./gradlew :androidApp:compileDebugKotlinAndroid` (PASS).
- [ ] **Step 4: Commit:** `feat(reader): pinch-to-zoom font scaling with reflow`

---

### Task 8 — Wire EPUB branch to Readium; locator plumbing in the VM

**Files:** `androidApp/.../ui/screens/reader/ReaderScreen.kt`, `androidApp/.../ui/screens/reader/ReaderViewModel.kt`

- [ ] **Step 1: ViewModel.** Add `initialLocator: String?` to the reader ui-state, populated from the reconciled progress `location` in `loadReaderState()` (it already reads local then server). Add:
  ```kotlin
  fun onLocatorChanged(locationJson: String, progress: Double) {
      _uiState.update { it.copy(progressPercent = progress) }
      saveProgress(location = locationJson, progressPercent = progress) // existing path: local + server
  }
  ```
  Keep `onPageChanged`/`onPageCountKnown` for the non-EPUB renderers.
- [ ] **Step 2: ReaderScreen EPUB branch** → call `ReadiumEpubReader(fileUrl, title, initialLocator = state.initialLocator, settings = state.displaySettings, onLocatorChanged = viewModel::onLocatorChanged, onSectionsKnown = viewModel::setSections, onTextScaleNudge = viewModel::nudgeTextScale)`. Leave `Pdf/Cbz/Txt/Fb2` branches exactly as they are.
- [ ] **Step 3: Page-label fallback.** The header `"X% · Page N of M"` is page-index based. For EPUB, switch the label to the percentage + Readium's `currentLocator.title`/position when available (e.g. `"${pct}%"` only, or `"${pct}% · ${positionLabel}"`); guard so PDF/CBZ keep the page form.
- [ ] **Step 4: Build.** `./gradlew :androidApp:compileDebugKotlinAndroid` (PASS).
- [ ] **Step 5: Commit:** `feat(reader): EPUB uses Readium with locator-based progress`

---

### Task 9 — Sections / table of contents from Readium

**Files:** `androidApp/.../ui/screens/reader/readium/ReadiumEpubReader.kt`

The existing `SectionsSheet` consumes `List<ReaderSection>` and jumps via `onJumpTo(section)`. Map Readium's TOC to `ReaderSection` and route a jump to `navigator.go(link.locator)`.

- [ ] **Step 1:** `publication.tableOfContents()` (suspend) → `ReaderSection(index, title, location = ReadiumLocatorCodec.encode(link.toLocator()))`; call `onSectionsKnown(...)`.
- [ ] **Step 2:** Section jump → decode the section's `location` to a Locator and `navigator.go(locator)`. (If the VM's section-jump path is page-index based, add an EPUB-specific jump callback that takes the locator string.)
- [ ] **Step 3: Build + commit:** `feat(reader): EPUB table of contents from Readium`

---

### Task 10 — Full verification + remove the old renderer

**Files:** delete `androidApp/.../ui/screens/reader/EpubReader.kt` (and its `EpubBook` helper **iff** no other format uses it — grep first).

- [ ] **Step 1: Whitespace + gate:**
  `git diff --check && ./gradlew :androidApp:testDebugUnitTest :androidApp:compileDebugKotlinAndroid`
- [ ] **Step 2: adb device pass (Pixel):** open an EPUB and verify, with screenshots:
  - Real **page turns** (tap/swipe advances one page, not a whole chapter); page does not vertically scroll.
  - **Pinch out** enlarges the font and the book **re-paginates** (page count changes, current position preserved within tolerance).
  - **Theme** follows the app (dark by default; switching to Sepia/Light in the settings sheet applies live).
  - **Resume**: back out mid-book, reopen → lands on the same page (locator restored). Kill + relaunch offline → still resumes (local snapshot). Back online → `EbookProgressSyncer` pushes the locator (verify the detail's reading progress reflects it).
  - **TOC** lists chapters and jumps correctly.
- [ ] **Step 3:** grep for `EpubReader(` / `EpubBook` usages; if the EPUB path is the only consumer, delete `EpubReader.kt`. If `EpubBook` is shared with FB2/TXT, leave it.
- [ ] **Step 4: Commit:** `chore(reader): remove bones-level WebView EPUB renderer`
- [ ] **Step 5:** Use superpowers:finishing-a-development-branch to decide merge / PR.

---

## Risks & sequencing notes

- **FragmentActivity (Task 2) is the blast-radius change.** Everything else is additive and reader-scoped. Verify the whole app after Task 2 before building on it.
- **Readium API drift.** The toolkit's `AssetRetriever` / `PublicationOpener` / `EpubNavigatorFactory` / `EpubPreferences` signatures differ across 2.x and 3.x. Pin one version (Task 1) and reconcile the code shapes in Tasks 4–6 to *that* version — the plan specifies the **contract** (open→Publication, settings→preferences, locator round-trip), not version-exact call signatures.
- **Locator back-compat.** Old EPUB rows store `"page:N"`; `ReadiumLocatorCodec.decode` returns `null` for those so the reader opens at the start — acceptable one-time reset. New rows store Locator JSON; the already-shipped `EbookProgressSyncer` transports it unchanged.
- **No server change.** `location` stays an opaque string end to end (client save/load, local store, sync-back). Nothing on `silo-server` is required.

## Out of scope (later phases)

- In-book **search** and **highlights/notes** rendering (their own phases) — Readium supports both, but this plan only establishes the navigator + locators they will build on.
- **Font-family** selection mapping (deferred to the fonts/brightness phase; preferences default for now).
- Moving **PDF / CBZ / TXT / FB2** to Readium — they keep their current renderers.

## Self-review vs. goal

- **True paginated pages + reflow** → `EpubNavigatorFragment` owns it (Tasks 6, 8). ✔
- **Pinch-to-zoom font scaling that redoes pages** → Task 7 nudges `textScale` → `EpubPreferences.fontSize` → navigator reflow. ✔
- **Resume exactly (incl. offline + cross-device)** → Locator JSON in the existing `location` string + the shipped local/server reconciliation + `EbookProgressSyncer`. ✔ (Tasks 3, 8)
- **Don't break the rest of the app / other formats** → only the EPUB branch changes; FragmentActivity verified app-wide (Task 2); other renderers untouched. ✔
- **No new server contract** → `location` stays opaque. ✔
