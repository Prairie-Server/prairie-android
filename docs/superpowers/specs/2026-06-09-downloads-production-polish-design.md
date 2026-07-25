# Downloads Production Polish Design

## Goal

Make downloads feel like user-owned files, not app-locked cache:

- downloaded bytes keep their original file names and formats;
- completed video, audiobook, ebook, comic, manga, and unknown downloads can be opened by other apps from the Downloads screen;
- public Android storage remains the source for discoverability;
- Prairie still uses private sidecars for metadata and offline app state.

This pass hardens the current system. It does not replace the existing download worker, storage layout, or Downloads UI.

## Current State

The app already has the right broad architecture:

- `DownloadStorage` writes bytes to public storage, not only private app files.
- Android 10+ uses `MediaStorePublicDownloadStore`.
- Android 9 and below use a public `Download/Prairie` fallback.
- Original file names and containers are carried through `DownloadSidecar`.
- Downloads are grouped by media type in the Downloads tab.
- Completed rows expose `Open with` when a local URI exists.
- Downloaded ebooks with EPUB/PDF/CBZ can also open in Prairie's reader.

The remaining production gaps are mostly hardening:

- MIME type mapping is duplicated between `DownloadStorage` and `DownloadOpenTarget`.
- The mapping needs tests so future formats do not regress into `application/octet-stream`.
- Public collection behavior should be explicit and tested: video goes to Movies, audiobooks/audio goes to Music, ebooks/comics/manga/unknown go to Downloads.
- The UI should make `Open with` available for every completed file with a local URI, including video and audiobook files.
- Open-with intent construction should grant read access consistently and avoid crashing when no external app is available.

## Product Behavior

### File Ownership

Downloads must remain in their original formats. No `.bin` extension should be introduced for new downloads. If the server provides `fileName`, Prairie uses the sanitized basename. If not, Prairie falls back to `<fileId>.<container>`, and only falls back to `<fileId>.download` when no useful container exists.

### Public Discoverability

Files should be discoverable by other Android apps:

- movies, TV episodes, and other video files land under the public video collection;
- audiobooks and other audio files land under the public audio collection;
- ebooks, comics, manga, and unknown files land under public Downloads;
- sidecar metadata remains private under app files.

For Android 10+, discoverability means MediaStore entries with correct display name, MIME type, and `IS_PENDING = 0` after completion. For Android 9 and below, discoverability means real files under `Download/Prairie/...`.

### Open With

Every completed download with a local URI should show `Open with`. This includes:

- videos;
- TV episodes;
- audiobooks;
- ebooks;
- comics/manga;
- unknown file types.

For EPUB/PDF/CBZ ebooks, Prairie may also show `Read`. `Read` opens the in-app reader. `Open with` opens the Android chooser so another reader/player can handle the same original file.

Open-with should:

- use the best MIME type from file name/container;
- use `FileProvider` only for `file://` fallback files;
- use MediaStore `content://` URIs directly;
- grant read permission;
- show a short failure toast instead of crashing if no app can open the file.

## Technical Design

### Shared Android MIME Resolver

Move the MIME mapping into one Android-shared helper used by both:

- `MediaStorePublicDownloadStore` when creating MediaStore rows;
- `DownloadOpenTarget` when constructing open-with intents.

The resolver should cover at least:

- ebooks/comics: EPUB, PDF, CBZ, CBR, MOBI, AZW, AZW3, FB2, FBZ, Markdown;
- audiobooks/audio: M4B, M4A, MP3, AAC, FLAC, OGG, OPUS, WAV;
- video: MP4, M4V, MKV, WEBM, AVI, MOV, TS;
- fallback through `MimeTypeMap`;
- final fallback `application/octet-stream`.

### Public Collection Resolver

Keep `PublicDownloadCollection.forMediaType()` as the collection authority, but add focused tests around its mapping. If needed, make the enum/helper visible enough for unit tests without exposing it outside the Android-shared download package.

### UI/Open Target

Keep `DownloadOpenTarget` as the object used by the Downloads screen. It should use the shared MIME resolver and produce no target unless the item is complete and has a local URI.

The Downloads row already renders `Open with` when `item.isComplete && localUri != null`. This behavior should be preserved for every media type.

## Error Handling

- If no external app can open a file, show a toast.
- If a local URI is missing, hide `Open with`; do not show a dead button.
- If MIME detection fails, use `application/octet-stream` rather than blocking open-with.
- If the original file name contains path separators or unsafe characters, keep sanitizing it before writing to public storage.

## Testing

Add Android-shared unit tests for:

- MIME mapping from file name/container for literary, audio, and video formats.
- fallback from empty filename extension to container.
- public collection mapping by `DownloadMediaType`.
- `DownloadOpenTarget.from()` returns null for incomplete/missing URI and returns a MIME-bearing target for completed downloads.

Run the full shared/mobile/TV verification suite after implementation.

## Out Of Scope

- Changing server download APIs.
- Reworking download worker retry/resume behavior.
- Adding a custom file manager.
- Adding external-app launch buttons to detail pages.
- Moving sidecars into public storage.
- Android TV downloads UI changes, except compile/test regression protection.
