# Audiobook Player — Phase 7: Android Auto — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Make Prairie audiobooks browsable and playable from Android Auto. Convert the process-wide shared playback service from a Media3 `MediaSessionService` into a Media3 `MediaLibraryService`, expose a browsable content tree (`Continue Listening`, `Audiobook Libraries`, and — when an item is playing — its `Chapters`), and declare the automotive browse intent-filter. The video player must keep working byte-for-byte: this refactor is purely additive from video's perspective.

**Architecture:** `ContinuumPlaybackService` (in `android-shared`) owns the single `ExoPlayer` for the process and currently exposes it through a `MediaSession`. `MediaLibraryService` is a strict superset of `MediaSessionService`; its session type is `MediaLibrarySession`, which is itself a `MediaSession`. The video `PlayerScreen` connects with a plain `MediaController` against the same `ComponentName` and never calls any library API — so it is unaffected by the upgrade. The browse tree is built by a new **pure** `AudiobookBrowseTree` builder (in `shared`, `commonMain`, unit-tested) that turns `UserLibrary`/`BrowseItem`/`ProgressEntry`/`VersionChapter` data into a flat `MediaItem` parent/children model expressed as platform-agnostic data, plus a thin Android adapter (`AudiobookBrowseTreeMediaItems`, in `android-shared`) that maps that data to Media3 `MediaItem`s with the right `MediaMetadata` browsable/playable flags. A new `AudiobookBrowseRepository` (in `shared`) fetches the live data via the existing `PersonalDataRepository` (libraries + continue-listening progress), `CatalogRepository` (library contents + item detail for chapters), and exposes it to the session callback. The `MediaLibrarySession.Callback` (`onGetLibraryRoot`/`onGetChildren`/`onGetItem`/`onAddMediaItems`) lives in the service and delegates ID parsing + child resolution to these builders.

**Tech Stack:** Kotlin (KMP: `shared` commonMain/commonTest, `android-shared` androidMain/androidUnitTest), Media3 `MediaLibraryService` / `MediaLibrarySession` (`androidx.media3.session`), Koin DI, kotlinx.coroutines, Guava `ListenableFuture` / `Futures.immediateFuture`, Android Auto / Desktop Head Unit (DHU) for manual verification.

> ⚠️ **MIGRATION RISK — READ FIRST.** `ContinuumPlaybackService` is the *single* playback service for the whole process and is shared by **video** (`androidApp/.../ui/screens/player/PlayerScreen.kt`, and the TV player) **and** audiobooks. The same `ExoPlayer`, the `positionMs` flow, the audio-sync / subtitle-sync jobs, and the swipe-away teardown in `onTaskRemoved` all live here. A regression here breaks all playback on both apps. Rules for this phase:
> 1. **Do not change** `onCreate`, `onTaskRemoved`, `onDestroy`, the position/audio/subtitle jobs, or the `MediaSession.Builder` player wiring beyond swapping `MediaSession` → `MediaLibrarySession` and `MediaSessionService` → `MediaLibraryService`. The session must keep the *same* `ExoPlayer` instance and the *same* `onTaskRemoved` teardown.
> 2. `onGetSession` must keep returning the one session instance (now typed `MediaLibrarySession`) for every controller — video controllers included.
> 3. The browse tree is **lazy and side-effect free** on the playback path: building/serving it must never touch the `ExoPlayer`, never call `setMediaItem`/`prepare`/`play`, and never block the Main thread (all network on `Dispatchers.IO`, returned as `ListenableFuture`).
> 4. Task 7 is a mandatory **full video-playback regression pass** and is a release gate — do not mark Phase 7 done until it passes.

---

## File Structure

Real paths (repository root = `prairie-android`, assumed cwd):

| Path | Responsibility |
| --- | --- |
| `shared/src/commonMain/kotlin/com/continuum/app/audiobook/browse/AudiobookBrowseTree.kt` | **New, pure.** Browse-tree domain: stable node IDs (`MediaBrowseId`), `BrowseNode` data model (id, title, subtitle, artworkUrl, isBrowsable, isPlayable, playableContentId/fileId), and pure builders `rootChildren()`, `continueListeningChildren(progress, details)`, `audiobookLibrariesChildren(libraries)`, `libraryItemsChildren(items)`, `chaptersChildren(contentId, chapters)`. No Android imports. |
| `shared/src/commonTest/kotlin/com/continuum/app/audiobook/browse/AudiobookBrowseTreeTest.kt` | **New.** Unit tests for the pure builders and ID round-trips. |
| `shared/src/commonMain/kotlin/com/continuum/app/repository/AudiobookBrowseRepository.kt` | **New.** Fetches live data for the tree from `PersonalDataRepository` (libraries, progress) and `CatalogRepository` (library contents via `browse`, chapters via `getItemDetail`), filtering to audiobook libraries/items. Returns `BrowseNode` lists by delegating to `AudiobookBrowseTree`. |
| `shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt` | **Edit.** Register `AudiobookBrowseRepository` as a Koin `single`. |
| `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/browse/AudiobookBrowseTreeMediaItems.kt` | **New.** Maps `BrowseNode` → Media3 `MediaItem` (sets `MediaMetadata.isBrowsable`/`isPlayable`, title/subtitle/artwork, `mediaType`). Builds the playable `MediaItem` for a leaf (content/file id → resolved stream `MediaItem` reusing `ContinuumPlayerFactory`/the audiobook session start path). |
| `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/browse/AudiobookLibrarySessionCallback.kt` | **New.** Implements `MediaLibrarySession.Callback`: `onGetLibraryRoot`, `onGetChildren`, `onGetItem`, `onAddMediaItems`. Bridges coroutines → `ListenableFuture`. |
| `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlaybackService.kt` | **Edit.** `MediaSessionService` → `MediaLibraryService`; `MediaSession` → `MediaLibrarySession` built with the callback; `onGetSession` return type widened. Everything else unchanged. |
| `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/browse/AudiobookBrowseTreeMediaItemsTest.kt` | **New.** Unit tests for `BrowseNode` → `MediaItem` flag/metadata mapping (no Android runtime needed; Media3 `MediaItem`/`MediaMetadata` are plain builders, and the module already runs `isReturnDefaultValues = true`). |
| `android-shared/src/androidMain/kotlin/com/continuum/app/common/di/PlayerModule.kt` | **Edit.** Provide `AudiobookBrowseTreeMediaItems` and (if not constructed inline in the service) the callback's collaborators as Koin singletons. |
| `androidApp/src/androidMain/AndroidManifest.xml` | **Edit.** Add the `automotive_app_desc` metadata + Auto `<automotiveApp>` resource, and (for safety) keep the existing media3 session intent-filter while adding the browse `<action>`. |
| `androidApp/src/androidMain/res/xml/automotive_app_desc.xml` | **New.** Declares `<uses name="media"/>` so Android Auto/Automotive treats the app as a media app. |
| `docs/superpowers/plans/2026-06-12-audiobook-phase7-android-auto.md` | This plan (do not edit during implementation). |

**Stable ID scheme** (defined in `MediaBrowseId`, used everywhere so parsing is centralized and tested):

- Root: `"root"`
- Continue Listening node: `"continue_listening"`
- Audiobook Libraries node: `"libraries"`
- A specific library: `"library/<libraryId>"`
- A playable audiobook item: `"item/<contentId>"` (optionally `"item/<contentId>/<fileId>"`)
- Chapters parent for the currently-playing item: `"chapters/<contentId>"`
- A specific chapter (playable, seeks within the item): `"chapter/<contentId>/<index>"`

---

### Task 1 — Pure browse-tree domain + IDs (TDD)

Establishes the tested, Android-free core. No service changes yet, so zero playback risk.

**Files:**
- `shared/src/commonMain/kotlin/com/continuum/app/audiobook/browse/AudiobookBrowseTree.kt` (new)
- `shared/src/commonTest/kotlin/com/continuum/app/audiobook/browse/AudiobookBrowseTreeTest.kt` (new)

- [ ] Write the failing test file `AudiobookBrowseTreeTest.kt` first. Cover:
  - `rootChildren()` returns exactly two nodes with ids `continue_listening` and `libraries`, both browsable, both not playable, with human titles `"Continue Listening"` and `"Audiobook Libraries"`.
  - `audiobookLibrariesChildren(...)` maps a `List<UserLibrary>` to nodes with id `library/<id>`, title = library name, browsable, not playable; and that it **filters** to audiobook libraries only (a `type = "movie"` library is dropped, `type = "audiobook"` kept) using `com.continuum.app.model.navigation.isAudiobookLikeLibraryType`.
  - `libraryItemsChildren(...)` maps `List<BrowseItem>` to nodes with id `item/<contentId>`, title = item title, subtitle = year-as-string when `year > 0` else null, artworkUrl = `posterUrl`, playable = true, browsable = false.
  - `continueListeningChildren(...)` takes a list of `(BrowseItem, positionSeconds, durationSeconds)` and produces playable `item/<contentId>` nodes ordered as given, subtitle = a `"N% complete"` string derived from position/duration (guard divide-by-zero → null subtitle).
  - `chaptersChildren(contentId, chapters)` maps `List<VersionChapter>` to nodes id `chapter/<contentId>/<index>`, title = `chapter.title.ifBlank { "Chapter ${index+1}" }`, playable = true, browsable = false; empty list → empty list.
  - `MediaBrowseId` round-trips: `parse(encode(x)) == x` for Root, ContinueListening, Libraries, `Library(7)`, `Item("abc")`, `Chapters("abc")`, `Chapter("abc", 3)`; and `parse("garbage")` returns `MediaBrowseId.Unknown`.
- [ ] Run it and confirm it fails to compile (symbols absent):
  - `./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.audiobook.browse.AudiobookBrowseTreeTest"`
  - Expected: compilation failure / unresolved references to `AudiobookBrowseTree`, `MediaBrowseId`, `BrowseNode`.
- [ ] Implement `AudiobookBrowseTree.kt`:
  - `data class BrowseNode(val id: String, val title: String, val subtitle: String? = null, val artworkUrl: String? = null, val isBrowsable: Boolean, val isPlayable: Boolean, val playableContentId: String? = null, val chapterIndex: Int? = null)`.
  - `sealed interface MediaBrowseId` with `Root`, `ContinueListening`, `Libraries`, `data class Library(val libraryId: Int)`, `data class Item(val contentId: String)`, `data class Chapters(val contentId: String)`, `data class Chapter(val contentId: String, val index: Int)`, `Unknown`; plus `fun encode(id: MediaBrowseId): String` and `fun parse(raw: String): MediaBrowseId`. Use `/` as the separator and `contentId.substringAfter`/`split(limit=...)` so content ids containing no `/` parse cleanly (content ids in this codebase are opaque strings; reject ids with embedded `/` in `Item`/`Chapters`/`Chapter` by treating malformed input as `Unknown`).
  - `object AudiobookBrowseTree` with the pure builders above. Reuse `isAudiobookLikeLibraryType(library.type)` for filtering. Percent string helper: `if (durationSeconds > 0) "${((positionSeconds / durationSeconds) * 100).toInt()}% complete" else null`.
- [ ] Run the test again; expected: all green.
  - `./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.audiobook.browse.AudiobookBrowseTreeTest"`
- [ ] Commit: `feat(audiobook): add pure Android Auto browse-tree domain + IDs`.

---

### Task 2 — Browse repository (live data → BrowseNode)

Wires the pure tree to real server data. Still no service/player changes.

**Files:**
- `shared/src/commonMain/kotlin/com/continuum/app/repository/AudiobookBrowseRepository.kt` (new)
- `shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt` (edit)

- [ ] Implement `AudiobookBrowseRepository(personalDataRepository: PersonalDataRepository, catalogRepository: CatalogRepository)` with suspend functions returning `List<BrowseNode>`:
  - `suspend fun rootChildren(): List<BrowseNode>` → `AudiobookBrowseTree.rootChildren()` (no I/O).
  - `suspend fun audiobookLibraries(): List<BrowseNode>` → `personalDataRepository.listUserLibraries()`; on `ApiResult.Success`, `AudiobookBrowseTree.audiobookLibrariesChildren(data)`; on error, `emptyList()` (Auto must degrade gracefully, never throw).
  - `suspend fun libraryItems(libraryId: Int): List<BrowseNode>` → `catalogRepository.browse(libraryId = libraryId, mediaType = "audiobook", limit = 200)`; map `CatalogResponse.items` (`List<BrowseItem>`) via `AudiobookBrowseTree.libraryItemsChildren(...)`. On error → `emptyList()`.
  - `suspend fun continueListening(): List<BrowseNode>` → `personalDataRepository.listProgress()`; filter to in-progress, non-completed entries, then resolve each `mediaItemId` to a `BrowseItem`-like node. Resolve titles/posters by calling `catalogRepository.getItemDetail(mediaItemId)` per entry **only for audiobook-type items** (`com.continuum.app.model.catalog.isAudiobookItemType(detail.type)`); skip non-audiobook progress. Cap at the first 20 to bound calls. Map to nodes with `continueListeningChildren(...)` using `ProgressEntry.positionSeconds`/`durationSeconds`. On error → `emptyList()`.
  - `suspend fun chapters(contentId: String): List<BrowseNode>` → `catalogRepository.getItemDetail(contentId)`, take the first version's `chapters` (`ItemDetail.versions.firstOrNull()?.chapters` — already a `List<VersionChapter>?`), `AudiobookBrowseTree.chaptersChildren(contentId, chapters.orEmpty())`. On error → `emptyList()`.
- [ ] Register in `RepositoryModule.kt`: add `single { AudiobookBrowseRepository(get(), get()) }` near the existing `CatalogRepository`/`PersonalDataRepository` singles.
- [ ] Build the shared module to confirm it compiles (the repo has no unit test of its own — it is thin glue over already-tested repos; the tree logic is covered in Task 1):
  - `./gradlew :shared:compileDebugKotlinAndroid`
  - Expected: BUILD SUCCESSFUL.
- [ ] Commit: `feat(audiobook): add AudiobookBrowseRepository for Auto browse data`.

---

### Task 3 — BrowseNode → Media3 MediaItem adapter (TDD)

Android adapter that turns `BrowseNode`s into Media3 `MediaItem`s with correct browsable/playable flags. This is the part Auto rendering depends on, so it is unit-tested.

**Files:**
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/browse/AudiobookBrowseTreeMediaItems.kt` (new)
- `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/browse/AudiobookBrowseTreeMediaItemsTest.kt` (new)

- [ ] Write the failing test `AudiobookBrowseTreeMediaItemsTest.kt`:
  - `toMediaItem(browsableNode)` → a `MediaItem` whose `mediaId == node.id`, `mediaMetadata.isBrowsable == true`, `isPlayable == false`, `title == node.title`.
  - `toMediaItem(playableNode)` → `isPlayable == true`, `isBrowsable == false`, `title`/`subtitle`/`artworkUri` populated when present (artwork null when `artworkUrl` null/blank).
  - `rootMediaItem()` → `mediaId == "root"`, browsable, not playable, title `"Prairie Audiobooks"`.
  - `toMediaItems(list)` preserves order and size.
- [ ] Run and confirm failure:
  - `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.browse.AudiobookBrowseTreeMediaItemsTest"`
  - Expected: unresolved reference to `AudiobookBrowseTreeMediaItems`.
- [ ] Implement `AudiobookBrowseTreeMediaItems` (`@UnstableApi` not required for `MediaItem`/`MediaMetadata`):
  - `fun rootMediaItem(): MediaItem` — `mediaId = MediaBrowseId.encode(MediaBrowseId.Root)`, metadata `setIsBrowsable(true).setIsPlayable(false).setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).setTitle("Prairie Audiobooks")`.
  - `fun toMediaItem(node: BrowseNode): MediaItem` — build `MediaMetadata.Builder()` with `setTitle(node.title)`, `setSubtitle(node.subtitle)` (only when non-null), `setIsBrowsable(node.isBrowsable)`, `setIsPlayable(node.isPlayable)`, `setMediaType(if (node.isBrowsable) MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS else MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)`, and `setArtworkUri(Uri.parse(it))` for non-blank `artworkUrl`. `MediaItem.Builder().setMediaId(node.id).setMediaMetadata(...).build()`.
  - `fun toMediaItems(nodes: List<BrowseNode>): List<MediaItem> = nodes.map(::toMediaItem)`.
- [ ] Run again; expected: green.
  - `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.browse.AudiobookBrowseTreeMediaItemsTest"`
- [ ] Commit: `feat(audiobook): map browse nodes to Media3 MediaItems for Auto`.

---

### Task 4 — MediaLibrarySession.Callback (browse navigation)

Implements the actual Auto navigation: root, children of each node, and resolving a tapped playable item into a real stream `MediaItem` so playback starts on the shared `ExoPlayer`.

**Files:**
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/browse/AudiobookLibrarySessionCallback.kt` (new)
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/di/PlayerModule.kt` (edit)

- [ ] Implement `AudiobookLibrarySessionCallback` taking `(browseRepository: AudiobookBrowseRepository, mediaItems: AudiobookBrowseTreeMediaItems, sessionResolver: AudiobookSessionMediaItemResolver, scope: CoroutineScope)`. Implement `MediaLibrarySession.Callback`:
  - `onGetLibraryRoot(session, browser, params)` → `Futures.immediateFuture(LibraryResult.ofItem(mediaItems.rootMediaItem(), params))`.
  - `onGetChildren(session, browser, parentId, page, pageSize, params)` → bridge a coroutine to a `ListenableFuture` via `future(scope) { ... }` (Guava `androidx.concurrent.futures` / `kotlinx-coroutines-guava` `future {}` builder — add `libs.kotlinx.coroutines.guava` to `android-shared` androidMain deps if not present). Inside: `when (MediaBrowseId.parse(parentId))`:
    - `Root` → `browseRepository.rootChildren()`
    - `Libraries` → `browseRepository.audiobookLibraries()`
    - `is Library` → `browseRepository.libraryItems(libraryId)`
    - `ContinueListening` → `browseRepository.continueListening()`
    - `is Chapters` → `browseRepository.chapters(contentId)`
    - else → `emptyList()`
    Map to `mediaItems.toMediaItems(...)`, wrap in `LibraryResult.ofItemList(items, params)`.
  - `onGetItem(session, browser, mediaId)` → resolve a single node by re-deriving from the parent context where cheap; for simplicity return `LibraryResult.ofItem` built from the parsed id (browsable nodes get a folder item, item/chapter ids get a playable item). Acceptable to return `LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)` for `Unknown`.
  - `onAddMediaItems(mediaSession, controller, mediaItems)` → for each incoming `MediaItem` whose `mediaId` parses to `Item`/`Chapter`, call `sessionResolver.resolvePlayable(id)` (suspend, on IO) to produce a fully-specified `MediaItem` (absolute stream URL + metadata; for a `Chapter`, the same item but with start position carried in `MediaItem.Builder().setMediaMetadata(...)` / a `RequestMetadata` extra `startPositionMs`). Return them as a `ListenableFuture<List<MediaItem>>`. This is the Media3-idiomatic hook that lets Auto's "tap to play" resolve placeholder browse items into playable ones. Items that fail to resolve are returned unchanged (Media3 then surfaces a play error rather than crashing).
- [ ] Implement `AudiobookSessionMediaItemResolver` (same package): `suspend fun resolvePlayable(id: MediaBrowseId): MediaItem?`. It reuses the existing audiobook start path: resolve `contentId`→version/fileId via `CatalogRepository.getItemDetail`, start a playback session through `PlaybackSessionManager` (mirror `AudiobookPlayerViewModel.applySession` semantics — DIRECT vs TRANSCODE/REMUX), then `ContinuumPlayerFactory.buildMediaItem(streamUrl=..., playMethod=..., serverUrl=..., title=..., subtitle=..., artworkUrl=...)`. For a `Chapter`, resolve the parent item the same way and attach the chapter's `startSeconds` (ms) as a `requestMetadata` extra so the service seeks after prepare. Keep this in `android-shared` and inject `CatalogRepository`, `PlaybackSessionManager`, `ProfileRepository`, `ContinuumPlayerFactory` via Koin.
  > Note: `AudiobookPlayerViewModel.applySession` currently encapsulates this DIRECT/TRANSCODE logic inline. If subagent-driven-development notices Phase 1's VM relocation already extracted a reusable session-start helper into `android-shared`, reuse it instead of duplicating; otherwise factor the minimal shared helper here and leave a `// TODO(phase-1-dedupe)` only if Phase 1 has not landed. Do not copy the whole VM.
- [ ] In `PlayerModule.kt`, add singles: `single { AudiobookBrowseTreeMediaItems() }`, `single { AudiobookSessionMediaItemResolver(get(), get(), get(), get()) }`. (The callback itself is constructed inside the service with the service-scoped `CoroutineScope`, not via Koin.)
- [ ] Compile:
  - `./gradlew :android-shared:compileDebugKotlinAndroid`
  - Expected: BUILD SUCCESSFUL.
- [ ] Commit: `feat(audiobook): MediaLibrarySession callback for Android Auto browse + play`.

---

### Task 5 — Service migration: MediaSessionService → MediaLibraryService

**The high-risk edit.** Keep the diff minimal and surgical.

**Files:**
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlaybackService.kt` (edit)

- [ ] Change the import `androidx.media3.session.MediaSessionService` → `androidx.media3.session.MediaLibraryService`, and add `import androidx.media3.session.MediaLibraryService.MediaLibrarySession`.
- [ ] Change the class declaration `class ContinuumPlaybackService : MediaSessionService()` → `class ContinuumPlaybackService : MediaLibraryService()`.
- [ ] Change the field `private var mediaSession: MediaSession? = null` → `private var mediaSession: MediaLibrarySession? = null`. (`MediaLibrarySession` *is a* `MediaSession`, so all existing `mediaSession?.player?...` usages in `onTaskRemoved`/`onDestroy` keep compiling unchanged.)
- [ ] In `onCreate`, replace the session build:
  - was: `mediaSession = MediaSession.Builder(this, player).build()`
  - now: inject the browse collaborators (`private val browseRepository: AudiobookBrowseRepository by inject()`, `private val browseMediaItems: AudiobookBrowseTreeMediaItems by inject()`, `private val sessionResolver: AudiobookSessionMediaItemResolver by inject()`), construct `val callback = AudiobookLibrarySessionCallback(browseRepository, browseMediaItems, sessionResolver, scope)`, then `mediaSession = MediaLibrarySession.Builder(this, player, callback).build()`.
  - **Do not** change anything else in `onCreate` — the `player`, `addAnalyticsListener`, the `playerInstanceCount` logging, and all three `scope.launch { ... }` jobs stay byte-for-byte.
- [ ] Change `onGetSession`'s return type and body: `override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession`. (Video `MediaController`s call `onGetSession` and receive this same session — they never invoke library APIs, so behavior is identical.)
- [ ] **Do not touch** `onTaskRemoved`, `onDestroy`, the `companion object`, or the doc comments describing the single-ExoPlayer invariant beyond noting it now also backs the browse tree.
- [ ] Compile both consuming apps to prove the superset is source-compatible for video:
  - `./gradlew :android-shared:compileDebugKotlinAndroid :androidApp:compileDebugKotlin :androidTvApp:compileDebugKotlin`
  - Expected: BUILD SUCCESSFUL — confirms `PlayerScreen`/`TvPlayerScreen`'s `MediaController` + `SessionToken(ComponentName(..., ContinuumPlaybackService::class.java))` still resolve unchanged.
- [ ] Run the existing `android-shared` player tests to confirm no regression in the player module:
  - `./gradlew :android-shared:testDebugUnitTest`
  - Expected: all existing tests (incl. `PlayerModuleTest`, `PlaybackSessionLifecycleTest`, `FfmpegClasspathTest`) green plus the new browse tests.
- [ ] Commit: `refactor(player)!: upgrade ContinuumPlaybackService to MediaLibraryService`.

---

### Task 6 — Manifest: automotive browse intent-filter + media app descriptor

Auto discovers the app via the media-browse service action and the `automotive_app_desc` metadata. The existing `mediaPlayback` foreground type and media3 session intent-filter are preserved.

**Files:**
- `androidApp/src/androidMain/res/xml/automotive_app_desc.xml` (new)
- `androidApp/src/androidMain/AndroidManifest.xml` (edit)

- [ ] Create `androidApp/src/androidMain/res/xml/automotive_app_desc.xml`:
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <automotiveApp>
      <uses name="media" />
  </automotiveApp>
  ```
- [ ] In `AndroidManifest.xml`, inside `<application>`, add the Auto descriptor metadata (sibling of the existing `<service>`):
  ```xml
  <meta-data
      android:name="com.google.android.gms.car.application"
      android:resource="@xml/automotive_app_desc" />
  ```
- [ ] In the existing `<service android:name="com.continuum.app.common.player.ContinuumPlaybackService" ...>` block, keep the current `mediaPlayback` `foregroundServiceType` and the existing `MediaSessionService` action, and add the media-browse action so Auto can bind for browsing:
  ```xml
  <service
      android:name="com.continuum.app.common.player.ContinuumPlaybackService"
      android:exported="true"
      android:foregroundServiceType="mediaPlayback">
      <intent-filter>
          <action android:name="androidx.media3.session.MediaLibraryService" />
          <action android:name="androidx.media3.session.MediaSessionService" />
          <action android:name="android.media.browse.MediaBrowserService" />
      </intent-filter>
  </service>
  ```
  > Rationale: Media3's `MediaLibraryService` advertises itself via the `MediaLibraryService` action; the legacy `android.media.browse.MediaBrowserService` action is what Android Auto's `MediaBrowserService` discovery scans for, and Media3 bridges legacy browsers to the session automatically. Keeping the `MediaSessionService` action preserves the exact discovery path video's `MediaController` already uses.
- [ ] Build a debug APK to confirm the merged manifest is valid:
  - `./gradlew :androidApp:assembleDebug`
  - Expected: BUILD SUCCESSFUL; the merged manifest at `androidApp/build/intermediates/merged_manifest/debug/.../AndroidManifest.xml` contains all three `<action>`s and the `com.google.android.gms.car.application` meta-data.
- [ ] Commit: `feat(audiobook): declare Android Auto media browse service + descriptor`.

---

### Task 7 — FULL VIDEO-PLAYBACK REGRESSION PASS (release gate)

Manual, on a real Pixel (and ideally one Android TV device). **Phase 7 is not done until every box here passes.** The point is to prove the `MediaLibraryService` upgrade did not regress the shared service for video.

**Files:** none (verification only).

Build & install:
- [ ] `./gradlew :androidApp:installDebug` (and `:androidTvApp:installDebug` if a TV/emulator is available).

Video core (phone):
- [ ] Launch a **video** item from a library → it plays; picture + audio present, no extra/orphaned `ExoPlayer` (check `adb logcat -s ContinuumPlayback` shows `live instance count = 1`, not 2).
- [ ] Seek via the overlay scrubber → playhead moves and audio follows.
- [ ] Switch audio track and subtitle track from the overlay → both apply (this exercises the same session the browse tree now shares).
- [ ] Trigger a transcode item (a deliberately incompatible file) → the preflight fallback still fires and playback recovers.

Lock screen / system controls (phone):
- [ ] With video playing, lock the device → lock-screen media controls show title/artwork; play/pause/seek from the lock screen control the player.
- [ ] Bluetooth/wired **headset** play/pause and next/prev buttons act on the session.
- [ ] Google Assistant "pause"/"resume" affects playback.

Teardown (the most regression-prone path — `onTaskRemoved`):
- [ ] Exit the player via back → audio stops immediately, session ends, `live instance count = 0` in logcat (the `PlayerScreen` `onDispose` teardown still runs).
- [ ] Re-enter and **swipe the app away** from Recents while playing → audio stops and the service tears down (verifies `onTaskRemoved` still calls `stop()/clearMediaItems()/stopSelf()` after the superclass change).

Watch Together (if available):
- [ ] Join a room and play → transport mirroring and corrective seeks still work (uses the same `MediaController`).

Android TV (if a device/emulator is available):
- [ ] `TvPlayerScreen` video plays, D-pad transport + `stopPlaybackAndExit` teardown still work.

- [ ] Record results (pass/fail + device/build) in the PR description. Any failure here blocks the phase.

---

### Task 8 — Android Auto manual verification via Desktop Head Unit (DHU)

Proves the new browse tree and playback work in Auto itself. DHU is the official emulator for Android Auto on a phone.

**Files:** none (verification only).

One-time DHU setup:
- [ ] Install **Android Auto** from the Play Store on the test phone (Auto is preinstalled on most modern phones).
- [ ] In Android Auto settings, enable **Developer mode** (tap the version/"About" header 10×), then **Settings → Developer settings → "Add new cars to Android Auto"** unchecked is fine; enable **"Unknown sources"** so the debug build is browsable.
- [ ] Install DHU: `sdkmanager "extras;google;auto"` (lands under `$ANDROID_SDK_ROOT/extras/google/auto/`).
- [ ] Connect the phone by USB, enable USB debugging, then start head-unit server on the phone: in Android Auto **Developer settings → "Start head unit server"**.
- [ ] Forward the port and launch DHU:
  - `adb forward tcp:5277 tcp:5277`
  - `"$ANDROID_SDK_ROOT/extras/google/auto/desktop-head-unit"`
  - Expected: the DHU window opens showing the Auto home screen.

Browse-tree verification (in DHU):
- [ ] Open the media app launcher in DHU and select **Prairie** (the new media app entry, surfaced because of the `automotive_app_desc` + browse service from Task 6).
- [ ] Confirm the **root** shows two folders: **Continue Listening** and **Audiobook Libraries**.
- [ ] Open **Audiobook Libraries** → confirm only audiobook-type libraries appear (no movie/TV libraries — the `isAudiobookLikeLibraryType` filter).
- [ ] Open a library → confirm audiobook items appear with titles and cover art.
- [ ] Open **Continue Listening** → confirm in-progress audiobooks appear with a "N% complete" subtitle.
- [ ] **Tap an item to play** → playback starts on the device's shared `ExoPlayer`; DHU's now-playing shows title/artwork; play/pause and skip from DHU control playback (verifies `onAddMediaItems` → `resolvePlayable` resolved a real stream).
- [ ] While an item is playing, confirm a **Chapters** browse node is reachable for the current item and that selecting a chapter seeks within the book (verifies `chapters/<contentId>` + `chapter/<contentId>/<index>` resolution with the `startPositionMs` extra).
- [ ] Verify no crash/ANR in `adb logcat` during browse/play; the `ContinuumPlayback` tag still shows a single ExoPlayer instance.
- [ ] Record DHU results in the PR description.

---

## Self-review vs. spec §4.6 / §6 phase 7

- ✅ **MediaSessionService → MediaLibraryService** conversion (Task 5), keeping the same `ExoPlayer` and session-as-superset so video uses the session API unchanged (spec §4.6 superset note; risk callout at top + Task 5 rules).
- ✅ **MediaLibrarySession + browsable tree**: root → **Continue Listening**, **Audiobook Libraries**, and (when playing) **Chapters**, via `onGetLibraryRoot`/`onGetChildren`/`onGetItem` (Tasks 1, 4). `onAddMediaItems` added because Media3 requires it to resolve a tapped browse item into a playable stream — spec lists the three callbacks but play-on-tap is implied by "start playback"; documented in Task 4.
- ✅ **AndroidManifest** automotive/browse intent-filter (Task 6), preserving the existing media3 session filter and `mediaPlayback` foreground type.
- ✅ **Dedicated video-regression verification pass** (Task 7) covering lock-screen controls, headset, exit teardown, and swipe-away `onTaskRemoved` — the single biggest risk in the spec.
- ✅ Browse data grounded in **real** repos: `PersonalDataRepository.listUserLibraries()`/`listProgress()`, `CatalogRepository.browse()`/`getItemDetail()`, `VersionChapter` chapters off `ItemDetail.versions` (verified shapes). Filtering reuses existing `isAudiobookLikeLibraryType`/`isAudiobookItemType`.
- ✅ TDD on the **pure** builders (Task 1 `:shared:testDebugUnitTest`) and the Android `MediaItem` mapper (Task 3 `:android-shared:testDebugUnitTest`) — both verified-real gradle task names.
- ⚠️ **Deferred / cross-phase dependency:** This phase assumes **Phase 1** has landed (`AudiobookPlayerViewModel` relocated to `android-shared`, optional shared session-start helper). The browse `resolvePlayable` path duplicates minimal DIRECT/TRANSCODE session-start logic from `AudiobookPlayerViewModel.applySession`; Task 4 instructs reusing a shared helper if Phase 1 extracted one, else factoring a small helper here. If Phase 7 is implemented before Phase 1, flag this to the human reviewer — the spec sequences Phase 1 first.
- ⚠️ **Deferred:** Rich notification chapter actions (spec §4.8 / Phase 6) and the `SKIP_TO_PREV/NEXT_CHAPTER` custom session commands are **out of scope for Phase 7** and owned by Phase 6; the Chapters browse node here is independent of those.
- Note: `MediaLibraryService` requires Media3 `media3-session` (already a dependency of `android-shared`, confirmed in `android-shared/build.gradle.kts`). `kotlinx-coroutines-guava` (`future {}` bridge) must be present for Task 4; if absent, add `libs.kotlinx.coroutines.guava` to `android-shared` androidMain deps as the first step of Task 4.
