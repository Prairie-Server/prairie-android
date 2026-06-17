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
- [x] **P3.1 Audio pre-selector (TV detail)** — detail More-menu "Audio" picker (TvOptionDialog) over the selected version's audioTracks; selection → server session start via VideoPlaybackStartRequest.audioTrackIndex + TvVideoPlaybackStarter.startSession. Robust/server-side. Commit a329670.
- [x] **P3.2 Subtitle pre-selector (TV detail)** — detail More-menu "Subtitles" picker incl. Off; -1/Off + auto robust; positive index applied via subtitleSelectRequests on first track list (consume-once). ⚠ positive-index→player-ordinal mapping best-effort, needs on-device verification. Commit a329670.
- [x] **P3.3 Player route track args (TV)** — TvRoute.Player + tvPlayDestinationFor gained audioTrackIndex/subtitleTrackIndex; parsed in TvAppNavigation → TvPlayerScreen → launch args → VM. Route tests added. Commit a329670.

## Phase 4 — Admin / Requests / Auth HIGH
- [x] **P4.1 Admin Logs (TV)** — TvAdminLogsViewModel + TvAdminLogsScreen: App/Audit tabs over shared AdminRepository.getAppLogs/getAuditLogs, App-level filter chip, cursor pagination (near-end load-more), monospace rows; route/DI/hub "Logs" row. Generation-gated; loading/error/load-more derived from active-tab count/cursor (Codex-reviewed: fixed both-empty gating + missed-load key). Commit 7c2ea90. (Free-text search + component filter + expandable rows = follow-up.)
- [x] **P4.2 Admin Scans (TV)** — TvAdminScansViewModel + TvAdminScansScreen (Scan-all + per-library scan/cancel via AdminRepository) + route/DI/hub entry. Per-library busy guard prevents duplicate in-flight requests.
- [x] **P4.3 Admin create/edit user (TV)** — quick role/enable actions (setRole/setEnabled in shared AdminUsersViewModel) PLUS the full create/edit FORM: TvAdminUserEditScreen over shared AdminUserEditViewModel (username/email/password, role chips, enabled + download toggles, library-access ids, stream/transcode/profile quotas). Reachable from "Add user" row + "Edit user" dialog option. Nested AdminUserEdit(userId?) route (non-restoring nav); edit-mode derived from route userId; Save gated until edit-load; focus targets first editable field. Codex-reviewed. Commit a85e5a5.
- [x] **P4.4 Request Detail (TV)** — parameterized route RequestDetail(mediaType,tmdbId) + DI(params) + TvRequestDetailScreen (reuses shared RequestDetailViewModel; title/meta/genres/overview + Request button[disabled while submitting]/status); reachable from TvRequestsScreen non-actionable taps. (Recommendations rail + poster art = follow-up.)
- [x] **P4.5 My Requests open non-library rows (TV)** — both TvRequestsScreen + TvMyRequestsScreen non-library taps open Request Detail (rows always actionable, phone parity).
- [x] **P4.6 Pair Device (TV)** — TvPairDeviceScreen over shared DevicePairingViewModel (approve/deny by token deep link or manual code via TvTextInputDialog); top-level TvRoute.PairDevice(token,code) + DI factory + TvAppNavigation composable + pendingDeepLink "device" branch (prefers token, launchSingleTop); MainTvActivity + manifest now accept `silo` scheme; Settings "Pair a device" row. Codex-reviewed (button enabled-gating + launchSingleTop). Commit d16bde3. (HTTPS `/device` App Links = best-effort follow-up, same caveat as phone parser.)
- [x] **P4.7 Manage Sessions (TV)** — TvManageSessionsViewModel/Screen (own sessions via AuthRepository getSessions/deleteSession) + route/DI; "Manage sessions" row in Settings Account.

## Phase 5 — Collections  ✅ commit f88b63b (Codex-reviewed)
- [x] **P5.1 Collection groups (TV)** — TvCollectionsScreen renders shared VM `sections` (named groups + Ungrouped); header "Add Group"; named-group header Edit affordance → TvOptionDialog(Rename/Delete) → openGroupAction; create/rename via TvTextInputDialog, delete via confirm dialog.
- [x] **P5.2 Collection card actions (TV)** — card onLongClick → TvOptionDialog(Move to group / Delete); Move → TvOptionDialog list (Ungrouped + groups, current pre-selected) → moveCollection; Delete → confirm → deleteCollection.
- [x] **P5.3 Manual vs Smart type (TV)** — TvCreateCollectionDialog gains Manual/Smart TvFilterChips bound to shared createType/onCreateTypeChanged.
- [x] **P5.4 Collection detail rename/delete (TV)** — TvCollectionDetailViewModel gained name/rename/delete state+methods; detail header Rename/Delete buttons (TvTextInputDialog + confirm dialog); pops back on delete; grid refreshes on resume to reflect edits.
- Codex hardening: busy-gated all confirm actions, surfaced group/detail-delete errors in dialog titles, first-resume guard, section-order initial focus, always-available header actions.

## Phase 6 — MEDIUM batch
- [x] **P6.1 Aspect options (TV)** — added Stretch (RESIZE_MODE_FILL) to VideoFillMode so the fill toggle now offers Letterbox/Zoom/Stretch (mobile Fit/Fill/Stretch). Persistence still session-only (separate gap).
- [x] **P6.2 In-player Auto-skip-intro toggle (TV)** — On/Off in HUD Video pane (onSetAutoSkipIntro).
- [x] **P6.3 In-player Auto-play-next toggle (TV)** — On/Off in HUD Video pane (onSetAutoPlayNext).
- [~] **P6.4 Next-Episode prompt overlay (TV)** — DEFERRED (device-test required): credits-position-triggered overlay + auto-play-next consumption + next-episode resolution; needs the TV Ready state to carry seriesId/season/episode (data-flow change) and on-real-media verification of the credits trigger, end-of-stream, and player re-mount. Build in a device-test session alongside P2.3/P2.5.
- [x] **P6.5 Media Info sheet (TV detail)** — new TvMediaInfoDialog (resolution/codecs/HDR/container/size + audio/subtitle track lists) opened from the detail More menu (now available for movies too, not just episodes); rendered as a focusable Popup (dismissOnBackPress).
- [~] **P6.6 Subtitle search / AI-translate language list (TV)** — effectively at parity: TvSubtitleLanguageOptions already has 28 common languages; marginal diff vs mobile, not pursued.
- [x] **P6.7 Version picker keeps every file (TV)** — TvVersionPicker lists one option per file (codec·audio·size detail), no quality-key collapsing; exact-fileId selection. Commit e37f8ed.
- [x] **P6.8 Direct episode play (TV)** — episode rail OK plays/resumes via onPlay (resume policy = finite/>30s/!played/not-near-end), long-press opens detail. Commit e37f8ed.
- [~] **P6.9 Series-level Watch Together (TV)** — DEFERRED (device-test required): host WT on the resolved next/playable episode from series detail; depends on next-episode resolution (P6.4) + WT room-create/sync verified against live playback. Build with P6.4 in a device-test session.
- [x] **P6.10 Home hero Play/Resume action (TV)** — hero OK plays/resumes (resume policy = !played/finite/>30s/not-near-end), long-press opens detail; series heroes open detail (no single playable file); audiobooks route to audiobook player. Library hero keeps OK=detail. Commit 7966717.
- [x] **P6.11 Browse sort order asc/desc (TV)** — added Order section (Descending/Ascending) to the browse filter sheet + onOrderChanged VM setter.
- [N/A] **P6.12 Settings: theme preference (TV)** — TV has no theming infra and is dark-by-design (10-foot UI); a System/Dark/Light selector is inappropriate for TV (like orientation-lock). Mobile ThemePreference is phone-only. Not pursued.
- [x] **P6.13 Settings: default audio language (TV)** — Audio Language picker in Playback section, wired to the shared playerSettingsStore.audioLanguageFlow/setAudioLanguage (local setting, like mobile).
- [x] **P6.14 Settings: full subtitle language list (TV)** — added ko/zh/pt/it/ru to match mobile's 10.
- [x] **P6.15 Server rename (TV)** — Rename action (Edit icon) on each server row -> TvTextInputDialog -> ServerRegistry.rename.
- [x] **P6.16 Admin session "Send message" (TV)** — "Send message" action in the session menu -> TvTextInputDialog -> control(Message, SessionControlRequest(message)).

## Phase 7 — LOW / polish
- **Done:** ~~Account email/role display~~ (94c387f) · ~~"Manage Servers" settings row~~ (94c387f) · ~~person bio scroll~~ (raised clip to 12 lines, 94c387f) · ~~Library default sort~~ (added_at, 94c387f) · ~~Browse Reset~~ (75cd6b1; Apply is reactive on TV so no Apply button).
- **Done (this pass):** ~~active sleep-timer label~~ (97cee1f) · ~~full genre tags in detail facts~~ (e6c091d) · ~~Home/Library "See All"~~ (2f01097, routes to Browse like phone) · ~~Browse Release-Date naming~~ (2f01097, → `year`) · ~~Search library-derived filters~~ (2f01097) · ~~WT QR invite~~ (2f01097) · ~~audiobook About/description~~ (44db294) · ~~subtitle provider warnings~~ (44db294).
- **N/A (not a real parity gap):** Combined audio+subtitle Tracks surface — TV already has complete audio selection (HUD Audio pane) + subtitle selection (TvSubtitleMenu), just in separate surfaces vs the phone's one sheet; functionally at parity, not worth a player reorg. · WT room-indicator persistence — the phone has no persistent shell badge either (only in-player transport gating, which the TV room binding already does).
- [x] **Audiobook bookmarks (TV)** — TvAudiobookBookmarksPanel over shared AudiobookPlayerViewModel: "Bookmark here" + list (OK jumps, long-press deletes); "Bookmarks" chip + AudiobookPanel.Bookmarks. Codex-reviewed (focus-return-after-delete). Commit 54a30e9.

---

## Won't-fix / N/A (TV-inappropriate)
- [N/A] Player **orientation lock** — meaningless on TV (always landscape).
- [N/A] **Combined Favorites&Watchlist** nav entry — TV deliberately uses separate routes.

## Server-blocked (needs silo-server PR, not TV work)
- Multi-select browse filters (genre/content-rating) — catalog API takes single values; mobile only sends `firstOrNull()`. TV single-select is functionally equivalent today.

---

## Progress log
- 2026-06-17 (SESSION TALLY, ~36 commits ahead, NOT pushed): DONE = P1, P2.1/2.2/2.4, P6.1/2/3/5/11/13/14/15/16,
  P7 account email+role, + HUD scroll fix + reusable TvTextInputDialog + TvMediaInfoDialog + TvSubtitleStyleDialog.
  N/A = P6.12 theme (TV dark-by-design), orientation-lock, combined Favorites&Watchlist. DEFERRED ⚠device = P2.3, P2.5.
  **REMAINING for fresh-context sessions** (one big screen per session, Codex-review hard):
  Phase 4 — P4.1 AdminLogs (App/Audit tabs+filters+pagination, biggest), P4.3 admin user create/edit FORM
  (check AdminUsersViewModel for create/update methods first; fields role/enabled/library-access/max streams/transcodes/profiles),
  P4.4 Request Detail (+P4.5) — DE-RISKED: shared RequestDetailViewModel(RequestsRepository, mediaType:String, tmdbId:Int)
    is reusable (load/submitRequest); render state.detail: RequestMediaDetail (hero/overview/recommendations/request+status
    actions — read RequestMediaDetail fields + mobile RequestDetailScreen content). Add parameterized TvRoute.RequestDetail
    (mediaType+tmdbId, mirror TvRoute.Player arg pattern), DI viewModel{params-> RequestDetailViewModel(get(), params.get(), params.get())},
    nav composable in TvMainShell, and wire navigation from TvRequestsScreen result cards + TvMyRequestsScreen rows (P4.5).
  P4.6 Pair Device (route+silo://device deeplinks+DevicePairingScreen+settings row).
  [DONE P4.7 Manage Sessions — shared AuthRepository getSessions/deleteSession.]
  Phase 5 — P5.1–5.4 collection groups CRUD + card actions (check shared collections repo for group APIs).
  Playback-path ⚠device — P3.x, P6.4/7/8/9/10. Phase 7 LOW remnants.
  TEMPLATE for new admin/list screens: TvAdminScansViewModel/Screen (commit 05fd826) — VM mirrors phone VM,
  screen = Header + LazyColumn of Cards -> TvOptionDialog; wire TvRoute (TvMainRoute) + TvMainShell composable +
  AndroidTvModule viewModel{} + TvAdminHubScreen entry. Text entry: TvTextInputDialog. Dialogs over detail: focusable Popup.
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
