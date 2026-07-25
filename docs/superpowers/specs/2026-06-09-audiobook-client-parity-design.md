# Audiobook Client Parity Design

## Context

Android mobile and Android TV already have audiobook foundations: mobile has a dedicated Media3 audiobook player with offline fallback, local resume, bookmarks, speed controls, sleep timer, and chapters; TV treats audiobooks as playable media through the standard TV detail and player path. Download storage now preserves original downloaded files in public Android collections instead of private `.bin` files.

This design finishes the audiobook parity pass by closing the remaining client gaps: external app access for downloaded audiobooks, richer mobile detail metadata from the current Prairie server contract, and focused hardening around version/offline behavior.

## Goals

- Let completed downloaded audiobooks open in other Android audio/audiobook apps.
- Preserve original audiobook file formats and MIME types for all download/open flows.
- Surface richer audiobook metadata on Android mobile when the server provides it.
- Keep Android TV audiobook playback visible and ebook content hidden.
- Add focused tests around MIME mapping, download open targets, storage routing, and media type filters.

## Non-Goals

- Rewriting the existing mobile audiobook player.
- Changing server contracts.
- Adding Android TV offline downloads.
- Adding ebook support to Android TV.
- Building server-side bookmark/progress sync beyond the existing playback-session calls.

## Architecture

Keep the existing audiobook architecture and improve the edges around it:

- Mobile detail remains in `AudiobookDetailContent`.
- Mobile playback remains in `AudiobookPlayerScreen` and `AudiobookPlayerViewModel`.
- Offline playback continues through `OfflineMediaResolver`.
- Download rows continue through the shared mobile downloads UI.
- `DownloadOpenTarget` becomes media-aware enough for audiobook/audio originals instead of defaulting to ebook-shaped names and MIME handling.
- TV continues using the standard TV detail/player path for audiobooks.

The download rule is explicit: completed audiobooks are public MediaStore audio entries in their original file format. Prairie may play them internally, but other apps must be able to discover and open them through Android's normal content URI flow.

## Download And Open-With Behavior

Completed audiobook downloads expose `Open with` when a real local URI is available. The action builds an `Intent.ACTION_VIEW` target from the download's content URI, display name, and detected MIME type.

MIME detection must cover common audiobook and audio originals:

- `m4b` -> `audio/mp4`
- `m4a` -> `audio/mp4`
- `mp3` -> `audio/mpeg`
- `aac` -> `audio/aac`
- `flac` -> `audio/flac`
- `ogg` -> `audio/ogg`
- `opus` -> `audio/opus`
- `wav` -> `audio/wav`

Unknown extensions must fall back to Android's `MimeTypeMap`, then `application/octet-stream` only as a final fallback.

If no external app can handle the target, the mobile app shows a short toast and does not crash. Incomplete downloads, missing local URIs, or unresolved files do not show `Open with`.

## Mobile Detail Behavior

`AudiobookDetailContent` must use the existing `AudiobookMetadata` fields when present:

- authors
- narrators
- publisher
- total duration
- series
- other narrations
- related content

The layout stays practical: cover/title/people/duration near the top, a clear play action, the overview when present, a chapter list for the selected playable version, then optional metadata/related sections when the server supplies data. The screen must not show empty labels or placeholder rails.

The selected version remains the source of truth for chapter availability and playback. If no playable version exists, the play action is disabled and reads `Unavailable`.

## Offline And Version Behavior

Mobile playback resolves a completed local audiobook first when the requested content/file exists. If the app is offline but a completed sidecar and file are available, the player must still show usable title, cover, file identity, and playback URL from local data.

When detail is available, detail metadata can enrich the player and detail page. When detail is unavailable, local sidecar data is sufficient to play the completed download.

Requested file IDs must be respected. If the user opens a specific downloaded audiobook file, the player must not silently switch to a different file unless the open was non-explicit and fallback behavior is already allowed.

## Android TV Behavior

TV remains audiobook-positive and ebook-negative:

- Audiobooks remain visible in libraries, search filters, detail pages, and playback.
- Ebooks stay hidden from TV browsing surfaces.
- TV detail metadata must continue to label audiobooks correctly and avoid movie/series-only sections that do not fit audiobook content.

The implementation should only change TV code if a concrete parity gap is found while writing tests or wiring shared helpers.

## Error Handling

- Completed download without a local URI: hide `Open with`.
- External app resolution failure: show a toast.
- Missing audiobook version: disable play with `Unavailable`.
- Missing chapters: omit the chapter section.
- Unknown MIME: use the best available Android MIME fallback before `application/octet-stream`.
- Offline server failure with a completed local audiobook: continue local playback.

## Testing

Add or update focused tests for:

- Audio MIME mapping in `DownloadOpenTarget`.
- Audiobook `DownloadOpenTarget` creation for completed downloads.
- No open target for incomplete or URI-less downloads.
- Public download storage routing audiobook media into the audio collection.
- TV media type filters preserving audiobooks and hiding ebooks.

After implementation, verify at minimum:

- `./gradlew :android-shared:testDebugUnitTest`
- `./gradlew :androidApp:compileDebugKotlinAndroid`
- `./gradlew :androidTvApp:testDebugUnitTest`
- `./gradlew :androidTvApp:compileDebugKotlinAndroid`
