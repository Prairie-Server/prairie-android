# Production Playback Backend Boundary Design

**Date:** 2026-06-14
**Status:** Approved for implementation planning
**Branch:** `feature/production-playback-architecture`

## Decision

Introduce a shared video playback backend boundary for Android mobile and Android TV, while keeping Media3/ExoPlayer as the only active backend in this slice.

This is Approach A from the reference-client comparison: build the seam first, prove current playback remains stable, and add MPV in a later phase behind the same interface. The branch must not add an MPV dependency, libmpv AAR, or new native packaging yet.

## Goals

- Create a backend abstraction that can host Media3 now and MPV later.
- Keep current Media3 behavior stable for mobile and TV.
- Reduce direct Media3 assumptions in shared video mounting, track selection, subtitle selection, and player lifecycle code.
- Preserve the existing shared video session work: `VideoPlaybackStarter`, `VideoPlaybackSessionCoordinator`, `VideoPlayerMediaMounter`, `VideoTrackSelectionCoordinator`, `PlaybackBufferPolicy`, route capability labels, and resume handling.
- Give future MPV work a clear integration point without another player rewrite.
- Keep mobile and TV presentation surfaces separate.

## Non-Goals

- Do not add MPV, libass, or new native dependencies in this slice.
- Do not redesign mobile or TV player controls.
- Do not merge mobile and TV composables.
- Do not change server playback APIs.
- Do not move audiobook playback into the video backend boundary.
- Do not expose ebooks on Android TV.
- Do not make Media3 behavior route-selectable beyond the current observed route vocabulary.

## Current Shape

The current code already has meaningful shared playback infrastructure in `android-shared`:

- `ContinuumPlayerFactory` constructs Media3 `ExoPlayer` instances, builds `MediaItem`s, applies track-selection presets, configures buffer policy, injects authenticated data sources, enables the FFmpeg audio extension, and installs subtitle/audio delay helpers.
- `VideoPlayerMediaMounter` provides the shared `setMediaItem(mediaItem, startMs)` mount path used to avoid resume-from-zero regressions.
- `VideoPlaybackStarter` and `VideoPlaybackSessionCoordinator` centralize enough of the session start flow for mobile and TV to share start results.
- `VideoTrackSelectionCoordinator` centralizes subtitle/audio selection and remounts sidecar subtitles when needed.
- `PlaybackRoute`, `RouteCapability`, and `RouteCapabilityMatrix` exist as an observational route vocabulary, not a runtime backend selector.
- Mobile and TV still own separate ViewModels and screens, which is acceptable as long as shared player operations stop depending on raw Media3 calls at every surface.

The key limitation is that the shared layer still treats Media3 `Player` plus `ContinuumPlayerFactory` as the only possible engine. That makes MPV or any future hard-subtitle backend harder to add safely.

## Reference Client Findings

The Android reference projects point to the same architectural direction:

- Findroid wraps MPV behind a Media3 `BasePlayer` implementation, which lets UI code treat MPV much like a normal player while MPV handles hard codecs, cache behavior, and subtitles.
- Wholphin chooses between ExoPlayer and MPV at player creation time, and also shows a separate ExoPlayer plus libass path for ASS/SSA rendering.
- Jellyfin Android TV separates `PlayerBackend` from playback state, queues, timed events, surfaces, and subtitle views.

Silo should not copy any one project wholesale. The right first move is a backend boundary that keeps our current Media3 behavior intact while making the future MPV path obvious.

## Architecture

Add a small backend layer under:

`android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/`

The backend layer should contain these units:

- `VideoPlaybackBackend`
- `VideoPlaybackBackendFactory`
- `Media3VideoPlaybackBackend`
- `VideoPlaybackBackendKind`
- `VideoBackendCapabilities`

The boundary should be intentionally narrow. It is not a second playback framework. It is the common set of operations the mobile and TV player surfaces already need.

No separate backend state object or surface wrapper is part of this slice. Existing Media3 `Player` state, `VideoPlayerUiState`, and platform view attachment remain in the current mobile and TV surfaces.

## Backend Interface

`VideoPlaybackBackend` should expose:

- `kind: VideoPlaybackBackendKind`
- `capabilities: VideoBackendCapabilities`
- `player: androidx.media3.common.Player`
- `mount(spec: VideoPlayerMediaSpec, startPositionMs: Long = spec.startPositionMs, playWhenReady: Boolean = true)`
- `refresh(spec: VideoPlayerMediaSpec)`
- `selectSubtitle(track: VideoPlayerTrackEntry?): Boolean`
- `selectMountedSubtitle(subtitles: List<PlayerSubtitleInfo>, selectedIndex: Int): Boolean`
- `selectAudioTrack(track: VideoPlayerTrackEntry)`
- `applyTrackSelection(audioCaps, displayHdr, preferredAudioLanguage, preferredTextLanguage, hdrEnabled)`
- `release()`

For this slice, exposing the Media3 `Player` is acceptable because both current clients already use Media3 `Player` and `MediaController` idioms. The boundary exists to concentrate raw Media3 construction and track operations in one place, not to pretend the UI has zero knowledge of Media3 yet.

When MPV arrives later, it can either:

- implement Media3 `Player` through a `BasePlayer` wrapper, like Findroid, or
- extend the backend interface if a specific MPV operation cannot map cleanly.

This spec prefers the `BasePlayer` wrapper path for MPV because it minimizes UI churn.

## Media3 Backend

`Media3VideoPlaybackBackend` should adapt the current implementation:

- player creation from `ContinuumPlayerFactory.createPlayer`
- media item building from `ContinuumPlayerFactory.buildMediaItem`
- mounting through `mountVideoMedia`
- remounting through `refreshMountedVideoMedia`
- audio track selection through `AudioTrackManager`
- subtitle selection through `VideoTrackSelectionCoordinator`
- track preset application through `ContinuumPlayerFactory.applyTrackSelectionPresets`
- release through the underlying `Player.release`

`ContinuumPlayerFactory` can remain Media3-specific. It should not be renamed to a generic backend factory in this slice because it still constructs ExoPlayer and Media3 `MediaItem`s. The new generic factory should sit above it.

## Backend Factory

`VideoPlaybackBackendFactory` should return `Media3VideoPlaybackBackend` for every request in this slice.

It should still accept a small request object so future work does not need to redesign the call site:

- content id
- selected file version if available
- play method if known
- form factor if needed
- user backend preference, defaulting to `Auto`

The request object should be allowed to grow in the MPV phase. For this slice, it exists to make the factory seam testable and future-proof without changing behavior.

## Capabilities

`VideoBackendCapabilities` should report backend-level traits, separate from the existing route capability matrix:

- `backendKind`
- `route`
- `supportsSidecarSubtitles`
- `supportsEmbeddedSubtitleSelection`
- `supportsAudioTrackSelection`
- `supportsBufferReporting`
- `supportsSubtitleDelay`
- `supportsAudioDelay`
- `subtitleRendering`

`subtitleRendering` should be an enum:

- `Media3Text`
- `ExternalView`
- `NativeBackend`

For Media3 today:

- `backendKind = Media3`
- `route = Compatibility`, `NativeDirect`, or `Hls` only where that is already observable
- `supportsSidecarSubtitles = true`
- `supportsEmbeddedSubtitleSelection = true`
- `supportsAudioTrackSelection = true`
- `supportsBufferReporting = true`
- `supportsSubtitleDelay = true`
- `supportsAudioDelay = true`
- `subtitleRendering = Media3Text`

Do not overstate ASS/SSA quality in this slice. Media3 text rendering is the current backend, and hard subtitle fidelity remains one reason MPV or libass is planned later.

## Data Flow

1. Mobile or TV starts a video session through the existing `VideoPlaybackStarter` path.
2. The screen obtains a `VideoPlaybackBackend` from `VideoPlaybackBackendFactory`.
3. The backend owns the concrete Media3 player and associated helper objects.
4. The screen still renders its own controls and observes player state.
5. Mounting, remounting, track selection, subtitle selection, and track preset application go through the backend.
6. Existing session lifecycle and progress reporting remain unchanged.
7. Backend capabilities are exposed to UI state where useful, especially TV stats and diagnostics.

## Migration Plan

### Phase 1: Backend Types

Add the backend package and pure data types. Add tests for default Media3 capabilities and factory selection.

### Phase 2: Media3 Backend Adapter

Wrap current Media3 operations in `Media3VideoPlaybackBackend`. Keep implementation thin and delegate to existing helpers rather than moving large logic blocks.

### Phase 3: Mobile Call Site

Move mobile player creation, mounting, remounting, audio selection, and subtitle selection to the backend adapter. Preserve current mobile UI behavior and route arguments.

### Phase 4: TV Call Site

Move TV player creation, mounting, remounting, audio selection, and subtitle selection to the same backend adapter. Preserve D-pad/focus behavior and current TV control surfaces.

### Phase 5: Diagnostics

Thread `VideoBackendCapabilities` into existing stats/debug surfaces where low-risk. This should be text only and should not change player controls.

### Phase 6: Cleanup

Remove duplicated direct helper calls that now go through the backend. Keep UI-specific ViewModels and screens separate.

## Error Handling

- Backend creation failure should surface as the existing player error state.
- Media mount failure should preserve the existing player notice/error path.
- Unsupported backend preference should fall back to Media3 in this slice and log a reason, because no other backend exists yet.
- If a backend operation is unsupported, return `false` or a typed result rather than throwing from UI-triggered actions.

## Testing

Add focused tests at the boundary:

- `VideoPlaybackBackendFactoryTest`: auto/default requests return Media3.
- `VideoBackendCapabilitiesTest`: Media3 capabilities match current behavior.
- `Media3VideoPlaybackBackendMountTest`: backend delegates initial mount to `setMediaItem(mediaItem, startMs)` before prepare.
- `Media3VideoPlaybackBackendRefreshTest`: backend preserves current position and playWhenReady on subtitle remount.
- `Media3VideoPlaybackBackendTrackSelectionTest`: subtitle and audio selection delegate to the existing coordinators.
- Existing mobile start-position tests must keep passing.
- Existing TV start-position tests must keep passing.

Run the standard verification command before implementation commits:

```bash
./gradlew :shared:test :android-shared:testDebugUnitTest :androidApp:testDebugUnitTest :androidTvApp:testDebugUnitTest :androidApp:assembleDebug :androidTvApp:assembleDebug
```

Manual device checks remain required after implementation:

- Pixel: video resumes from a nonzero position.
- Pixel: subtitle selection still sticks.
- Shield: video resumes from a nonzero position.
- Shield: D-pad controls still navigate and activate.
- Shield: subtitle selection still sticks.

## Risks

- The boundary could become too abstract and slow us down. Keep it thin and centered on operations the app already performs.
- Exposing Media3 `Player` from the interface is not perfectly pure, but it is the lower-risk bridge while Media3 remains the only backend.
- Moving mobile and TV call sites at the same time can hide regressions. Implement mobile and TV as separate tasks with verification between them.
- MPV may require native packaging, ABI filtering, subtitle view behavior, and cache policy decisions. Those are intentionally deferred.

## Acceptance Criteria

- A shared backend package exists in `android-shared`.
- Media3 is represented as `Media3VideoPlaybackBackend`.
- `VideoPlaybackBackendFactory` returns Media3 for all current requests.
- Mobile video player uses the backend for mount, refresh, audio selection, and subtitle selection.
- TV video player uses the backend for mount, refresh, audio selection, and subtitle selection.
- Existing playback session start, resume, buffer, subtitle delay, audio delay, and progress behavior remain unchanged.
- No MPV, libass, or new native dependency is added.
- Full Gradle verification passes.

## Future Work

After this slice lands, the next design can add an MPV backend behind the boundary:

- choose dependency source: Maven `dev.jdtech.mpv:libmpv`, local AAR, or maintained fork
- define ABI/package-size policy
- decide Media3 `BasePlayer` wrapper versus backend-specific UI adapter
- map MPV track list to Silo subtitle/audio entries
- wire MPV cache policy
- validate ASS/SSA, image subtitles, and hard codec cases on Pixel and Shield
