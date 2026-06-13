# Shared Video Player Core Design

## Decision

Move Android mobile and Android TV video playback onto one shared player core while keeping separate presentation surfaces for touch and D-pad/remote UX.

This replaces the current drift-prone model where `PlayerViewModel` / `PlayerScreen` and `TvPlayerViewModel` / `TvPlayerScreen` each own similar playback logic. The shared core should live in `android-shared` and expose state plus commands to both clients. Mobile and TV will continue to render their own overlays, controls, focus behavior, settings entry points, and Watch Together affordances.

## Goals

- One implementation for session start, resume position resolution, transcode/remux fallback, progress reporting, outage recovery, buffer policy, and player MediaItem mounting.
- One implementation for subtitle/audio track state and commands, including downloaded/AI subtitle metadata where available.
- TV and mobile should receive the same bug fixes by default.
- TV must retain remote-first controls, focus handling, large text, and leanback layout.
- Mobile must retain touch-first gestures, portrait/landscape behavior, subtitle sheets, and local-download behavior.
- Ebooks remain excluded from TV. This work is video playback only.

## Non-Goals

- Do not merge mobile and TV UI into one composable.
- Do not redesign player controls during this migration except where a control must adapt to the shared command/state API.
- Do not change server playback APIs unless a clear client/server mismatch is discovered during implementation.
- Do not move audiobook playback into this video core. Audiobooks already use a shared audio-focused stack and should stay separate.

## Current Shape

The app already has shared player infrastructure in `android-shared`:

- `ContinuumPlayerFactory`
- `PlaybackSessionManager`
- `PlaybackSessionLifecycle`
- `SubtitleManager`
- audio capability and playback capability helpers
- shared start-position helpers in `shared`

But the two video screens still duplicate major behavior:

- Mobile: `androidApp/.../player/PlayerViewModel.kt` and `PlayerScreen.kt`
- TV: `androidTvApp/.../player/TvPlayerViewModel.kt` and `TvPlayerScreen.kt`

This duplication caused the resume bug: mobile was patched to call `setMediaItem(mediaItem, startMs)` before `prepare()`, while TV still calls `setMediaItem(mediaItem)`, then `seekTo(startMs)`, then `prepare()`.

## Architecture

Introduce a shared video player core in `android-shared`. The implementation should use these concepts; exact class names may follow local conventions if the boundaries remain the same:

- `VideoPlayerCoreViewModel`
- `VideoPlayerUiState`
- `VideoPlayerCommand`
- `VideoPlayerRouteArgs`
- `VideoPlayerMediaSpec`

The core owns all playback state that should be form-factor independent:

- content id and selected file id
- title, subtitle, artwork
- stream URL, play method, server URL
- selected version, audio track, subtitle track
- subtitle track list and merged downloaded/AI subtitles
- start position, current position, duration
- playback session id
- intro and credits ranges
- chapters
- buffer and reconnect notice state
- play/pause, seek, skip, subtitle/audio/version selection commands
- session lifecycle adoption and recovery

The mobile and TV composables own only presentation:

- how controls look
- how focus/touch input maps to commands
- which menus are visible
- orientation and system UI rules
- TV-specific remote affordances
- mobile-specific gesture/sheet affordances

## Media3 Mounting

Extract a shared Media3 mounting helper so both surfaces mount playback consistently.

The helper should:

- build the `MediaItem` through `ContinuumPlayerFactory`
- prefer local downloaded bytes when the active file exists locally, for mobile
- compute `startMs` from shared state
- call `controller.setMediaItem(mediaItem, startMs)` before `controller.prepare()`
- preserve live position when rebuilding the media item for subtitle refresh
- restore `playWhenReady`

TV will pass a resolver that returns no local media until TV downloads are explicitly supported.

## Navigation Inputs

Mobile already accepts:

- `contentId`
- `fileId`
- `audioTrackIndex`
- `subtitleTrackIndex`
- `resumePosition`
- `roomId`

TV will move to the same route input contract for video:

- `contentId`
- `fileId`
- `resumePosition`
- `roomId`

Audio/subtitle launch selections can be added to TV later if the TV detail surface exposes those choices. The shared core should support them now so mobile does not lose functionality.

## Data Flow

1. A detail/home/search surface launches video with `VideoPlayerRouteArgs`.
2. The shared core resolves the content, active profile, file version, capability profile, and resume position.
3. The shared core starts or adopts a playback session through `PlaybackSessionManager` / `PlaybackSessionLifecycle`.
4. The shared core emits `VideoPlayerUiState`.
5. Mobile or TV observes the same state, renders its own UI, and passes user actions back as commands.
6. A shared Media3 mount helper applies the stream to the bound `MediaController`.
7. Progress reports and recovery remain centralized in `PlaybackSessionLifecycle`.

## Migration Plan

Implement this incrementally to avoid breaking both clients at once.

Phase 1: Shared Mount Helper

- Extract the common Media3 mount behavior first.
- Patch TV resume through the helper.
- Add tests that guard both mobile and TV against reverting to `setMediaItem()` plus post-prepare seek.
- Verify on Pixel and Shield.

Phase 2: Shared State Model

- Introduce `VideoPlayerUiState` in `android-shared`.
- Map existing mobile and TV state to the shared shape.
- Keep existing ViewModels as adapters temporarily if that reduces risk.

Phase 3: Shared Session Core

- Move duplicated session start, transcode/remux fallback, resume resolution, progress reporting handoff, and recovery handling into the shared core.
- Keep mobile and TV ViewModels as thin wrappers only if needed for Koin or SavedStateHandle differences.

Phase 4: Shared Track/Subtitles Core

- Move audio/subtitle/version selection commands and track-list state into the shared core.
- Keep TV and mobile menus separate.

Phase 5: Remove Duplicate Logic

- Delete or shrink the old mobile/TV-specific player ViewModels once both surfaces consume the shared core directly.
- Keep UI-specific files separate.

## Testing

Add tests at each migration layer:

- shared resume position precedence
- route argument parsing/building for mobile and TV
- Media3 mount helper uses `setMediaItem(mediaItem, startMs)` before prepare
- TV detail resume launch preserves resume position
- mobile detail/home launch preserves resume position
- subtitle track selection state survives track-list refresh
- transcode/remux fallback preserves resume position
- session-missing recovery restarts from last reported position

Device verification remains required:

- Pixel: detail Resume opens at nonzero timeline position
- Shield: detail Resume opens at nonzero timeline position
- Shield: D-pad controls still focus and activate correctly
- Shield: subtitles still display and selection sticks
- Mobile: subtitles still display and selection sticks

## Risks

- The shared core can become too broad if UI-specific concerns leak into it. Keep remote/touch behavior outside the core.
- TV has focus and lifecycle requirements that mobile does not. Avoid making the core own focus.
- Watch Together state must continue to respect room authority on both clients.
- Local downloads are mobile-first today. The shared mount helper must allow local playback without forcing TV to support downloads.
- This touches high-use playback code, so migration should be phased and verified after every phase.

## Acceptance Criteria

- Mobile and TV both use the shared Media3 mount path.
- Mobile and TV both use shared resume/session resolution.
- The TV resume bug is fixed and verified on Shield.
- Existing mobile resume behavior remains verified on Pixel.
- Mobile and TV retain separate UI/control surfaces.
- No ebook surfaces are added to TV.
