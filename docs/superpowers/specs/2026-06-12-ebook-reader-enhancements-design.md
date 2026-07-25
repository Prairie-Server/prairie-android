# Ebook Reader Enhancements — Design

**Status:** Approved design (brainstorming output). Implementation plan to follow via writing-plans.
**Scope:** Android phone app (`androidApp`) reader, with the position/annotation models in the KMP `shared` module. Coordinated **server changes are in scope** (annotation/locator generalization). Paths are repository-relative; assume the repository root (`prairie-android`) is the cwd, and the sibling `prairie-server` repo for server work.

---

## 1. Goal

Bring the reader to a real reading experience across **every format the server offers** (EPUB, PDF, CBZ/comic, TXT/Markdown, FB2/FBZ): true paginated EPUB, in-text search, highlights & notes (synced), font family + brightness controls, and reading-time estimates — on a unified position model.

## 2. Current state

`androidApp/.../ui/screens/reader/`: `ReaderScreen` dispatches by format to `EpubReader` (WebView **scroll**, self-described "bones-level", no real pagination), `PdfReader` (`android.graphics.pdf.PdfRenderer`), `ComicReader` (zip images), `TextReader`, `FictionBookReader`. `ReaderViewModel` handles progress sync + bookmarks. Server has a progress endpoint and an **annotations model that exists but has no reader UI**, and it is **CFI-only** (`cfiRange`). Reading location is a crude `"page:N"` string.

## 3. Core architectural move — `ReaderLocator`

A single position abstraction every format maps to and from. This is the foundation all features build on (progress, search jumps, highlight anchors):

- EPUB / FB2 / TXT / MD → **CFI** (or HTML-harness equivalent).
- PDF → **page + normalized rect**.
- Comic → **page**.

`ReaderLocator` (in `shared/.../model/reader/`) serializes to a typed JSON. It **supersedes `"page:N"`** for progress and is the anchor type for annotations — with a backward-compatible reader so existing `"page:N"` progress rows still load.

## 4. Rendering per format (all server formats)

- **EPUB / FB2 / TXT / Markdown → shared `PaginatedWebReader`**: a WebView host running **epub.js** for EPUB, and an HTML + CSS-multicolumn harness for text/FB2 (converted to HTML). Delivers real **page turns**, accurate page counts, CFI locators, and a JS↔Kotlin bridge used for search and highlight injection.
  - *Alternative considered:* the **Readium-Kotlin** toolkit — more robust and standards-complete, but a heavier dependency and a larger rewrite. **Recommendation: epub.js**, to build on the existing WebView approach and keep the renderer in-tree; Readium remains the fallback if epub.js pagination proves inadequate.
- **PDF** → keep `PdfRenderer`; add **zoom** and a **highlight overlay** (page + rect). Search only if a text layer is added (see risks).
- **CBZ/Comic** → add **zoom** and optional **double-page** spread; text features (search/highlight) are N/A.

## 5. Feature components

- **`ReaderSearchController`** — per-format search producing `ReaderLocator` results with snippets, shown in a results sheet that jumps on tap. EPUB/text/FB2 via JS-bridge / string search; PDF deferred unless a text layer is added; comic N/A.
- **`AnnotationController` + selection UI** — text-selection toolbar → highlight color picker + optional note; renders highlight overlays; lists/edits/deletes via a Highlights sheet. Syncs through the existing `EbookReaderApi` (generalized model — §6).
- **Reader settings** — extend `ReaderControls`/settings with **font family** (serif / sans / dyslexic-friendly) and an in-reader **brightness** slider (window attribute), alongside existing size/theme/margins. Applied to all reflowable formats.
- **`ReadingEstimator`** — "~X min left in chapter / book" from remaining content length × reading speed (start from a default WPM; optionally adapt to the user's observed pace).

## 6. Server changes (coordinated, in `prairie-server`)

The current annotation model is **CFI-only**, which cannot anchor PDF (page+rect) or comic highlights. Generalize it:

- Annotation create/list payloads carry a **typed `ReaderLocator` JSON** (range form: start+end locators) instead of only `cfiRange`. Keep `cfiRange` accepted for backward compatibility / migration.
- Progress `location` accepts the same typed locator (string `"page:N"` still accepted).
- DB migration via Goose (timestamped, `make migrate-create`) adding the generalized columns/JSON; no destructive change to existing rows.

Client `shared` models (`EbookReaderModels`) and `EbookReaderApi` update to match. This unlocks **highlights across all formats with cross-device sync**.

## 7. Data flow

Format reader ⇄ `ReaderLocator` ⇄ {progress store + server progress endpoint, annotation store + server annotations endpoint, search results, reading-time}. The WebView JS bridge emits/accepts CFI locators for reflowable formats; PDF/comic map locators natively in Kotlin.

## 8. Phasing

1. **`ReaderLocator` + generic progress** — introduce the model, refactor every reader and the progress path to it (backward-compatible with `"page:N"`).
2. **Paginated EPUB** — `PaginatedWebReader` with epub.js: page turns, page counts, CFI progress.
3. **Fonts + brightness** — settings for all reflowable formats.
4. **Highlights & notes** — selection UI + overlays + **server locator generalization** (§6) + sync.
5. **In-text search** — EPUB/text/FB2; PDF deferred.
6. **Reading-time estimates.**
7. **Per-format polish** — PDF zoom + highlight overlay, comic zoom/double-page, FB2 pagination parity.

Phases 1–2 deliver the biggest reading-quality jump; 4 depends on the server change landing.

## 9. Risks & assumptions

- ⚠️ **PDF search**: `PdfRenderer` has no text extraction. PDF in-text search needs an added text-extraction lib or is **deferred** (recommended: defer, note in UI).
- ⚠️ **epub.js in WebView**: asset bundling, JS-bridge performance on large books, and selection/highlight reliability across reflow. Spike early in Phase 2; Readium is the fallback.
- **Server coordination**: Phase 4 requires the `prairie-server` annotation/locator change to ship first; until then highlights can run client-local (no sync) behind the same UI.
- **Assumptions:** Android phone scope; models live in `shared` so Apple/TV can adopt; comic/PDF do not get reflow (fonts/margins) — those controls hide for fixed-layout formats, as today.

## 10. Testing

- **Unit:** `ReaderLocator` round-trips per format + `"page:N"` backward-compat; reading-time math; annotation serialization (typed locator + legacy CFI).
- **Server:** annotation create/list with typed locator + legacy CFI; progress migration safety; Goose up/down.
- **Manual on-device:** EPUB page turns + accurate page count, search→jump, select→highlight+note→reopen persists + syncs across two devices, font/brightness across formats, reading-time sanity, PDF zoom + highlight overlay, comic zoom/double-page.

## 11. Out of scope

Audiobook playback (covered by the audiobook player redesign spec), TTS/read-aloud, dictionary/translation lookups, and social/shared annotations.
