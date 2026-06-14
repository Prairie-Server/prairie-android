# Best-Of-Breed Client Architecture Design

**Date:** 2026-06-14
**Status:** Direction approved. Written spec pending user review before implementation planning.
**Branch:** `feature/production-playback-architecture`

## Decision

Silo should stay Silo: one product shell, one server integration model, one navigation model, and one download philosophy. We should not fork or rebuild around any single reference app.

Instead, Silo adopts a best-of-breed client architecture:

- Video playback follows the backend-boundary path already started in this branch, with Media3 now and MPV/libass-class capability later.
- Audiobook playback becomes a dedicated audiobook experience, not a thin audio player: chapters, sleep timer, per-book settings, cache-aware streaming, and multi-file chapter mapping.
- Reading becomes a premium mobile-only reader system with separate engines for reflowable books, fixed documents, and comics/manga.
- Downloads remain public, discoverable, original-format files that other apps can open.
- Android TV supports video and audiobooks, but ebooks, comics, and manga remain out of TV.

The reference apps are used as product and engineering sources of truth for behavior, but Silo owns the final interfaces and user experience.

## Product Goals

- Make Silo feel like a finished media client instead of a collection of feature checkboxes.
- Use specialized engines under a unified app shell rather than forcing one player or reader to handle every media type.
- Keep mobile and TV experiences tailored to their devices while sharing infrastructure wherever it lowers risk.
- Make hard playback cases boring: subtitles display, resume works, buffering is intelligent, external players can open downloaded media, and unsupported formats fail gracefully.
- Build toward video, audio, and reading hubs that hide empty media classes automatically.

## Non-Goals

- Do not replace the Silo Android apps with a fork of AFinity, Jellyfin, KOReader, Librera, Readest, Mihon, Voice, or Lissen.
- Do not put ebooks, comics, or manga on Android TV.
- Do not move completed downloads back into app-private storage.
- Do not treat the current Media3 player as the final answer for every video format.
- Do not force audiobook behavior into the video playback backend.
- Do not make one giant implementation pass that rewrites video, audio, reading, downloads, and navigation together.

## Reference Priorities

### Video

Primary references:

- `/Users/jimcole/source/jellyfin-androidtv`
- `/Users/jimcole/source/AFinity`
- `/Users/jimcole/source/Wholphin`
- `/Users/jimcole/source/findroid`

Best ideas to absorb:

- Jellyfin Android TV's player backend shape: concrete backends hidden behind a stable playback contract.
- AFinity and Findroid's MPV-as-player direction for hard containers/codecs/subtitles.
- Wholphin's runtime backend choice and libass-aware subtitle path.
- Jellyfin TV's buffer presets and explicit buffer reporting.

Silo direction:

- Keep the current `VideoPlaybackBackend` work as the foundation.
- Media3 remains the default backend.
- MPV becomes a later backend behind the same boundary.
- Subtitle rendering must become backend-capability-aware so the UI can show what is actually being used.
- Resume and buffer state must be verified through the backend contract, not per-screen assumptions.

### Audiobooks

Primary references:

- `/Users/jimcole/source/Voice`
- `/Users/jimcole/source/lissen-android`
- `/Users/jimcole/source/audiobookshelf-app`

Best ideas to absorb:

- Voice's audiobook-specific player behavior: chapter-aware skip, sleep timer, pause rewind, per-book speed, skip silence, and gain.
- Lissen's server-backed playback model: a whole book timeline mapped onto multiple chapter/file media items.
- Lissen's cache-aware streaming path for smoother playback without replacing completed public downloads.
- Audiobookshelf app's domain expectations around libraries, chapters, progress, and series.

Silo direction:

- Mobile gets a dedicated premium audiobook player.
- TV gets a remote-friendly audiobook player, but not every mobile management feature.
- Progress is book-level and chapter-aware.
- Playback cache is an internal streaming aid only. Completed downloads stay public and original-format.

### Ebooks And Documents

Primary references:

- `/Users/jimcole/source/readest`
- `/Users/jimcole/source/book-story`
- `/Users/jimcole/source/LibreraReader`
- `/Users/jimcole/source/document-viewer`
- `/Users/jimcole/source/android-book-reader`
- `/Users/jimcole/source/koreader`

Best ideas to absorb:

- Readest's foliate-style document model and serious multi-format thinking.
- Book's Story's Compose-native reader polish, settings, and reader-state ergonomics.
- Librera/document-viewer/android-book-reader/KOReader as format-depth references, especially for MOBI/AZW/PDF/DjVu-style decisions.

Silo direction:

- Reading stays mobile-only.
- Reflowable books use a dedicated reader engine, not separate one-off renderers per format.
- PDF/fixed documents use a fixed-page document engine path.
- Comics/manga use an image/page-sequence engine path.
- Unsupported or low-confidence formats should still offer external open when a public downloaded file exists.

### Comics And Manga

Primary references:

- `/Users/jimcole/source/mihon`
- `/Users/jimcole/source/Kotatsu`
- `/Users/jimcole/source/seeneva-reader-android`

Best ideas to absorb:

- Mihon's pager, webtoon mode, navigation zones, preloading, RTL/LTR support, and reader settings.
- Kotatsu's Android 7-friendly patterns and manga-reader ergonomics.
- Seeneva's future-facing OCR/balloon zoom ideas, kept out of the first production slice.

Silo direction:

- Comics and manga belong under Reading on mobile.
- The first production version should focus on stable local/opened CBZ-style reading, paging, preloading, fit modes, and progress.
- Advanced OCR or panel detection is a later premium feature.

### Downloads And Offline

Primary references:

- Current Silo `DownloadStorage`, `DownloadWorker`, `DownloadOpenTarget`, and `OfflineMediaResolver`
- `/Users/jimcole/source/AFinity`
- `/Users/jimcole/source/lissen-android`

Best ideas to absorb:

- Silo's current public MediaStore direction is the product rule.
- AFinity-style queue/status polish is useful for download management.
- Lissen-style cache is useful for streaming, not completed-file storage.

Silo direction:

- Completed downloads are always original-format files.
- Files are stored where other applications can discover or open them.
- Sidecars hold Silo metadata privately, but never replace the original media file.
- Streaming cache and completed downloads are separate systems.

## Architecture

Silo's media architecture should settle into four bounded subsystems:

1. `VideoPlaybackBackend`
   - Owns video engine selection, Media3/MPV backend behavior, subtitle rendering capability, buffer reporting, and mount/remount operations.
   - Shared by mobile and TV.
   - UI surfaces remain separate.

2. `AudiobookPlaybackCore`
   - Owns book timeline, chapter mapping, audiobook-specific controls, sleep timer, per-book settings, cache-aware streaming, and progress sync.
   - Shared where practical, with separate mobile and TV controls.

3. `ReaderEngine`
   - Owns mobile-only reading.
   - Has reflowable, fixed-document, and comic/manga engine families behind one shell.
   - Integrates with existing ebook progress, bookmarks, settings, and downloads.

4. `OfflineAccess`
   - Owns public completed downloads, private sidecars, open-with intents, local playback resolution, and streaming cache policies.
   - Exposes media-type-specific behavior without forcing player or reader code to know storage details.

These are not UI tabs. They are internal ownership boundaries. The app-level navigation can still present Video, Audio, and Reading hubs when those media classes exist.

## App-Level Navigation

The long-term app shell should keep the user's earlier direction:

- Show `Video` only when the profile/server has movies, TV, sports, courses, or other playable video libraries.
- Show `Audio` only when audiobooks or music exist.
- Show `Reading` only on mobile, and only when ebooks, comics, or manga exist.
- Do not show empty top-level media classes.
- Global search can span all media classes available on that device.

TV must never show Reading, even when the server has ebooks or comics.

## Data Flow

### Video Start

1. User selects a video item on mobile or TV.
2. Existing start flow builds a playback request and media spec.
3. `VideoPlaybackBackendFactory` chooses Media3 today, MPV later when the item or user preference requires it.
4. The backend mounts the media, reports capabilities, exposes player state, and owns track/subtitle operations.
5. Mobile or TV renders its own controls over the backend state.
6. Progress/resume is saved through the existing playback session path.

### Audiobook Start

1. User selects an audiobook on mobile or TV.
2. The audiobook core builds a book timeline from server chapters, file versions, and any local completed downloads.
3. Playback maps book position to file/chapter media items.
4. Streaming uses cache-aware data sources; completed downloads resolve directly to local public files.
5. Player controls operate in audiobook terms: chapter, skip, sleep timer, speed, bookmark, and book progress.

### Reading Start

1. Mobile user selects an ebook, comic, or manga item.
2. `ReaderScreen` resolves a local downloaded file or authenticated remote/local temporary file as it does today.
3. `ReaderEngine` selects a reflowable, fixed-document, or comic/manga engine.
4. The engine emits locator/progress events to the existing ebook sync path.
5. If the format is unsupported internally but the file is downloaded, Silo offers external open.

## Error Handling

- Video backend selection failure falls back to Media3 when possible and surfaces a clear player error when not.
- Unsupported subtitle rendering should be visible in diagnostics and should not pretend selection succeeded.
- Audiobook chapter/file mapping failures should preserve book-level progress and show the unavailable chapter/file clearly.
- Streaming cache failures should degrade to direct streaming, not block playback.
- Reader parse failures should show a readable error page with external-open/download options when available.
- Public download URI failures should hide open-with actions rather than crashing.
- TV filtering must treat ebooks/comics/manga as unavailable content, not as broken rows.

## Implementation Phases

### Phase 1: Finish The Video Backend Boundary

Complete the current `feature/production-playback-architecture` work:

- Mobile call sites use `VideoPlaybackBackend`.
- TV call sites use `VideoPlaybackBackend`.
- Backend capabilities are threaded into diagnostics.
- Resume, subtitle selection, audio selection, and buffer state are covered by tests.

### Phase 2: Video Hardening And MPV Planning

Add the production criteria needed before MPV:

- Backend selection policy.
- Capability labels for subtitle rendering and hard-container support.
- Test fixtures for resume, subtitle selection, sidecar subtitles, and buffer reporting.
- Clear package boundary for an eventual MPV backend.

### Phase 3: Audiobook Core Upgrade

Build the shared audiobook playback core:

- Book timeline model.
- Multi-file chapter mapping.
- Chapter-aware seek/skip behavior.
- Sleep timer and pause rewind.
- Per-book speed/skip-silence/gain settings.
- Cache-aware streaming that does not replace public downloads.

### Phase 4: Reader Engine Upgrade

Upgrade mobile reading in three lanes:

- Reflowable books: EPUB, FB2/FBZ, TXT, Markdown, and MOBI/AZW strategy.
- Fixed documents: PDF and document-like formats.
- Comics/manga: CBZ-style page sequence, fit modes, RTL/LTR, webtoon/pager modes.

This phase must preserve the rule that ebooks do not appear on TV.

### Phase 5: Offline And External App Polish

Finish user-facing download behavior across all media types:

- Open-with actions for video, audio, ebooks, and comics where Android has handlers.
- Public original filenames and MIME types.
- Better download queue/status UI.
- Clear separation between completed downloads and streaming cache.

### Phase 6: Unified Hubs And Search

Refine the app shell:

- Capability-driven Video, Audio, and Reading hubs.
- Continue Watching, Continue Listening, and Continue Reading.
- Search filters that match the media classes available on the current device.
- Empty media classes hidden without creating dead navigation.

## Testing Strategy

Each subsystem needs focused tests at the ownership boundary:

- Video: backend factory, capabilities, mount/remount, resume position, subtitle selection, audio selection, buffer reporting.
- Audiobooks: timeline mapping, multi-file chapter resolution, skip/seek semantics, sleep timer transitions, pause rewind, cache/local resolution.
- Reading: format detection, locator persistence, progress math, reader engine selection, unsupported-format fallback, TV exclusion.
- Downloads: public storage routing, original filename/MIME preservation, open-with intent targets, completed-vs-cache behavior.
- Navigation/search: device-specific media class filtering and hidden empty hubs.

Device verification remains mandatory for:

- Android mobile reader UX.
- Android TV D-pad playback controls.
- Subtitles visibly rendering on screen.
- Resume after app restart and player restart.
- Poor-network playback with buffer/cache behavior.
- External app open-with for downloaded files.

## Risks

- A single giant refactor would destabilize an already broad app. The phases must stay independently shippable.
- MPV can solve hard playback problems but adds native packaging, lifecycle, and TV surface risk.
- Foliate/Readest-style reading may be the best long-term reader path, but it needs a careful Android WebView bridge and sandboxing.
- MOBI/AZW support should be planned as a real format decision, not as ad hoc text extraction.
- Audiobook streaming cache must not blur into completed downloads, because users need files discoverable by other applications.

## Acceptance Criteria

This direction is complete when:

- Video playback has a stable backend contract and both mobile and TV use it.
- Audiobooks feel like audiobooks on mobile and TV, not generic audio files.
- Mobile Reading has real engine boundaries for reflowable books, documents, and comics/manga.
- Downloads remain public, original-format, and externally openable.
- Navigation shows only media classes that exist and are valid for the device.
- TV never exposes ebooks, comics, or manga.
- Each phase has tests that prove the boundary behavior before large UI changes land.
