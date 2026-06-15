# Best-Of-Source Borrow Map

**Date:** 2026-06-15
**Status:** Draft for review. Extraction + plan; no code changes yet.
**Branch:** `feature/production-playback-architecture`
**Related specs:** `2026-06-14-all-media-best-of-source-roadmap-design.md`, `2026-06-14-best-of-breed-client-architecture-design.md`

## Purpose

This document mines the reference apps in `/Users/jimcole/source` and maps their best
product behavior and engineering patterns onto silo's current subsystems. It is the
concrete companion to the approved best-of-source roadmap: where the roadmap says *what*
to borrow and in what order, this says *exactly which pattern, from which file, applied
where in silo, at what effort, under what license, with what Android-7 caveat*.

Approach (agreed): **hybrid, prioritized by gap.** Deep code-level extraction for
Reading (ebook/reflow, comics/manga, PDF), Audiobook, and the Video player; survey-level
(gaps only) for Watch-Together, Notifications, Requests, and Admin. Each was mined by an
agent reading the mapped reference apps against silo's current code.

## How to read this

- Effort: **S** = ≤1 day, **M** = a few days, **L** = a week+ / new subsystem.
- Every borrowed idea cites a real source file and the silo file it applies to.
- Correctness items overlapping the playback/reader review (2026-06-15) are flagged.
- Constraints carried throughout: **Android 7 / API 24 hard floor** and **AGPLv3**.

## License reality (read before copying any code)

silo is **AGPLv3**. The roadmap's "copyleft is not a blocker" is true *for ideas* and
*for AGPL/GPLv3 code*, but there is a real incompatibility to respect:

| License of source | Copy code into silo? | Use as pattern/reimplement? |
|---|---|---|
| AGPL-3.0 (readest, koreader) | Yes | Yes |
| GPL-3.0 / LGPL-3.0 (book-story, Kotatsu, seeneva, Voice, lissen, findroid, AFinity, LibreraReader, libmobi) | Yes (GPLv3-or-later → AGPLv3 OK; LGPL as separate lib) | Yes |
| **GPL-2.0-only** (jellyfin-androidtv, jellyfin-android, Wholphin) | **No** — not AGPLv3-compatible for direct reuse | Yes — architecture reference only, reimplement |
| Apache-2.0 (mihon, komikku, Seismic) | Yes (with attribution) | Yes |
| MIT (JellyBook) | Avoid copying (attribution friction in AGPL tree) | Ideas only |

**Practical rule:** treat the Jellyfin GPLv2 clients and MIT JellyBook as *read-only
references*; reimplement their ideas in silo's own code. Track lineage in the provenance
ledger below per the roadmap's AGPL tracking requirement.

---

## Cross-Subsystem Prioritized Backlog

Ranked by value × current-gap × (inverse) effort, then mapped to the roadmap's phases.

### P0 — Quick wins / correctness (do first; mostly S, high value)

These are small, independent, and several overlap confirmed bugs from the branch review.
They de-risk everything built on top.

| # | Item | Subsystem | Effort | From branch review? |
|---|---|---|---|---|
| 1 | Stop presets force-disabling text tracks; actually apply `preferredTextLanguage` | Video | S | Yes — subtitles vanish mid-playback |
| 2 | EPUB WebView base URL → OPF dir, not unpacked root | Reading/ebook | S | Yes — broken EPUB images/CSS |
| 3 | `chooseReaderVersion` fall through to a readable version | Reading/ebook | S | Yes — reader refuses readable book |
| 4 | Treat any `room_closed` as terminal; cap/abort non-transient reconnects | Watch-Together | S | Yes — infinite reconnect storm |
| 5 | Notifications: fold all mutations through `MutableStateFlow.update{}`; terminal on 401; reset backoff on connect | Notifications | S–M | Yes — lost updates + reconnect storm |
| 6 | Admin: send `libraryIds = null` (omit) when field blank | Admin | S | Yes — clears all library access |
| 7 | Requests: add `reset()` on logout + `loadGeneration` gating | Requests | S–M | Yes — cross-user leak + stale overwrite |
| 8 | Natural/numeric comic page sort (`page2` vs `page10`) | Reading/comic | S | New (found in mining) |
| 9 | Move MPV `runBlocking` auth-header fetch off the player-build thread | Video | S | New (found in mining) |
| 10 | PDF OOM guard: catch `OutOfMemoryError`, drop cache, continue | Reading/PDF | S | Roadmap error-handling |

### P1 — Reading phase (current release focus, roadmap Phase 1–3)

**Ebook/reflow**
- Typography expansion: font-family picker + line-height + justification + brightness, built on a type-safe settings store. *(M)*
- Three-tier settings (global < per-book deltas < session). *(M)*
- Element-level locator (CSS-selector / text-quote anchor) + char-offset location map. *(M)* — prerequisite for accurate resume, TOC jumps, and bookmarks.
- Nested TOC drawer with auto-expand; reading-time estimate. *(S each)*
- Bookmarks / highlights / notes. *(L; needs element locator first)*

**Comics/manga**
- `Viewer` interface with per-mode implementations (comic LTR / manga RTL / webtoon), replacing the `ComicReader.kt` monolith; config vs tap-navigation split. *(M)*
- `ZoomMode` fit modes + zoom/pan via SubsamplingScaleImageView; adaptive RGB_565 / RAM-gated decode. *(L; the A7-safe rendering path)*
- Bounded look-ahead prefetch (Semaphore + RAM gate). *(M)*

**PDF/fixed-doc**
- Memory-derived render cap + RGB_565 (replace the 2×/2000px hardcode). *(S)*
- LRU page-bitmap cache + bitmap pool/`inBitmap` reuse + adjacent prefetch. *(M)*
- Pinch-zoom/pan/double-tap + fit modes; page thumbnails grid. *(M each)*

### P2 — Audiobook phase (roadmap Phase 4)

- **Multi-file book timeline** (one MediaItem per chapter from clipped/concatenated file segments). *(L — the load-bearing gap)*
- **Background playback fix** (don't stop audiobooks on swipe-away/off-screen; detached session + mini-player). *(M–L)*
- Wire the already-persisted skip-silence + volume gain; per-book prefs. *(S–M)*
- Streaming `SimpleCache` separate from public downloads. *(M)*
- Sleep-timer fade-out + auto-rewind + shake-to-reset; end-of-chapter via scheduled cue points. *(M)*

### P3 — Video hardening (roadmap Phase 1–2; preserve stability)

- Surface backend kind / `displayName` / subtitle-rendering mode on the player UI state (the "libass (MPV) vs Media3 text" chip). *(S)*
- `switchBackend()` hot-swap Media3↔MPV on preflight fallback. *(M)*
- Buffer preset as a user preference honored by both backends. *(M)*
- libass on the Media3 backend (ASS/SSA parity without MPV); MPV libass styling surface. *(L / M)*
- Tighter resume cadence (3s play / 15s pause) + server→client seek channel; explicit FF/REW keys. *(S–M)*

### Borrow now / later / don't

- **Now:** all P0; Reading typography + locator + comic Viewer abstraction + PDF memory fixes; Video P0 + capability surfacing.
- **Later:** bookmarks/highlights (needs locator), MOBI/AZW via libmobi, double-page spreads, MPV-as-default, libass-on-Media3, music (separate roadmap phase).
- **Don't:** fork any app shell; copy GPLv2 Jellyfin code or MIT JellyBook code; adopt book-story's CSS-discarding native-text parser; pull a native RAR/7z lib for CBR/CB7 in the first slice; ship MPV as the default or only path on TV/A7; Seeneva OCR/panel detection in the first comic pass.

---

# Deep Sections

## Ebook / Reflow Reader

### Best-in-class reference(s) and why

- **readest** (AGPL-3.0) — most directly transferable: foliate-js WebView reader whose **document model, CFI locator, and three-tier settings hierarchy** map ~1:1 onto silo's WebView-reflow architecture. Primary mine.
- **book-story** (GPL-3.0) — best **Compose-native shell and settings UX** (type-safe DataStore DSL, debounced progress persistence, nested-TOC drawer, font registry). *Do not* borrow its rendering path (it discards EPUB CSS).
- **koreader** (AGPL-3.0) — **typography depth** (language-keyed hyphenation, margin presets, CSS-tweak taxonomy) as data tables/UX, not the C++ engine.
- **LibreraReader** (GPL-3.0; bundles LGPL libmobi) — the concrete **MOBI/AZW native-parse** path (libmobi → EPUB conversion, then reuse the EPUB pipeline).
- *Not used as code:* JellyBook (MIT — ideas only).

### What they do well

- **readest document model & locator** (`apps/readest-app/src/libs/document.ts`): one polymorphic `DocumentLoader.open()` routes every format; **magic-byte** detection (not extension); ZIP tail-scan for a malformed EOCD. Locator = EPUB **CFI** (render-independent, survives font/size changes) + a parallel **byte-offset location map** (`SIZE_PER_LOC = 2500`); degrades to spine-prefix match on malformed CFIs; also stores a KOReader-compatible `xpointer`. TOC cached to `nav.json` with a version for invalidation (`services/nav/index.ts`). Settings (`types/book.ts`): global < book-config < per-view, persisting only deltas via `overrideFont/Layout/Color`.
- **book-story Compose shell** (`data/settings/SettingsManager.kt`): a `Setting<T,S>` DSL over DataStore with custom (de)serialize lambdas, each setting a reactive `Flow`. `ReaderModel.updateProgress()` debounces persistence (300 ms via `snapshotFlow + distinctUntilChanged + debounce`). Nested-TOC drawer auto-expands current chapter (`ui/reader/ReaderChaptersDrawer.kt`). Font registry of variable fonts (`ui/reader/data/ReaderData.kt`).
- **koreader typography** (`frontend/apps/reader/modules/readertypography.lua`, `ui/data/creoptions.lua`, `css_tweaks.lua`): 60+ language→hyphenation-dictionary tags; named margin presets; toggleable CSS tweaks.
- **LibreraReader MOBI** (`app/.../libmobi/LibMobi.java`, `Builder/jni/libmobi-0.12/`): JNI `convertToEpub()`; strategy is convert-once then reuse EPUB pipeline. FB2 parse has a retry-on-XML-fixup fallback (`Fb2Context.java`).

### Silo current state

- **Two parallel EPUB paths:** legacy `EpubReader.kt` (per-chapter pager + scrolling WebView, no intra-chapter pagination) and the strategic `reflow/ReflowableReader.kt` (single `ReflowWebView` + JS column paginator). `EpubReader.kt` looks superseded.
- **Locator sound but coarse:** `reflow/ReflowLocator.kt` = `(sectionIndex, pageProgression, bookProgression)`, JSON-encoded; book progress estimated from per-section char counts (`SectionWeights.kt`). Render-independent (good) but only section+page-fraction granularity — no intra-paragraph anchor, so resume can drift after a font change and TOC/bookmarks can't target an element.
- **Typography minimal:** `ReaderDisplaySettings` (`android-shared/.../ebook/ReaderControls.kt:10`) carries only `theme`, `textScale`, `marginScale`. No font-family, line-height (hardcoded 1.55 in `ReflowStyle.kt:16`), spacing, justification, or brightness; `ReflowStyle.kt:23` hardcodes `font-family:Georgia,...serif`.
- **No per-book settings; no bookmarks/highlights/notes/in-book-search/reading-time.**
- **Confirmed bug — wrong base URL:** `EpubReader.kt:187` and `reflow/EpubReflowSource.kt:30` pass `readerDirectoryBaseUrl(book.unpackedRoot)`, but `EpubBook.kt:16` already knows `opfDir`; spine hrefs are relative to the OPF dir, so EPUBs with the OPF in `OEBPS/` get broken images/CSS. Fix: use `book.opfDir` (and per-entry parent dir for nested hrefs).
- **Confirmed bug — `chooseReaderVersion`:** `EbookVersionSelection.kt:180-182` returns null when the requested fileId is `Unsupported`, even if another readable version exists; `chooseEbookVersion` (line 164) already falls through. Fix: fall through to `preferredReaderTarget(...)`.

### Concrete adoptable patterns

| Idea | Source location | Where in silo | Effort | License | A7 caveat |
|---|---|---|---|---|---|
| Element-level locator (CSS-selector/text-quote anchor) alongside section+fraction | readest `utils/cfi.ts`, `services/nav/locations.ts` | `reflow/ReflowLocator.kt`, `paginator.js` (emit nearest-element selector on relocate) | M | AGPL design, reimplement | `elementFromPoint`+`querySelector` OK on API24 WebView; avoid `:has()` |
| Char/byte-offset location map for accurate book progress | readest `bakeLocationsAndCfis` (SIZE_PER_LOC=2500) | replace `SectionWeights.kt` estimate with cumulative offset table | S | AGPL design | none |
| Three-tier settings (global < per-book deltas < session) | readest `types/book.ts` override flags | `ReaderControls.kt` + `EbookLocalStateStore.kt` | M | AGPL design | none |
| Type-safe DataStore settings DSL | book-story `data/settings/SettingsManager.kt` | silo display-prefs persistence (absent today) | M | GPL-3.0 — attribute if copied | DataStore fine on API24 |
| Debounced (300ms) progress persistence | book-story `ReaderModel.updateProgress()` | `ReflowableReader` locator callback / `ReaderViewModel.kt` | S | GPL-3.0 pattern | none |
| Font registry + font-family picker | book-story `ui/reader/data/ReaderData.kt` | extend `ReaderDisplaySettings`; wire `ReflowStyle.kt:23` | M | GPL-3.0 pattern | bundle static weights; variable fonts API26+ |
| Nested-TOC drawer w/ auto-expand current | book-story `ui/reader/ReaderChaptersDrawer.kt` | `ReaderShell.kt` | S | GPL-3.0 pattern | none |
| Magic-byte format detection + ZIP EOCD tolerance | readest `libs/document.ts` | `EbookVersionSelection.kt` / reflow source builder | M | AGPL design | none |
| MOBI/AZW → EPUB via libmobi, then reuse pipeline | LibreraReader `LibMobi.java` + `libmobi-0.12/` | new `MobiReflowSource`; flips MOBI/AZW to InApp | L | libmobi LGPL-3.0 (separate `.so`) | NDK `minSdk 24`; ship arm64+armv7; size cost |
| FB2 retry-on-XML-fixup fallback | LibreraReader `Fb2Context.java` | `reflow/Fb2ReflowSource.kt` | S | GPL-3.0 pattern | none |
| Language-keyed hyphenation + margin presets + CSS tweaks | koreader `readertypography.lua`, `creoptions.lua`, `css_tweaks.lua` | `ReflowStyle.kt` + settings UI | M | AGPL data tables | `hyphens:auto` unreliable pre-API26 — gate it |
| Reading-time estimate (chars + WPM) | readest `types/book.ts` `TimeInfo` | derive from `SectionWeights`; surface in chrome | S | AGPL design | none |
| Bookmarks/highlights/notes model | readest `types/book.ts` `BookNote` | `EbookLocalStateStore.kt`; needs element locator | L | AGPL design | selection overlays via JS OK on API24 |

### What NOT to borrow / risks

- Don't copy JellyBook (MIT) code; don't adopt book-story's CSS-discarding native-text parser (silo's WebView correctly preserves publisher CSS).
- Don't chase koreader's CREngine; only its Lua data tables/UX taxonomy are practical.
- Full EPUB-CFI is heavy — a lighter element-selector + text-quote anchor gives ~90% of resume/bookmark stability for far less.
- libmobi adds NDK build + binary size + LGPL notice obligations — justify only if MOBI/AZW is a real need; otherwise keep graceful external-open.
- Variable fonts and CSS hyphenation are API-version-sensitive in System WebView — ship static weights and gate hyphenation on API24.
- **Fix the two confirmed bugs before layering features** — both undermine any feature assuming correct asset loading / version selection.

## Comics / Manga Reader

### Best-in-class reference(s) and why

- **mihon** (Apache-2.0) — the architecture gold standard: a tiny `Viewer` interface with swappable implementations (L2R/R2L/Vertical pager + Webtoon recycler), a `ViewerConfig`/`ViewerNavigation` split (reading-direction separate from tap-zone behavior), and chapter-transition + adjacent-page preloading in the page-selection callback. Best model for "comic vs manga must be split."
- **Kotatsu** (GPL-3.0) — best **Android-7 ergonomics**: `ZoomMode` enum via SubsamplingScaleImageView, a remappable 9-zone tap grid, a RAM-aware bounded prefetch queue, adaptive downsampling.
- **komikku** (Apache-2.0, mihon fork) — incremental features (double-page pairing, smaller tap zones, webtoon pinch-zoom, rotation-restore guard).
- **seeneva** (GPL-3.0) — **defer**; OCR/balloon-zoom via a Rust native layer, out of the first slice.

### What they do well

- **mihon Viewer abstraction** (`viewer/Viewer.kt:12`): 5-method interface; concrete `L2RPagerViewer`/`R2LPagerViewer`/`VerticalPagerViewer` (`viewer/pager/PagerViewers.kt`) differ only by overriding move + orientation; R2L reverses the adapter list (`PagerViewerAdapter.kt:102-104`); Webtoon is a separate `RecyclerView` viewer. **Direction/mode = object selection, not branching.**
- **mihon Config/Navigation split** (`viewer/ViewerConfig.kt`, `viewer/ViewerNavigation.kt:44-53`): config holds behavior fed reactively from prefs; navigation maps a normalized `PointF`→`NavigationRegion` via a list of fractional `RectF`s with `invert()` for RTL. Presets (`LNavigation`, `EdgeNavigation`, `KindlishNavigation`…) are pure data.
- **mihon preloading + transitions** (`PagerViewer.kt:225-244`, `PagerViewerAdapter.kt:47-114`): preload next chapter when `pages.size - page.number < 5`; flat list stitches [prev][transition][curr][transition][next]; `offscreenPageLimit = 1`; per-page decode is a cancellable coroutine tied to view attach/detach.
- **Kotatsu** (`core/model/ZoomMode.kt`, `reader/domain/PageLoader.kt`): fit modes via SSIV; `Semaphore(3)` + reversed LIFO prefetch capped at 6, **disabled under 80 MB free RAM**; adaptive `downSampling` and RGB_565 vs ARGB_8888 by RAM; auto-webtoon detection by aspect ratio; remappable 9-zone `TapGridArea` with per-zone `TapAction`.

### Silo current state

`ComicReader.kt` is a ~360-line monolith:
- **Single mode only:** `HorizontalPager` LTR (`:212`); no RTL/vertical/webtoon (RTL flagged "later", `:56-58`).
- **No fit modes / no zoom:** `ContentScale.Fit` hardcoded (`:268-269`); no SSIV-equivalent for zoom/pan.
- **Hardcoded 3-zone tap nav** inline (`:192-210`) — not configurable/invertible.
- **No real preloading:** each page decodes lazily when composed (`:235-239`); no look-ahead/LIFO/RAM gate.
- **Decode:** sensible 2-pass `inSampleSize` downsample (`:289-314`) — keep — but ARGB_8888 only, no RGB_565/low-RAM path, no region decoder.
- **Archive:** one `ZipFile` open for reader lifetime, closed off-main (`:111-133`) — good. CBR/CB7/CBT external-only. Page listing is **lexicographic** (`:343-358`) → `page2` vs `page10` misorders.
- **No chapter/issue nav; comic and manga not split.**

### Concrete adoptable patterns

| Idea | Source location | Where in silo | Effort | License | A7 caveat |
|---|---|---|---|---|---|
| `Viewer` interface + per-mode impls (L2R/R2L/Vertical/Webtoon) by object | mihon `viewer/Viewer.kt:12`, `pager/PagerViewers.kt` | replace `ComicReader.kt` monolith; `ComicViewer` + manga/webtoon impls | M | Apache-2.0 — reimplement, attribute | none |
| Config vs tap-navigation split (reactive config; fractional `RectF`→region) | mihon `ViewerConfig.kt`, `ViewerNavigation.kt:44-53` | new `ComicReaderConfig` from `ReaderDisplaySettings` | M | Apache-2.0 | none |
| Tap-zone presets (L/RightLeft/Edge/Kindlish) + RTL invert | mihon `navigation/*.kt` | replace inline thirds `ComicReader.kt:192-210` | S | Apache-2.0 | none |
| Remappable 9-zone tap grid + per-zone action | Kotatsu `reader/domain/TapGridArea.kt`, `data/TapGridSettings.kt` | tap layer; persist in display settings | M | GPL-3.0 (AGPL-OK) | none |
| Look-ahead prefetch: bounded LIFO + `Semaphore(3)` + **RAM gate (<80MB skip)** | mihon `PagerViewer.kt:225`; Kotatsu `PageLoader.kt` | new `ComicPagePrefetcher` from held `ZipFile`; decode N±2 | M | Apache/GPL | **critical on API24**: keep RAM gate, cap concurrency |
| `ZoomMode` (FIT_WIDTH/HEIGHT/SCREEN/ORIGINAL) + zoom/pan/double-tap via SSIV | Kotatsu `core/model/ZoomMode.kt`, `ui/pager/standard/PageHolder.kt`; mihon `ReaderPageImageView.kt` | replace `Image`/`ContentScale.Fit` (`ComicReader.kt:263-270`) with SSIV in `AndroidView` | L | GPL/Apache | SSIV region/tile decode = the A7-safe path for large/zoomed pages |
| Adaptive decode: RGB_565 + region decoder + downsample by free-RAM | Kotatsu `BasePageHolder`, `core/image/BitmapDecoderCompat.kt` | extend `decodeComicPageBitmap` (`:299-314`) | M | GPL-3.0 | targets API24 OOM; `ImageDecoder` is API28+ — keep BitmapFactory fallback |
| Chapter/issue stitching (prev/transition/curr/transition/next) + range preload | mihon `PagerViewerAdapter.kt:47-114` | new series-aware layer above `ReaderViewModel` | L | Apache-2.0 | needs backend series API |
| Natural/numeric page sort | (silo bug) | `listComicArchivePages` (`:343-358`) | S | n/a | none |
| Double-page spread pairing (landscape) — later | komikku `PagerViewerAdapter` joinedItems | future `PageLayout` mode | L | Apache-2.0 | two bitmaps at once → watch API24 memory |
| Rotation-restore guard (suppress spurious page events) | komikku `viewer/pager/Pager.kt` | pager-state restore | S | Apache-2.0 | none |

### What NOT to borrow / risks

- Don't fork the View-based viewers wholesale (XML ViewPager/RecyclerView + SSIV); borrow the *abstractions* and reimplement in Compose, using thin `AndroidView`/SSIV wrappers only where zoom demands it.
- Don't pull a native unrar/7z lib for CBR/CB7 in the first slice — keep external-only.
- **API24 decode memory is the dominant risk:** mandatory RAM-gated prefetch, bounded concurrent decodes, RGB_565 + region decoding via SSIV, free-RAM-tied downsample. `ImageDecoder` paths are API28+ — keep BitmapFactory fallback.
- Don't copy mihon's Injekt/preferences plumbing; route config through silo's existing `ReaderDisplaySettings` + `EbookLocalStateStore`.

## PDF / Fixed-Document Reader

silo renders fixed documents with Android's built-in `PdfRenderer` (no native MuPDF/pdfium). The references all sit on MuPDF (GPL/AGPL) — adopt **algorithms/patterns**, not their JNI codecs.

### Best-in-class reference(s) and why

- **koreader** (AGPLv3) — strongest *conceptual* reference: tile-cache budgeting from live free memory, `(page|zoom|rotation|gamma)` hash keys, content-bbox auto-crop, N-page render hinting.
- **document-viewer / EBookDroid** (GPLv3) — best *Android-native* reference: real `BitmapManager` pool, `LinkedHashMap` LRU with `removeEldestEntry`, `ZoomModel` + `MultiTouchGestureDetector`, cleanest `OutOfMemoryError` recovery.
- **LibreraReader** (GPLv3) — best **page-locator** (percentage position) and quadtree tiling for deep zoom; sane memory budget (`min(maxHeap/2, 256MB)`, floor 64MB).

### What they do well

- **Memory-aware cache budget** — koreader `doccache.lua:15,53` (`free_mem × proportion`, slots = budget/avg_itemsize); live-pressure evict 50% when free RAM <20% (`cache.lua:151`).
- **LRU page cache + recycle-on-evict** — document-viewer `DecodeServiceBase.java:385-417`; Librera `:63-82`.
- **Bitmap pool/reuse** — document-viewer `BitmapManager.java:81-257` (match w/h/config before alloc, cap `maxMemory()/2`).
- **OOM safety net** — document-viewer `DecodeServiceBase.java:215-228`; Librera `:291-369`: catch `OutOfMemoryError`, clear cache, recycle, abort task — never crash.
- **Native page lifecycle guards** — Librera `MuPdfPage.java` `isRecycled()` + global `TempHolder.lock`; document-viewer `MuPdfPage.java:93-113` synchronized recycle. (Validates silo's own concern from two codebases.)
- **Fit modes** — document-viewer `SinglePageController.java:253-268`; koreader `readerzooming.lua:522-593` (9 modes).
- **Content auto-crop** — koreader `pdfdocument.lua:155-176` + `koptinterface.lua:191-237` (validates bbox > 10% of page). `PdfRenderer` exposes no bbox → needs pixel scan.
- **Prefetch/hinting** — koreader `readerhinting.lua:13-26` (pre-render ~3 pages async).
- **Double-tap & pinch** — document-viewer `MultiTouchGestureDetector.java`, `ZoomModel.java` (clamp 1–32), `AbstractViewController.java:690-695,812-845`.
- **Page locator** — Librera `AppBook.java:99-116` (fraction → `round(pages*p)`); document-viewer `BookSettings.java` (currentPage + offsetX/Y).
- **Thumbnails** — document-viewer `ThumbnailFile.java`/`CacheManager.java` (SoftReference + disk JPEG).

### Silo current state

`PdfReader.kt` (backed by `PdfRenderer`, in a `HorizontalPager`):
- **Lifecycle — mostly solid:** `SerializedCloseable` (`:244-260`) serializes every renderer touch + close through one `Mutex`; `awaitDispose` closes on a scope outliving composition (`:92-96`); `openRenderer` closes PFD on ctor throw (`:269-277`). Already has the single-mutex version of the references' lesson.
- **Render config — heavy for API24:** `renderPdfPageBitmap` (`:284-303`) always ARGB_8888 at 2× page width capped 2000px → a 2000×2800 page ≈ 22 MB each, multiplied by pager neighbors. No RGB_565, no `inBitmap`/pool, no memory-derived cap. **Gap.**
- **No cache / no prefetch:** re-renders on `LaunchedEffect(pageIndex)` (`:194-196`). **Gap.**
- **No zoom/pan/double-tap:** static `Image(ContentScale.Fit)` (`:220-227`). **Gap.**
- **No fit modes; no thumbnails** (text-only page list in `ReaderShell.kt:336`). **Gap.**
- **No OOM guard:** render failure shows error text (`:229-231`) but doesn't clear state to recover next page. **Gap.**
- **Locator adequate:** persists `page:N`, resumes via `initialPage` (`:139-148`), reports via `onPageChanged` (`:151-155`).

### Concrete adoptable patterns

| Idea | Source location | Where in silo | Effort | License | A7 caveat |
|---|---|---|---|---|---|
| Memory-derived render cap + RGB_565 | koreader `doccache.lua:15,53`; Librera `MemoryUtils.java` | `renderPdfPageBitmap` `PdfReader.kt:284` | S | pattern only | critical — `memoryClass` on API24 can be 32–64MB |
| LRU page-bitmap cache (recycle on evict) | doc-viewer `DecodeServiceBase.java:385-417` | cache around `PdfPage` (`:187`) | M | GPL pattern | window 1–3 pages on low-heap |
| Bitmap pool / `inBitmap` reuse | doc-viewer `BitmapManager.java:81-257` | render path (`:297`) | M | GPL pattern | `PdfRenderer` renders into caller bitmap — easy win |
| Adjacent-page prefetch (N=1–2) | koreader `readerhinting.lua:13-26` | around pager (`:151`) | S | AGPL pattern | cap N=1 low-mem; cancel on fast swipe |
| Pinch-zoom + pan + double-tap | doc-viewer `MultiTouchGestureDetector.java`, `ZoomModel.java` | `PdfPage` (`:197-227`) via Compose `transformable`/`detectTapGestures` | M | GPL — Compose-native, ideas only | re-render sharper region only after zoom settles |
| Fit modes (page/width/height/original) | doc-viewer `SinglePageController.java:253`; koreader `readerzooming.lua:522` | new mode enum in `PdfPage` | S/M | pattern | fit-width = tall bitmaps; clamp by memory |
| Content auto-crop (margin trim) | koreader `pdfdocument.lua:155`, `koptinterface.lua:191` | post-render bbox scan | L | AGPL pattern | no bbox API → pixel scan; skip on low-end |
| Page thumbnails + grid overview | doc-viewer `ThumbnailFile.java`, `MuPdfPage.renderThumbnail` | replace text list `ReaderShell.kt:336` | M | GPL pattern | render ~120px RGB_565, throttle |
| OOM recovery (clear cache, recycle, continue) | doc-viewer `DecodeServiceBase.java:215-228` | wrap `renderPdfPageBitmap` (`:284`) | S | GPL pattern | essential on API24 |
| Intra-page offset in locator (zoom resume) | Librera `AppBook.java:27-28`; doc-viewer `BookSettings` | `ReaderViewModel.kt:481` `page:N` → +offset/zoom | S | pattern | keep `page:N` back-compat |

### What NOT to borrow / risks

- Don't import MuPDF/pdfium or `com.artifex.*`/`org.ebookdroid.droids.mupdf.*` — silo deliberately uses `PdfRenderer`; MuPDF is a large native + GPL-coupling commitment (separate engine decision, not a borrow).
- Skip quadtree tiling for v1 — `PdfRenderer.Page.render` supports a clip `Rect` + `Matrix`, so region rendering is far simpler; revisit only for >4× zoom on huge pages.
- Don't blindly allow 1–32× zoom — render *clipped regions* at high zoom, never a full-page bitmap scaled up.
- Avoid giant fixed thread pools (doc-viewer thumbnail executor sized 256) — bound to 1–2 render threads.
- The **2×/2000px hardcode (`PdfReader.kt:294`) is the single biggest A7 liability** — replace before adding zoom.
- koreader's k2pdfopt reflow is irrelevant — silo reflows via its separate reflow engine.

## Audiobook Player

### Best-in-class reference(s) and why

- **Voice** (GPL-3.0) — gold standard for an *audiobook-native* on-device player: per-book speed/gain/skip-silence, chapter cue points, sleep timer with fade + shake-to-reset + end-of-chapter, `MediaLibraryService`. Borrow its **playback-service shape, sleep-timer state machine, and per-book audio-processing model**.
- **lissen-android** (GPL-3.0) — gold standard for *server-backed multi-file timeline*: one MediaItem per chapter from clipped/concatenated file segments, centralized absolute↔(chapter,offset) math, a streaming `SimpleCache` **separate** from downloads, chapter-boundary-aware sync. Borrow its **timeline/MediaSource construction and dual-cache separation** — silo's biggest gap.
- absorb / aradia / AudioAnchor / audiobookshelf-app — secondary UX/domain references.

### What they do well

- **Voice service & sleep timer:** `PlaybackService : MediaLibraryService` (`core/playback/.../session/PlaybackService.kt:32`); ExoPlayer for speech (`AUDIO_CONTENT_TYPE_SPEECH`, `setHandleAudioBecomingNoisy`, `setWakeMode`, `PlaybackModule.kt:47`). Notification prev/next remapped to interval seek (`player/VoicePlayer.kt:109,128`); in-app buttons do chapter-boundary skip via custom commands (`session/LibrarySessionCallback.kt:56,78`). **Sleep timer** is a `tailrec` countdown that pauses while not playing (`SleepTimerImpl.kt:75,125`), ramps volume in the fade window (`:116`), pauses with auto-rewind (`PlayerController.pauseWithRewind:147`), then a 30s shake-to-reset window (Seismic, `:108,134`); EndOfChapter via scheduled `PlayerMessage` cue points (`VoicePlayer.registerChapterMarkCallbacks:306`). Per-book speed/gain/skip-silence on `BookContent` (`BookContent.kt:14,26`) re-applied on load (`:289`); gain via `LoudnessEnhancer` keyed to the session id (`misc/VolumeGain.kt`).
- **lissen timeline:** `bookToChapterMediaItems()` → one MediaItem per chapter (`PlaybackService.kt:303-346`); `resolveChapterToFiles()` handles chapter/file misalignment (`:255-301`); `LissenMediaSourceFactory` builds `ClippingMediaSource`/`ConcatenatingMediaSource2` over a `lissen://` URI (`:62-112`); absolute→chapter binary search (`CalculateChapterIndexAndPosition.kt:20-48`). **Dual cache:** opportunistic `SimpleCache` LRU (`MediaModule.kt:41-55`, ≤512MB) via `CacheDataSource`, distinct from the Room+filesystem download store; `provideFileUri()` prefers the local downloaded file, else cached HTTP (`LissenMediaProvider.kt:70-93`). Progress sync event + interval (45s, tightening to 5s near boundaries) writing local + `POST /session/{id}/sync` with `timeListened` delta (`PlaybackSynchronizationService.kt:69-136`).

### Silo current state

- Chapter math already clean/shared: `AudiobookChapters` (`shared/.../audiobook/AudiobookChapters.kt:26,29,120-144`); chapters from `VersionChapter` (`CatalogModels.kt:200`).
- `AudiobookPlayerViewModel` (`android-shared/.../common/player/AudiobookPlayerViewModel.kt:78`) mirrors a `MediaController`, polls position at 4 Hz.
- `ContinuumPlaybackService : MediaSessionService` (`:40`) owns one ExoPlayer **shared with video**; built in `ContinuumPlayerFactory.createPlayer()` (`:74`).
- Sleep timer Off/Minutes/EndOfChapter/EndOfBook implemented (`applySleepTimer:539`, `maybeFireSleepBoundary:586`), tested. Speed clamp 0.5–3.0 (`:487`); skip intervals (`AudiobookSettingsStore.kt:104`).
- Bookmarks: `AudiobookBookmark` (`:12`) + local JSON store. Progress sync: local store every 5s, resume `max(local, server)`, push via `AudiobookProgressSyncer`.

**Gaps (load-bearing):**
1. **No multi-file timeline** — plays ONE file/version, chapters as spans on it (`AudiobookPlayerViewModel.kt:186-205,869`); a book split across files can't play continuously.
2. **Background listening broken** — `onTaskRemoved` stops/clears on swipe-away "because Continuum is a video player" (`ContinuumPlaybackService.kt:172-184`); `MediaController` bound to the Compose screen → leaving calls `pause();stop();clearMediaItems();release()` (`AudiobookPlayerScreen.kt:87-94`). No mini-player/detached session.
3. **Skip-silence/normalization/boost are dead code** — persisted (`AudiobookSettingsStore.kt:73-95`) but never consumed (chain has only `DelayAudioProcessor`, `ContinuumPlayerFactory.kt:100-104`).
4. **No streaming cache** — re-streamed audio re-fetched.
5. **Bookmarks local-only** (no server endpoint; ebooks have one).
6. **Sync position-only, open-time-only** — no per-book speed/finished/bookmark sync; `max`-position can clobber a legit rewind.
7. **Prefs per-profile not per-book**; speed cap 3.0×; no notification custom actions; no Auto/Wear tree.

### Concrete adoptable patterns

| Idea | Source location | Where in silo | Effort | License | A7 caveat |
|---|---|---|---|---|---|
| One-MediaItem-per-chapter playlist from clipped/concatenated segments → true multi-file timeline | lissen `PlaybackService.kt:303`, `resolveChapterToFiles:255`, `LissenMediaSourceFactory.kt:62` | new `AudiobookMediaItemBuilder` → `ContinuumPlayerFactory`; VM stops collapsing to one `selectedFileId` (`:186`) | L | GPLv3 — reimplement | `ConcatenatingMediaSource2`/`ClippingMediaSource` fine on API24 |
| Centralized absolute↔(chapter,offset) math + `CHAPTER_START_MS` | lissen `CalculateChapterIndexAndPosition.kt:20`, `PlaybackSynchronizationService.kt:175` | extend `AudiobookChapters.kt:36` with reverse-map + media-index helpers | S | GPLv3 pattern | pure Kotlin |
| Dual cache: streaming `SimpleCache`+`CacheDataSource` separate from public downloads | lissen `MediaModule.kt:41`, `LissenDataSourceFactory.kt:43`, `LissenMediaProvider:70` | new `AudiobookStreamCache`; resolver tries `OfflineMediaResolver` first else cached HTTP | M | GPLv3 pattern | cache under `externalCacheDir`, never `Music/Silo` |
| Sleep-timer fade + auto-rewind + shake-to-reset | Voice `SleepTimerImpl.kt:75,108,116`, `PlayerController.pauseWithRewind:147` | extend `applySleepTimer:539`; add `ShakeDetector` (androidMain) | M | Voice GPLv3; Seismic Apache-2.0 (direct dep OK) | `SensorManager` since API1; gate on availability |
| End-of-chapter via scheduled cue points (vs poll) | Voice `VoicePlayer.registerChapterMarkCallbacks:306` | replace `maybeFireSleepBoundary:586` once chapters are real MediaItems | S | GPLv3 pattern | `PlayerMessage` API24 |
| Per-book audio prefs (speed/gain/skip-silence on the book) | Voice `BookContent.kt:14,26`, `VoicePlayer.setBook:289` | per-book override layer over `AudiobookSettingsStore` | M | GPLv3 pattern | n/a |
| Wire skip-silence + volume gain | Voice `VoicePlayer:353`, `misc/VolumeGain.kt` (`LoudnessEnhancer`) | consume `AudiobookSettingsStore.kt:73-95`; set `skipSilenceEnabled`; attach `LoudnessEnhancer` | S–M | GPLv3 pattern | `LoudnessEnhancer` API19+; wrap in try/catch |
| Notification next/prev → interval; in-app → chapter skip | Voice `VoicePlayer.getAvailableCommands:109`, `LibrarySessionCallback:56` | `ContinuumPlaybackService` command/button config; **gate by media type** | M | GPLv3 pattern | Media3 commands API24 |
| Keep audiobooks playing on swipe-away/off-screen | Voice (never stops on task removal); lissen `START_STICKY` | branch `onTaskRemoved:172` by media type; detach `MediaController` from Compose (`AudiobookPlayerScreen.kt:87`) into app-scoped holder + mini-player | M–L | own code | FG-service mediaPlayback type API34+; on A7 keep session alive |
| Chapter-boundary-aware progress sync (tighten near boundaries; `timeListened` delta; finished-state) | lissen `PlaybackSynchronizationService.kt:69-136` | extend `AudiobookProgressSyncer` | S–M | GPLv3 pattern | n/a |
| Server-synced bookmarks (reuse ebook annotation pattern) | silo ebook `EbookReaderRepository.kt:30`; audiobookshelf-app | `AudiobookBookmarksStore:13` → repository + sync; note-edit in sheet | M | own code / server | n/a |

### What NOT to borrow / risks

- Don't collapse the audiobook player into a music/now-playing UX — keep the book metaphor (chapter list w/ per-chapter progress, whole-book scrub, chapter-aware skip).
- **Don't let the streaming cache replace public downloads** — two stores: ephemeral `SimpleCache` (`externalCacheDir`, evictable) vs user-facing `DownloadStorage` (`Music/Silo`, `DownloadStorage.kt:420,596`); `OfflineMediaResolver` stays the authoritative "downloaded?" check.
- Don't apply Voice's notification prev/next remap globally — silo's service is shared with video; gate by media type.
- Don't adopt lissen's per-chapter `ConcatenatingMediaSource2` for the video path; keep `ContinuumPlayerFactory` buffer/processor config branched by media type.
- License: Voice/lissen GPLv3 (AGPL-compatible) — reimplement, don't paste. Seismic Apache-2.0 (direct dep OK).
- A7: implement the background fix with an API-level branch (`START_STICKY` + persistent `MediaSession` on API24).

## Video Player

silo is **not greenfield** here — it already ships a `VideoPlaybackBackend` contract with both Media3 and MPV implementations, a route-capability matrix, a buffer-preset enum, a real `MediaCodecList` probe, and a Compose TV remote key mapper. The references are best used as a **maturity checklist** that exposes wiring gaps and two selection bugs.

### Best-in-class references & why

| Ref | License | Why |
|---|---|---|
| **jellyfin-androidtv** `playback/` | GPLv2 | Cleanest backend-behind-contract: `PlayerBackend` interface, `BackendService.switchBackend()` hot-swap. Target shape for silo's backend. |
| **findroid** `player/` | GPLv3 | Canonical `dev.jdtech.mpv:libmpv` integration — the *same* dep silo uses; `MPVPlayer extends BasePlayer`. |
| **AFinity** `player/mpv/` | GPLv3 | Richest libass styling surface (`sub-ass-override`, `sub-border-style`, `sub-color`, `sub-font-size`). minSdk 35 — MPV not proven on A7. |
| **Wholphin** `services/PlayerFactory.kt` | GPLv2 | Runtime backend choice as a factory + libass-aware ExoPlayer path (`AssRenderersFactory`) — libass *without* MPV. |
| **jellyfin-android** `TrackSelectionHelper.kt` | GPLv2 | Delivery-method-aware track selection (EMBED/EXTERNAL/ENCODE). |
| **streamyfin** `PlaybackService.ts` | MPL 2.0 | Compact remote-event→command map + explicit buffer config (checklist only). |

### What they do well

- **jellyfin-androidtv contract** (`playback/core/.../backend/PlayerBackend.kt:16`): backend-agnostic interface (`supportsStream`, `setSurfaceView`, `setSubtitleView`, `getPositionInfo`, transport). `BackendService.switchBackend()` (`:20`) detaches old backend, re-attaches surface+subtitle views, rewires listener. Buffer is **polled** via `PositionInfo` (`buffer = exoPlayer.bufferedPosition`, `ExoPlayerBackend.kt:330`); presets a data class (`ExoPlayerOptions.kt:7`) → `DefaultLoadControl.setBufferDurationsMs` (`:113`). Resume at 3s play / 15s pause (`PlaybackController.java:59`), server-driven seek over WebSocket (`PlaySessionSocketService.kt:37`).
- **findroid/AFinity MPV-behind-`BasePlayer`:** wrap libmpv as `BasePlayer` so the app keeps talking Media3; player choice a boolean pref at VM init; `hwdec=mediacodec`; map `TrackSelectionOverride`→mpv `aid/sid/vid`; external subs via `sub-add`. AFinity `PlayerViewModel.kt:566-613` surfaces libass styling as settings.
- **Wholphin libass on ExoPlayer** (`PlayerFactory.kt:108`): `AssHandler(renderType)` with version-gated `AssRenderType` (Canvas ≤P / OpenGL Q+) — ASS/SSA without MPV's native risk.

### Silo current state

silo is ahead on every axis except UI surfacing and two bugs.
- **Backend contract — DONE:** `backend/VideoPlaybackBackend.kt:12`; `Media3VideoPlaybackBackend` + `MpvVideoPlaybackBackend`; factory + `VideoPlaybackBackendSelector.kt:6` Auto policy (TRANSCODE→Media3, hard-container→MPV, styled-subs→MPV).
- **Capability metadata — DONE but not surfaced:** `VideoBackendCapabilities.kt` carries `subtitleRendering`, `supportsHardContainers`, `displayName`. **Gap:** `video/VideoPlayerUiState.Ready:25` has no field for backend kind/displayName/subtitle-rendering → UI can't show "libass (MPV)" vs "Media3 text". Headline ask; wiring gap.
- **MPV backend — DONE, real native libs:** `dev.jdtech.mpv:libmpv:1.0.0`; `libmpv.so` + ffmpeg packaged for **armeabi-v7a** (the 32-bit A7 ABI). `MpvPlayer.kt` complete `BasePlayer` (cache→buffering, libass via fontconfig, buffered position from `demuxer-cache-time`, auth headers). Auth headers pulled with `runBlocking` (`ContinuumPlayerFactory.kt:179`) — latent ANR.
- **Buffer presets — DONE, partial:** `PlaybackBufferPolicy.kt` defines modes, but factory **hardcodes** `forMode(SmoothPlayback)` (`:137`); MPV `bufferSizeMb=64` independent; nothing renders buffer reporting.
- **Capability detection — the "hardcoded codec set" concern is largely fixed:** `MediaCodecCapabilitiesProbe.kt` does a real per-codec sweep; only `platformSoftwareDecodableAudioMimes` (`:197`) remains static, used by the `evaluateTracks` fast-path — can disagree with the real probe on odd devices. Minor.
- **Presets force-disable text — REAL bug:** `TrackSelectionPresets.kt:69` (TV) & `:108` (phone) unconditionally `setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)`, and `preferredTextLanguage` is accepted but never applied. Kills subtitles on the Media3 path on every audio-caps change.
- **TV remote — DONE** (`TvPlayerRemoteKeyAction.kt`, `onPreviewKeyEvent`), but **no explicit FF/REW / press-and-hold scrub** mapping (relies on 10s/30s seek).
- **External playback — DONE** (`DownloadExternalOpen.kt:10`). **Resume — coarser** (~10s cadence; no server→client seek channel).

### Concrete adoptable patterns

| Idea | Source location | Where in silo | Effort | License | A7 caveat |
|---|---|---|---|---|---|
| Surface backendKind/displayName/subtitleRendering on player UI state | jellyfin-androidtv `PlayerState.kt:83` | add fields to `VideoPlayerUiState.Ready` (`:25`) from `backend.capabilities` | S | concept | none |
| Fix unconditional text disable + apply `preferredTextLanguage` | jellyfin-android `TrackSelectionUtils.kt:13` | `TrackSelectionPresets.kt:69` & `:108` | S | GPLv2 — reimplement | none |
| `switchBackend()` hot-swap Media3↔MPV on preflight fallback | jellyfin-androidtv `BackendService.kt:20` | new method on backend coordinator | M | GPLv2 — pattern | MPV attach/detach in `MpvPlayer.kt:1202` |
| Buffer preset as user pref honored by both backends | jellyfin-androidtv `ExoPlayerOptions.kt:7`; streamyfin | wire `PlaybackBufferMode` setting; consume `ContinuumPlayerFactory.kt:137` + MPV builder | M | GPLv2/MPL — pattern | lower ceilings on low-RAM A7 TVs |
| libass styling settings driving MPV `sub-*` | AFinity `PlayerViewModel.kt:566-613` | settings → `MpvPlayer.setOption` (`:1309`) | M | GPLv3 — reimplement | MPV-only; A7 MPV unproven |
| libass on the Media3 backend (no MPV) | Wholphin `PlayerFactory.kt:108` | new renderers/extractors in `createExoPlayer` | L | GPLv2 — pattern; check libass lib license | Canvas overlay ≤API28; safest A7 sub upgrade |
| Delivery-method-aware track switching | jellyfin-android `TrackSelectionHelper.kt:43,105,151` | `VideoTrackSelectionCoordinator` + `PlaybackSessionManager.changeAudio` | M | GPLv2 — pattern | none |
| Explicit FF/REW + press-and-hold scrub | jellyfin-androidtv `CustomPlaybackOverlayFragment.java:453` | `TvPlayerRemoteKeyAction.kt:19` add media keys | S | GPLv2 — pattern | none |
| Tighter resume (3s/15s) + server→client seek listener | jellyfin-androidtv `PlaybackController.java:59`, `PlaySessionSocketService.kt:37` | `PlaybackSessionManager.reportProgress` (`:71`) | S–M | GPLv2 — pattern | none |
| Move `runBlocking` auth fetch off player-build thread | (silo-internal) | `ContinuumPlayerFactory.kt:179` | S | n/a | avoids ANR on slower A7 |

### What NOT to borrow / risks

- **Don't make MPV the default / sole path on TV/A7.** Both MPV references run minSdk 28/35 — neither validates libmpv on API24/armeabi-v7a (silo packages the `.so`, but "builds" ≠ "decodes reliably on a 2017 ARMv7 box"). Keep MPV opt-in/Auto-only (as `VideoPlaybackBackendSelector` already does); keep abiFilters tight (APK size).
- **Don't destabilize the Media3 path during the reading phase.** Highest-value, lowest-risk S items (text-disable fix, capability surfacing, FF/REW, off-thread auth) are all Media3-side — land those first; defer libass-on-Media3 (L) and MPV styling (M).
- **Don't copy GPLv2 code** (jellyfin-androidtv/android, Wholphin) into AGPLv3 silo — reference only; reimplement. Only `dev.jdtech.mpv:libmpv` (binary dep) and a GPLv3+ libass lib are linkable.
- Keep MPV's event-driven `STATE_BUFFERING` (push) rather than poll-only buffer; use `getBufferedPosition()` only for the scrub cushion.
- Route the `evaluateTracks` audio check through the real `MediaCodecList` probe rather than the static mime set on exotic devices.

---

# Survey Sections (gaps only)

## Watch-Together (survey)

**Silo current state.** Time-sync = NTP-midpoint from one ping/pong, last-writer-wins (`RoomSyncEngine.kt:53-61`); single `serverTimeOffsetMs` (`:47`). Scheduling computes `localExecuteDelayMs`, falls back to apply-immediately when no offset yet (`:91-95`); corrective seek only when drift > 350ms (`:97-103`). Server-authoritative; seek host-only, play/pause gated (`RoomTransportAuthority.kt:22-28`); snapshot carries a server anchor (`WatchTogetherModels.kt:146-147`) the engine never uses. Dedupe on single `lastCommandId` (`:50,82,88`); no `issued_at` ordering. Reconnect backoff `[500,1k,2k,5k]`; terminal only on `room_closed` with non-null reason (`WatchTogetherRepository.kt:277-284`); catch-all `Throwable` → reconnect forever (`:295-297`). Buffering send exists but no local "hold until group ready" gate.

**Best reference approach.** jellyfin-android delegates SyncPlay to bundled jellyfin-web JS; jellyfin-androidtv has none; streamyfin's two-way-sync is unrelated offline reconciliation. The real reference is **jellyfin-web SyncPlay** (`src/components/syncPlay/core/`): TimeSyncCore keeps a **rolling sample window** and picks the **min-RTT** sample (continuous re-measure); commands carry an absolute server `When`, scheduled via offset, with continuous drift correction (small → rate nudge/micro-seek, large → hard seek); **WAITING** state — server doesn't Unpause until all members report Ready (leader blocks on the slowest); on reconnect the client requests fresh group state + re-runs time-sync before resuming; commands applied in `When` order, older-than-last dropped.

**Concrete gaps worth closing:**
- Stale-replay rewind via single `lastCommandId` — track last-applied `issued_at`/monotonic seq, drop non-newer. **S** — *branch-review bug.*
- `room_closed` null reason → infinite reconnect — treat any `room_closed` as terminal. **S** — *branch-review bug.*
- Catch-all `Throwable` reconnects forever — cap attempts/time; don't retry auth/room-gone. **S** — *branch-review bug.*
- No re-join/re-anchor on reconnect — fetch fresh snapshot + re-measure offset before honoring commands; use the unused server anchor. **M.**
- Single-sample, last-writer clock offset — rolling window + min-RTT/median. **M.**
- No continuous drift correction between commands — periodic tick vs server anchor, micro-seek/rate-nudge vs hard-seek. **M.**
- Buffering not enforced locally — local WAITING gate withholding `setPlaying` until group-ready (server must also block Unpause — **L** if missing). **M.**
- Apply-immediately fallback before first pong can jump on join — withhold non-seek transitions until an offset sample exists. **S.**

## Notifications (survey)

**Silo current state.** REST-source-of-truth inbox + realtime accelerator: pure `applyEvent` fold (`NotificationsRepository.kt:58`); capped-backoff reconnect (`:227`); optimistic `markRead`/`markAllRead` with revert (`:169,177`); `reset()` on profile switch (`:212`). Realtime client mints ws-ticket, decodes frames (`NotificationsRealtimeClient.kt:65`). **silo is far ahead of every reference here** (streamyfin = expo-push/badge only; jellyfin clients = in-memory app alerts; Campfire = server config) — only correctness gaps:
- Unsynchronized `_state` RMW — fold all mutations through `_state.update{}` (atomic pattern); derive rows/unread from `_state`. **S** — *branch-review bug.*
- No terminal on persistent auth failure — `connectRealtime` loops forever; `Closed("ticket_error_401")` folds as no-op. Stop/long-backoff on 401/403; expose a disconnected state. **M** — *branch-review bug.*
- Backoff not reset on clean close — reset on successful *connect* (hello/subscribed), not on traffic. **S** — *branch-review bug.*
- Blank `createdAt` row never marked read — `readAt = createdAt` leaves `isRead` false (`NotificationModels.kt:105`); use a sentinel timestamp or `readPending` flag. **S** — *branch-review bug.*
- `sync()` API wired but unused (`NotificationsApi.kt:40`) — wire into `refresh`/foreground catch-up or drop the dead endpoint. **S.**

## Requests (survey)

**Silo current state.** `RequestsRepository.kt` exposes status/discover/search/detail/create/cancel + singleton `_mine` (`:18`, `upsertMine:73`); ViewModels in `RequestsViewModels.kt`. Only **streamyfin** (Jellyseerr) covers requests — a model for *breadth* (admin approve/decline, season-level requests, list filter/sort/paginate, per-user clear on logout/403) but **no** optimistic/gating/cancel patterns.
- No `loadGeneration` gating → stale overwrite — `refreshMine` (`:378`) and search write without a generation guard. Adopt the `AdminUsersViewModel.kt:34` pattern. **M** — *branch-review bug.*
- Singleton `_mine` not cleared on logout/switch → cross-user leak — add `reset()` (contrast `NotificationsRepository.reset()`), invoke from the profile-switch path. **M** — *branch-review bug.*
- Discover-section pagination unused — `totalPages` returned but only first page loaded (`:83`); wire load-more or accept. **S.**
- No admin approve/decline path — `RequestDecisionBody` model exists (`RequestModels.kt:200`) but unused; add if silo-server supports it, else drop. **M, optional.**

## Admin (survey)

**Silo current state.** `AdminRepository.kt` stateless pass-through: stats, user CRUD, session list + control, app/audit logs, library scan. ViewModels: `AdminUsersViewModel` (generation-gated, `:34`), `AdminUserEditViewModel` (`:53`), `AdminStatsViewModel`. **No reference app covers admin** — silo is the only one with real CRUD + session control + logs. Correctness only:
- Cleared library-ids field revokes all access — `update` always sends `libraryIds = parseLibraryIds(text)` (`:137`); blank → `emptyList()` (`AdminUserForm.kt:48`), *sent* (not omitted) → server revokes all. Send `null` when blank, or distinguish cleared vs empty. **S** — *branch-review bug.* (Quota fields are safe — `parseQuota` returns null.)
- `deleteUser` — current code removes from list **only on success** (`:59`), no optimistic resurrect path; the review's "resurrect" premise appears stale for this revision. **No change unless an optimistic variant returns** — confirm.
- `AdminUserEditViewModel.load` idempotent guard (`loaded` flag, `:60`) can't switch targets within one instance — correct only if always freshly scoped per navigation; else add id-aware reload. **S.**
- No optimistic enable/disable quick toggle — low priority, not correctness. **S, optional.**

---

## Provenance Ledger

Per the roadmap's AGPL lineage-tracking requirement. Update as items land.

| Idea borrowed | Source app | Source license | Mode |
|---|---|---|---|
| Reflow locator (element anchor + offset map), 3-tier settings, document model, reading-time | readest | AGPL-3.0 | reimplement |
| Compose reader shell, settings DSL, debounced persistence, nested TOC, font registry | book-story | GPL-3.0 | reimplement (attribute if copied) |
| Hyphenation/margin/CSS-tweak typography tables | koreader | AGPL-3.0 | data/UX |
| MOBI→EPUB via libmobi; FB2 fixup | LibreraReader / libmobi | GPL-3.0 / LGPL-3.0 | reimplement; libmobi as separate `.so` |
| Comic Viewer abstraction, config/nav split, preloading, transitions | mihon | Apache-2.0 | reimplement + attribute |
| Fit/zoom modes, tap grid, RAM-gated decode | Kotatsu | GPL-3.0 | reimplement |
| Double-page, smaller tap zones, rotation guard | komikku | Apache-2.0 | reimplement |
| PDF memory budget, LRU/pool, OOM recovery, fit modes, thumbnails | document-viewer, LibreraReader, koreader | GPL-3.0 / AGPL-3.0 | reimplement |
| Audiobook timeline/MediaSource, dual cache, boundary sync | lissen-android | GPL-3.0 | reimplement |
| Sleep timer fade/shake, per-book audio, chapter cue points | Voice | GPL-3.0 | reimplement (Seismic Apache-2.0 direct dep) |
| Video backend contract, switchBackend, buffer presets, resume cadence, track selection | jellyfin-androidtv, jellyfin-android | **GPL-2.0-only** | **reference only — reimplement** |
| MPV-behind-BasePlayer, libass styling | findroid, AFinity | GPL-3.0 | reimplement (libmpv binary dep already used) |
| libass-on-ExoPlayer, factory backend choice | Wholphin | **GPL-2.0-only** | **reference only — reimplement** |
| SyncPlay time-sync/WAITING/drift correction | jellyfin-web | GPL-2.0-only | reference only — reimplement |

## Next Step

This is a draft for your review. After you approve it, the agreed next step is to turn the
**P0 quick-wins + the Reading-phase P1 items** into an implementation plan via the
writing-plans skill (the P0 correctness items also close several confirmed branch-review
bugs and should land first regardless of phase).
