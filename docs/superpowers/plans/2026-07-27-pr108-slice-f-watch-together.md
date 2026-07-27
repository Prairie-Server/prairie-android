# PR 108 Slice F: Watch Together Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development or superpowers:executing-plans.
> Every behavioral change follows test-driven development.

**Goal:** Forward-port PR 108's complete Watch Together feature onto Slice E,
correct its production transport/session/security defects, and validate a real
host/guest flow on two concurrent Android emulators.

**Architecture:** `RoomSession` is the application-scoped owner of one
immutable room/auth lease and one reconnect job. The repository folds room
state only for the current generation. The realtime client distinguishes
protocol closure from transport termination, exposes a writable connection
epoch, and keeps credentials on same-origin approved transports. Phone and TV
controllers subscribe before attach and re-attach on every epoch.

**Tech stack:** Kotlin Multiplatform, coroutines/Flow, Ktor WebSockets,
OkHttp/MockWebServer, Koin, Android Compose, Gradle, ADB/emulator tooling.

## Constraints

- Base is draft Slice E PR #116 (`split/108-e-subtitles`).
- Preserve closed PR #108 as archival reference; do not merge any PR.
- Do not import TV request UX, catalog/mixed-library polish, or unrelated
  home/startup work; those belong to Slice G.
- Preserve the A-E auth-origin, Room, playback, subtitle, and lifecycle
  contracts.
- Fully removing room/profile credentials from query parameters is blocked on
  server protocol support; remove the redundant access JWT now and document
  the residual contract.
- Do not use a physical device for destructive validation. Use at least two
  concurrent emulators with separate app data/identities.

---

### Task 1: Correct realtime transport semantics and credential boundaries

**Files:**
- Modify `shared/.../network/WatchTogetherRealtimeEvent.kt`
- Modify `shared/.../network/WatchTogetherRealtimeClient.kt`
- Modify `shared/.../network/CleartextOriginConsent.kt`
- Modify focused common tests
- Add Android/JVM real-websocket tests under `android-shared/src/androidUnitTest`

**Red tests:**
- Normal socket EOF and I/O failure are transient transport termination, not a
  decoded `room_closed`.
- Cancellation never emits a terminal close.
- The handshake URL omits the access JWT while same-origin Authorization and
  profile headers are present.
- `ws://` requires the exact `http://` origin's cleartext approval before the
  engine sees any credential.
- Early server snapshot and outbound attach/ping ordering survive a real
  MockWebServer upgrade.

**Implementation:**
- Split explicit protocol room closure from physical connection termination.
- Re-throw cancellation and surface typed transport termination.
- Protect mutable session ownership by connection identity.
- Encode the room id as a path segment.
- Rely on the pinned/same-origin auth plugin for the access bearer; retain only
  server-required query fields.

- [ ] Establish RED.
- [ ] Implement minimum production changes.
- [ ] Run forced focused tests green and commit.

### Task 2: Make repository reconnect and room leases production-safe

**Files:**
- Modify `shared/.../repository/WatchTogetherRepository.kt`
- Modify `shared/.../network/api/WatchTogetherApi.kt` only where scoped room
  binding requires it
- Modify `shared/.../repository/WatchTogetherRepositoryTest.kt`
- Add a deterministic per-attempt transport harness

**Red tests:**
- EOF/I/O reconnect; decoded room close never reconnects.
- Every unstable failed attempt counts even after one healthy frame.
- Snapshot plus 30 seconds of stable connection resets the failure window.
- Cancellation/obsolete generation cannot clear or fold into a replacement.
- Room A's late REST/socket completion cannot use or mutate room B.
- Missing room fails fast.
- Attach/readiness/user transport sends report actual delivery.

**Implementation:**
- Capture immutable room id/token/auth scope in a generation lease.
- Gate fold, close, send, and final cleanup on the current generation.
- Expose connection state/epoch.
- Keep capped 500/1000/2000/5000 ms backoff with the 30-second stability rule.
- Make voted-id state concurrency-safe and retain the PR 108 fail-fast room
  behavior.

- [ ] Establish RED.
- [ ] Implement minimum production changes.
- [ ] Run forced focused tests green and commit.

### Task 3: Add the application-scoped RoomSession owner

**Files:**
- Create `shared/.../watchtogether/RoomSession.kt`
- Modify shared Koin registration
- Add `RoomSessionTest.kt`

**Red tests:**
- Same-room enter is idempotent.
- A-to-B replacement cancels and joins blocked A before B starts.
- Late A frames/finalizers cannot clear B.
- Concurrent enters serialize deterministically.
- Leave cancels and joins before reset.
- Server/profile/logout/credential-generation change ends the lease.

**Implementation:**
- Port the intent of `864de27e`/`79b4278d`, replacing cancel-only behavior with
  cancel-and-join and immutable generation ownership.
- Register one session per app process with an application coroutine scope.

- [ ] Establish RED.
- [ ] Implement minimum production changes.
- [ ] Run forced focused tests green and commit.

### Task 4: Port phone/TV controllers with reconnect-safe delivery

**Files:**
- Modify mobile `RoomSyncController`, `PlayerScreen`, and lifecycle tests
- Modify TV `TvRoomSyncController`, `TvPlayerScreen`, and lifecycle tests
- Add shared/two-client transport-broker tests

**Red tests:**
- Collectors are active before initial attach/ping.
- Every connection epoch re-attaches exactly once.
- Failed ready/buffering does not advance the local latch and retries.
- Failed user transport is surfaced/deferred rather than silently dropped.
- Player leave calls `RoomSession.leave`; screen disposal does not.
- Attach/readiness state is keyed by connection epoch plus playback session id,
  including equal initial snapshots and unchanged buffering after reconnect.
- A command accepted for playback session A is revalidated after its
  `execute_at` delay; publishing replacement session B prevents the stale
  command from seeking/pausing B or sending `ready(A)`.
- Two participants receive the same revision/command and independently apply
  host play/pause/seek, guest policy, drift correction, buffering, and ready.
- Host terminal close reaches both and stops reconnect.

**Implementation:**
- Port `323510a2`, `c6ba32ef`, and the controller portions of `79b4278d`.
- Keep RoomSyncEngine scheduling/session/revision/dedupe behavior from the
  current base.
- Use the connection epoch as the attach/ping/reconciliation trigger.

- [ ] Establish RED.
- [ ] Implement minimum production changes.
- [ ] Run phone/TV controller and two-client tests green and commit.

### Task 5: Port the complete reachable lobby, voting, and suggestion surface

**Files:**
- Modify phone entry/lobby/detail/navigation/ViewModels
- Modify TV entry/lobby/detail/navigation/ViewModels/QR layout
- Add/restore destination, policy, navigation, source-surface, voting, and
  focus tests

**Behavior:**
- Phone and TV can host or join.
- Vote rooms open empty, accept suggestions, votes, promotion, and host
  override.
- Invite code remains visible and TV controls remain reachable/focusable.
- Members can browse and suggest while the application-scoped room remains
  connected.
- Lobby-to-player and back do not create a second socket.

**Sources:** `9fe27a27`, `e171e011`, `acfac18a`, `fca6d7c6`, `bc58049b`,
`ea5756ec`, `8f36f7d2`, `530f9488`, `f91aa62e`, `923a1a7f`.

- [ ] Restore/author RED tests for missing behavior.
- [ ] Forward-port the net UI behavior against the current stack.
- [ ] Run focused phone/TV suites green and commit.

### Task 6: Validate two real emulator participants

**Environment:**
- Use two concurrent dedicated AVDs, preferring `Silo_Phone` and `Silo_TV`.
- Use distinct app data and user/profile identities.
- Start an appropriate local/configured Silo test backend without modifying
  production data.

**Flow:**
- [ ] Record AVD/API/app/backend versions and startup commands.
- [ ] Host, join, verify handshake and attach-session ordering.
- [ ] Exercise host and guest transport policy.
- [ ] Exercise suggestions, vote/unvote, promote, and host override.
- [ ] Exercise drift/seek/buffer/ready propagation.
- [ ] Interrupt/restore one emulator's network and verify reconnect/re-attach.
- [ ] Background/foreground both clients.
- [ ] Replace one room with another.
- [ ] Close/depart as host and verify terminal guest closure.
- [ ] Capture redacted ADB/backend logs and classify any limitation or defect.
- [ ] Commit the reproducible evidence document.

### Task 7: Audit, review, verify, and publish

- [ ] Audit net diff and remove Slice G/unrelated paths.
- [ ] Run all focused Watch Together, auth/origin, Room, and player suites.
- [ ] Run `./scripts/check-build-supply-chain.sh`.
- [ ] Run fresh `testDebugUnitTest` and phone/TV release assemblies.
- [ ] Obtain independent lifecycle/concurrency/security review.
- [ ] Fix every Critical/Important finding test-first and repeat full gates.
- [ ] Update source-to-local mapping and emulator evidence.
- [ ] Push `split/108-f-watch-together`.
- [ ] Create a draft PR based on `split/108-e-subtitles`; do not merge.

## Initial source mapping

| Area | PR 108 sources |
| --- | --- |
| Feature entry | `9fe27a27` |
| Send/open and room action fixes | `323510a2`, `c6ba32ef`, `e171e011` |
| Lobby/invite/TV focus | `acfac18a`, `fca6d7c6`, `bc58049b` |
| Voting and empty vote rooms | `ea5756ec`, `8f36f7d2`, `923a1a7f` |
| Browsing suggestions | `530f9488`, `f91aa62e` |
| App-scoped ownership intent | `864de27e`, `79b4278d` |
