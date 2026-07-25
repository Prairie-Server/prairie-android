# Android TV Full Parity Rework — Design

**Date:** 2026-05-23
**Status:** Approved; revised post-implementation audit (2026-05-23)
**Author:** Brainstormed via collaborative session
**Scope:** `androidTvApp/`, `android-shared/`, `shared/`

## Revision history

- **2026-05-23 — initial draft.** Sections A–E written against the file-list survey performed earlier in the session.
- **2026-05-23 — post-audit revision.** A forensic read of the actual `androidTvApp/` tree (which had moved further than the survey suggested) reset the status of every A.x subsection. Sections now carry a **Status:** header with concrete file/line refs and explicit gaps. Implementation order updated accordingly. Sub-projects B–E remain entirely outstanding and are unchanged.

## Current implementation status (audit results)

| Sub-project | Status | Headline |
|---|---|---|
| A.1 Top menu bar | ✅ Shipping | `TvTopMenuBar.kt` wired in `TvMainShell`. Scroll-hide implemented via `NestedScrollConnection` on the content Box (`TvMainShell.kt:167-184`) — `available.y < 0` (content scrolling down) fades menu out, `> 0` fades it back in via `Animatable(1f)` driven through `graphicsLayer` so layout doesn't reflow. Up-arrow on the top content row restores focus to the menu (`:201-209`). Dead code (`TvNavigationDrawer.kt` + drawer color tokens) removed during the original A.1 polish; only the spec status entry was stale. |
| A.2 Ambient backdrop | ✅ Shipping | Closed by plan `docs/superpowers/plans/2026-05-23-a2-palette-tint-propagation.md` (commits `1526914`, `7a73aca`, `efdf106`, `269913b`). `androidx.palette` dep added, `AmbientBackdropTintState` + `LocalAmbientBackdropTint` published from `TvHomeScreen`, backdrop applies 0.18α accent multiply with 600ms crossfade. Manual emulator QA still pending. |
| A.3 HUD redesign | A.3a ✅ · A.3b ✅ · A.3c ✅ · A.3d-gravity ✅ · A.3d-hdr ✅ · A.3f ✅ | A.3a (commits `4fea448`, `cb26572`, `fe5d7fc`): 6 tabs always visible, HUD persists until Back, legacy menus deleted. A.3b (commits `31a3884`, `75bd890`, `04f027e`): chapters wiring sourced from server (`FileVersion.chapters`, populated by FFprobe at ingest — confirmed via cross-repo survey of `prairie-server/internal/catalog/detail.go:258-266` + Apple's matching consumer). New `VersionChapter` model in `shared/commonMain` (3 serialization tests); `TvPlayerViewModel.UiState.chapters` populated from selected version; scrubber tick marks light up via existing `ChapterInfo` scaffolding; HUD Chapters pane is a focus-driven picker replacing the placeholder (placeholder remains as empty-state fallback). Media3 1.10 has no first-class chapter API — server data is the only viable source, and conveniently matches Apple's contract exactly. Chapter thumbnails (server provides `thumbnail_url`/`thumbnail_thumbhash`) intentionally deferred — text rows ship; thumbhash rendering pending ThumbhashImage API review. A.3c (commits `951abfc`, `b4da035`): Stats pane via `PlaybackAnalyticsListener` + `PlayerStatsSnapshot` reducer (6 tests). A.3d-gravity (commits `deef1ca`, `81e8193`, `d0ce36f`): Video pane `Fill mode` toggle (Letterbox / Zoom) via `AspectRatioFrameLayout.RESIZE_MODE_*` on `PlayerView`; multi-video-track picker preserved as secondary section. A.3d-hdr (commits `780bb3d`, `8e61d23`, `04975f0`): HDR On/Off chip pair above Fill mode in the Video pane, bound to existing per-profile `hdrEnabledFlow`. `TrackSelectionPresets.buildTvVideoMimePreferences` gains an `allowHdr` param (3 tests); when false, Dolby Vision MIME is dropped from the preferred list so the selector picks H.265/H.264 over DV on multi-track content. `applyTrackSelectionPresets` forwards `hdrEnabled`; screen's `LaunchedEffect` re-runs on toggle so the change takes effect mid-playback. Honest constraint vs Apple: Media3 has no surface-level SDR forcing (AVPlayer's `setHDREnabled` does) — for single-track HDR-only files the toggle becomes a per-file no-op. A.3f (commits `1a849d3`, `387814c`, `8de05c9`): `SubtitleOffsetHolder` (AtomicLong; 4 tests) + `OffsetSubtitleParserFactory` wrap `DefaultSubtitleParserFactory` and shift each `CuesWithTiming.startTimeUs` at parse time. `ContinuumPlaybackService` collects per-profile `subtitleSyncMsFlow`, pushes into holder, forces `seekTo(currentPosition)` on change so buffered cues are dropped. HUD Subtitles pane shares the `DelayStepperRow` extracted from the audio pane (single source of truth for the −50/−10/Reset/+10/+50 stepper). All A.3 sub-projects shipping. |
| A.4 Split-panel login | ✅ Shipping (visual reskin) · QR-flow logic still pending sub-project C | Closed by `docs/superpowers/plans/2026-05-23-a4-split-panel-login-reskin.md` (commit `6cde26d`). Form extracted to `CredentialFormCard`; new `QrPlaceholderCard` renders a 320×320 dp non-focusable placeholder with "Loading device-login code…" text. Layout: 620dp form + 18dp gap + 480dp QR card. Sub-project C will populate the placeholder with real device-login QR + polling logic. |
| A.5 Filter UX | ✅ Shipping (option 2 — full bottom-sheet) | Closed by `docs/superpowers/plans/2026-05-23-a5-filter-bottom-sheet.md` (commits `236b718`, `3113a18`, `a5c8e4d`, `f844832`, `7af3f6d`). `TvFilterSheet` bottom 60% panel with Genre / Year (decade chips) / Sort / Jump-to sections (all `FlowRow` wrapped, scrolling Column inside sheet). Active-filter pill row above grid. Year wired via existing `yearMin`/`yearMax` browse params (no backend change). `TvAlphabetRail.kt` now orphan — deleted in the cleanup commit. |
| A.6 Focus polish | A.6a ✅ Shipping (card lift) · A.6b deferred (Palette glow) | A.6a closed by `docs/superpowers/plans/2026-05-23-a6a-card-lift-default.md` (commits `5093086`, `53160ec`): `continuumCardDefaults` default `focusedScale = 1.08f`. A.6b (per-card Palette glow) deferred — requires per-poster extraction infra not introduced by A.2. |
| A.7 Theme tokens | ✅ Shipping | Tokens are tvOS-faithful. All three placeholder spacing tokens present in `Spacing.kt`: `hudPanelInset = 24.dp` (line 28), `topMenuBarHeight = 64.dp` (line 31), `heroBackdropFade = 120.dp` (line 34). |
| B Watch Next | ✅ Shipping | Closed by `docs/superpowers/plans/2026-05-23-b-watch-next-launcher.md` (commits `17432e8`, `bb24193`, `5841155`, `2ae85da`, `f9324bf`, `d4c3d3c`). `androidx.tvprovider` + `koin-androidx-workmanager` deps added; manifest declares `WRITE_EPG_DATA`/`READ_EPG_DATA` + `continuum://` intent filter. New `watchnext/` package: `WatchNextProgramMapper` (pure, 11 tests) maps `SectionItem`→`WatchNextProgramFields` for `continue_watching`/`next_up`; `WatchNextRepository` wraps `TvContractCompat.WatchNextPrograms` ContentResolver with `diffAndApply` (insert/update/delete by `internalProviderId`) + `clearAll`; `WatchNextSyncWorker` (Koin-built CoroutineWorker) pulls `/api/v1/home/sections` and applies the diff; `WatchNextSeeder` enqueues expedited one-shot + 1h periodic WorkManager jobs and exposes `clear()`. Lifecycle hooks: seed on login success + profile select, clear on sign-out + profile switch (all wired in `TvAppNavigation`). `MainTvActivity.onNewIntent` + cold-launch `handleIntent` publish `continuum://` URIs to a shared `pendingDeepLink` flow that `TvAppNavigation` collects to route to ItemDetail/Player. Reactive on-playback-end re-seed not wired (spec leaves to a future commit — periodic 1h refresh covers the gap). |
| C Device login | ✅ Shipping (C.6 setup-screen QR deferred) | Closed by `docs/superpowers/plans/2026-05-23-c-device-login.md` (commits `e871da9`, `8ed4b77`, `b918ff5`, `140237a`, `ec42718`). `shared/commonMain` ships `DeviceLoginModels` + `DeviceLoginApi` + `DeviceLoginRepository` (state machine: Idle → Initiating → Awaiting(session) → Approved(tokens) / Failed(reason), 7 unit tests). Wire format mirrors Apple's `DeviceLoginModels.swift` verbatim (snake_case @SerialName; 404 on poll = Failed.Expired; server-supplied `poll_after` honored; status enum `pending`/`approved`/`denied`/`expired`/`consumed`/`unknown`). New `QrCodePanel.kt` renders ZXing QR matrix into a Compose Canvas (ECC level M, margin handled by surrounding Box). `TvLoginViewModel` now runs credential + device flows in parallel from `init`; credential success branch explicitly cancels `deviceLoginJob` so a late Approved can't clobber freshly-saved tokens. Device-flow Approved branch lifts tokens into `TokenManager.saveTokens` to match `AuthRepository.login()`'s post-success contract. `QrPlaceholderCard` replaced by `QrLoginCard` (5 branches: Idle/Initiating spinner box, Awaiting QR+code, Approved "Signed in!", Failed message+Try-again pill). New dep `com.google.zxing:core:3.5.3` in `androidTvApp`. C.6 (`TvServerSetupScreen` QR) deferred — to be split out as a follow-up. Manual emulator/Shield QA still pending. |
| D Per-profile settings | ✅ Shipping (audit revealed mostly already done) | Per-profile infrastructure (`AndroidPlayerSettingsStore`, `LibraryPlaybackPrefsStore`, `ProfileRepository`) was already in production. D closed by `docs/superpowers/plans/2026-05-23-d-tv-preferences-cleanup.md` (commits `3f86124`, `1aef6e4`, `11cb631`): moved the last live key (`selectedLibraryId`) from global `TvPreferences` to a new `TvLibrarySelectionStore` (per-profile DataStore, hashed filename) and deleted `TvPreferences.kt` entirely along with its dead migration reads in `TvSettingsViewModel`. UI enums (`PlaybackQuality`/`SubtitleMode`/`SubtitleSize`) extracted to `TvSettingsEnums.kt` to preserve consumers. |
| E Player route matrix | ✅ Shipping (RouteSelector + persistent override deferred) | Closed by `docs/superpowers/plans/2026-05-23-e-player-route-matrix.md` (commits `96d8016`, `d801826`, `34f8801`, `0bda5c3`). New `player/route/` package with `PlaybackRoute` enum (Compatibility/NativeDirect/Hls), `RouteCapability` data class, and `RouteCapabilityMatrix` (taxonomy + capability table mirroring `tvos-player/05-route-capability-matrix.md`; 6 tests). New `player/audio/DelayAudioProcessor` (Media3 `AudioProcessor`, ±500ms clamp, head silence on positive delay / head drop on negative, hot-reconfigurable, applies at next flush; 6 tests). Wired through `ContinuumPlayerFactory` via `DefaultAudioSink.Builder.setAudioProcessorChain` and registered as a Koin `single` in `PlayerModule`. `ContinuumPlaybackService` collects `playerSettingsStore.audioSyncMsFlow`, mirrors into the shared processor, and forces a `seekTo(currentPosition)` on change so the new delay applies mid-playback. HUD Audio pane stepper row (−50/−10/Reset/+10/+50 ms, ±500 clamp) wired in `TvPlayerHud` + `TvPlayerScreen` via new `audioDelayMs` StateFlow + `onAudioDelayChanged` on `TvPlayerViewModel`. **Deferred (E.5/E.7):** `RouteSelector` decision-tree class, `RouteResolution` reasons-list HUD surface, and per-profile `playback_route_override` preference — observational taxonomy is enough to unblock A.3f and the HUD; the runtime route is still MIME-driven via `DefaultMediaSourceFactory`. |

**Critical path:** A.2 must finish (Palette + CompositionLocal) before A.6's per-card glow color can land, before A.3 (HUD) reuses the same Palette cache.

**Revised implementation order** (✅ = completed; → = next):

✅ A.7 → ✅ A.1 polish (incl. scroll-hide + dead-code cleanup) → ✅ A.2 finish → ✅ A.6a → ✅ A.5 → ✅ A.3a → ✅ A.4 → ✅ A.3c → ✅ A.3d-gravity → ✅ D → ✅ B → ✅ C (device login) → ✅ E (player route taxonomy + audio delay; RouteSelector deferred) → ✅ A.3f (subtitle delay stepper) → ✅ A.3d-hdr (HDR toggle via track-selector reconfig) → ✅ A.3b (chapters wiring from server) → → A.6b (deferred), E.5/E.7 RouteSelector + per-profile override, C.6 setup-screen QR.

## Why this exists

The Android TV app and the tvOS app (`/opt/prairie-apple`) ship the same Prairie product against the same `/api/v1/*` backend, but the Android TV UX and feature set lag the tvOS sibling on six dimensions: top-level navigation chrome, home backdrop treatment, player HUD layout, login screen layout, library filter affordance, and focus polish — plus four feature gaps: Watch Next launcher integration, QR-code device login, per-profile settings scoping, and a route-capability model for playback (including audio delay). A side-by-side review concluded that the Android TV experience feels visually generic and is missing system-integration and settings parity that the tvOS sibling has.

This document specifies the work to close that gap, broken into five named sub-projects (A–E) that can be designed as one unit but implemented and shipped independently. The reference target is tvOS, not the other way around — tvOS code is not modified by this work.

## Non-goals

- No iOS / tvOS code changes.
- No new screens beyond what tvOS already has.
- No re-platforming of Compose, Media3, Koin, Ktor, or Kotlin versions.
- No Material 3 → tv-material migration audit beyond components rewritten here.
- No phone-app (`androidApp/`) UI work. Changes to `shared/` and `android-shared/` will naturally be available to the phone app, but no phone-specific surface is added.
- Player sub-project (E) does not exceed Apple's coverage — we match the architecture, we do not lead.

## Constraints

- Compose-for-TV 1.0.1, Media3 1.10.0, Kotlin 2.1.20 — fixed.
- Existing `continuumFocus()` / `continuumCardDefaults()` modifiers (`androidTvApp/.../ui/theme/FocusModifier.kt`) are the right abstraction layer — extend, don't replace.
- `AGENTS.md` testing philosophy applies: focused tests only for non-trivial logic (HUD state machine, route selector, settings migration, ContentProvider). No tests for restyling.
- Settings migration (sub-project D) must preserve existing user state. `TvPreferences` keys are currently global; we migrate values into the active profile's bucket, we do not wipe.
- `applicationId com.continuum.app.tv` and `MainTvActivity` stay as-is for install continuity.

## Sub-project map

| Letter | Area | Primary surface |
|---|---|---|
| A | Visual rework | `androidTvApp/ui/` |
| B | Watch Next channel (Top Shelf equivalent) | `androidTvApp/` + new `androidx.tvprovider` integration |
| C | QR-code device login | `shared/auth/` + `androidTvApp/auth/TvLoginScreen.kt` |
| D | Per-profile settings | `androidTvApp/data/preferences/TvPreferences.kt` + DataStore migration |
| E | Player route-capability matrix + audio delay | `android-shared/player/` + `androidTvApp/player/` |

Implementation order (revised post-audit, with completion markers): **✅ A.7 → ✅ A.1 polish (incl. scroll-hide + drawer cleanup) → ✅ A.2 finish → ✅ A.6a (card lift) → ✅ A.5 (bottom-sheet) → ✅ A.3a (HUD structure) → ✅ A.4 (split-panel login) → ✅ A.3c (Stats) → ✅ A.3d-gravity (Video fill mode) → ✅ D → ✅ B → ✅ C → ✅ E (route taxonomy + DelayAudioProcessor; RouteSelector + per-profile override deferred) → ✅ A.3f (subtitle delay stepper via offset parser wrapper) → ✅ A.3d-hdr (HDR toggle via track-selector reconfig) → ✅ A.3b (chapters wiring from server FileVersion.chapters) → A.6b (deferred), E.5/E.7 RouteSelector + per-profile override, C.6 setup-screen QR.** D before B because B's per-profile reseed depends on D's `ProfileKey` flow. D before E because E.7's per-profile preferences (`playback_route_override`, `audio_delay_ms`) assume the per-profile DataStore is in place. A.2 before A.6 because both share the Palette extraction cache. A.3f after E because the delay sliders depend on `DelayAudioProcessor`. **A.3b moved to deferred:** Media3 has no first-class chapters API; chapters may come from MKV/MP4 file metadata (custom extractor), HLS `#EXT-X-DATERANGE` markers, or a Prairie server response — needs investigation before a tight plan can be written.

---

## Sub-project A — Visual rework

### A.1 — Floating top menu bar (`TvTopMenuBar`)

**Status: ~90% shipping.** `TvTopMenuBar.kt` (538 lines) is implemented and wired into `TvMainShell.kt:289-302`. Centered cluster (Search / Home / Libraries / ForYou) at lines 174–236; right cluster (Profile pill + avatar with chevron) at lines 239–256 + 486; D-pad wrap via `focusProperties { left/right }` at lines 198, 255; up/down handoff in `TvMainShell:171-178`; active pill from `NavController.currentBackStackEntryAsState()` at `TvMainShell:111-113`; `zIndex(1f)` + scrim at lines 155–162, 299–302.

**File:** `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/shell/TvTopMenuBar.kt` (NOT under `ui/components/` as originally specified — actual location).

**Outstanding work:**
- **Gap:** No scroll-hide. Spec wanted the bar to hide on scroll-down inside a focused row (via `nestedScroll`); reappear on focus-up. Not wired. Implement by hosting a `NestedScrollConnection` in `TvMainShell` that drives an offset/alpha animation on the bar.
- **Dead code:** `TvNavigationDrawer.kt` (737 lines) is unreferenced. Confirmed by Color.kt comment ("Phase 2 replaces the drawer with a top menu bar and these become dead code that gets pruned along with TvNavigationDrawer"). Delete the file + the drawer-only color tokens in Color.kt: `DrawerSurface`, `DrawerMenuSurface`, `DrawerOutline`, `DrawerScrimStart`, `DrawerScrimMid`, `DrawerScrimEnd`, `DrawerSelectedSurface`, `DrawerSelectedBorder`, `DrawerIconSurface`.

### A.2 — Ambient hero backdrop

**Status: ✅ Shipping (manual emulator QA pending).** Closed by plan `docs/superpowers/plans/2026-05-23-a2-palette-tint-propagation.md` across four commits (`1526914`, `7a73aca`, `efdf106`, `269913b`).

**What landed:**
- `androidx.palette:palette-ktx:1.0.0` added to the version catalog and `androidTvApp` build.
- New file `androidTvApp/.../ui/components/AmbientBackdropTint.kt` — state class (with `contentId`-keyed stale-result guard), `LocalAmbientBackdropTint` CompositionLocal (`Empty` default for off-Home consumers), and `rememberAmbientBackdropTintState()` composable that loads bitmaps via `SingletonImageLoader` and runs `Palette.from(bitmap).generate()` on `Dispatchers.IO` with `vibrant ?: muted ?: dominant` fallback.
- `TvRootHeroBackdrop.kt` gained a fifth overlay multiplying the accent at 0.18α with a 600ms `animateColorAsState` crossfade. No-op when accent is null.
- `TvHomeScreen.kt` provides `LocalAmbientBackdropTint`, tracks the active hero in `var activeHeroItem`, seeds `tintState` via `LaunchedEffect`, and consumes `TvHomeHeroCarousel.onActiveItemChanged` (which already existed) to advance both. `featuredItem` removed.
- Tests: `AmbientBackdropTintStateTest` (3 cases) covers happy path, stale-result guard, and null clear. Bootstrap of `androidTvApp` `androidUnitTest` source set added.

**Implementation note vs original outstanding-work list:** the carousel itself was NOT modified — its `onActiveItemChanged` callback was already present. The caller (`TvHomeScreen`) now consumes it. Type name in the original outstanding work was `BrowseItem`; the actual code uses `SectionItem` (its KMP equivalent on the carousel path). Both are documented post-fact for the reader.

Unblocks A.6's per-card Palette glow (will share the same color cache via `LocalAmbientBackdropTint`).

### A.3 — Player HUD redesign (`TvPlayerInfoHud` with 6 tabs)

**Status: A.3a ✅ Shipping; A.3b/c/d/f deferred.** Audit revealed the original "0% — fully outstanding" was wrong; the HUD was already ~70% built when this spec was written (5 conditional tabs in `TvPlayerHud.kt` with state in `TvPlayerViewModel.UiState`). A.3a closed the structural gap.

**A.3a landed** (`docs/superpowers/plans/2026-05-23-a3a-hud-six-tabs-and-autohide.md`, commits `4fea448`, `cb26572`, `fe5d7fc`):
- All 6 tabs always rendered (Info / Stats / Video / Audio / Subtitles / Chapters) with empty-state placeholders for tabs lacking data.
- HUD survives independent of global controls auto-hide. The 5s timer at `TvPlayerScreen.kt` now gates on `!state.hudOpen`; HUD persists until explicit Back.
- Legacy `TvSubtitleMenu` + `TvAudioTrackMenu` modals + `TvTrackMenus.kt` deleted (subsumed by HUD tabs; no remaining call sites).

**Intentional deviation from the original spec text below:** original called for "HUD auto-hides after 5s inactivity." Implemented behavior is HUD persists until Back — better UX (user explicitly opened the HUD; auto-dismiss would lose their place). The controls strip retains its 5s timer.

**Outstanding work — separate plans:**
- **A.3b — chapters wiring**: extract `MediaItem.MediaMetadata.chapters` from Media3; populate the Chapters pane; thread chapters to the scrubber's `chapters: List<ChapterInfo>` parameter (currently always `emptyList()`).
- **A.3c — Stats wiring**: hook `PlaybackAnalyticsListener` to expose bitrate, codec, dropped frames, HDR mode; populate the Stats pane.
- **A.3d — Video pane toggles**: HDR force-SDR, video gravity (letterbox vs zoom).
- **A.3f — audio/subtitle delay sliders**: depends on `DelayAudioProcessor` from sub-project E.

The original outstanding-work section below is preserved for historical context.


**New files:**
- `androidTvApp/.../ui/screens/player/TvPlayerInfoHud.kt`
- `androidTvApp/.../ui/screens/player/TvPlayerInfoHudTab.kt` — sealed class: `Info, Stats, Video, Audio, Subtitles, Chapters`.
- `androidTvApp/.../ui/screens/player/panes/{InfoPane,StatsPane,VideoPane,AudioPane,SubtitlesPane,ChaptersPane}.kt`

**Folded / removed:**
- `TvPlayerHud.kt`, `TvPlayerTransportCluster.kt`, `TvTrackMenus.kt`:
  - Transport (play/pause, ±10 s, scrubber) stays bottom-anchored as a thin control strip in the idle state (matches Apple's `TVPlayerControls` idle: thin scrubber + icon transport).
  - Audio/subtitle menus become the `AudioPane` / `SubtitlesPane` of the new HUD.
- `TvPlayerScrubber.kt` kept, slimmed visually; hold-to-fast-seek behavior unchanged.

Mirrors Apple `TVPlayerInfoHUD.swift`:

- Two display states: **Idle** (thin scrubber + transport at bottom) and **HUD open** (top-center floating panel with tab strip + active pane below).
- D-pad up from idle scrubber opens HUD (default focus on `Info`). Left/right move between tabs (row with `Modifier.focusGroup()`). Down moves into active pane.
- HUD auto-hides after 5 s inactivity; timer resets on any key event.
- Back/Menu cycles: hide HUD → dismiss transport → exit player. Mirrors Apple's `onExitCommand` chain.

**State machine:** extracted to `TvPlayerHudController` (plain Kotlin class, not a ViewModel) holding `StateFlow<HudState>` where `HudState = Idle | Open(tab, lastActivityMs)`. Drives auto-hide via `delay()` in a coroutine scope. **This is the only new piece with focused unit tests** — tab navigation, auto-hide, exit-cycle.

**Pane contents** (parity with Apple):

| Pane | Contents |
|---|---|
| Info | Title, year, runtime, overview snippet |
| Stats | Current bitrate, dropped frames, decoder name, resolution, HDR mode (from `PlaybackCapabilityDetector` + Media3 `Format`) |
| Video | Route info (until E lands: shows "Single engine"), HDR toggle, video gravity |
| Audio | Track list, delay slider (provisioned but disabled until E lands; "unsupported" label matches Apple's current state) |
| Subtitles | Track list, size, style, delay |
| Chapters | List from `MediaItem.MediaMetadata` |

### A.4 — Split-panel login reskin

**Status: 0% — fully outstanding.** `TvLoginScreen.kt` has no split-panel structure and no QR placeholder. Design below applies as written.


**Modified:** `androidTvApp/.../ui/screens/auth/TvLoginScreen.kt`

Mirrors Apple `TVLoginView`:

- Two-column `Row`: left = credential form (username + password + sign-in), right = QR-code panel.
- For this sub-project the QR panel is a **static placeholder** (320×320 dp neutral panel + "Loading device-login code…" text). The live device-login fetch + polling lands in C.
- Focus default lands on username. D-pad right traverses into the QR panel (non-focusable until C lands).

### A.5 — Filter bottom-sheet (`TvFilterSheet`)

**Status: ✅ Shipping (option 2 — full bottom-sheet).** Closed by plan `docs/superpowers/plans/2026-05-23-a5-filter-bottom-sheet.md` across five commits (`236b718`, `3113a18`, `a5c8e4d`, `f844832`, `7af3f6d`).

**What landed:**
- `TvLibraryBrowseFilter` extended with `yearMin: Int?, yearMax: Int?`; ViewModel gained `onYearRangeChanged` handler that threads to the existing `catalogRepository.browse()` call (the params were already accepted).
- New `TvFilterSheet.kt` — bottom-anchored slide-up panel, 40% scrim + 60% sheet, `BackHandler`, focus-trapped via `focusGroup` + `FocusRequester`.
- New `TvLibraryYearOptions.kt` — pure module synthesizing decade chips (`2020s`/`2010s`/.../`Older`) from `currentYear` argument, with `match()` for reverse-lookup. 5 unit tests in `TvLibraryYearOptionsTest`.
- `TvLibraryDetailScreen.kt` reworked: `showGenrePicker`/`showSortPicker` collapsed to `showFilterSheet`; old `TvFullScreenPicker` filter dialogs deleted; edge `TvAlphabetRail` deleted; new "Filter" button + active-filter pill row added; sheet renders four sections (Genre, Year, Sort, Jump-to) as `FlowRow`s with vertical wrapping and a scrolling `Column` container. `FilterDropdownButton` removed (zero remaining callers); `TvAlphabetRail.kt` deleted as orphan.
- New private helpers in `TvLibraryDetailScreen.kt`: `FilterEntryButton`, `ActiveFilterPill`, `FilterSectionHeader`, `FilterChoiceChip`.

**Deviations from original outstanding-work list:**
- Apply-on-close was specced but apply-on-change is implemented (preserves existing ViewModel semantics and matches the established filter handler pattern).
- Alphabet section uses inline `FlowRow` chips inside the sheet instead of the previous vertical `TvAlphabetRail` component (which assumed vertical layout); rail component deleted as a result.

`TvFullScreenPicker.kt` retained — still used by `TvLibrariesScreen.kt`.

Mirrors Apple `TVLibraryFilterSheet`:

- Slide-up sheet anchored to bottom 60% of screen, dark surface, focus-trapped.
- Sections: **Genre** (chips), **Year** (range or chip list per `/api/v1/catalog/filters`), **Sort** (Title / Year / Date Added / Rating), **Alphabet** (existing `TvAlphabetRail` moves inside the sheet).
- Apply on close; close on Back. A thin "active filters" pill row reflects selection on the library page when the sheet is closed.

### A.6 — Focus treatment polish

**Status: ~70% shipping.** `continuumFocus()` defaults are correct (scale `1.03f`, spring `0.72/380`, shadow `14f`, border `2.dp AccentLavender`). `continuumCardDefaults()` defaults `focusedScale = 1f` — meaning poster cards (`TvMediaCard`) do not lift on focus unless the caller overrides. Some call sites already override (`TvEpisodeCard` → 1.04f, `TvHeroToggleIconButton` → 1.08f, `TvProfileSelectionScreen` → 1.08f), but `TvMediaCard` uses the default and therefore doesn't lift.

**File:** `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/theme/FocusModifier.kt`

**Outstanding work:**
- **Card lift:** change `continuumCardDefaults(focusedScale: Float = 1f)` default to `1.08f`. Audit overrides — any that match `1.08f` become redundant and can be dropped.
- **Palette-derived glow (depends on A.2):** add an optional `paletteColor: Color?` parameter to `continuumCardDefaults`. When non-null, use it for `Glow.elevationColor` instead of the hardcoded `ContinuumBlueGlow`. Update `TvMediaCard` (and `TvEpisodeCard`) to query the per-poster color from the same Palette cache A.2 introduces and pass it in.
- **Spring/shadow tune (optional):** no concrete spec value differs from current; defer unless visual comparison against tvOS shows a mismatch.

### A.7 — Theme tokens

**Status: ~95% shipping.** `Color.kt`, `Type.kt`, `Spacing.kt`, `Theme.kt` are already tvOS-faithful with explicit comments documenting the alignment. No work needed there.

**Outstanding work:**
- **Three placeholder spacing tokens** referenced by A.3 (and possibly A.1 once scroll-hide ships): add `Spacing.hudPanelInset`, `Spacing.topMenuBarHeight`, `Spacing.heroBackdropFade` to `androidTvApp/.../ui/theme/Spacing.kt`. No consumers yet — values can be conservative defaults (e.g. `24.dp`, `64.dp`, `120.dp`) and tuned when first used.
- `TvUiDensityScale = 0.86f` retained — already in place.
- New spacing tokens: `Spacing.hudPanelInset`, `Spacing.topMenuBarHeight`, `Spacing.heroBackdropFade`.

### A — Integration points

- `LocalAmbientBackdropTint` (A.2) and existing `LocalDensity` (A.7) keep state propagation out of ViewModels.
- `TvPlayerHudController` is the only new piece of logic; everything else is pure recomposition.
- New dependency: `androidx.palette:palette` (if not already transitively present). No other new dependencies.

---

## Sub-project B — Watch Next channel (Android-TV Top Shelf equivalent)

### B.1 — System surface choice

Android TV offers two launcher surfaces: custom Preview Channels and the system-managed Watch Next row. We target **Watch Next only** — it is the direct equivalent of Apple's Top Shelf "Continue Watching" + "Next Up" sections (same UX intent, same launcher placement) — and avoids a duplicate Prairie-branded channel that nobody curates.

### B.2 — New files

```
androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/watchnext/
├── WatchNextRepository.kt        # ContentResolver wrapper (insert / update / delete)
├── WatchNextProgramMapper.kt     # BrowseItem → WatchNextProgram conversion
├── WatchNextSyncWorker.kt        # CoroutineWorker, periodic + on-demand
└── WatchNextSeeder.kt            # One-shot post-login seed
```

### B.3 — Manifest changes

`androidTvApp/src/androidMain/AndroidManifest.xml`:

```xml
<uses-permission android:name="com.android.providers.tv.permission.WRITE_EPG_DATA" />
<uses-permission android:name="com.android.providers.tv.permission.READ_EPG_DATA" />

<activity android:name=".MainTvActivity" …>
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="continuum" />
    </intent-filter>
</activity>
```

### B.4 — Behavior model

- **Source of truth:** existing `/api/v1/home/sections` (already returns Continue Watching + Next Up). No new endpoint.
- **Mapping:** each `BrowseItem` becomes a `WatchNextProgram` (`androidx.tvprovider.media.tv.WatchNextProgram.Builder`):
  - `setType(WATCH_NEXT_TYPE_CONTINUE)` for in-progress; `WATCH_NEXT_TYPE_NEXT` for next-episode candidates.
  - `setLastEngagementTimeUtcMillis(item.userState.lastWatchedAtMs)` for system ordering.
  - `setPosterArtUri(item.backdropUrl)` + `setPosterArtAspectRatio(ASPECT_RATIO_16_9)` for landscape Watch Next tiles (matches Apple Top Shelf's wide-image style).
  - `setIntentUri("continuum://item/${item.contentId}")`. A separate `continuum://play/${id}` deep link lands users directly in the player. Matches Apple's `continuum://item/` vs `continuum://play/` split.
- **Update model:**
  - **Seed:** `WatchNextSeeder` runs once on first successful login; re-runs on profile switch.
  - **Periodic:** `WatchNextSyncWorker` via WorkManager on a 1-hour periodic constraint (`Constraints.Builder().setRequiredNetworkType(CONNECTED).build()`), persists across app death.
  - **Reactive:** one-shot expedited `WatchNextSyncWorker` on (a) playback session end (any `MediaSessionService.onPlaybackStatusChanged` reaching `STATE_ENDED` or session destroy), (b) profile switch. An explicit per-item "remove from Watch Next" user action is *not* in scope for B — Android's Watch Next system row already provides a long-press "Remove from row" gesture handled by the launcher; we honor that by simply not re-inserting the item on the next sync (the launcher's removal sets a flag the system surfaces via `WatchNextPrograms`'s `BROWSABLE` column).
- **Removal:** rows for items that drop out of the API response (finished, removed from watchlist) are deleted by content URI in the same sync pass. `WatchNextRepository.diffAndApply(remoteSet, localSet)` computes inserts/updates/deletes.
- **Per-profile:** rows are scoped to the active profile. Profile switch triggers full-delete + reseed (the launcher cannot show multiple profiles' tiles).
- **Cross-process auth:** not a concern — workers run in-process and use the Koin-injected Ktor client with the existing `AuthInterceptorImpl`. Refresh is automatic.

### B.5 — Deep-link handler

`MainTvActivity.onNewIntent()` and `onCreate()` parse `intent.data`:
- `continuum://item/<id>` → push `TvRoute.ItemDetail(id)` onto `NavController`.
- `continuum://play/<id>` → push `TvRoute.Player(id)` directly.

If unauthenticated, the deep link is stashed in a `pendingDeepLink: StateFlow<Uri?>` on `MainTvViewModel` and consumed by the auth flow on successful login.

### B.6 — Koin wiring

`androidTvApp/.../di/AndroidTvModule.kt`:
- `single { WatchNextRepository(androidContext()) }`
- `single { WatchNextProgramMapper() }`
- `worker { WatchNextSyncWorker(get(), get(), get(), get()) }` (via `koin-androidx-workmanager`).
- `single { WatchNextSeeder(get(), get()) }`

New dependency: `io.insert-koin:koin-androidx-workmanager:4.1.0`. `androidx.work:work-runtime-ktx` may be transitively present (Coil 3 pulls it); add explicitly if not.

### B.7 — Testing

- `WatchNextRepositoryTest` — Robolectric or instrumented test against a fake `ContentResolver`, verifying diff logic.
- `WatchNextProgramMapperTest` — pure unit test.
- `WatchNextSyncWorkerTest` — `WorkManagerTestInitHelper` driving the worker against a mocked sections response.
- Seeder, deep-link handler, live worker → ContentProvider integration: manual e2e on a real Android TV device.

### B.8 — Known limits

- Watch Next tiles only appear after first launch (Android TV launcher policy). Acceptable; no workaround.
- Tile image quality is governed by the launcher; we hint via `ASPECT_RATIO_16_9` + `backdropUrl`.
- `WRITE_EPG_DATA` is a normal permission on Android TV; no runtime grant flow needed.

---

## Sub-project C — QR-code device login

### C.1 — Flow model

Standard OAuth 2.0 Device Authorization Grant (RFC 8628), matching Apple's `QRLoginViewModel`:

1. TV calls `POST /api/v1/auth/device` → server returns `{ device_code, user_code, verification_uri, expires_in, interval }`.
2. TV renders a QR code encoding `verification_uri` (typically `https://<server>/device?code=<user_code>`). User scans on phone, signs in, approves.
3. TV polls `POST /api/v1/auth/device/token` every `interval` seconds with `device_code` until success / `pending` / `slow_down` / `denied` / `expired`. Success returns `{ access_token, refresh_token }`.

**Pre-implementation verification (before writing C code):** open `/opt/prairie-apple/iosApp/iosApp/Networking/ContinuumAPI.swift` and `QRLoginViewModel.swift` (or its equivalent) and read off the exact endpoint paths, JSON field names, and `slow_down` / `expired` / `pending` literal strings the Apple client sends and accepts. Mirror those verbatim in Kotlin. The endpoint shape in C.1 above is the OAuth 8628 default; if Prairie deviates, Apple is the source of truth.

### C.2 — New files in `shared/commonMain`

KMP placement means phone inherits automatically; no scope creep into `androidApp`.

```
shared/src/commonMain/kotlin/com/continuum/app/shared/auth/
├── DeviceLoginApi.kt           # Ktor endpoint wrappers
├── DeviceLoginModels.kt        # @Serializable request/response data classes
├── DeviceLoginRepository.kt    # initiate() + poll() + emits StateFlow<DeviceLoginState>
└── DeviceLoginState.kt         # sealed class: Idle / Initiating /
                                #   Pending(userCode, verificationUri, expiresAtMs) /
                                #   Succeeded(authToken) / Failed(reason)
```

`DeviceLoginRepository.poll()` is a `Flow<DeviceLoginState>` built from `flow { while(active) { delay(intervalMs); … } }`. Honors `slow_down` by doubling `intervalMs`. Cancels cleanly on collector cancellation.

### C.3 — Android TV wiring

```
androidTvApp/.../ui/screens/auth/
├── TvLoginScreen.kt            (modified — split-panel already shipped in A.4)
├── TvLoginViewModel.kt         (modified — orchestrates both flows)
└── QrCodePanel.kt              (new — Compose composable)
```

`TvLoginViewModel` exposes parallel state flows:
- `credentialState: StateFlow<CredentialUiState>`
- `deviceState: StateFlow<DeviceLoginState>`

On `init`, kicks off `deviceLoginRepository.initiate()` + starts polling. Either flow reaching `Succeeded` calls `authService.completeLogin(token)`; the other is cancelled. Races to authentication.

`QrCodePanel(verificationUri, userCode, expiresAtMs, modifier)` — renders QR via `com.google.zxing:core` into a Compose `Canvas`. Beneath the QR: `userCode` text, "Open <verification_uri> on your phone", countdown chip. On expiry: "Tap to refresh" CTA → `viewModel.restartDeviceLogin()`.

### C.4 — Dependencies

- New: `com.google.zxing:core:3.5.3` (pure JVM, ~600 KB). No Android-specific zxing dependency.

### C.5 — Error / edge handling

- Server unreachable during `initiate()` → device pane shows "Couldn't reach server" + retry; credential pane stays operational.
- Polling network drop → continue polling with backoff; don't surface transient failures.
- `slow_down` → double `intervalMs` for the session.
- `expired_token` → flip to `Failed(Expired)`; "Code expired" overlay with refresh CTA.
- Profile-selection step (post-login) is unchanged.

### C.6 — Server-setup screen

Apple's `TVServerSetupView` also exposes a setup QR. This sub-project adds the equivalent affordance to `TvServerSetupScreen` (QR encoding `silo-setup://server` or whatever Apple emits — verify against `TVServerSetupView`). User scans, phone confirms, server URL flows back via the same device-grant pattern.

If verifying this turns out to require server changes beyond what's in scope, **split out** as a follow-up. The credential-entry path on `TvServerSetupScreen` remains as the always-available fallback.

### C.7 — Testing

- `DeviceLoginRepositoryTest` in `shared/commonTest` — `runTest` with a mocked `DeviceLoginApi`; polling cadence, `slow_down` honored, cancellation clean.
- `TvLoginViewModelTest` — fakes both flows; whichever completes first wins and cancels the other.
- `QrCodePanel` rendering and `TvServerSetupScreen` QR: visual / device verification, not unit-tested.

### C.8 — Scope note

C.6 (setup-screen QR) is part of the device-login feature work even though the *visual* part of `TvServerSetupScreen` arguably could have lived in A. We keep it here so the visual and behavior changes ship together for that screen.

---

## Sub-project D — Per-profile settings

### D.1 — Current vs. target

| | Today | Target |
|---|---|---|
| Storage | One DataStore file `tv_preferences.preferences_pb` | One DataStore file per `(serverId, profileId)`: `tv_preferences__<serverId>__<profileId>.preferences_pb` |
| Key shape | `playback_quality`, `subtitle_mode`, … (flat) | Same keys, isolated per file |
| Scope of a change | All profiles on the device | Only the active profile on the active server |
| New profile | Inherits whatever the last profile set | Starts with defaults |
| Profile delete | No effect on prefs | Settings file deleted |
| Server delete | No effect on prefs | All profile files for that server deleted |

Mirrors Apple's per-(server, profile) Keychain scoping via `ServerRegistry.shared.activeServerId` + `AuthService.shared.profileId`.

### D.2 — Storage choice

Options considered:
1. **File-per-profile DataStore** (chosen) — clean isolation, trivial enumeration, delete-on-profile-removal is one file delete.
2. Single DataStore with namespaced keys — simpler scaffolding but messy enumeration and cleanup.
3. Proto DataStore with nested map — type-safe but adds Proto schema management for one feature.

Chosen because it matches Apple's per-profile semantics directly and profile-switch perf is dominated by DataStore instance swap, not file I/O (DataStore caches in memory).

### D.3 — New / modified files

```
androidTvApp/.../data/preferences/
├── TvPreferences.kt              (modified — instance class, no longer singleton)
├── TvPreferencesStore.kt         (new — factory: get(serverId, profileId) → TvPreferences)
├── TvPreferencesProvider.kt      (new — reactive provider keyed on active (server, profile))
└── TvPreferencesMigration.kt     (new — one-shot migration from legacy global file)
```

- `TvPreferencesStore` holds a `ConcurrentHashMap<ProfileKey, DataStore<Preferences>>`. `get(key)` lazily creates via `PreferenceDataStoreFactory.create(File(...))`. Path: `<filesDir>/datastore/tv_preferences__${serverId}__${profileId}.preferences_pb`.
- `TvPreferencesProvider.current: StateFlow<TvPreferences>` derived from a session-level `StateFlow<ProfileKey?>` via `mapLatest { key -> store.get(key) }`. Consumers collect from `provider.current.flatMapLatest { it.playbackQuality }`.
- `TvPreferencesMigration.run(context, defaultProfileKey)` runs once on first launch after upgrade: detects legacy `tv_preferences.preferences_pb`, reads all values, writes them as the initial state of `defaultProfileKey`'s DataStore, drops a `migration_done` marker, deletes the legacy file on the next launch.

### D.4 — Consumer audit

Consumers (verified during implementation; this is the starting set):
- `TvSettingsScreen.kt` / `TvSettingsViewModel.kt`
- `TvPlayerViewModel.kt` (reads `playback_quality`, `auto_skip_intro`, `auto_skip_credits`, `audio_language`, `subtitle_*`)
- `TvLibrariesScreen.kt` (reads/writes `libraries_selected_library_id`)

Additional consumers found during implementation are in-scope — there is no clean shippable mid-state where some consumers are profile-scoped and others are global.

### D.5 — Profile / server switch path

Existing `TvProfileSelectionScreen` → `authService.selectProfile(...)`. Extended:
- Profile change pushes the new `ProfileKey(serverId, profileId)` into the session-level `StateFlow`.
- Downstream consumers re-emit via `flatMapLatest`. No explicit invalidation needed.
- Watch Next sync (B) listens to the same `StateFlow` and re-seeds on change.

Sign-out resets the `StateFlow` to `null`. Consumers read synthesized "anonymous defaults" (read-only; setters guard with `require(currentProfileKey != null)`). Avoids accidental writes during between-screens windows.

### D.6 — Cleanup on profile / server deletion

- Profile delete → delete matching `tv_preferences__<serverId>__<profileId>.preferences_pb`.
- Server delete → enumerate `<filesDir>/datastore/` for files matching `tv_preferences__<serverId>__*` and delete each. Implemented in `TvPreferencesStore.deleteForServer(serverId)`.
- Deletion happens after DataStore close to release the in-memory handle.

### D.7 — Testing

- `TvPreferencesStoreTest` — Robolectric: two profile keys, verify isolation.
- `TvPreferencesMigrationTest` — seed legacy file, run migration, verify values land in target profile's DataStore + legacy file removed.
- `TvPreferencesProviderTest` — `runTest` with fake profile-key flow; assert emission on key change.
- Settings UI: not unit-tested (no semantics change).

### D.8 — Risks / call-outs

- **One-way migration.** Downgrade to pre-migration build wouldn't see the values. Acceptable for an internal app; flag in commit message.
- **DataStore file count grows with profiles.** Mitigated by D.6 deletion hooks. No proactive "trim old profiles" sweep.
- **Profile-key boundary mid-playback.** If a profile switch happens during playback, the player sees new settings on next read. We do not invalidate in-flight playback. Matches Apple's current behavior.

---

## Sub-project E — Player route-capability matrix + audio delay

### E.1 — Honest framing

Android's playback stack is structurally different from Apple's. Apple has four physically distinct player implementations selected per session. Android has one engine (Media3 ExoPlayer) with composable `MediaSource`, `Renderer`, and `AudioProcessor` modules. A literal 4-engine swap on Android would be cargo-culting.

**What we match:** the architecture of how playback decisions are made and surfaced — first-class `PlaybackRoute`, capability matrix with the fields Apple uses, `RouteSelector` invoked per session, HUD that surfaces chosen route + capabilities.

**What we don't match:** 4 separate `ExoPlayer` instances; a `DV loopback` route (Android decoders handle DV natively where the hardware supports it; the loopback solves an AVPlayer-only problem).

### E.2 — Route taxonomy

| Apple route | Android equivalent | Realisation |
|---|---|---|
| `playerCoreDirect` (default) | `Compatibility` | `ProgressiveMediaSource` + `DefaultRenderersFactory` with `EXTENSION_RENDERER_MODE_PREFER` (FFmpeg AAR engaged for TrueHD / EAC-3 JOC / AC-4) |
| `avPlayerNativeDirect` | `NativeDirect` | `ProgressiveMediaSource` + `EXTENSION_RENDERER_MODE_OFF` (platform decoders only) |
| `avPlayerHLS` | `Hls` | `HlsMediaSource` (when server delivers HLS or remux is requested) |
| `avPlayerLocalDVLoopback` | *N/A — explicitly not implemented* | Documented as "DV handled by platform decoders" |

A `Dash` route is reserved as a sealed-class hook with no implementation.

### E.3 — New / modified files

```
android-shared/src/androidMain/kotlin/com/continuum/app/common/player/
├── route/
│   ├── PlaybackRoute.kt           (new — sealed class: Compatibility | NativeDirect | Hls)
│   ├── RouteCapability.kt         (new — data class fields below)
│   ├── RouteCapabilityMatrix.kt   (new — map<PlaybackRoute, RouteCapability>)
│   ├── RouteSelector.kt           (new — selects route per FileVersion + device caps)
│   └── RouteResolution.kt         (new — output: route + reasons[] for HUD display)
├── audio/
│   └── DelayAudioProcessor.kt     (new — Media3 AudioProcessor; head delay)
├── ContinuumPlayerFactory.kt      (modified — accepts PlaybackRoute; builds MediaSource +
│                                              RenderersFactory + AudioProcessor chain)
└── ContinuumPlaybackService.kt    (modified — exposes current route in MediaSession metadata)
```

The pre-existing TODO at `androidTvApp/.../di/AndroidTvModule.kt:45` ("currently a duplicate of the phone module… extract to `:android-player` later") is resolved as a side-effect — the new factory takes a route parameter and is consumed identically by phone and TV.

### E.4 — `RouteCapability` fields

Mirrors `docs/tvos-player/05-route-capability-matrix.md` field-for-field so the HUD can render identical labels:

```kotlin
data class RouteCapability(
    val buffersReported: Boolean,
    val videoGravityToggle: Boolean,
    val hdrToggle: Boolean,
    val audioDelaySupported: Boolean,
    val subtitleDelaySupported: Boolean,
    val subtitleStyling: SubtitleStyling,  // None | SystemOnly | Full (Prairie-rendered)
    val supportsTunneling: Boolean,
    val supportsPassthrough: Boolean,
)
```

| Route | buffers | gravity | HDR toggle | audio delay | sub style | tunneling | passthrough |
|---|---|---|---|---|---|---|---|
| `Compatibility` | ✓ | ✓ | ✓ | ✓ | Full | ✓ | ✓ |
| `NativeDirect` | ✓ | ✓ | ✓ | ✓ | Full | ✓ | ✓ |
| `Hls` | ✓ | ✓ | ✗ (server-baked) | ✓ | SystemOnly | ✗ | depends |

`Compatibility` and `NativeDirect` look near-identical in capabilities; the differentiator is **codec breadth** (FFmpeg AAR on/off). Recorded via separate `supportedAudioCodecs: Set<Codec>` used by the selector, not exposed in the HUD.

### E.5 — `RouteSelector` logic

```
input:  FileVersion (codec, container, HDR profile, audio codecs),
        DeviceCapabilities (probed at startup),
        UserPreference (Auto | ForceCompatibility | ForceNativeDirect | ForceHls)
output: RouteResolution(route, reasons: List<String>)
```

Decision tree:

1. `UserPreference != Auto` → return forced route with reason `"user override"`.
2. `container == HLS` or server returned a manifest URL → `Hls`.
3. `container in {MP4, MOV, M4V}` AND every audio track is platform-natively decodable on this device → `NativeDirect`.
4. Else → `Compatibility`.

`reasons[]` is surfaced verbatim in the HUD Stats pane (e.g., `"Route: Compatibility — reason: container=MKV; reason: TrueHD requires FFmpeg extension"`). Matches Apple's validation-record style.

### E.6 — Audio delay (`DelayAudioProcessor`)

Resolves Apple's "audio delay unsupported" TODO and ports the capability to Android.

- Implements `androidx.media3.common.audio.AudioProcessor`.
- Single configurable parameter: `delayMs: Int` (range −500 to +500).
- Positive delay: prepends `delayMs * sampleRate / 1000` frames of silence at stream head; subsequent buffers pass through.
- Negative delay: drops `|delayMs| * sampleRate / 1000` head frames.
- Hot-reconfigured via `setDelayMs(int)`; pending change applied on next `flush()` (call from `Player.seekTo(currentPosition)` to re-engage seamlessly).
- Wired via `DefaultAudioSink.Builder.setAudioProcessorChain(DefaultAudioProcessorChain(DelayAudioProcessor()))`.

UI: the HUD `AudioPane` (built in A.3) already provisioned the disabled delay slider. E unhides it; persisted to per-profile `TvPreferences` (key `audio_delay_ms`).

### E.7 — Route persistence & override

- New per-profile preference `playback_route_override: String` (`auto` / `compatibility` / `native_direct` / `hls`), default `auto`. Surfaced in `TvSettingsScreen`'s Playback section.
- Per-session route choice logged via existing `PlaybackAnalyticsListener` (`android-shared`) so we can audit selector behavior on real devices.

### E.8 — Capability detection coupling

Existing `PlaybackCapabilityDetector` and `AudioCapabilityManager` (in `shared`) already expose device-level facts. The `RouteSelector` consumes these — no new probing. The HUD `Stats` pane reads `PlaybackCapabilityDetector` directly (already injected in `TvPlayerScreen` per A.3).

### E.9 — Testing

- `RouteSelectorTest` (in `android-shared/src/androidUnitTest`) — pure logic, no Android deps. Table-driven: `(FileVersion, DeviceCapabilities, UserPreference) → expected RouteResolution`. Covers every branch + every reason string.
- `DelayAudioProcessorTest` — positive delay prepends correct frame count; negative trims; zero is no-op; reconfiguration non-destructive across `flush()`.
- `RouteCapabilityMatrixTest` — single test asserting every `PlaybackRoute` sealed-class variant is keyed in the matrix.
- `ContinuumPlayerFactoryTest` — assert a given route produces the expected `MediaSource` class + `RenderersFactory` configuration. Light, no actual playback.
- Live playback: validation matrix on real Android TV hardware. New doc: `docs/media3/09-android-route-validation-template.md` (mirrors Apple's `docs/tvos-player/06-validation-record-template.md` in Android terms).

### E.10 — Risks

- **Regression risk on the existing single-engine path is real.** Today every TV playback goes through one `ExoPlayer` configuration. After E lands, the `Compatibility` route should be bit-identical to today's behavior on a typical MKV — verify by playing the same file pre/post and diffing playback analytics. Rollback is `playback_route_override = compatibility` everywhere.
- **Audio delay across `seekTo` discontinuities** — `DelayAudioProcessor.flush()` is invoked on seek; the prepended silence must re-apply or the user perceives the delay vanishing after every seek. The test for this is the most important one in E.
- **HLS route conditional on backend** — if Prairie doesn't serve HLS today, the `Hls` route is wired but never selected. Acceptable; route's presence costs nothing.

### E.11 — Explicit non-implementation

- No `DV loopback` route.
- No new player engine (single Media3 instance throughout, configured per route).
- No phone-specific UI or settings (player factory lives in `android-shared`, so phone inherits the architecture; no phone surface added).
- No `MediaSessionService` swap.

---

## Cross-cutting integration notes

- **A.3 ↔ E:** HUD `AudioPane` ships with delay slider disabled; E enables. HUD `Video` / `Stats` panes ship showing "Single engine"; E populates with route + reasons.
- **C ↔ phone:** `DeviceLoginRepository` lives in `shared/commonMain`, so phone gets device login for free even though phone UI is out of scope here.
- **Dependencies driving the impl order:** B reads the active `ProfileKey` flow defined in D. E.6 + E.7 persist into per-profile DataStore defined in D. Hence **D precedes B and E** in the implementation order.

Implementation order (revised post-audit): **A.7 placeholder tokens → A.1 polish → A.2 finish → A.6 finish → A.5 (after decision) → A.3 → A.4 → D → B → C → E.**

## Testing summary

Per `AGENTS.md`, no tests for restyling. Focused tests for non-trivial logic:

- A.3: `TvPlayerHudController` (tab nav, auto-hide, exit-cycle).
- B: `WatchNextRepository`, `WatchNextProgramMapper`, `WatchNextSyncWorker`.
- C: `DeviceLoginRepository` (shared/commonTest), `TvLoginViewModel`.
- D: `TvPreferencesStore`, `TvPreferencesMigration`, `TvPreferencesProvider`.
- E: `RouteSelector`, `DelayAudioProcessor`, `RouteCapabilityMatrix`, `ContinuumPlayerFactory`.

Manual e2e on real Android TV hardware for: deep-link routing, live Watch Next worker → ContentProvider, QR rendering, player route validation matrix (new doc `docs/media3/09-android-route-validation-template.md`).

## New dependencies

| Dep | Where | Purpose |
|---|---|---|
| `androidx.palette:palette` | `androidTvApp/build.gradle.kts` | Color extraction for A.2 / A.6 |
| `androidx.tvprovider:tvprovider` | `androidTvApp/build.gradle.kts` | Watch Next ContentProvider |
| `androidx.work:work-runtime-ktx` | `androidTvApp/build.gradle.kts` (if not transitive) | WorkManager |
| `io.insert-koin:koin-androidx-workmanager:4.1.0` | `androidTvApp/build.gradle.kts` | Worker DI |
| `com.google.zxing:core:3.5.3` | `androidTvApp/build.gradle.kts` | QR code rendering |

All additions go into `gradle/libs.versions.toml` first.

## What this design does NOT cover

- Phone-app UI changes.
- iOS / tvOS code changes.
- Backend / server changes (verification points in C may surface a need to confirm endpoint shape; this is read, not edit).
- Performance optimization or memory tuning unrelated to the rework.
- Localization audit.
- Telemetry / analytics schema changes.

Each of those, if needed, is its own follow-up.
