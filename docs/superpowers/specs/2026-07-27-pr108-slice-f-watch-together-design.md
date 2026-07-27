# PR 108 Slice F: Watch Together Forward-Port Design

**Date:** 2026-07-27  
**Status:** Approved by the existing Watch Together design plus the user's
explicit instruction to continue Slice F and validate with multiple emulators  
**Base:** `split/108-e-subtitles` / draft PR #116  
**Source:** Closed archival PR #108 and the approved
`2026-06-12-watch-together-design.md`

## Goal

Forward-port the complete, user-reachable Watch Together vertical slice onto
the reviewed A-E stack, while correcting connection ownership, room
replacement races, credential exposure, and the gap between fake-flow tests
and real two-participant behavior.

Success means phone and TV can create, join, vote, promote, enter playback,
exchange transport state, recover from transient disconnects, and terminate
cleanly when the room closes. One application-scoped owner must hold exactly
one room connection across lobby, browsing, and player screens.

## Considered approaches

1. **Forward-port the PR 108 net behavior behind corrected ownership
   boundaries (selected).** Preserve the already-designed UI/protocol surface,
   reconcile each source commit against the current A-E stack, and replace the
   fragile lifecycle pieces before publication. This keeps traceability while
   making review about the resulting behavior rather than old conflict
   resolutions.
2. **Cherry-pick the original Watch Together commits.** This preserves commit
   history but replays obsolete screen-owned connection logic and conflicts
   with the current player/subtitle/auth stack. It would require corrective
   commits whose review surface is harder to understand.
3. **Rewrite only a minimal host/join/player path.** This reduces immediate
   code volume but drops the voting, suggestion, lobby, and TV behavior the
   approved feature requires. It would not be a coherent replacement for the
   PR 108 slice.

## Ownership and concurrency

`RoomSession` is the application-scoped authority for room membership and the
single reconnect job. Lobby and player components observe it and send through
the repository; they never start their own socket loops and never call
`repository.reset()` directly.

`enter(roomId)` is serialized. Re-entering the active room is idempotent.
Replacing a room cancels and joins the previous connection before publishing
the replacement owner. `leave()` cancels and joins before clearing repository
state. A monotonically increasing connection generation prevents an obsolete
connection's cancellation/finally path from clearing the current client,
snapshot, token, or close reason.

Create/join responses bind the room id, room token, and captured
`AuthScopeSnapshot` together. Room-scoped REST and websocket work captures that
immutable lease, so a late operation from room A cannot use room B's token or
mutate room B's state. Server/profile/logout or credential-generation changes
terminate the lease rather than silently continuing under a replacement
identity.

## Realtime transport and reconnect

The event model distinguishes:

- an explicit server `room_closed` frame, which is terminal and never
  reconnects;
- a transport EOF/exception, which is transient and participates in capped
  exponential backoff;
- cancellation by the current owner, which is silent and cannot mutate a
  replacement connection.

Each physical socket attempt publishes an "open" readiness epoch. One-shot
frames such as `attach_session` and the initial ping are sent only after that
attempt is writable, and are re-sent after every successful reconnect.
Ordinary outbound calls return whether the frame reached the current attempt;
they do not silently succeed before a handshake. Attach/readiness/latest-state
messages are retained or coalesced until sent; user transport requests surface
failure instead of disappearing.

The reconnect cap counts every failed or unexpectedly ended attempt, including
an attempt that emitted frames before dropping. A connection resets the
failure window only after receiving an authoritative snapshot and remaining
open for at least 30 seconds; a single frame cannot keep a flapping connection
alive forever. Backoff uses the protocol's 500 ms, 1 s, 2 s, and 5 s steps. A
terminal close, identity change, or explicit leave stops the loop immediately.

Discrete commands and pong samples are not delivered through a replay-zero
flow before subscribers exist. Player controllers subscribe to commands,
pongs, close state, and the connection-epoch `StateFlow` before attaching.
Every new epoch triggers attach plus the initial ping, so no command can be
valid for that local playback session before its collector is ready.
Reconnection reconciles from the authoritative snapshot and never replays an
already-applied command.

## Credentials

The Silo auth plugin already attaches same-origin `Authorization`,
`X-Profile-Id`, and `X-Profile-Token` headers to websocket handshakes. Slice F
removes the redundant access JWT from the websocket URL and verifies the
header/query boundary with a real handshake test.

The current server contract still reads `room_token`, `profile_id`, and
`profile_token` from the query. Changing those requires coordinated server
work outside this Android slice, so the residual exposure is documented rather
than hidden. All websocket URLs remain same-origin and pass the existing
cleartext-consent/origin policy before credentials leave the client. The
cleartext policy must treat `ws://` as `http://` and `wss://` as `https://`;
tests prove that an unapproved cleartext websocket is blocked before the
engine observes any header or query credential.

## UI and player integration

Port the PR 108 phone/TV entry, lobby, suggestion/voting, host override, and
browsing-to-suggest behavior as one vertical feature. Screen ViewModels call
`RoomSession.enter` to adopt an already-created/joined room, but screen
destruction does not leave it. Explicit leave, host close, or a terminal server
close ends the session.

Phone and TV room controllers attach the current playback session after
connection readiness and again after reconnect. They keep the existing
`RoomSyncEngine` authority checks, command de-duplication, selection/session
gates, execution scheduling, drift thresholds, ready/buffering barrier, and
state-report suppression window.

## Automated verification

Test-first coverage includes:

- repository transport EOF versus explicit room-close behavior, reconnect cap,
  stable-window reset, cancellation, generation ownership, identity changes,
  and room-token scoping;
- `RoomSession` same-room idempotence, A-to-B replacement with a blocked old
  connection, leave during connect, and concurrent enter calls;
- real Ktor/OkHttp websocket handshakes against `MockWebServer`, verifying
  access JWT absence from the URL, Authorization/header presence, early
  server-frame delivery, outbound frame ordering, cleartext consent,
  disconnect, and reconnect;
- a two-participant transport harness exercising host and guest snapshots,
  attach ordering, commands, votes, promotion/override, buffering/ready, drift
  correction, terminal close, and replacement;
- phone/TV lifecycle/controller and source-surface tests.

## Required two-emulator validation

Run at least two Android emulator instances concurrently on this Mac with
separate app data and participant identities. Prefer the dedicated
`Silo_Phone` and `Silo_TV` AVDs; do not reset or repurpose unrelated physical
devices.

Against a local or configured test backend:

1. host a room and join from the second emulator;
2. verify websocket handshake and attach-session ordering;
3. exercise host play/pause/seek and permitted/blocked guest controls;
4. add/vote/unvote, promote, and use host override;
5. observe drift correction, seek, buffering, and ready propagation;
6. interrupt and restore networking to prove transient reconnect/re-attach;
7. background/foreground each participant without creating duplicate owners;
8. replace one held room with another;
9. close or depart as host and verify the guest receives terminal room close.

Capture ADB logcat, emulator/device metadata, backend logs, timestamps, room
ids, and the exact commands in a reproducible evidence document. Redact
credentials and classify every unmet step as an app defect, backend defect, or
environment limitation.

## Traceability and scope

The source commits are `9fe27a27`, `323510a2`, `c6ba32ef`, `e171e011`,
`acfac18a`, `fca6d7c6`, `bc58049b`, `ea5756ec`, `8f36f7d2`, `530f9488`,
`864de27e`, `79b4278d`, `f91aa62e`, and `923a1a7f`.

Slice F excludes TV request UX, catalog/mixed-library polish, and unrelated
home/startup work. Those remain Slice G.
