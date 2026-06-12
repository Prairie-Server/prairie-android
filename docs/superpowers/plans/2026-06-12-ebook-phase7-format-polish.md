# Ebook Reader — Phase 7: Per-Format Polish (PDF/Comic/FB2) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Bring the three fixed/legacy-scroll readers up to the reading-quality bar set by the paginated EPUB harness. PDF gains pinch-zoom + pan and a highlight overlay anchored by `page + normalized rect` (consuming the Phase 4 highlight model), with progress flowing through the `ReaderLocator` page/page-rect types. Comic/CBZ gains pinch-zoom + pan and an optional landscape double-page spread toggle. FB2/FBZ stops being a single vertical scroll and routes through the shared Phase 2 paginated HTML harness so it gets EPUB-parity page turns, page counts, and CFI locators.

**Architecture:** Each format reader stays a self-contained `@Composable` dispatched from `ReaderScreen`. Phase 7 adds:
1. A reusable `ZoomPanState` + `Modifier.zoomable(...)` gesture layer (pure clamp/bounds math, unit-tested) shared by `PdfReader` and `ComicReader`.
2. A PDF highlight overlay that maps a normalized rect (`0f..1f` in page space) to on-screen pixels under the current zoom/pan transform, drawing Phase 4 highlights as `Canvas` rects over the rendered page bitmap.
3. Double-page pairing logic (pure index math, unit-tested) feeding the comic pager when the spread toggle is on in landscape.
4. An FB2→HTML conversion step that emits the same HTML contract the Phase 2 `PaginatedWebReader` consumes, replacing the single-scroll `TextDocumentContent` path for FB2/FBZ.

All position/locator types live in the `shared` KMP module (`ReaderLocator` and friends from Phase 1); all UI lives in `androidApp` (`com.continuum.app.android.ui.screens.reader`); reusable Compose gesture/HTML helpers live in `android-shared` (`com.continuum.app.common.ebook`) so `androidTvApp` can adopt later. Pure logic (zoom clamp, pan bounds, normalized-rect↔pixel mapping, double-page pairing, FB2→HTML) is TDD'd in `androidApp`'s `androidUnitTest` source set; zoom/render/overlay UI that needs a real device is verified via `adb` manual steps.

**Tech Stack:** Kotlin, Jetpack Compose (`Modifier.pointerInput` + `detectTransformGestures`, `graphicsLayer`, `Canvas`/`drawRect`), `android.graphics.pdf.PdfRenderer`, `androidx.compose.foundation.pager.HorizontalPager`, WebView-hosted paginated HTML harness (Phase 2), kotlinx.serialization for `ReaderLocator`. Tests: `kotlin("test")` + `kotlin("test-junit")`, Robolectric where an Android API is touched.

---

## Assumed prerequisites (shipped by Phases 1, 2, 4)

This plan does NOT build these; it consumes them. If a symbol below is missing when implementation starts, stop and confirm the prior phase landed — do not re-implement it here.

- **Phase 1 — `ReaderLocator`** in `shared/src/commonMain/kotlin/com/continuum/app/model/reader/`:
  - Sealed `ReaderLocator` with at least: `data class Page(val pageIndex: Int)`, `data class PageRect(val pageIndex: Int, val left: Float, val top: Float, val right: Float, val bottom: Float)` (rect components normalized `0f..1f` in page space), and a CFI variant `data class Cfi(val cfi: String)`.
  - JSON (de)serialization via kotlinx.serialization, plus a backward-compatible reader that parses the legacy `"page:N"` progress string into `ReaderLocator.Page(N)`.
  - A helper to format a `ReaderLocator` back to the progress `location` string stored by `ReaderViewModel`.
- **Phase 2 — `PaginatedWebReader`** in `androidApp/.../ui/screens/reader/`:
  - `@Composable fun PaginatedWebReader(html: String, baseUrl: String?, settings: ReaderDisplaySettings, initialLocator: ReaderLocator?, onLocatorChanged: (ReaderLocator) -> Unit, onPageChanged: (Int) -> Unit, onPageCountKnown: (Int) -> Unit, onSectionsKnown: (List<ReaderSection>) -> Unit)` — a WebView host running the CSS-multicolumn HTML harness (the text/FB2 path described in spec §4) with real page turns and CFI locators. EPUB uses the epub.js path of the same host; FB2/text use the HTML-string path consumed via `html` + `baseUrl`.
- **Phase 4 — highlight model**: `EbookAnnotation` (already in `shared/.../model/ebook/EbookReaderModels.kt`) carrying a typed `ReaderLocator` (range form) plus `color`; an `AnnotationController`/highlight list exposed on `ReaderViewModel` as `state.highlights: List<EbookAnnotation>`. Phase 7 only reads `state.highlights`, filters to the current page, and renders overlays for PDF; it does not add the selection UI.

Where this plan needs a symbol from a prerequisite that is genuinely ambiguous, the corresponding task defines the exact expected signature inline and the implementer wires to the real one.

---

## File Structure

Real paths and responsibilities.

- `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ZoomPanState.kt` — **NEW.** Pure-Kotlin zoom/pan state holder + clamp/bounds math + a `Modifier.zoomable()` extension built on `detectTransformGestures`. Shared by PDF and comic.
- `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/ZoomPanStateTest.kt` — **NEW.** TDD for zoom clamp and pan-bounds math.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/FictionBookHtml.kt` — **NEW.** Converts parsed FB2 structure to the HTML string the Phase 2 harness consumes. Pure string logic.
- `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/FictionBookHtmlTest.kt` — **NEW.** TDD for FB2→HTML conversion.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PdfReader.kt` — **EDIT.** Add zoom/pan to the rendered page, a highlight overlay, and route page changes through `ReaderLocator.Page`/`PageRect`.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PdfHighlightOverlay.kt` — **NEW.** Normalized-rect↔pixel mapping + `Canvas` overlay composable for PDF highlights.
- `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/PdfHighlightMappingTest.kt` — **NEW.** TDD for normalized-rect→pixel mapping under zoom/pan and `ContentScale.Fit` letterboxing.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicReader.kt` — **EDIT.** Add zoom/pan per page and a double-page spread mode (landscape).
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicSpread.kt` — **NEW.** Pure double-page index-pairing logic.
- `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ComicSpreadTest.kt` — **NEW.** TDD for spread pairing.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/FictionBookReader.kt` — **EDIT.** Replace the single-scroll `TextDocumentContent` render with the Phase 2 `PaginatedWebReader`, feeding FB2→HTML.
- `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/FictionBookReaderTest.kt` — **EDIT.** Extend existing parse tests to cover the new HTML routing entry point.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt` — **EDIT.** Add a comic double-page toggle to the FB2 dispatch nothing changes; wire the spread toggle for `BookFormat.Cbz`.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ReaderControls.kt` — **EDIT.** Extend `ReaderCapabilities` with `supportsZoom` and `supportsDoublePage`; FB2/FBZ capabilities flip to the reflowable (theme/size/margins/sections) profile now that they route through the harness.

**Test task command (verified against `androidApp/build.gradle.kts` KMP `androidUnitTest` source set):**

```
./gradlew :androidApp:testDebugUnitTest
./gradlew :android-shared:testDebugUnitTest
```

Lint/format gate before MR (from repo root):

```
./gradlew :androidApp:lintDebug :android-shared:lintDebug
```

---

### Task 1 — `ZoomPanState`: pure zoom-clamp + pan-bounds math (TDD)

**Files:**
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ZoomPanState.kt` (new)
- `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/ZoomPanStateTest.kt` (new)

Pull the math out of the gesture handler so it is testable without a device. The state holds `scale` and `offset` (pixels); gesture deltas feed pure functions that clamp scale to `[1f, maxScale]` and clamp pan so the scaled content cannot be dragged past its own edges (no empty gutters when zoomed; locked to center at `scale == 1f`).

- [ ] Write `ZoomPanStateTest` first. Cases:
  - `clampScale` returns `1f` for input below min, `maxScale` for input above max, identity in range.
  - `maxPanOffset(scale, viewportSize)` returns `Offset.Zero` at `scale == 1f`.
  - `maxPanOffset` at `scale == 2f`, viewport `1000x2000` → `Offset(500f, 1000f)` (half the overflow per axis).
  - `clampOffset(requested, scale, viewportSize)` clamps each axis into `[-max, +max]`.
  - Pinch around a focal point: `applyZoom(current, zoomFactor, focal, viewportSize)` keeps `focal` visually anchored (the content point under the focal stays under it) and re-clamps offset.
- [ ] Implement in `ZoomPanState.kt`:
  - `class ZoomPanState(val maxScale: Float = 4f)` with `var scale by mutableFloatStateOf(1f)` and `var offset by mutableStateOf(Offset.Zero)`, plus `var viewportSize by mutableStateOf(IntSize.Zero)`.
  - Pure top-level functions `internal fun clampScale(scale: Float, min: Float, max: Float): Float`, `internal fun maxPanOffset(scale: Float, viewport: IntSize): Offset`, `internal fun clampOffset(offset: Offset, scale: Float, viewport: IntSize): Offset`, `internal fun applyZoom(state: ZoomState, zoom: Float, focal: Offset, viewport: IntSize): ZoomState` where `ZoomState` is a small `data class ZoomState(val scale: Float, val offset: Offset)` so the math is testable without Compose state.
  - `fun ZoomPanState.onGesture(centroid: Offset, pan: Offset, zoom: Float)` applies `applyZoom` then `clampOffset` and writes back to the observable fields.
  - `fun ZoomPanState.reset()` sets `scale = 1f`, `offset = Offset.Zero`.
- [ ] Add the gesture modifier: `fun Modifier.zoomable(state: ZoomPanState): Modifier` using `pointerInput(state) { detectTransformGestures { centroid, pan, zoom, _ -> state.onGesture(centroid, pan, zoom) } }` and a `graphicsLayer { scaleX = state.scale; scaleY = state.scale; translationX = state.offset.x; translationY = state.offset.y }`, with an `onSizeChanged { state.viewportSize = it }`. Add a double-tap-to-reset via a second `pointerInput(state) { detectTapGestures(onDoubleTap = { state.reset() }) }`.
- [ ] Run `./gradlew :android-shared:testDebugUnitTest`; confirm green.

---

### Task 2 — Comic double-page pairing logic (TDD)

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicSpread.kt` (new)
- `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ComicSpreadTest.kt` (new)

Pure index math: given an ordered page count and whether the first page is a standalone cover, produce the list of "spreads" (each spread = 1 or 2 page indices) and map between a flat page index and its spread index in both directions, so resume/progress stays correct when toggling the mode.

- [ ] Write `ComicSpreadTest` first. Cases:
  - `pairPages(pageCount = 0, coverAlone = true)` → `emptyList()`.
  - `pairPages(5, coverAlone = true)` → `[[0],[1,2],[3,4]]`.
  - `pairPages(5, coverAlone = false)` → `[[0,1],[2,3],[4]]`.
  - `pairPages(4, coverAlone = true)` → `[[0],[1,2],[3]]`.
  - `spreadIndexForPage(page = 3, spreads)` returns the index of the spread containing page 3.
  - `firstPageOfSpread(spreadIndex, spreads)` returns the leading page index (used to report `onPageChanged` consistently with single-page mode).
- [ ] Implement in `ComicSpread.kt`:
  - `internal data class ComicSpread(val pages: List<Int>)`.
  - `internal fun pairPages(pageCount: Int, coverAlone: Boolean): List<ComicSpread>` — when `coverAlone` and `pageCount > 0`, emit `[0]` then pair `1,2 / 3,4 / ...`; otherwise pair from `0`; trailing odd page becomes a single-page spread.
  - `internal fun spreadIndexForPage(page: Int, spreads: List<ComicSpread>): Int` — `indexOfFirst { page in it.pages }.coerceAtLeast(0)`.
  - `internal fun firstPageOfSpread(spreadIndex: Int, spreads: List<ComicSpread>): Int` — `spreads.getOrNull(spreadIndex)?.pages?.firstOrNull() ?: 0`.
- [ ] Run `./gradlew :androidApp:testDebugUnitTest`; confirm green.

---

### Task 3 — PDF normalized-rect ↔ pixel mapping for the highlight overlay (TDD)

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PdfHighlightOverlay.kt` (new — mapping fns only in this task; the Composable lands in Task 5)
- `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/PdfHighlightMappingTest.kt` (new)

The page bitmap is drawn with `ContentScale.Fit` (letterboxed) inside the viewport, then transformed by the `ZoomPanState` `graphicsLayer`. A highlight rect is normalized to page space (`0f..1f`). Mapping must first place the rect within the letterboxed page area, then apply scale+offset, so the overlay lands exactly on the highlighted text under any zoom/pan.

- [ ] Write `PdfHighlightMappingTest` first. Use plain data types (`androidx.compose.ui.geometry.Rect`, `Size`, `Offset`) — these are JVM-safe, no Robolectric needed. Cases:
  - `fittedPageRect(pageAspect = 1f, viewport = Size(1000f, 2000f))` (square page in a tall viewport) → `Rect(0f, 500f, 1000f, 1500f)` (letterboxed top/bottom).
  - `fittedPageRect(pageAspect = 2f, viewport = Size(1000f, 2000f))` (wide page) → `Rect(0f, 750f, 1000f, 1250f)`.
  - `mapNormalizedRect(norm = Rect(0f,0f,1f,1f), fitted = Rect(0f,500f,1000f,1500f), scale = 1f, offset = Offset.Zero)` → `Rect(0f,500f,1000f,1500f)` (full page).
  - `mapNormalizedRect(norm = Rect(0.25f,0.25f,0.75f,0.75f), fitted = Rect(0f,0f,1000f,1000f), scale = 1f, offset = Offset.Zero)` → `Rect(250f,250f,750f,750f)`.
  - Same norm with `scale = 2f, offset = Offset(-500f, -500f)` → each fitted coord scaled by 2 then translated by offset (assert exact pixel rect).
- [ ] Implement mapping fns in `PdfHighlightOverlay.kt`:
  - `internal fun fittedPageRect(pageAspect: Float, viewport: Size): Rect` — letterbox: if `viewport.width / viewport.height > pageAspect`, height-bound; else width-bound; center the page.
  - `internal fun mapNormalizedRect(norm: Rect, fitted: Rect, scale: Float, offset: Offset): Rect` — lerp `norm` into `fitted`, then apply `x' = x * scale + offset.x` (and y), returning the transformed `Rect`.
- [ ] Run `./gradlew :androidApp:testDebugUnitTest`; confirm green.

---

### Task 4 — PDF reader: zoom + pan + progress via `ReaderLocator` (device-verified UI)

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PdfReader.kt` (edit)

Wire the Task 1 `ZoomPanState` into `PdfPage`, and switch the page-change callback to carry a `ReaderLocator.Page`. (The overlay is Task 5.) Each pager page owns its own `ZoomPanState`, reset when the page changes so swiping always starts un-zoomed.

- [ ] In `PdfReader`, keep `HorizontalPager` for page turns. When zoomed (`state.scale > 1f`) the `graphicsLayer` consumes drag, so pan does not fight the pager; verify on device (Task 7) that horizontal swipe still turns pages at `scale == 1f`.
- [ ] In `PdfPage(handle, pageIndex)`, add `val zoom = remember(pageIndex) { ZoomPanState() }` (import from `com.continuum.app.common.ebook`). Apply `Modifier.zoomable(zoom)` to the page `Box` that wraps the `Image`. Keep `ContentScale.Fit` and the existing `aspectRatio` so `fittedPageRect` math (Task 3) stays valid.
- [ ] Change the `PdfReader` signature from `onPageChanged: (Int) -> Unit` to `onLocatorChanged: (ReaderLocator) -> Unit` (plus keep `onPageCountKnown`). In the `snapshotFlow { pagerState.currentPage }` collector emit `onLocatorChanged(ReaderLocator.Page(it))`. Update the call site in `ReaderScreen` (Task 8).
- [ ] Update the KDoc header block: remove the "no zoom or rotation in the bones pass" sentence; document that zoom/pan is per-page and resets on page change.
- [ ] Compile check: `./gradlew :androidApp:compileDebugKotlin`.

---

### Task 5 — PDF highlight overlay Composable (device-verified UI)

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PdfHighlightOverlay.kt` (edit — add the Composable)
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PdfReader.kt` (edit)

Draw Phase 4 highlights for the current page as translucent rects over the rendered bitmap, transformed by the same zoom/pan so they track the text.

- [ ] Add `@Composable internal fun PdfHighlightOverlay(highlights: List<EbookAnnotation>, pageIndex: Int, pageAspect: Float, zoom: ZoomPanState, modifier: Modifier)`:
  - Filter `highlights` to those whose `ReaderLocator` is a `PageRect` (or a range whose start is a `PageRect`) on `pageIndex`. Provide a small private helper `pageRectFor(annotation): ReaderLocator.PageRect?` that reads the typed locator from `EbookAnnotation` (Phase 4 field).
  - In a `Canvas(modifier.fillMaxSize().onSizeChanged { ... })`, compute `fitted = fittedPageRect(pageAspect, size)` then for each highlight `mapNormalizedRect(norm, fitted, zoom.scale, zoom.offset)` and `drawRect(color = Color(annotation.color).copy(alpha = 0.3f), topLeft = rect.topLeft, size = rect.size)`. Parse `annotation.color` (hex string) defensively, default to a theme highlight color.
- [ ] In `PdfReader.PdfPage`, accept `highlights: List<EbookAnnotation>` and the page aspect (compute from the decoded bitmap `bmp.width / bmp.height`), and overlay `PdfHighlightOverlay(...)` on top of the `Image` inside the same zoomable `Box` (so the overlay shares the bitmap's transform). Thread `highlights` down from `PdfReader` (new param `highlights: List<EbookAnnotation>`), sourced from `state.highlights` at the `ReaderScreen` call site.
- [ ] Compile check: `./gradlew :androidApp:compileDebugKotlin`.

---

### Task 6 — Comic reader: zoom/pan + double-page spread mode (device-verified UI)

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicReader.kt` (edit)

Add per-page zoom/pan (same `ZoomPanState`), and an optional landscape double-page spread driven by Task 2's pairing. The pager pages over spreads when the toggle is on; otherwise over single pages as today. Progress reported via `firstPageOfSpread`.

- [ ] Add a `doublePage: Boolean` param to `ComicReader` (default `false`) and read landscape via `LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE`. Effective spread mode = `doublePage && isLandscape`.
- [ ] When spread mode is on, compute `val spreads = remember(pages, coverAlone) { pairPages(pages.size, coverAlone = true) }` and drive the pager `pageCount = { spreads.size }`; render each spread as a `Row` of 1–2 `ComicPage`s sharing the page width. When off, keep the existing single-page pager unchanged.
- [ ] Resume/report mapping: convert `initialPage` (a flat page index) to its spread index via `spreadIndexForPage` for `initialPage`; in the page-change collector emit the leading flat page via `firstPageOfSpread(currentSpread, spreads)` so progress stays comparable across mode toggles. (Keep the existing `(Int) -> Unit` page callback for comic — comic has no `PageRect`/CFI; a `ReaderLocator.Page` wrapping is applied at the `ReaderScreen` call site in Task 8.)
- [ ] Add `Modifier.zoomable(remember(pageIndex){ ZoomPanState() })` to each `ComicPage` `Box` (resets on page/spread change). Keep `ContentScale.Fit`/`aspectRatio`.
- [ ] Compile check: `./gradlew :androidApp:compileDebugKotlin`.

---

### Task 7 — Device manual-verify: PDF zoom/overlay + comic zoom/spread (adb)

No code; real-device verification of the UI from Tasks 4–6. Requires a connected device/emulator (`adb devices` shows one) and a debug build installed.

- [ ] Build + install: `./gradlew :androidApp:installDebug`.
- [ ] Launch the app and capture a screenshot baseline:
  - `adb shell am start -n com.continuum.app.android/com.continuum.app.android.MainActivity` (confirm the launcher activity name first with `adb shell cmd package resolve-activity --brief com.continuum.app.android`).
- [ ] PDF: open a PDF book. Verify (a) pinch zooms in, two-finger drag pans, content cannot be dragged past its edges; (b) double-tap resets to fit; (c) horizontal swipe at fit turns pages; (d) a Phase 4 highlight renders as a translucent rect anchored to the text and tracks the page while zooming/panning. Capture: `adb exec-out screencap -p > /tmp/pdf_zoom_highlight.png` and visually confirm the overlay aligns.
- [ ] Comic: open a CBZ. Verify pinch-zoom/pan/double-tap-reset per page; rotate to landscape, enable the double-page toggle, confirm two pages render side-by-side with a standalone cover, page turns advance by a spread, and progress resumes to the same place when toggling the mode off. Capture: `adb exec-out screencap -p > /tmp/comic_spread.png`.
- [ ] Record results (pass/fail + screenshot paths) in the MR description. Any failure → fix in the owning task and re-verify; do not mark Phase 7 complete on unverified UI.

---

### Task 8 — `ReaderScreen` + capabilities wiring for zoom/spread + PDF highlights

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt` (edit)
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ReaderControls.kt` (edit)
- `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/ReaderControlsTest.kt` (edit)

Surface the comic double-page toggle, pass PDF highlights, and adapt the locator callbacks. PDF/comic gain `supportsZoom`; comic gains `supportsDoublePage`.

- [ ] In `ReaderControls.kt` add `val supportsZoom: Boolean = false` and `val supportsDoublePage: Boolean = false` to `ReaderCapabilities`. In `forFormat`: `Pdf` → `supportsZoom = true`; `Cbz` → `supportsZoom = true, supportsDoublePage = true`. Leave reflowable formats' zoom false (they reflow instead).
- [ ] Extend `ReaderControlsTest` with cases asserting `forFormat(BookFormat.Pdf).supportsZoom`, `forFormat(BookFormat.Cbz).supportsDoublePage`, and that `forFormat(BookFormat.Epub).supportsZoom` is false. Run `./gradlew :android-shared:testDebugUnitTest`.
- [ ] In `ReaderScreen`, for the `BookFormat.Pdf` branch: pass `highlights = state.highlights` and change `onPageChanged = viewModel::onPageChanged` to `onLocatorChanged = viewModel::onLocatorChanged` (the Phase 1 VM method that accepts `ReaderLocator`; if Phase 1 only exposes `onPageChanged`, adapt with `onLocatorChanged = { loc -> (loc as? ReaderLocator.Page)?.let { viewModel.onPageChanged(it.pageIndex) } }` and note the follow-up).
- [ ] For the `BookFormat.Cbz` branch: add a `doublePage` reader-local toggle (`var doublePage by rememberSaveable { mutableStateOf(false) }`) shown as an `IconButton` (use `Icons.AutoMirrored.Filled.MenuBook` or a spread-style icon) in the top `Row`, enabled when `state.capabilities.supportsDoublePage`; pass `doublePage = doublePage` to `ComicReader`.
- [ ] Compile check: `./gradlew :androidApp:compileDebugKotlin`.

---

### Task 9 — FB2→HTML conversion (TDD)

**Files:**
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/FictionBookHtml.kt` (new)
- `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/FictionBookHtmlTest.kt` (new)

Produce the HTML string the Phase 2 `PaginatedWebReader` consumes from FB2 structure, so FB2/FBZ reflows and paginates like EPUB. Reuse the existing FB2 parsing in `FictionBookReader.kt` (`parseFictionBookText`/`loadFictionBookText`) but emit semantic HTML (`<h1>`, `<p>`, `<br>`) instead of a flattened text blob, since the harness needs block structure for accurate page breaks and CFI anchoring.

- [ ] Write `FictionBookHtmlTest` first (pure JVM, no Robolectric — `parseFictionBook*` use `javax.xml`, available on the JVM). Cases:
  - Title/subtitle/`<p>` map to `<h1>`/`<h2>`/`<p>`; `<empty-line>` → `<br>`; verse `<v>` lines → `<p class="verse">` or `<br>`-joined.
  - Output is a complete HTML document with a `<head>` the harness can inject reader CSS into (so `withReaderCss`-style insertion works) and a `<body>` wrapping the content.
  - HTML-escaping: `&`, `<`, `>`, `"` in text content are escaped.
  - An empty/no-body FB2 yields a minimal valid `<body>` (harness shows an empty page, not a crash).
- [ ] Implement `fun fictionBookToHtml(parsed: FictionBookDocument): String`. Introduce `FictionBookDocument` as a structured parse result (title, author, list of blocks) — refactor the FB2 parser to also expose this structure. Keep `parseFictionBookText` working (existing tests) by deriving the flat text from the structured form, or keep both and share the DOM walk. Escape via a small `htmlEscape(String)` helper.
- [ ] Run `./gradlew :android-shared:testDebugUnitTest`; confirm green.

*Note:* the FB2 parser currently lives in `androidApp/.../FictionBookReader.kt`. Moving the structured parse into `android-shared` keeps the HTML builder and its tests in the same module as the harness helpers. If moving the parser is too invasive, define `FictionBookDocument` + `fictionBookToHtml` in `FictionBookHtml.kt` and have `FictionBookReader.kt` build the document from its existing DOM walk; either way the conversion is unit-tested in `android-shared`.

---

### Task 10 — FB2 reader routes through the paginated HTML harness

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/FictionBookReader.kt` (edit)
- `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/FictionBookReaderTest.kt` (edit)
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ReaderControls.kt` (edit)

Replace the single-scroll `TextDocumentContent` render with the Phase 2 `PaginatedWebReader`, fed FB2→HTML. FB2/FBZ now reports real page counts, page turns, and CFI locators like EPUB.

- [ ] In `FictionBookReader`, change the loaded branch: instead of `TextDocumentContent(text = result.text, settings = settings)`, build `html = fictionBookToHtml(document)` (off-main, in the existing `produceState`/`withContext(Dispatchers.IO)`) and render `PaginatedWebReader(html = html, baseUrl = null, settings = settings, initialLocator = initialLocator, onLocatorChanged = onLocatorChanged, onPageChanged = onPageChanged, onPageCountKnown = onPageCountKnown, onSectionsKnown = onSectionsKnown)`.
- [ ] Update the `FictionBookReader` signature to match the harness contract: add `initialPage: Int = 0` (or `initialLocator: ReaderLocator?` if Phase 1 threads locators), `onLocatorChanged`, and `onSectionsKnown`. Remove the placeholder `onPageCountKnown(1)`/`onPageChanged(0)` `LaunchedEffect` — the harness now drives those.
- [ ] In `ReaderControls.kt` `forFormat`, FB2/FBZ already report theme/size/margins; add `supportsSections = true` for FB2/FBZ now that the harness produces sections. (Verify against the harness's actual `onSectionsKnown` output; if FB2 has no chapter structure, leave sections false and note it.)
- [ ] Update `FictionBookReaderTest`: keep existing `parseFictionBookText` assertions; add a case asserting `fictionBookToHtml` is reachable from the FB2 load path (e.g. `loadFictionBook` produces a document the HTML builder accepts). Do not assert on WebView rendering in unit tests.
- [ ] Update `ReaderScreen`'s `BookFormat.Fb2, BookFormat.Fbz` branch to pass the new params (`initialPage = state.currentPage`, `onLocatorChanged = viewModel::onLocatorChanged`, `onSectionsKnown = viewModel::setSections`), mirroring the EPUB branch.
- [ ] Run `./gradlew :androidApp:testDebugUnitTest`; confirm green.

---

### Task 11 — Full verification + self-review against spec §4/§8

**Files:** none (verification only).

- [ ] `./gradlew :android-shared:testDebugUnitTest :androidApp:testDebugUnitTest` — all green.
- [ ] `./gradlew :android-shared:lintDebug :androidApp:lintDebug` — no new warnings/errors.
- [ ] Re-run Task 7 device checks if any of Tasks 4–6/8/10 changed after first verification.
- [ ] Self-review checklist vs spec §4 + §8:
  - PDF has pinch-zoom + pan (Task 4), a highlight overlay anchored by page + normalized rect consuming Phase 4 (Tasks 3, 5), and progress via `ReaderLocator.Page` (Task 4/8). ✓
  - Comic has pinch-zoom + pan and an optional landscape double-page toggle (Tasks 2, 6, 8). ✓
  - FB2/FBZ routes through the Phase 2 paginated HTML harness for page-turn parity with EPUB (Tasks 9, 10). ✓
  - No reflow controls leak to PDF/comic (spec §9: fixed-layout formats keep size/margins hidden) — confirm `ReaderCapabilities.forFormat` still reports `supportsTextSize/Margins = false` for Pdf/Cbz. ✓
- [ ] Confirm no plan placeholders remain and every referenced prerequisite symbol resolved to a real Phase 1/2/4 type; file any unresolved item as MR follow-up.

---

## Self-review (vs spec, fixed inline)

- **Spec §4 "PDF → keep `PdfRenderer`; add zoom and a highlight overlay (page + rect)"** — covered by Tasks 1, 3, 4, 5. Zoom shared with comic via `ZoomPanState` (DRY per CLAUDE.md maintainability rule), not duplicated.
- **Spec §4 "CBZ → add zoom and optional double-page spread"** — Tasks 2, 6, 8. Spread is landscape-gated and toggle-controlled per spec.
- **Spec §4 "FB2 → shared `PaginatedWebReader`"** — Tasks 9, 10. FB2 emits semantic HTML (not the flattened text blob the current scroll path uses) so the harness can paginate and anchor CFIs.
- **Spec §8 phase 7** — all three deliverables present; no scope creep into search (PDF search is deferred per §9) or selection UI (Phase 4 owns it; Phase 7 only renders existing highlights).
- **Spec §9 "comic/PDF do not get reflow"** — Task 8/11 keep `supportsTextSize/Margins = false` for Pdf/Cbz; only `supportsZoom`/`supportsDoublePage` are added.
- **CLAUDE.md "models live in shared so Apple/TV can adopt"** — `ZoomPanState`/`FictionBookHtml` live in `android-shared` (Android-Compose-specific, so not `shared` commonMain, but the module `androidTvApp` can depend on); `ReaderLocator` stays in `shared` (Phase 1). This is the correct boundary: zoom gestures are Compose-Android, not KMP-common.
- **Deferred (out of Phase 7 scope, noted for follow-up):** PDF text-layer + PDF in-text search (§9 defer); comic RTL reading direction; per-format adaptive max-zoom tuning; persisting the comic double-page toggle to reader config (currently reader-local `rememberSaveable`).
