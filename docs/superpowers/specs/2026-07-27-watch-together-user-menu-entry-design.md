# Watch Together User-Menu Entry — Design

**Date:** 2026-07-27

**Status:** Approved design, awaiting written-spec review

**Clients:** Android phone and Android TV

## Goal

Expose Watch Together from each client's user/profile menu without requiring a
title first. The new entry is a lightweight launch surface for hosting an empty
vote room, joining by code, or resuming the room already owned by the current
app session. The existing title-detail entry remains available and continues to
host with that title preselected.

This product decision supersedes the older “hidden from user menus” direction
for Watch Together only. It does not expose rich administration or change any
other menu policy.

## User experience

Both profile menus add a **Watch Together** row in their content/action group,
before **Requests** when Requests is present and before the divider that
precedes settings and account actions.

Selecting the row closes the profile menu and opens a transient, dedicated
Watch Together entry surface:

1. **Resume current room** appears first only when the current authenticated
   app session owns a non-terminal room snapshot.
2. **Host a room** creates a room in vote mode with no selected content, then
   opens the existing lobby.
3. **Join by code** opens the existing code-entry flow. A successful join uses
   the existing room destination decision: a room with playable selected
   content goes to the synchronized player; an unselected room goes to the
   lobby. The existing host-alone rule may keep a host in the lobby to share
   the invite.

On phone, the dedicated surface follows the existing modal-sheet idiom. On TV,
it follows the existing focused popup/dialog idiom. It is not a new persistent
Watch Together home or a replacement for the lobby. When Resume is available,
it receives initial TV focus; otherwise Host receives initial focus. Back,
dismissal, and busy-state input blocking match the existing entry surfaces.

Resuming uses the same destination decision as joining. It does not create,
join, or reconnect through a second path; the selected destination adopts the
existing room through `RoomSession`.

## Existing title-detail behavior

Movie, episode, and existing playable-series detail affordances remain intact.
Their **Watch Together** action continues to open the title-bound entry surface:

- **Host a room** creates the room and sets the current title/file selection.
- **Host a vote room** and **Join by code** retain their current behavior.
- Existing feature-policy and media-type gates remain unchanged.

The new menu entry never invents a content ID, opens a title picker, or changes
the meaning of the detail action.

## Architecture and state ownership

This is an additional presentation entry point over the existing Watch
Together system:

- `WatchTogetherRepository` remains the only owner of REST room operations,
  room credentials, auth-scope validation, room snapshots, websocket state,
  voting, suggestions, and server errors.
- `RoomSession` remains the process-scoped connection owner and the only path
  for adopting, replacing, leaving, or closing a room connection.
- The existing phone and TV lobby, player, websocket, voting, and routing
  surfaces remain authoritative.
- The entry controllers reuse the existing create/join state machines. The
  title-free host action is explicitly
  `CreateRoomRequest(selectionMode = RoomSelectionMode.Vote.wire)` and must not
  call `setSelection`.
- Phone and TV may keep platform-specific composables and navigation types, but
  they must derive equivalent entry actions and destination decisions from the
  same room snapshot semantics. No second repository, room cache, websocket
  client, or parallel “menu room” state is introduced.

“Current room” means the valid, non-terminal room represented by the existing
process-scoped repository/session state for the current server and profile.
Identity transitions and room termination already clear that state. This
feature does not persist room credentials, discover rooms after process death,
or add a server-side “my active room” lookup. A successful create or join may
replace the current room through the existing generation/lease and
`RoomSession` replacement rules.

## Routing and lifecycle

- Empty vote-room creation always routes to the existing lobby with its
  `roomId`; the lobby establishes the existing session adoption and websocket
  flow.
- Join and Resume route to the existing player only when the shared destination
  rules say the snapshot is ready for playback; otherwise they route to the
  existing lobby.
- Repeated taps while an operation is busy are ignored. The entry surface
  cannot be dismissed while its create/join operation is in flight, matching
  current behavior.
- Opening or dismissing the entry surface does not reset a current room.
- TV closes the profile dropdown before showing the popup so the dropdown and
  popup never compete for D-pad focus. Dismissing the popup returns through the
  shell's established focus-restoration path.
- Phone and TV consume one-shot navigation results before navigating, preventing
  recomposition from launching the lobby or player twice.

## Authentication, transport, and errors

The menu action is available only in the authenticated profile shell and uses
the current server/profile scope. All calls continue through the repository and
existing network clients, preserving auth-scope transition barriers, cleartext
consent, room-token handling, reconnect behavior, and credential redaction.
Room credentials must not be copied into UI state, logs, or new route
parameters.

Create and join failures remain on the entry surface using the existing
user-facing error mapping. A failed operation does not navigate or discard a
previously active room. Lobby/player websocket and terminal-room errors remain
owned by those existing surfaces. No new fallback transport or retry policy is
added.

## Test strategy

Focused automated coverage must establish:

- **Phone menu visibility:** Watch Together is present in the authenticated
  profile menu, invokes the entry surface, and does not disturb Requests or
  account actions.
- **TV menu visibility and focus:** the row is present in the profile dropdown;
  opening it closes the dropdown; Resume is initially focused when present,
  otherwise Host is; Back restores focus without leaking focus behind the
  popup.
- **Empty host flow:** both clients issue one vote-mode create request, never
  call `setSelection`, and navigate to the existing lobby with the returned
  room ID.
- **Join routing:** code normalization/validation and errors remain intact;
  selected rooms route to the synchronized player and unselected rooms route to
  the lobby on both clients.
- **Resume routing:** Resume appears only for valid current-session state,
  routes through existing snapshot rules without a create/join call, and
  disappears after room termination or an identity change.
- **Parity:** the phone and TV entry surfaces expose the same action set and
  state-dependent behavior, with platform-appropriate presentation.
- **Regression:** title-detail Watch Together remains visible under its current
  policy/media gates and still hosts with the selected content/file rather than
  creating an empty room.

Existing repository, `RoomSession`, lobby, websocket, voting, player, auth,
cleartext-consent, and replacement-race tests remain part of the verification
gate. Device checks should cover phone touch interaction and TV D-pad
focus/back behavior; they do not replace the automated routing tests.

## Out of scope

- A title picker before room creation.
- A new full Watch Together home, room browser, or room history.
- Cross-process room restoration or a new server endpoint.
- Changes to room protocol, websocket ownership, voting rules, player sync,
  cleartext policy, or authentication.
- Removal or redesign of the title-detail Watch Together action.
- Changes to non-Android clients.

## Acceptance criteria

The feature is complete when an authenticated phone or TV user can open Watch
Together from the profile menu, host an empty vote room into the existing
lobby, join through the existing code flow, and resume current in-process room
state when available; both clients behave equivalently, TV focus is
deterministic, title-detail hosting still preselects its title, and no parallel
room/session architecture or credential exposure is introduced.
