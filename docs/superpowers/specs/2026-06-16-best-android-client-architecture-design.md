# Silo Android — Foundational Architecture (design)

**Date:** 2026-06-16
**Status:** Design draft — converged via the Claude↔Codex debate in
`docs/superpowers/notes/architecture-debate.md` (Rounds 1–4). Supersedes the
narrow foliate spike spec for sequencing purposes (foliate reading becomes a
later sub-phase, see Roadmap).
**Goal:** Make Silo the best self-hosted media client on Android — beating Plex,
Jellyfin (findroid/streamyfin), and Infuse on Android specifically. Cost is not a
constraint; deep re-architecture is acceptable. Single Android target (no iOS/desktop).

## Why this is not a greenfield rewrite

A device-level review of the live tree (corroborated by Codex) found Silo already has
substantial best-in-class scaffolding, which dictates a **strangler re-foundation, not
a big-bang rewrite**:
- libmpv dependency (`android-shared/build.gradle.kts:46`) + a 53k-line MPV
  `BasePlayer` wrapper (`android-shared/.../player/mpv/MpvPlayer.kt`, `vo=gpu-next`,
  `ao=aaudio`, `hwdec=mediacodec`).
- A playback backend-swap seam (`.../player/backend/VideoPlaybackBackendFactory.kt`,
  `Media3VideoPlaybackBackend.kt`).
- `HdrDisplayController` doing `preferredDisplayModeId` selection + restore from
  `display.supportedModes` (HDR-type selection still stubbed).
- Capability detection, audio-track, subtitle, and track-selection managers.

The gap is therefore **completion + hardening + proof** of playback, and a **real
offline-first data layer** — not invention.

## Converged decisions (D1–D5)

- **D1 — KMP teardown is later hygiene, not a gate.** No iOS/desktop ever, so
  `:shared`/`:android-shared` multiplatform earns nothing — but MPV already runs in
  `android-shared` androidMain, so collapsing KMP is *not* a prerequisite for any
  playback/data work. Do it opportunistically.
- **D2 — Dual engine behind the existing backend factory.** ExoPlayer/Media3 default
  (adaptive HLS/DASH, Cast, DRM); libmpv for direct-play of local/LAN where fidelity
  wins (libass-grade ASS/SSA, exotic containers, broad passthrough). Selection is an
  **Auto policy = content + capability + device-class + route/session intent**
  (Cast / DRM / adaptive-transcode / external display / HDMI-audio sink can *force*
  the engine), manual override available; MPV disabled below a device floor the
  matrix establishes. (Battery/thermal is a later policy input, not a Phase-1
  selector.) The UI, MediaSession, and Cast bridge program against
  `androidx.media3.common.Player` only.
- **D3 — Display-aware playback (fps + HDR + passthrough), device-proven, phone *and*
  TV.** fps/refresh matching is table-stakes and partly built; the differentiator is
  the *combination*: real HDR-mode switching (`Display.Mode.getSupportedHdrTypes` on
  API-34+, explicit policy below), audio passthrough/bitstream detection, and bringing
  it to the phone — all verified on real devices. `preferredDisplayModeId` is the
  down-floor path (API-23, below our floor); `Surface.setFrameRate` (API-30) is additive.
- **D4 — Offline-first as the data architecture.** Room as the local projection +
  sync outbox is the source of truth; UI always reads local; a single sync engine
  round-trips position, watched state, **track selections**, reading CFI, ratings,
  favorites; downloads are first-class rows. Replaces today's network-first screens
  (downloads are file/sidecar now; app only fast-paths Downloads when offline).
- **D5 — Strangler, two-track Phase 1.** Player-surface migration and KMP teardown come
  *after* the foundation, not first.

## Phase 1 — two parallel tracks (touch disjoint code)

Tracks A and B run **in parallel** (disjoint code). The only sequencing constraint:
shipping **production Auto defaults** waits on Track A's matrix results; Track B does
not depend on Track A.

### Cross-cutting Phase-1 must-haves (apply to both tracks)
- **Fallback contract.** A failed Auto/MPV startup must automatically retry on
  ExoPlayer and record the reason — never a dead player surface.
- **Observability.** Structured logs for: each playback-engine decision (axes +
  outcome), display-mode changes and restores, HDR/passthrough negotiation results,
  and sync conflicts. This is how the device matrix and offline round-trip are *proven*.
- **Migration boundary.** Room-backed repositories must preserve the current offline
  downloads and watched/progress state during strangling — no regression, no data loss.

### Track A — Playback truth (discharges the project's biggest risk)
A device-matrix hardening spike on the *existing* `MpvPlayer` + `HdrDisplayController`.
Matrix: Pixel phone, NVIDIA SHIELD, and (if obtainable) an old ARMv7 API-24 TV box.
Prove and measure:
- MPV direct-play + **ASS/SSA subtitle fidelity** vs ExoPlayer.
- Refresh-rate switch **and restore** across HDMI transitions (TV) and on phone.
- HDR behavior (and what real HDR-mode switching requires per API level).
- Audio passthrough/bitstream to an AVR vs PCM downmix.
- Cast/MediaSession interplay with each engine.
**Exit criteria:** named devices, named media fixtures (containers/codecs/HDR/audio
formats/ASS subs), and pass/fail thresholds per check, yielding the published
**MPV-enable floor** and the **Auto-policy thresholds** — the inputs every later
playback task depends on. Wire `MpvPlayer` as a second `VideoPlaybackBackend` behind
the existing factory.

### Track B — Offline-first foundation
Stand up the Room local-projection + sync-outbox schema and begin strangling
network-first screens behind Room-backed repositories.
Schema (per the debate): servers/accounts; libraries/items; media files/sources/
streams; subtitles; downloads; per-profile user item state; track selections; reading
CFI; ratings/favorites; and a **dirty-operations outbox** for sync. UI reads Room;
a sync engine reconciles with the server and flushes the outbox.

## What we cut (focus the polish budget)
Calendar, admin, people, requests stay deliberately thin until core
browse → play → offline is reliable. No polish budget there in Phase 1.

## Roadmap (later phases, each its own spec)
- **Phase 2:** complete the Auto engine policy + display-aware playback (HDR + passthrough)
  to ship quality, from Track A's findings; trickplay scrubbing thumbnails; skip
  intro/credits + chapter markers (findroid models these: `FindroidSegment`, `FindroidChapter`,
  `FindroidTrickplayInfo`).
- **Phase 3:** finish offline-first migration across all surfaces; smart downloads
  (auto-next-episode, quality ladders).
- **Phase 4:** Reading flagship — foliate-js cross-client CFI (the earlier spike), comic
  webtoon/RTL, PDF.
- **Phase 5:** Audiobooks flagship — chapter nav, sleep timer (incl. auto by time-of-day,
  per Voice), pitch-corrected speed, volume gain, Android Auto.
- **Phase 6:** TV polish — Google TV WatchNext "continue watching" channel, focus
  correctness audit, ambient/screensaver. KMP teardown folded in opportunistically.

## Success criteria for Phase 1
- Track A: a committed **findings note** stating the MPV-enable device floor, the Auto
  thresholds, HDR/passthrough behavior per device, and a go/no-go for MPV-as-default;
  `MpvPlayer` selectable via the backend factory and proven on the matrix.
- Track B: Room is the read source for at least the home/library browse + resume path;
  position/watched/track-selection writes go local-first and sync via the outbox,
  verified to round-trip across an offline→online cycle.

## Resolved empirically by Phase 1 (not by debate)
- The MPV-enable device floor and the Auto-policy thresholds — outputs of Track A's
  device matrix, feeding production Auto defaults.
