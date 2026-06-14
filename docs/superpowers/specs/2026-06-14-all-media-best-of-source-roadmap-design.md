# All Media Best-Of-Source Roadmap Design

**Date:** 2026-06-14
**Status:** Direction approved. Written as the bridge from architecture to implementation planning.
**Branch:** `feature/production-playback-architecture`
**Related specs:** `2026-06-14-best-of-breed-client-architecture-design.md`, `2026-06-13-reflowable-reader-engine-design.md`, `2026-06-09-premium-reader-subsystem-design.md`

## Decision

Silo should become a unified media client that feels specialized in each media class. We will borrow the best product behavior and engineering patterns from the source apps in `/Users/jimcole/source`, but keep one Silo-native app shell, one server model, one public-download philosophy, and one capability-driven navigation model.

The implementation order is:

1. Define the full all-media roadmap now, so Video, Audio, Reading, Requests, Search, Downloads, and Home fit together.
2. Execute Reading first, because ebooks, comics, and manga are the largest quality gap.
3. Preserve current video stability while Reading work happens.
4. Follow with audiobook polish, then music.

Android TV supports video, music, and audiobooks. Android TV must not expose ebooks, comics, manga, or a Reading tab.

Android 7 support remains a hard compatibility constraint. Any borrowed dependency or engine must either support that floor or be isolated behind a capability gate with a fallback.

## Current Reality

Silo already has important foundations:

- Capability-driven mobile tabs: Video, Audio, Reading, hidden when the server/profile lacks those libraries.
- TV excludes Reading surfaces.
- Public, original-format downloads with private sidecars.
- Mobile ebook/detail/download routing.
- A Silo-native reader dispatcher:
  - EPUB, FB2/FBZ, TXT, Markdown through the new WebView-based reflow engine.
  - PDF through Android `PdfRenderer`.
  - CBZ through ZIP image paging.
  - MOBI, AZW, AZW3, and CBR as external-reader original files.
- Audiobook playback on mobile and TV with shared player state, chapters, bookmarks, speed, sleep timer, resume, progress sync, and offline resolution.
- Requests on mobile and TV, with TV filtering ebooks.
- A shared video playback backend boundary with Media3 now and MPV-capable architecture later.

This is not yet enough to claim "best of every source." The current reader is Silo-native, not Book Story, Readest, Kotatsu, Komikku, or Seeneva. Music is recognized as an Audio-class future surface, but not yet a Finamp-grade music client.

## Borrowing Policy

We will borrow deliberately:

- Borrow product expectations, interaction patterns, edge cases, and tested architecture ideas.
- Keep code provenance explicit when source code is reused.
- Prefer Silo-native interfaces around borrowed ideas so the app does not become a patchwork of embedded apps.
- Do not fork the app shell around any single source project.
- Do not add broad rewrites unless they directly serve the current phase.

The project is AGPLv3, so strong copyleft source projects are not a product blocker. We still track what ideas or code came from where, because future maintenance depends on knowing the lineage.

## Source-App Targets

### Ebooks

Primary references:

- `/Users/jimcole/source/book-story`
- `/Users/jimcole/source/readest`
- `/Users/jimcole/source/JellyBook`

Features to absorb:

- Premium content-first reader UI.
- In-reader typography: font family, size, line height, margins, theme, brightness.
- TOC and chapter navigation.
- Bookmarks, highlights, notes, and annotations.
- Search inside book text.
- Stable locator-based resume.
- Reading-time estimates.
- Per-book display settings with sensible global defaults.
- Graceful external open for formats better handled by dedicated readers.

Silo direction:

- Keep the Silo-native `ReaderScreen`/`ReaderViewModel` ownership.
- Build a premium reader shell that all reading engines use.
- Keep reflowable formats under one WebView/pagination engine.
- Treat PDF as a fixed-document engine, not a text-reflow engine.
- Treat unsupported native formats as first-class originals that open externally.

### Comics And Manga

Primary references:

- `/Users/jimcole/source/Kotatsu`
- `/Users/jimcole/source/komikku`
- `/Users/jimcole/source/seeneva-reader-android`

Features to absorb:

- RTL and LTR reading direction.
- Webtoon/vertical mode.
- Page fit: width, height, screen, original size where useful.
- Single-page and future double-page/spread modes.
- Aggressive adjacent-page preloading.
- Chapter/issue navigation.
- Series-aware progress.
- Archive resilience and metadata.
- Gesture zones tuned for one-handed reading.
- Future smart features from Seeneva: panel/balloon zoom, OCR, and TTS.

Silo direction:

- Comics and manga live under mobile Reading.
- Do not expose them on TV.
- Split "comic" and "manga" behavior even if they share low-level archive/image plumbing.
- First production target is stable local/downloaded archive reading with direction, fit modes, preloading, and progress.
- Smart panel/OCR features are later; they should not block a polished reader.

### Audiobooks

Primary references:

- `/Users/jimcole/source/lissen-android`
- `/Users/jimcole/source/audiobookshelf-app`
- `/Users/jimcole/source/audiobookshelf`

Features to absorb:

- Clean audiobook-first player instead of video-player metaphors.
- Book timeline mapped across files and chapters.
- Chapter list with progress and resume context.
- Bookmarks and notes.
- Sleep timer with end-of-chapter behavior.
- Speed, skip intervals, and per-book playback preferences.
- Cache-aware streaming for poor connections.
- Background playback and notification polish.
- Cross-device progress sync.

Silo direction:

- Keep audiobooks under Audio.
- Mobile gets the richer management/player surface.
- TV gets a remote-friendly listening surface.
- Share low-level audio session/cache infrastructure with Music where useful, but do not collapse audiobook UX into music UX.

### Music

Primary reference:

- `/Users/jimcole/source/finamp`

Features to absorb:

- Artists, albums, songs, playlists, genres, and search.
- Queue and now-playing screens.
- Shuffle, repeat, previous/next, and seek.
- Gapless playback.
- ReplayGain/volume normalization when server metadata supports it.
- Lyrics where available.
- Offline music downloads in original files.
- Background playback and media notification/session polish.
- Android Auto later.

Silo direction:

- Music lives under Audio beside Audiobooks.
- Mobile gets the full music client.
- TV gets browse/play/queue/now-playing with simple playlist and album navigation.
- Music is not part of the first Reading phase, but the roadmap reserves the Audio model so we do not design Reading in a way that blocks it.

### Video

Primary references:

- `/Users/jimcole/source/jellyfin-androidtv`
- `/Users/jimcole/source/streamyfin`
- `/Users/jimcole/source/Wholphin`

Features to preserve or absorb:

- Stable mobile and TV playback.
- Backend boundary for Media3 now and MPV later.
- Correct subtitle rendering and track selection.
- Resume reliability.
- Intelligent buffering.
- Professional TV controls and remote ergonomics.
- External playback for downloaded originals.

Silo direction:

- Do not destabilize video during Reading phase.
- Continue backend-boundary work in targeted slices only.
- Keep video under Video, not under a generic "media file" surface.

### Requests

Features to preserve or absorb:

- Mobile can request movies, TV, audiobooks, and ebooks.
- TV can request movies, TV, and audiobooks.
- TV cannot request ebooks.
- Search results should make media type obvious.
- Request state should be visible and cancelable where server support exists.

Silo direction:

- Requests stay shared-model, device-specific UI.
- Reading-first work should not regress request filters.

## Product Architecture

### App Shell

The app shell presents capability-driven top-level areas:

- `Video`: movies, TV, and other video libraries.
- `Audio`: audiobooks and music.
- `Reading`: ebooks, comics, and manga on mobile only.
- `Downloads`: visible when local downloads exist or download management is relevant.
- `Search`: global search across available media classes.
- `Requests`: requestable media based on device capability and server support.

Empty classes are hidden. TV always hides Reading.

### Reading Phase Architecture

Reading is the first implementation phase. It should be split into five bounded components:

1. `ReaderShell`
   - Immersive content-first layout.
   - Auto-hiding chrome.
   - Tap zones, swipe, back behavior, and overlay controls.
   - Shared sheets for TOC, bookmarks, highlights, search, and settings.

2. `ReflowReaderEngine`
   - EPUB, FB2/FBZ, TXT, Markdown.
   - WebView pagination, locators, themes, typography, search, and highlights.

3. `FixedDocumentEngine`
   - PDF.
   - Page rendering, zoom/pan, fit modes, page thumbnails later, page-based locators.

4. `ComicMangaEngine`
   - CBZ first, with the interface ready for CBR/CB7/CBT if archive support is selected.
   - LTR/RTL, webtoon mode, fit modes, preloading, progress, and chapter navigation.

5. `ExternalReadingTarget`
   - MOBI, AZW, AZW3, CBR until native archive support exists, plus recognized original formats that lack an in-app engine.
   - Opens the public original with Android intents.
   - Never traps the user inside Silo when another app can read the file better.

### Audio Phase Architecture

After Reading, Audio becomes two product tracks sharing low-level infrastructure:

- `AudiobookCore`: book timelines, chapters, speed, sleep timer, bookmarks, progress sync.
- `MusicCore`: queue, albums, artists, playlists, lyrics, gapless, ReplayGain, shuffle/repeat.

They may share a playback service, media session, notification primitives, streaming cache, and local-file resolver. Their UI, progress model, and controls remain distinct.

## Data Flow

### Reading Start

1. User opens an ebook, comic, or manga from detail, search, downloads, or continue reading.
2. Silo resolves the source through one reader file resolver: public download, `file://`, `content://`, server-relative API path, or authenticated absolute URL.
3. `ReaderShell` asks the engine selector for the correct reading engine.
4. The engine emits location/progress events.
5. Local state saves immediately.
6. Server sync is debounced and non-blocking.
7. Unsupported native formats offer external open using the original downloaded file.

### Audiobook Start

1. User opens an audiobook from Audio, detail, search, requests, downloads, or continue listening.
2. Silo resolves book timeline, file/chapter mapping, and local/offline availability.
3. Playback starts from the requested or resumed book position.
4. Controls operate in audiobook terms: chapter, speed, skip, bookmark, sleep timer.
5. Progress saves locally and syncs when possible.

### Music Start

1. User opens song, album, artist, playlist, or queue.
2. Silo builds a stable queue.
3. Playback starts through the shared audio playback infrastructure.
4. Controls operate in music terms: previous/next, queue, shuffle, repeat, lyrics, seek.
5. Progress/reporting and downloads use music-specific rules, not audiobook rules.

## Error Handling

- Reader parse failures show a readable error with retry, manage download, and open externally when available.
- Reflow WebView crashes recreate the WebView and restore the latest locator.
- PDF renderer failures close native resources safely and show a page-level error instead of crashing.
- Comic archive failures show archive-specific errors and do not delete the original file.
- Missing completed downloads are flagged and can be retried; they do not count as ready.
- Audiobook timeline mapping failures preserve book progress and identify the unavailable file/chapter.
- Music queue failures preserve current playback where possible.
- Streaming cache failures degrade to direct streaming.
- Request failures show server-provided status when available and avoid duplicate submissions.

## Testing Strategy

### Automated

- Capability tests: mobile shows Reading only when ebooks/comics/manga exist; TV never shows Reading.
- Reader engine selection by format.
- Reflow locator round-trips and legacy progress fallback.
- Reader file resolver for public, local, content, server-relative, and authenticated URLs.
- PDF renderer lifecycle safety.
- Comic archive page sorting, decode failure handling, and direction mapping.
- External open MIME and intent target creation.
- Audiobook timeline mapping, resume, chapters, sleep timer, speed, and bookmark behavior.
- Music queue reducers once Music starts.
- Request media-type filters on mobile and TV.

### Device QA

Reading phase must be verified on real or emulator devices with:

- EPUB: open, resume, TOC, settings, search, bookmark, highlight/note when available.
- FB2/FBZ/TXT/Markdown: open, page, resume, settings.
- PDF: open, page, zoom/fit, resume.
- CBZ: open, page, direction, fit, resume.
- Manga sample: RTL and webtoon behavior.
- External-only samples: MOBI/AZW/AZW3/CBR open through another app after download.
- Offline mode: downloaded originals still open; progress sync queues and flushes later.

Audiobook and request QA remain required before claiming full production completion.

## Phase Plan

### Phase 1: Reading Foundation

- Replace the current reader chrome with a premium immersive `ReaderShell`.
- Keep current reflow engine as the starting point.
- Add a formal reader engine selector and capability model.
- Preserve public original downloads and external open.
- Add tests that lock TV out of Reading.

### Phase 2: Ebooks

- Finish reflowable reader ergonomics.
- Add typography, brightness, search, TOC polish, bookmarks, and highlights/notes.
- Improve EPUB metadata/TOC handling.
- Keep MOBI/AZW/AZW3 external unless a reliable native parser is selected.

### Phase 3: Comics And Manga

- Build comic/manga mode on shared archive/page infrastructure.
- Add direction, webtoon, fit modes, preloading, chapter navigation, and progress.
- Decide whether to add CBR/CB7/CBT archive libraries natively or keep them external.

### Phase 4: Audiobook Polish

- Upgrade audiobook UI/UX against Lissen and Audiobookshelf expectations.
- Harden book timeline, chapters, bookmarks/notes, sleep behavior, notification, and cache-aware streaming.
- Verify mobile and TV separately.

### Phase 5: Music

- Build Finamp-inspired music browsing and playback.
- Add queue, artists, albums, playlists, shuffle/repeat, lyrics, ReplayGain, downloads, and background playback.
- Add leanback TV music.

### Phase 6: Integrated QA

- Run all-media flows against a real Silo server:
  - Continue Watching, Continue Listening, Continue Reading.
  - Search across Video, Audio, and Reading.
  - Requests for the allowed media types per device.
  - Offline downloads and external app open for every completed original.
  - Resume/progress sync after app restart and offline/online transitions.

## Non-Goals For The Reading-First Pass

- No Reading surfaces on TV.
- No music implementation inside the Reading phase.
- No large video rewrite while reader work is underway.
- No forced native MOBI/AZW/AZW3 parser unless a reliable library is selected and tested.
- No Seeneva-style OCR/panel detection in the first comic/manga pass.
- No server-side request rewrite unless client work exposes a concrete API gap.

## Approval Gate

This spec authorizes the next step: write an implementation plan for Phase 1, Reading Foundation, while keeping the all-media roadmap visible. Implementation should not start by touching music, audiobook, or video internals unless a small shared boundary is required by the reader work.
