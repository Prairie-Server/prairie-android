# Android TV ↔ tvOS Full-App Parity Audit

**Date:** 2026-06-18
**Reference (gold standard):** prairie-apple tvOS, `feature/playback-ux-redesign` — `iosApp/iosApp/tvOS/…` + shared views with `#if os(tvOS)`.
**Target:** prairie-android `androidTvApp`, Compose-for-TV — `…/ui/screens/…`.
**Method:** 9 parallel source-level audits (one per surface cluster). Color/theming excluded (Continuum palette already matches); focus is IA / layout / component anatomy / focus / metrics.
**Standing directive:** Android TV must match tvOS exactly. Rate & Watch Together were intentionally removed from TV detail (do not restore).

---

## Headline

The single root cause behind most divergences: **Android TV is on a pre-"Skyline" architecture.** tvOS uses content-type-first top tabs + an anchored two-level cascade scope selector + a passive focus-driven home marquee. Android uses a generic `Home · Libraries · For You` shell + full-screen library picker + an auto-advancing featured carousel. Almost every surface gap descends from that.

A second theme: **Android has several whole feature areas tvOS does not** (Requests, Notifications/Inbox, WatchTogether, a full Admin CRUD suite, global Browse, a personal Collections manager, library filter sheets). "Match tvOS exactly" implies removing/hiding them — a product decision, flagged below, not yet executed.

---

## A. Global navigation & chrome — **HIGHEST IMPACT (everything hangs off this)**

| # | Divergence | tvOS | Android | Sev |
|---|---|---|---|---|
| A1 | Tab taxonomy | content-type tabs `Home·Movies·Series·Music·Audiobooks·Calendar`, derived from profile libraries (`TVTopMenuBar.swift:33-94`, `TVMainTabView.swift:584-592`) | hardcoded `Search·Home·Libraries·For You` (`TvMediaDestinations.kt:10-17`) | High |
| A2 | Search | trailing icon button → `.search` (`TVTopMenuBar.swift:268-273`) | leftmost center **tab** (`TvTopMenuBar.kt:199-221`) | High |
| A3 | "For You" tab | none (recommendations only as pushed route) | first-class tab | High |
| A4 | Calendar | last root **tab** | buried in profile dropdown (`TvMainShell.kt:714`) | High |
| A5 | Cascade selector | anchored 2-level dropdown (libraries → sections flyout), dwell-open / d-pad-down (`TVCascadeSelector.swift`) | ABSENT — full-screen `TvFullScreenPicker` from inside content | High |
| A6 | Per-type scope store | persisted per type/server/profile (`TVLibraryScopeStore.swift`) | ABSENT | High |
| A7 | Bar anatomy | `PRAIRIE wordmark | center tabs | search+profile` | `profile(leading) | center tabs`, no wordmark, no trailing cluster | High |
| A8 | Tab focus chrome | inverted white capsule, bg-colored text | translucent white fill, never inverted (`TvTopMenuBar.kt:329-342`) | Med |
| A9 | Dwell-preview / d-pad-down panel entry | yes | ABSENT (down just moves to content) | High |
| A10 | Profile dropdown rows | Switch Profile·Watchlist·Favorites·History·Settings·[Admin]·Switch Server·Sign Out + avatar header | Notifications·Calendar·Switch Profile·Settings·Switch Server·Sign Out | Med |
| A11 | Bar dims on focus-leave | yes | scroll-hide only, no focus dim | Med |
| A12 | Bar metrics | `ContinuumTheme.Skyline.*` tokens, 188pt inset | bespoke values (94dp inset, 12sp labels) | Med |

## B. Home (Skyline) — **HIGH, big rebuild**

- **B1** tvOS = passive **focus-marquee** (mirrors focused row card, 150ms debounce, 240ms crossfade, no Play/Info buttons); Android = auto-advancing **featured hero carousel** with page dots + Play/More-Info pills. (`TVSkylineSectionFeed.swift`, `TVFocusMarquee.swift` vs `TvHomeHeroCarousel.kt`). **High**
- **B2** Backdrop: tvOS = crisp top-right corner-anchored art (0.64w×0.70h) + diagonal tint; Android = full-width 22dp-blurred top band + vertical fade. **High**
- **B3** Backdrop driver: focused card (tvOS) vs carousel slide (Android). **High**
- **B4** Marquee billboard (logo/title + codec/HDR/rating badges + 3-line synopsis + cast/air-date line): ABSENT on Android. **High**
- **B5** Card anatomy: centered caption (tvOS) vs left (Android); 16pt vs 8dp caption gap; watched badge 40pt white check vs 12dp blue dot; episode tag "S2 · E10" vs "S02E03"; row spacing 40pt vs 20dp. **Med**
- **B6** "See all" on every row (Android) — tvOS Home has none. Continue-Watching `play.circle.fill` header icon absent on Android. Client-side "Coming this week" row is Android-only. **Med**

## C. Libraries — **HIGH**

- **C1** Per-type tabs (see A1) vs single "Libraries" screen. **High**
- **C2** Cascade selector (see A5) vs inline TabSlider + full-screen picker. **High**
- **C3** Sub-destination naming/order: tvOS `Recommended·Collections·Browse`; Android `Recommended·Library·Collections`. **Med**
- **C4** Alphabet rail: tvOS persistent right-edge collapsible A–Z rail; Android = chips inside filter sheet (stale docstring claims a rail exists). **High**
- **C5** Grid spacing: tvOS 40H/60V, 6-col (5 beside rail); Android inconsistent (collection grids 20/32, Adaptive(180)). **Med**
- **C6** Library collections: tvOS grouped sections + mono headers + 2:3 poster cards (centered caption, caps count noun); Android flat 6-col, left caption, "N items". **High**
- **Android-only:** in-grid filter sheet (Genre/Year/Sort/Order/Content-Rating/Reset), decade Year picker, global cross-library Browse, personal Collections manager (4-col text tiles), full-screen library picker.

## D. Detail — **mostly done; residuals**

- **D1** Series/Season Play does not target a next-up episode; missing next-up label ("Play S2·E3"), next-up selector row + `TVVersionPillPlaceholder`, auto-focus-on-resolve. **High**
- **D2** Section header sizes: tvOS title 42pt / eyebrow 20pt tracking 3.0; Android title 28sp / eyebrow 16sp tracking 0. Page-wide. **High**
- **D3** Season page missing "Go to Series" overflow + season hero tokens (eyebrow=series title, episode-count source token). **High**
- **D4** Similar rail uses `TvSectionHeader` (22sp, "See all") not `TvDetailSectionHeader`; inconsistent within page. **Med**
- **D5** Body horizontal inset 40dp vs tvOS 80pt. **Med**
- **D6** Cast card: name 20sp vs 22pt, no reserved 2-line height, vpad 16 vs 24, focus = Card halo vs explicit white circle ring. **Med**
- Verified-aligned: episode rail (460×260 r10), season chips. Android-only: Media Info dialog (intentional), Genres facts row.

## E. Player HUD — **HIGH**

- **E1** HUD shape: tvOS top-center floating card (1100×380, horizontal pill tabs, no dim); Android full-height right drawer (560dp, vertical tabs). **High**
- **E2** No Subtitles tab in Android HUD (subtitles are a separate `TvSubtitleMenu`). **High**
- **E3** Info pane: tvOS two rich columns (title/stream); Android = title + time only. **High**
- **E4** Transport: Android adds Back + separate Subtitles buttons; tvOS uses `xmark` + options only. Forward skip 10s (Android) vs **30s** (tvOS). Uniform 66pt buttons (tvOS) vs enlarged play/pause; focus scale (Android) vs none. **High/Med**
- **E5** Selection model: tvOS row→picker-dialog; Android inline chips/steppers. No Quality/bitrate picker on Android. **High**
- **E6** Up-Next / end-of-playback panel (mini-player + Play Now + Keep Watching + countdown ring): ABSENT (Android only has a "Still watching?" pass-out dialog). **High**
- Plus: title footer placement, time-row size/position, hold-seek indicator anatomy, intro-region scrubber band, buffering/sleep status chips, chapter row index/marker.

## F. Settings — **HIGH**

- **F1** IA: tvOS drill-in category menu (sub-screens, trailing value summaries); Android one giant scrolling wall. **High**
- **F2** Account header: tvOS tappable avatar row; Android text info rows. **High**
- **F3** Card Overlays settings sub-screen (two-pane preview + per-overlay enable/position/accent/icon + reset): **entirely ABSENT on Android.** **High**
- **F4** Subtitle Appearance block (font family/color/outline/background/opacity/position + Custom Appearance toggle + live preview): ABSENT (Android exposes only Size, 3 of 5 sizes). **High**
- **F5** "Show Next Up" prompt-timing picker absent; Android-only "Resume Skip-Back" & "Still-Watching threshold". Picker-sheet pattern, sign-out confirm, footers, Profile-7 HDR10 toggle, bundle version — all missing. **Med**
- **Android-only sections:** large title block, Notifications section, Library shortcuts, Email/Role rows, Manage Sessions, Pair Device.

## G. Calendar — **HIGH**

- **G1** Root tab (tvOS) vs profile-menu route (Android). **High**
- **G2** Layout: tvOS grouped **horizontal poster shelves** per day; Android vertical `LazyColumn` of full-width **landscape row cards**. **High**
- **G3** Card anatomy: tvOS poster + overlays (badge pills, watched check, time capsule) + below-poster caption; Android inline thumbnail+text. **High**
- **G4** Filter bar segmented capsule vs separate chips; week-strip event dots + selectable day→scroll-to-shelf (Android day chips inert); empty days rendered (tvOS) vs skipped; empty-state action button absent. Android-only Library filter rail. **Med**

## H. Onboarding (Aurora) — **HIGH**

- **H1** Aurora cinematic skin (ribbon backdrop variants, eyebrows, glass panels, cream/gold button kit, controlled input fields): **entirely absent**; Android is a generic Material re-skin. **High**
- **H2** iPhone-handoff server setup (LAN advertiser + receiver pairing panel + SearchingBeacon): ABSENT. **High**
- **H3** Phone-first QR login hero + numbered step rows + password-fallback toggle: Android shows form + QR side-by-side instead. **High**
- **H4** Profile grid: tile 280pt vs 180dp, wrapping grid vs single row, tint focus halo, child/PIN badges, dashed add-tile. Android-only Manage-mode. **Med**
- Plus password show/hide, "Change server" on login, advanced server options (scheme/port), two-column create-profile with DiceBear preset grid.

## I. Surface presence — **KEEP & RESTYLE (decided 2026-06-18)**

Policy: Android-only surfaces are **kept and reworked in the tvOS design language**, not removed. WatchTogether & Rate are **restored** to the TV detail (styled as tvOS-grammar controls). The list below is therefore a *restyle* worklist, not a removal list.

Android surfaces with **no tvOS counterpart** (adopt tvOS visual grammar; do not delete):

| Surface | tvOS reality | Android | Note |
|---|---|---|---|
| **WatchTogether** | none anywhere | entry dialog still wired into TV detail (`TvItemDetailScreen.kt:85,102`) | Contradicts the "removed from TV detail" directive — plumbing remains |
| **Requests** | none | full Requests/MyRequests/RequestDetail (Settings-reached) | stale `TvRoute.kt:12` comment |
| **Notifications/Inbox** | none | Inbox screen + avatar unread badge + dropdown row | |
| **Admin** | read-only 6-card stats dashboard only | full Users CRUD + Sessions + Scans + Logs | major over-build |
| Global Browse, personal Collections manager, library filter sheet/year picker, audiobook Bookmarks/Sleep/Skip panels, profile Manage-mode | none | present | Android extras |

Surfaces that **match well** already: Person detail, Personal grids (Watchlist/Favorites/History), Search (idiom differs), Recommendations (missing SavedShortcutsRow).

---

## Recommended sequencing (load-bearing first)

1. **Global nav / IA** (A) — content-type tabs, Calendar tab, Search icon, cascade selector, wordmark. Everything else (Libraries, Home entry focus) depends on it.
2. **Home Skyline** (B) — focus-marquee + corner backdrop + card anatomy.
3. **Libraries** (C) — cascade + alphabet rail + grouped collections (follows from A).
4. **Detail residuals** (D) — quick wins: section header sizes, similar-rail header, season "Go to Series", body inset; then series/season next-up.
5. **Player HUD** (E), **Settings** (F), **Calendar** (G), **Onboarding/Aurora** (H) — each a self-contained sub-project.
6. **Surface-presence cleanup** (I) — only after product decision on remove-vs-keep.

Each numbered item is its own brainstorm → spec → plan → implement cycle (like the detail sub-project was).
