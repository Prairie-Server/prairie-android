# Audiobook Player Redesign (Premium) — Design

**Status:** Approved design (brainstorming output). Implementation plan to follow via writing-plans.
**Scope:** Android **phone** (`androidApp`) **and Android TV** (`androidTvApp`). All non-UI audiobook logic lives in `shared` (pure chapter math, commonMain) and `android-shared` (the player ViewModel, settings store, playback service) so both apps reuse it; only the Compose screens differ — phone touch UI vs. TV 10-foot D-pad UI. Paths are repository-relative; assume the repository root (`silo-android`) is the cwd.
**Server changes:** None required for the audiobook player. All work is client-side.

---

## 1. Goal

Turn the audiobook player from a thin, single-column screen into a premium listening experience **on both phone and Android TV**: chapter-aware playback and navigation, smarter sleep, configurable skip/speed, listening-quality processing (skip silence, volume normalization/boost), Android Auto, a phone home-screen widget, and a richer media notification. The TV app gains a first-class audiobook player it does not have today.

## 2. Problems with the current player

Current implementation: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerScreen.kt` + `AudiobookPlayerViewModel.kt`. Playback runs in the shared `android-shared/.../player/ContinuumPlaybackService` (a Media3 `MediaSessionService`) and is driven through a `MediaController`. Chapters come from the server as `VersionChapter` (with start times) on the selected version.

- The **chapter list is rendered inline, always expanded** (`AudiobookPlayerScreen.kt:370-406`) inside the page's vertical scroll, so the controls sit on top of a long always-open list (e.g. 111 chapters).
- **No chapter awareness**: no "current chapter" display, no chapter progress, no prev/next-chapter navigation.
- **Sleep timer** offers only fixed durations — no "end of chapter".
- **Skip is fixed at ±30s**; no configurable interval; speed is a cycle, not presets.
- **No listening-quality controls**: no skip-silence, no volume normalization/boost.
- **No Android Auto, no widget**, and the media notification is the Media3 default.

## 3. Target UX

Chapters move off the main screen into a sheet. The main screen becomes chapter-aware:

```
┌──────────────────────────────┐
│ ‹                  ⠇ bookmarks │
│        ╭──────────────╮        │
│        │    cover     │        │   hero cover (existing ThumbhashImage)
│        ╰──────────────╯        │
│        Title · Author          │
│        Narrated by …           │
│ ──── Chapter 7 of 111 ────     │   NEW current-chapter header
│ ▓▓▓▓▓░░░░░░  (chapter / book)  │   NEW progress bar w/ book↔chapter toggle
│   12:04 ───────────── 38:20    │
│  ⤓ch   ⟲15   ▶ ❚❚   15⟳   ch⤒  │   prev-ch · skip-back · play · skip-fwd · next-ch
│   1.5×      💤 Sleep     ☰ Ch   │   secondary row → sheets
└──────────────────────────────┘
```

## 4. Architecture & components

### 4.0 Shared engine vs. per-platform UI

The redesign is structured so phone and TV share everything except the screen:

- **`shared` (commonMain, pure Kotlin):** `AudiobookChapters` — pure chapter math (current index from position + start times, chapter progress, prev/next target incl. the "restart current chapter if >3s in" rule). Platform-agnostic and unit-tested in `commonTest`; reusable by the Apple clients later.
- **`android-shared` (androidMain):** the `AudiobookPlayerViewModel` (currently in `androidApp`) **moves here** so both apps instantiate it; plus `AudiobookSettingsStore` (DataStore) and the audio processing in `ContinuumPlaybackService`. The VM owns Media3 `MediaController` interaction, chapter state (via `AudiobookChapters`), sleep, skip, and speed.
- **`androidApp`:** the phone Compose screen (touch).
- **`androidTvApp`:** the TV Compose screen (10-foot, D-pad focus) — new.

Only the two screens are platform-specific; chapter logic, settings, sleep/skip/speed behavior, audio processing, the notification, Android Auto, and the session all live in shared modules and behave identically on both apps.

### 4.1 Phone UI decomposition (replaces the monolith screen)

The single `Column` in `androidApp/.../ui/screens/audiobook/AudiobookPlayerScreen.kt` is split into focused composables under that package:

- `AudiobookCoverHeader` — cover, title, author, narrator.
- `ChapterProgressBar` — shows chapter-relative or book-relative progress with a toggle; reflects the seek slider.
- `AudiobookTransport` — five controls: prev-chapter, skip-back, play/pause, skip-forward, next-chapter.
- `AudiobookSecondaryBar` — speed chip, sleep chip, chapters button (and bookmarks stays top-right).
- `ChaptersSheet` — bottom sheet; lazy list; highlights the current chapter and auto-scrolls to it on open. Replaces the inline list.
- `SpeedSheet` — speed presets (0.5×–3.0×) plus fine adjust. (Extends existing `AudiobookSpeedSheet`.)
- `SleepTimerSheet` — adds **End of chapter** and **End of book** alongside fixed durations. (Extends existing `AudiobookSleepTimerSheet`.)
- `SkipIntervalSheet` — choose skip-back/forward interval (10/15/30/60s).

### 4.2 ViewModel (`AudiobookPlayerViewModel`, relocated to `android-shared`)

The VM moves from `androidApp` into `android-shared/.../player/` so both apps share it (the phone screen's import path updates; no behavior change in that move). New responsibilities, all derived from Media3 truth (position polling already exists):

- **Chapter computation**: delegates to `shared`'s pure `AudiobookChapters` — given `positionSeconds` and chapter start times, derive `currentChapterIndex`, `chapterProgress`, and `chapterCountLabel`.
- **Chapter navigation**: `skipToPreviousChapter()` / `skipToNextChapter()` (prev re-starts current chapter if >3s in, else goes to the previous — standard audiobook behavior, computed in `AudiobookChapters`).
- **End-of-chapter sleep**: a watcher that, when the sleep choice is "end of chapter", pauses when the position crosses the current chapter's end.
- **Settings exposure**: skip interval and default speed read from a new settings store (below).

### 4.3 Settings (`AudiobookSettingsStore`)

New store in `android-shared/.../player/` (DataStore-backed, mirroring the existing player settings pattern) holding: `skipBackSeconds`, `skipForwardSeconds`, `defaultSpeed`, `skipSilenceEnabled`, `volumeNormalizationEnabled`, `volumeBoostDb`. Exposed as flows; the screen and service both observe it.

### 4.4 Playback service & audio processing

`ContinuumPlaybackService` (shared with video) gains audiobook-only audio processing, applied when the active item is an audiobook:

- **Skip silence**: `ExoPlayer.setSkipSilenceEnabled(true)` toggled from the setting.
- **Volume normalization / boost**: a `LoudnessEnhancer` attached to the player's audio session id (targetGain in mB), driven by the normalization/boost settings. No extra permissions.

Plumbing: settings flows are observed in the service (or pushed via custom `MediaSession` commands) so changes apply live without recreating the player.

### 4.5 Chapter metadata in the session

The active `MediaItem`'s `MediaMetadata` carries chapter info so the **notification**, **lock screen**, **widget**, and **Android Auto** can all show "Chapter N — title" and offer skip-chapter actions. Custom session commands: `SKIP_TO_PREV_CHAPTER`, `SKIP_TO_NEXT_CHAPTER`.

### 4.6 Android Auto (largest risk)

Convert the shared `ContinuumPlaybackService` from `MediaSessionService` to a Media3 **`MediaLibraryService`** and expose a browsable content tree:

- Root → `Continue Listening`, `Audiobook Libraries`, and (when playing) `Chapters`.
- `onGetLibraryRoot` / `onGetChildren` / `onGetItem` populated from the catalog + the current item's chapters.

> ⚠️ **This service is shared with video playback.** The refactor must preserve all existing video behavior (lock-screen controls, headset buttons, Watch Together, the stop-on-exit teardown in `PlayerScreen`). This is the single biggest risk in the spec and gets its own verification pass. `MediaLibraryService` is a superset of `MediaSessionService`, so video continues to use the session API unchanged; only the browse tree is additive.

### 4.7 Home-screen widget

A Glance `AudiobookWidget` showing cover, title, current chapter, and play/pause + skip actions, bound to the media session's playback state. Tapping opens the player.

### 4.8 Rich media notification

Use the session's chapter metadata to show the chapter title and add skip-chapter custom actions + artwork to the Media3-provided notification. Applies to phone and TV (same session).

### 4.9 TV audiobook player (`androidTvApp`) — new

The TV app has no audiobook player today. Add a 10-foot, D-pad-navigable `TvAudiobookPlayerScreen` + a thin TV view glue that consumes the **same** `AudiobookPlayerViewModel` from `android-shared`. Follow the existing TV player patterns (`androidTvApp/.../ui/screens/player/TvPlayerScreen.kt`, `TvPlayerHud.kt`, `TvPlayerScrubber.kt`) for focus handling and the HUD style:

- Layout: cover + metadata, current-chapter header, chapter progress, a D-pad transport row (prev-ch / skip-back / play-pause / skip-fwd / next-ch), and chapters/speed/sleep as focusable side panels or overlays (not phone bottom sheets).
- **Entry point**: the TV detail screen (`TvItemDetailScreen` / `TvItemDetailViewModel`) must route a Play action for audiobook-type items to `TvAudiobookPlayerScreen` (audiobooks already surface in TV libraries because `MediaMode.tvModes()` permits Audio). Add the nav destination to the TV shell.
- Reuses chapter logic, settings, sleep/skip/speed, audio processing, notification, and Android Auto unchanged — only the Compose UI and focus model are TV-specific.

## 5. Data flow

Server `VersionChapter[]` → VM chapter model → (a) UI current-chapter/progress, (b) `MediaMetadata` on the session → notification / lock screen / widget / Android Auto. Position polling (existing 4 Hz) feeds chapter computation and the end-of-chapter sleep watcher. Settings store → screen (UI) + service (audio processing).

## 6. Phasing

1. **Shared engine** — add pure `AudiobookChapters` to `shared` (with `commonTest`); relocate `AudiobookPlayerViewModel` from `androidApp` to `android-shared` and point the existing phone screen at it. No UI change yet — pure refactor + new chapter math, both apps now able to consume the VM.
2. **Phone player redesign** — decompose the phone screen, chapters-as-sheet (current-chapter highlight + auto-scroll), current-chapter header, chapter progress bar, prev/next-chapter transport.
3. **TV player** — new `TvAudiobookPlayerScreen` (D-pad) consuming the shared VM, plus the TV detail/library Play entry point and nav destination.
4. **Sleep + skip + speed** — `AudiobookSettingsStore` + end-of-chapter/end-of-book sleep, configurable skip interval, speed presets; wired into both phone and TV UIs.
5. **Listening quality** — skip-silence + volume normalization/boost through the service (benefits both apps automatically).
6. **Rich notification** — chapter title + skip-chapter actions + artwork.
7. **Android Auto** — `MediaLibraryService` refactor + browse tree (video-regression verification pass).
8. **Phone widget** — Glance now-playing widget (phone only).

Each phase is independently shippable. Phase 1 unblocks both UIs; Phases 2–4 deliver most of the perceived value on each platform.

## 7. Testing

- **Unit (commonTest/androidUnitTest):** chapter computation (boundaries, single-chapter degrade, position exactly on a boundary), prev-chapter "restart if >3s" rule, end-of-chapter sleep trigger, settings store round-trips.
- **Manual on-device (Pixel):** chapters sheet open/close + auto-scroll, prev/next chapter, end-of-chapter sleep, skip-interval changes, skip-silence + boost audible effect, notification/lock-screen chapter + actions, Android Auto via Desktop Head Unit, widget actions.
- **Regression:** full video playback pass after the `MediaLibraryService` refactor (Phase 5).

## 8. Assumptions & out of scope

- **Assumptions:** chapters arrive from the server (`VersionChapter` with start times); a book without chapters degrades to a single chapter and hides chapter-only chrome. Phone + Android TV scope; shared logic in `shared`/`android-shared` so Apple can adopt later. Client-only — no server changes.
- **Out of scope:** the **phone home-screen widget is phone-only** (Android TV has no equivalent home widget; a TV recommendation-channel row is a separate future effort). Cross-device "current chapter" sync beyond existing progress sync, and CarPlay/Apple (separate repos). Reading/ebooks on TV remain excluded (`MediaMode.tvModes()` filters Reading).
