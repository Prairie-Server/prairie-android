# Reader Production Polish Design

## Goal

Make the mobile reader honest and resilient:

- EPUB, PDF, and CBZ are in-app readable formats.
- CBR, MOBI, AZW/AZW3, FB2/FBZ, Markdown, and unknown formats remain downloadable/openable by other apps, but are not advertised as in-app readable.
- A broken or unsupported CBZ archive shows an in-reader error state instead of crashing or leaving the user on a spinner.

Android TV remains ebook-free.

## Current State

The shared version-selection rules already do the important platform split:

- `isInAppReadableEbookFormat()` returns true only for EPUB, PDF, and CBZ.
- `chooseEbookVersion()` ignores CBR and other external-only formats.
- `ReaderCapabilities.forFormat(BookFormat.Cbr)` is external-only.

The remaining mismatch is in the UI layer: `ReaderScreen` still dispatches both CBZ and CBR to `ComicReader`, and `ComicReader` contains a visible CBR placeholder. That can make the app look like CBR is a half-supported reader format, even though the product decision is external-only.

CBZ loading also needs hardening. `ComicReader` resolves the archive file, then opens the zip and image entries directly during composition. Invalid ZIPs, unreadable entries, or empty archives should become explicit reader errors.

## Product Behavior

### Format Truth

Mobile detail/read actions should continue to expose in-app `Read` only for EPUB, PDF, and CBZ. CBR and other external-only originals should still download in their original format and expose `Open with` from Downloads.

If a CBR somehow reaches `ReaderScreen` through a stale route or manually constructed URL, the screen should show an unsupported-format message and not call `ComicReader`.

### CBZ Reader

CBZ should:

- load local, content, and remote file URLs through the existing resolver path;
- show a loading indicator while resolving the archive;
- show a clear error when the archive cannot be opened;
- show a clear error when no supported page images are present;
- keep reporting page count and page changes for valid archives.

Supported page images remain JPG, JPEG, PNG, WEBP, and GIF. Page order remains lexicographic by archive entry name.

## Technical Design

Create a small Android reader helper near `ComicReader` to make CBZ archive inspection testable:

- `ComicArchivePage` stores the zip entry name and display index.
- `ComicArchiveLoadResult` is `Loaded`, `Empty`, or `Error`.
- `loadComicArchivePages(file)` opens the zip, filters image entries, sorts by name, and catches archive errors.

`ComicReader` should use this helper from `produceState`. It should render:

- loading while the file or page list is unresolved;
- an error message for `Error`;
- a no-images message for `Empty`;
- `HorizontalPager` only for `Loaded`.

`ReaderScreen` should dispatch only `BookFormat.Cbz` to `ComicReader`. `BookFormat.Cbr` should fall through to the external-only unsupported message.

## Testing

Add Android unit tests for:

- valid CBZ image entry sorting;
- empty CBZ returning `Empty`;
- invalid archive returning `Error`;
- CBR remaining external-only in shared/version capability tests.

Run the full shared/mobile/TV verification suite after implementation.

## Out Of Scope

- Adding RAR/CBR rendering.
- Adding ebooks to Android TV.
- Rewriting EPUB/PDF renderers.
- Changing server reader APIs.
- Moving download/open-with behavior; external apps remain the path for external-only formats.
