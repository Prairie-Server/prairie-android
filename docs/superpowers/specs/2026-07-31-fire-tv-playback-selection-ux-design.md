# Fire TV Playback Selection UX Design

**Date:** 2026-07-31
**Status:** Approved for implementation planning
**Baseline:** `Silo-Server/silo-android` `main` at `3b2044c8`

## Problem

The current Android TV release has three related playback-selection defects:

1. The Version, Audio, Subtitles, and Edition menus on item detail pages do not show a legible TV focus state. Version and Subtitles are the reported cases.
2. Moving from one episode to the next does not carry the viewer's active source-resolution intent or subtitle choice. This affects automatic/explicit Up Next and the refreshed next-up controls on series and season detail pages.
3. Long in-player option lists, most visibly Subtitle Track, can move focus below the clipped viewport without scrolling the focused row into view.

These are Android TV client defects. They require no server, API, database, or profile-preference changes.

## Verified Causes

### Detail selector contrast

`TvAnchoredSelectorMenu` embeds phone Material 3 `DropdownMenu` and `DropdownMenuItem` components inside the TV Material theme. The rows have explicit idle foreground colors but no TV-focused container/content treatment. Selection adds only a checkmark and semantics. The component itself documents this missing TV focus grammar.

### Episode-to-episode selection continuity

Durable track selections are correctly scoped to `(server, profile, contentId, fileId)`. A different episode necessarily has different content and file identities. The detail refresh path clears the old next-up state and restores only state already saved for the new episode. The player Up Next path carries only a resolution-shaped quality string; it drops subtitle intent and cannot distinguish versions sharing a resolution. The target episode's `lastFileId` may also override the carried quality.

Raw file IDs and subtitle indexes must not cross episode boundaries. Android's current `FileVersion` model also lacks a stable edition identity.

### In-player picker scrolling

`HudPickerDialog` eagerly composes all rows in a clipped `Column.verticalScroll`, preserving a complete modal focus graph, but assumes focus movement will relocate that scroll container. Fire TV may focus a clipped child without scrolling it onscreen. A previous `LazyColumn` implementation performed explicit scrolling but was removed because lazy composition caused focus-boundary leakage.

## Apple Comparison

Current `Silo-Server/silo-apple` `main` at `e7de923a` has the same episode-continuity gap on both iOS and tvOS. `PlayerViewModel.playNextEpisodeNow()` starts the next episode with file, audio, and subtitle overrides all `nil`, then applies the new item's stored/profile preferences. Series and season detail also reset next-up selections when identity changes.

Apple is therefore not the continuity behavior to copy. Its native tvOS menu and picker controls do not share Android's contrast or scrolling implementation defects.

## Chosen Design

### 1. TV-native focused selector rows

Keep the existing anchored detail menu and selection callbacks. Replace the implicit phone-menu focus appearance with an explicit row visual-state policy:

- Focused: existing TV `FocusedContainer` background, `FocusedContent` text/icons, and a visible focused border.
- Selected but not focused: restrained selected fill/border plus the existing checkmark.
- Idle: current dark surface and high-contrast foreground.
- Disabled: current disabled semantics and visibly muted content.

The same treatment applies to every `TvAnchoredSelectorMenu` consumer so Audio and Edition do not retain the latent defect.

### 2. Session-scoped semantic episode handoff

Represent the outgoing viewer intent without reusing episode-local IDs:

- Source intent: normalized resolution plus available codec, HDR/Dolby Vision, and container characteristics. This is a preference, not an exact file identity.
- Subtitle intent:
  - `Auto`
  - explicit `Off`
  - explicit semantic track fingerprint: normalized language, forced/SDH flags, source kind, and codec/format where available.

When the next episode's watch detail is available, resolve the intent deterministically:

1. Preserve `Off` exactly.
2. For an explicit subtitle, select the best semantic match. Prefer language and accessibility/forced meaning over incidental index or filename. If no valid match exists, return to the normal profile `Auto` behavior.
3. For source selection, prefer the closest semantic version match. Resolution is primary; codec/HDR/container break ties. Never transfer a raw file ID. If no meaningful match exists, use the existing automatic version policy.
4. A carried explicit session choice takes precedence over the target episode's stale `lastFileId` for that transition. With no carried choice, existing target-episode state and automatic behavior remain unchanged.

Carry this handoff through both TV paths:

- Player Up Next request and navigation route into the next player.
- Series/season next-up identity refresh while the detail screen remains alive.

The handoff is process/session scoped. It does not rewrite the per-episode durable preference key, create a series-wide preference, or alter server profile settings. Once the target episode resolves and the viewer changes a selection, existing per-item persistence continues normally.

Audio continuity is not added in this change because it was not reported and materially expands matching semantics. Existing audio behavior remains unchanged.

### 3. Explicit focused-row relocation in the HUD picker

Keep the eager `Column` so every modal row remains in the focus graph. Give each option row a `BringIntoViewRequester`; when it gains focus, request that the row be brought into the clipped viewport. This covers initial programmatic focus and every D-pad transition without restoring the previous lazy-list focus-boundary regression.

The shared correction applies to Subtitle Track and all other long HUD pickers, including delay lists.

## State and Lifecycle Rules

- The semantic handoff belongs to a single active TV browsing/playback flow.
- It is discarded when the next episode consumes it, the user exits the flow, or process state is lost.
- Profile/server changes do not inherit it.
- Explicit `Off` is distinct from `Auto` throughout routing and resolution.
- Watch Together authority and its auto-advance suppression are unchanged.
- Playback session shutdown ordering is unchanged.

## Testing

### Focus contrast

- Unit-test a pure selector-row visual-state resolver for focused, selected, idle, and disabled states.
- Verify focused foreground/background meet the established TV inverted-focus policy.
- Manually D-pad through Version, Subtitle, Audio, and Edition menus where present.

### Episode handoff

- E1 explicit resolution/source intent resolves to the closest E2 version.
- A previously watched E2 `lastFileId` does not override an active carried choice.
- Same-resolution candidates use codec/HDR/container tie-breakers deterministically.
- No meaningful source match falls back to existing automatic selection.
- Explicit subtitle language/forced/SDH/source/format resolves to the closest E2 track.
- Missing subtitle match falls back to profile Auto.
- Explicit Off remains Off.
- Auto remains Auto.
- Raw file IDs and track indexes are never transferred.
- Both Player Up Next and series/season next-up refresh use the same resolver.
- Existing same-episode persistence tests remain green.

### Picker scrolling

- A picker opened with an offscreen selected/focused row brings it onscreen.
- Repeated D-pad Down/Up keeps the focused row visible across viewport boundaries.
- Focus remains trapped within the modal at the first and last rows.
- A short list does not move unnecessarily.

## Verification

Run focused Android TV unit tests for the new policies and affected existing suites, then the complete Android TV unit suite, supply-chain verification required by the repository, and Android TV debug/release compilation. Perform a Fire TV or TV-emulator D-pad smoke covering detail selector contrast, long subtitle-list scrolling, and E1-to-E2 continuity when an appropriate device/test fixture is available. No device installation is authorized by this design.

## Out of Scope

- Server/API/schema changes.
- Series-wide durable playback preferences.
- Stable edition identity additions to the Android model.
- Cross-episode audio-track continuity.
- Phone behavior changes.
- Replacing the entire anchored selector popup architecture.
- Installing a build on a physical device.
