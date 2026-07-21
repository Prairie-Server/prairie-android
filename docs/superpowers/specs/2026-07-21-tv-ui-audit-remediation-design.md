# Android TV UI Audit Remediation Design

**Date:** 2026-07-21
**Status:** Approved for implementation planning
**Reference:** `ops-notes/tv-ui-deep-dive-2026-07-21.md`
**Baseline:** `fix/usability-audit` at `29a258cb`

## Objective

Resolve every actionable Android TV issue identified by the independent review of the TV UI deep-dive, including behavior and copy differences that are product choices rather than crashes. The current tvOS implementation is the canonical behavior unless the Android repository documents an explicit platform exception.

The existing fixes in `29a258cb` remain the baseline. This work corrects regressions or incomplete fixes in that batch, completes confirmed open findings, aligns intentional parity differences, and verifies the already-correct fixes without rewriting them.

## Product and Platform Constraints

- Keep ebooks and Reading off Android TV.
- Keep rich admin screens and Watch Together inaccessible. Existing defensive layout fixes may remain, but this work must not expose those routes.
- Preserve Android-specific shell, Compose focus, and Media3 architecture. Match tvOS behavior rather than porting tvOS implementation details literally.
- Do not modify finding 41's `heightIn(min = 108.dp)` solely to address the reported clipping. A minimum height is not a clipping bound. Any independently reproducible child-level font-scaling defect must be handled at the child that imposes the restrictive size.
- Prefer deterministic unit tests for extracted decisions and formatting. Avoid broad unrelated refactors.

## Remediation Stages

### 1. Navigation and Focus

#### Calendar Up routing (finding 5; regression in current patch)

Track the identity/index of the shelf that owns focus, not merely whether any shelf is focused. The shell Up fallback must:

- send focus to the week controls only from the first focusable event shelf;
- call normal `FocusManager.moveFocus(Up)` from later shelves so focus advances one shelf at a time;
- retain the existing guarded retry choreography when returning to the selected day;
- delegate normally when focus is already in controls or no event shelf owns focus.

The routing decision will be expressed as a small testable function or state model, with cases for first shelf, later shelf, controls, and the in-flight return state.

#### Remaining focus/navigation behavior

- Restore the previous horizontal position when re-entering cast rails instead of always selecting card zero (51).
- Restore library-grid position after returning from detail using the same saveable focus ladder pattern used by Home (23).
- Make profile-menu Back return to the avatar/profile control rather than tunneling to Home (24).
- Route Down from an action button to the geometrically corresponding playback selector instead of always landing on the leading Version selector (28).
- Prevent Search from re-requesting the text field and reopening the IME when returning from results or child content unless search entry itself requests text focus (22).
- Preserve the existing dialog initial-focus repairs, including Cancel ownership and phase-aware AI-dialog retries (7, 25, 26, 35, 48).
- Preserve audiobook overlay focus containment and the bookmark long-click action (6, 34).

### 2. Playback State, Timing, and Persistence

- Persist next-up version, audio, and subtitle choices using the same session/persistence path as the main playback selectors; restore those choices when the next item becomes active (33).
- Update the actively displayed marquee when enrichment arrives for the still-current content ID, while retaining cache population for future visits (21).
- Add a cancellable 150 ms marquee-preview debounce keyed by candidate identity. Rapid focus changes must not publish intermediate previews (31).
- Render scrubber elapsed/remaining labels from `scrubPreviewSec` while scrubbing and from playback position otherwise (29).
- Suppress the reconnecting spinner in PiP (42).
- Align episode OK behavior with tvOS: selecting an episode opens/browses its detail rather than immediately starting playback, unless an explicit Play action is used (10).
- Keep the fixed combined-subtitle-index persistence mapping and Skip Intro gating from the current patch (1, 2).

### 3. Track Labels, Ordering, Badges, and Selector State

#### Audio labels (finding 12; incomplete current fix)

Build the base label from language, codec, and channel layout, then retain a meaningful non-redundant display title as a qualifier. Titles such as Commentary, Descriptive Audio, Director Commentary, or Alternate Mix must distinguish otherwise identical tracks. Server identity strings, filenames, bare language duplicates, codec duplicates, and channel-layout duplicates must not be repeated.

Examples:

- `English DTS 5.1` remains unchanged when the title is redundant or empty.
- A structured track titled `Commentary` becomes `English AC3 5.1 (Commentary)`.
- Two structured English AC3 5.1 tracks with Normal and Commentary titles remain distinguishable.

#### Other selector corrections

- In the audio menu, exactly one row is selected. Auto is selected only when automatic selection is active; the currently resolved physical track must not also show selected (17).
- Map Dolby Vision and HDR accurately, and retain Atmos/Dolby audio identity instead of reducing it to generic `HDR` or `AUDIO` badges (18).
- Apply the same semantic subtitle display order as tvOS rather than raw catalog order (19).
- Use the shared human-readable subtitle and audio formatters throughout the HUD, AI translation dialog, and selected-value summaries (13-15).
- Hide or disable single-option selectors in the same circumstances as tvOS (11).
- Match tvOS automatic-selection copy, including the no-track state (20).
- Constrain selector buttons and dropdown rows with weights, maximum lines, and ellipsis so long localized values cannot push actions off-screen (16, 45).
- Preserve the up-next autoplay control repair (3).

### 4. Layout and Presentation

- Prevent the For You pills from overlapping the first content row by reserving explicit vertical space or applying the tvOS-equivalent overlay treatment (37).
- Reconcile the 40 dp/44 dp top-inset discrepancy using the canonical menu/content alignment token rather than another local literal (38).
- Show a meaningful empty state when every Home section is filtered out (39).
- Let Search status text grow with font scale instead of clipping inside a fixed 18 dp region (40).
- Make subtitle previews reflect selected font, size, position, and outline styling; do not use a generic rectangular border as an outline substitute (43).
- While a logo image is loading or fails, keep the textual title visible; replace it only after the logo has rendered successfully (32).
- Make the HUD picker distinguish selected-unfocused from focused using more than the same white background treatment (30).
- Preserve the synopsis, episode-title, profile/server ellipsis, white-on-white synopsis, and admin/session sizing fixes already in the baseline (8, 9, 36, 44, 46).

### 5. Accessibility

Every selectable TV picker row must expose selected state to accessibility services, including `TvOptionDialog`, settings picker sheets, full-screen picker replacements, HUD selectors, and AI-translation choices (47).

Color swatches must expose a useful content description or text semantics containing the color name/value and selected state. Selection must never be communicated by color alone. Focus and selection visuals must remain distinct at normal and high-contrast viewing conditions.

### 6. Cleanup and Contract Accuracy

- Remove `TvFullScreenPicker` if it remains unreferenced after selector remediation; otherwise replace its dead-code status with a real, documented use (49).
- Correct stale comments that describe subtitle indexes as catalog ordinals when the runtime contract is a combined selection index (50).
- Keep the alphabet-rail stale-fallback repair and calendar controls choreography documented in terms of the shell fallback identity protocol (4).

## Finding Disposition

The implementation must account for all 51 reported findings:

- **Already addressed in the baseline and retained/verified:** 1-4, 6-9, 13-15, 25-27, 34-36, 44, 46, 48.
- **Current baseline fixes requiring correction or completion:** 5, 12, 47.
- **Confirmed open behavior or presentation work:** 16-19, 21-24, 28-33, 37, 39-40, 42-43, 45, 51.
- **Approved tvOS parity/product choices:** 10, 11, 20, 38.
- **Cleanup/documentation:** 49, 50.
- **Rejected as stated:** 41. No arbitrary minimum-height change will be made. A different reproducible child constraint may be fixed if demonstrated by evidence.

## Testing Strategy

Implementation follows red-green-refactor for each behavior group.

Focused tests will cover, at minimum:

- calendar Up routing for the first shelf, later shelves, controls, and return-in-progress;
- audio title qualification and redundancy elimination;
- exactly-one selected audio row under Auto and explicit selection;
- badge classification for HDR10, Dolby Vision, Atmos, and ordinary audio;
- semantic subtitle ordering;
- scrubber preview label source;
- marquee debounce cancellation and active-content enrichment identity checks;
- next-up selector persistence mapping;
- Home empty-state decision and PiP reconnect visibility where those decisions can be isolated;
- focus restoration state models used by grids and rails.

Compose-only behavior that cannot be covered reliably with local JVM tests will be verified through the narrowest available Compose/instrumentation test. If no suitable harness exists, verification will include source-level contract checks plus manual TV/emulator scenarios documented in the handoff; lack of a connected TV/emulator will be stated explicitly.

Final verification requires:

1. all new focused tests passing after having failed for the expected pre-fix reason;
2. `./gradlew :androidTvApp:testDebugUnitTest --rerun-tasks` passing;
3. `./gradlew :androidTvApp:assembleDebug` passing;
4. `git diff --check` passing;
5. a final review against this finding-disposition checklist and the tvOS reference behavior.

## Delivery and Review Boundaries

Work will be staged by risk so navigation/focus, playback state, labels/order, presentation/accessibility, and cleanup can be reviewed independently. No route exposure, unrelated architectural migration, dependency upgrade, or mobile-app redesign is included.
