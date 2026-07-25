# Reflowable Reader Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the three bones-level text renderers (`EpubReader`, `FictionBookReader`, `TextReader`) with one CSS-multicolumn WebView **reflowable engine** that gives EPUB/FB2/TXT/Markdown true fixed pages, pinch-to-zoom font scaling with reflow, themes, and locator-based resume.

**Architecture:** A `ReflowableSource` turns each format into ordered HTML sections; one `WebView` renders a section at a time, paginated by a bundled `paginator.js` (viewport-wide CSS columns) that talks to Kotlin over a typed `@JavascriptInterface` JSON bridge. A `ReflowableReader` composable orchestrates source + WebView + gestures + locators, plugging into the existing `ReaderScreen`/`ReaderViewModel` shell. Reading position is a `ReflowLocator` JSON stored in the existing `progressLocation` string — no server change.

**Tech Stack:** Kotlin, Jetpack Compose (`AndroidView`, gestures), Android `WebView` + `@JavascriptInterface`, `kotlinx.serialization` (bridge + locator JSON), Robolectric (existing androidApp unit-test runner), JUnit/kotlin.test. No new third-party reader library.

Commands assume the repository root (`prairie-android`) is the cwd. Design reference: `docs/superpowers/specs/2026-06-13-reflowable-reader-engine-design.md`.

---

## Current-state facts (verified)

- `BookFormat` (`shared/.../model/book/BookFormat.kt`): `Epub, Pdf, Cbz, Cbr, Mobi, Azw, Azw3, Txt, Markdown, Fb2, Fbz, Unknown`. This engine serves `Epub, Fb2, Fbz, Txt, Markdown`.
- `EpubBook` is an `internal class` **inside** `androidApp/.../ui/screens/reader/EpubReader.kt`: `companion object { fun open(epub: File, cacheRoot: File): EpubBook }`, `val unpackedRoot: File`, `val spine: List<String>` (chapter hrefs), `fun readChapterHtml(href: String): String?`. Task 1 extracts it so it survives `EpubReader`'s deletion.
- `resolveReaderFile(context, okHttp, url, serverUrl, extension): File` — `internal suspend`, in `androidApp/.../ui/screens/reader/ReaderFileCache.kt`. Returns a local `File` for a `file://`/`content://`/`http(s)` reader URL.
- `ReaderSection(index: Int, title: String, location: String)` and `ReaderDisplaySettings(theme: ReaderTheme = System, textScale: Float = 1f, marginScale: Float = 1f)` with `normalized()` clamping `textScale` to `0.8..1.6` and `marginScale` to `0.75..1.5` — both in `android-shared/.../common/ebook/ReaderControls.kt`. `ReaderTheme = { System, Light, Dark, Sepia }`. **There is no `fontFamily` field** (fonts are a later sub-project).
- `ReaderScreen` dispatches by `BookFormat`; the `Epub`/`Fb2`/`Fbz`/`Txt`/`Markdown` branches call the old renderers with `initialPage: Int`, `settings`, `onPageChanged`, `onPageCountKnown`, `onSectionsKnown`. `ReaderViewModel` already does local→server progress reconciliation in `loadReaderState()`, persists via `saveProgress(location, progressPercent)` (local store + server) and is carried offline→online by the shipped `EbookProgressSyncer` (location-agnostic).

## File Structure

New package: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/reflow/`

- `EpubBook.kt` — **moved** out of `EpubReader.kt`. The EPUB unpack/spine/chapter-HTML parser. *(Task 1)*
- `reflow/ReflowLocator.kt` — `@Serializable ReflowLocator` + `ReflowLocatorCodec`. *(Task 2)*
- `reflow/SectionWeights.kt` — pure `bookProgression` math from per-section char weights. *(Task 3)*
- `reflow/ReflowStyle.kt` — `ReflowStyle` + `toCss()` + `ReaderDisplaySettings.toReflowStyle(systemDark)`. *(Task 4)*
- `reflow/ReflowBridge.kt` — `@Serializable` event/command payloads + `decodeReflowEvent` / `encodeReflowCommand`. *(Task 5)*
- `reflow/ReflowableSource.kt` — `ReflowableSource` interface + `ReflowSection`/`ReflowTocEntry` + `buildReflowableSource(format, file, cacheDir)`. *(Task 6)*
- `reflow/EpubReflowSource.kt`, `Fb2ReflowSource.kt`, `MarkdownReflowSource.kt`, `PlainTextReflowSource.kt` — per-format adapters. *(Task 6)*
- `androidApp/src/main/assets/reader/reflow/reader.html` + `paginator.js` — bundled WebView harness. *(Task 7)*
- `reflow/ReflowWebView.kt` — Compose `AndroidView` wrapper hosting the WebView + bridge. *(Task 8)*
- `reflow/ReflowableReader.kt` — orchestrator composable (the `ReaderScreen` entry point). *(Tasks 9–11)*
- Tests under `androidApp/src/androidUnitTest/.../reader/reflow/`. *(Tasks 2–6)*

Modified: `ReaderControls.kt` (widen `textScale` clamp), `ReaderViewModel.kt` (locator plumbing + `nudgeTextScale`), `ReaderScreen.kt` (route reflowable formats to `ReflowableReader`, progress label). Deleted at the end: `EpubReader.kt`, `FictionBookReader.kt`, `TextReader.kt` (after verification).

---

### Task 1 — Extract `EpubBook` to its own file

**Files:**
- Create: `androidApp/.../ui/screens/reader/EpubBook.kt`
- Modify: `androidApp/.../ui/screens/reader/EpubReader.kt` (remove the moved class)

- [ ] **Step 1: Move the class.** Cut the `internal class EpubBook private constructor(...) { ... companion object { fun open(...) ... } }` block (and any private regexes/helpers it owns, e.g. `SPINE_ITEMREF_REGEX`) out of `EpubReader.kt` into a new `EpubBook.kt` in the same package (`com.continuum.app.android.ui.screens.reader`). Keep it `internal`. Add the imports it needs (`java.io.File`, zip, etc.).
- [ ] **Step 2: Build.** Run `./gradlew :androidApp:compileDebugKotlinAndroid` — expect PASS (`EpubReader` still references `EpubBook` from the same package).
- [ ] **Step 3: Commit:** `refactor(reader): extract EpubBook parser to its own file`

---

### Task 2 — `ReflowLocator` + codec (TDD)

**Files:**
- Create: `androidApp/.../ui/screens/reader/reflow/ReflowLocator.kt`
- Test: `androidApp/src/androidUnitTest/.../reader/reflow/ReflowLocatorCodecTest.kt`

- [ ] **Step 1: Write the failing test.**
```kotlin
package com.continuum.app.android.ui.screens.reader.reflow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReflowLocatorCodecTest {
    @Test fun `round-trips through json`() {
        val l = ReflowLocator(sectionIndex = 3, pageProgression = 0.4, bookProgression = 0.27)
        val decoded = ReflowLocatorCodec.decode(ReflowLocatorCodec.encode(l))
        assertEquals(l, decoded)
    }
    @Test fun `legacy page form decodes to null`() {
        assertNull(ReflowLocatorCodec.decode("page:7"))
    }
    @Test fun `blank and garbage decode to null`() {
        assertNull(ReflowLocatorCodec.decode(null))
        assertNull(ReflowLocatorCodec.decode(""))
        assertNull(ReflowLocatorCodec.decode("{not json"))
    }
}
```
- [ ] **Step 2: Run (expect FAIL — unresolved reference):**
  `./gradlew :androidApp:testDebugUnitTest --tests "*ReflowLocatorCodecTest"`
- [ ] **Step 3: Implement.**
```kotlin
package com.continuum.app.android.ui.screens.reader.reflow

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ReflowLocator(
    val sectionIndex: Int,
    val pageProgression: Double, // 0..1 within the section
    val bookProgression: Double, // 0..1 across the book
)

object ReflowLocatorCodec {
    private val json = Json { ignoreUnknownKeys = true }
    fun encode(locator: ReflowLocator): String = json.encodeToString(ReflowLocator.serializer(), locator)
    fun decode(location: String?): ReflowLocator? {
        if (location.isNullOrBlank() || !location.trimStart().startsWith("{")) return null
        return runCatching { json.decodeFromString(ReflowLocator.serializer(), location) }.getOrNull()
    }
}
```
- [ ] **Step 4: Run (expect PASS).**
- [ ] **Step 5: Commit:** `feat(reader): ReflowLocator + json codec`

---

### Task 3 — `bookProgression` math (TDD)

**Files:**
- Create: `androidApp/.../ui/screens/reader/reflow/SectionWeights.kt`
- Test: `androidApp/src/androidUnitTest/.../reader/reflow/SectionWeightsTest.kt`

- [ ] **Step 1: Write the failing test.**
```kotlin
package com.continuum.app.android.ui.screens.reader.reflow

import kotlin.test.Test
import kotlin.test.assertEquals

class SectionWeightsTest {
    private val w = SectionWeights(listOf(100, 300)) // total 400

    @Test fun `start of first section is zero`() =
        assertEquals(0.0, w.bookProgression(0, 0.0), 1e-9)
    @Test fun `mid first section weights by chars`() =
        assertEquals(0.125, w.bookProgression(0, 0.5), 1e-9) // 0 + 0.25*0.5
    @Test fun `start of second section is first section weight`() =
        assertEquals(0.25, w.bookProgression(1, 0.0), 1e-9)
    @Test fun `end of last section is one`() =
        assertEquals(1.0, w.bookProgression(1, 1.0), 1e-9)
    @Test fun `single empty section degrades to page progression`() =
        assertEquals(0.5, SectionWeights(listOf(0)).bookProgression(0, 0.5), 1e-9)
}
```
- [ ] **Step 2: Run (expect FAIL).** `./gradlew :androidApp:testDebugUnitTest --tests "*SectionWeightsTest"`
- [ ] **Step 3: Implement.**
```kotlin
package com.continuum.app.android.ui.screens.reader.reflow

/** Book-level progress estimate from per-section text lengths, so we never
 *  pre-render unseen sections. */
class SectionWeights(private val approxChars: List<Int>) {
    private val total = approxChars.sum().coerceAtLeast(1)
    private val cumulativeBefore = IntArray(approxChars.size).also {
        var acc = 0
        for (i in approxChars.indices) { it[i] = acc; acc += approxChars[i] }
    }
    fun bookProgression(sectionIndex: Int, pageProgression: Double): Double {
        if (approxChars.isEmpty()) return pageProgression.coerceIn(0.0, 1.0)
        val i = sectionIndex.coerceIn(0, approxChars.lastIndex)
        val before = cumulativeBefore[i].toDouble() / total
        val weight = approxChars[i].toDouble() / total
        // Fall back to raw page progression for zero-length sections.
        val span = if (weight <= 0.0 && approxChars.size == 1) 1.0 else weight
        return (before + span * pageProgression.coerceIn(0.0, 1.0)).coerceIn(0.0, 1.0)
    }
}
```
- [ ] **Step 4: Run (expect PASS).**
- [ ] **Step 5: Commit:** `feat(reader): section-weighted book progression`

---

### Task 4 — `ReflowStyle` → CSS (TDD)

**Files:**
- Create: `androidApp/.../ui/screens/reader/reflow/ReflowStyle.kt`
- Test: `androidApp/src/androidUnitTest/.../reader/reflow/ReflowStyleTest.kt`

- [ ] **Step 1: Write the failing test.**
```kotlin
package com.continuum.app.android.ui.screens.reader.reflow

import com.continuum.app.common.ebook.ReaderDisplaySettings
import com.continuum.app.common.ebook.ReaderTheme
import kotlin.test.Test
import kotlin.test.assertTrue

class ReflowStyleTest {
    @Test fun `system theme resolves to dark when device is dark`() {
        val css = ReaderDisplaySettings(theme = ReaderTheme.System)
            .toReflowStyle(systemDark = true).toCss()
        assertTrue(css.contains("#1c1b1f")) // dark background token
    }
    @Test fun `system theme resolves to light when device is light`() {
        val css = ReaderDisplaySettings(theme = ReaderTheme.System)
            .toReflowStyle(systemDark = false).toCss()
        assertTrue(css.contains("#fffbfe"))
    }
    @Test fun `explicit sepia ignores system dark`() {
        val css = ReaderDisplaySettings(theme = ReaderTheme.Sepia)
            .toReflowStyle(systemDark = true).toCss()
        assertTrue(css.contains("#f4ecd8"))
    }
    @Test fun `text scale becomes font-size percent`() {
        val css = ReaderDisplaySettings(textScale = 1.5f).toReflowStyle(false).toCss()
        assertTrue(css.contains("font-size: 150%"))
    }
}
```
- [ ] **Step 2: Run (expect FAIL).** `./gradlew :androidApp:testDebugUnitTest --tests "*ReflowStyleTest"`
- [ ] **Step 3: Implement.**
```kotlin
package com.continuum.app.android.ui.screens.reader.reflow

import com.continuum.app.common.ebook.ReaderDisplaySettings
import com.continuum.app.common.ebook.ReaderTheme

enum class ReflowTheme(val color: String, val background: String) {
    Light("#1c1b1f", "#fffbfe"),
    Sepia("#2b2118", "#f4ecd8"),
    Dark("#e6e1e5", "#1c1b1f"),
}

data class ReflowStyle(
    val theme: ReflowTheme,
    val fontScalePercent: Int,
    val marginEm: Double,
    val lineHeight: Double = 1.55,
) {
    fun toCss(): String =
        "color:${theme.color};background:${theme.background};" +
            "font-size: $fontScalePercent%;margin:${marginEm}em;line-height:$lineHeight;"
}

fun ReaderDisplaySettings.toReflowStyle(systemDark: Boolean): ReflowStyle {
    val n = normalized()
    val theme = when (n.theme) {
        ReaderTheme.System -> if (systemDark) ReflowTheme.Dark else ReflowTheme.Light
        ReaderTheme.Light -> ReflowTheme.Light
        ReaderTheme.Dark -> ReflowTheme.Dark
        ReaderTheme.Sepia -> ReflowTheme.Sepia
    }
    return ReflowStyle(
        theme = theme,
        fontScalePercent = (n.textScale * 100).toInt(),
        marginEm = (n.marginScale * 1.2),
    )
}
```
- [ ] **Step 4: Run (expect PASS).**
- [ ] **Step 5: Commit:** `feat(reader): ReflowStyle to CSS mapping`

---

### Task 5 — Bridge payloads (TDD)

**Files:**
- Create: `androidApp/.../ui/screens/reader/reflow/ReflowBridge.kt`
- Test: `androidApp/src/androidUnitTest/.../reader/reflow/ReflowBridgeTest.kt`

- [ ] **Step 1: Write the failing test.**
```kotlin
package com.continuum.app.android.ui.screens.reader.reflow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReflowBridgeTest {
    @Test fun `decodes paginated`() {
        val e = decodeReflowEvent("""{"type":"paginated","pageCount":12}""")
        assertEquals(ReflowEvent.Paginated(12), e)
    }
    @Test fun `decodes relocated`() {
        val e = decodeReflowEvent("""{"type":"relocated","page":3,"pageProgression":0.25}""")
        assertEquals(ReflowEvent.Relocated(3, 0.25), e)
    }
    @Test fun `unknown type decodes to null`() {
        assertEquals(null, decodeReflowEvent("""{"type":"nope"}"""))
        assertEquals(null, decodeReflowEvent("{garbage"))
    }
    @Test fun `encodes goToPage command`() {
        assertTrue(encodeReflowCommand(ReflowCommand.GoToPage(4)).contains("\"page\":4"))
    }
}
```
- [ ] **Step 2: Run (expect FAIL).** `./gradlew :androidApp:testDebugUnitTest --tests "*ReflowBridgeTest"`
- [ ] **Step 3: Implement.**
```kotlin
package com.continuum.app.android.ui.screens.reader.reflow

import kotlinx.serialization.json.Json
import org.json.JSONObject

sealed interface ReflowEvent {
    object Ready : ReflowEvent
    data class Paginated(val pageCount: Int) : ReflowEvent
    data class Relocated(val page: Int, val pageProgression: Double) : ReflowEvent
    data class Error(val message: String) : ReflowEvent
}

sealed interface ReflowCommand {
    data class Load(val html: String, val baseUrl: String) : ReflowCommand
    data class GoToPage(val page: Int) : ReflowCommand
    data class ApplyStyle(val css: String) : ReflowCommand
}

fun decodeReflowEvent(message: String): ReflowEvent? = runCatching {
    val o = JSONObject(message)
    when (o.getString("type")) {
        "ready" -> ReflowEvent.Ready
        "paginated" -> ReflowEvent.Paginated(o.getInt("pageCount"))
        "relocated" -> ReflowEvent.Relocated(o.getInt("page"), o.getDouble("pageProgression"))
        "error" -> ReflowEvent.Error(o.optString("message"))
        else -> null
    }
}.getOrNull()

fun encodeReflowCommand(cmd: ReflowCommand): String = when (cmd) {
    is ReflowCommand.Load -> JSONObject()
        .put("type", "load").put("html", cmd.html).put("baseUrl", cmd.baseUrl).toString()
    is ReflowCommand.GoToPage -> JSONObject().put("type", "goToPage").put("page", cmd.page).toString()
    is ReflowCommand.ApplyStyle -> JSONObject().put("type", "applyStyle").put("css", cmd.css).toString()
}
```
- [ ] **Step 4: Run (expect PASS).**
- [ ] **Step 5: Commit:** `feat(reader): typed reflow bridge payloads`

---

### Task 6 — `ReflowableSource` + format adapters (TDD on HTML shaping)

**Files:**
- Create: `reflow/ReflowableSource.kt`, `reflow/EpubReflowSource.kt`, `reflow/Fb2ReflowSource.kt`, `reflow/MarkdownReflowSource.kt`, `reflow/PlainTextReflowSource.kt`
- Test: `androidApp/src/androidUnitTest/.../reader/reflow/ReflowSourceTest.kt`

The interface + the three non-EPUB adapters are pure (no Android), so TDD them on string fixtures. EPUB wraps `EpubBook` (filesystem) — covered by the device pass.

- [ ] **Step 1: Define the interface (no test yet).**
```kotlin
package com.continuum.app.android.ui.screens.reader.reflow

data class ReflowSection(val index: Int, val title: String?, val approxChars: Int)
data class ReflowTocEntry(val title: String, val sectionIndex: Int)

interface ReflowableSource {
    val sections: List<ReflowSection>
    val tableOfContents: List<ReflowTocEntry>
    suspend fun html(index: Int): String?   // null = unreadable
    fun baseUrl(index: Int): String         // "" when no relative resources
}
```
- [ ] **Step 2: Write the failing test for the text adapters.**
```kotlin
package com.continuum.app.android.ui.screens.reader.reflow

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReflowSourceTest {
    @Test fun `plain text becomes one section of paragraphs`() = runTest {
        val s = PlainTextReflowSource("Hello world\n\nSecond para")
        assertEquals(1, s.sections.size)
        val html = s.html(0)!!
        assertTrue(html.contains("<p>Hello world</p>"))
        assertTrue(html.contains("<p>Second para</p>"))
    }
    @Test fun `markdown splits on top-level headings`() = runTest {
        val s = MarkdownReflowSource("# One\n\ntext\n\n# Two\n\nmore")
        assertEquals(2, s.sections.size)
        assertEquals("One", s.sections[0].title)
        assertTrue(s.html(0)!!.contains("<h1>One</h1>"))
        assertTrue(s.html(0)!!.contains("<p>text</p>"))
    }
    @Test fun `fb2 sections map to html with titles`() = runTest {
        val fb2 = """
            <FictionBook><body>
              <section><title><p>Chapter 1</p></title><p>Alpha</p></section>
              <section><title><p>Chapter 2</p></title><p>Beta</p></section>
            </body></FictionBook>
        """.trimIndent()
        val s = Fb2ReflowSource.fromXml(fb2)
        assertEquals(2, s.sections.size)
        assertEquals("Chapter 1", s.sections[0].title)
        assertTrue(s.html(0)!!.contains("Alpha"))
    }
    @Test fun `approxChars reflects text length`() = runTest {
        val s = PlainTextReflowSource("abcdef")
        assertTrue(s.sections[0].approxChars >= 6)
    }
}
```
- [ ] **Step 3: Run (expect FAIL).** `./gradlew :androidApp:testDebugUnitTest --tests "*ReflowSourceTest"`
- [ ] **Step 4: Implement the text adapters** (pure Kotlin). Each builds `sections` eagerly and returns escaped HTML from `html(index)`.

  `PlainTextReflowSource(text)`: one section; `html` wraps each blank-line-delimited block in `<p>` with HTML-escaping. `approxChars = text.length`.

  `MarkdownReflowSource(md)`: split on lines matching `^# ` into sections (text before the first heading is section 0 titled null); render each with a minimal converter — headings `# .. ######` → `<h1..6>`, blank-line paragraphs → `<p>`, leaving inline text as-is (escaped). `title` = the heading text. `approxChars` = section source length.

  `Fb2ReflowSource(sectionsHtml, titles)` with `companion fun fromXml(xml: String): Fb2ReflowSource`: parse `<section>` blocks under `<body>`, take the first `<title>` text, convert `<p>` to `<p>`, `<empty-line/>` to `<br>`, `<emphasis>`→`<em>`, `<strong>`→`<strong>`; ignore unknown tags (strip). `<binary>` images map to `data:` URIs keyed by `id` (resolve `<image l:href="#id"/>`). `approxChars` = stripped text length.

  Provide a tiny shared `htmlEscape(String)` helper in `ReflowableSource.kt`.
- [ ] **Step 5: Run (expect PASS).**
- [ ] **Step 6: Implement `EpubReflowSource`** (filesystem; no unit test):
```kotlin
class EpubReflowSource(private val book: EpubBook) : ReflowableSource {
    override val sections = book.spine.mapIndexed { i, href ->
        ReflowSection(i, href.substringAfterLast('/').substringBeforeLast('.'), approxChars = 4000)
    }
    override val tableOfContents = sections.map { ReflowTocEntry(it.title ?: "", it.index) }
    override suspend fun html(index: Int): String? =
        book.spine.getOrNull(index)?.let { book.readChapterHtml(it) }
    override fun baseUrl(index: Int): String = "file://${book.unpackedRoot.absolutePath}/"
}
```
  > EPUB `approxChars` uses a flat estimate (4000) since reading every chapter just to weight progress would negate the lazy design; this keeps `bookProgression` ~= `sectionIndex/sectionCount`, which is acceptable for EPUB. (A later refinement can read sizes from the spine file lengths.)
- [ ] **Step 7: Implement the factory** `buildReflowableSource`:
```kotlin
suspend fun buildReflowableSource(format: BookFormat, file: File): ReflowableSource =
    withContext(Dispatchers.IO) {
        when (format) {
            BookFormat.Epub -> EpubReflowSource(EpubBook.open(file, file.parentFile ?: file))
            BookFormat.Fb2, BookFormat.Fbz -> Fb2ReflowSource.fromXml(readFb2Xml(file)) // unzip if Fbz
            BookFormat.Markdown -> MarkdownReflowSource(file.readText())
            BookFormat.Txt -> PlainTextReflowSource(file.readText())
            else -> error("unsupported reflow format $format")
        }
    }
```
- [ ] **Step 8: Run the suite + build.** `./gradlew :androidApp:testDebugUnitTest --tests "*ReflowSourceTest" :androidApp:compileDebugKotlinAndroid`
- [ ] **Step 9: Commit:** `feat(reader): reflowable source + format adapters`

---

### Task 7 — Bundled WebView paginator (assets)

**Files:**
- Create: `androidApp/src/main/assets/reader/reflow/reader.html`
- Create: `androidApp/src/main/assets/reader/reflow/paginator.js`

*(No unit test — exercised by the device pass in Task 14. Provide the full assets.)*

- [ ] **Step 1: `reader.html`:**
```html
<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<style id="reflow-style"></style>
<style>
  html,body{margin:0;height:100%;}
  #reflow-root{height:100vh;column-width:100vw;column-gap:0;overflow:hidden;
    -webkit-column-fill:auto;will-change:transform;}
  #reflow-root img,#reflow-root svg,#reflow-root table{max-width:100%;height:auto;break-inside:avoid;}
</style></head>
<body><div id="reflow-root"></div><script src="paginator.js"></script></body></html>
```
- [ ] **Step 2: `paginator.js`:**
```js
(function () {
  var root = document.getElementById('reflow-root');
  var styleEl = document.getElementById('reflow-style');
  var page = 0, pageCount = 1;
  function send(o){ try{ AndroidReflow.onEvent(JSON.stringify(o)); }catch(e){} }
  function viewW(){ return window.innerWidth || document.documentElement.clientWidth; }
  function measure(){
    pageCount = Math.max(1, Math.round(root.scrollWidth / viewW()));
    send({type:'paginated', pageCount: pageCount});
  }
  function apply(){ root.style.transform = 'translateX(' + (-page * viewW()) + 'px)'; }
  function relocate(){
    send({type:'relocated', page: page, pageProgression: pageCount>1 ? page/(pageCount-1) : 0});
  }
  window.ReflowApi = {
    load: function(html, baseUrl){
      // baseUrl is applied via <base> for relative resources.
      var b = document.querySelector('base'); if(!b){ b=document.createElement('base'); document.head.appendChild(b);} 
      if(baseUrl) b.href = baseUrl;
      root.innerHTML = html; page = 0; apply();
      // wait for layout/images-ish, then measure
      requestAnimationFrame(function(){ requestAnimationFrame(function(){ measure(); apply(); relocate(); }); });
    },
    goToPage: function(n){ page = Math.min(Math.max(0, n), pageCount-1); apply(); relocate(); },
    applyStyle: function(css){ styleEl.textContent = '#reflow-root{'+css+'}';
      requestAnimationFrame(function(){ var frac = pageCount>1?page/(pageCount-1):0; measure();
        page = Math.round(frac*(pageCount-1)); apply(); relocate(); }); }
  };
  window.addEventListener('resize', function(){ var frac = pageCount>1?page/(pageCount-1):0;
    measure(); page = Math.round(frac*(pageCount-1)); apply(); relocate(); });
  send({type:'ready'});
})();
```
- [ ] **Step 3: Commit:** `feat(reader): bundled CSS-column paginator harness`

---

### Task 8 — `ReflowWebView` (Compose host + bridge)

**Files:** Create `reflow/ReflowWebView.kt`

*(No unit test — Compose/WebView glue; device pass covers it.)*

- [ ] **Step 1: Implement** a composable that creates a `WebView`, installs the `@JavascriptInterface` bridge that forwards `decodeReflowEvent(msg)` to an `onEvent` callback, loads `file:///android_asset/reader/reflow/reader.html`, and exposes a `ReflowController` (remembered) with `load/goToPage/applyStyle` that call `webView.evaluateJavascript("window.ReflowApi.<fn>(...)", null)`. Configure `settings.javaScriptEnabled = true`, `allowFileAccess = true`, `allowContentAccess = true`, transparent background, and `webViewClient` that triggers an initial `applyStyle`/`load` once `onPageStarted`→`ready` arrives. Handle `onRenderProcessGone` by signalling an `onCrash` callback (the orchestrator recreates + reloads at the saved locator).
  ```kotlin
  class ReflowController(private val web: WebView) {
      fun load(html: String, baseUrl: String) =
          web.evaluateJavascript("window.ReflowApi.load(${jsString(html)},${jsString(baseUrl)})", null)
      fun goToPage(n: Int) = web.evaluateJavascript("window.ReflowApi.goToPage($n)", null)
      fun applyStyle(css: String) = web.evaluateJavascript("window.ReflowApi.applyStyle(${jsString(css)})", null)
  }
  // jsString() JSON-encodes the argument so HTML/CSS with quotes/newlines is safe.
  ```
- [ ] **Step 2: Build.** `./gradlew :androidApp:compileDebugKotlinAndroid` (PASS).
- [ ] **Step 3: Commit:** `feat(reader): ReflowWebView host + JS bridge`

---

### Task 9 — `ReflowableReader` orchestrator: load, restore, page

**Files:** Create `reflow/ReflowableReader.kt`

- [ ] **Step 1: Implement** the composable with this contract + control flow:
```kotlin
@Composable
fun ReflowableReader(
    format: BookFormat,
    fileUrl: String,
    settings: ReaderDisplaySettings,
    initialLocator: String?,
    onLocatorChanged: (locationJson: String, progress: Double) -> Unit,
    onSectionsKnown: (List<ReaderSection>) -> Unit,
    onTextScaleNudge: (Float) -> Unit,
)
```
  Logic:
  1. `produceState` resolves the file (`resolveReaderFile(..., extension = format.wire)`) then `buildReflowableSource(format, file)`. While null → `CircularProgressIndicator`; on failure → error text.
  2. On source ready: `onSectionsKnown(source.tableOfContents.map { ReaderSection(it.sectionIndex, it.title, ReflowLocatorCodec.encode(ReflowLocator(it.sectionIndex,0.0, weights.bookProgression(it.sectionIndex,0.0)))) })`; build `SectionWeights(source.sections.map { it.approxChars })`.
  3. State: `var sectionIndex by remember { mutableStateOf(decode(initialLocator)?.sectionIndex ?: 0) }`, `var pendingPageProgression by remember { mutableStateOf(decode(initialLocator)?.pageProgression ?: 0.0) }`, `var pageCount by remember { mutableStateOf(1) }`, `var page by remember { mutableStateOf(0) }`.
  4. `ReflowWebView(onEvent = { ev -> when (ev) { Ready -> reloadSection(); Paginated -> { pageCount = ev.pageCount; controller.goToPage((pendingPageProgression*(pageCount-1)).roundToInt()); pendingPageProgression = 0.0 }; Relocated -> { page = ev.page; onLocatorChanged(encode(ReflowLocator(sectionIndex, ev.pageProgression, weights.bookProgression(sectionIndex, ev.pageProgression))), weights.bookProgression(sectionIndex, ev.pageProgression)) }; Error -> {} } })`.
  5. `reloadSection()` (suspend, in a `LaunchedEffect(sectionIndex)`): `controller.load(source.html(sectionIndex) ?: unreadableHtml(), source.baseUrl(sectionIndex))`, then `controller.applyStyle(settings.toReflowStyle(isSystemInDarkTheme()).toCss())`.
  6. `LaunchedEffect(settings)`: `controller.applyStyle(settings.toReflowStyle(isSystemInDarkTheme()).toCss())`.
- [ ] **Step 2: Build.** `./gradlew :androidApp:compileDebugKotlinAndroid` (PASS).
- [ ] **Step 3: Commit:** `feat(reader): ReflowableReader orchestrator (load + restore + relocate)`

---

### Task 10 — Page turns + section boundaries + pinch-zoom

**Files:** Modify `reflow/ReflowableReader.kt`; modify `android-shared/.../common/ebook/ReaderControls.kt`; modify `ReaderViewModel.kt`

- [ ] **Step 1: Widen the text-scale clamp** for pinch in `ReaderControls.kt` `normalized()`: `textScale.coerceIn(0.6f, 3.0f)` (was `0.8..1.6`). Leave `marginScale` as-is.
- [ ] **Step 2: Paging.** Add `fun nextPage()`/`prevPage()` to the orchestrator:
  - `nextPage`: if `page < pageCount-1` → `controller.goToPage(page+1)`; else if `sectionIndex < source.sections.lastIndex` → `sectionIndex++` (LaunchedEffect reloads at page 0).
  - `prevPage`: if `page > 0` → `controller.goToPage(page-1)`; else if `sectionIndex > 0` → set a `pendingPageProgression = 1.0` and `sectionIndex--` (so it opens on the previous section's last page).
- [ ] **Step 3: Gestures** over the WebView Box:
  ```kotlin
  Modifier
    .pointerInput(Unit) { detectTapGestures { o -> when { o.x < size.width/3f -> prevPage(); o.x > size.width*2/3f -> nextPage(); else -> onToggleChrome() } } }
    .pointerInput(Unit) { detectHorizontalDragGestures(onDragEnd = { /* snap */ }) { _, dx -> /* accumulate; on threshold prev/next */ } }
    .pointerInput(Unit) { detectTransformGestures { _, _, zoom, _ -> if (zoom != 1f) onTextScaleNudge(zoom) } }
  ```
- [ ] **Step 4: VM** `nudgeTextScale` in `ReaderViewModel`:
  ```kotlin
  fun nudgeTextScale(zoom: Float) {
      val cur = _uiState.value.displaySettings
      setDisplaySettings(cur.copy(textScale = (cur.textScale * zoom).coerceIn(0.6f, 3.0f)))
  }
  ```
  (the `settings` LaunchedEffect → `applyStyle` re-paginates and the paginator re-anchors by progression).
- [ ] **Step 5: Build + commit:** `feat(reader): page turns, section boundaries, pinch-zoom reflow`

---

### Task 11 — Wire `ReaderScreen` + VM locator plumbing + progress label

**Files:** Modify `ReaderScreen.kt`, `ReaderViewModel.kt`

- [ ] **Step 1: VM.** Add `initialLocator: String?` to the reader ui-state (populated from the reconciled progress `location` in `loadReaderState()`), and:
  ```kotlin
  fun onLocatorChanged(locationJson: String, progress: Double) {
      _uiState.update { it.copy(progressPercent = progress) }
      saveProgress(location = locationJson, progressPercent = progress) // existing local+server path
  }
  ```
  Keep `onPageChanged`/`onPageCountKnown` for PDF/CBZ.
- [ ] **Step 2: ReaderScreen.** Replace the `Epub`, `Fb2`, `Fbz`, `Txt`, `Markdown` branches with a single:
  ```kotlin
  BookFormat.Epub, BookFormat.Fb2, BookFormat.Fbz, BookFormat.Txt, BookFormat.Markdown ->
      ReflowableReader(
          format = state.format,
          fileUrl = state.fileUrl!!,
          settings = state.displaySettings,
          initialLocator = state.initialLocator,
          onLocatorChanged = viewModel::onLocatorChanged,
          onSectionsKnown = viewModel::setSections,
          onTextScaleNudge = viewModel::nudgeTextScale,
      )
  ```
  Leave `Pdf`/`Cbz` branches unchanged.
- [ ] **Step 3: Progress label.** For reflowable formats show `"${(state.progressPercent*100).toInt()}%"` (drop the page-index form, which no longer applies); keep the page form for PDF/CBZ.
- [ ] **Step 4: Build.** `./gradlew :androidApp:compileDebugKotlinAndroid` (PASS).
- [ ] **Step 5: Commit:** `feat(reader): route reflowable formats through the engine`

---

### Task 12 — TOC + locator-based bookmarks

**Files:** Modify `ReaderScreen.kt` (SectionsSheet jump), `ReaderViewModel.kt`

- [ ] **Step 1: TOC jump.** The `SectionsSheet` already lists `ReaderSection`s and calls a jump with the section's `location` (now a `ReflowLocator` JSON). Route that to the orchestrator: add an `onJumpToLocator: (String) -> Unit` from `ReaderScreen` to `ReflowableReader` that decodes and sets `sectionIndex` + `pendingPageProgression`. (Pass via a remembered callback / a `MutableState<String?>` jump request hoisted in `ReaderScreen`.)
- [ ] **Step 2: Bookmarks.** Bookmark add stores the current `ReflowLocator` JSON (the orchestrator exposes the current locator via a hoisted state); bookmark jump reuses `onJumpToLocator`. The existing `EbookLocalStateStore.BookmarkSnapshot.location` already holds an opaque string — no schema change.
- [ ] **Step 3: Build + commit:** `feat(reader): TOC + locator bookmarks for the reflow engine`

---

### Task 13 — Full verification + remove the old renderers

**Files:** delete `EpubReader.kt`, `FictionBookReader.kt`, `TextReader.kt` (keep `EpubBook.kt`, `PdfReader.kt`, `ComicReader.kt`).

- [ ] **Step 1: Gate:** `git diff --check && ./gradlew :androidApp:testDebugUnitTest :androidApp:compileDebugKotlinAndroid`
- [ ] **Step 2: adb device pass (Pixel)** — one book of each format (EPUB, FB2, TXT, MD), with screenshots:
  - Real **page turns** (tap right/left, swipe) advance one page; **no vertical scroll**.
  - **Pinch out/in** changes font and **re-paginates**, position preserved within a page.
  - **Theme** follows the app (dark default); Sepia/Light from the settings sheet apply live; brightness scrim dims.
  - **Resume**: back out mid-book, reopen → same page. Cold-start **offline** → resumes (local). Back **online** → `EbookProgressSyncer` pushes the locator (detail reading-progress reflects it).
  - **TOC** jump + **bookmark** add/jump land correctly.
  - **Unreadable section** shows the inline message, paging continues.
- [ ] **Step 3: Confirm no orphan refs:** `grep -rn "EpubReader(\|FictionBookReader(\|TextReader(" androidApp/src | grep -v /build/` → only the deletions remain. Delete the three files.
- [ ] **Step 4: Build + test once more; commit:** `chore(reader): remove bones-level text renderers`
- [ ] **Step 5:** Use superpowers:finishing-a-development-branch to decide merge / PR.

---

## Self-review vs. spec

- **One engine, 4 formats** → `ReflowableSource` + adapters (Task 6); routed in Task 11. ✔
- **True pages + paging** → CSS columns + `paginator.js` + tap/swipe (Tasks 7, 10). ✔
- **Pinch-zoom reflow** → transform gesture → `nudgeTextScale` → `applyStyle` re-anchor (Task 10). ✔
- **Themes/margins/line-height + brightness** → `ReflowStyle.toCss` (Task 4); brightness scrim is a Compose overlay in Task 10's Box (add a dim `Box` over the WebView keyed on a brightness setting — uses the existing setting if present, else 0). ✔
- **Locator resume + offline/sync** → `ReflowLocator` in `location`, existing save path + shipped syncer (Tasks 2, 11). ✔
- **Weighted global %** → `SectionWeights` (Task 3); label in Task 11. ✔
- **Error handling** (unreadable section, render-process-gone, legacy `page:N`) → Tasks 2, 8, 9. ✔
- **Replace 3 renderers; PDF/CBZ untouched** → Tasks 11, 13. ✔
- **No server change** → `location` stays opaque end to end. ✔

**Note for the implementer:** the brightness setting referenced in Task 10 isn't in `ReaderDisplaySettings` yet; if absent, wire the scrim to a constant 0 (no-op) and leave the dim control to sub-project 2 — do not add a new setting field in this plan.
