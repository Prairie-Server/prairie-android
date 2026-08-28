# Whole-Application Focus Hardening Design

## Goal

Make focus behavior deterministic across the entire Silo Android application by removing recurring failure modes in TV D-pad navigation, modal ownership, asynchronous focus acquisition, detail-return restoration, dynamic-list identity, disabled controls, and phone/TV IME traversal.

This work is a follow-up series stacked on the focused PR #164 remediation. PR #164 retains its four scoped corrections; this series addresses the whole-application audit findings without turning #164 into a focus mega-PR.

## Audit basis

The read-only audit covered every production file containing Compose focus, key-event, D-pad, requester, restorer, or IME behavior. Three independent domains were inspected:

- global TV shell and content navigation;
- auth, forms, dialogs, settings, admin, profiles, and phone IME surfaces;
- playback, detail, audiobook, casting, and media controls.

The audit found no Critical issues. It found 15 Important findings plus three Minor findings, including one duplicated stable-key finding. These consolidate into six recurring causes:

1. A focus request executing without exception is treated as focus acquisition.
2. Numeric positions are persisted where stable content identity is required.
3. In-window overlays are visually modal but do not own focus or restore their opener.
4. Visual disabled state is not propagated to focus eligibility and accessibility semantics.
5. Focus transitions are not recomputed when asynchronous eligibility changes.
6. IME actions and cleanup are partially implemented or intercepted incorrectly.

## Design principles

- Observed focus is authoritative. `requestFocus()` returning or not throwing is never sufficient when correctness depends on the destination actually owning focus.
- Retries are bounded, lifecycle-cancelled, and keyed to stable target identity.
- Explicit navigation overrides stale restoration state.
- Modal UI owns focus for its full visible lifetime and returns it to the exact opener.
- Dynamic content restores by stable identity, never solely by a saved index.
- Disabled means disabled in rendering, input, focus search, and semantics.
- Key handling consumes matching phases consistently and treats held repeats as a state-machine input rather than accidental repeated taps.
- Empty, loading, error, and all-disabled states always expose a deterministic escape or action target.
- Shared helpers encode repeated policy, but screen-specific resolution remains close to the screen. This avoids a global focus coordinator with hidden cross-route coupling.

## Architecture

### 1. Bounded observed-focus policy

Add a small Android TV focus-policy unit under `androidTvApp/.../ui/focus/`. It will model:

- target state: not ready, ready, or disposed;
- request result: rejected, accepted-but-unobserved, or observed focused;
- bounded attempts separated by frames;
- lifecycle cancellation as the disposal authority;
- deterministic exhaustion fallback owned by the caller.

The policy accepts functions for target readiness, request execution, observed-focus state, and frame advancement. It contains no Compose nodes or screen state, so its retry/exhaustion behavior is covered by JVM tests. Callers still own requesters and `onFocusChanged` state.

Existing specialized flows that already observe focus correctly, such as For You restoration, remain intact unless they can adopt the helper without losing their row/card preparation semantics.

Migrations include:

- server-list initial focus;
- Collections and Collection Detail initial focus;
- Pair Device eligibility transitions;
- detail hero/body and cast-return handoffs;
- dialog initial focus;
- AI Translate and other valid empty-state dialogs.

### 2. Modal focus ownership contract

Every in-window overlay must implement the same contract:

1. Capture the opener's stable requester or focus identity.
2. Make covered background controls ineligible with `canFocus = false` while visible.
3. Attach a requester to the first eligible modal action or a dedicated focusable Close/scroll target.
4. Acquire observed modal focus with the bounded policy.
5. Cancel directional exit at modal boundaries when the UX is a trap.
6. On dismissal, wait until the overlay's focus nodes/window release ownership, then restore the exact opener with a bounded request.

This contract will be applied to Browse Filters and audiobook overlays. Existing Popup/Dialog implementations will be audited for opener restoration but will not be rewritten solely for consistency.

AI Translate's empty-source branch will provide a focusable Close action and will not run an infinite retry loop. Audiobook About will provide a focusable dismiss/scroll target. The covered audiobook player subtree will be non-focusable while any panel is active.

### 3. Stable focus identity and detail-return contracts

Shell responsibility and screen responsibility remain separate:

- The shell arms the generic outer-detail return handshake for every media-detail callback.
- The originating screen records the stable item identity needed to recover the exact launch target.
- The screen resolves that identity against fresh data after return, scrolls the target into composition, attaches the requester, and performs a bounded observed-focus request.
- If the identity disappeared, the screen uses an explicitly tested nearest/first eligible fallback.

Identity shapes vary by surface:

- row feeds: section ID plus content ID;
- flat grids/lists: content ID;
- profile selection: profile ID;
- cascade selector: library ID;
- Calendar: day/shelf identity plus content ID where needed.

Saved numeric indices may remain as fallback coordinates, but never as the primary identity after disposal or refresh.

This covers Search, Browse, Calendar, libraries, Skyline feeds, Watchlist, Favorites, History, people, Collections, Requests/My Requests, and other content routes currently bypassing the shell wrapper.

### 4. Enabled-state correctness

Reusable TV controls will propagate `enabled` to their actual clickable/focusable primitive rather than only guarding callbacks or changing alpha. Disabled controls must:

- be skipped by D-pad focus search;
- expose disabled semantics;
- reject activation at the primitive;
- never be selected as an initial-focus target.

Affected reusable primitives and call sites include option rows, Aurora buttons, PIN/join-code keys, card-overlay reset, admin scan actions, and busy/invalid auth actions. Tests will target reusable primitives first, then representative high-risk call sites.

Selectors derive interactivity from their final actionable option model. A subtitle selector with Auto, Off, and one physical track remains interactive. A genuinely noninteractive selector must not become a focusable no-op.

### 5. Asynchronous eligibility state machines

Initial focus must be keyed to the stable identity of the first currently eligible action, not only screen entry or an unrelated completion field.

- Pair Device recomputes its target across loading, resolved, error, approving, and completed states.
- Server List retries rejected requests until bounded success/exhaustion.
- Collections latch only observed acquisition and notify the shell only after acquisition.
- Profile selection distinguishes first materialization from refresh and restores the focused profile ID or nearest survivor.
- Calendar freezes Up movement while an offscreen control handoff is already in progress.

Pure target-selection and transition functions will carry most unit coverage. Compose/device tests verify attachment and observed ownership.

### 6. IME and form traversal

Phone fields will not install a no-op `KeyboardActions.onAny`. Each field family will either:

- leave `ImeAction.Next` to default focus traversal when no callback is supplied; or
- explicitly call `FocusManager.moveFocus(FocusDirection.Next)`.

Go/Done actions continue invoking their supplied callback once.

Create Collection will share the TV text-input lifecycle policy: keyboard show only after field focus, `imePadding`, hide on explicit completion/dismissal, and hide again on disposal as a safety net.

## Finding coverage

The series must address every verified audit finding:

- generic detail-return bypass across content routes;
- Browse filter focus trap and opener restoration;
- Skyline/library index-based restoration;
- Calendar repeat leakage during an in-flight offscreen handoff;
- phone `ImeAction.Next` interception;
- visually disabled but focusable TV controls;
- server-list false-return retry termination;
- Collections failed-attempt latching;
- Pair Device missing loading-to-ready handoff;
- Create Collection TV IME cleanup;
- Cascade rows missing stable keys;
- unbounded dialog initial-focus retry;
- profile refresh stealing focus;
- audiobook More/About overlay ownership;
- detail handoffs conflating execution, Boolean acceptance, and observed focus;
- AI Translate empty state without a target;
- single-subtitle selector dead focus stop.

The duplicate Cascade finding is implemented once. Runtime-only hypotheses remain verification scenarios unless device evidence promotes them to defects.

## Delivery series

### Series A — Focus foundations and enabled controls

- Add and test bounded observed-focus policy.
- Bound dialog initial-focus behavior.
- Propagate enabled state through reusable TV primitives and representative call sites.
- Add stable Cascade keys.
- Correct single-subtitle selector interactivity.

This series creates the primitives needed by later migrations without changing shell restoration.

### Series B — Async screens and IME

- Fix Server List, Collections, Pair Device, and Profile Selection transitions.
- Correct phone Next traversal.
- Add Create Collection TV IME cleanup.
- Finish Calendar in-flight repeat freezing.

### Series C — Modal ownership

- Make Browse Filters a true modal focus scope with opener restoration.
- Make audiobook panels own focus and disable the covered player.
- Add AI Translate empty-state Close focus and bounded acquisition.

### Series D — Stable content restoration

- Route all media-detail openings through shell handoff.
- Introduce per-surface stable return targets and resolvers.
- Migrate Skyline, library grids, Search, Calendar, personal lists, people, Collections, and Requests.
- Cover reorder, insertion, removal, offscreen placement, recreation, and fallback.

Because this is the largest series, implementation plans may split it into feed, grid, and heterogeneous-screen tasks while retaining one shared contract.

### Series E — Detail and playback handoffs

- Require observed focus for cast return.
- Correct false-return handling after hero scroll.
- Verify player HUD/overlay transitions against the shared policy where applicable.

### Series F — Integrated verification

- Run all focused JVM tests after every task.
- Run module and full repository tests at series boundaries.
- Assemble phone and TV debug artifacts.
- Run formatting/diff hygiene checks.
- Execute the device matrix below before release.

## Testing strategy

All behavioral changes use red-green TDD.

### Pure JVM tests

- bounded retry: false, exception, accepted-but-unobserved, observed, exhausted, stale identity, cancellation;
- stable target resolution across reorder, insertion, cross-row move, removal, and empty data;
- async eligible-target transitions;
- Calendar in-flight repeat actions;
- selector actionability from final options;
- profile survivor fallback;
- IME action policy.

### Compose focus tests

- disabled primitives expose disabled semantics and are skipped;
- modal boundary traversal cannot reach covered content;
- dismissal restores the opener;
- async attachment succeeds after initial rejection;
- dynamic keyed rows retain identity after reorder;
- empty/error/all-disabled branches retain a focusable escape.

Where local JVM Compose tests cannot faithfully model platform focus windows, add instrumented tests and retain a physical-device gate.

### Regression matrix

- every direction at first/last targets;
- press versus held repeat;
- loading, populated, empty, error, and disabled states;
- warm recomposition, screen disposal/recreation, and process-saved state;
- refresh/reorder/removal while behind detail or while an overlay is open;
- Back, outside dismiss, selection dismiss, and successful completion;
- keyboard visible/hidden and resize/pan behavior;
- explicit root reselection versus natural detail return.

## Device validation

Run on at least one Shield/Google TV device and one Fire TV device:

- full top-menu and content-route D-pad sweep;
- native held-key repeat races;
- exact Back restoration from offscreen items on every content family;
- refresh/reorder/removal during detail return;
- Browse and audiobook modal boundary traversal;
- player HUD, subtitle search, AI Translate, and popup key routing;
- Gboard/Leanback/Fire TV keyboard traversal and cleanup;
- TalkBack/Switch Access disabled-state and traversal checks where supported.

Device failures are converted into reproducible tests or explicit platform-specific guards before release.

## Non-goals

- No global singleton focus coordinator.
- No navigation redesign or visual redesign.
- No unrelated media, networking, or settings refactor.
- No assumption that one platform's spatial focus behavior proves another's.
- No merging of the follow-up series into PR #164.

## Completion criteria

- Every verified audit finding maps to an implemented task and regression test.
- All new focus requests that affect correctness either observe acquisition or have a documented reason not to.
- Dynamic restoration uses stable identity.
- Modal surfaces own and restore focus deterministically.
- Disabled controls agree across visuals, input, focus, and semantics.
- Phone and TV IME flows pass their traversal/cleanup tests.
- Full automated suites and both debug builds succeed.
- Required Shield and Fire TV scenarios pass or are documented as release blockers.
