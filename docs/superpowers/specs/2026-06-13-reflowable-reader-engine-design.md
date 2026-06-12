# Reflowable Reader Engine — Design

**Status:** Approved design (brainstorming). Next step: implementation plan via `superpowers:writing-plans`.

**Scope:** Sub-project 1 of the premium multi-format reader. This spec covers **only** the shared reflowable text engine. PDF, comics, and the premium feature layer (highlights, search, dictionary, fonts, reading-time, page-curl) are separate sub-projects.

## Goal

Replace the three "bones-level" text renderers — `EpubReader` (a `HorizontalPager` over chapters, each a vertically-scrolling `WebView`), `FictionBookReader`, and `TextReader` — with **one** reflowable engine that delivers, for EPUB / FB2(FBZ) / TXT / Markdown:

- **True fixed pages** (no vertical scroll) with tap/swipe page turns.
- **Pinch-to-zoom font scaling that reflows the pages** (the original request).
- Themes matching the existing `ReaderTheme` (light / sepia / dark, with `System` → device dark), adjustable margins, line height, and a brightness dim overlay. (An OLED "black" theme is a trivial future addition once `ReaderTheme` gains it — out of scope here.)
- **Locator-based resume** that fits the existing offline local/server progress reconciliation and the already-shipped `EbookProgressSyncer` — with **no server change**.

Non-goals here: highlights/notes, in-book search, dictionary lookup, bundled fonts, reading-time, page-curl animation, and PDF/comic rendering. They build on this engine in later sub-projects.

## Why this shape

The four target formats all reduce to HTML, so one HTML-rendering engine serves all of them; only a thin per-format adapter differs. A WebView gives us faithful HTML/CSS/image/font rendering (the part we never want to reimplement), while we own pagination, gestures, animation, typography, themes, and locators natively. We deliberately avoid Readium and epub.js — the engine is ours end to end. PDF and comics are fundamentally different (fixed pages / image sequences) and stay as dedicated renderers behind the same shell.

## Architecture

```
ReaderScreen (shell: top bar, settings sheet, TOC, bookmarks, progress label)
  └─ ReflowableReader(format, fileUrl, settings, initialLocator,
                      onLocatorChanged, onSectionsKnown, onTextScaleNudge)
       ├─ ReflowableSource            // format → ordered sections of HTML
       │    ├─ EpubReflowSource       // reuse existing EPUB spine/chapter parser
       │    ├─ Fb2ReflowSource        // FB2/FBZ XML → HTML
       │    ├─ MarkdownReflowSource   // MD → HTML
       │    └─ PlainTextReflowSource  // TXT → HTML
       ├─ ReflowWebView + paginator   // CSS multi-column WebView + JS harness
       │    └─ ReflowBridge           // typed @JavascriptInterface JSON, both ways
       ├─ ReflowStyle → CSS           // theme/font-size/margins/line-height
       └─ ReflowLocator + codec       // {sectionIndex, pageProgression, bookProgression}
```

The shell (`ReaderViewModel` + `ReaderScreen`) is unchanged in shape: file resolution, settings persistence, TOC/bookmarks, and progress save/load already exist. Only the EPUB/FB2/TXT/MD render branches swap to `ReflowableReader`; PDF and CBZ branches are untouched.

## Components

### ReflowableSource

```kotlin
interface ReflowableSource {
    val sections: List<ReflowSection>   // ordered reading units
    suspend fun html(index: Int): String?   // section HTML (IO; null = unreadable)
    fun baseUrl(index: Int): String         // for relative <img>/<link> resolution
    val tableOfContents: List<ReflowTocEntry>
}

data class ReflowSection(val index: Int, val title: String?, val approxChars: Int)
data class ReflowTocEntry(val title: String, val sectionIndex: Int)
```

`approxChars` (text length of the section) is the **weight** used for the global progress estimate (below), so we never need to pre-render the whole book to show a stable percentage.

Per-format adapters:
- **EpubReflowSource** — wraps the existing EPUB parser (`EpubBook`): spine entries become sections; `html(i)` returns the styled chapter HTML; `baseUrl(i)` is the unpacked-epub root. TOC from the EPUB nav.
- **Fb2ReflowSource** — parse FB2 XML (FBZ = zipped FB2), transform `<section>`/`<body>` into HTML sections; `<binary>` images become data URIs. TOC from section titles.
- **MarkdownReflowSource** — split on top-level headings into sections; render each via a lightweight Markdown→HTML converter.
- **PlainTextReflowSource** — one section (or split on form-feed / large blank runs); paragraphize into `<p>`.

### ReflowWebView + paginator (the pagination engine)

A single `WebView` renders **one section at a time**. A bundled HTML harness (`assets/reader/reflow/reader.html` + `paginator.js`) wraps the section body and applies CSS that paginates it into viewport-wide columns:

```css
html, body { margin: 0; height: 100%; }
#reflow-root {
  height: 100vh; column-width: 100vw; column-gap: 0;
  overflow: hidden; -webkit-column-fill: auto;
}
img, svg, table { max-width: 100%; height: auto; break-inside: avoid; }
```

`paginator.js` responsibilities:
- After layout, compute `pageCount = round(scrollWidth / innerWidth)` and report it.
- `goToPage(n)`: translate `#reflow-root` by `-n * innerWidth` (CSS transform; instant, no reload) and report the settled page.
- On viewport/style change (font size, rotation), re-measure `pageCount` and re-anchor to the prior `pageProgression`.
- Emit a `relocated` event (current page, pageProgression) when the page settles.

`WebView` is configured: `javaScriptEnabled = true` (paginator only — no remote scripts), `allowFileAccess = true` for the unpacked content, transparent background so the dark page surface shows during load.

### ReflowBridge (typed JS ↔ Kotlin)

A `@JavascriptInterface` carrying `kotlinx.serialization` JSON both directions, so parsing is pure and unit-testable:

- **JS → Kotlin:** `ready`, `paginated { pageCount }`, `relocated { page, pageProgression }`, `error { message }`.
- **Kotlin → JS:** `load(html, baseUrl)`, `goToPage(n)`, `applyStyle(styleJson)`.

All payloads are `@Serializable` data classes; a single `decode(message): ReflowEvent` and a single `command(json)` dispatch keep the surface tiny and testable.

### ReflowStyle → CSS

```kotlin
data class ReflowStyle(
    val theme: ReflowTheme,       // Light, Sepia, Dark (mirrors ReaderTheme; System resolved upstream)
    val fontScale: Float,         // 1.0 == 100%
    val marginScale: Float,
    val lineHeight: Float = 1.55f,
    val fontFamily: String? = null, // null = publisher/default (bundled fonts: sub-project 2)
)
fun ReflowStyle.toCss(): String
```

`ReaderDisplaySettings` (existing: theme/textScale/marginScale/fontFamily) maps to `ReflowStyle`, with `ReaderTheme.System` resolved against `isSystemInDarkTheme()` before mapping (same pattern just shipped for the old EPUB CSS). A separate **brightness scrim** (a translucent black overlay above the WebView, opacity from a brightness setting) handles night dimming without touching CSS.

### ReflowLocator + codec

```kotlin
@Serializable
data class ReflowLocator(
    val sectionIndex: Int,
    val pageProgression: Double,  // 0..1 within the section
    val bookProgression: Double,  // 0..1 across the book (for % + resume ordering)
)
object ReflowLocatorCodec {
    fun encode(l: ReflowLocator): String          // JSON for the `location` field
    fun decode(location: String?): ReflowLocator?  // null for legacy "page:N"/garbage
}
```

Stored in the existing `progressLocation` string; `progressPercent` carries `bookProgression`. The shipped `EbookProgressSyncer` is location-agnostic, so it transports these unchanged — **no server contract change**.

### ReflowableReader (orchestrator)

The Compose entry point that `ReaderScreen` calls for EPUB/FB2/TXT/MD. Responsibilities:
1. Resolve the local file (existing `resolveReaderFile`) and build the `ReflowableSource` for the format (off the main thread).
2. Report `onSectionsKnown(source.tableOfContents)` for the shell's TOC.
3. Load the initial section (`decode(initialLocator)?.sectionIndex ?: 0`), and on the `paginated` event `goToPage(round(pageProgression × pageCount))`.
4. Drive paging from gestures; load the next/previous section at section boundaries.
5. On each `relocated`, compute `bookProgression` and call `onLocatorChanged(encode(locator), bookProgression)`.
6. Re-apply `applyStyle` whenever `settings` change; overlay the pinch gesture and the brightness scrim.

## Data flow

```
open → resolveReaderFile → ReflowableSource(sections)
     → onSectionsKnown(toc)
     → load(section = initial.sectionIndex, applyStyle)
     → JS: paginated{pageCount} → goToPage(initial.pageProgression × pageCount)
user pages (tap/swipe):
     within section → JS goToPage(n) → relocated → onLocatorChanged → save (local + server, existing path)
     past edge      → load next/prev section (page 0 / last) → relocated → save
user pinches:
     gesture → onTextScaleNudge(zoom) → VM updates textScale → settings change
            → applyStyle(new fontScale) → JS re-measure pageCount → re-anchor pageProgression
```

### Global progress (bookProgression)

We avoid an expensive full-book pre-scan. `bookProgression` is a **weighted estimate**:

```
weightBefore = Σ approxChars[0..sectionIndex-1] / Σ approxChars[all]
weightThis   = approxChars[sectionIndex] / Σ approxChars[all]
bookProgression = weightBefore + weightThis * pageProgression
```

This yields a stable, monotonic percentage without rendering unseen sections. The shell shows **"{pct}% · page N of M"** (M = current section page count); a true global "page X of Y" is intentionally out of scope (it would require pre-paginating the whole book at the current font size).

## Page-turn UX

- **Tap zones:** left third = previous page, right third = next page, center third = toggle the reader chrome (top bar / progress).
- **Swipe:** horizontal drag pages; vertical is unused (no scroll).
- **Animation:** a horizontal slide between pages (translate). Within a section this is the CSS transform; across a section boundary it's a crossfade/slide while the next section loads. Page-curl is a later polish.
- **Section-boundary smoothness (enhancement):** optionally preload the adjacent section in a second offscreen WebView and swap on the boundary turn. v1 may accept a brief load at boundaries; the design leaves room for the 2-WebView window without changing the bridge.

## Shell integration

- `ReaderScreen`: the `Epub`, `Fb2/Fbz`, `Txt/Markdown` branches all call `ReflowableReader(format = …)`; `Pdf` and `Cbz` branches are unchanged.
- `ReaderViewModel`: add `state.initialLocator: String?` (from the reconciled progress `location` in `loadReaderState`), and `onLocatorChanged(locationJson, progress)` that updates percent and persists via the existing `saveProgress` (local + server) path. The page-index callbacks remain for PDF/CBZ. Add `nudgeTextScale(zoom)` (coalesced) for pinch.
- Settings sheet, TOC sheet (now fed by `ReflowSection`s), and bookmark add/jump (now locator-based for reflowable formats) reuse the shell. Bookmarks store a `ReflowLocator` JSON in the existing bookmark `location` field.

## Error handling

- **Unreadable section** (`html(i)` null): render an inline "This section could not be loaded" page; paging skips to the next readable section.
- **Parse failure** (corrupt EPUB/FB2): the orchestrator surfaces the existing reader error state via `ReaderScreen`'s `state.error`.
- **Empty book** (no sections): "This book has no readable content."
- **Image/resource missing:** WebView renders alt/blank; never fatal.
- **WebView render-process gone:** catch `onRenderProcessGone`, recreate the WebView, reload the current section at the saved locator.
- **Legacy `"page:N"` locator:** `decode` returns null → open at section 0, page 0 (one-time reset; new saves are `ReflowLocator` JSON).
- **Pinch during section load:** style changes are queued and applied on the next `paginated`.

## Testing

Pure, TDD'd units (no WebView):
- `ReflowLocatorCodec` — encode/decode round-trip; legacy `"page:N"` and garbage → null.
- `ReflowStyle.toCss` — theme colors (incl. System resolved), font-size %, margins, line-height.
- **bookProgression math** — weighting across sections; monotonic; boundaries (first/last section, single-section book, zero-length sections).
- Format adapters — each adapter's HTML output for a small fixture (EPUB chapter, FB2 snippet, Markdown, TXT): produces sections with expected count, titles, and `approxChars`; images become resolvable refs/data-URIs.
- `ReflowBridge` payload `decode`/`command` — JSON round-trips for every event/command type.

Device verification (not unit-testable): real page turns (no vertical scroll), pinch-zoom reflow with position preserved, theme switching live, resume to the saved page (incl. offline cold start and online sync-back), TOC jump, and graceful unreadable-section handling — across one book of each format.

## Performance

- One WebView holds one section; memory stays bounded regardless of book size.
- Within-section paging is a CSS transform (no reload) — instant.
- Re-pagination on font change re-measures one section, not the book.
- Section boundary = one reload (mitigated by the optional adjacency preload).

## Out of scope (later sub-projects)

- Highlights / notes, in-book search, dictionary/lookup, reading-time + "pages left," bundled custom fonts, page-curl animation — **sub-project 2**.
- PDF renderer — **sub-project 3**. Comic (CBZ) renderer — **sub-project 4**.
- A true global "page X of Y" (full-book pre-pagination).
- Any server-side change (the engine is client-only; `location` stays an opaque string).
