# Ebook Reader — Phase 2: Paginated EPUB (epub.js) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Replace the current "bones-level" WebView-scroll EPUB renderer (`EpubReader.kt`) with a `PaginatedWebReader` that hosts **epub.js** inside a WebView to deliver real page turns, accurate page counts, and CFI locators, with a typed JS↔Kotlin bridge. EPUB reading progress maps to `ReaderLocator.Cfi` (shipped in Phase 1), restores to the saved CFI on open, and keeps the existing theme/size/margin settings applied. Search and highlight injection are **later phases** — Phase 2 only establishes the bridge hooks they will reuse.

**Architecture:** A single `WebView` loaded from a bundled HTML harness (`androidApp/src/main/assets/reader/epubjs/reader.html`) that imports a vendored `epub.min.js` + `jszip.min.js`. The harness renders the unzipped EPUB (served from the on-disk cache via a `WebViewAssetLoader` content provider that maps both the bundled assets and the unpacked EPUB directory to `https://appassets.androidplatform.net/`). A `@JavascriptInterface` bridge (`EpubBridge`) carries strongly-typed JSON messages both directions: JS → Kotlin emits `ready`, `relocated` (current CFI + percentage + page label), `error`; Kotlin → JS calls `display(cfi)`, `next()`, `prev()`, `applyTheme(json)`. All message payloads are `kotlinx.serialization` data classes so the parsing is unit-testable pure Kotlin. CFI ↔ `ReaderLocator.Cfi` mapping is pure Kotlin in `shared` and TDD'd.

**Tech Stack:** Kotlin, Android `WebView` + `@JavascriptInterface` + `androidx.webkit.WebViewAssetLoader`, **epub.js** (vendored web asset), `kotlinx.serialization` (JSON bridge payloads), Jetpack Compose `AndroidView`, Robolectric (existing androidApp unit-test runner).

---

## Assumptions carried from Phase 1

- Phase 1 shipped `ReaderLocator` in `shared/src/commonMain/kotlin/com/continuum/app/model/reader/ReaderLocator.kt`, a `@Serializable sealed interface` with at least a `Cfi` variant. **This plan depends only on the `Cfi` variant and assumes this exact shape; if Phase 1's shape differs, adapt the mapping in Task 3 to match the real symbols — do not invent new variants here.** The assumed contract:
  ```kotlin
  @Serializable
  sealed interface ReaderLocator {
      @Serializable
      @SerialName("cfi")
      data class Cfi(
          val cfi: String,
          val progress: Double? = null, // 0.0..1.0, book-level fraction when known
      ) : ReaderLocator
      // other variants (Page, PageRect) exist for PDF/comic — not used here
  }

  /** Typed-JSON round-trip used by progress + annotations. */
  fun ReaderLocator.encodeToLocationString(): String
  fun decodeReaderLocator(location: String?): ReaderLocator? // tolerates legacy "page:N"
  ```
- Progress `location` is now a typed-JSON string produced by `ReaderLocator.encodeToLocationString()`; the legacy `"page:N"` form is still decodable via `decodeReaderLocator` (used by PDF/comic and old rows). EPUB rows written by Phase 2 carry the `cfi` form.
- If `ReaderLocator` is NOT present when Phase 2 starts, STOP and complete Phase 1 first — Phase 2 is built on it.

## File Structure

Real paths, with responsibility:

- `androidApp/src/main/assets/reader/epubjs/epub.min.js` — vendored epub.js library (web asset). **New.**
- `androidApp/src/main/assets/reader/epubjs/jszip.min.js` — vendored JSZip (epub.js peer dependency). **New.**
- `androidApp/src/main/assets/reader/epubjs/reader.html` — HTML harness that boots epub.js, exposes `window.PrairieReader` JS API, and posts messages to the `AndroidReaderBridge` interface. **New.**
- `androidApp/src/main/assets/reader/epubjs/reader.js` — harness glue (rendition setup, relocation listener, theme application, tap/swipe zones). **New.**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PaginatedWebReader.kt` — Compose `AndroidView` hosting the WebView, wiring `WebViewAssetLoader`, the bridge, and Kotlin→JS calls. Replaces `EpubReader`'s `HorizontalPager` for EPUB. **New.**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/EpubBridge.kt` — `@JavascriptInterface` host object + the Kotlin→JS command sender (`EpubCommandSender`). **New.**
- `shared/src/commonMain/kotlin/com/continuum/app/model/reader/EpubBridgeMessages.kt` — `@Serializable` bridge payload data classes (`BridgeEvent` and its parsing) — pure, KMP, unit-testable. **New (in shared so Apple/TV can reuse the wire format).**
- `shared/src/commonMain/kotlin/com/continuum/app/model/reader/EpubCfiMapping.kt` — pure `ReaderLocator.Cfi` ↔ epub.js CFI string mapping + `relocatedToLocator`. **New.**
- `shared/src/commonTest/kotlin/com/continuum/app/model/reader/EpubBridgeMessagesTest.kt` — bridge message parse/serialize round-trips + malformed-input handling. **New.**
- `shared/src/commonTest/kotlin/com/continuum/app/model/reader/EpubCfiMappingTest.kt` — CFI↔Cfi mapping + `page:N` rejection. **New.**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/EpubReader.kt` — **edited**: keep the unzip/parse (`EpubBook`) but route the composable through `PaginatedWebReader`; remove `HorizontalPager`/`EpubChapter`/`withReaderCss` once the harness owns rendering (CSS now injected via the bridge theme call). Keep `EpubBook.open` + `unpackedRoot` (the harness loads the unpacked tree).
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt` — **edited**: EPUB branch passes a CFI-aware `initialLocation` and CFI-aware `onLocationChanged` (replacing page-index callbacks for EPUB only).
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModel.kt` — **edited**: add `onLocationChanged(ReaderLocator)` for reflowable formats that persists a typed locator; keep `onPageChanged` for fixed-layout. Restore EPUB to saved `ReaderLocator.Cfi` on open.
- `androidApp/build.gradle.kts` — **edited**: add `androidx.webkit:webkit` dependency for `WebViewAssetLoader`.
- `gradle/libs.versions.toml` — **edited**: add the `androidx.webkit` version + library alias.
- `docs/architecture/reader-epubjs-bridge.md` — short reference doc for the bridge protocol (event/command JSON). **New** (architecture reference is explicitly allowed by repo guidelines).

---

### Task 0 — epub.js pagination spike (go / no-go vs Readium fallback)

Per spec §9: epub.js in a WebView is the top risk (asset bundling, bridge performance on large books, reflow reliability). Spike it on a device **before** building the production reader. This task produces a throwaway harness and a decision, not shippable code.

**Files:**
- `androidApp/src/main/assets/reader/spike/spike.html` (throwaway; delete after decision)
- `androidApp/src/main/assets/reader/epubjs/epub.min.js`, `jszip.min.js` (vendored — kept if go)

- [ ] Vendor epub.js + JSZip. Download pinned releases and place them:
  ```bash
  mkdir -p androidApp/src/main/assets/reader/epubjs
  curl -L -o androidApp/src/main/assets/reader/epubjs/jszip.min.js \
    https://cdn.jsdelivr.net/npm/jszip@3.10.1/dist/jszip.min.js
  curl -L -o androidApp/src/main/assets/reader/epubjs/epub.min.js \
    https://cdn.jsdelivr.net/npm/epubjs@0.3.93/dist/epub.min.js
  ```
- [ ] Record the pinned versions (`epubjs@0.3.93`, `jszip@3.10.1`) and their SHA-256 in `docs/architecture/reader-epubjs-bridge.md` so the vendored blobs are auditable:
  ```bash
  shasum -a 256 androidApp/src/main/assets/reader/epubjs/*.js
  ```
- [ ] Write a minimal `spike.html` that loads epub.js against an unpacked EPUB directory and renders with `flow: "paginated"`, `width: "100%"`, `height: "100%"`. Use `ePub(rootUrl, { openAs: "directory" })` so it reads the already-unpacked tree (matching `EpubBook.unpackedRoot`) rather than re-unzipping:
  ```html
  <!doctype html><html><head><meta name="viewport"
    content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"/>
  <script src="epubjs/jszip.min.js"></script>
  <script src="epubjs/epub.min.js"></script></head>
  <body style="margin:0"><div id="viewer" style="width:100vw;height:100vh"></div>
  <script>
    const book = ePub("UNPACKED_ROOT_URL/", { openAs: "directory" });
    const rendition = book.renderTo("viewer",
      { flow: "paginated", width: "100%", height: "100%", spread: "none" });
    book.ready
      .then(() => book.locations.generate(1600))
      .then(() => rendition.display())
      .then(() => { document.title = "READY:" + book.locations.length(); });
    rendition.on("relocated", (loc) => {
      document.title = "CFI:" + loc.start.cfi + ":" + loc.start.percentage;
    });
  </script></body></html>
  ```
- [ ] Build a one-off spike Activity/composable that loads `spike.html` via `WebViewAssetLoader` (see Task 1) and points `UNPACKED_ROOT_URL` at a real unpacked EPUB from `context.cacheDir/readers/epub-<key>`. Enable `javaScriptEnabled = true`.
- [ ] **Manual verify on a connected device** (`adb`), against a SMALL epub and a LARGE epub (>5MB, >300 spine pages):
  ```bash
  adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
  adb shell am start -n com.continuum.app/.android.ui.screens.reader.SpikeActivity
  adb logcat -s chromium PrairieReaderSpike   # observe console + title changes
  ```
  Confirm: (a) book renders paginated; (b) `book.locations.generate` completes and `READY:<N>` shows a plausible page/location count; (c) swipe/`rendition.next()` turns a page and `relocated` fires with a non-empty CFI; (d) on the LARGE book, `locations.generate` finishes within a tolerable budget (target < 8s on a mid-range device) and page turns stay responsive (no multi-second jank).
- [ ] **Go / no-go decision**, recorded in `docs/architecture/reader-epubjs-bridge.md`:
  - **GO (epub.js):** all four checks pass → proceed to Task 1.
  - **NO-GO (Readium fallback):** if pagination is unreliable, CFIs are empty/unstable, or large-book `locations.generate` is unacceptably slow → STOP this plan. Open a follow-up to re-scope Phase 2 onto **Readium-Kotlin** (`org.readium.kotlin-toolkit`), which owns pagination + CFI/Locator natively. The `ReaderLocator.Cfi` model, the ViewModel `onLocationChanged` path (Task 6), and the `ReaderScreen` wiring (Task 5) are renderer-agnostic and carry over; only Tasks 1–4 (the WebView harness/bridge) are discarded.
- [ ] If GO: delete the spike harness (`rm -r androidApp/src/main/assets/reader/spike`) and the spike Activity; keep the vendored `epubjs/*.js`.

---

### Task 1 — WebViewAssetLoader plumbing (bundled assets + unpacked EPUB on one origin)

epub.js needs same-origin access to both the bundled library and the EPUB content. Serve both under `https://appassets.androidplatform.net/` so there is no `file://`/CORS friction and JS is enabled safely (no broad `allowFileAccess`).

**Files:**
- `androidApp/build.gradle.kts`
- `gradle/libs.versions.toml`
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PaginatedWebReader.kt` (asset-loader builder portion)

- [ ] Add the webkit dependency. In `gradle/libs.versions.toml` under `[versions]` add `androidx-webkit = "1.12.1"`, under `[libraries]` add `androidx-webkit = { module = "androidx.webkit:webkit", version.ref = "androidx-webkit" }`.
- [ ] In `androidApp/build.gradle.kts` `androidMain.dependencies { ... }` add `implementation(libs.androidx.webkit)`.
- [ ] Build a `WebViewAssetLoader` that maps two path prefixes onto one origin: bundled harness/library under `/assets/reader/epubjs/...` and the unpacked EPUB under `/epub/...`:
  ```kotlin
  import androidx.webkit.WebViewAssetLoader
  import androidx.webkit.WebViewAssetLoader.AssetsPathHandler
  import androidx.webkit.WebViewAssetLoader.InternalStoragePathHandler

  internal fun buildReaderAssetLoader(context: Context, unpackedRoot: File): WebViewAssetLoader =
      WebViewAssetLoader.Builder()
          // bundled epub.js + harness: appassets.androidplatform.net/assets/reader/...
          .addPathHandler("/assets/", AssetsPathHandler(context))
          // unpacked epub tree: appassets.androidplatform.net/epub/<href>
          .addPathHandler("/epub/", InternalStoragePathHandler(context, unpackedRoot))
          .build()
  ```
  Note: `InternalStoragePathHandler` requires `unpackedRoot` to live under app-internal storage; `context.cacheDir/readers/...` (where `EpubBook` unpacks) satisfies this. Verify the directory is within `cacheDir`/`filesDir` — if not, move the unpack target into `context.cacheDir` (it already is).
- [ ] Confirm `gradlew :androidApp:assembleDebug` resolves the new dependency:
  ```bash
  ./gradlew :androidApp:compileDebugKotlin
  ```

---

### Task 2 — Bridge message model (TDD, pure Kotlin in `shared`)

Strongly-typed, serializable payloads for JS→Kotlin events. This is pure logic — test first.

**Files:**
- `shared/src/commonTest/kotlin/com/continuum/app/model/reader/EpubBridgeMessagesTest.kt`
- `shared/src/commonMain/kotlin/com/continuum/app/model/reader/EpubBridgeMessages.kt`

- [ ] Write failing tests in `EpubBridgeMessagesTest.kt`:
  - `parses a relocated event`: given `{"type":"relocated","cfi":"epubcfi(/6/4!/4/2)","percentage":0.42,"pageLabel":"12"}`, `parseBridgeEvent(json)` returns `BridgeEvent.Relocated(cfi="epubcfi(/6/4!/4/2)", percentage=0.42, pageLabel="12")`.
  - `parses a ready event`: `{"type":"ready","totalLocations":1600}` → `BridgeEvent.Ready(totalLocations=1600)`.
  - `parses an error event`: `{"type":"error","message":"render failed"}` → `BridgeEvent.Error("render failed")`.
  - `returns null for unknown type`: `{"type":"bogus"}` → `null` (not an exception).
  - `returns null for malformed json`: `"not json"` → `null`.
  - `percentage defaults to null when absent`: `{"type":"relocated","cfi":"x"}` → `Relocated(cfi="x", percentage=null, pageLabel=null)`.
- [ ] Implement `EpubBridgeMessages.kt` to pass:
  ```kotlin
  package com.continuum.app.model.reader

  import kotlinx.serialization.SerialName
  import kotlinx.serialization.Serializable
  import kotlinx.serialization.json.Json

  @Serializable
  sealed interface BridgeEvent {
      @Serializable @SerialName("ready")
      data class Ready(val totalLocations: Int) : BridgeEvent
      @Serializable @SerialName("relocated")
      data class Relocated(
          val cfi: String,
          val percentage: Double? = null,
          val pageLabel: String? = null,
      ) : BridgeEvent
      @Serializable @SerialName("error")
      data class Error(val message: String) : BridgeEvent
  }

  private val bridgeJson = Json {
      classDiscriminator = "type"
      ignoreUnknownKeys = true
      isLenient = true
  }

  /** Tolerant parse of a JS-emitted bridge event; null on malformed/unknown. */
  fun parseBridgeEvent(raw: String): BridgeEvent? =
      runCatching { bridgeJson.decodeFromString<BridgeEvent>(raw) }.getOrNull()
  ```
- [ ] Run `./gradlew :shared:testDebugUnitTest --tests "*EpubBridgeMessagesTest"` — all green.

---

### Task 3 — CFI ↔ ReaderLocator.Cfi mapping (TDD, pure Kotlin in `shared`)

**Files:**
- `shared/src/commonTest/kotlin/com/continuum/app/model/reader/EpubCfiMappingTest.kt`
- `shared/src/commonMain/kotlin/com/continuum/app/model/reader/EpubCfiMapping.kt`

- [ ] Write failing tests:
  - `relocated maps to Cfi locator`: `relocatedToLocator(BridgeEvent.Relocated(cfi="epubcfi(/6/4!/4)", percentage=0.5))` returns `ReaderLocator.Cfi(cfi="epubcfi(/6/4!/4)", progress=0.5)`.
  - `relocated with null percentage yields null progress`: `Relocated(cfi="x", percentage=null)` → `ReaderLocator.Cfi(cfi="x", progress=null)`.
  - `locator to display cfi extracts the raw cfi`: `displayCfiFor(ReaderLocator.Cfi(cfi="epubcfi(/6/4!/4)"))` == `"epubcfi(/6/4!/4)"`.
  - `legacy page location yields no cfi`: `displayCfiFor(decodeReaderLocator("page:3"))` == `null` (a `page:N` row can't restore to a CFI; the reader opens at the start). Asserts the Phase-1 backward-compat path is honored without crashing.
  - `blank cfi rejected`: `relocatedToLocator(Relocated(cfi="", percentage=0.1))` returns `null` (don't persist an empty CFI as progress).
- [ ] Implement `EpubCfiMapping.kt`:
  ```kotlin
  package com.continuum.app.model.reader

  /** JS relocation → typed locator. Null when the CFI is blank. */
  fun relocatedToLocator(event: BridgeEvent.Relocated): ReaderLocator.Cfi? {
      val cfi = event.cfi.trim()
      if (cfi.isEmpty()) return null
      return ReaderLocator.Cfi(cfi = cfi, progress = event.percentage?.coerceIn(0.0, 1.0))
  }

  /** Raw epub.js CFI to feed rendition.display(); null when locator isn't a CFI. */
  fun displayCfiFor(locator: ReaderLocator?): String? =
      (locator as? ReaderLocator.Cfi)?.cfi?.takeIf { it.isNotBlank() }
  ```
  Adjust the `ReaderLocator.Cfi` constructor/field names to the real Phase-1 symbols if they differ.
- [ ] Run `./gradlew :shared:testDebugUnitTest --tests "*EpubCfiMappingTest"` — green.

---

### Task 4 — HTML harness + bridge glue (epub.js host)

The web side. Owns rendering, theming, tap/swipe zones, and posting typed events to the host.

**Files:**
- `androidApp/src/main/assets/reader/epubjs/reader.html`
- `androidApp/src/main/assets/reader/epubjs/reader.js`

- [ ] `reader.html` — minimal shell that loads the vendored libs and the glue, all same-origin under `appassets.androidplatform.net`:
  ```html
  <!doctype html>
  <html>
    <head>
      <meta charset="utf-8"/>
      <meta name="viewport"
        content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover"/>
      <style>html,body{margin:0;height:100%;overflow:hidden;background:#fff}#viewer{height:100%}</style>
      <script src="/assets/reader/epubjs/jszip.min.js"></script>
      <script src="/assets/reader/epubjs/epub.min.js"></script>
    </head>
    <body>
      <div id="viewer"></div>
      <script src="/assets/reader/epubjs/reader.js"></script>
    </body>
  </html>
  ```
- [ ] `reader.js` — boots the book from the unpacked tree, generates locations, wires relocation + theme + tap zones, and posts events through `AndroidReaderBridge.postEvent(json)`:
  ```javascript
  (function () {
    var book, rendition;
    function post(obj) {
      try { AndroidReaderBridge.postEvent(JSON.stringify(obj)); } catch (e) {}
    }
    // Kotlin → JS surface.
    window.PrairieReader = {
      open: function (rootUrl, startCfi, themeJson) {
        book = ePub(rootUrl, { openAs: "directory" });
        rendition = book.renderTo("viewer", {
          flow: "paginated", width: "100%", height: "100%", spread: "none", manager: "default"
        });
        if (themeJson) { PrairieReader.applyTheme(themeJson); }
        book.ready
          .then(function () { return book.locations.generate(1600); })
          .then(function () {
            return rendition.display(startCfi && startCfi.length ? startCfi : undefined);
          })
          .then(function () { post({ type: "ready", totalLocations: book.locations.length() }); })
          .catch(function (e) { post({ type: "error", message: String(e && e.message || e) }); });
        rendition.on("relocated", function (loc) {
          post({
            type: "relocated",
            cfi: loc.start.cfi,
            percentage: typeof loc.start.percentage === "number" ? loc.start.percentage : null,
            pageLabel: (loc.start.displayed && String(loc.start.displayed.page)) || null
          });
        });
      },
      next: function () { if (rendition) rendition.next(); },
      prev: function () { if (rendition) rendition.prev(); },
      display: function (cfi) { if (rendition && cfi) rendition.display(cfi); },
      applyTheme: function (themeJson) {
        if (!rendition) return;
        var t = JSON.parse(themeJson); // {bg,fg,fontPercent,marginEm,fontFamily}
        rendition.themes.register("prairie", {
          "body": {
            "background": t.bg, "color": t.fg,
            "margin": t.marginEm + "em !important",
            "line-height": "1.55 !important",
            "font-family": t.fontFamily ? (t.fontFamily + " !important") : undefined
          },
          "img": { "max-width": "100% !important", "height": "auto !important" }
        });
        rendition.themes.select("prairie");
        rendition.themes.fontSize(t.fontPercent + "%");
      }
    };
    // Tap zones: left 33% = prev, right 33% = next, center reserved for chrome toggle (later phase).
    document.addEventListener("click", function (ev) {
      var x = ev.clientX, w = window.innerWidth;
      if (x < w * 0.33) { PrairieReader.prev(); }
      else if (x > w * 0.66) { PrairieReader.next(); }
      else { post({ type: "tapCenter" }); } // hook reserved; host ignores unknowns
    }, true);
  })();
  ```
  Note: `next/prev` also covers swipe because epub.js's paginated manager handles horizontal swipe natively; tap zones add explicit affordance. The `tapCenter` event is intentionally unknown to `parseBridgeEvent` (returns null) — reserved for the chrome-toggle hook in a later phase.

---

### Task 5 — `PaginatedWebReader` + `EpubBridge` (WebView host, Kotlin side)

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/EpubBridge.kt`
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PaginatedWebReader.kt`

- [ ] `EpubBridge.kt` — the `@JavascriptInterface` host and a `display`/`next`/`prev`/`applyTheme` sender:
  ```kotlin
  package com.continuum.app.android.ui.screens.reader

  import android.webkit.JavascriptInterface
  import android.webkit.WebView
  import com.continuum.app.model.reader.BridgeEvent
  import com.continuum.app.model.reader.parseBridgeEvent

  /** Host object exposed to JS as `AndroidReaderBridge`. All callbacks
   *  arrive on a WebView JS thread; [onEvent] must marshal to main itself. */
  internal class EpubBridge(private val onEvent: (BridgeEvent) -> Unit) {
      @JavascriptInterface
      fun postEvent(raw: String) {
          parseBridgeEvent(raw)?.let(onEvent) // unknown/malformed → ignored
      }
  }

  /** Kotlin → JS commands. Always invoked on the main thread. */
  internal class EpubCommandSender(private val web: WebView) {
      fun open(rootUrl: String, startCfi: String?, themeJson: String) =
          eval("window.PrairieReader.open(${js(rootUrl)}, ${js(startCfi ?: "")}, ${js(themeJson)})")
      fun next() = eval("window.PrairieReader.next()")
      fun prev() = eval("window.PrairieReader.prev()")
      fun display(cfi: String) = eval("window.PrairieReader.display(${js(cfi)})")
      fun applyTheme(themeJson: String) = eval("window.PrairieReader.applyTheme(${js(themeJson)})")
      private fun eval(script: String) = web.evaluateJavascript(script, null)
      // JSON-encode a string literal so CFIs/themes can't break out of the call.
      private fun js(value: String): String =
          buildString {
              append('"')
              value.forEach { c ->
                  when (c) {
                      '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n")
                      '\r' -> append("\\r"); '\t' -> append("\\t"); '<' -> append("\\u003C")
                      else -> append(c)
                  }
              }
              append('"')
          }
  }
  ```
- [ ] Add a small unit test for `EpubCommandSender.js` escaping if it is extracted to a pure helper. To keep `js(...)` testable without a WebView, **extract it** to a top-level `internal fun jsStringLiteral(value: String): String` in `EpubBridge.kt` and test in `androidApp/src/androidUnitTest/.../EpubCommandSenderTest.kt`: a CFI containing `"` and `</script>` round-trips to a literal that contains `\"` and `</script>` (no raw `"` or `<`). Run `./gradlew :androidApp:testDebugUnitTest --tests "*EpubCommandSenderTest"`.
- [ ] `PaginatedWebReader.kt` — Compose host:
  ```kotlin
  package com.continuum.app.android.ui.screens.reader

  import android.annotation.SuppressLint
  import android.webkit.WebResourceRequest
  import android.webkit.WebResourceResponse
  import android.webkit.WebView
  import android.webkit.WebViewClient
  import androidx.compose.foundation.layout.fillMaxSize
  import androidx.compose.runtime.*
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.platform.LocalContext
  import androidx.compose.ui.viewinterop.AndroidView
  import androidx.webkit.WebViewAssetLoader
  import com.continuum.app.common.ebook.ReaderDisplaySettings
  import com.continuum.app.model.reader.BridgeEvent
  import com.continuum.app.model.reader.ReaderLocator
  import com.continuum.app.model.reader.displayCfiFor
  import com.continuum.app.model.reader.relocatedToLocator
  import kotlinx.coroutines.Dispatchers
  import kotlinx.coroutines.withContext
  import java.io.File

  private const val READER_ORIGIN = "https://appassets.androidplatform.net"

  /**
   * Paginated EPUB renderer: a WebView hosting epub.js. Loads the
   * already-unpacked EPUB ([unpackedRoot]) over a same-origin
   * WebViewAssetLoader, restores to [initialLocator] (a CFI), and emits
   * relocations as ReaderLocator.Cfi via [onLocationChanged].
   */
  @SuppressLint("SetJavaScriptEnabled")
  @Composable
  fun PaginatedWebReader(
      unpackedRoot: File,
      settings: ReaderDisplaySettings,
      initialLocator: ReaderLocator?,
      onLocationChanged: (ReaderLocator.Cfi) -> Unit,
      onTotalLocationsKnown: (Int) -> Unit,
      modifier: Modifier = Modifier,
  ) {
      val context = LocalContext.current
      val assetLoader = remember(unpackedRoot) { buildReaderAssetLoader(context, unpackedRoot) }
      // Keep a stable sender so theme changes don't reload the book.
      var sender by remember { mutableStateOf<EpubCommandSender?>(null) }
      var ready by remember { mutableStateOf(false) }
      val themeJson = remember(settings) { settings.toEpubThemeJson() }

      AndroidView(
          modifier = modifier.fillMaxSize(),
          factory = { ctx ->
              WebView(ctx).apply {
                  settings.javaScriptEnabled = true
                  settings.allowFileAccess = false      // assets come via the loader, not file://
                  settings.allowContentAccess = false
                  webViewClient = object : WebViewClient() {
                      override fun shouldInterceptRequest(
                          view: WebView, request: WebResourceRequest,
                      ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

                      override fun onPageFinished(view: WebView, url: String?) {
                          val s = EpubCommandSender(view)
                          sender = s
                          val startCfi = displayCfiFor(initialLocator).orEmpty()
                          // unpacked tree → appassets origin /epub/
                          val rootUrl = "$READER_ORIGIN/epub/"
                          s.open(rootUrl, startCfi, themeJson)
                      }
                  }
                  addJavascriptInterface(
                      EpubBridge { event ->
                          post {  // marshal JS thread → main
                              when (event) {
                                  is BridgeEvent.Relocated ->
                                      relocatedToLocator(event)?.let(onLocationChanged)
                                  is BridgeEvent.Ready -> {
                                      ready = true
                                      onTotalLocationsKnown(event.totalLocations)
                                  }
                                  is BridgeEvent.Error -> { /* surface via logcat for now */ }
                              }
                          }
                      },
                      "AndroidReaderBridge",
                  )
                  loadUrl("$READER_ORIGIN/assets/reader/epubjs/reader.html")
              }
          },
      )
      // Re-apply theme on settings change without reloading the book.
      LaunchedEffect(themeJson, ready) {
          if (ready) sender?.applyTheme(themeJson)
      }
  }
  ```
- [ ] Add `ReaderDisplaySettings.toEpubThemeJson()` (Android-side extension, colocated in `PaginatedWebReader.kt`) reusing the existing color logic from the old `withReaderCss`:
  ```kotlin
  import com.continuum.app.common.ebook.ReaderTheme

  internal fun ReaderDisplaySettings.toEpubThemeJson(): String {
      val n = normalized()
      val (bg, fg) = when (n.theme) {
          ReaderTheme.System, ReaderTheme.Light -> "#fffbfe" to "#1c1b1f"
          ReaderTheme.Sepia -> "#f4ecd8" to "#2b2118"
          ReaderTheme.Dark -> "#1c1b1f" to "#e6e1e5"
      }
      val fontPercent = (n.textScale * 100).toInt()
      val marginEm = n.marginScale * 1.2f
      // Plain JSON; values are numeric/known-safe hex, no user input.
      return """{"bg":"$bg","fg":"$fg","fontPercent":$fontPercent,"marginEm":$marginEm}"""
  }
  ```
  Note: `fontFamily` is intentionally omitted in Phase 2 (font family lands in Phase 3); the harness treats a missing `fontFamily` as "inherit".

---

### Task 6 — Wire EPUB through `ReaderViewModel` + `ReaderScreen` (CFI progress)

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModel.kt`
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt`
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/EpubReader.kt`

- [ ] In `ReaderViewModel`, add a reflowable-format location path that persists a typed locator (parallel to the existing `onPageChanged`, which stays for PDF/comic):
  ```kotlin
  import com.continuum.app.model.reader.ReaderLocator
  import com.continuum.app.model.reader.encodeToLocationString

  fun onLocationChanged(locator: ReaderLocator.Cfi) {
      val state = _uiState.value
      val fileId = state.fileId ?: return
      // suppress the first relocation that merely echoes the restored position
      if (shouldSuppressInitialPageChange &&
          locator.cfi == (decodeReaderLocator(state.progressLocation) as? ReaderLocator.Cfi)?.cfi) {
          shouldSuppressInitialPageChange = false
          return
      }
      shouldSuppressInitialPageChange = false
      val location = (locator as ReaderLocator).encodeToLocationString()
      val percent = locator.progress?.coerceIn(0.0, 1.0) ?: state.progressPercent
      _uiState.update { it.copy(progressLocation = location, progressPercent = percent) }
      persistProgress(fileId, location, percent) // extract the existing local-write + lazy server-save block from onPageChanged into this shared helper
  }
  ```
  - [ ] Refactor the local-write + debounced server-`saveProgress` block currently inside `onPageChanged` (lines ~293–333) into a private `persistProgress(fileId: Int, location: String, percent: Double)` and call it from BOTH `onPageChanged` and `onLocationChanged`. This is the "extract shared logic" the repo guidelines require — no duplication.
- [ ] Expose the restored locator to the EPUB renderer. Add a derived `initialLocator: ReaderLocator?` to `ReaderUiState` computed from `progressLocation` via `decodeReaderLocator`, OR compute it inline in `ReaderScreen`. Prefer a `ReaderUiState.initialLocator` val so the screen stays thin:
  ```kotlin
  val initialLocator: ReaderLocator? get() = decodeReaderLocator(progressLocation)
  ```
- [ ] In `ReaderScreen`, change the `BookFormat.Epub` branch to call the new reader. Replace the old `EpubReader(...)` call:
  ```kotlin
  BookFormat.Epub -> EpubReader(
      fileUrl = state.fileUrl!!,
      settings = state.displaySettings,
      initialLocator = state.initialLocator,
      onLocationChanged = viewModel::onLocationChanged,
      onTotalLocationsKnown = viewModel::onPageCountKnown,
  )
  ```
- [ ] In `EpubReader.kt`: keep `EpubBook.open` (unzip/parse) but replace the `HorizontalPager`/`EpubChapter` body with a call to `PaginatedWebReader(unpackedRoot = b.unpackedRoot, ...)`. Update the `EpubReader` composable signature to `(fileUrl, settings, initialLocator, onLocationChanged, onTotalLocationsKnown)`. Delete `EpubChapter`, `EpubChapterContent`, and `withReaderCss` (the harness now owns CSS via `toEpubThemeJson`). Keep `EpubBook` (still needed for unpack + `unpackedRoot`); `readChapterHtml` can be removed since the harness reads files directly.
  - [ ] `onSectionsKnown`/TOC: Phase 2 drops the crude spine-as-sections list (it was page-index based and no longer maps to CFI). The Sections sheet for EPUB is **deferred to a later phase** (real epub.js TOC → CFI). Update `ReaderScreen`'s EPUB branch to not pass `onSectionsKnown`; `state.sections` stays empty for EPUB, which correctly disables the Sections button (it's gated on `state.sections.isNotEmpty()`). Note this deferral in the architecture doc.
- [ ] EPUB bookmarks: `ReaderScreen`'s bookmark/section "jump" currently uses `ebookPageNumberFromProgressLocation` → `jumpToPage`. For EPUB this no longer applies (bookmarks now carry CFI locations). For Phase 2, add `viewModel.jumpToLocator(locator: ReaderLocator.Cfi)` that updates state + tells the active reader to `display(cfi)`. The renderer reads a `displayLocator: ReaderLocator?` parameter (a `State` the VM drives) and calls `sender?.display(...)` in a `LaunchedEffect`. If wiring a VM→renderer display channel is too large for this task, **scope it down**: keep bookmark *creation* (CFI captured from current `progressLocation`) but mark bookmark *jump* for EPUB as deferred alongside Sections, and disable the jump-on-tap for CFI bookmarks. Pick one and record it in the architecture doc; do not leave a tap that silently does nothing.

---

### Task 7 — Architecture doc + protocol reference

**Files:**
- `docs/architecture/reader-epubjs-bridge.md`

- [ ] Document the bridge protocol: the JS→Kotlin events (`ready`, `relocated`, `error`, reserved `tapCenter`) with their JSON shape; the Kotlin→JS commands (`open`, `next`, `prev`, `display`, `applyTheme`); the same-origin `WebViewAssetLoader` mapping (`/assets/reader/epubjs/` → bundled, `/epub/` → unpacked tree); the vendored library versions + SHA-256; and the Phase-2 deferrals (Sections TOC, EPUB bookmark-jump or font-family, per whatever Task 6 decided). Use repository-relative paths only (no absolute/worktree paths — repo guideline).

---

### Task 8 — On-device manual verification (epub.js behavior needs a device)

Pagination, page counts, and reopen-restore are WebView/epub.js behaviors that unit tests can't cover. Verify on a connected device.

**Files:** none (verification only).

- [ ] Build + install:
  ```bash
  ./gradlew :androidApp:installDebug
  ```
- [ ] **Page turns (tap + swipe):** open an EPUB, tap right third → advances one page; tap left third → goes back; horizontal swipe → advances. Confirm no vertical scroll within a page (true pagination, not the old scroll behavior). Watch relocations:
  ```bash
  adb logcat -s chromium
  ```
- [ ] **Accurate page count / progress:** the `ReaderScreen` header (`"${(progressPercent*100).toInt()}% · Page ..."`) updates as you turn pages and reaches ~100% at the end. Confirm `onTotalLocationsKnown` fired (header shows a total) and the percentage advances monotonically forward.
- [ ] **Reopen restores position:** turn to roughly the middle of the book, background the app, kill it (`adb shell am force-stop com.continuum.app`), relaunch, reopen the same book. It restores to within one page of where you left off (CFI restore). Verify the persisted location is a CFI typed-locator, not `page:N`:
  ```bash
  adb shell run-as com.continuum.app sqlite3 \
    databases/<local_state_db> "select location from <progress_table> order by updated_at desc limit 1;"
  ```
  (Use the real DB/table names from `EbookLocalStateStore`'s SQLDelight schema; if unknown, grep `localStateStore` impl for the table.)
- [ ] **Theme/size/margins still apply:** open reader settings, change theme (Light/Sepia/Dark), font size, and margins. Each change re-applies live via `applyTheme` WITHOUT reloading the book (position is preserved across a theme change). Confirm Sepia/Dark backgrounds render and text size visibly changes.
- [ ] **Large-book sanity:** repeat page-turn + reopen on the LARGE epub from Task 0. `Ready` arrives within the Task 0 budget; page turns stay responsive.
- [ ] **Offline path:** download an EPUB (offline media), open it with the network off. The reader resolves the local `file://` via `resolveReaderFile`, `EpubBook.open` unpacks under `cacheDir`, and `InternalStoragePathHandler` serves it. Confirm pagination + restore still work offline.

---

### Task 9 — Full verification gate

**Files:** none.

- [ ] `./gradlew :shared:testDebugUnitTest` — all shared unit tests green (bridge + CFI mapping).
- [ ] `./gradlew :androidApp:testDebugUnitTest` — androidApp unit tests green (`EpubCommandSenderTest` + existing reader tests still pass after the `EpubReader` refactor; note `ReaderFileCacheTest`, `ReaderLoadResultTest`, etc. must remain green).
- [ ] `./gradlew :androidApp:compileDebugKotlin :androidApp:lintDebug` — compiles and lints clean.
- [ ] `make verify-local-paths` (if present at repo root) — no absolute/worktree paths leaked into the new docs/assets.
- [ ] Confirm `git status` shows: new assets under `androidApp/src/main/assets/reader/epubjs/`, new Kotlin files, edited `EpubReader.kt`/`ReaderScreen.kt`/`ReaderViewModel.kt`/`build.gradle.kts`/`libs.versions.toml`, and the architecture doc — and that the throwaway spike harness from Task 0 is gone.

---

## Self-review vs spec

- **§4 "shared `PaginatedWebReader`":** Built (Task 5). Named exactly `PaginatedWebReader`. Text/FB2 multicolumn harness is explicitly out of Phase 2 scope (spec §8 phase 2 is "Paginated EPUB" only; FB2 pagination parity is phase 7). The reader is structured so the same WebView host can later load a text/FB2 HTML harness — noted, not built. ✅
- **§4 "bundled as a web asset":** epub.js + JSZip vendored under `androidApp/src/main/assets/reader/epubjs/`, versions pinned + hashed (Task 0). ✅
- **§4 "real page turns, page counts, CFI locators, JS↔Kotlin bridge":** page turns via tap zones + native swipe; counts via `book.locations.generate` → `Ready.totalLocations`; CFIs via `relocated`; bridge is `EpubBridge`/`EpubCommandSender` with typed payloads. ✅
- **§4 "theme/size/margins still applied (reuse existing settings)":** `toEpubThemeJson` reuses `ReaderDisplaySettings` + the exact color values from the old `withReaderCss`; applied via `rendition.themes`. ✅
- **§4 / §3 "Map epub.js CFI ↔ ReaderLocator.Cfi; restore to saved CFI on open":** Task 3 (mapping, TDD) + Task 5 (`displayCfiFor(initialLocator)` feeds `rendition.display`) + Task 6 (VM persists CFI typed-locator, suppresses the echo relocation). ✅
- **§4 "search/highlight are LATER phases — Phase 2 only establishes bridge hooks":** No search/highlight built. The reserved `tapCenter` event + the `EpubCommandSender`/`EpubBridge` channel are the hooks later phases extend. ✅
- **§9 risk "spike early; Readium fallback":** Task 0 is first, with explicit large-book budget and a recorded GO/NO-GO that names Readium-Kotlin and states which tasks carry over. ✅
- **§10 testing:** CFI round-trips + bridge parsing are unit-tested in `shared` (`testDebugUnitTest`); `page:N` backward-compat asserted in `EpubCfiMappingTest`; device behaviors (page turns, count, reopen-restore, theme-live) in Task 8. ✅
- **Maintainability guideline:** progress-persistence is extracted to one `persistProgress` helper shared by `onPageChanged` and `onLocationChanged` (Task 6) — no duplicated save logic. ✅
- **Backward-compat correction:** an EPUB row that is still legacy `page:N` (pre-Phase-2) can't restore to a CFI; `displayCfiFor` returns null and the reader opens at the start — asserted in Task 3 and acceptable (first relocation re-persists a CFI). Flagged so it isn't mistaken for a regression.
- **Deferred-but-flagged:** EPUB Sections (TOC→CFI) and EPUB bookmark-jump are explicitly deferred in Task 6 with a no-silent-dead-tap rule, since the old page-index jump path doesn't map to CFI. This is a deliberate Phase-2 scope cut, recorded in the architecture doc.
