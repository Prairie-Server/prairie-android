# TV → Mobile Parity — Implementation & Tracking Plan

**Goal:** bring Android **TV** to full feature parity with Android **mobile** (the gold standard) in every way except **ebooks** and **downloads**.

**Source of truth:** `docs/superpowers/specs/2026-06-17-tv-mobile-parity-audit.md` (Codex + Claude reconciled audit, file-referenced). Codex full transcript: `/tmp/codex_parity_result.md`.

**Conventions for every item below:**
- Mobile is the spec — match its behavior/options. Shared ViewModels/repos in `shared/` + `android-shared/` usually already expose the capability; the gap is the TV UI wiring.
- Each item: implement → `:androidTvApp:compileDebugKotlin` (or `:androidApp` for mobile) → Codex-review → device-verify on Shield where observable (screencap is black for the player/secure surfaces, so verify via logcat + behavior) → commit locally (author rxwatcher, **no push**) → check the box here.
- Group commits sensibly (one per coherent item or small cluster).

Legend: `[ ]` todo · `[x]` done (commit sha) · `[~]` partial · `[N/A]` won't-fix.

---

## Phase 1 — Player Sync controls (user-requested; both clients) — DONE (commit pending)
- [x] **P1.1 Subtitle delay (TV)** — TvSubtitleMenu: title "Subtitle delay" + signed current offset in the stepper center ("+50 ms"/"−50 ms"/"0 ms"); removed the "Advance/Delay subtitles by X" wording + `subtitleSyncLabel`; step **50 ms**.
- [x] **P1.2 Subtitle delay step (mobile)** — PlayerSettingsSheet subtitle `DelaySpinnerRow` stepMs 100 → **50** (+ doc comment).
- [~] **P1.3 Audio delay (TV)** — reviewed, KEPT as-is: already shows current value ("Audio delay: X ms") and already steps 50 ms (plus finer −10/+10/Reset). Richer than mobile's single ±50 spinner; not a gap, so no downgrade. (Mobile audio already 50.)
- [x] **P1.4** Verified: delays still apply unchanged — subtitle via SubtitleOffsetHolder reparse, audio via DelayAudioProcessor; only the UI display/step changed.
- Tests: updated TvPlayerControlsUsabilityTest (step 50, "Subtitle delay", subtitleSyncLabel removed) — green.

## Phase 2 — Player HIGH
- [x] **P2.1 Playback speed (TV)** — HUD Video pane Speed presets 0.5–3× (click-committed HudClickChip), wired to onSetPlaybackSpeed. Commit fd8ba79.
- [x] **P2.2 Sleep timer (TV)** — HUD Video pane: preset chips (15m/30m/45m/1h/1h30m) when idle; "Sleeping in Xm Ys" + Cancel when armed. Wired to onStartSleepTimer/onCancelSleepTimer.
- [ ] **P2.3 Quality / version switching (TV)** — DEFERRED (device-test required): not UI-only; switching quality re-mounts the player on a new stream. Needs real-media on-device verification. Implement in a device-test session.
  - [~] **P2.5 Server-side audio switching (TV)** — DEFERRED (device-test required): transcoded audio is baked in; needs server changeAudio + re-mount, validated against transcoded media on-device.
- [x] **P2.4 In-player subtitle style editor (TV)** — new TvSubtitleStyleDialog (size/font/text color/bg style+color/opacity/outline+color/position), opened from the subtitle menu; modal (Back closes, suppresses player keys + autohide), captures focus. Wired to onSetSubtitleAppearance.

## Phase 3 — Detail HIGH
- [ ] **P3.1 Audio pre-selector (TV detail)** — add pre-playback Audio picker to the detail action row/overflow. Mobile: MediaSelectors:148.
- [ ] **P3.2 Subtitle pre-selector (TV detail)** — add pre-playback Subtitle picker (incl. Off). Mobile: MediaSelectors:186.
- [ ] **P3.3 Player route track args (TV)** — add `audioTrackIndex`/`subtitleTrackIndex` to TvRoute.Player + pass selected indexes into playback (supports P3.1/P3.2). Mobile: Routes:127.

## Phase 4 — Admin / Requests / Auth HIGH
- [ ] **P4.1 Admin Logs (TV)** — port AdminLogsScreen (App/Audit tabs, filters, search, component filter, pagination, expandable rows) + route + hub entry.
- [ ] **P4.2 Admin Scans (TV)** — port AdminScansScreen (scan-all, per-library scan, cancel) + route + hub entry.
- [ ] **P4.3 Admin create/edit user (TV)** — replace deferred delete-only with create FAB + edit form (role/enabled/library access/max streams/transcodes/profiles).
- [ ] **P4.4 Request Detail (TV)** — add RequestDetail route + screen (hero metadata, overview, recommendations, request/library/status actions). Mobile: RequestDetailScreen.
- [ ] **P4.5 My Requests open non-library rows (TV)** — rows should open Request Detail even without a library item.
- [ ] **P4.6 Pair Device (TV)** — route + `silo://device`/`continuum://device` deep links + DevicePairingScreen + Settings row. Mobile: DevicePairingScreen.
- [ ] **P4.7 Manage Sessions (TV)** — Settings Account: open session management + revoke. Mobile: AccountSection:137.

## Phase 5 — Collections
- [ ] **P5.1 Collection groups (TV)** — Add Group + group action menu (rename/delete); group the collections grid. Mobile: CollectionsScreen.
- [ ] **P5.2 Collection card actions (TV)** — move-to-group, delete-from-grid.
- [ ] **P5.3 Manual vs Smart type (TV)** — create dialog offers collection type. Mobile: CreateCollectionSheet:103.
- [ ] **P5.4 Collection detail rename/delete (TV)** — top-bar actions when manageable.

## Phase 6 — MEDIUM batch
- [ ] **P6.1 Aspect options (TV)** — Fit/Fill/Stretch (+ persist). Mobile: AspectRow.
- [ ] **P6.2 In-player Auto-skip-intro toggle (TV)**.
- [ ] **P6.3 In-player Auto-play-next toggle (TV)**.
- [ ] **P6.4 Next-Episode prompt overlay (TV)**.
- [ ] **P6.5 Media Info sheet (TV detail)** — video/audio/subtitle track details.
- [ ] **P6.6 Subtitle search / AI-translate language list (TV)** — use full LanguageNames list.
- [ ] **P6.7 Version picker keeps every file (TV)** — stop collapsing files by quality key.
- [ ] **P6.8 Direct episode play (TV)** — Select on episode plays; separate affordance opens detail.
- [ ] **P6.9 Series-level Watch Together (TV)** — on next/playable episode.
- [ ] **P6.10 Home hero Play/Resume action (TV)**.
- [ ] **P6.11 Browse sort order asc/desc (TV)**.
- [ ] **P6.12 Settings: theme preference (TV)** — System/Dark/Light.
- [ ] **P6.13 Settings: default audio language (TV)**.
- [ ] **P6.14 Settings: full subtitle language list (TV)** — match mobile's 10.
- [ ] **P6.15 Server rename (TV)**.
- [ ] **P6.16 Admin session "Send message" (TV)**.

## Phase 7 — LOW / polish
- [ ] Combined audio+subtitle Tracks surface · subtitle provider warnings · WT room-indicator persistence · audiobook active-sleep-timer label + About/description · full genre tags in detail facts · Home/Library "See All" · Browse Release-Date naming · Browse Reset/Apply · Search media-type deep-link + library-derived filters · person bio scroll · WT copy/QR invite · Account email/role display · "Manage Servers" settings row · Library default sort.
- [ ] **Audiobook bookmarks (TV)** — (HIGH-tagged in audit but audiobook-domain; sequence with Phase 7 unless prioritized).

---

## Won't-fix / N/A (TV-inappropriate)
- [N/A] Player **orientation lock** — meaningless on TV (always landscape).
- [N/A] **Combined Favorites&Watchlist** nav entry — TV deliberately uses separate routes.

## Server-blocked (needs silo-server PR, not TV work)
- Multi-select browse filters (genre/content-rating) — catalog API takes single values; mobile only sends `firstOrNull()`. TV single-select is functionally equivalent today.

---

## Progress log
- 2026-06-17: audit complete (Codex + Claude). Plan created. Starting Phase 1.
