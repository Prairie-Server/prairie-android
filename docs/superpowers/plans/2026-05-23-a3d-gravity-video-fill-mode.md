# A.3d-gravity — Video pane fill-mode toggle (letterbox vs zoom)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the HUD's Video pane empty-state with a working "Fill mode" toggle (Letterbox / Zoom). Wire to `PlayerView.resizeMode` via the existing `AndroidView.update` lambda. Session-scoped (no persistence — Apple's tvOS video gravity is per-playback).

**Architecture:**
- Add `VideoFillMode` enum (`Fit`, `Zoom`) + `videoFillMode: VideoFillMode = Fit` to `TvPlayerViewModel.UiState`.
- Add `onVideoFillModeChanged(VideoFillMode)` handler.
- Update the existing `AndroidView.update` lambda in `TvPlayerScreen.kt` to apply `RESIZE_MODE_FIT` / `RESIZE_MODE_ZOOM` based on state.
- Replace the Video pane's `HudEmptyStatePane(...)` branch in `TvPlayerHud.kt` with a real `HudVideoPane` showing the toggle.

**Scope split note:** A.3d's spec also mentions an HDR force-SDR toggle. That requires track-selector reconfiguration (`TrackSelectionParameters` mutation) and possibly Display API hooks — deferred as **A.3d-hdr** for separate investigation. The video gravity toggle is independent and lands now.

**Tech stack:** Kotlin 2.1.20, Compose-for-TV 1.0.1, Media3 1.10.0 `AspectRatioFrameLayout.RESIZE_MODE_*` constants.

**Reference:** Spec section A.3 at `/opt/prairie-android/docs/superpowers/specs/2026-05-23-android-tv-parity-rework-design.md`. Existing `AndroidView` setup is in `TvPlayerScreen.kt:385-398` (PlayerView factory + update lambda).

**Testing posture:** Per `AGENTS.md`, no UI test for the toggle. The enum + state addition is trivial and doesn't warrant a unit test.

---

### Task 1: Add `VideoFillMode` + ViewModel state and handler

**Files:**
- Modify: `/opt/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerViewModel.kt`

**Why:** Enum + state field + handler — pure plumbing.

- [ ] **Step 1: Add the enum**

In `TvPlayerViewModel.kt` (top-level alongside `PlayerStatsSnapshot` / `PlayerTrackEntry`), add:

```kotlin
/**
 * How the video surface scales to fill the player area. Session-scoped
 * (resets to [Fit] on each new playback) — matches tvOS behavior.
 */
enum class VideoFillMode {
    /** Letterbox: preserve aspect ratio, may show bars. Default. */
    Fit,
    /** Zoom: preserve aspect ratio, fill screen, may crop edges. */
    Zoom,
}
```

- [ ] **Step 2: Add field to `UiState`**

In `UiState` (alongside the other player fields like `stats`, `audioTracks`, etc.), add:

```kotlin
    val videoFillMode: VideoFillMode = VideoFillMode.Fit,
```

- [ ] **Step 3: Add handler**

After the existing handlers (e.g. near `openHUD()`/`closeHUD()`), add:

```kotlin
    fun onVideoFillModeChanged(mode: VideoFillMode) {
        _uiState.update { it.copy(videoFillMode = mode) }
    }
```

Match the local state-update idiom — the file uses `_uiState.update { ... }` (confirmed in A.3c).

- [ ] **Step 4: Build**

```bash
cd /opt/prairie-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerViewModel.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android commit -m "feat(tv-player): VideoFillMode enum + session-scoped state (A.3d-gravity)

Adds VideoFillMode (Fit/Zoom) + uiState.videoFillMode (default Fit) +
onVideoFillModeChanged() handler. Session-scoped per Apple's tvOS
behavior — resets on each new playback. UI + PlayerView wiring land
in the next two commits."
```

---

### Task 2: Wire `resizeMode` in `AndroidView.update` lambda

**Files:**
- Modify: `/opt/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt`

**Why:** The `AndroidView.update` lambda is invoked on each recomposition where its captured state changes. Adding `state.videoFillMode` to the lambda's closure means a toggle in the HUD will trigger recomposition and re-run the update — applying the new resize mode immediately.

- [ ] **Step 1: Add the import**

Add to `TvPlayerScreen.kt`:

```kotlin
import androidx.media3.ui.AspectRatioFrameLayout
```

- [ ] **Step 2: Update the `AndroidView.update` lambda**

Find the existing `AndroidView(... update = { view -> view.player = controller }, ...)` around line 397 (the line will shift slightly post-Step 1 import).

Replace the update lambda body:

```kotlin
                        update = { view ->
                            view.player = controller
                            view.resizeMode = when (state.videoFillMode) {
                                VideoFillMode.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                VideoFillMode.Zoom -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            }
                        },
```

`state.videoFillMode` references the existing `state` variable already captured from `viewModel.uiState.collectAsState()`. The `when` is exhaustive on the enum.

- [ ] **Step 3: Build**

```bash
cd /opt/prairie-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android commit -m "feat(tv-player): apply VideoFillMode to PlayerView.resizeMode (A.3d-gravity)

AndroidView.update lambda now reads uiState.videoFillMode and sets
PlayerView.resizeMode to RESIZE_MODE_FIT or RESIZE_MODE_ZOOM.
Recomposition on state change triggers re-run of the lambda; effect
is instantaneous on toggle.

HUD UI toggle to drive this state lands in the next commit."
```

---

### Task 3: Render the toggle in HUD Video pane

**Files:**
- Modify: `/opt/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt`

**Why:** Replace `HudTab.Video -> HudEmptyStatePane("Video options will appear here")` with a real `HudVideoPane` that shows the Fill mode toggle.

- [ ] **Step 1: Find the Video branch**

```bash
grep -n "HudTab.Video" /opt/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt
```

Should land on `HudTab.Video -> HudEmptyStatePane("Video options will appear here")`.

- [ ] **Step 2: Add `HudVideoPane` private composable**

Add to `TvPlayerHud.kt`:

```kotlin
@Composable
private fun HudVideoPane(
    fillMode: VideoFillMode,
    onFillModeChanged: (VideoFillMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = "Fill mode",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            VideoFillMode.entries.forEach { mode ->
                VideoFillModeChip(
                    mode = mode,
                    selected = fillMode == mode,
                    onClick = { onFillModeChanged(mode) },
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun VideoFillModeChip(
    mode: VideoFillMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = when (mode) {
        VideoFillMode.Fit -> "Letterbox"
        VideoFillMode.Zoom -> "Zoom (crop)"
    }
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
            } else {
                Color.Transparent
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.surface,
            pressedContainerColor = MaterialTheme.colorScheme.onSurface,
            pressedContentColor = MaterialTheme.colorScheme.surface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        modifier = Modifier.continuumFocus(interactionSource, RoundedCornerShape(12.dp)),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
```

If `Surface`, `ClickableSurfaceDefaults`, `MutableInteractionSource`, `continuumFocus`, `ExperimentalTvMaterial3Api`, or `Color` aren't yet imported in this file, add them. Most should already be present from other chip composables in the same file or sibling files.

Check what the existing chip pattern is in this codebase — for example `TvFilterSheet.kt` from A.5 introduced `FilterChoiceChip`. If similar already exists in `TvPlayerHud.kt` or a nearby helper file, prefer reusing it rather than introducing a new chip style. Match the local visual language.

- [ ] **Step 3: Replace the Video branch**

Change:

```kotlin
HudTab.Video -> HudEmptyStatePane("Video options will appear here")
```

To:

```kotlin
HudTab.Video -> HudVideoPane(
    fillMode = state.videoFillMode,
    onFillModeChanged = onVideoFillModeChanged,
)
```

- [ ] **Step 4: Add `onVideoFillModeChanged` parameter to `TvPlayerHud`**

Open `TvPlayerHud.kt`'s function signature and add:

```kotlin
    onVideoFillModeChanged: (VideoFillMode) -> Unit,
```

Place it near the other `onXxxChanged` callbacks (e.g. next to audio/subtitle selection callbacks).

- [ ] **Step 5: Wire from `TvPlayerScreen.kt`'s `TvPlayerHud(...)` call site**

Find the `TvPlayerHud(...)` call site and add:

```kotlin
    onVideoFillModeChanged = viewModel::onVideoFillModeChanged,
```

- [ ] **Step 6: Build**

```bash
cd /opt/prairie-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android commit -m "feat(tv-player): HUD Video pane renders Fill mode toggle (A.3d-gravity)

HudVideoPane shows two chips (Letterbox / Zoom) bound to
uiState.videoFillMode. Selection fires onVideoFillModeChanged, which
the ViewModel reduces into the state; AndroidView.update applies the
new resizeMode on the next recomposition.

A.3d's HDR force-SDR toggle is deferred to A.3d-hdr (requires track
selector reconfiguration that warrants its own investigation)."
```

---

## Self-Review

**Spec coverage** (A.3 Video pane requirements):
- "Video gravity" → Tasks 1-3 ✓
- "HDR toggle" → DEFERRED to A.3d-hdr; documented in plan + commit message
- "Route info" → DEFERRED to sub-project E (will populate the route-info row in the Video pane when E's `RouteResolution` data is available)

**Placeholder scan:** No "TBD." The chip styling falls back to the local idiom if a matching chip pattern already exists.

**Type consistency:** `VideoFillMode` referenced identically across all three files. `onVideoFillModeChanged: (VideoFillMode) -> Unit` consistent signature throughout.

**Sequencing:** Task 1 (state) → Task 2 (PlayerView wiring) → Task 3 (UI). Order matters: Task 2 reads `state.videoFillMode` which Task 1 must add first; Task 3 reads + writes the same field, which Task 2 turns into observable behavior.

**Risk:** None significant. The `AndroidView.update` lambda re-runs cleanly when captured state changes — well-trodden Compose pattern.
