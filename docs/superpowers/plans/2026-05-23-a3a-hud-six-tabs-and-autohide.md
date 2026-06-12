# A.3a — HUD six-tab structure + independent auto-hide + legacy menu cleanup

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Take the existing `TvPlayerHud` from its current 5-tab-conditional state to a 6-tab-always-visible Infuse-style HUD. Make the HUD's visibility independent of the global controls auto-hide timer (today the HUD inherits the 5s global timer, which is wrong — the user opened the HUD intentionally and expects it to stay until they close it). Remove the now-redundant `TvSubtitleMenu` + `TvAudioTrackMenu` modal dialogs that pre-date the HUD.

**Architecture:** All changes inside `androidTvApp/.../ui/screens/player/` and `TvPlayerViewModel.kt`. No new dependencies. No new pane files (deferred to A.3b/c when actual data wiring lands). Three discrete commits.

**Scope split:** This is **A.3a** of multiple A.3 plans:
- **A.3a (this plan):** structural refresh — 6 tabs always-on, HUD-independent auto-hide, legacy menu cleanup. No new data wiring.
- **A.3b (next):** chapters wiring — extract Media3 `MediaMetadata.chapters`, thread to scrubber markers + Chapters pane.
- **A.3c (later):** Stats wiring — `PlaybackAnalyticsListener` hookup for bitrate/codec/HDR/dropped frames.
- **A.3d (later):** Video pane toggles — HDR force-SDR + video gravity (letterbox vs zoom).
- **A.3f (after E):** Audio/subtitle delay sliders — depends on `DelayAudioProcessor` from sub-project E.

**Tech stack:** Kotlin 2.1.20, Compose-for-TV 1.0.1, existing player infrastructure.

**Reference:** Spec section A.3 at `/opt/silo-android/docs/superpowers/specs/2026-05-23-android-tv-parity-rework-design.md`. Architectural map from explorer agent (relevant excerpts in tasks below).

**Testing posture:** Per `AGENTS.md`, no UI tests for visual changes. One focused unit test on the auto-hide gating logic if it's extracted to a pure function — otherwise skipped.

---

### Task 1: Make all 6 HUD tabs always visible (drop conditional gating)

**Files:**
- Modify: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt`

**Why:** Per the architectural audit, the HUD currently conditionally shows tabs based on track availability (lines 82–92 per the audit) — for example, the Audio tab is hidden if there's only one audio track, Stats and Chapters are explicitly gated as "stub" tabs that are never rendered. Apple's `TVPlayerInfoHUD` shows all 6 tabs unconditionally. Match that: always show Info / Stats / Video / Audio / Subtitles / Chapters. Panes that have no data show an empty-state message ("Stats unavailable", "No chapters in this title", etc.).

- [ ] **Step 1: Read the current tab list and gating logic**

```bash
sed -n '70,180p' /opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt
```

Find:
- The list/sealed class enumerating the tabs (the audit said "Tabs are computed locally in `TvPlayerHud.kt:82–92` based on track availability").
- The gating conditions (`audioTracks.isNotEmpty()`, etc.).
- Comments mentioning "gated per spec §4.2" (the audit noted these around stub tabs).

- [ ] **Step 2: Replace conditional tab list with a fixed 6-tab list**

Change whatever the current `tabs` computation is into a fixed list:

```kotlin
private enum class HudTab(val label: String) {
    Info("Info"),
    Stats("Stats"),
    Video("Video"),
    Audio("Audio"),
    Subtitles("Subtitles"),
    Chapters("Chapters"),
}

private val HUD_TABS = HudTab.entries
```

(If a `HudTab` enum already exists with subset of these values, ADD the missing ones rather than replace. Match casing/style to what's there.)

- [ ] **Step 3: Update the tab strip to render `HUD_TABS` unconditionally**

The current tab strip likely iterates `tabs.forEach { … }` where `tabs` was a filtered list. Change it to iterate `HUD_TABS.forEach`. If the iteration uses the old gated variable, replace with `HUD_TABS`.

- [ ] **Step 4: Update the active-pane switch**

The pane-rendering `when (selectedTab)` block likely has branches only for tabs that were conditionally shown. Add branches for `Stats` and `Chapters` (and `Video` if it was gated):

```kotlin
when (selectedTab) {
    HudTab.Info -> HudInfoPane(state)
    HudTab.Stats -> HudEmptyStatePane("Stats unavailable")
    HudTab.Video -> HudEmptyStatePane("Video options will appear here")
    HudTab.Audio -> HudPickerPane(
        title = "Audio",
        tracks = state.audioTracks,
        onSelect = onAudioSelect,
        emptyMessage = "No alternate audio tracks",
    )
    HudTab.Subtitles -> HudPickerPane(
        title = "Subtitles",
        tracks = state.subtitleTracks,
        onSelect = onSubtitleSelect,
        emptyMessage = "No subtitles available",
    )
    HudTab.Chapters -> HudEmptyStatePane("No chapters in this title")
}
```

If `HudPickerPane` doesn't currently accept an `emptyMessage` parameter, add one (default to a generic "Nothing here yet" string for back-compat — single new optional param).

- [ ] **Step 5: Add `HudEmptyStatePane` if not present**

Inside `TvPlayerHud.kt` (private composable), add:

```kotlin
@Composable
private fun HudEmptyStatePane(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

Adjust imports for `Box`/`Alignment`/`padding` if not already there.

- [ ] **Step 6: Build**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "feat(tv-player): always render all 6 HUD tabs (A.3a)

Stats and Chapters were previously stub-gated; Audio/Subtitles/Video
were conditional on track availability. Match Apple's TVPlayerInfoHUD
which shows all 6 tabs unconditionally with empty-state messages for
panes with no data.

Stats + Chapters + Video panes currently render placeholder empty
states; data wiring lands in A.3b (chapters), A.3c (stats), and A.3d
(video toggles)."
```

---

### Task 2: HUD-independent auto-hide timer

**Files:**
- Modify: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt`

**Why:** Per the audit, the global controls auto-hide (`CONTROLS_AUTO_HIDE_MS = 5000`) at lines 354–360 hides the entire overlay including the HUD when 5s elapses without input. This is wrong for the HUD specifically — the user opened it intentionally (pressed the Tune button) and expects it to stay until they close it explicitly via Back. Make the auto-hide suspend while `state.hudOpen` is true.

- [ ] **Step 1: Read the current auto-hide LaunchedEffect**

```bash
sed -n '345,370p' /opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt
```

Confirm it looks like:
```kotlin
LaunchedEffect(state.showControls, state.isPaused) {
    if (state.showControls && !state.isPaused) {
        delay(CONTROLS_AUTO_HIDE_MS)
        viewModel.setControlsVisible(false)
    }
}
```

- [ ] **Step 2: Gate the timer on `!state.hudOpen`**

Change to:

```kotlin
LaunchedEffect(state.showControls, state.isPaused, state.hudOpen) {
    if (state.showControls && !state.isPaused && !state.hudOpen) {
        delay(CONTROLS_AUTO_HIDE_MS)
        viewModel.setControlsVisible(false)
    }
}
```

(`state.hudOpen` becomes a key so the effect re-runs when the HUD opens/closes; the inner condition prevents the timer from starting while the HUD is open.)

When the user closes the HUD via Back, `state.hudOpen` flips to false; this effect re-keys, the inner condition becomes true, and the 5s timer starts fresh. That's the right behavior — closing the HUD should give the user 5s of transport visibility before the overlay fades.

- [ ] **Step 3: Build**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "fix(tv-player): suspend 5s controls auto-hide while HUD is open (A.3a)

The global controls auto-hide LaunchedEffect treated the HUD like any
other transient overlay and dismissed it after 5s of input idle.
The HUD is intentional UI (user pressed Tune to open it) and should
only dismiss on explicit Back. Gate the timer on !state.hudOpen;
closing the HUD restarts the 5s window for the transport cluster."
```

---

### Task 3: Remove legacy `TvSubtitleMenu` + `TvAudioTrackMenu` modal dialogs

**Files:**
- Modify: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt`
- Modify: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerViewModel.kt`
- Possibly delete: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvTrackMenus.kt` (if both modals are removed and the file is now empty)

**Why:** Per the audit, `TvSubtitleMenu` and `TvAudioTrackMenu` are pre-HUD modal dialogs rendered at `TvPlayerScreen.kt:474–493`, with corresponding `subtitleMenuOpen` / `audioMenuOpen` ViewModel state and `closeSubtitleMenu()` / `closeAudioMenu()` handlers. The HUD's Audio + Subtitles tabs subsume their function — these are dead UX paths. Remove the modal rendering, the open/close handlers, and the state fields. The companion `BackHandler` chain entries (`state.subtitleMenuOpen -> viewModel.closeSubtitleMenu()` etc.) go too.

**Critical:** before deleting, grep for ANY open trigger:

```bash
grep -rn "openSubtitleMenu\|openAudioMenu\|subtitleMenuOpen\|audioMenuOpen" /opt/silo-android/androidTvApp/src
```

If anything other than `TvPlayerScreen.kt`, `TvPlayerViewModel.kt`, or `TvTrackMenus.kt` itself references these, STOP and report — the dialogs may still be reachable from a path the audit missed. (Expected: no external openers; they're orphan UI paths after the HUD landed.)

- [ ] **Step 1: Run the grep guard**

```bash
grep -rn "openSubtitleMenu\|openAudioMenu\|subtitleMenuOpen\|audioMenuOpen" /opt/silo-android/androidTvApp/src
```

If unexpected hits appear, STOP and report BLOCKED.

- [ ] **Step 2: Remove modal rendering from `TvPlayerScreen.kt`**

Find the block (around lines 474–493 per audit):

```kotlin
if (state.subtitleMenuOpen) { TvSubtitleMenu(...) }
if (state.audioMenuOpen) { TvAudioTrackMenu(...) }
```

Delete both blocks entirely.

- [ ] **Step 3: Remove BackHandler entries for the menus in `TvPlayerScreen.kt`**

The `BackHandler` (around lines 170–180 per audit) has branches:

```kotlin
state.subtitleMenuOpen -> viewModel.closeSubtitleMenu()
state.audioMenuOpen -> viewModel.closeAudioMenu()
```

Delete those two branches. The resulting `BackHandler` should now read:

```kotlin
BackHandler(enabled = true) {
    when {
        state.hudOpen -> viewModel.closeHUD()
        state.showControls -> viewModel.setControlsVisible(false)
        else -> stopPlaybackAndExit()
    }
}
```

- [ ] **Step 4: Remove the menu state fields and handlers from `TvPlayerViewModel.kt`**

In `UiState` (around lines 106–157), remove:
- `val subtitleMenuOpen: Boolean = false,`
- `val audioMenuOpen: Boolean = false,`

Remove the handler methods (`openSubtitleMenu`, `closeSubtitleMenu`, `openAudioMenu`, `closeAudioMenu`) if they exist.

- [ ] **Step 5: Remove imports of `TvSubtitleMenu` / `TvAudioTrackMenu` from `TvPlayerScreen.kt`**

Search for these imports near the top of `TvPlayerScreen.kt` and delete them.

- [ ] **Step 6: Build and check if `TvTrackMenus.kt` still has callers**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. If `TvSubtitleMenu` or `TvAudioTrackMenu` are still referenced, the grep guard in Step 1 missed something — investigate and report.

Then check whether `TvTrackMenus.kt` has any remaining purpose:

```bash
grep -rn "TvSubtitleMenu\|TvAudioTrackMenu" /opt/silo-android/androidTvApp/src
```

If the only hits are inside `TvTrackMenus.kt` itself (i.e., its own definitions), the entire file is dead. Delete it:

```bash
git -C /opt/silo-android rm androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvTrackMenus.kt
```

If there are remaining external references you didn't expect, STOP — investigate first.

- [ ] **Step 7: Rebuild to confirm**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerViewModel.kt

# Only add the deletion if Step 6 confirmed the file is dead:
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvTrackMenus.kt 2>/dev/null || true

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "chore(tv-player): remove legacy TvSubtitleMenu and TvAudioTrackMenu (A.3a)

These modal track-picker dialogs pre-dated the HUD's Audio / Subtitles
tabs. They were unreachable from UI but kept compiling because the
BackHandler still listed their open-state branches. Per audit, no
external opener exists.

Removes the menu rendering, BackHandler branches, ViewModel state
fields (subtitleMenuOpen, audioMenuOpen) and their close handlers.
Deletes TvTrackMenus.kt if it's now empty."
```

---

## Self-Review

**Spec coverage** (against the A.3 section of the spec doc — partial because A.3 has multiple sub-plans):
- Always-on 6-tab structure → Task 1 ✓
- HUD survives independent of global controls auto-hide → Task 2 ✓
- Legacy menu cleanup → Task 3 ✓
- Per-pane data wiring (Stats, Chapters, Video toggles, audio/subtitle delays) → DEFERRED to A.3b/c/d/f (each its own plan)
- Extracted per-pane files (`InfoPane.kt`, `StatsPane.kt`, etc.) → DEFERRED — the current 2-pane generic structure works fine for empty states; extract when adding real data wiring

**Placeholder scan:** No "TBD." Task 1 Step 4 mentions adding `emptyMessage` param to `HudPickerPane` if not present — concrete, not a placeholder.

**Type consistency:** `HudTab` enum + `HUD_TABS` consistent. `state.hudOpen` field is already in `UiState` per audit. `HudEmptyStatePane` is the only new composable name; used consistently.

**Sequencing:** Task 1 (tab list) → Task 2 (auto-hide) → Task 3 (cleanup). Tasks 1 and 2 are independent; Task 3 is independent of both. Could be reordered. Sequenced for natural narrative: structure → behavior → cleanup.

**Risk:** Task 3 carries the most risk — touching `TvPlayerViewModel.UiState` shape can break consumers if any other UI path reads `subtitleMenuOpen` / `audioMenuOpen`. The grep guard in Step 1 is the safety net. If unexpected callers exist, BLOCKED stops the work.
