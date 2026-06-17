# TV → Mobile Parity — Implementation & Tracking Plan

> **AUTONOMOUS MODE (2026-06-17):** User is away; complete ALL unchecked items
> non-stop, self-directed. Per item: implement → compile → Codex-review → fix →
> commit locally (author rxwatcher, **NO push**) → tick box + SHA here. Commit
> per item/small-cluster so compaction never loses work. Playback-decode-path
> items (P2.3, P2.5, P3 track pre-select) can't be device-verified here (Shield
> screencap is black) — implement carefully, Codex-review hard, commit, and tag
> "⚠ NEEDS DEVICE VERIFICATION". Bank safe UI/nav items first. Final report when
> all boxes ticked.

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
- [x] **P6.1 Aspect options (TV)** — added Stretch (RESIZE_MODE_FILL) to VideoFillMode so the fill toggle now offers Letterbox/Zoom/Stretch (mobile Fit/Fill/Stretch). Persistence still session-only (separate gap).
- [x] **P6.2 In-player Auto-skip-intro toggle (TV)** — On/Off in HUD Video pane (onSetAutoSkipIntro).
- [x] **P6.3 In-player Auto-play-next toggle (TV)** — On/Off in HUD Video pane (onSetAutoPlayNext).
- [ ] **P6.4 Next-Episode prompt overlay (TV)**.
- [x] **P6.5 Media Info sheet (TV detail)** — new TvMediaInfoDialog (resolution/codecs/HDR/container/size + audio/subtitle track lists) opened from the detail More menu (now available for movies too, not just episodes); rendered as a focusable Popup (dismissOnBackPress).
- [~] **P6.6 Subtitle search / AI-translate language list (TV)** — effectively at parity: TvSubtitleLanguageOptions already has 28 common languages; marginal diff vs mobile, not pursued.
- [ ] **P6.7 Version picker keeps every file (TV)** — stop collapsing files by quality key.
- [ ] **P6.8 Direct episode play (TV)** — Select on episode plays; separate affordance opens detail.
- [ ] **P6.9 Series-level Watch Together (TV)** — on next/playable episode.
- [ ] **P6.10 Home hero Play/Resume action (TV)**.
- [x] **P6.11 Browse sort order asc/desc (TV)** — added Order section (Descending/Ascending) to the browse filter sheet + onOrderChanged VM setter.
- [ ] **P6.12 Settings: theme preference (TV)** — System/Dark/Light.
- [x] **P6.13 Settings: default audio language (TV)** — Audio Language picker in Playback section, wired to the shared playerSettingsStore.audioLanguageFlow/setAudioLanguage (local setting, like mobile).
- [x] **P6.14 Settings: full subtitle language list (TV)** — added ko/zh/pt/it/ru to match mobile's 10.
- [x] **P6.15 Server rename (TV)** — Rename action (Edit icon) on each server row -> TvTextInputDialog -> ServerRegistry.rename.
- [x] **P6.16 Admin session "Send message" (TV)** — "Send message" action in the session menu -> TvTextInputDialog -> control(Message, SessionControlRequest(message)).

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
- 2026-06-17: DONE + committed (no push): P1 (17c7ca6), P2.1 (fd8ba79), P2.2 (08b1b09),
  P2.4 (a749aff), P6.14 (346aafc), P6.11 (1b3ba35), P6.13 (97c4758), P6.2/P6.3 (7524f52),
  + HUD Video-pane scroll fix. P1.3/P6.6 assessed [~]. P2.3/P2.5 deferred ⚠device.
  Player launches clean on Shield after all player changes.
- **RESUME HERE (autonomous):** P6.13 + P6.2/P6.3 are now DONE. Remaining unchecked, suggested order:
  - DONE since: P6.1 (e3c7e38), P6.5 (f2d18f6). 
  - DE-RISKED findings for fast resume:
    * P6.16 admin session message: CONTAINED — TvAdminSessionsScreen `control(sessionId, action, SessionControlRequest)`
      already accepts a message (SessionControlRequest.message; SessionControlAction.Message exists). Just add a
      "Send message" TvDialogOption to the actions menu (~line 254) + a text dialog → control(id, Message,
      SessionControlRequest(message=text)). Text dialog: reuse TvCreateCollectionDialog's OutlinedTextField+Popup pattern.
    * P6.15 server rename: ServerRegistry.rename(serverId, name) exists + TvServerListViewModel; add a rename action +
      the same text-dialog pattern (TvCreateCollectionDialog).
    * P6.7 version-picker keep-all: do WITH P3 (coupled — collapse exists because TV detail lacks audio/sub pre-selectors).
    * P6.8/P6.9/P6.10 (direct episode play / series WT / home-hero play): coupled to the playback-launch path
      (onPlay(contentId,fileId,itemType,resume) in TvItemDetailScreen) — need file resolution; do with care, device-test.
    * P6.12 theme: TV likely lacks System/Dark/Light theming infra — scope before building (may be large).
  - REMAINING (all are LARGER multi-file builds — start each with fresh context, Codex-review, commit per item):
    * Moderate: P6.10 home hero Play/Resume (hero card play button + launch), P6.8 direct episode play (episode-row
      Select plays; long-press/separate affordance opens detail), P6.9 series-level WatchTogether, P6.4 next-episode
      prompt overlay, P6.5 Media Info sheet (new TV dialog), P6.15 server rename + P6.16 admin session message
      (both need a TV text-entry dialog — check TvServerSetupScreen URL entry for a reusable field), P6.12 theme
      (TV may lack theming infra — scope first), P6.7 version-picker keep-all (do WITH P3 pre-selectors).
    * Large new screens+routes+DI: Phase 5 collections groups (P5.1–5.4); Phase 4 (P4.1 AdminLogs, P4.2 AdminScans,
      P4.3 admin user create/edit, P4.4 Request Detail, P4.5 my-requests open, P4.6 Pair Device + deep links,
      P4.7 Manage Sessions).
    * Playback-decode-path ⚠NEEDS DEVICE VERIFICATION: Phase 3 (P3.1/3.2 detail pre-selectors + P3.3 route args),
      P2.3 quality switching, P2.5 server audio switching.
    * Phase 7 LOW/polish + audiobook bookmarks.
  1. P6.13 default audio language (TV settings) — CONFIRMED contained: audio-language is a LOCAL shared setting
     (`playerSettingsStore.audioLanguageFlow` / `setAudioLanguage`, android-shared), NOT a profile/server field.
     Mirror mobile SettingsViewModel (audioLanguageLabel/audioLanguageWireValue) — add a picker to TV settings.
  2. P6.2/P6.3 in-player auto-skip-intro + auto-play-next toggle chips in HUD Video pane (settings flows exist in
     playerSettingsStore; expose flows+setters on TvPlayerViewModel, add HudClickChip toggles like Speed/Sleep).
  3. P6.15 server rename — TvServerListViewModel + ServerRegistry.rename(serverId, name) exists; needs a TV
     text-entry dialog (check for an existing TV text-input dialog to reuse; server SETUP screen has URL entry).
  4. P6.5 Media Info sheet (TV detail), P6.10 home hero Play/Resume, P6.8 direct episode play, P6.9 series WT,
     P6.7 version-picker keep-all, P6.4 next-episode prompt, P6.16 admin session message, P6.1 aspect Fit/Fill/Stretch,
     P6.12 theme.
  5. Phase 5 Collections groups (P5.1–5.4) — sizable: groups CRUD + card actions + create-type + detail rename/delete.
  6. Phase 4 (P4.x) — biggest: port AdminLogs/AdminScans screens, admin user create/edit, Request Detail screen+route,
     Pair Device route+deeplinks+settings row, Manage Sessions. New screens + routes + DI + nav.
  7. Phase 3 (P3.x) + P2.3 + P2.5 — playback-decode-path; implement carefully, Codex-review hard, commit, tag
     ⚠NEEDS DEVICE VERIFICATION (Shield screencap is black; can't validate real playback here).
  7b. Phase 7 LOW/polish + audiobook bookmarks.
  Cadence: trivial data/mirror edits → compile + commit (Codex light); substantive/risky → full Codex review.
