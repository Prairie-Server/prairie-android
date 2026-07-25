# Android TV Detail Parity (match prairie-apple tvOS) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Rebuild the prairie-android `androidTvApp` detail screen (movie / series / season / episode) to pixel-match the prairie-apple **tvOS** detail (`feature/playback-ux-redesign`).

**Architecture:** Mirror Apple's component decomposition in Compose-for-TV. Build the squared control kit + anchored selector popover first, then the hero, synopsis, selector wiring, and screen assembly. The **design spec is the source of exact values** — `docs/superpowers/specs/2026-06-17-android-tv-detail-parity-design.md` (cited per task as **SPEC §N**). The Apple Swift files (cited as **APPLE: …**) and the reference frames (`docs/superpowers/reference/tvos-detail/`) are the ground truth.

**Tech Stack:** Kotlin, Compose-for-TV, `ContinuumTvTheme` tokens (`Color`/`Type`/`Spacing`/`FocusModifier`), Coil, Koin, Media3 (unaffected).

**Per-task loop (every task):** implement → `./gradlew :androidTvApp:compileDebugKotlinAndroid` clean → run any unit tests → **Codex review of the diff** → commit (author `rxwatcher`, `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`, no push). Visual parity is verified on the **Android-TV emulator** against the reference frames (the physical Shield `screencap` returns black).

**Conventions:**
- Apple repo lives at `/Users/jimcole/projects/silo/prairie-apple` (branch `feature/playback-ux-redesign`; `cd iosApp && xcodegen generate` before building `PrairieTV`). Read the cited Swift file for exact pt/opacity/scale values; translate per SPEC "Architecture" (unitless 1:1; radii face value; sizes → existing Android TV token scale, tuned on emulator).
- Add a dedicated control radius token `TvControlCorner = 8.dp` (do NOT reuse `small = 12.dp`).
- Commit author: every commit uses `git -c user.name=rxwatcher commit …`.

---

### Task 1: Squared control kit

**Files:**
- Create: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/components/TvSquaredButtons.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/theme/Shape.kt`/`Theme.kt` (add `TvControlCorner = 8.dp`)
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvSeasonPicker.kt` (re-skin chips squared)

**Reference:** SPEC §2; APPLE: `tvOS/Screens/Detail/TVDetailActions.swift` (`TVPillButtonStyle`, `TVCircleButtonStyle`, `TVPrimaryPillButton`, `TVSecondaryPillButton`, `TVCircleActionButton`). Frames: `movie-detail-rest.png`, `movie-detail-toggle-favorite-focus.png`, `movie-detail-toggle-watched-focus.png`.

- [ ] **Step 1: Add the `TvControlCorner = 8.dp` radius token.**
- [ ] **Step 2: Implement `TvPrimaryPillButton`, `TvSecondaryPillButton`, `TvSquareToggleButton`** with the exact `.compact` focus treatment and per-kind fills/borders from SPEC §2 (primary 54h/26v, secondary 40h/22v, toggle 72×72; compact ring 2.5dp inset 3, scale 1.025, shadow 0.14→0.24 r4→10 y2→4, glow onSurface@0.08 r6; toggle ring white@0.96 3dp inset −5 scale 1.10). Each owns its focus appearance via `interactionSource`/`onFocusChanged` (suppress the default TV focus halo, mirroring the custom `ButtonStyle` approach). Icon-only toggle uses 28sp icon + active/inactive icon swap.
- [ ] **Step 3: Re-skin `TvSeasonPicker` chips** to squared `TvControlCorner`, 22sp, idle outline white@0.25 1.5dp, focus white@0.18 + scale 1.04, selected white fill / black label, row spacing 14dp, keep auto-center.
- [ ] **Step 4: Compile.** `./gradlew :androidTvApp:compileDebugKotlinAndroid` → CLEAN.
- [ ] **Step 5: Codex review** of the diff (focus-treatment fidelity vs `TVPillButtonStyle`/`TVCircleButtonStyle`; suppression of the default halo). Fix findings.
- [ ] **Step 6: Commit** — `Add squared TV control kit (pills + 72dp toggle) matching tvOS`.

---

### Task 2: Anchored selector popover shell

**Files:**
- Create: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/components/TvAnchoredSelectorMenu.kt`

**Reference:** SPEC §6 (picker mechanism); APPLE: `TVPlaybackSelectorRow.swift` (`TVSelectorButton` uses SwiftUI `Menu` → anchored). Frames: `selector-audio-open.png`, `selector-subtitles-open.png`, `selector-version-open.png`.

- [ ] **Step 1: Implement `TvAnchoredSelectorMenu`** — a composable that renders a trigger (the secondary `.compact` squared selector pill from Task 1, label layout per SPEC §6: icon 22 / LABEL 18 bold tracking 1.0 @0.6 / value 22 / chevron.down 15 @0.6) and, on OK, opens a Compose `DropdownMenu`/`Popup` **anchored under the trigger** (not a centered modal). Rows are `[checkmark?] "Title — Detail"`; selecting invokes a callback and dismisses. On dismiss, **restore focus to the trigger**.
- [ ] **Step 2: Provide a stateless API:** `data class TvSelectorOption(val title: String, val detail: String, val selected: Boolean, val onSelect: () -> Unit)` + `TvAnchoredSelectorMenu(icon, label, value, options)`.
- [ ] **Step 3: Compile** → CLEAN.
- [ ] **Step 4: Codex review** (anchoring vs centered modal; focus capture/restore; d-pad within the menu). Fix.
- [ ] **Step 5: Commit** — `Add anchored selector popover for TV detail`.

---

### Task 3: Playback formatting + tests (pure)

**Files:**
- Create: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvPlaybackFormatting.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/detail/TvPlaybackFormattingTest.kt`

**Reference:** SPEC §6; APPLE: `Screens/Detail/DetailPlaybackFormatting.swift` + `PlaybackEditions.swift`. Mirror its label/badge/option logic against the Android `FileVersion` / track models (read the actual Android model field names first; the Apple field names are a guide, not literal).

- [ ] **Step 1: Write failing tests** capturing the value labels (use the real Android `FileVersion`/track types):

```kotlin
package com.continuum.app.tv.ui.screens.detail

import kotlin.test.Test
import kotlin.test.assertEquals

class TvPlaybackFormattingTest {
    @Test fun versionShortLabel_4kHdr() {
        val v = fileVersion(resolution = "2160p", hdr = true)
        assertEquals("4K · HDR", TvPlaybackFormatting.versionShortLabel(v))
    }
    @Test fun versionShortLabel_1080() {
        assertEquals("1080P", TvPlaybackFormatting.versionShortLabel(fileVersion(resolution = "1080p", hdr = false)))
    }
    @Test fun versionShortLabel_nullIsAuto() {
        assertEquals("Auto", TvPlaybackFormatting.versionShortLabel(null))
    }
    @Test fun audioValueLabel_codecLayoutLanguage() {
        val v = fileVersion(audio = listOf(audioTrack(codec = "EAC3", layout = "5.1", lang = "English", default = true)))
        assertEquals("EAC3 5.1 - English", TvPlaybackFormatting.audioValueLabel(v, selectedAudioTrackIndex = null))
    }
    @Test fun subtitleValueLabel_offForMinusOne() {
        assertEquals("Off", TvPlaybackFormatting.subtitleValueLabel(fileVersion(), selectedSubtitleTrackIndex = -1))
    }
    @Test fun subtitleValueLabel_autoForNull() {
        assertEquals("Auto", TvPlaybackFormatting.subtitleValueLabel(fileVersion(), selectedSubtitleTrackIndex = null))
    }
    // fileVersion()/audioTrack() = local builders matching the real Android model constructors.
}
```

- [ ] **Step 2: Run** `./gradlew :androidTvApp:testDebugUnitTest --tests "com.continuum.app.tv.ui.screens.detail.TvPlaybackFormattingTest"` → FAIL (unresolved `TvPlaybackFormatting`).
- [ ] **Step 3: Implement `TvPlaybackFormatting`** — `versionShortLabel`, `versionDetailLabel`, `audioValueLabel`, `audioOptions`, `subtitleValueLabel`, `subtitleOptions` (+ `Forced`/`HI`/`Auto`/`Off` strings), `currentEdition`/`editions` — mirroring `DetailPlaybackFormatting.swift`. (Edition returns ≤1 group until the Android model exposes edition data — see SPEC §6/§11.)
- [ ] **Step 4: Run tests** → PASS.
- [ ] **Step 5: Codex review** (label parity vs the Swift source; null/empty handling; track-index semantics). Fix.
- [ ] **Step 6: Commit** — `Add TvPlaybackFormatting (selector labels) + tests`.

---

### Task 4: Inline selector row + retire the old picker

**Files:**
- Create: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvPlaybackSelectorRow.kt`
- Modify: `TvItemDetailScreen.kt` (wire in; remove the version pill + the Audio/Subtitles "More"-menu entries)
- Delete: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvVersionPicker.kt`

**Reference:** SPEC §6; APPLE: `TVPlaybackSelectorRow.swift`, `TVMovieDetailView.actionColumn`.

- [ ] **Step 1: Implement `TvPlaybackSelectorRow`** — `HStack`(spacing 28) of Edition(if `editions>1`)/Version/Audio/Subtitles, each a `TvAnchoredSelectorMenu` (Task 2) fed by `TvPlaybackFormatting` (Task 3) values + options; the row is `fillMaxWidth(Start)` + a full-width focus container. Hidden until a current version resolves; series/season show a placeholder while next-up loads.
- [ ] **Step 2: Wire** to the existing VM handlers (`onSelectVersion(Int?)`, `onSelectAudioTrack(Int?)`, `onSelectSubtitleTrack(Int?)`; `nil`=Auto, `-1`=subs off) — preserve current Auto + track-index semantics.
- [ ] **Step 3: Resolve the series/season next-up dependency** — feed the selector from the next-up episode's playback detail; if not yet exposed in detail VM state, add the minimal resolve (or render the placeholder until available). SPEC §6.
- [ ] **Step 4: Delete `TvVersionPicker`** and remove the Audio/Subtitles items from the old More menu.
- [ ] **Step 5: Compile** → CLEAN.
- [ ] **Step 6: Codex review** (selector appear/hide rules; Auto/-1 semantics; next-up wiring; no dangling refs to the deleted picker). Fix.
- [ ] **Step 7: Commit** — `Add inline TV playback selector row; retire version pill`.

---

### Task 5: Hero rebuild

**Files:**
- Modify (rebuild): `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvDetailHero.kt`

**Reference:** SPEC §3; APPLE: `TVDetailHero.swift` (heroHeight 980, scrim stops, `TVHeroTitle`/`TVEpisodeHierarchyTitle`, `TVHeroEyebrow`, source/facts rows, starring overlay, `TVHeroMetadata`). Frames: `movie-detail-rest.png`, `series-detail.png`.

- [ ] **Step 1: Rebuild the hero** per SPEC §3: backdrop + left scrim (`0.92/0.70/0.35/clear` @ `0/0.22/0.55/0.88`) + bottom fade (`clear/clear/bg@0.55/bg` @ `0/0.55/0.85/1.0`); height = **0.907 × viewport height**; content column (maxWidth 1200) = eyebrow Capsule → title (compressed-heavy, split-on-colon; episode hierarchy variant) → source row → synopsis slot → facts row; action cluster slot (`fillMaxWidth(Start)` focus section). Starring overlay trailing at `heroHeight × 0.45`.
- [ ] **Step 2: Port `TVHeroMetadata`** value builders (source tokens, facts line, quality chips `4K/HD/SD`+`DOLBY VISION/HDR`+`ATMOS/7.1/5.1`+`CC`, eyebrow, starring) — reuse `TvPlaybackFormatting`/existing `TvDetailMetadata` where present.
- [ ] **Step 3: Compile** → CLEAN.
- [ ] **Step 4: Codex review** (scrim/spacing/type fidelity; viewport-relative height; episode vs movie/series title variants). Fix.
- [ ] **Step 5: Commit** — `Rebuild TV detail hero to match tvOS`.

---

### Task 6: Expandable synopsis

**Files:**
- Create: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvExpandableSynopsis.kt`
- Modify: `TvDetailHero.kt` (use it in the synopsis slot); `TvItemDetailScreen.kt` (remove the standalone "About" overview; keep the Details facts table)

**Reference:** SPEC §5; APPLE: `TVExpandableSynopsis.swift`.

- [ ] **Step 1: Implement `TvExpandableSynopsis(overview, tagline)`** — clamp 3 lines (26sp @0.82 lineSpacing 8, maxWidth 1200, padding 20h/14v); focusable leaf with `onSurface@0.55` rounded-8 fill on focus; OK toggles `expanded` → unclamp + show serif-italic tagline (28sp @0.85); `animateContentSize` 120ms; reduce-motion aware.
- [ ] **Step 2: Wire** into the hero; remove the body "About" overview block (Details table stays).
- [ ] **Step 3: Compile** → CLEAN.
- [ ] **Step 4: Codex review** (focus leaf not stealing default focus; expand animation; About removal leaves no dead focusable). Fix.
- [ ] **Step 5: Commit** — `Add expandable hero synopsis (TV)`.

---

### Task 7: Episode rail re-skin + OK→detail

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvDetailEpisodeRail.kt`
- Modify: `TvItemDetailScreen.kt` (episode `onSelect` → navigate to episode detail, not play)

**Reference:** SPEC §7; APPLE: `TVEpisodeRail.swift`.

- [ ] **Step 1: Re-skin the episode card** to the exact metrics (card 460, still 260 r10, spacing 36, vertical 32; episode-number 18sp bold tracking 2 @0.55; "NOW VIEWING" 14sp heavy on white capsule; title 26sp 2 lines; overview 20sp 3 lines reservesSpace; focus scale 1.04 + white 3dp ring + shadow; watched badge; progress bar h5). Keep current-episode center + default focus.
- [ ] **Step 2: Change OK** so an episode card navigates to the episode **detail** page (mirror Apple's `onSelect → onEpisodeTap`), not direct play.
- [ ] **Step 3: Compile** → CLEAN.
- [ ] **Step 4: Codex review** (metric fidelity; OK-navigates-to-detail; current-episode focus/centering). Fix.
- [ ] **Step 5: Commit** — `Re-skin TV episode rail; OK opens episode detail`.

---

### Task 8: Screen assembly, action inventory & focus rewire

**Files:**
- Modify (rebuild): `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvItemDetailScreen.kt`

**Reference:** SPEC §1 + §4; APPLE: `TVMovieDetailView.swift` (+ `TVSeriesDetailView.swift`, `TVSeasonDetailView.swift` for series/season body order).

- [ ] **Step 1: Action row** (`HStack spacing 36`): Play/Resume (Task 1 primary) + Start-Over (secondary, if resumable) + Favorite/Watchlist/Watched (Task 1 squared toggles) + **More ⋯ only on episode pages** (Go to Season / Go to Series). **Remove Rate + Watch-Together** (SPEC §1 / §11). **Wire the Watched toggle** to `onToggleWatched` (currently inert).
- [ ] **Step 2: Action cluster** = action row + `TvPlaybackSelectorRow` (Task 4) inside the hero's action slot.
- [ ] **Step 3: Body** (`VStack spacing 72`, horizontal `safeArea`, bottom 160; hero→body spacing 48): episodes (episode page: header + season chips if >1 + rail; series/season per their Apple views) → cast → details → similar (similar hidden on episode pages). Section headers per SPEC §8.
- [ ] **Step 4: Focus rewire** — default focus Play; action + selector clusters as full-width focus sections with explicit `focusProperties` for Down-from-far-right-toggle and Up-into-cluster; per-section `focusGroup()` + user-initiated default focus on current episode / selected season / first cast; **drop the macro `focusableDetailSection` container**.
- [ ] **Step 5: Compile** → CLEAN.
- [ ] **Step 6: Codex review** (action inventory exactly matches Apple; Rate/WatchTogether gone; Watched wired; focus flow; body order/spacing). Fix.
- [ ] **Step 7: Commit** — `Assemble TV detail screen to match tvOS (action inventory + focus)`.

---

### Task 9: Emulator visual tuning

**Files:** any of the above, as tuning requires.

**Reference:** all reference frames in `docs/superpowers/reference/tvos-detail/`.

- [ ] **Step 1: Run on the Android-TV emulator** (`screencap` works there). Navigate a movie detail and a multi-season series detail.
- [ ] **Step 2: Compare side-by-side** with the reference frames: at rest, each toggle focus, each selector popover (anchored), expanded synopsis, season chips, episode + cast rails. Capture emulator screenshots.
- [ ] **Step 3: Tune** hero height fraction, spacing, type weights/sizes, scrim, focus scales/rings until they match the frames.
- [ ] **Step 4: Codex review** of the tuning diff. Fix.
- [ ] **Step 5: Commit** — `Tune TV detail metrics to match tvOS reference frames`.

---

## Self-review

- **Spec coverage:** §1 action row → T8/T1; §2 control kit → T1; §3 hero → T5; §4 body/focus → T8; §5 synopsis → T6; §6 selector row → T2/T3/T4; §7 episode rail → T7; §8 cast/headers/details → T5/T8 (re-skin existing); §9 decomposition → T1–T8; §10 testing → per-task + T9; §11 divergences (Rate/WatchTogether removed → T8; next-up dep → T4; Edition deferred → T3; fonts/hero → T5/T9). ✓ All covered.
- **Placeholders:** UI tasks reference SPEC §N for the exhaustive value tables (DRY — the spec is the single source of exact metrics) rather than duplicating hundreds of Compose lines; the only literal code block (Task 3 tests) is real. Acceptance per task = compile clean + Codex + (T9) frame match.
- **Type consistency:** `TvPlaybackFormatting` (T3) consumed by `TvPlaybackSelectorRow` (T4); `TvAnchoredSelectorMenu` (T2) consumed by T4; squared buttons (T1) consumed by T5/T8; names consistent across tasks.
