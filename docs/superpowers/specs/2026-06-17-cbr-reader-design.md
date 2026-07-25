# CBR (RAR comic) in-app reader — design

**Goal:** Render CBR (RAR-compressed comic) archives in-app, identical UX to CBZ, closing the comic half of the "all server formats in-app" gap. Client-side only; no server change.

**Status:** Approved at the decision level (scope: CBR now; MOBI/AZW/AZW3 a separate later server track). This spec covers **CBR only**.

## Background

prairie-server serves 9 ebook formats. The Android reader already renders epub, pdf, cbz, fb2, fbz in-app. Four are routed to `External` (download + "open in another app"): **cbr, mobi, azw, azw3**. CBR is the only one of the four that is a pure image-sequence comic — it is CBZ with a RAR container instead of ZIP. Everything the comic engine needs (page list, per-page image bytes, natural-order sort, page progress) already exists; only the container reader differs.

Deep-dived by Claude + Codex (independent convergence): keep the per-format reader model; add CBR client-side via libarchive; do **not** add junrar (RAR4-only, no RAR5, nonstandard UnRAR license).

## Library

Add `me.zhanghai.android.libarchive:library` — Apache-2.0 Android packaging of libarchive (BSD). Reads RAR including RAR5; same archive stack Mihon ships. Native (JNI) — acceptable: the app already bundles FFmpeg native libs with per-ABI splits (`androidApp/build.gradle.kts:106-122`), so the packaging/size infra exists. Pin the version in `gradle/libs.versions.toml` and add it to `androidApp` only (the comic reader lives in `:androidApp`).

## The core problem: random access

`ComicReader` today (`ComicReader.kt`) holds one `java.util.zip.ZipFile` for the reader's lifetime and, per page, calls `zip.getInputStream(entry)` **twice** — pass 1 decodes bounds for downsampling, pass 2 decodes the bitmap (`decodeComicPageBitmap`, `ComicReader.kt:296`). The `HorizontalPager` also jumps to arbitrary pages (restore-progress, tap-to-skip). This is a **random-access, multi-open** access pattern.

ZIP gives cheap random access. RAR does not: libarchive reads an archive as a forward stream; seeking to entry N means reading through entries 0..N-1, and solid/RAR5 archives make per-entry re-open O(n). The two-pass decode would double that. So we cannot back a RAR with a naive "re-seek per `open()`".

### Chosen approach: a `ComicArchive` abstraction; RAR extracts once on open

Introduce a small archive abstraction so the engine is container-agnostic:

```kotlin
internal interface ComicArchive {
    val pages: List<ComicArchivePage>          // natural-sorted image entries
    fun open(entryName: String): InputStream   // fresh stream, callable repeatedly, any order
    fun close()
}
```

- **`ZipComicArchive`** — wraps `ZipFile`; `open` = `zip.getInputStream(zip.getEntry(name))`. Behavior identical to today (no temp files, no extra disk).
- **`LibarchiveComicArchive`** — on construction, performs **one** forward pass with libarchive, extracting each image entry to a private temp dir (`<cacheDir>/readers/comic/<sha1(url)>/`); `pages` is the natural-sorted list; `open` = `File(extractDir, safeName).inputStream()`. `close()` deletes the temp dir. This converts RAR's sequential model into random-access files once, so the existing two-pass decode and pager-jump work unchanged and fast.

Why extract-on-open rather than stream-per-entry: it matches the engine's existing random-access + two-pass-decode model exactly, isolates all RAR-specific quirks (solid archives, RAR5, ordering) to a single linear pass, and keeps page rendering as fast as plain files. Cost: transient disk equal to the decompressed images and an upfront extraction delay shown via the existing loading spinner. CBR files are typically tens of MB; acceptable. (Stream-per-entry would re-scan the archive up to twice per page — rejected.)

Path-safety: sanitize/flatten entry names when materializing extracted files (strip `..`, leading `/`, collapse separators) to prevent traversal outside the extract dir; the natural-sort/page-list still uses the original archive entry name for ordering.

## Touch points

1. **`shared/.../model/ebook/EbookVersionSelection.kt:75/81`** — move `"cbr"` from the `ExternalOnly` branch into the `InApp` branch (alongside `cbz`). `mobi/azw/azw3` stay `ExternalOnly`.
2. **`android-shared/.../common/ebook/ReaderEnginePolicy.kt:36/41`** — move `BookFormat.Cbr` into the `ComicManga` branch (with `Cbz`); drop it from the external branch.
3. **`android-shared/.../common/ebook/ReaderControls.kt:44`** — add `BookFormat.Cbr` to the `BookFormat.Cbz` case in `ReaderCapabilities.forFormat` (same comic capabilities). *(This is the duplicated format→engine mapping Codex flagged. For this spec we keep both call sites and add Cbr to each; a follow-up may consolidate `forFormat` onto `readerEnginePolicyFor`. Out of scope here to keep the change tight.)*
4. **`androidApp/.../reader/ComicReader.kt`** — extract the `ComicArchive` interface + the two impls; replace the `OpenedComicArchive`/`ZipFile` internals so `produceState` builds a `ComicArchive` chosen by container, `ComicPage`/`decodeComicPageBitmap` take a `ComicArchive` instead of `ZipFile`, and dispose calls `archive.close()`. Page-listing (`listComicArchivePages`, `naturalPageComparator`) is reused for both — refactor it to operate on `List<String>` entry names so it is container-agnostic.
5. **`ComicReader.kt:73`** — pass the real container extension to `resolveReaderFile` (currently hardcoded `"cbz"`) so the cache file is named correctly for CBR; thread the format/extension from `ReaderUiState.format` through `ReaderEngineHost` into `ComicReader`.
6. **`gradle/libs.versions.toml` + `androidApp/build.gradle.kts`** — add the libarchive dependency.

## Error handling

- Corrupt/non-RAR/non-ZIP file → existing `ComicArchiveLoadResult.Error` path ("Could not open this comic archive.").
- Empty archive (no images) → existing `ComicArchiveLoadResult.Empty` path.
- Encrypted RAR (password) → libarchive surfaces an error on the extract pass → `Error`. (No password UI; out of scope.)
- Extraction failure mid-pass (disk full, IO) → `Error`; partial temp dir cleaned on `close`/dispose.

## Testing

Unit (JVM/Robolectric, `androidUnitTest`):
- Page listing + natural sort over an entry-name list (container-agnostic) — extend existing `listComicArchivePages` tests.
- `ZipComicArchive`: build a tiny CBZ in a temp file → `pages` count/order, `open` yields decodable bytes.
- `LibarchiveComicArchive`: bundle small RAR4 **and** RAR5 fixtures (3–4 tiny images each) → `pages` count/order matches, `open` yields the right bytes; corrupt and empty fixtures → `Error`/`Empty`; entry-name traversal fixture (`../evil.png`) stays inside the extract dir.
- Routing: `EbookVersionSelection` maps a `.cbr` version to `InApp` + `ComicManga`; `ReaderCapabilities.forFormat(Cbr)` == comic caps; `readerEnginePolicyFor(Cbr, InApp)` == `ComicManga`.

Device validation (per project rule for user-facing flows): open a real CBR (RAR4 and a RAR5) from the live server `jim/Amsterdam123!`, page through, verify ordering + progress restore, verify offline-downloaded CBR opens in-app.

## Out of scope

- MOBI/AZW/AZW3 (separate later spec: server-side convert → EPUB, preferring a lighter Go/libmobi converter over Calibre).
- Consolidating the two format→engine mappings (follow-up).
- Comic fit-mode/zoom v2 (already a tracked TODO in `ComicReader.kt:237`).
- 7z/tar comic containers (libarchive could do them later; server doesn't ingest them).
