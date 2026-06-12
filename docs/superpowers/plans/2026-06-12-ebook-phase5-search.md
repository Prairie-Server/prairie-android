# Ebook Reader — Phase 5: In-Text Search — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Add in-text search to the ebook reader for reflowable formats. Users open a search sheet, type a query, see a results list (snippet + position), and tap a result to jump to that location with the match highlighted transiently. EPUB search runs through the epub.js JS bridge shipped in Phase 2; TXT/Markdown and FB2/FBZ search runs over the already-loaded text in Kotlin, producing `ReaderLocator.CharOffset` results. PDF search is **DEFERRED** (no text layer in `PdfRenderer`) and shows an explanatory note; comic (CBZ) search is **N/A** (no text).

**Architecture:** A pure, testable Kotlin search core (`TextSearchEngine`) handles string formats: case-insensitive matching, snippet windowing, and char-offset → `ReaderLocator.CharOffset` mapping. A `ReaderSearchController` interface abstracts per-format search; concrete implementations are `TextSearchController` (wraps `TextSearchEngine` over the in-memory document string) and `EpubSearchController` (calls epub.js `book.search()` via the existing WebView JS bridge, mapping CFI hits to `ReaderLocator.Cfi`). `ReaderViewModel` owns search state (query, results, active controller, transient highlight target) and exposes it to a new `ReaderSearchSheet` Compose UI following the existing `ModalBottomSheet` sheet pattern in `ReaderScreen.kt`. Tapping a result calls back into the format reader to navigate + flash-highlight the match.

**Tech Stack:** Kotlin, Jetpack Compose (Material3 `ModalBottomSheet`), Android WebView JS bridge (`@JavascriptInterface`) driving epub.js `EpubCFI` + `Book.search`. Unit tests via `kotlin-test` + JUnit on the `androidUnitTest` source set (`:androidApp:testDebugUnitTest`). Shared models in the `:shared` KMP module under `com.continuum.app.model.reader`.

**Assumptions (shipped in earlier phases):**
- Phase 1 `ReaderLocator` exists in `shared/src/commonMain/kotlin/com/continuum/app/model/reader/` as a `@Serializable` sealed type with at least `ReaderLocator.Cfi(cfi: String)` and `ReaderLocator.CharOffset(start: Int, end: Int)` variants, plus typed-JSON round-trip serialization. This plan **uses** that type; it does not define it. If a variant is missing, add it as part of Task 1 and note the deviation, but do not redesign the Phase 1 model.
- Phase 2 `PaginatedWebReader` (epub.js host) exists with a Kotlin↔JS bridge. This plan extends that bridge with a `search(query)` round-trip and a `highlightCfi`/`clearHighlight` call. If Phase 2 shipped the per-chapter `EpubReader.kt` instead of a paginated epub.js host, implement the EPUB controller against whichever epub.js host exists and note the deviation; the text-format work below is independent of that choice.

---

## File Structure

Real repository paths (repository root = `silo-android`, the cwd):

- `shared/src/commonMain/kotlin/com/continuum/app/model/reader/ReaderLocator.kt` — **(exists, Phase 1)** locator sealed type. Read-only reference here; only touched if a needed variant is missing.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/search/TextSearchEngine.kt` — **(new)** pure Kotlin search core for string documents: case-insensitive scan, snippet windowing, `SearchMatch` → `ReaderLocator.CharOffset`. No Android/Compose deps; fully unit-tested.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/search/ReaderSearchController.kt` — **(new)** `ReaderSearchController` interface + `ReaderSearchResult` data class (snippet, locator, match metadata) + `TextSearchController` implementation wrapping `TextSearchEngine`.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/search/EpubSearchController.kt` — **(new)** `ReaderSearchController` backed by the epub.js JS bridge; maps epub.js search hits (CFI + excerpt) to `ReaderSearchResult` with `ReaderLocator.Cfi`.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/EpubReader.kt` — **(edit)** extend the epub.js WebView bridge with `search`, `highlightCfi`, and `clearHighlight`; expose them to the controller. (If the Phase 2 paginated host lives in a different file, edit that file instead and note it.)
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/TextReader.kt` — **(edit)** hold the loaded document string in a hoisted state callback so the ViewModel can build a `TextSearchController`; accept a transient highlight target (char range) and a scroll-to-offset request.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/FictionBookReader.kt` — **(edit)** same hoisting as `TextReader` for the parsed FB2 text (it already renders via shared `TextDocumentContent`).
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModel.kt` — **(edit)** add search state + intents (`openSearch`, `closeSearch`, `runSearch`, `jumpToSearchResult`, `onSearchableTextLoaded`), own the active `ReaderSearchController`, manage transient highlight target.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt` — **(edit)** add a Search toolbar icon (gated on a new `supportsSearch` capability) and render `ReaderSearchSheet`.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/search/ReaderSearchSheet.kt` — **(new)** `ModalBottomSheet` with a query `TextField`, loading/empty/no-results/PDF-deferred states, and a results `LazyColumn`; tap jumps via callback.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ReaderControls.kt` — **(edit)** add `supportsSearch` to `ReaderCapabilities` and set it per format (EPUB/TXT/MD/FB2/FBZ = true; PDF/CBZ/Unknown = false).
- `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/search/TextSearchEngineTest.kt` — **(new)** exhaustive unit tests for the search core.
- `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/search/TextSearchControllerTest.kt` — **(new)** controller-level tests (result mapping, ordering, empty query).
- `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/ReaderControlsTest.kt` — **(edit)** assert `supportsSearch` per format.

**Verification command (run from the repository root, the cwd) for all unit-test tasks:**
```
./gradlew :androidApp:testDebugUnitTest --tests 'com.continuum.app.android.ui.screens.reader.search.*'
```
For the capabilities test:
```
./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.ebook.ReaderControlsTest'
```

---

### Task 1 — Confirm `ReaderLocator` variants and add the `supportsSearch` capability

**Files:**
- `shared/src/commonMain/kotlin/com/continuum/app/model/reader/ReaderLocator.kt` (read; edit only if a variant is missing)
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ReaderControls.kt`
- `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/ReaderControlsTest.kt`

- [ ] Read `ReaderLocator.kt`. Confirm `ReaderLocator.Cfi` and `ReaderLocator.CharOffset(start: Int, end: Int)` exist with JSON round-trip. If `CharOffset` is missing, add it as a `@Serializable` variant matching the existing Phase 1 style (do not redesign existing variants) and note the deviation in your task report.
- [ ] In `ReaderControls.kt`, add `val supportsSearch: Boolean` to `ReaderCapabilities` (place it after `supportsMargins`, before `supportsExternalOnly`, with a default of `false` so no other call sites break).
- [ ] In `ReaderCapabilities.forFormat`, set `supportsSearch = true` for the `BookFormat.Epub` branch and the `BookFormat.Txt, BookFormat.Markdown, BookFormat.Fb2, BookFormat.Fbz` branch. Leave it `false` for the `BookFormat.Pdf, BookFormat.Cbz` branch and the `else` branch.
- [ ] In `ReaderControlsTest.kt`, add assertions:
  - `assertTrue(ReaderCapabilities.forFormat(BookFormat.Epub).supportsSearch)`
  - `assertTrue(ReaderCapabilities.forFormat(BookFormat.Txt).supportsSearch)`
  - `assertTrue(ReaderCapabilities.forFormat(BookFormat.Fb2).supportsSearch)`
  - `assertFalse(ReaderCapabilities.forFormat(BookFormat.Pdf).supportsSearch)`
  - `assertFalse(ReaderCapabilities.forFormat(BookFormat.Cbz).supportsSearch)`
  - `assertFalse(ReaderCapabilities.forFormat(BookFormat.Unknown).supportsSearch)`
- [ ] Run: `./gradlew :android-shared:testDebugUnitTest --tests 'com.continuum.app.common.ebook.ReaderControlsTest'`. Confirm green.

---

### Task 2 — `TextSearchEngine` pure search core (TDD)

Write the test first, watch it fail, then implement. This is the load-bearing, format-agnostic logic — test it heavily.

**Files:**
- `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/search/TextSearchEngineTest.kt`
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/search/TextSearchEngine.kt`

- [ ] Create `TextSearchEngineTest.kt` with this exact test scaffold (uses the public API the implementation must satisfy):

```kotlin
package com.continuum.app.android.ui.screens.reader.search

import com.continuum.app.model.reader.ReaderLocator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextSearchEngineTest {

    private val engine = TextSearchEngine(snippetRadius = 12)

    @Test
    fun `blank query returns no matches`() {
        assertTrue(engine.search("The quick brown fox", "   ").isEmpty())
        assertTrue(engine.search("The quick brown fox", "").isEmpty())
    }

    @Test
    fun `no match returns empty`() {
        assertEquals(emptyList(), engine.search("The quick brown fox", "cat"))
    }

    @Test
    fun `single match maps to char offset locator`() {
        val results = engine.search("The quick brown fox", "brown")
        assertEquals(1, results.size)
        val locator = results[0].locator
        assertTrue(locator is ReaderLocator.CharOffset)
        assertEquals(10, (locator as ReaderLocator.CharOffset).start)
        assertEquals(15, locator.end)
    }

    @Test
    fun `matching is case insensitive but snippet preserves original casing`() {
        val results = engine.search("The Quick BROWN Fox", "brown")
        assertEquals(1, results.size)
        assertTrue(results[0].snippet.contains("BROWN"))
        assertEquals(10, (results[0].locator as ReaderLocator.CharOffset).start)
    }

    @Test
    fun `multiple matches are returned in document order`() {
        val text = "ab AB Ab aB"
        val results = engine.search(text, "ab")
        assertEquals(4, results.size)
        val starts = results.map { (it.locator as ReaderLocator.CharOffset).start }
        assertEquals(listOf(0, 3, 6, 9), starts)
    }

    @Test
    fun `overlapping matches do not double count - advance past full match`() {
        // "aaa" searching "aa" yields one non-overlapping match at 0, next scan starts at 2.
        val results = engine.search("aaa", "aa")
        assertEquals(1, results.size)
        assertEquals(0, (results[0].locator as ReaderLocator.CharOffset).start)
    }

    @Test
    fun `snippet windows around match with radius and adds ellipses when truncated`() {
        val text = "0123456789 brown 9876543210 padding padding"
        val results = engine.search(text, "brown")
        val snippet = results[0].snippet
        assertTrue(snippet.startsWith("…"), "expected leading ellipsis, got: $snippet")
        assertTrue(snippet.contains("brown"))
        assertTrue(snippet.endsWith("…"), "expected trailing ellipsis, got: $snippet")
    }

    @Test
    fun `snippet at document start has no leading ellipsis`() {
        val results = engine.search("brown fox jumps over the lazy dog here", "brown")
        assertTrue(!results[0].snippet.startsWith("…"))
    }

    @Test
    fun `snippet at document end has no trailing ellipsis`() {
        val results = engine.search("padding padding here is brown", "brown")
        assertTrue(!results[0].snippet.endsWith("…"))
    }

    @Test
    fun `match offsets within snippet point at the matched substring`() {
        val text = "alpha beta gamma delta"
        val results = engine.search(text, "gamma")
        val r = results[0]
        val matched = r.snippet.substring(r.snippetMatchStart, r.snippetMatchEnd)
        assertEquals("gamma".length, matched.length)
        assertEquals("gamma", matched.lowercase())
    }

    @Test
    fun `results are capped at maxResults`() {
        val engineCapped = TextSearchEngine(snippetRadius = 4, maxResults = 3)
        val text = "x ".repeat(50)
        assertEquals(3, engineCapped.search(text, "x").size)
    }
}
```

- [ ] Run the test, confirm it **fails to compile** (no implementation yet). This proves the test is wired to the real source set.
- [ ] Create `TextSearchEngine.kt` implementing the contract the tests pin down:
  - `class TextSearchEngine(private val snippetRadius: Int = 40, private val maxResults: Int = 200)`.
  - `data class TextSearchMatch(val locator: ReaderLocator.CharOffset, val snippet: String, val snippetMatchStart: Int, val snippetMatchEnd: Int)` — returned list element type. (The `ReaderSearchResult` UI model in Task 3 wraps this; keep this type snippet-focused and Compose-free.)
  - `fun search(text: String, query: String): List<TextSearchMatch>`:
    - Return `emptyList()` if `query.isBlank()`.
    - Lowercase both `text` and `query` **once** for scanning (`text.lowercase()`), but build snippets from the **original** `text` so casing is preserved. Note: keep matching simple with `String.indexOf(needle, startIndex, ignoreCase = true)` over the original string rather than a pre-lowercased copy, to avoid any length skew from locale-sensitive case folding; this keeps offsets exact against the original `text`. Use the default locale-independent `ignoreCase = true` overload.
    - Loop: `var from = 0; while (true) { val idx = text.indexOf(query, from, ignoreCase = true); if (idx < 0) break; emit match at [idx, idx + query.length); from = idx + query.length; if (results.size >= maxResults) break }`. Advancing `from` by the full match length yields the non-overlapping behavior the test pins.
    - For each match compute the snippet window: `snippetStart = (idx - snippetRadius).coerceAtLeast(0)`, `snippetEnd = (idx + query.length + snippetRadius).coerceAtMost(text.length)`. Slice `text.substring(snippetStart, snippetEnd)`. Prefix `"…"` when `snippetStart > 0`; suffix `"…"` when `snippetEnd < text.length`.
    - Compute `snippetMatchStart` / `snippetMatchEnd` relative to the **rendered** snippet string, accounting for a leading ellipsis: `val leading = if (snippetStart > 0) 1 else 0; snippetMatchStart = leading + (idx - snippetStart); snippetMatchEnd = snippetMatchStart + query.length`.
    - `locator = ReaderLocator.CharOffset(start = idx, end = idx + query.length)`.
- [ ] Run: `./gradlew :androidApp:testDebugUnitTest --tests 'com.continuum.app.android.ui.screens.reader.search.TextSearchEngineTest'`. Confirm **all green**. Fix any off-by-one in the ellipsis offset math against the failing assertion (do not weaken the assertion).

---

### Task 3 — `ReaderSearchController` interface + `TextSearchController` (TDD)

**Files:**
- `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/search/TextSearchControllerTest.kt`
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/search/ReaderSearchController.kt`

- [ ] Create `TextSearchControllerTest.kt`:

```kotlin
package com.continuum.app.android.ui.screens.reader.search

import com.continuum.app.model.reader.ReaderLocator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextSearchControllerTest {

    @Test
    fun `controller searches the loaded document text`() = runTest {
        val controller = TextSearchController("The quick brown fox jumps over the brown log")
        val results = controller.search("brown")
        assertEquals(2, results.size)
        assertTrue(results[0].locator is ReaderLocator.CharOffset)
        assertEquals(10, (results[0].locator as ReaderLocator.CharOffset).start)
        assertEquals(35, (results[1].locator as ReaderLocator.CharOffset).start)
    }

    @Test
    fun `blank query yields empty results`() = runTest {
        val controller = TextSearchController("anything at all")
        assertEquals(emptyList(), controller.search("  "))
    }

    @Test
    fun `result carries a non-empty snippet and ordered index`() = runTest {
        val controller = TextSearchController("alpha beta gamma alpha")
        val results = controller.search("alpha")
        assertEquals(0, results[0].index)
        assertEquals(1, results[1].index)
        assertTrue(results[0].snippet.contains("alpha"))
    }
}
```

- [ ] Run, confirm it fails to compile.
- [ ] Create `ReaderSearchController.kt` containing:
  - `data class ReaderSearchResult(val index: Int, val locator: ReaderLocator, val snippet: String, val snippetMatchStart: Int, val snippetMatchEnd: Int)` — the UI-facing result model used by every controller. `index` is the stable position in the result list (used as the `LazyColumn` key and for transient-highlight bookkeeping).
  - `interface ReaderSearchController { suspend fun search(query: String): List<ReaderSearchResult> }`.
  - `class TextSearchController(private val text: String, private val engine: TextSearchEngine = TextSearchEngine()) : ReaderSearchController`. Implement `search` to call `engine.search(text, query)`, then `mapIndexed { i, m -> ReaderSearchResult(index = i, locator = m.locator, snippet = m.snippet, snippetMatchStart = m.snippetMatchStart, snippetMatchEnd = m.snippetMatchEnd) }`. Keep it `suspend` (no actual suspension needed for text, but the interface is shared with the async epub.js controller).
- [ ] Run: `./gradlew :androidApp:testDebugUnitTest --tests 'com.continuum.app.android.ui.screens.reader.search.TextSearchControllerTest'`. Confirm green.

---

### Task 4 — `EpubSearchController` over the epub.js JS bridge

epub.js exposes `book.search(query)` per-section (commonly via iterating `book.spine.spineItems` and calling `section.find(query)` / `section.search(query)`, each returning `{ cfi, excerpt }` objects). This controller cannot be JVM-unit-tested (needs a WebView + bundled epub.js), so it is verified on-device in Task 8. Implement against the bridge defined in Task 5.

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/search/EpubSearchController.kt`

- [ ] Define a transport-agnostic bridge contract this controller depends on (so it does not bake in WebView types):

```kotlin
package com.continuum.app.android.ui.screens.reader.search

/** Implemented in EpubReader.kt by the epub.js WebView host (Task 5). */
interface EpubSearchBridge {
    /** Runs epub.js search across the book; resolves with raw hits. */
    suspend fun search(query: String): List<EpubSearchHit>
}

data class EpubSearchHit(val cfi: String, val excerpt: String)
```

- [ ] Implement the controller:

```kotlin
package com.continuum.app.android.ui.screens.reader.search

import com.continuum.app.model.reader.ReaderLocator

class EpubSearchController(
    private val bridge: EpubSearchBridge,
) : ReaderSearchController {
    override suspend fun search(query: String): List<ReaderSearchResult> {
        if (query.isBlank()) return emptyList()
        return bridge.search(query).mapIndexed { i, hit ->
            // epub.js excerpts are already windowed around the match; we
            // surface them verbatim. We do not know the in-excerpt match
            // offsets reliably across reflow, so highlight the whole
            // snippet rather than a sub-range (matchStart=0, matchEnd=len).
            ReaderSearchResult(
                index = i,
                locator = ReaderLocator.Cfi(hit.cfi),
                snippet = hit.excerpt.trim(),
                snippetMatchStart = 0,
                snippetMatchEnd = hit.excerpt.trim().length,
            )
        }
    }
}
```

- [ ] Confirm it compiles as part of Task 5's build (it has no standalone test). Note in the report that the in-excerpt match offsets are intentionally the whole snippet for EPUB because epub.js excerpt windowing is opaque.

---

### Task 5 — Extend the EPUB epub.js WebView bridge with search + transient highlight (device-verified)

This wires `EpubSearchBridge` to the real epub.js host. epub.js search is asynchronous in JS; bridge it with a request-id map and a `@JavascriptInterface` callback that resumes a suspended coroutine.

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/EpubReader.kt` (or the Phase 2 paginated epub.js host file, if separate — edit whichever hosts the epub.js `WebView`)

- [ ] In the epub.js host, ensure `webView.settings.javaScriptEnabled = true` (the Phase 2 paginated host already needs this; the legacy per-chapter `EpubReader` shown today sets it `false`, which is fine because that path does not use search — only the epub.js host implements `EpubSearchBridge`).
- [ ] Add a JS↔Kotlin bridge object with request-id correlation. Add the following `@JavascriptInterface`-annotated host class and register it via `webView.addJavascriptInterface(host, "AndroidSearch")`:

```kotlin
private class EpubSearchJsBridge : EpubSearchBridge {
    private val pending = java.util.concurrent.ConcurrentHashMap<
        Int, kotlinx.coroutines.CompletableDeferred<List<EpubSearchHit>>>()
    private val nextId = java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile var webView: android.webkit.WebView? = null

    override suspend fun search(query: String): List<EpubSearchHit> {
        val view = webView ?: return emptyList()
        val id = nextId.incrementAndGet()
        val deferred = kotlinx.coroutines.CompletableDeferred<List<EpubSearchHit>>()
        pending[id] = deferred
        val encoded = org.json.JSONObject.quote(query)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            view.evaluateJavascript("window.siloSearch($id, $encoded);", null)
        }
        return try {
            kotlinx.coroutines.withTimeout(15_000) { deferred.await() }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            pending.remove(id); emptyList()
        }
    }

    @android.webkit.JavascriptInterface
    fun onSearchResults(requestId: Int, json: String) {
        val deferred = pending.remove(requestId) ?: return
        val arr = org.json.JSONArray(json)
        val hits = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            EpubSearchHit(cfi = o.getString("cfi"), excerpt = o.optString("excerpt"))
        }
        deferred.complete(hits)
    }
}
```

- [ ] Add the JS side to the epub.js bootstrap HTML/JS (the harness Phase 2 injects). Define `window.siloSearch` to iterate the spine and post results back to `AndroidSearch.onSearchResults`:

```javascript
window.siloSearch = function (requestId, query) {
  var book = window.siloBook; // the epub.js Book created in Phase 2 bootstrap
  var items = book.spine.spineItems;
  Promise.all(items.map(function (item) {
    return item.load(book.load.bind(book))
      .then(function () { return item.find(query); })
      .finally(function () { item.unload(); });
  })).then(function (perSection) {
    var hits = [];
    perSection.forEach(function (list) {
      (list || []).forEach(function (m) {
        hits.push({ cfi: m.cfi, excerpt: m.excerpt });
      });
    });
    AndroidSearch.onSearchResults(requestId, JSON.stringify(hits));
  }).catch(function () {
    AndroidSearch.onSearchResults(requestId, "[]");
  });
};

window.siloHighlightCfi = function (cfi) {
  try {
    window.siloRendition.annotations.remove(window.siloLastHl, "highlight");
  } catch (e) {}
  window.siloLastHl = cfi;
  window.siloRendition.display(cfi).then(function () {
    window.siloRendition.annotations.highlight(cfi, {}, function () {},
      "silo-search-hl", { "fill": "yellow", "fill-opacity": "0.4" });
  });
};

window.siloClearHighlight = function () {
  try {
    window.siloRendition.annotations.remove(window.siloLastHl, "highlight");
  } catch (e) {}
  window.siloLastHl = null;
};
```

  (Adjust `window.siloBook` / `window.siloRendition` to the exact global names the Phase 2 bootstrap assigns; if Phase 2 named them differently, use those names and note the mapping.)
- [ ] Expose the bridge instance and two navigation helpers from the composable up to the caller via callbacks so the ViewModel/screen can drive them:
  - `onSearchBridgeReady: (EpubSearchBridge) -> Unit` — invoked once the WebView + epub.js are loaded.
  - `highlightTarget: ReaderLocator.Cfi?` parameter — a `LaunchedEffect(highlightTarget)` calls `view.evaluateJavascript("window.siloHighlightCfi(${JSONObject.quote(it.cfi)});", null)` when non-null, and `window.siloClearHighlight()` when null.
- [ ] Wire `EpubSearchJsBridge.webView` to the created `WebView` in the `AndroidView` `factory`, register the interface, and call `onSearchBridgeReady(bridge)` after `onPageFinished`.
- [ ] **adb manual verification:**
  - [ ] Build & install: `./gradlew :androidApp:installDebug` then launch: `adb shell am start -n com.continuum.app/.MainActivity` (confirm the launcher activity name with `adb shell cmd package resolve-activity --brief com.continuum.app`).
  - [ ] Open an EPUB, open the search sheet (Task 7), type a word known to occur (e.g. "the"), confirm results appear within ~2s.
  - [ ] Capture logcat for bridge round-trips: `adb logcat -v time | grep -iE "siloSearch|onSearchResults|chromium"` while searching; confirm no JS errors.
  - [ ] Tap a result; confirm the reader navigates to the CFI and the match is highlighted, then verify the highlight clears when the sheet is dismissed (ViewModel sets `highlightTarget = null`).

---

### Task 6 — Hoist loaded text from TextReader / FictionBookReader + accept highlight/scroll

The string-format readers already hold the full document in memory. Surface it so the ViewModel can build a `TextSearchController`, and accept a transient char-range highlight + scroll-to-offset.

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/TextReader.kt`
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/FictionBookReader.kt`

- [ ] In `TextReader`, add params: `onTextLoaded: (String) -> Unit` and `highlightRange: IntRange? = null` and `scrollToOffset: Int? = null`. When `result` resolves to text, call `onTextLoaded(text)` from a `LaunchedEffect(text)`. Pass `highlightRange` / `scrollToOffset` into `TextDocumentContent`.
- [ ] In `TextDocumentContent`, render the body with an `AnnotatedString` so a transient highlight can be applied: when `highlightRange != null`, wrap that char range in a `SpanStyle(background = MaterialTheme.colorScheme.tertiaryContainer)` via `buildAnnotatedString`. Otherwise render the plain text as today. Keep existing color/size/theme behavior.
- [ ] For `scrollToOffset`, replace the bare `rememberScrollState()` usage with one that can be scrolled programmatically: keep the `verticalScroll(scrollState)` but add `LaunchedEffect(scrollToOffset)` that, when non-null, approximates the pixel position. Use a `Text` `onTextLayout` callback to capture the `TextLayoutResult`, then `scrollState.animateScrollTo(textLayout.getBoundingBox(offset.coerceIn(0, text.length - 1)).top.toInt())`. Guard against `null` layout (no scroll until laid out). This gives a "good enough" jump for a scrolling text view; document that pixel-accurate jumping is best-effort for the non-paginated text reader.
- [ ] In `FictionBookReader`, add the same three params and forward `onTextLoaded(result.text)` from the `FictionBookLoadResult.Loaded` branch (in a `LaunchedEffect(result.text)`), passing `highlightRange` / `scrollToOffset` into the shared `TextDocumentContent`.
- [ ] Build only (no new unit test; UI behavior verified on-device in Task 8): `./gradlew :androidApp:compileDebugKotlin`.

---

### Task 7 — ViewModel search state + `ReaderSearchSheet` UI + screen wiring

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModel.kt`
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/search/ReaderSearchSheet.kt`
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt`

- [ ] In `ReaderViewModel`, add to `ReaderUiState`:
  - `val searchQuery: String = ""`
  - `val searchResults: List<ReaderSearchResult> = emptyList()`
  - `val isSearching: Boolean = false`
  - `val searchHighlightCfi: ReaderLocator.Cfi? = null` (drives the EPUB transient highlight)
  - `val searchHighlightRange: IntRange? = null` (drives the text/FB2 transient highlight)
  - `val searchScrollOffset: Int? = null` (drives the text/FB2 scroll jump)
- [ ] Add ViewModel fields: `private var textSearchController: TextSearchController? = null`, `private var epubSearchController: EpubSearchController? = null`, `private var searchJob: Job? = null`.
- [ ] Add intents:
  - `fun onSearchableTextLoaded(text: String) { textSearchController = TextSearchController(text) }` — called by Text/FB2 readers via `onTextLoaded`.
  - `fun onEpubSearchBridgeReady(bridge: EpubSearchBridge) { epubSearchController = EpubSearchController(bridge) }` — called by the EPUB host.
  - `fun setSearchQuery(q: String)` — updates `searchQuery`, cancels `searchJob`, and (debounced ~250ms) launches `runSearch`. Use `searchJob?.cancel(); searchJob = viewModelScope.launch { delay(250); runSearch(q) }`.
  - `private suspend fun runSearch(q: String)`:
    - If `q.isBlank()`: update state to empty results, `isSearching = false`; return.
    - Pick controller: `val controller = when (format) { Epub -> epubSearchController; Txt/Markdown/Fb2/Fbz -> textSearchController; else -> null }`.
    - If `controller == null` (PDF/Comic/not-yet-loaded): set `searchResults = emptyList()`, `isSearching = false`. The sheet decides the PDF-deferred copy from `format`/`capabilities`, not from a result list.
    - Else: set `isSearching = true`; `val results = controller.search(q)`; update `searchResults = results, isSearching = false`.
  - `fun jumpToSearchResult(result: ReaderSearchResult)`:
    - When `result.locator is ReaderLocator.Cfi`: set `searchHighlightCfi = result.locator`, clear `searchHighlightRange`/`searchScrollOffset`. (epub.js `display(cfi)` performs the navigation.)
    - When `result.locator is ReaderLocator.CharOffset`: set `searchHighlightRange = result.locator.start until result.locator.end`, `searchScrollOffset = result.locator.start`, clear `searchHighlightCfi`.
    - Also persist progress to the jumped location by reusing the existing progress path is **out of scope** for search jumps (search is navigation, not progress); do not call `onPageChanged` here.
  - `fun openSearch()` / `fun closeSearch()` — `closeSearch` clears query, results, and **all** transient highlight fields (`searchHighlightCfi = null`, `searchHighlightRange = null`, `searchScrollOffset = null`) so the highlight is genuinely transient.
- [ ] Create `ReaderSearchSheet.kt` following the `ModalBottomSheet` pattern already in `ReaderScreen.kt` (`SectionsSheet`/`BookmarkSheet`):

```kotlin
package com.continuum.app.android.ui.screens.reader.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSearchSheet(
    query: String,
    results: List<ReaderSearchResult>,
    isSearching: Boolean,
    searchSupported: Boolean,
    onQueryChange: (String) -> Unit,
    onResultClick: (ReaderSearchResult) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Search", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        if (!searchSupported) {
            Text(
                "Search is not available for this format.",
                modifier = Modifier.padding(16.dp),
            )
            return@ModalBottomSheet
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Find in book") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        when {
            isSearching -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                CircularProgressIndicator()
            }
            query.isNotBlank() && results.isEmpty() -> Text(
                "No results.",
                modifier = Modifier.padding(16.dp),
            )
            else -> LazyColumn {
                items(results, key = { it.index }) { r ->
                    ListItem(
                        headlineContent = { Text(r.snippet) },
                        modifier = Modifier.clickable { onResultClick(r) },
                    )
                }
            }
        }
    }
}
```

  - The PDF-deferred case is the `!searchSupported` branch ("Search is not available for this format."), satisfying §9's "show a search-not-available note for PDF."
- [ ] In `ReaderScreen.kt`:
  - Add `import androidx.compose.material.icons.filled.Search` and a `var showSearch by remember { mutableStateOf(false) }`.
  - Add an `IconButton(onClick = { showSearch = true }, enabled = state.capabilities.supportsSearch) { Icon(Icons.Default.Search, contentDescription = "Search") }` in the top toolbar row (place before the Sections icon).
  - Pass the new params through to the format readers: `TextReader` / `FictionBookReader` receive `onTextLoaded = viewModel::onSearchableTextLoaded`, `highlightRange = state.searchHighlightRange`, `scrollToOffset = state.searchScrollOffset`. `EpubReader` (epub.js host) receives `onSearchBridgeReady = viewModel::onEpubSearchBridgeReady` and `highlightTarget = state.searchHighlightCfi`.
  - Render the sheet:
    ```kotlin
    if (showSearch) {
        ReaderSearchSheet(
            query = state.searchQuery,
            results = state.searchResults,
            isSearching = state.isSearching,
            searchSupported = state.capabilities.supportsSearch,
            onQueryChange = viewModel::setSearchQuery,
            onResultClick = { viewModel.jumpToSearchResult(it); showSearch = false },
            onDismiss = { showSearch = false; viewModel.closeSearch() },
        )
    }
    ```
- [ ] Build: `./gradlew :androidApp:compileDebugKotlin`. Fix any wiring/signature mismatches.

---

### Task 8 — On-device end-to-end verification (adb)

No JVM test can cover the WebView/Compose integration; verify on a device/emulator.

**Files:** none (verification only).

- [ ] Install: `./gradlew :androidApp:installDebug`.
- [ ] Launch the app: `adb shell monkey -p com.continuum.app -c android.intent.category.LAUNCHER 1`.
- [ ] **EPUB:** open an EPUB → tap Search → type a common word → confirm a results list with snippets → tap a result → reader jumps to the passage and the match is highlighted → dismiss the sheet → confirm the highlight clears. Watch `adb logcat -v time | grep -iE "siloSearch|onSearchResults|System.err|chromium"` for JS errors.
- [ ] **TXT/Markdown:** open a `.txt` → search a word that appears multiple times → confirm result count and document order match expectations → tap result #2 → confirm the view scrolls to and highlights that occurrence (not the first).
- [ ] **FB2/FBZ:** open an `.fb2` → repeat the TXT check → confirm parsed-text search works through the shared `TextDocumentContent` highlight path.
- [ ] **PDF:** open a `.pdf` → confirm the Search toolbar icon is **disabled** (`supportsSearch = false`); if reachable, the sheet shows "Search is not available for this format." This confirms PDF search is **deferred**, not broken.
- [ ] **Comic (CBZ):** confirm the Search icon is disabled (N/A).
- [ ] **Empty/no-results:** in EPUB and TXT, search a string that does not occur → confirm "No results." and no crash; clear the field → confirm results clear and `isSearching` resolves.
- [ ] Record findings (timings, any large-book latency on the epub.js spine scan) in the task report.

---

## Self-Review vs Spec (§5, §8 Phase 5, §9)

- **§5 "per-format search producing `ReaderLocator` results with snippets, shown in a results sheet that jumps on tap":** Covered. `ReaderSearchResult` carries `locator: ReaderLocator` + `snippet`; `ReaderSearchSheet` is the results sheet; `jumpToSearchResult` performs the jump. ✓
- **§5 "EPUB via JS bridge":** `EpubSearchController` + Task 5 epub.js `book.spine` / `section.find` bridge → `ReaderLocator.Cfi`. ✓
- **§5 "text/FB2 via Kotlin string search … snippet windows + match offsets → CharOffset locator":** `TextSearchEngine` produces snippet + `snippetMatchStart/End` + `ReaderLocator.CharOffset`. ✓ (Tested in Task 2.)
- **§5 / §9 "PDF DEFERRED — PdfRenderer has no text layer — show note":** No PDF controller invented; `supportsSearch = false` for PDF; sheet shows "Search is not available for this format." No text layer fabricated. ✓
- **§5 "comic N/A":** `supportsSearch = false` for CBZ; no controller. ✓
- **§5 "tapping a result jumps to its locator and highlights the match transiently":** EPUB via `siloHighlightCfi`/`siloClearHighlight`; text/FB2 via `searchHighlightRange` `SpanStyle` + `closeSearch` clearing all highlight state on dismiss. ✓ (Transience verified in Tasks 5 & 8.)
- **§8 Phase 5 scope ("EPUB/text/FB2; PDF deferred"):** Exactly matches. ✓
- **TDD requirement:** The pure search logic (case-insensitivity, snippet windowing, char-offset → CharOffset, multi-match ordering, empty/no-results, capping) is test-first in Tasks 2–3. WebView/Compose paths use adb manual verification with real code. ✓

**Fix applied inline during review:** The EPUB excerpt match offsets are set to the whole snippet (`snippetMatchStart = 0`, `snippetMatchEnd = excerpt.length`) because epub.js excerpt windowing does not expose a reliable in-excerpt match index across reflow — documented in Task 4 rather than inventing a fake offset.

**Deferred items (explicit):**
- PDF in-text search — deferred (no `PdfRenderer` text layer); UI shows a not-available note. Revisit only if a text-extraction lib is added (spec §9).
- Comic (CBZ) search — N/A (no text content).
- Pixel-accurate scroll-to-match in the non-paginated `TextReader` — best-effort via `TextLayoutResult.getBoundingBox`; exact pagination jump lands with FB2/text pagination parity in Phase 7, out of scope here.
