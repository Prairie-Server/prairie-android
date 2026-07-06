# Open Issue Closure Design

## Scope

Close the remaining open Android-client issues that were verified as not corrected or only partially corrected:

- #5 Collections on Android
- #6 Collections on Android TV
- #15 SiloControl TV receiver
- #16 SiloControl phone remote
- #17 SiloControl "Play on device"
- #19 Android native push delivery
- #22 Download series monitoring, retention, and Reclaim Watched

Issue #27 is explicitly excluded from activation. Requests, Admin, and Watch Together code may remain compiled, but they stay inaccessible from user menus until product direction changes.

## Global Constraints

- Android 7 remains supported.
- Ebooks remain mobile-only and must not surface on Android TV.
- Requests, Admin, and Watch Together stay hidden from Android phone and Android TV user menus.
- Downloads remain public/discoverable and use original filenames/formats for completed media bytes.
- New feature work must be test-first: add failing tests, verify the failure, implement, then verify green.
- Android TV surfaces must be D-pad operable with visible focus and no focus traps.
- Apple/tvOS is the master for SiloControl protocol shape. Android should interoperate with Apple, not invent a parallel protocol.

## Design Summary

The closure work splits into four independent slices.

First, Collections becomes browse-only on both Android surfaces. Shared collection repositories and APIs stay, but create, edit, delete, move, and reorder actions are removed from release UI entry points. The user can browse collection lists, open a collection, and play/read supported member items. Authoring remains web-only.

Second, Downloads gains monitored subscriptions. Users can monitor a series or audiobook/reading collection for future downloads, choose quality and Wi-Fi policy, define retention, and reclaim watched/read/listened downloaded items. A WorkManager worker evaluates subscriptions periodically and after app foreground, compares server metadata against local sidecars, and enqueues missing eligible files through the existing `DownloadEnqueuer`.

Third, SiloControl ports Apple SiloCast. Android TV advertises `_silocast._tcp`, accepts one controller at a time, exposes current playback state, accepts launch/control commands, and can show a standby/remote-connected state. Android phone discovers `_silocast._tcp` devices, shows a target picker, exposes a now-playing mini-bar/full remote, and adds "Play on device" on playable detail screens. The wire protocol mirrors `silo-apple/iosApp/iosApp/Cast/SiloCastProtocol.swift`.

Fourth, Android native push adds the client-side FCM integration around the server's private push architecture. The app registers an FCM token with the signed-in server/profile, handles data-only messages in a `FirebaseMessagingService`, fetches notification details from the user's server, and posts a generic local notification/deep link. If the checked-in Android repo does not yet have Firebase configuration, the implementation must keep the push code behind optional build/config guards and expose a clear disabled state rather than crashing or inventing fake delivery.

## Collections Browse-Only

### Current Problem

Both Android and Android TV currently expose collection authoring:

- Android has `CreateCollectionSheet`, `showCreateSheet`, `createCollection`, delete, and move actions.
- Android TV has `TvCreateCollectionDialog`, `showCreateSheet`, `createCollection`, delete, and move actions.

Issues #5 and #6 require no create/manage entry point in release builds.

### Target Behavior

Collections screens show existing collections and collection groups. Empty states say there are no collections to show. Collection detail opens and lists members. User actions are limited to opening playable/readable supported content and normal card context actions that do not mutate collection structure.

The shared repository can keep create/update/delete methods for future/admin/web reuse, but Android phone and Android TV production UI must not call them from user-accessible surfaces.

### Tests

Add source/behavior tests that fail on current code:

- Android collections source must not reference `CreateCollectionSheet`, `showCreateSheet`, `createCollection`, `deleteCollection`, or `moveCollection` from production collection screens.
- Android TV collections source must not reference `TvCreateCollectionDialog`, `showCreateSheet`, `createCollection`, `deleteCollection`, or `moveCollection` from production collection screens.
- Browse/detail source tests keep route and detail rendering references intact so the feature is not accidentally removed.

## Download Monitoring, Retention, And Reclaim Watched

### Current Problem

Downloads support direct/manual file downloads, public storage, offline playback/reading, Wi-Fi-only constraints, and quality presets. They do not implement:

- monitored series auto-downloads
- periodic subscription evaluation
- retention policies
- Reclaim Watched
- a storage breakdown of reclaimable bytes

### Data Model

Add a Room-backed subscription model in `android-shared`:

- `DownloadSubscriptionEntity`
  - `id: String`
  - `serverId: String`
  - `profileId: String`
  - `targetType: String` with values `series`, `season`, `audiobook_series`, `author`, `collection`
  - `targetId: String`
  - `displayTitle: String`
  - `mediaKind: String` with values `video`, `audio`, `reading`
  - `quality: String`
  - `wifiOnly: Boolean`
  - `enabled: Boolean`
  - `includeExisting: Boolean`
  - `keepUnwatchedLimit: Int`
  - `deleteWatchedAfterDays: Int`
  - `createdAt: Long`
  - `updatedAt: Long`
  - `lastEvaluatedAt: Long?`
  - `lastError: String?`

Add a small domain model in `shared` so UI and worker code do not depend directly on Room entities.

### Evaluation Flow

`DownloadSubscriptionWorker` runs:

1. Read active server/profile-scoped subscriptions.
2. For each subscription, fetch the smallest existing API surface that can enumerate candidate files:
   - series/season video candidates from detail/season endpoints already used by detail screens
   - audiobook series/author/collection candidates from existing audiobook/library endpoints where present
   - reading subscriptions only on Android mobile
3. Filter out files already downloaded or queued.
4. Filter out watched/read/listened candidates unless the subscription has `includeExisting`.
5. Enqueue eligible candidates with `DownloadEnqueuer` using the subscription quality.
6. Apply retention by deleting completed downloads that are watched/read/listened and older than `deleteWatchedAfterDays`, while preserving at least `keepUnwatchedLimit` unwatched items.
7. Persist `lastEvaluatedAt` and `lastError`.

Workers are scheduled periodically and can be triggered manually from the Downloads screen.

### Reclaim Watched

Add a Downloads action that computes reclaimable items from local sidecars plus local/server user state:

- watched movies/episodes
- completed audiobooks where progress is at completion threshold
- completed ebooks where reading progress is at completion threshold

The action presents count and bytes to free, then deletes selected completed files using existing download deletion paths. It must not delete partial/in-flight downloads unless the user explicitly deletes those rows.

### UI

Mobile Downloads gets:

- a monitored downloads section
- a Reclaim Watched action with bytes/count
- subscription detail controls for quality, Wi-Fi-only, retention, and enabled/disabled

Android TV can show video/audio monitored download status only if Downloads management is already exposed for TV. If not exposed, the worker/storage model still handles existing TV downloads, but no new TV menu surface is added.

### Tests

Add tests for:

- subscription DAO create/update/disable/query by server/profile
- worker enqueues only missing eligible files
- worker does not enqueue ebooks for TV
- retention deletes only completed watched/read/listened items past policy
- Reclaim Watched calculation excludes queued/failed/in-progress rows
- manual downloads still work with existing `DownloadEnqueuer`

## SiloControl / SiloCast

### Protocol

Mirror Apple `SiloCastProtocol` version 1:

- service type: `_silocast._tcp`
- messages: `hello`, `launch`, `control`, `state`, `error`, `ping`, `pong`, `close`
- peer roles: `phone`, `tv`
- launch payload: `serverId` plus playback request
- controls: play, pause, play/pause, seek, stop, audio/subtitle selection, speed, quality, video gravity, HDR, subtitle sync, subtitle position, volume, mute, play next
- state payload: content/session/title/loading/buffering/time/duration/tracks/quality/gravity/HDR/subtitle/volume/next/error

JSON field names must match Apple exactly, including snake-case enum values such as `play_pause`, `select_audio_track`, and `set_subtitle_sync_ms`.

### Transport

Reuse the companion-pairing transport pattern where possible:

- TV advertises with Android `NsdManager`.
- TV accepts one controller connection at a time.
- Newest controller wins, closing the previous controller.
- Length-prefixed JSON frames are used like the existing pairing frame style.
- TLS-PSK is preferred for Apple parity if the existing Android TLS-PSK helper can be reused safely. If Android interoperability proves blocked by platform TLS limitations, use the same framing with a clearly named unauthenticated LAN transport for Android-to-Android first and keep the protocol layer independent so TLS can be swapped in.

### Android TV Receiver

The receiver owns:

- advertise/start/stop lifecycle in signed-in TV app
- active controller state
- launch command routing into TV playback
- control command mapping into `TvPlayerViewModel`
- playback state snapshots from active TV player
- idle/standby state when no player is active

The TV player already has remote-command plumbing for server playback realtime; SiloCast should use a small adapter rather than duplicate player logic.

### Android Phone Controller

The phone owns:

- NSD browse for `_silocast._tcp`
- target picker
- connect/disconnect lifecycle
- launch from playable detail screens
- now-playing mini-bar
- full remote control screen
- optimistic clock behavior for play/pause/seek, matching Apple tests

The remote UI should be present but not flashy: clear transport buttons, scrubber, title/art, tracks, subtitles, quality, volume/mute where supported.

### Tests

Add tests for:

- message serialization round-trips matching Apple sample JSON
- control command names and nullable subtitle-off handling
- playback clock optimistic seek/play behavior
- TV receiver source has `_silocast._tcp` advertising and one-controller policy
- phone source has NSD browser, target picker, now-playing mini-bar, and play-on-device entry points
- command adapter maps controls to existing player operations without requiring a live player in unit tests

## Android Native Push

### Server Contract

The server already documents a privacy-preserving FCM relay architecture. Android client implementation should target these capabilities:

- register FCM token for active server/profile/device
- unregister token on sign-out/server removal
- update push mode per device
- receive opaque data-only FCM messages
- fetch real notification/inbox rows from the user's server after wake

If the concrete token-registration endpoint is absent or differs, add the Android API interface behind a repository contract and leave the endpoint path in one central file for adjustment.

### Android App Behavior

Phone app:

- requests `POST_NOTIFICATIONS` on Android 13+
- obtains FCM registration token when Firebase is configured
- registers token after server/profile selection
- refreshes registration when token changes, server changes, or profile changes
- handles data messages in `SiloFirebaseMessagingService`
- fetches inbox details from server
- posts a generic notification with a deep link into notifications/detail or the relevant content if the fetched row provides one

TV app:

- does not request mobile push by default.
- may compile shared push models, but no TV user-facing push setup is added unless server/product policy later requires it.

### Build Configuration

Firebase dependencies must not break local/debug builds without `google-services.json`. The implementation should:

- keep FCM code in `androidApp` only
- use Gradle configuration that compiles when Firebase is configured
- expose a disabled push state when Firebase is absent
- keep notification inbox/websocket behavior unchanged

### Tests

Add tests for:

- push registration repository builds the expected request
- sign-out/server removal unregisters locally stored token mapping
- data-only message handler rejects messages missing opaque delivery IDs
- notification permission gate shows disabled state before permission grant
- no Android TV push setup route is exposed

## Documentation And Issue Hygiene

Update README/docs to say:

- Collections are browse-only on Android clients; authoring is web-only.
- Downloads support monitored auto-download and Reclaim Watched once implemented.
- SiloControl supports Android phone to Android TV control/play-on-device.
- Android push requires server push provider support and Firebase configuration.
- Requests/Admin/Watch Together remain hidden from user menus.

When implementation is complete, each issue should be verified with tests plus source/device checks where applicable.

## Verification Strategy

Run focused tests after each slice, then the broader verification set:

```bash
./gradlew --rerun-tasks --no-build-cache :shared:testDebugUnitTest :android-shared:testDebugUnitTest :androidApp:testDebugUnitTest :androidTvApp:testDebugUnitTest
```

For SiloControl, run an emulator/device check:

- Android TV advertises `_silocast._tcp`.
- Android phone discovers the TV.
- "Play on device" launches the selected item.
- phone remote can play/pause/seek/select subtitles.

For downloads, run a local flow:

- create a monitored subscription
- trigger evaluation
- confirm eligible downloads enqueue
- mark watched/read/listened
- confirm Reclaim Watched reports and deletes only reclaimable files

For push, run the deepest available environment:

- if Firebase config exists, send a data-only test push and confirm inbox fetch/local notification
- if Firebase config is absent, verify disabled state and registration code tests

## Self-Review

This spec has a complete requirement set for the approved scope. It deliberately excludes #27 activation to honor the current product decision. The highest-risk dependencies are SiloCast transport interoperability and concrete server FCM endpoint shape; both are isolated behind protocol/repository boundaries so implementation can progress without infecting unrelated app code.
