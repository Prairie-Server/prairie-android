# Mobile Ebook Upgrade Design

## Goal

Upgrade Android mobile ebooks as one coordinated batch: improve the reader experience, polish ebook library/detail entry points, and finish the offline/export story so downloaded ebooks remain user-owned files that other Android apps can discover and open.

This is mobile-only. Android TV continues to hide ebooks. Admin request work remains out of scope.

## Current Context

The Android mobile app already has a server-backed ebook foundation:

- Shared ebook metadata, reader API models, repository, and version selection.
- Mobile `BookReader` route and `ReaderViewModel`.
- EPUB, PDF, and comic reader surfaces.
- Local-first reader progress and bookmark storage with best-effort server sync.
- Public-download storage that preserves original media filenames and extensions.

This design builds on that foundation instead of replacing it.

## User Outcomes

1. A user can open an ebook from library, search, detail, or downloads and land in the right reader with clear format/version behavior.
2. A user can navigate long ebooks more comfortably through bookmarks, table-of-contents or section navigation where available, and page/location controls.
3. A user can personalize reading enough for daily use with basic reader display settings.
4. A user can find downloaded ebook files under Android public downloads and open them in another reader app without Silo lock-in.

## Scope

### Reader UX

Add a reader control layer shared by the existing mobile ebook renderers where practical:

- A controls overlay or sheet that exposes reader actions without crowding the reading surface.
- Bookmark list with jump and delete.
- Add bookmark at the current reader location.
- Table-of-contents or section list when the renderer can provide it safely.
- Page/location scrub or jump controls where the renderer exposes stable page/location data.
- Reader settings for theme, text size, and margins where a renderer supports them. Unsupported settings are hidden or disabled per format rather than faked.

Progress and bookmarks remain local-first. Server sync remains best-effort and must not block reading.

### Library and Detail Polish

Improve ebook detail and routing so ebooks feel first-class on mobile:

- Detail pages show richer ebook metadata: authors, publisher, series, format/version summary, and related content if present.
- Primary action is `Read`, not video playback.
- Reader launch chooses the best supported version, honoring a requested file id first, then the existing supported-version preference.
- Unsupported format states explain which formats can be read in-app and whether the downloaded file can be opened externally.
- Library, search, downloads, and detail entry points consistently route ebooks to `Route.BookReader`.

### Offline and Export Polish

Finish the downloaded ebook ownership path:

- Downloaded ebook rows show original filename, extension/format, and clear actions for `Read` and `Open with`.
- Public downloads keep original filenames and extensions. `.bin` is not supported and no legacy fallback is added.
- `Open with` uses Android content/file sharing mechanisms already supported by the download storage layer, granting temporary read access where needed.
- Downloads remain discoverable under the public `Downloads/Silo` area so other reader apps can find EPUB/PDF/CBZ/etc. directly.
- If a file exists locally but cannot be opened in Silo, the user can still hand it to another app.

## Non-Goals

- Android TV ebook support.
- Admin features.
- Full web-reader parity.
- Highlight/note annotation UI beyond bookmarks.
- TTS, dictionary lookup, cloud import, or advanced typography controls.
- Reintroducing private `.bin` ebook downloads or legacy migration paths.

## Architecture

### Shared Reader State

Keep renderer-specific reading mechanics in the existing reader files, but define a small common UI/state boundary for cross-format controls:

- Current location/page summary.
- Progress percent.
- Supported control capabilities for the active format.
- Bookmark list and bookmark actions.
- Reader display settings supported by the active format.

The `ReaderViewModel` remains the coordinator for loading detail, selecting the version, choosing local vs server read URL, saving progress, and syncing bookmarks.

### Renderer Capabilities

Each renderer advertises only what it can genuinely support:

- EPUB supports text-size/theme/margins. It exposes a table of contents only when the parser can provide stable entries.
- PDF supports page navigation and bookmarks by page. Theme controls appear only if the rendering path can apply them cleanly.
- Comic archives support page navigation and bookmarks by page.

The UI consumes capabilities so unsupported controls do not appear as broken promises.

### Entry Points

Use existing mobile navigation:

- `Route.BookReader(contentId, fileId)` remains the reader entry.
- Detail/read actions pass `fileId` when a specific version is selected.
- Downloads can either read in Silo or open the public file with another app.

## Data Flow

1. User opens an ebook from library/search/detail/downloads.
2. App resolves a readable version, preferring an explicit `fileId`.
3. `ReaderViewModel` tries completed local media first; otherwise it uses the server ebook read endpoint.
4. Reader initializes with local progress/bookmark state, then overlays server state if available and relevant to the same file.
5. Reader emits location/progress changes.
6. `ReaderViewModel` saves local progress immediately and syncs to the server best-effort.
7. Bookmark add/delete updates local state immediately and syncs best-effort.
8. `Open with` uses the public downloaded file when present.

## Error Handling

- No supported in-app version: show unsupported format with available external-open option if downloaded.
- Local file missing: fall back to server read endpoint when online, otherwise show offline unavailable.
- Server read failure: show unavailable state without deleting local progress/bookmarks.
- Progress/bookmark sync failure: keep local state and show a non-blocking sync warning.
- External open failure: show a message that no compatible reader app is installed or the file could not be handed off.

## Testing

Add focused tests around:

- Ebook version selection and requested `fileId` behavior.
- Reader capability mapping per format.
- Local progress/bookmark persistence and best-effort server sync failure.
- Downloads rows showing ebook format/original filename.
- Public download paths preserving original extensions.
- Reader route wiring from detail/search/library/downloads.

Run mobile compile and relevant shared/android-shared tests before completion.

## Rollout

Implement in this order:

1. Reader UX and capability model.
2. Library/detail/download entry-point polish.
3. Offline/export `Open with` and public-file verification.
4. Final verification and follow-up capture.

This keeps the reading surface stable before expanding entry points and external handoff behavior.

## Follow-Ups

- Highlight and note annotations.
- Rich EPUB table-of-contents parsing if the first pass can only provide simple sections.
- Reader typography presets beyond basic size/margins/theme.
- Better PDF reflow or crop controls.
- Cross-device conflict resolution UI for progress/bookmark sync.
