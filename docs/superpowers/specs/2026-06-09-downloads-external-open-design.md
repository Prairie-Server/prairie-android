# Downloads External Open Design

## Goal

Let users open every completed Android mobile download in another capable app, including movies and episodes. Silo should not lock people into its own player or reader when the downloaded bytes are already on the device.

## Current Shape

Downloads already use the right foundation:

- `DownloadStorage` writes media bytes to public storage where possible.
- Original filenames/extensions are preserved instead of forcing `.bin`.
- Private sidecars retain Silo metadata for offline playback, reading, deletion, and grouping.
- `DownloadOpenTarget` creates safe external open targets only for completed downloads with a local URI.
- The Downloads UI already exposes `Open` for completed ebooks and audiobooks.
- Mobile offline playback/reading already prefers local files when appropriate.

The remaining product gap is video. Completed movie/episode downloads are publicly stored, but the grouped Downloads UI still treats the row tap as the main in-app action and does not expose an explicit secondary `Open` action for external video players.

## Product Behavior

Primary row tap stays in-app:

- Movies/episodes continue to route to Silo playback/detail as they do now.
- Readable ebooks continue to offer in-app `Read`.
- Audiobooks continue to work with Silo playback.

Secondary external action expands:

- Any completed download with a nonblank local URI shows `Open`.
- This includes videos, audiobooks, and ebooks.
- Incomplete, queued, downloading, failed, or missing-local-URI downloads do not show `Open`.
- The `Open` action uses Android's chooser with an `ACTION_VIEW` intent and read permission.
- If Android cannot find a target app or cannot open the URI, show the existing short toast failure.

## MIME And Naming

`DownloadOpenTarget` should continue to derive the MIME type from the preserved display name first, then the container as fallback.

Add explicit MIME coverage for common video containers:

- `mp4` -> `video/mp4`
- `m4v` -> `video/mp4`
- `mkv` -> `video/x-matroska`
- `webm` -> `video/webm`
- `avi` -> `video/x-msvideo`
- `mov` -> `video/quicktime`
- `ts` -> `video/mp2t`

Existing ebook and audiobook mappings stay unchanged. Unknown formats still fall back through `MimeTypeMap`, then `application/octet-stream`.

Fallback names should keep the original container:

- A completed video with no display name but container `mkv` should open as `download.mkv`.
- A completed video with display name `Film.Final.mkv` should keep that display name.

## UI Scope

Modify the grouped Downloads rows, not the older unused `DownloadItemRow` unless compilation requires it.

In `DownloadEntryRows.kt`:

- Replace the current `canOpenExternal` condition so it is media-type agnostic:
  - complete
  - nonblank `localUri`
- Keep the `Read` button for in-app readable ebooks.
- Keep the `Open` text button as the secondary action.
- Preserve row sizing, delete behavior, section grouping, and progress display.

No TV UI changes are required in this slice.

## Error Handling

- Do not render `Open` for incomplete or failed rows.
- Do not render `Open` when `localUri` is blank.
- Keep chooser/toast behavior centralized in `DownloadsScreen.openDownloadExternally`.
- For file URIs, continue to wrap via `FileProvider` before sending the intent.
- For content URIs, send the content URI directly with read permission.

## Testing

Add focused unit coverage in `DownloadOpenTargetTest`:

- Video MIME mappings for `mp4`, `m4v`, `mkv`, `webm`, `avi`, `mov`, and `ts`.
- Fallback name for a completed video with no display name preserves the container.
- Open target remains unavailable for incomplete downloads and missing local URIs.

Compile/check:

```bash
./gradlew :android-shared:testDebugUnitTest --tests com.continuum.app.common.downloads.DownloadOpenTargetTest
./gradlew :androidApp:compileDebugKotlinAndroid
```

Final verification:

```bash
git diff --check
./gradlew :android-shared:testDebugUnitTest :androidApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest :androidTvApp:compileDebugKotlinAndroid
git status --short --branch
```

## Out Of Scope

- Changing download storage location.
- Changing primary row tap behavior.
- Reworking offline playback or reader logic.
- Adding TV Downloads UI.
- Server changes.
