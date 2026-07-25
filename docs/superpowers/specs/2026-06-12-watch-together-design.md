# Watch Together (Synchronized Playback Rooms) — Design

**Date:** 2026-06-12
**Status:** Approved
**Scope:** Sub-project 5. Both mobile AND TV get the full feature: create/join/lobby/suggestions/voting/synced playback/invite, including **room creation on TV** (revised 2026-06-12 by user decision — TV is not join-only). Server: prairie-server `origin/main` (the Watch Together feature is on main). Realtime-only feature — the per-room websocket IS the feature; no REST fallback.

## Goal

Close the "Watch Together" parity gap: synchronized playback rooms where members watch the same title in lock-step, with a lobby, host-picked or voted selection, suggestions + voting, and an invite code — on mobile and TV (both can create AND join).

## Server protocol (verified against prairie-server main — exact contracts)

### Tokens & auth
- Create/join returns `{ "room": <Snapshot>, "room_access_token": "<JWT>" }`. The **room JWT** (24h) is a SECOND token distinct from the auth JWT.
- Every room-scoped REST call (get room, all suggestion ops, selection/policy/close) requires `?room_token=<roomJWT>`.
- The room WS authenticates by **query string only**: `?token=<authJWT>&room_token=<roomJWT>&profile_id=<id>&profile_token=<profileToken>`. Separate socket from `/events/ws`.

### REST (`/api/v1/watch-together`)
- `POST /rooms` body `{selection_mode?: "host_pick"|"vote"}` → 201 room+token (caller = host).
- `POST /join` body `{code?: String, join_token?: String}` (one required; token wins) → 200 room+token.
- `GET /rooms/{id}?room_token=` → room.
- `PUT /rooms/{id}/selection` body `{content_id, file_id?, library_id?}` (host-only) → room; sets phase=playing, state=waiting, position=0, bumps selection_revision.
- `PATCH /rooms/{id}/policy` body `{guest_control_policy: "host_only"|"guest_play_pause"}` (host-only) → room.
- `DELETE /rooms/{id}` (host-only) → 204 (closes room).
- Suggestions: `GET /rooms/{id}/suggestions` → `{suggestions:[…]}`; `POST /rooms/{id}/suggestions` `{content_id, content_type:"movie"|"episode", title, subtitle?, poster_url?, note?}` → list; `DELETE .../suggestions/{sid}` (host or suggester) → list; `POST .../suggestions/{sid}/vote` / `DELETE …/vote` → list (409 on dup/not-voted); `POST .../suggestions/promote` `{suggestion_id}` (host-only) → room.
- No leave endpoint (close the WS); no GET-by-code lookup (join resolves it).

### Snapshot (universal room payload; several fields are per-recipient)
`{room_id, phase(lobby|playing|ended), playback_state(idle|waiting|paused|playing), selection_mode(host_pick|vote), selection_revision(Long), selected_content_id?, selected_file_id?, selected_library_id?, code(8-char), guest_control_policy, is_paused, anchor_position_seconds(Double — server-computed expected position), anchor_updated_at(RFC3339), generation(Long), member_count, host_connected, self_role(host|guest), self_can_control_transport, self_can_manage_room, self_ignore_wait, attached_session_id?, invite_path?(host only)}`.
Suggestion: `{id, room_id, suggester_user_id, suggester_profile_id, content_id, content_type, title, subtitle, poster_url, note, vote_count, voted_by_me, created_at}` (in WS broadcast `voted_by_me` is forced false — re-merge locally).

### Room WS frames
Server→client (discriminator `type`): `snapshot {room}` (sent on connect + every state change — diff it for member/selection/host changes; no dedicated member/selection frames), `transport_command {command}` where command = `{command_id(uuid), session_id, selection_revision, action(play|pause|seek), position_seconds, execute_at(RFC3339Nano), issued_at, playback_state}`, `suggestions_update {suggestions}`, `room_closed {reason}`, `pong {client_sent_at, server_received_at, server_sent_at}`, `error {code, message}`.
Client→server: `attach_session {session_id}`, `transport_request {action, position_seconds?, is_paused}`, `state_report {session_id, position_seconds, is_paused}`, `ready {session_id, position_seconds, is_paused}`, `buffering {session_id, position_seconds, is_paused}`, `ping {client_sent_at(RFC3339Nano)}`.

### Sync semantics (the crux)
- Authority: host can play/pause/seek; guests can play/pause only if policy=`guest_play_pause`, never seek; no transport outside phase=playing.
- Server broadcasts discrete `transport_command`s with a future `execute_at` (server clock; `execute_at = now + lead`, lead = max(350ms, 500ms, 2×maxPingMs)). Client schedules execution at `execute_at − serverTimeOffset`.
- **Clock sync mandatory:** client sends `ping` on open + every 15s; on `pong` computes `serverTimeOffsetMs = ((server_received − client_sent) + (server_sent − client_received))/2`.
- Applying a command: dedupe by `command_id`; ignore if `session_id` ≠ attached session or `selection_revision` ≠ current; at execute time, corrective seek only if action==seek OR |localPos − command.position| > 0.35s; then apply play/pause; if `playback_state==waiting` and buffered, auto-send `ready`.
- Seek/new-selection/buffering push room to `waiting` (re-sync barrier): all members re-ready, then server resumes when all ready.
- Late-join: attach mid-play → server forces a re-sync barrier (multi-member) or sends a targeted seek (solo).
- Drift: client sends `state_report` every ~1.5s (suppress for ~250ms around a pending command's execute_at); server moves the anchor to the host's report on >1.5s drift, or sends a targeted corrective seek to a guest on >1.0s drift.

### Per-member playback
Each member opens their OWN normal playback session (the existing start/stream flow) and sends `attach_session` with that session id (server validates it matches the room's selected content). The room only syncs position/state.

### Lifecycle
Any user creates (becomes host); no member cap; host WS disconnect → 15s grace → room closes (`room_closed reason=host_left`); explicit close = DELETE or host WS leave; guests leaving just decrement; reconnect WS with backoff [500,1000,2000,5000]ms (not after room_closed), re-receive snapshot, re-attach_session. Closed room → 410 on join/get.

## Client design

### Shared (`shared/`)
- `model/watchtogether/WatchTogetherModels.kt`: RoomSnapshot, Suggestion, TransportCommand, create/join/selection/policy/suggestion requests + response wrappers, WS frame models + a sealed `RoomRealtimeEvent` (SnapshotEvent/TransportCommandEvent/SuggestionsEvent/Pong/Closed/Error). Serialization tests.
- `network/api/WatchTogetherApi.kt` (interface+Default): all REST endpoints, each room-scoped call taking the room token; registered in NetworkModule. MockEngine tests.
- `network/WatchTogetherRealtimeClient.kt`: opens the per-room WS (query-param auth from auth token + room token + active profile id/token), decodes frames, exposes `Flow<RoomRealtimeEvent>`, and provides send methods (attach/transport_request/state_report/ready/buffering/ping). Pure `decodeRoomFrame` unit-tested.
- `RoomSyncEngine` (pure, fully unit-tested): the timing brain. Inputs: server `ping/pong` samples → maintains `serverTimeOffsetMs`; a `transport_command` + current local position/playstate + now → a decision (`Seek(toMs)+SetPlaying` / `SetPlaying only` / `Ignore`) and the local execute delay; dedupe by command_id; selection_revision/session_id gating; the 0.35s corrective-seek threshold; whether to emit `ready`. No coroutines, no player — just data in/decision out.
- `repository/WatchTogetherRepository.kt` (singleton): owns room snapshot + suggestions StateFlows + the WS lifecycle (connect/reconnect/close), folds snapshot/suggestions events (re-merging `voted_by_me`), drives the ping loop + offset, exposes create/join/setSelection/policy/close/suggestion ops + the sync-relevant send methods, and a `reset()` on leave. Registered in RepositoryModule.

### Player binding (mobile + TV share the engine; each app wires its own player)
A `RoomSyncController` (per app, in the player package) active only when the player route carries a `roomId`:
- On enter: ensure the member's playback session exists (the normal start flow already creates it), connect the repo's WS for the room, send `attach_session(sessionId)`.
- Apply incoming `transport_command`s via `RoomSyncEngine` → the existing `PlayerViewModel.onSeek`/`onPlayPause` (mobile) / `TvPlayerViewModel` equivalents (TV); schedule at the engine-computed local time.
- Emit `state_report` every ~1.5s from the player's position (suppressed around pending command execute), and `ready`/`buffering` on buffer state transitions during `waiting`.
- Route user-initiated play/pause/seek through `transport_request` instead of applying locally, gated on `self_can_control_transport` (guests without control get a disabled/blocked affordance); seek disabled for guests.
- Player overlay: a room indicator (member count, host-connected, "Waiting for members…" during `waiting`), an invite affordance (show/share the code) on mobile host, and Leave (closes WS; host close ends the room with a confirm).
- `room_closed` → exit the synced player back to detail with a message.

### Mobile (`androidApp`)
- Item detail "Watch Together" action → bottom sheet: **Host** (create room with this title as selection → open synced player) or **Join by code** (enter 8-char code → resolve → if selection set, synced player; else waiting/lobby).
- Lobby/waiting screen (when phase=lobby or state=waiting pre-playback): member count, host vs guest, the **suggestions list with add/vote/unvote** and, for the host, **promote-to-selection** + a selection-mode/policy control; invite code share. Host picking a selection (or promoting) moves everyone to the synced player.
- Player room binding per above; route gains optional `roomId`.

### TV (`androidTvApp`)
- Item detail "Watch Together" → D-pad choice: **Host** (create room with this title as selection → TV synced player, displaying the join code prominently on-screen for others) or **Join by code** (D-pad code entry → resolve → TV lobby/waiting or synced player).
- TV lobby/waiting screen: member count + host/guest indicator + suggestions/voting (D-pad: focusable suggestion rows, vote/unvote, add via the existing TV add idiom). For the TV **host**: promote-to-selection + pick-selection + policy/selection-mode controls (D-pad), and the join code shown large. Guests see the voting/suggestion view without host-only controls.
- TV synced player binding reusing the shared engine + repo; TV player overlay room indicator + the join code (host) + leave (host close ends room with a confirm).

## Error handling
- REST/WS failures surface via `ApiResult.errorMessage`/in-screen messages; join errors (bad code, 410 closed) → clear message, no crash.
- WS reconnect with the spec backoff; on `room_closed` stop reconnecting and exit.
- Transport requests that the server rejects (guest without control) are prevented client-side by the `self_can_control_transport` gate; a late server rejection is ignored gracefully.
- Clock-sync not yet established → hold commands until the first offset sample (or apply immediately with delay 0 as a safe fallback, documented).

## Testing
- Shared: serialization (snapshot incl. per-recipient fields, suggestion, transport command, all frames + unknown-type tolerance), `decodeRoomFrame`, `RoomSyncEngine` exhaustively (offset computation from ping/pong samples; dedupe; selection_revision/session_id gating; 0.35s threshold; schedule-time math; ready emission; waiting barrier), repository fold (snapshot/suggestions merge incl. voted_by_me re-merge, reset). Realtime transport behind a fake.
- Player/UI: compile gates + manual checklists on real devices — the sync behavior MUST be verified with two devices in a room (host + guest): join, lock-step play/pause/seek, late-join re-sync, guest-without-control blocked, host leave closes room, suggestions/voting round-trip, reconnect.

## Out of scope
- Chat, host transfer/reassignment (server has neither).
- Any shared-stream model (each member streams independently — by server design).
