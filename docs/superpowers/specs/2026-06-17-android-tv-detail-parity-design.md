# Android TV Detail Screen — prairie-apple tvOS Parity ("Squared Skyline") — Design

> **For agentic workers:** Design spec. Implementation plan follows via the writing-plans skill.

**Goal:** Rebuild the prairie-android **TV** detail screen (`androidTvApp`; movie / series / season / episode) to **exactly** match the prairie-apple **tvOS** detail (`feature/playback-ux-redesign`). Match the layout, metrics, focus behavior, and action inventory of the Apple source — do not preserve Android-only deviations.

**Fidelity:** Pixel-faithful. Apple Swift source = exact values; the screenshots in `docs/superpowers/reference/tvos-detail/` = visual ground-truth. Tune on the Android-TV **emulator** (the Shield `screencap` returns black).

**Architecture:** Mirror Apple's component decomposition in Compose-for-TV. The Apple values below are authored in **pt on a 1920×1080 tvOS canvas**; Android TV renders in **dp** with `TvUiDensityScale = 0.86` + ~2× type tokens — so **unitless values (scales, opacities, gradient stops) port 1:1; radii port at face value (8pt→8dp); fonts/sizes are translated to the existing Android TV token scale and tuned on-emulator against the references.** Where Apple sizes a thing relative to the viewport (hero height), do the same in Android (fraction of screen), not a fixed dp.

**Scope:** Android **TV** detail only. Phone deferred. Sub-project #1 of the parity roadmap.

---

## 0. Apple source map (the contract)

Branch `feature/playback-ux-redesign` at `/Users/jimcole/projects/silo/prairie-apple`, all under `iosApp/iosApp/tvOS/Screens/Detail/`:
- `TVMovieDetailView.swift` — movie/episode page composition (action row, More menu, body order, focus).
- `TVSeriesDetailView.swift`, `TVSeasonDetailView.swift` — series/season page composition (read these for season-chip + episode-rail placement and the next-up-driven selector).
- `TVDetailHero.swift` — hero (heroHeight 980, scrim stops, editorial column, title treatments, eyebrow, source row, facts row, starring overlay, `TVHeroMetadata`).
- `TVDetailActions.swift` — `TVPrimaryPillButton`/`TVSecondaryPillButton`/`TVCircleActionButton`/`TVCircleMenuButton` + `TVPillButtonStyle`/`TVCircleButtonStyle`.
- `TVPlaybackSelectorRow.swift` + `…/Screens/Detail/DetailPlaybackFormatting.swift` + `PlaybackEditions.swift` — the selector row, the `Menu` picker, value/label/badge logic, edition grouping.
- `TVExpandableSynopsis.swift`, `TVEpisodeRail.swift`, `TVSeasonChip.swift`, `TVDetailCastRail.swift`, `TVSimilarRail.swift`, `TVSectionHeader.swift`, `TVDetailFactsSection.swift`.

> `Prairie.xcodeproj` is git-ignored + XcodeGen-generated from `iosApp/project.yml`. To rebuild/run: `cd iosApp && xcodegen generate`, then build `PrairieTV`.

**Android target** (`androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/`):
- Rebuild: `ui/screens/detail/{TvItemDetailScreen,TvDetailHero,TvSeasonPicker}.kt`.
- New: `ui/components/TvSquaredButtons.kt`, `ui/screens/detail/{TvPlaybackSelectorRow,TvExpandableSynopsis,TvPlaybackFormatting}.kt`, `ui/components/TvAnchoredSelectorMenu.kt`.
- Keep + re-skin: `TvDetailEpisodeRail.kt`, `TvCastCrewSection.kt`, `TvDetailFactsTable.kt`.
- **Retire:** `TvHeroActionPill.kt` (capsule pills) and `TvVersionPicker.kt` (standalone version pill).

---

## 1. Action row — match exactly

`HStack(spacing: 36)`, left→right (`TVMovieDetailView.actionRow`):
1. **Play / Resume** — `TVPrimaryPillButton`, icon `play.fill`. Label `"Play"`, or `"Resume H:MM:SS"` when `userData.positionSeconds > 30` and not within 5s of the end.
2. **Start Over** — `TVSecondaryPillButton`, icon `backward.end.fill` — **only when resumable**.
3. **Favorite** — `TVCircleActionButton` `heart` / `heart.fill`.
4. **Watchlist** — `bookmark` / `bookmark.fill`.
5. **Watched** — `checkmark.circle` / `checkmark.circle.fill`. **(Android's watched hero toggle is currently inert — wire it to `onToggleWatched`.)**
6. **More ⋯** — `TVCircleMenuButton` (`ellipsis`) **only on episode pages** (`type == "episode" && seriesId != nil`); menu items: *Go to Season* (`square.stack`, when seasonNumber>0) and *Go to Series* (`tv`).

> **Deliberate match-exactly consequence:** Apple's tvOS detail has **no Rate and no Watch-Together** buttons. To match exactly, **remove both from the Android TV detail action row.** (This intentionally diverges from the prior "TV mirrors Android mobile" directive, per the explicit "match tvOS exactly" instruction. Recorded as a known divergence — if these must stay reachable, that's a separate, later decision.)

One `focusGroup()` around the row.

---

## 2. Squared control kit (`TvSquaredButtons.kt`) — exact `TVPillButtonStyle`/`TVCircleButtonStyle`

All radii **`smallCornerRadius`** (Apple tvOS = 8pt → Android control radius **8.dp**; add a dedicated token, don't reuse `small = 12.dp`). All pills here use the **`.compact`** focus treatment (Apple uses `.compact` for the primary, secondary, *and* selector pills).

**Pill body** (`TVPillButtonBody`), per kind:

| | Primary | Secondary |
|---|---|---|
| Padding | 54h / 26v dp | 40h / 22v dp |
| Foreground | black | idle white → focus black |
| Fill | idle white@0.76 → focus white | idle black@0.52 → focus white |
| Inner border (color/width) | idle white@0.12 / 0.8dp → focus black@0.18 / 1.8dp | idle white@0.24 / 1.2dp → focus black@0.12 / 1.5dp |

**Compact focus treatment (all pills):** focus outline color white@0.94 (primary) / white@0.98 (secondary), **width 2.5dp, inset 3dp**, outline radius = 8+2 = **10.dp**. **Scale 1.025** (pressed ×0.98). Shadow black, opacity idle 0.14 → focus 0.24, radius idle 4 → focus 10, y idle 2 → focus 4. Focus glow onSurface@0.08, radius 6 (focus only). Animate with the spring token; press with fast easeOut. Disable the system focus halo (custom focus only).

**Square toggle** (`TVCircleButtonStyle`): **frame 72×72 dp**, background `RoundedRectangle(8.dp)` fill idle white@0.10 → focus white; foreground idle white → focus black; icon 28sp semibold with replace symbol transition. Focus ring white@0.96 3dp inset −5, **scale 1.10**, shadow black@0.34 r16 y6, glow onSurface@0.15 r10 (confirm the tail of `TVCircleButtonStyle` for exact ring/scale values when implementing).

**Season chips** (`TvSeasonPicker.kt` re-skin, `TVSeasonChip`): squared 8dp, 22sp; idle transparent + white@0.25 1.5dp outline; focus white@0.18 fill + scale 1.04; selected white fill / black label. Row spacing 14dp, auto-center selected.

Quality/maturity facts chips: see §3 (outlined squared, r4–r5).

Reference: `movie-detail-rest.png`, `movie-detail-toggle-*-focus.png`.

---

## 3. Hero (`TvDetailHero.kt` rebuild) — exact `TVDetailHero`

`ZStack(bottomLeading)` { backdrop, leftGradient, bottomFade, content } + trailing starring overlay; `height = heroHeight`, full width, clipped.

- **heroHeight = 980pt of 1080 → ≈ 0.907 × viewport height** (compute from screen height; *not* a fixed dp). `contentMaxWidth = 1200.dp`.
- **Backdrop:** full-bleed `CachedAsyncImage`-equivalent (Coil), fill, else `continuumSurface`.
- **Left gradient** (leading→trailing): `black 0.92 / 0.70 / 0.35 / clear` at `0.0 / 0.22 / 0.55 / 0.88`.
- **Bottom fade** (top→bottom): `clear / clear / background@0.55 / background` at `0.0 / 0.55 / 0.85 / 1.0`.
- **content** = `VStack(spacing 24)` { editorialColumn; actionCluster }, padding leading/trailing `safeArea`, bottom 120dp. **actionCluster** = `actions()` `.padding(top 8)` `.fillMaxWidth(alignment=Start)` as its **own focusSection** (§1 + §4 inside).
- **editorialColumn** = `VStack(spacing 24)`, maxWidth 1200, alignment Start:
  1. **Eyebrow** (optional, `TVHeroEyebrow`) — **Capsule** (not squared): 18sp semibold, tracking 1.2, white, padding 16h/8v, `black@0.55` fill + `white@0.18` 1dp stroke.
  2. **Title** (padding top 4 if eyebrow):
     - Movie/series (`TVHeroTitle`): split on `": " / " — " / " – " / " - "` → primary **92sp black, compressed width**, UPPERCASE, lineLimit 2, shadow black@0.55 r16 y4; subtitle **40sp heavy compressed**, tracking 1.5, white@0.95, shadow black@0.5 r10 y3.
     - Episode (`TVEpisodeHierarchyTitle`, `VStack spacing 10`): series 92sp black compressed UPPERCASE; episode 50sp heavy compressed white@0.94; subtitle 32sp heavy compressed tracking 1.2 white@0.82.
     - Logo (movie/series, if `logoUrl`): image fit, max 620×220, bottom-leading.
  3. **Source row** (`HStack spacing 14`): tokens 26sp medium white@0.92; "·" dividers 24sp semibold white@0.5; trailing **rating chip** 20sp heavy tracking 1.0 white, padding 12h/4v, `RoundedRectangle(5.dp)` stroke white@0.7 1.5dp, leading 4.
  4. **Expandable synopsis** — §5 (only if overview non-empty).
  5. **Facts row** (`HStack spacing 14`): `.text` 22sp medium white@0.88 with "·" (22sp semibold white@0.45) **between consecutive text tokens only**; `.rating` = `checkmark.circle.fill` 18sp `continuumSuccess@0.9` + 22sp text; `.chip` 16sp heavy tracking 1.0 white, padding 9h/4v, `RoundedRectangle(4.dp)` stroke white@0.65 1.2dp.
- **Quality chips** (`TVHeroMetadata.qualityTokens`): resolution `2160/4k→"4K"`, `1080/720→"HD"`, `480→"SD"`; HDR → `"DOLBY VISION"` (if any video track has dolbyVision) else `"HDR"`; audio → `ATMOS`/`7.1`/`5.1` (stereo/2.0 → none); subtitles present → `"CC"`. Source version = `userData.lastFileId` match else first.
- **Starring overlay** (trailing, `TVHeroMetadata.starringText` = "Starring " + first 3 cast names): 24sp regular white@0.8, trailing-aligned, lineLimit 2, maxWidth 460, shadow black@0.55 r6 y2, padding trailing `safeArea`, **bottom = heroHeight × 0.45**.

Reference: `movie-detail-rest.png`, `series-detail.png`.

---

## 4. Body composition & focus — exact `TVMovieDetailView` (+ series/season views)

- Outer `ScrollView(vertical)` → `VStack(spacing 48)` { hero; body }. Body = `VStack(spacing 72)`, padding horizontal `safeArea`, bottom 160dp.
- Body sections in order: **episodes** (episode page: `type=="episode" && episodes nonempty`; series/season pages place season-chips + episode-rail per `TVSeriesDetailView`/`TVSeasonDetailView`) → **cast** (`TVSectionHeader("Cast","& Crew")` + `TVDetailCastRail`) → **details** (`TVSectionHeader("Info","Details")` + facts table) → **similar** (`TVSectionHeader("Recommended","More Like This")` + `TVSimilarRail`; **hidden on episode pages**).
- Episodes section = `TVSectionHeader(eyebrow "Season N"/"This Season", "Episodes")` + `TVSeasonChipRow` (if `seasons.count > 1`) + `TVEpisodeRail`.
- **Focus model** (mirror Apple; in Compose-TV use `focusGroup()` + `FocusRequester` + `focusProperties`):
  - Whole detail = one focus scope; **default focus → Play** (user-initiated).
  - Action cluster + selector row are each `fillMaxWidth(Start)` **focusSection-equivalents** so Down from the far-right toggle lands on the nearest selector (use explicit `focusProperties`/`focusGroup` + a full-width focusable container; the bare Android `focusGroup()` is *not* a 1:1 `focusSection`, so add directional `focusProperties` for Down-from-far-right and Up-into-cluster).
  - Episode rail / cast rail / season chips / similar rail each `focusGroup()` + user-initiated default focus on the **current** episode / **selected** season / first card. Episode rail auto-centers the current card on first appearance.
  - Drop the macro `focusableDetailSection` container (Apple deleted `ReadableFocusSection`).
  - Watch for: Play-focus racing async series/season next-up load; the synopsis (a focusable leaf) stealing default focus by geometry — keep Play the explicit default; focus restore after the selector popover dismisses (return focus to the originating selector).

---

## 5. Expandable synopsis (`TvExpandableSynopsis.kt`) — exact `TVExpandableSynopsis`

Receives `overview` + `tagline`. Overview clamped to **3 lines**, 26sp white@0.82, lineSpacing 8, maxWidth 1200, padding 20h/14v. **Focusable leaf**: on focus a light `onSurface@0.55` `RoundedRectangle(8.dp)` fill appears (no border). **OK toggles `expanded`** → removes clamp and shows the **tagline** (28sp `Serif` italic white@0.85) above the overview. `animateContentSize` 120ms easeOut; respect reduce-motion. Only hero text focus stop; reached by **Up** from the action row. Folds in today's separate "About" overview (the key-value Details table stays in the body).

---

## 6. Inline selector row (`TvPlaybackSelectorRow.kt` + `TvPlaybackFormatting.kt` + `TvAnchoredSelectorMenu.kt`)

`HStack(spacing 28)` { editionSelector (if `editions.count>1`); versionSelector; audioSelector; subtitleSelector }, `fillMaxWidth(Start)` + focusSection-equivalent. Shown only when a current version is resolved; on a series/season page show a placeholder while the next-up episode's playback detail loads.

**Each selector** = `TvSelectorButton` = a **secondary `.compact` squared pill** that **opens an anchored dropdown** (Compose `DropdownMenu` / `Popup` anchored to the button — **NOT** the centered `TvOptionDialog`; Apple uses a SwiftUI `Menu`). Label layout (`HStack spacing 12`): icon 22sp semibold, `LABEL` UPPERCASE 18sp bold tracking 1.0 @0.6, `value` 22sp semibold lineLimit 1, `chevron.down` 15sp bold @0.6.

| Selector | Icon | `value` | Menu options (each row `"Title — Detail"`, checkmark on selected) |
|---|---|---|---|
| Edition (if >1) | `rectangle.stack` | `currentEdition.label` ?? "Standard" | each edition → "{label} — {n} version(s)"; selects best version of that edition |
| Version | `4k.tv` | `versionShortLabel(current)` | `Auto — Best match for this device` + scoped versions (`versionShortLabel` — `versionDetailLabel`) |
| Audio | `speaker.wave.2` | `audioValueLabel(current, idx)` | `Auto — Use the file default track` + `audioOptions` (ordinal) |
| Subtitles | `captions.bubble` | `subtitleValueLabel(current, idx)` | `Auto — Use your subtitle preferences` + `Off — Start without subtitles` (-1) + `subtitleOptions` |

- `TvPlaybackFormatting.kt` mirrors `DetailPlaybackFormatting.swift`: `versionShortLabel` ("4K · HDR" / "1080P" / "Auto"), `versionDetailLabel` (res · codec · container · size), `audioValueLabel`/`audioOptions`, `subtitleValueLabel`/`subtitleOptions` (+ `Forced`/`HI`), `currentEdition`. Pure + table-tested.
- Wire to the **existing** VM version/audio/subtitle state + handlers (`onSelectVersion(Int?)`, `onSelectAudioTrack(Int?)`, `onSelectSubtitleTrack(Int?)`; `-1` = subtitles off; `nil` = Auto). **Preserve current Auto + track-index semantics.** Retire `TvVersionPicker` and the Audio/Subtitles entries in the old "More" menu.
- **Series/season selectors are driven by the NEXT-UP episode's playback detail** (versions/audio/subs). Android detail state does not expose this yet → **dependency:** resolve the next-up episode's playback metadata to feed the selector on series/season pages (or show the placeholder until it loads).
- **Edition:** Android `FileVersion` has no `editionKey`/`editionRaw` → `editions.count` ≤ 1 → Edition hidden (3 selectors). Edition is a **follow-up** pending model/server support.

Reference: `selector-version-open.png`, `selector-audio-open.png`, `selector-subtitles-open.png`.

---

## 7. Episode rail (`TvDetailEpisodeRail.kt` re-skin) — exact `TVEpisodeRail`

Horizontal `LazyRow`, card spacing **36**, padding vertical 32 + horizontal `safeArea`, focusSection, scroll-clip disabled, user-initiated default focus on the **current** episode, auto-center current on first appearance. **OK navigates to the episode detail page** (`onEpisodeTap(contentId)`) — **not** direct play.

**Card** (`width 460`, `VStack spacing 18`): **still** (cardWidth × 260, `RoundedRectangle(10.dp)` clip) — `continuumSurfaceElevated` base, image fill, `film` placeholder; if played → black@0.35 scrim + watched badge (top-trailing, white circle 40 + checkmark 18 black, padding 12); progress bar bottom (black@0.6 track + white fill, height 5). Border: focus white@0.9 3dp / current white@0.7 2dp / else none. Below still, `VStack spacing 6`: `HStack spacing 10` { episode number 18sp bold tracking 2.0 @0.55 ("EPISODE n · 45m"); if current → "NOW VIEWING" 14sp heavy tracking 1.6 black on white Capsule, padding 8h/3v }; title 26sp semibold lineLimit 2 (color @0.92 idle / full on focus or current); overview 20sp regular secondaryText lineLimit 3 reservesSpace lineSpacing 3, top padding 4. Card style: scale 1.04 focus (pressed ×0.97), shadow black 0.45 r18 y8 (focus) / 0.3 r8 y4 (idle).

Reference: `series-episodes-cast.png`.

---

## 8. Cast rail, section headers, details, similar (keep / re-skin)

- **Cast** (`TVDetailCastRail`): circular photos 200dp, spacing 44, name 22sp semibold, character 18sp secondary; focus scale 1.05 + white@0.85 2dp ring + shadow; first-card default focus.
- **Section header** (`TVSectionHeader`): eyebrow 20sp bold tracking 3.0 @0.55 + title 42sp semibold, spacing 10.
- **Details** (`TVDetailFactsSection`) key-value facts; **Similar** = poster `TvMediaRow`.

---

## 9. Decomposition (each → a plan task)

1. **Squared control kit** — `TvSquaredButtons.kt`: primary/secondary `.compact` pill style + 72×72 square-toggle style (exact §2 values) + re-skin season chips. Visual-check against `movie-detail-toggle-*`.
2. **Anchored selector popover shell** — `TvAnchoredSelectorMenu.kt`: a `DropdownMenu`/`Popup` anchored under a button, rows = `"Title — Detail"` + checkmark, focus + dismiss + focus-restore. (Independent of selector data.)
3. **Playback formatting + tests** — `TvPlaybackFormatting.kt` (version/audio/subtitle/edition labels + Forced/HI/Auto), pure unit tests. (No UI.)
4. **Selector row wiring** — `TvPlaybackSelectorRow.kt` using #2 + #3 + the existing VM handlers; **retire `TvVersionPicker` + the More-menu audio/subs**; add the next-up-detail dependency for series/season.
5. **Hero rebuild** — `TvDetailHero.kt` (backdrop/scrims/editorial column/eyebrow/title/source/facts/starring + the action+selector focus sections) on the squared kit.
6. **Expandable synopsis** — `TvExpandableSynopsis.kt`; fold in the About overview.
7. **Screen assembly + focus rewire** — `TvItemDetailScreen.kt`: action inventory (remove Rate/Watch-Together; wire Watched; episode-only More), body order/spacing, episode-OK→detail, drop the macro focus container, Apple's focus flow with explicit `focusProperties`.
8. **Metric/scrim/scale tuning + emulator verification** against `docs/superpowers/reference/tvos-detail/`.

---

## 10. Testing & verification

- **Unit:** `TvPlaybackFormatting` (table-driven), focus-model helpers if extracted.
- **Compile:** `:androidTvApp:compileDebugKotlinAndroid` clean per task.
- **Visual:** Android-TV **emulator**; navigate a movie detail and a multi-season series detail; compare side-by-side with the reference frames — at rest, each toggle focus, each selector popover, expanded synopsis, season chips, episode/cast rails. (Shield screencap is black.)
- **Codex review** of each task diff before commit (standing rule).

---

## 11. Known divergences / open items

- **Rate + Watch-Together removed** from the TV detail to match tvOS (recorded above). Revisit only if they must stay reachable.
- **Series/season selector** needs next-up episode playback metadata wired (dependency in #4).
- **Edition selector** deferred until Android `FileVersion` carries edition data.
- **Compressed/black title fonts:** Apple uses `.width(.compressed)` heavyweight system fonts; Android lacks an exact equivalent — use the heaviest available weight + tight letter spacing and accept minor reflow (tune on emulator).
- **Hero height** is viewport-relative (≈0.907×); validate on the emulator.

## 12. Out of scope
Android phone detail; Cast/now-playing; player HUD; Search/Settings/Calendar/Audiobook/Browse; card-overlay badges; Aurora onboarding. Each is a later sub-project.
