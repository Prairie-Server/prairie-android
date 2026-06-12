# A.3d-hdr — HDR force-SDR toggle in HUD Video pane

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an HDR toggle to the HUD Video pane that mirrors Apple's `TVPlayerInfoHUD` HDR row. Bound to the existing per-profile `PlayerSettingsStore.hdrEnabledFlow` (already plumbed through `TvPlayerViewModel.hdrEnabled` but currently unconsumed by the player). When toggled off, drop Dolby Vision from the preferred video-MIME list so the selector picks an H.265/H.264 baselayer when available; toggle change re-runs the track-selection presets so it takes effect mid-playback.

**Honest Android constraint vs Apple:** Apple's `PlayerCore.setHDREnabled(false)` forces SDR rendering at the surface level (peak luminance 0; range coerced to `.sdr` — `PlayerCore.swift:1295,2452`). Android's Media3 surface has no equivalent — we can't ignore HDR metadata after a stream is selected. The behavioral approximation is **preference-driven track selection**: when `hdrEnabled = false`, deprioritize DV MIME so the selector chooses an SDR-tagged variant if one exists. For single-track HDR-only content there is no SDR alternative, and the toggle becomes a no-op for that file. This matches the spec's E.1 "honest framing" rule (match architecture, not implementation when the platforms differ structurally).

**Architecture:**
- `TrackSelectionPresets.buildTvParameters` gains an `allowHdr: Boolean` parameter (default `true` for source compatibility). `buildTvVideoMimePreferences` likewise gains `allowHdr: Boolean` and excludes `VIDEO_DOLBY_VISION` from the preferred-MIME list when `false`.
- `ContinuumPlayerFactory.applyTrackSelectionPresets` forwards the new `hdrEnabled: Boolean = true` flag to the preset builder.
- `TvPlayerScreen`'s `LaunchedEffect` that calls `applyTrackSelectionPresets` adds `hdrEnabled` to its key list so the presets re-apply when the user flips the toggle.
- HUD Video pane gets an "HDR" chip-style toggle row above the existing Fill mode row. Same visual pattern as `VideoFillModeChip` (focus-driven commit, white-on-focus). Bound to `viewModel.hdrEnabled` + a new `viewModel.onSetHdrEnabled` setter (the setter `onSetHdrEnabled` already exists at `TvPlayerViewModel.kt:696`).

**Tech stack:** Kotlin, Media3 1.10.0, existing per-profile DataStore. No new dependencies.

**Reference:**
- Spec section A.3, A.3d row, at `/opt/silo-android/docs/superpowers/specs/2026-05-23-android-tv-parity-rework-design.md`.
- Apple HUD toggle: `/opt/silo-apple/iosApp/iosApp/Screens/Player/tvOS/TVPlayerInfoHUD.swift:559` (`boolLabel(viewModel.settings.hdrEnabled)`).
- Apple core SDR coercion: `/opt/silo-apple/iosApp/iosApp/Screens/Player/CoreMedia/PlayerCore.swift:1295,2452` (informational — Android can't mirror surface-level SDR forcing).
- Current Android plumbing: `playerSettingsStore.hdrEnabledFlow` (read), `TvPlayerViewModel.hdrEnabled` (StateFlow), `TvPlayerViewModel.onSetHdrEnabled` (setter), `TrackSelectionPresets.buildTvVideoMimePreferences` (filter point — `TrackSelectionPresets.kt:116`).

**Testing posture:** Pure MIME-preference helper gets a unit test (one case for `allowHdr=true` keeps DV, one for `allowHdr=false` drops it). HUD chip + re-application verified manually on TV hardware.

---

### Task 1: `TrackSelectionPresets` — `allowHdr` parameter + test

**Files:**
- Modify: `/opt/silo-android/android-shared/src/androidMain/kotlin/com/continuum/app/common/player/TrackSelectionPresets.kt`
- Modify: `/opt/silo-android/android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/TrackSelectionPresetsFfmpegTest.kt` (or sibling test file — verify on first read) — add two cases for `buildTvVideoMimePreferences`.

**Why:** The preset is the only place where DV MIME enters the preferred list. Dropping it there is the smallest change that produces the user-visible effect.

- [ ] **Step 1: Extract `buildTvVideoMimePreferences` to take `allowHdr`**

```kotlin
internal fun buildTvVideoMimePreferences(
    displayHdr: HdrCapabilities,
    allowHdr: Boolean = true,
): List<String> {
    val mimes = mutableListOf<String>()
    if (allowHdr && displayHdr.dolbyVisionProfiles.isNotEmpty()) {
        mimes += MimeTypes.VIDEO_DOLBY_VISION
    }
    mimes += MimeTypes.VIDEO_H265
    mimes += MimeTypes.VIDEO_H264
    return mimes
}
```

Make the function `internal` (it's already at file scope) so it's reachable from `androidUnitTest`. Match the visibility convention of `buildTvAudioMimePreferences` directly above it.

- [ ] **Step 2: Thread `allowHdr` through `buildTvParameters`**

Add the parameter just after `displayHdr`:

```kotlin
fun buildTvParameters(
    context: Context,
    base: TrackSelectionParameters,
    audioCaps: AudioPassthroughCapabilities,
    displayHdr: HdrCapabilities,
    preferredAudioLanguage: String?,
    preferredTextLanguage: String?,
    allowHdr: Boolean = true,
    ffmpegAvailable: Boolean = FfmpegAudioSupport.isAvailable(),
): DefaultTrackSelector.Parameters {
    val audioMimes = buildTvAudioMimePreferences(audioCaps, ffmpegAvailable)
    val videoMimes = buildTvVideoMimePreferences(displayHdr, allowHdr)
    ...
}
```

Default `true` preserves the existing call-site behavior.

- [ ] **Step 3: Add unit-test cases**

Find the existing `TrackSelectionPresetsFfmpegTest` (or whichever test exercises the MIME helpers). Add two cases:

```kotlin
@Test
fun `buildTvVideoMimePreferences includes DV when display supports DV and HDR allowed`() {
    val mimes = TrackSelectionPresets.buildTvVideoMimePreferences(
        displayHdr = HdrCapabilities(dolbyVisionProfiles = setOf(5)),
        allowHdr = true,
    )
    assertEquals(listOf(MimeTypes.VIDEO_DOLBY_VISION, MimeTypes.VIDEO_H265, MimeTypes.VIDEO_H264), mimes)
}

@Test
fun `buildTvVideoMimePreferences drops DV when HDR disabled`() {
    val mimes = TrackSelectionPresets.buildTvVideoMimePreferences(
        displayHdr = HdrCapabilities(dolbyVisionProfiles = setOf(5)),
        allowHdr = false,
    )
    assertEquals(listOf(MimeTypes.VIDEO_H265, MimeTypes.VIDEO_H264), mimes)
}
```

Adjust `HdrCapabilities` constructor args to match the actual data-class shape (verify on first read).

- [ ] **Step 4: Build + test**

```bash
cd /opt/silo-android && ./gradlew :android-shared:compileDebugKotlin
cd /opt/silo-android && ./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.TrackSelectionPresets*"
```

- [ ] **Step 5: Commit**

```bash
git -C /opt/silo-android add \
  android-shared/src/androidMain/kotlin/com/continuum/app/common/player/TrackSelectionPresets.kt \
  android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "feat(player-track-selection): allowHdr param drops DV MIME when disabled (A.3d-hdr)

buildTvVideoMimePreferences gains an allowHdr flag; when false, the
Dolby Vision MIME is excluded from the preferred-MIME list so the
selector picks H.265/H.264 over DV when both are present on the
content. Defaults true; downstream callers wire the user's HDR
preference in the next commit.

Honest constraint: Android Media3 has no surface-level SDR forcing
equivalent to AVPlayer's setHDREnabled. The toggle is a track-selection
preference — for single-track HDR-only files there is no SDR variant
to fall back to and the toggle becomes a no-op for that file."
```

---

### Task 2: Wire `hdrEnabled` through `applyTrackSelectionPresets` + screen re-application

**Files:**
- Modify: `/opt/silo-android/android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlayerFactory.kt`
- Modify: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt`

**Why:** Connect the existing per-profile preference to the new preset parameter, and ensure the toggle takes effect mid-playback by re-running the preset bind whenever the value changes.

- [ ] **Step 1: Forward `hdrEnabled` through `applyTrackSelectionPresets`**

```kotlin
fun applyTrackSelectionPresets(
    player: Player,
    audioCaps: AudioPassthroughCapabilities,
    displayHdr: HdrCapabilities = HdrCapabilities(),
    preferredAudioLanguage: String? = null,
    preferredTextLanguage: String? = null,
    hdrEnabled: Boolean = true,
) {
    val base = player.trackSelectionParameters
    val next = if (isTv) {
        TrackSelectionPresets.buildTvParameters(
            context = context,
            base = base,
            audioCaps = audioCaps,
            displayHdr = displayHdr,
            preferredAudioLanguage = preferredAudioLanguage,
            preferredTextLanguage = preferredTextLanguage,
            allowHdr = hdrEnabled,
        )
    } else {
        TrackSelectionPresets.buildPhoneParameters(
            context = context,
            base = base,
            audioCaps = audioCaps,
            spatializerOn = audioCaps.spatializerEnabled,
            preferredAudioLanguage = preferredAudioLanguage,
            preferredTextLanguage = preferredTextLanguage,
        )
    }
    player.trackSelectionParameters = next
}
```

(Phone preset doesn't take `allowHdr` — phone playback has no DV preference today.)

- [ ] **Step 2: Add `hdrEnabled` to the screen's `LaunchedEffect` key list**

In `TvPlayerScreen.kt` around line 186:

```kotlin
val hdrEnabled by viewModel.hdrEnabled.collectAsState()

LaunchedEffect(mediaController, audioCaps, state.preferredAudioLanguage, state.preferredTextLanguage, hdrEnabled) {
    val controller = mediaController ?: return@LaunchedEffect
    playerFactory.applyTrackSelectionPresets(
        player = controller,
        audioCaps = audioCaps,
        displayHdr = displayHdr,
        preferredAudioLanguage = state.preferredAudioLanguage,
        preferredTextLanguage = state.preferredTextLanguage,
        hdrEnabled = hdrEnabled,
    )
}
```

The `hdrEnabled` `collectAsState` goes alongside `audioDelayMs` / `subtitleDelayMs` near `TvPlayerScreen.kt:114`.

- [ ] **Step 3: Build**

```bash
cd /opt/silo-android && ./gradlew :android-shared:compileDebugKotlin :androidTvApp:compileDebugKotlin
```

- [ ] **Step 4: Commit**

```bash
git -C /opt/silo-android add \
  android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlayerFactory.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "feat(tv-player): wire HDR preference into track-selection presets (A.3d-hdr)

applyTrackSelectionPresets gains an hdrEnabled flag (default true)
that forwards to buildTvParameters. TvPlayerScreen's LaunchedEffect
that re-applies presets now keys on hdrEnabled, so flipping the
toggle re-runs the bind and the new preference takes effect on the
already-mounted player.

HUD toggle UI lands in T3."
```

---

### Task 3: HUD Video pane HDR chip

**Files:**
- Modify: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt`
- Modify: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt`

**Why:** UI counterpart. Mirror Apple's HUD HDR row as a chip toggle in the existing Video pane (above Fill mode).

- [ ] **Step 1: Add `hdrEnabled` + `onHdrEnabledChanged` params to `TvPlayerHud`**

Insert just after `onVideoFillModeChanged` in the parameter list:

```kotlin
hdrEnabled: Boolean,
onHdrEnabledChanged: (Boolean) -> Unit,
```

- [ ] **Step 2: Extend `HudVideoPane` to render an HDR row above Fill mode**

```kotlin
@Composable
private fun HudVideoPane(
    hdrEnabled: Boolean,
    onHdrEnabledChanged: (Boolean) -> Unit,
    fillMode: VideoFillMode,
    onFillModeChanged: (VideoFillMode) -> Unit,
    videoTracks: List<PlayerTrackEntry>,
    onSelectVideo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = "HDR",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            HdrModeChip(
                label = "On",
                selected = hdrEnabled,
                onClick = { onHdrEnabledChanged(true) },
            )
            HdrModeChip(
                label = "Off",
                selected = !hdrEnabled,
                onClick = { onHdrEnabledChanged(false) },
            )
        }

        Text(
            text = "Fill mode",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            VideoFillMode.entries.forEach { mode ->
                VideoFillModeChip(
                    mode = mode,
                    selected = fillMode == mode,
                    onClick = { onFillModeChanged(mode) },
                )
            }
        }
        if (videoTracks.size > 1) {
            Text(
                text = "Video track",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Spacing.md),
            )
            HudPickerPane(
                options = videoTracks.map {
                    TrackOption(it.index, it.label, it.isSelected)
                },
                onSelect = onSelectVideo,
            )
        }
    }
}
```

Add `HdrModeChip` styled identically to `VideoFillModeChip` (white-on-focus, ghost when idle). Since the two chips are functionally identical except for label + commit action, the cleanest factoring is to extract the shared pill body into a `private fun HudOptionChip(label: String, selected: Boolean, onClick: () -> Unit)` helper and have `VideoFillModeChip` + `HdrModeChip` both call it. **But** keep this refactor in scope only if it stays tight — if extracting forces a third visual variant, leave both as parallel composables.

For first cut, prefer the helper:

```kotlin
@Composable
private fun HudOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        if (isFocused) onClick()
    }
    val bg = when {
        isFocused -> Color.White.copy(alpha = 0.94f)
        selected -> Color.White.copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.06f)
    }
    val fg = when {
        isFocused -> Color.Black
        selected -> Color.White
        else -> Color.White.copy(alpha = 0.72f)
    }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.0f else 0.96f,
        animationSpec = tween(120),
        label = "hudOptionChipScale",
    )

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(50))
            .background(bg)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun HdrModeChip(label: String, selected: Boolean, onClick: () -> Unit) =
    HudOptionChip(label, selected, onClick)

@Composable
private fun VideoFillModeChip(mode: VideoFillMode, selected: Boolean, onClick: () -> Unit) {
    val label = when (mode) {
        VideoFillMode.Fit -> "Letterbox"
        VideoFillMode.Zoom -> "Zoom (crop)"
    }
    HudOptionChip(label, selected, onClick)
}
```

The existing inline body of `VideoFillModeChip` collapses into the helper. Net delta: -1 chip duplicate, +1 helper, +1 HDR chip.

- [ ] **Step 3: Forward params from `TvPlayerHud` to `HudVideoPane`**

```kotlin
HudTab.Video -> HudVideoPane(
    hdrEnabled = hdrEnabled,
    onHdrEnabledChanged = onHdrEnabledChanged,
    fillMode = videoFillMode,
    onFillModeChanged = onVideoFillModeChanged,
    videoTracks = videoTracks,
    onSelectVideo = onSelectVideo,
)
```

- [ ] **Step 4: Wire from screen to HUD**

`TvPlayerScreen.kt` already has `val hdrEnabled by viewModel.hdrEnabled.collectAsState()` after T2 Step 2. Add to the `TvPlayerHud(...)` call site:

```kotlin
hdrEnabled = hdrEnabled,
onHdrEnabledChanged = viewModel::onSetHdrEnabled,
```

`viewModel.onSetHdrEnabled` already exists at `TvPlayerViewModel.kt:696`. No ViewModel change needed.

- [ ] **Step 5: Build**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:compileDebugKotlin
```

- [ ] **Step 6: Commit**

```bash
git -C /opt/silo-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "feat(tv-player): HDR toggle in HUD Video pane (A.3d-hdr)

Mirrors Apple's TVPlayerInfoHUD HDR row: On/Off chip pair above the
existing Fill mode chips in the Video pane. Bound to the existing
per-profile playerSettingsStore.hdrEnabledFlow via TvPlayerViewModel
.onSetHdrEnabled (already present, was unconsumed by the HUD).

Chip styling factored to HudOptionChip so HDR + Fill mode share the
same focus-driven pill (white-on-focus, ghost idle, 0.96→1.0 scale).
VideoFillModeChip collapses into the helper.

Toggle flip causes the screen's track-selection-presets LaunchedEffect
to re-run (A.3d-hdr T2), so the new preference takes effect on the
currently-mounted player."
```

---

## Self-Review

**Spec coverage** (A.3d row in spec status table):
- HDR toggle in HUD Video pane → T3 ✓
- Track-selector reconfig → T1 + T2 ✓
- Per-profile preference (already exists) → reused ✓

**Placeholder scan:** No "TBD." Two verification gates flagged in-task: (1) `HdrCapabilities` data-class shape for the test cases, (2) the actual test-file name for the MIME helpers (the FfmpegTest naming suggests it exists but the video-side helper might live in a sibling).

**Sequencing:** T1 (preset signature + tests) → T2 (factory + screen wiring) → T3 (UI chip). Each independently buildable; T2 is a behavioral no-op until T3 surfaces the flip path.

**Risk:** For single-track HDR-only content there is no SDR alternative — toggle becomes a per-file no-op. Documented in T1's commit message. The mitigation requires either server-side SDR transcode (out of scope) or surface-level tonemap (not available on Media3 1.10). This is honest framing per the spec's E.1 rule, not a defect.

**What this plan does NOT cover:**
- `HdrDisplayController` changes — the current controller picks display modes by resolution + refresh rate, not HDR mode, so it doesn't need to respect `hdrEnabled`. (If a future revision starts requesting HDR display modes explicitly via API 34+ `Display.Mode.getSupportedHdrTypes`, the toggle would need to gate that path too.)
- Phone HUD — phone player has no HDR-mode HDMI handshake to avoid; A.3d-hdr is TV-only.
- Server-side SDR transcode opt-in — out of scope; handled by version selection on detail screen.
