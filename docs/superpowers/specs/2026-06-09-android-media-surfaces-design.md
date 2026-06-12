# Android Audiobook and Ebook Media Surfaces Design

## Context

The deployed server at `root@silo-new:/opt/silo-server` now exposes first-class audiobook and ebook catalog contracts. Android currently has partial audiobook and book scaffolding, but several assumptions are stale: old metadata expects fields such as `file_url`, while the server now exposes media through `versions`, `audiobook`, `ebook`, and dedicated ebook reader endpoints.

This design adds audiobooks to Android mobile and Android TV, adds ebooks to Android mobile, and hides ebooks from Android TV.

## Goals

- Align shared Android catalog models with the deployed server contract for `audiobook` and `ebook`.
- Support audiobook detail and playback on Android mobile and Android TV.
- Support ebook detail and reading on Android mobile with server-synced reader state.
- Hide ebooks from Android TV library, search, row, and detail surfaces.
- Avoid using stale metadata fields such as `file_url` for audiobook or ebook playback/reading.

## Non-Goals

- Full web-reader parity for Android mobile ebooks in the first implementation.
- Ebook support on Android TV.
- Server changes.
- Broad redesign of mobile or TV navigation outside the media-type work needed here.

## Server Contract

Catalog item details can include:

- `type = "audiobook"` with `audiobook` and `versions`.
- `type = "ebook"` with `ebook` and `versions`.

The audiobook extension includes authors, narrators, publisher, total duration, series, other narrations, and related content. The ebook extension includes authors, publisher, series, and related content.

The ebook reader API is available under `/api/v1/ebooks`:

- `GET` and `HEAD /{content_id}/files/{file_id}/read`
- `GET` and `PUT /{content_id}/progress`
- `GET` and `PUT /{content_id}/reader-config`
- `GET`, `POST`, `PATCH`, and `DELETE /{content_id}/annotations`

Reader progress saves `file_id`, `location`, and `progress`. Bookmarks are represented as annotations with `kind = "bookmark"` and a reader location.

## Architecture

The shared Android layer becomes the contract boundary for the new server shapes. It should add `ItemDetail.ebook`, refresh audiobook metadata, and introduce ebook reader request/response models and client calls.

Android mobile and Android TV then consume the same shared catalog contract with different product behavior:

- Mobile supports both audiobooks and ebooks.
- TV supports audiobooks.
- TV hides ebooks defensively from all user-facing media surfaces.

Both mobile and TV should use `versions` as the source of playable or readable files. Audiobooks should use the existing playback session flow with a selected `file_id`. Mobile ebooks should stream files through the ebook reader endpoint and sync reader state through the ebook APIs.

## Components

### Shared Catalog Contract

Update shared models so Android can deserialize and use:

- `ItemDetail.ebook`
- the current audiobook extension
- the current ebook extension
- person, series, narration, and related-item helper models

Add small helpers where useful so UI code can ask for common display values without duplicating contract knowledge, such as primary author names, narrator names, total audiobook duration, and supported ebook versions.

### Shared Ebook Reader Client

Add a focused client around the server ebook reader APIs:

- Build the read URL for a content/file pair.
- Load and save ebook progress.
- Load and save reader config when needed.
- List, create, update, and delete annotations, with bookmarks as the v1 required use case.

The client should preserve normal authentication behavior through the existing Android API stack.

### Mobile Audiobook Surface

Adapt the existing mobile audiobook detail and player path to the new server data:

- Render authors, narrators, publisher, total duration, series, and related content where the existing screen structure supports it.
- Select an audio `FileVersion` for playback.
- Start playback through the existing playback session API using `file_id`.
- Continue using existing playback progress behavior.

No mobile audiobook path should depend on `audiobook.file_url`.

### Mobile Ebook Surface

Add mobile ebook detail and reader behavior:

- Route ebook details to a read action instead of video playback.
- Choose a readable version, preferring a requested `file_id`, then EPUB or PDF, then another supported ebook format.
- Stream the selected file through `/api/v1/ebooks/{content_id}/files/{file_id}/read`.
- Restore matching server progress on open.
- Save progress while reading.
- Support server-backed bookmarks.
- Show a clear unsupported state for formats the Android reader cannot open.

The v1 reader scope is readable stream, progress restore/save, bookmarks, and graceful unsupported-format handling. Full highlight/note annotation UI, TTS, advanced settings, and web-reader parity can follow later.

### TV Audiobook Surface

Make TV media handling audiobook-aware:

- Label audiobook libraries and items correctly.
- Show audiobook metadata in TV detail where it fits the existing TV detail structure.
- Start audiobook playback from `versions` through the existing playback session path.
- Preserve normal TV playback error behavior when a session or playable file is unavailable.

### TV Ebook Suppression

Hide ebooks from Android TV:

- Exclude ebook libraries and ebook items from TV library surfaces.
- Exclude ebooks from TV search results and rails.
- Prevent TV detail navigation from presenting ebook details.
- If stale cached data or a deep link reaches an ebook detail on TV, fail closed with an unsupported or back-navigation state.

## Data Flow

### Audiobooks

1. Catalog detail loads an `ItemDetail` with `type = "audiobook"`, `audiobook`, and `versions`.
2. UI renders audiobook extension metadata.
3. Play chooses an audio `FileVersion`.
4. Android starts playback through the existing playback session API using the chosen `file_id`.
5. Progress continues through the normal playback/progress pipeline.

### Mobile Ebooks

1. Catalog detail loads an `ItemDetail` with `type = "ebook"`, `ebook`, and `versions`.
2. Reader chooses a supported file version.
3. Reader streams bytes from the ebook read endpoint.
4. Reader loads saved progress and resumes when the saved `file_id` matches the chosen file.
5. Reader saves `{ file_id, location, progress }` while reading.
6. Reader creates bookmarks as server annotations with `kind = "bookmark"`.
7. Reader config can be stored as a JSON object if the initial reader exposes safe preferences.

### TV Ebooks

TV filters out `type = "ebook"` before display. If an ebook reaches a TV route unexpectedly, the route should not expose a reader or playable action.

## Error Handling

- Audiobooks with no playable versions show an unavailable or disabled play state.
- Missing audiobook durations fall back from version duration to extension total duration when possible.
- Audiobook playback session failures use the existing playback error path.
- Mobile ebooks with no supported readable versions show an unsupported-format state.
- Saved ebook progress for a missing or different file is ignored until the next save replaces it.
- Ebook progress, config, or bookmark sync failures do not block reading.
- Ebook read authorization or file errors show an unavailable state.
- TV ebook filtering is defensive across libraries, search, rows, and detail navigation.

## Testing

Add focused tests for:

- Shared JSON deserialization of current audiobook and ebook detail payloads.
- Ebook reader API request and response models.
- Ebook file-version selection.
- Mobile item action routing for audiobook and ebook items.
- TV ebook filtering in libraries, search/rows, and detail navigation.
- Audiobook playback file selection from `versions`.

Manual verification should cover:

- Mobile audiobook detail and playback.
- TV audiobook library/detail/playback.
- Mobile ebook detail, opening a readable file, progress restore/save, and bookmarks.
- Confirming ebooks do not appear on Android TV.

## Open Decisions

No open product decisions remain for this spec. The approved direction is contract-first implementation, mobile ebook server sync, and complete TV ebook hiding.
