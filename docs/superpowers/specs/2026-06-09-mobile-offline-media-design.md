# Mobile Offline Audiobook and Ebook Design

## Context

Android mobile now supports server-backed audiobook playback and ebook reading through current Prairie server contracts. Existing download infrastructure already stores completed media bytes under `DownloadStorage` as `<filesDir>/downloads/<serverId>/<profileId>/<fileId>.bin` with sidecar metadata. The video player already uses those sidecars for offline-first playback.

This design adds offline mobile playback/reading for completed audiobook and ebook downloads. It does not change download enqueue behavior.

## Goals

- Play downloaded audiobooks on Android mobile without network access.
- Read downloaded ebooks on Android mobile without network access.
- Prefer local completed downloads when present for the requested content/file.
- Save progress locally immediately and sync to the server on a best-effort basis when online.
- Keep offline behavior scoped to Android mobile.

## Non-Goals

- Android TV offline read/play changes.
- New download formats or download queue behavior.
- Full offline annotation conflict resolution.
- Server changes.

## Architecture

Add a small Android mobile offline resolver around `DownloadStorage`. The resolver looks up completed sidecars by `contentId`, prefers a requested `fileId`, confirms local bytes exist, and returns a local `file://` URL plus sidecar metadata.

Audiobook and ebook view models use this resolver before server-backed streaming:

- Audiobooks use local `file://` playback and skip server playback sessions when a completed download exists.
- Ebooks use local `file://` reading and continue local progress/bookmark persistence even when server sync fails.

The resolver should be Android-only because it depends on `DownloadStorage` and local filesystem paths.

## Audiobook Flow

1. `AudiobookPlayerViewModel` loads local resume position first.
2. It fetches detail when available, but attempts local download lookup by `contentId` and requested `fileId`.
3. If a completed local file exists, it sets `streamUrl = file://...`, fills title/artwork from detail or sidecar, uses local position/bookmarks, and does not create a server playback session.
4. If no local file exists, it keeps the current server playback session path.
5. Local playback continues to save local position. Server progress reporting is skipped for local-only playback.

## Ebook Flow

1. `ReaderViewModel` loads detail when available, but chooses the readable version from downloaded sidecars if detail is unavailable.
2. If a completed local file exists, it sets `fileUrl = file://...`, chooses format from version metadata or sidecar filename/container clues, and renders the existing reader.
3. Reader progress saves locally immediately. Server progress save remains best-effort when online.
4. Bookmarks are stored locally immediately. Server bookmark creation remains best-effort when online.
5. If no local file exists, the current server-backed ebook read/progress/bookmark behavior remains.

## Error Handling

- A completed sidecar without local bytes is ignored.
- If a requested `fileId` is not downloaded but another completed file for the content is downloaded, use the requested file only when it exists; otherwise fall back to another local file for non-explicit opens.
- If no local file and server detail/read fails, show the existing unavailable/error state.
- Offline progress/bookmark local writes must not block reading.

## Testing

Add focused unit tests for the offline resolver:

- Finds a completed sidecar and local file by `contentId`.
- Prefers requested `fileId`.
- Ignores missing bytes or non-completed downloads.
- Falls back to another completed file only when no requested file was explicit.

Compile Android mobile after wiring:

- `./gradlew :androidApp:compileDebugKotlinAndroid`
- Existing shared/TV verification should remain green.
