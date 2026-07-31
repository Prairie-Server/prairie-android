# Playback-Behavior Migration — Design Spec (Subsystem A)

**Date:** 2026-06-17
**Status:** Approved design, pending implementation plan
**Target:** `prairie-android` (`androidApp`, `android-shared`, `shared`)
**Source of reference:** `continuum_clients` (reference design only — not a lift-and-shift)

## Background

`prairie-android` is a mature KMP successor to `continuum_clients`. It already has a
service-owned player (`ContinuumPlaybackService`, a `MediaSessionService`), working
dual-engine MPV+Media3, sleep timer, buffering, lock-screen notifications, intro
auto-skip, encrypted tokens, offline downloads, and Watch Together room sync.

A review of the requested feature migration found that several originally-requested
features already exist in prairie-android (sleep timer, buffering, lock-screen
notifications). This spec covers only the **playback-behavior** gaps, the first of
three planned subsystem specs:

- **Subsystem A (this spec):** skip-back-on-resume, pass-out protection, remote
  session-control socket.
- **Subsystem B (later):** mini-player, Google Cast.
- **Subsystem C (later):** server-driven theming (consume `/theme/catalog` +
  `ui_theme` and map theme `vars` → Material3).

## Guiding principles

- Pure, reusable logic lives in `shared`/`android-shared` so `androidTvApp` can adopt
  it later and so it is unit-testable without Android dependencies.
- UI (prompts, overlays) stays per-app.
- Match prairie's existing patterns (`RoomSyncController`, `WatchTogetherRealtimeClient`,
  `PlayerViewModel`) rather than copying continuum verbatim.
- Reuse methods `PlayerViewModel` already exposes; do not duplicate playback control.

---

## Feature 1 — Skip-back-on-resume

**Goal:** when resuming a partially-watched item, begin a few seconds before the saved
position to re-establish context.

**Touchpoint:** `resolvePlaybackStartPosition` in
`shared/src/commonMain/kotlin/com/continuum/app/model/playback` — consumed by
`PlayerViewModel` (start position applied at the `startPosition`/`position` assignment,
~lines 316/408) and by the TV player VM.

**Change:**
- Add a `resumeRewindSeconds` parameter to `resolvePlaybackStartPosition`.
- Apply the rewind **only when resuming** — i.e. saved position exceeds the existing
  `MinimumResumeSeconds` (30s) resume threshold and the item is not `played`.
- Never apply on a fresh start. Clamp the result at `0.0`.

**Setting:** `resume_rewind_seconds` in `PlaybackSettingsKeys` / `PlayerSettingsStore`,
default `7`, `0` disables.

**Tests:** extend `PlaybackResumePositionTest`:
- fresh start → no rewind
- resume above threshold → rewind applied
- resume just above threshold → clamped at 0
- setting = 0 → disabled

---

## Feature 2 — Pass-out protection

**Goal:** after N consecutively auto-played episodes, stop auto-advancing and show a
"Still watching?" prompt.

**New shared class:** `AutoPlayGuard` (in `android-shared` or `shared`, no Android deps):
- holds a consecutive auto-advance counter
- `shouldGate(): Boolean` returns true at threshold (default 3)
- `recordAutoAdvance()` increments
- resets to 0 on any **user-initiated** action: manual play, manual seek, manual next
  episode, or starting a new session that is not an auto-advance

**Touchpoint:** the auto-advance path in `PlayerViewModel` (`onNextEpisode()` ~line 922
and the credits/`showNextEpisode` auto-trigger gated by `autoPlayNextEnabled` ~line 223).
- Only the **automatic** advance consults `AutoPlayGuard`.
- Manual "next episode" is **never** gated and resets the counter.
- When gated, emit a `stillWatching` UI state instead of advancing.

**UI:** "Still watching? — Continue / Stop" prompt in `PlayerOverlay` (phone).
"Continue" resets the guard and advances; "Stop" ends playback / leaves on the prompt.

**Setting:** `passout_threshold_episodes` in `PlayerSettingsStore`, default `3`,
configurable, `0`/off supported.

**Tests:** unit-test `AutoPlayGuard` — increments, gates exactly at threshold, and each
reset rule. No Android dependencies.

---

## Feature 3 — Remote session-control socket

**Goal:** allow the server or another device to control *this* playback session in real
time (the admin pause/seek/stop/message surface that already exists server-side), and
consume server-pushed playback enrichment events.

**Server contract** (already implemented; see `internal/playback/realtime.go`,
`internal/api/handlers/session_ws.go`, `command_dispatcher.go`,
`admin_playback_control.go` in `prairie-server`):
- Endpoint: `GET …/sessions/{session_id}/control/ws`, authenticated via the normal
  profile session (no separate ticket). Session must belong to the requesting user.
- Message types: `hello`, `command`, `event`, `ack`, `result`.
- Client → server: `hello` (marks connection control-ready; `session_id` must match),
  `ack` (status `accepted`), `result` (status `completed` | `rejected`).
- Server → client `command` names: `pause`, `unpause`, `play_pause`, `seek`,
  `set_volume`, `stop`, `terminate`, `display_message`, `play_media`, `set_audio_track`,
  `set_subtitle_track`.
- Server → client `event` names: `chapter_thumbnail_ready`, `markers_updated`,
  `subtitle_ready`, `subtitle_translation_*`.
- Server runs a ping loop → the client must respond with pong (Ktor handles standard
  pong; confirm during implementation).
- On a `result: completed` for `stop`/`terminate`, the server stops the session.

**New shared client:** `PlaybackRealtimeClient`
(`shared/src/commonMain/kotlin/com/continuum/app/network`), modeled on
`WatchTogetherRealtimeClient`:
- connect + send `hello`
- expose inbound messages as a `Flow<PlaybackRealtimeMessage>`
- send `ack` and `result`
- reconnect with backoff (reuse the WatchTogether reconnect/backoff approach)
- kotlinx-serialization envelope models defined to match the Go structs in
  `realtime.go` (not continuum's envelopes)

**New binder:** `PlaybackRealtimeController`
(`androidApp/.../ui/screens/player`), modeled on `RoomSyncController`:
- active only while `PlayerViewModel.state.sessionId != null`
- maps `command` → existing VM methods: `pause`/`play`, `seekImmediate`, `changeAudio`,
  subtitle selection, `stop`
- maps `event` → existing handlers: `subtitle_ready` → `refreshSubtitles`,
  `markers_updated` → marker refresh, `chapter_thumbnail_ready` → thumbnail update
- `display_message` → transient `PlayerNoticeOverlay` message
- sends `ack` on receipt, `result` (`completed`/`rejected`) after applying

**Out of scope (this spec):** `play_media` (remotely starting a brand-new item) — gated
for a later iteration; respond `rejected` for now.

**DI:** register `PlaybackRealtimeClient` in the shared network module; register and bind
`PlaybackRealtimeController` in `androidModule`, attached in `PlayerScreen` the same way
`RoomSyncController` is.

**Tests:**
- envelope (de)serialization round-trips against captured server JSON
- fake-socket test: a `seek` command yields `ack` + `result` and invokes `seekImmediate`
- `stop` command yields `result: completed`

---

## Cross-cutting concerns

- **Error handling / degradation:** socket failures degrade silently — playback
  continues, matching the server's "disconnect does not stop the session" contract. The
  resume-rewind and pass-out changes are pure logic and cannot break playback.
- **Reuse:** every command/event handler calls a method `PlayerViewModel` already
  exposes; no parallel playback-control code.
- **TV:** `AutoPlayGuard` and the resume-rewind change land in shared/android-shared so
  `androidTvApp` can adopt them in a later spec. Only the phone UI prompts are in this
  spec.

## Settings summary (new keys in `PlaybackSettingsKeys` / `PlayerSettingsStore`)

| Key | Default | Meaning |
|---|---|---|
| `resume_rewind_seconds` | 7 | seconds to rewind on resume; 0 disables |
| `passout_threshold_episodes` | 3 | consecutive auto-plays before gating; 0 disables |

## Acceptance criteria

1. Resuming an item above the 30s threshold starts ~7s earlier; fresh starts and items
   under the threshold are unaffected; setting 0 disables.
2. After 3 consecutive auto-advanced episodes, the next auto-advance shows "Still
   watching?"; manual next is never gated and resets the count.
3. A playback session opens a control socket when `sessionId` is present; server
   `pause`/`seek`/`stop`/`display_message` commands take effect on the active player with
   correct `ack`/`result`; `subtitle_ready`/`markers_updated`/`chapter_thumbnail_ready`
   events refresh the corresponding player data; socket loss does not interrupt playback.
4. New unit tests pass for `resolvePlaybackStartPosition`, `AutoPlayGuard`, and the
   realtime envelope/dispatch.
