# Sweep 1 — Android TV Nav / IA Foundation Implementation Plan

> Execute task-by-task with subagent-driven-development; Codex-review each change before commit; emulator-verify; commit author rxwatcher, no push.

**Goal:** Migrate Android TV top nav to the tvOS content-type-first model: `SILO | Home·Movies·Series·Music·Audiobooks·Calendar | search-icon + profile-avatar`, with an anchored two-level cascade scope selector replacing the full-screen library picker, persisted per-type scope, inverted-capsule tab focus, and focus-driven bar dim.

**Reference (tvOS):** `silo-apple/iosApp/iosApp/tvOS/Navigation/{TVMainTabView,TVTopMenuBar,TVCascadeSelector,TVLibraryScopeStore}.swift`.

**For You decision:** demote from a tab to a Home "Recommended For You" row (matches tvOS); keep `TvRecommendationsScreen` reachable as a secondary route.

## Critical correctness rule
Cascade + profile panels stay **persistently composed** (alpha 0 when inactive), never added/removed reactively — mirrors tvOS `persistentPanels` and prevents Compose focus-graph thrashing.

## Stages & tasks (ordering matters)

### Stage 1 — Foundation models (additive, compile-clean, no behavior change)
- **1.1** `ui/shell/TvLibraryTabType.kt` — enum Movies/Series/Music/Audiobooks + `matches(UserLibrary)` (use MediaMode type sets), `title`, `iconRes`, `librariesHeader`. + unit test.
- **1.3** `ui/shell/TvLibraryPill.kt` — enum Recommended/Library/Collections + `title`/`iconRes` + `set(type)` (all 3 for movies/series; Recommended+Library for music/audiobooks).
- **1.5** `ui/shell/TvTopMenuPanel.kt` — sealed Root(TvRootDestination)/Profile. (created after 2.0 since it references TvRootDestination — or use a generic param; create alongside 2.0.)
- **1.4** `data/preferences/TvLibraryScopeStore.kt` — per server·profile·type scope (key `scope_<type>`), `getSelectedLibraryId/setSelectedLibraryId/resolvedLibrary`; register in `di/AndroidTvModule.kt`. + unit test (cold start → first; stored resolves; evicted → first).

### Stage 2 — Destination model + shell data flow (disruptive: ripples to all use sites)
- **2.0** `ui/shell/TvRootDestination.kt` — extract from TvTopMenuBar; make sealed: Home / LibraryType(type) / Calendar. Remove Search & ForYou. Update ALL use sites (TvTopMenuBar, TvMainShell, TvMediaDestinations, mapRouteToRoot, toRoute, TvTopMenuFocus). Search→callback; ForYou→null in mapRouteToRoot.
- **2.1** Lift `List<UserLibrary>` into `TvMainShell` (promote the existing `produceState` at ~125); add `scopeSelections`/`pillSelections` stateMaps; `activeLibrary(type)`; `visibleRoots = [Home] + present-type tabs + [Calendar]`; `ensureSelectedRootIsVisible()`.
- **2.2** `TvRoute.kt`: add Movies/Series/Music/Audiobooks main routes; NavHost composables → `TvLibraryTypeTabScreen` (stub forwarding to current library screen). Keep legacy Libraries/Audio/Video as aliases.
- **2.3** Update `toRoute()`/`mapRouteToRoot()` for type routes + Calendar; legacy routes → null.

### Stage 3 — Bar rewrite
- **3.1** Rewrite `TvTopMenuBar.kt`: wordmark | capsule type-tabs | trailing search-icon+avatar; `TvTopMenuBarFocus` sealed; dwell timers (LaunchedEffect+delay, key=focusedButton); per-tab Down→`onEnterPanel`; inverted-capsule chrome (`focusedScale=1f`); bar dim `animateFloatAsState(... 0.70f)`; publish anchors via `onGloballyPositioned` into a `SnapshotStateMap`.
- **3.2** `ui/components/TvSkylinePanelChrome.kt` — frosted rounded panel modifier (shared by cascade + profile).
- **3.3** Wire new bar into `TvMainShell`; add panel state + control fns (`handleDwell`/`openPanelPreview`/`openPanelAndEnter`/`enterPanelFor`/`closePanel`) translated from TVMainTabView; `onSearchPressed`/`onProfilePressed`.
- **3.4** Panel overlay rendering: persistent overlays positioned by cached `LayoutCoordinates`; clamp leading inset to safe area; block focus when inactive (`focusProperties{canFocus=isActive}`).

### Stage 4 — Cascade selector
- **4.1** `ui/components/TvCascadeSelector.kt` — translate TVCascadeSelector: level-1 library rows + level-2 sections flyout; dwell-preview; entry token focuses current-scope row; Right→flyout, Left→anchor row, Back→close; row-top reporting via onGloballyPositioned; 150ms flyout-follow debounce; inverted-row chrome; single-library variant (sections only). Build/test in isolation.
- **4.2** Replace `TvFullScreenPicker` usage: `TvLibrariesScreen`→`TvLibraryTypeTabScreen(type, activeLibrary, selectedPill, onPillChange)`; remove picker state.
- **4.3** Wire cascade into shell `commitScope(type, library, pill)` → persist + selectRoot + content focus.

### Stage 5 — Profile dropdown
- **5.1** `ui/components/TvProfileDropdown.kt` — anchored under avatar (no full-screen scrim, use panel chrome); rows match TVProfileDropdown; entry-token focus; Back→close; persistent mount.
- **5.2** Remove Calendar row from dropdown.

### Stage 6 — Calendar tab + ForYou demotion
- **6.1** Calendar: `onSelectRoot(Calendar)`→navigate `TvMainRoute.Calendar`; `mapRouteToRoot` maps it.
- **6.2** Remove ForYou from `visibleRoots`; add Home "Recommended For You" see-all row → `TvRecommendationsScreen`; `mapRouteToRoot(ForYou)→null`.

### Stage 7 — Polish
- **7.1** Bar dim tween. **7.2** Panel-close focus return to anchor. **7.3** Content-handoff clears stale panel.

## Ordering constraints
2.0 before all of 2.x/3.x/4.x. 1.4 before 2.1. 2.1–2.3 before 3.3. 3.1 before 3.4. 4.1 standalone (before 4.2/4.3). 4.3 before 4.2 (don't remove picker until cascade live). 5.x parallel after 3.3. Cleanup `TvLibrarySelectionStore` after Stage 4.

## Emulator test points
Per-stage: tab set/order + capsule focus + dim (3.1); dwell-open without focus drop + Down-enters + Right-flyout + commit + Back-returns (3.4/4.1); single-library variant; profile dropdown anchored, no Calendar row (5.1); Calendar last tab (6.1); ForYou gone, Home row present (6.2); cold-start scope persistence (1.4).
