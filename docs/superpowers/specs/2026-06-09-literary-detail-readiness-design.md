# Literary Detail Readiness Design

## Goal

Make mobile item detail pages production-ready for ebooks, comics, manga, and audiobooks, while preserving the platform contract:

- Android mobile can show and open literary detail pages.
- Android TV must not expose ebooks, comics, manga, or Reading.
- Android TV can continue to expose audiobooks under Audio.

This pass focuses on completing the actions and version-awareness of the existing literary detail screens. It does not redesign the entire detail system, and it does not depend on the server-side ebook+audiobook work-linking contract.

## Current State

The app already has:

- `ItemDetailScreen` dispatching `audiobook` to `AudiobookDetailContent`.
- `ItemDetailScreen` dispatching `book`, `ebook`, `comic`, and `manga` to `BookDetailContent`.
- Dedicated routes for `Route.AudiobookPlayer` and `Route.BookReader`.
- Dedicated audiobook player and ebook reader screens.
- Existing download infrastructure that queues single-file downloads and stamps sidecars with `DownloadMediaType.Audiobook` or `DownloadMediaType.Ebook` from `ItemDetail.type`.
- Download storage preserving original file names and containers.

The visible gap is that literary detail pages expose read/play actions but not a complete detail-page action set. Both `AudiobookDetailContent` and `BookDetailContent` already accept an optional download callback, but neither renders a real download action. Book detail also chooses the first supported in-app readable version internally and only lists versions as passive text, so users cannot intentionally pick EPUB/PDF/CBZ when multiple versions exist.

## Product Behavior

### Audiobook Detail

Audiobook detail should present:

- cover, title, author, narrator, duration, publisher, series, related content, and chapter list as it does today;
- a primary `Play` action that opens the audiobook player with the selected or first file;
- a secondary `Download` action when a file version is available;
- disabled/unavailable copy when no playable file exists.

The download action should queue the same file the `Play` action would use. If a download is already queued/downloading, the existing `ItemDetailViewModel.onDownloadTapped()` behavior cancels it. If completed, tapping remains a no-op for now, consistent with video detail behavior where completed downloads are managed from Downloads.

### Book/Ebook/Comic/Manga Detail

Book detail should present:

- cover, title, author, format, page count, publisher, series, overview, and related author items as it does today;
- a format/version selector when multiple ebook versions are present;
- a primary `Read` action for in-app readable formats only: EPUB, PDF, and CBZ;
- a secondary `Download` action for any selected supported ebook/comic/manga file version;
- clear copy when the selected version can be downloaded but cannot be read in-app.

The selected version should drive both the format badge and the Read/Download actions. Unsupported or externally-oriented formats such as MOBI, AZW, AZW3, FB2, CBR, and Markdown should not claim they can be read in-app; users can download those originals and open them from Downloads or another app.

### Download State

The detail screen should derive per-file download state from existing `DownloadRecord`s:

- queued/downloading: show progress where practical and route taps through existing cancel behavior;
- completed: show downloaded state;
- missing/failed/cancelled: allow queueing.

This pass should reuse the existing detail-level download action logic. It should not create a new download queue path.

### TV Scope

No Android TV ebook detail route or Reading surface should be added. Shared model/helper changes must preserve the existing tests that exclude ebook-like libraries from TV visible modes while keeping audiobooks as TV Audio.

## Data Flow

`ItemDetailViewModel` remains the detail owner:

1. Load `ItemDetail`.
2. Expose `downloads` as it does today.
3. Provide `downloadRecordFor(version)` and `onDownloadTapped(version, detail.title)`.

`ItemDetailScreen` remains the dispatcher:

1. Select the effective audiobook file from explicit or last-file selection.
2. Select the effective book file through user-selected version state.
3. Pass download state and callbacks into the literary detail content.

`BookDetailContent` should become stateless with respect to selected version. It receives the selected version/index and selection callback from `ItemDetailScreen`, rather than hiding version choice in `remember(detail.versions)`.

## Error Handling

- If no versions exist, literary detail pages should keep metadata visible and show unavailable action copy.
- If an ebook version is supported for download but not in-app reading, the Read button is disabled and helper copy tells the user to download/open it externally.
- Download queue failures can continue to be handled by existing repository/enqueuer behavior; this pass does not add toast/snackbar plumbing.

## Testing

Add focused shared tests for ebook version selection if existing coverage is missing:

- requested readable file id wins;
- unsupported requested file id falls back to preferred readable format;
- in-app readable detection distinguishes EPUB/PDF/CBZ from external-only formats.

Run the existing shared, Android mobile, and Android TV verification suite after implementation.

## Out Of Scope

- Server-side ebook+audiobook work linking.
- Unified work detail pages.
- New external app picker from detail pages.
- New TV ebook surfaces.
- Reworking the audiobook player or ebook reader.
- Changing download storage paths or MediaStore/public-file behavior.
