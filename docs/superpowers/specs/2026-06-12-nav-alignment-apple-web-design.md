# Navigation Alignment (Apple/web shell) — Design

**Status:** Approved design (brainstorming output). Implementation plan to follow via writing-plans.
**Scope:** Android **TV** (`androidTvApp`) and Android **mobile** (`androidApp`) navigation shells. Paths are repository-relative; assume the repository root (`silo-android`) is the cwd.
**Server changes:** None. Pure client navigation restructure.

---

## 1. Goal

Align the Android TV and Android mobile navigation with the Apple clients and the web UI: a dedicated **Home** (curated server sections) that is distinct from browsing, a single **Libraries** tab with a **library picker** (instead of per-media-type tabs), and a **For You** recommendations tab. This removes the current confusion where the TV "Video" tab is actually Home, the TV "Audio" tab is actually Libraries (defaulting to Movies), and the mobile bottom nav is split by media mode (Video/Audio/Reading).

## 2. Reference (what we're matching)

From the Apple clients (`silo-apple/iosApp/iosApp`):
- **tvOS top nav** (`tvOS/Navigation/TVTopMenuBar.swift`, `TVRootDestination`): **Search · Home · Libraries · For You**.
- **iOS bottom tabs** (`Navigation/TabRouter.swift`, `AppTab`): **Home · Libraries · For You · Settings**.
- **Home** (`Screens/Home/HomeView.swift`): a featured carousel + server-provided recommendation rows (incl. continue-watching/listening). A curated landing, *not* a media-type tab.
- **Libraries**: one tab with a **library selector**; per-library landing has Recommended / Collections modes. No Video/Audio/Reading split.

Web UI (`silo-server/web`) matches this shape: a sidebar with **Home** + a **Libraries** list, plus Discover/For-You.

> Note: Apple tvOS has **no audiobook player** — Android TV is ahead there (we just built one). Apple is the reference for the **nav shell only**; the audiobook player and the ebook reader stay as-is and are reached through Home (continue-listening) + the Libraries picker by item type.

## 3. Current state (what we're changing)

**TV** (`androidTvApp`):
- `TvRootDestination` (`ui/shell/TvTopMenuBar.kt`): `Search, Video, Audio, Requests`. The "Video" button uses `homeFocusRequester` and the "Audio" button uses `librariesFocusRequester` — the menu bar was *half-renamed* toward Apple's model but the enum, labels, and routing still say Video/Audio.
- `visibleTvDestinations` / `firstTvRoute` (`ui/shell/TvMediaDestinations.kt`): builds tabs from `MediaModeCapabilities` (Video if `hasVideo`, Audio if `hasAudio`).
- `TvMainShell.kt`: `TvMainRoute.Video` → `TvHomeScreen`, `TvMainRoute.Home` → `TvHomeScreen` (duplicate), `TvMainRoute.Audio` → `TvLibrariesScreen`, `TvMainRoute.Libraries` → `TvLibrariesScreen`. Route↔destination mapping collapses Home/Video → `Video` and Audio/Libraries → `Audio`.

**Mobile** (`androidApp`):
- Bottom nav (`ui/navigation/BottomNavBar.kt`, `MobileMediaTabs.kt`): `Tab.Video, Tab.Audio, Tab.Reading, Tab.Downloads`, derived from `MediaMode` via `visibleMobileTabs(capabilities, showDownloads)`.
- `MainScreen.kt` dispatch: `Tab.Video` → `HomeScreen`, `Tab.Audio` → `LibrariesScreen`, `Tab.Reading` → `ReadingHubScreen`, `Tab.Downloads` → `DownloadsScreen`.

## 4. Target

### 4.1 TV top nav — Search · Home · Libraries · For You ( + Requests)
- **Search** → `TvSearchScreen` (unchanged).
- **Home** → `TvHomeScreen` (the curated sections).
- **Libraries** → `TvLibrariesScreen` (the existing library-picker landing), surfacing **all** libraries (not media-mode-gated).
- **For You** → the recommendations screen (`TvRecommendationsScreen` / `RecommendationsViewModel`).
- **Requests** → `TvRequestsScreen`, still gated on the requests-enabled flag.

Changes:
- Rename `TvRootDestination`: `Search, Video, Audio, Requests` → `Search, Home, Libraries, ForYou, Requests`. Re-label the menu buttons "Home" / "Libraries" / "For You" (the focus requesters already match).
- `visibleTvDestinations`: `Search + Home + Libraries + ForYou + (Requests if enabled)` — Home/Libraries/For You always present (drop the per-media-mode gating; keep the `capabilities` param only if still needed for Requests/empty states).
- `firstTvRoute`: land on `TvMainRoute.Home`.
- `TvMainShell`: point the `Home` destination at `TvHomeScreen`, `Libraries` at `TvLibrariesScreen`, `ForYou` at the recommendations screen; fix the route↔destination mapping; remove the now-dead `TvMainRoute.Video`/`TvMainRoute.Audio` composables (and routes if unreferenced).

### 4.2 Mobile bottom nav — Home · Libraries · For You ( + Downloads)

**There is no "Reading" (or Video/Audio) concept at the nav level — it's all library-based.** The "Reading" tab and `ReadingHubScreen` are **removed**. Reading libraries (ebooks/audiobooks/comics) are just entries in the one Libraries picker, like every other library.

- **Home** → `HomeScreen` (curated sections).
- **Libraries** → a **unified** libraries screen with a library picker listing **every** library regardless of type. Selecting a library shows its content; tapping an item routes by **item type** to the right surface — video → player, audiobook → audiobook player, ebook/comic → reader.
- **For You** → recommendations.
- **Downloads** → `DownloadsScreen` (unchanged; still shown only when downloads exist / offline).

Changes:
- Replace the `Tab.Video/Audio/Reading` enum entries with `Tab.Home, Tab.Libraries, Tab.ForYou` (keep `Tab.Downloads`). Update `visibleMobileTabs` / `fallbackMobileTab` and the `MainScreen` dispatch; drop `tabForMediaMode` and the `MediaMode`→tab mapping (no longer a nav concept).
- Generalize `LibrariesScreen`/`LibrariesViewModel` to host **all** library types with a picker (today it's filtered to audio per the earlier fix — remove that filter; show every library). Per-item routing keys off item `type` (reuse `isAudiobookItemType` / `isBookLikeItemType`), not a library-mode tab.
- **Remove** `ReadingHubScreen` / `ReadingHubViewModel` and the `ReadingFormatFilter` tab plumbing; reading content is reached purely by selecting a reading library in the picker and tapping an item.

### 4.3 Library picker behavior (both platforms)
- One picker lists every visible `UserLibrary` (Movies, TV, Audio Books, Books, …). The active library persists per profile (TV already has `TvLibrarySelectionStore`; mobile uses the libraries VM's selected id).
- Content + item routing adapt to the selected library's `type` (and per-item `type`): video grids open the player; audiobook libraries open the audiobook player; ebook/comic libraries open the reader.
- This **subsumes the "Audio tab shows Movies" bug** — there is no Audio tab; Libraries shows a default library with a switcher.

## 5. Phasing

1. **TV nav rename + repoint** — `TvRootDestination` → Search/Home/Libraries/ForYou; menu labels; `visibleTvDestinations`/`firstTvRoute`; `TvMainShell` composables + route↔destination mapping; remove dead Video/Audio routes. (Groundwork already half-done.)
2. **TV For You tab** — wire the recommendations screen as the `ForYou` destination if not already reachable.
3. **Mobile unified Libraries** — generalize `LibrariesScreen`/VM to all library types with a picker + per-item-type routing; **remove `ReadingHubScreen`/`ReadingHubViewModel`** (reading libraries become picker entries). (Largest piece.)
4. **Mobile bottom nav** — `Tab` enum → Home/Libraries/ForYou(+Downloads); `MainScreen` dispatch; `visibleMobileTabs`/`fallbackMobileTab`.
5. **Cleanup** — remove now-unused `MediaMode`-tab plumbing where it no longer applies; keep `MediaMode`/library-type helpers used by the picker.

Each phase ships independently; Phase 1–2 (TV) are low-risk given the existing scaffolding, Phase 3 (mobile Libraries unification) is the real work.

## 6. Risks & assumptions

- ⚠️ **Mobile Libraries unification** is the risk: today Reading is a separate hub (`ReadingHubScreen`) with its own format filters and entry points. Removing it and surfacing reading libraries through the one Libraries picker must keep **per-item-type routing** correct — selecting a reading library and tapping a book must open the reader, an audiobook the audiobook player. Mis-wiring sends a book to the video player.
- ⚠️ **Navigation regressions** — both shells rewire route↔destination mapping, deep links, back-stack, and initial focus (TV). Each platform needs an on-device pass (TV via the `Silo_TV` emulator, phone via the Pixel).
- **Assumptions:** Home (`TvHomeScreen`/`HomeScreen`) already render server sections across media types (verified: they show Continue Watching + Continue Listening). Recommendations screens exist on both platforms. `MediaMode`/library-type helpers (`mobileMediaModeForLibraryType`, `isAudiobookItemType`, etc.) are reused by the picker for per-library/per-item routing, not for top-level tabs.
- **Out of scope:** the audiobook player and ebook reader internals (unchanged); a TV "recommendation channel" row; Settings as a bottom-nav tab on mobile (stays in the header/overflow as today).

## 7. Testing

- **Unit:** tab-visibility builders (`visibleTvDestinations`, `visibleMobileTabs`), route↔destination mapping round-trips, the per-item-type routing decision (extend the existing `tvPlayDestinationFor` pattern to mobile).
- **On-device:** TV — Home shows sections, Libraries opens the picker (switch Movies ↔ Audio Books), For You loads, audiobook Play still routes to the audiobook player. Mobile — Home/Libraries/For You tabs; the Libraries picker spans all types; book → reader, audiobook → audiobook player, video → player; Downloads still appears offline.
