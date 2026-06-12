# A.3c — Stats pane wiring via existing PlaybackAnalyticsListener

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Populate the HUD's Stats pane with live data (bitrate, codec, resolution, frame rate, HDR mode, decoder names, dropped frames) by consuming the existing `PlaybackAnalyticsListener.events` SharedFlow from `TvPlayerViewModel`, then rendering the snapshot in the Stats pane.

**Architecture:** The plumbing is already in place — `PlaybackAnalyticsListener` (in `android-shared`) emits `Event` values to a `SharedFlow` and is attached to the player in `ContinuumPlaybackService`. The ViewModel injects the same singleton (via Koin) and reduces events into a `PlayerStatsSnapshot` exposed on `UiState`. The Stats pane renders the snapshot or stays as empty-state if null.

**Tech stack:** Kotlin 2.1.20, Compose-for-TV 1.0.1, existing `PlaybackAnalyticsListener` infrastructure, Koin DI.

**Reference:** Spec section A.3 at `/opt/silo-android/docs/superpowers/specs/2026-05-23-android-tv-parity-rework-design.md`. Audit confirmed `PlaybackAnalyticsListener` already exists with `events: SharedFlow<Event>` exposing VideoDecoderInitialized, AudioDecoderInitialized, VideoFormatChanged, AudioFormatChanged, DroppedFrames, AudioUnderrun, LoadError, BandwidthEstimate.

**Testing posture:** Per `AGENTS.md`, focused tests for non-trivial logic. The event reducer (event → snapshot mutation) is non-trivial enough to warrant a small unit test (Task 1). Pane UI is verified manually.

---

### Task 1: Add `PlayerStatsSnapshot` + collect events in ViewModel

**Files:**
- Modify: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerViewModel.kt`
- Create: `/opt/silo-android/androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/PlayerStatsSnapshotReducerTest.kt`

**Why:** The ViewModel becomes the integration point: subscribe to `PlaybackAnalyticsListener.events`, reduce each event into a running snapshot, expose on `UiState`.

- [ ] **Step 1: Read the ViewModel's current shape**

```bash
sed -n '1,160p' /opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerViewModel.kt
```

Note:
- Constructor injects `PlaybackCapabilityDetector` already (line 79 per audit). Add a similar injection for `PlaybackAnalyticsListener`.
- `UiState` is around lines 106–157.
- The ViewModel runs inside Koin (per the grep — `koinViewModel()` callsite in screen).

- [ ] **Step 2: Add `PlayerStatsSnapshot` data class + a pure reducer function**

Inside `TvPlayerViewModel.kt` (or in a sibling file if you prefer separation; same-file is fine for a private data class), add:

```kotlin
/**
 * Snapshot of player statistics surfaced in the HUD's Stats pane.
 * Built by [reducePlayerStats] from a stream of [PlaybackAnalyticsListener.Event]s.
 *
 * All fields nullable — fields populate as events arrive; rendering should
 * tolerate any subset being null.
 */
data class PlayerStatsSnapshot(
    val videoDecoderName: String? = null,
    val audioDecoderName: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val resolution: String? = null,            // e.g. "1920x1080"
    val frameRate: Float? = null,
    val hdrMode: String? = null,               // e.g. "Dolby Vision", "HDR10", "SDR"
    val bitrateBps: Long? = null,
    val droppedFrames: Int = 0,                // cumulative since session start
    val audioUnderruns: Int = 0,               // cumulative
)

/**
 * Pure event-to-snapshot reducer. Used by the ViewModel; tested in isolation.
 * Does NOT clear state on unrelated events (e.g. a DroppedFrames event leaves
 * format/decoder fields untouched).
 */
internal fun reducePlayerStats(
    current: PlayerStatsSnapshot,
    event: PlaybackAnalyticsListener.Event,
): PlayerStatsSnapshot = when (event) {
    is PlaybackAnalyticsListener.Event.VideoDecoderInitialized ->
        current.copy(videoDecoderName = event.decoderName)
    is PlaybackAnalyticsListener.Event.AudioDecoderInitialized ->
        current.copy(audioDecoderName = event.decoderName)
    is PlaybackAnalyticsListener.Event.VideoFormatChanged -> current.copy(
        videoCodec = event.format.codecs ?: event.format.sampleMimeType,
        resolution = if (event.format.width > 0 && event.format.height > 0) {
            "${event.format.width}x${event.format.height}"
        } else current.resolution,
        frameRate = if (event.format.frameRate > 0f) event.format.frameRate else current.frameRate,
        hdrMode = describeHdrMode(event.format) ?: current.hdrMode,
    )
    is PlaybackAnalyticsListener.Event.AudioFormatChanged ->
        current.copy(audioCodec = event.format.codecs ?: event.format.sampleMimeType)
    is PlaybackAnalyticsListener.Event.DroppedFrames ->
        current.copy(droppedFrames = current.droppedFrames + event.count)
    is PlaybackAnalyticsListener.Event.AudioUnderrun ->
        current.copy(audioUnderruns = current.audioUnderruns + 1)
    is PlaybackAnalyticsListener.Event.BandwidthEstimate ->
        current.copy(bitrateBps = event.bitrateBps)
    is PlaybackAnalyticsListener.Event.LoadError ->
        current // load errors don't mutate the stats snapshot
}

private fun describeHdrMode(format: androidx.media3.common.Format): String? {
    val colorInfo = format.colorInfo ?: return null
    return when (colorInfo.colorTransfer) {
        androidx.media3.common.C.COLOR_TRANSFER_ST2084 -> "HDR10"
        androidx.media3.common.C.COLOR_TRANSFER_HLG -> "HLG"
        androidx.media3.common.C.COLOR_TRANSFER_SDR -> "SDR"
        else -> null
    }
}
```

Note: Dolby Vision detection in Media3 is via `Format.codecs` (e.g. `dvh1`, `dvhe`); the `describeHdrMode` above covers HDR10 / HLG / SDR. Add a DV branch if needed:

```kotlin
val codecs = format.codecs.orEmpty()
if (codecs.contains("dvh", ignoreCase = true) || codecs.contains("dvhe", ignoreCase = true)) {
    return "Dolby Vision"
}
```

Place this DV check at the top of `describeHdrMode`, before the `colorTransfer` switch.

- [ ] **Step 3: Inject `PlaybackAnalyticsListener` into the ViewModel + collect events**

Add to the ViewModel's constructor parameters (alongside the existing `PlaybackCapabilityDetector`):

```kotlin
private val playbackAnalytics: PlaybackAnalyticsListener,
```

In the ViewModel's `init` block (or wherever state collection is set up), add:

```kotlin
    init {
        // … existing init code …

        viewModelScope.launch {
            playbackAnalytics.events.collect { event ->
                _uiState.update { it.copy(stats = reducePlayerStats(it.stats, event)) }
            }
        }
    }
```

Adapt to the existing state-update pattern in this file — if `_uiState` is `MutableStateFlow`, use `.update { … }`; if it's manipulated via a `setX()` helper, follow that convention. Match local style.

- [ ] **Step 4: Add `stats: PlayerStatsSnapshot` to `UiState`**

In `UiState` (around lines 106–157), add:

```kotlin
    val stats: PlayerStatsSnapshot = PlayerStatsSnapshot(),
```

Default to an empty snapshot so the Stats pane always has a non-null instance to read.

- [ ] **Step 5: Wire Koin to provide `PlaybackAnalyticsListener` to the ViewModel**

The listener is registered as a singleton in `android-shared`'s `PlayerModule.kt:33` (`single { PlaybackAnalyticsListener() }`). The ViewModel's Koin module just needs to pull it via `get()`. Open the relevant module — likely `androidTvApp/.../di/AndroidTvModule.kt`. Find where `TvPlayerViewModel` is declared (likely `viewModel { TvPlayerViewModel(..., get()) }`) and add a `get()` for the new param.

- [ ] **Step 6: Add the import for `PlaybackAnalyticsListener` in ViewModel**

```kotlin
import com.continuum.app.common.player.PlaybackAnalyticsListener
```

(Plus `import kotlinx.coroutines.flow.update` if not already there.)

- [ ] **Step 7: Add unit tests for the reducer**

Create `/opt/silo-android/androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/PlayerStatsSnapshotReducerTest.kt`:

```kotlin
package com.continuum.app.tv.ui.screens.player

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import com.continuum.app.common.player.PlaybackAnalyticsListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerStatsSnapshotReducerTest {

    @Test
    fun `VideoFormatChanged fills resolution codec frame rate and hdr`() {
        val format = Format.Builder()
            .setSampleMimeType("video/avc")
            .setCodecs("avc1.640028")
            .setWidth(1920).setHeight(1080)
            .setFrameRate(23.976f)
            .setColorInfo(
                ColorInfo.Builder()
                    .setColorTransfer(C.COLOR_TRANSFER_ST2084)
                    .build(),
            )
            .build()
        val result = reducePlayerStats(
            PlayerStatsSnapshot(),
            PlaybackAnalyticsListener.Event.VideoFormatChanged(format),
        )
        assertEquals("avc1.640028", result.videoCodec)
        assertEquals("1920x1080", result.resolution)
        assertEquals(23.976f, result.frameRate)
        assertEquals("HDR10", result.hdrMode)
    }

    @Test
    fun `DroppedFrames accumulates across events`() {
        val initial = PlayerStatsSnapshot(droppedFrames = 3)
        val result = reducePlayerStats(
            initial,
            PlaybackAnalyticsListener.Event.DroppedFrames(count = 2, elapsedMs = 100L),
        )
        assertEquals(5, result.droppedFrames)
    }

    @Test
    fun `BandwidthEstimate updates bitrateBps`() {
        val result = reducePlayerStats(
            PlayerStatsSnapshot(),
            PlaybackAnalyticsListener.Event.BandwidthEstimate(bitrateBps = 5_000_000L),
        )
        assertEquals(5_000_000L, result.bitrateBps)
    }

    @Test
    fun `LoadError leaves snapshot unchanged`() {
        val initial = PlayerStatsSnapshot(droppedFrames = 7, bitrateBps = 1_000L)
        val result = reducePlayerStats(
            initial,
            PlaybackAnalyticsListener.Event.LoadError(IllegalStateException("test")),
        )
        assertEquals(initial, result)
    }

    @Test
    fun `Dolby Vision codec produces 'Dolby Vision' HDR mode`() {
        val format = Format.Builder()
            .setSampleMimeType("video/dolby-vision")
            .setCodecs("dvhe.05.06")
            .setWidth(3840).setHeight(2160)
            .build()
        val result = reducePlayerStats(
            PlayerStatsSnapshot(),
            PlaybackAnalyticsListener.Event.VideoFormatChanged(format),
        )
        assertEquals("Dolby Vision", result.hdrMode)
    }

    @Test
    fun `AudioUnderrun increments counter`() {
        val initial = PlayerStatsSnapshot(audioUnderruns = 2)
        val result = reducePlayerStats(initial, PlaybackAnalyticsListener.Event.AudioUnderrun)
        assertEquals(3, result.audioUnderruns)
    }
}
```

Verify the imports for `Format.Builder`, `ColorInfo`, and `C` against the actual Media3 1.10.0 API — the API has been stable for these since 1.0.

- [ ] **Step 8: Build + run tests**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:compileDebugKotlin
cd /opt/silo-android && ./gradlew :androidTvApp:testDebugUnitTest --tests "com.continuum.app.tv.ui.screens.player.PlayerStatsSnapshotReducerTest"
```

Expected: BUILD SUCCESSFUL + 6 tests pass.

If `LoadError`'s constructor takes a `Throwable` rather than `IOException`, adapt the test. If `Event` is `sealed class` rather than `sealed interface`, adapt construction syntax.

- [ ] **Step 9: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerViewModel.kt \
  androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/player/PlayerStatsSnapshotReducerTest.kt

# Add the DI module too if you modified it (usually AndroidTvModule.kt)
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt 2>/dev/null || true

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "feat(tv-player): collect player analytics into PlayerStatsSnapshot (A.3c)

ViewModel now injects PlaybackAnalyticsListener and reduces its
SharedFlow<Event> into a PlayerStatsSnapshot exposed on UiState.
Snapshot tracks decoder names, codecs, resolution, frame rate, HDR
mode (HDR10/HLG/SDR/Dolby Vision), bitrate, and cumulative dropped
frames + audio underruns.

Pure reducer (reducePlayerStats) tested in isolation — 6 cases
covering format mutations, accumulation, ignore-by-design events.

Stats pane consumes the snapshot in the next commit."
```

---

### Task 2: Render the snapshot in HUD Stats pane

**Files:**
- Modify: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt`

**Why:** The Stats pane currently shows `HudEmptyStatePane("Stats unavailable")`. Replace with a real renderer when the snapshot has at least one populated field, falling back to the empty state otherwise.

- [ ] **Step 1: Read the current Stats pane branch in `TvPlayerHud.kt`**

```bash
sed -n '140,200p' /opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt
```

Find the `HudTab.Stats -> HudEmptyStatePane("Stats unavailable")` branch (added in A.3a).

- [ ] **Step 2: Add `HudStatsPane` private composable**

In `TvPlayerHud.kt`, add a new private composable:

```kotlin
@Composable
private fun HudStatsPane(stats: PlayerStatsSnapshot, modifier: Modifier = Modifier) {
    val rows = buildList {
        stats.videoCodec?.let { add("Video codec" to it) }
        stats.resolution?.let { add("Resolution" to it) }
        stats.frameRate?.let { add("Frame rate" to "%.3f fps".format(it)) }
        stats.hdrMode?.let { add("HDR mode" to it) }
        stats.videoDecoderName?.let { add("Video decoder" to it) }
        stats.audioCodec?.let { add("Audio codec" to it) }
        stats.audioDecoderName?.let { add("Audio decoder" to it) }
        stats.bitrateBps?.let { add("Bitrate" to formatBitrate(it)) }
        if (stats.droppedFrames > 0) add("Dropped frames" to stats.droppedFrames.toString())
        if (stats.audioUnderruns > 0) add("Audio underruns" to stats.audioUnderruns.toString())
    }

    if (rows.isEmpty()) {
        HudEmptyStatePane("Stats unavailable", modifier)
        return
    }

    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        rows.forEach { (label, value) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun formatBitrate(bps: Long): String = when {
    bps >= 1_000_000 -> "%.1f Mbps".format(bps / 1_000_000.0)
    bps >= 1_000 -> "%.0f Kbps".format(bps / 1_000.0)
    else -> "$bps bps"
}
```

Match imports on `Column`, `Row`, `Arrangement`, `Spacing`, `MaterialTheme`, `Text`. Most should already be in this file.

- [ ] **Step 3: Replace the Stats branch in the `when (selectedTab)` block**

Change:

```kotlin
HudTab.Stats -> HudEmptyStatePane("Stats unavailable")
```

To:

```kotlin
HudTab.Stats -> HudStatsPane(state.stats)
```

(`state` is the existing `TvPlayerUiState` passed to the HUD. If the HUD doesn't currently receive `stats` field on `state`, it does after A.3c Task 1 — verify the parameter list of `TvPlayerHud` and the call site in `TvPlayerScreen`.)

- [ ] **Step 4: Build**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "feat(tv-player): HUD Stats pane renders live PlayerStatsSnapshot (A.3c)

Stats pane now shows codec, resolution, frame rate, HDR mode, decoder
names, bitrate, and (when non-zero) cumulative dropped frames and
audio underruns. Falls back to 'Stats unavailable' empty-state when
no fields are populated (e.g. before the first format event arrives).

Bitrate is formatted as Mbps/Kbps/bps; frame rate as 3-decimal fps
to surface 23.976 vs 24.000 distinctions that matter for refresh-rate
matching."
```

---

## Self-Review

**Spec coverage** (against A.3 Stats requirements):
- "Stats: current bitrate, dropped frames, decoder name, resolution, HDR mode (from `PlaybackCapabilityDetector` + Media3 `Format`)" → Task 1 (snapshot) + Task 2 (render) ✓
- Spec mentioned `PlaybackCapabilityDetector` as the data source but the actual signal-rich source is `PlaybackAnalyticsListener`. The capability detector is for device-level facts (can this TV decode HDR10? Does the AVR support Atmos?), not session-level facts (what's the current bitrate?). Using the listener is the right call; documented in the commit message implicitly.

**Placeholder scan:** No "TBD." The DV detection note is concrete.

**Type consistency:** `PlayerStatsSnapshot` referenced identically across both tasks. `reducePlayerStats` is `internal` so the test can call it. `state.stats` access path consistent.

**Sequencing:** Task 1 (data) → Task 2 (render). Order matters: Task 2 reads `state.stats` which must exist first.

**Risk:** `Format.codecs` is nullable in Media3. The reducer falls back to `sampleMimeType` if `codecs` is null. The DV detection should run before the `colorTransfer` check because DV bitstreams can have varying color transfers and Apple's reference treats DV as its own mode.
