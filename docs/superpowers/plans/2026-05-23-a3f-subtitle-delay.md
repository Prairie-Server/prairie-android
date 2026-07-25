# A.3f — Subtitle delay slider (symmetric to E's audio delay)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Working subtitle delay (±500ms) in the HUD Subtitles pane. Mirrors E's audio delay flow but uses subtitle parser cue-offset rather than an audio processor. Reuses the existing `PlayerSettingsStore.subtitleSyncMsFlow` per-profile preference (already ±500ms-coerced).

**Architecture:**
- New `OffsetSubtitleParserFactory` wraps `DefaultSubtitleParserFactory` and delegates each parser call to an `OffsetSubtitleParser` that adds a configurable Long microsecond offset to every `CuesWithTiming.startTimeUs`/`durationUs`. Offset is read from a Koin-singleton `SubtitleOffsetMs` holder (the same pattern as `DelayAudioProcessor`).
- `ContinuumPlayerFactory.createPlayer()` uses the wrapper instead of the plain default factory.
- `ContinuumPlaybackService` subscribes to `subtitleSyncMsFlow` (already exists) and pushes each change into the offset holder + forces `seekTo(currentPosition)` to drop buffered cues so the new offset takes effect.
- HUD Subtitles pane: add the same 5-button stepper (`−50 / −10 / Reset / +10 / +50`) bound to `setSubtitleSyncMs`. Existing track-picker behavior preserved.

**Tech stack:** Kotlin, Media3 1.10.0, existing per-profile DataStore.

**Reference:**
- Spec section A.3 (subtitles delay) at `/opt/prairie-android/docs/superpowers/specs/2026-05-23-android-tv-parity-rework-design.md`.
- E plan for the analogous audio-delay pattern: `/opt/prairie-android/docs/superpowers/plans/2026-05-23-e-player-route-matrix.md`.
- `PlayerSettingsStore.subtitleSyncMsFlow` already exists (`AndroidPlayerSettingsStore.kt:160-161`; setter at `:214-215`).
- `ContinuumPlayerFactory.kt:63` currently sets `DefaultSubtitleParserFactory()` — that's the swap point.

**Testing posture:** Pure offset helper gets a unit test; UI verified manually.

---

### Task 1: `SubtitleOffsetHolder` + `OffsetSubtitleParserFactory` + tests

**Files:**
- Create: `/opt/prairie-android/android-shared/src/androidMain/kotlin/com/continuum/app/common/player/subtitle/SubtitleOffsetHolder.kt`
- Create: `/opt/prairie-android/android-shared/src/androidMain/kotlin/com/continuum/app/common/player/subtitle/OffsetSubtitleParserFactory.kt`
- Create: `/opt/prairie-android/android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/subtitle/SubtitleOffsetHolderTest.kt`
- Modify: `/opt/prairie-android/android-shared/src/androidMain/kotlin/com/continuum/app/common/di/PlayerModule.kt` (Koin reg for holder)

**Why:** Hold the current offset in a shared object so the parser can read it without recreation on every change. Factory wraps the default factory so all subtitle formats (SRT, WebVTT, SSA, etc.) get the offset.

- [ ] **Step 1: Create `SubtitleOffsetHolder.kt`**

```kotlin
package com.continuum.app.common.player.subtitle

import java.util.concurrent.atomic.AtomicLong

/**
 * Shared mutable container for the current subtitle offset in microseconds.
 * Read by [OffsetSubtitleParserFactory] on each cue; mutated by
 * `ContinuumPlaybackService` from the per-profile `subtitleSyncMsFlow`.
 *
 * Positive offset → cues appear later (delay subtitles).
 * Negative offset → cues appear earlier (advance subtitles).
 * Range is clamped to ±500ms by the `setSubtitleSyncMs` setter; this
 * holder doesn't re-clamp.
 */
class SubtitleOffsetHolder {
    private val offsetUs = AtomicLong(0L)

    fun setOffsetMs(ms: Int) {
        offsetUs.set(ms.toLong() * 1_000L)
    }

    fun getOffsetUs(): Long = offsetUs.get()
    fun getOffsetMs(): Int = (offsetUs.get() / 1_000L).toInt()
}
```

- [ ] **Step 2: Create `OffsetSubtitleParserFactory.kt`**

```kotlin
package com.continuum.app.common.player.subtitle

import androidx.media3.common.Format
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.text.SubtitleParser.OutputOptions

/**
 * Wraps [DefaultSubtitleParserFactory] so every emitted [CuesWithTiming]
 * gets [SubtitleOffsetHolder.getOffsetUs] added to its `startTimeUs`.
 * Reads the live offset on each emission — no need to recreate the parser
 * when the offset changes.
 */
@UnstableApi
class OffsetSubtitleParserFactory(
    private val holder: SubtitleOffsetHolder,
) : SubtitleParser.Factory {

    private val delegate = DefaultSubtitleParserFactory()

    override fun supportsFormat(format: Format): Boolean = delegate.supportsFormat(format)

    override fun getCueReplacementBehavior(format: Format): Int =
        delegate.getCueReplacementBehavior(format)

    override fun create(format: Format): SubtitleParser =
        OffsetSubtitleParser(delegate.create(format), holder)
}

@UnstableApi
private class OffsetSubtitleParser(
    private val delegate: SubtitleParser,
    private val holder: SubtitleOffsetHolder,
) : SubtitleParser {

    override fun getCueReplacementBehavior(): Int = delegate.cueReplacementBehavior

    override fun parse(
        data: ByteArray,
        offset: Int,
        length: Int,
        outputOptions: OutputOptions,
        output: Consumer<CuesWithTiming>,
    ) {
        val offsetUs = holder.getOffsetUs()
        val shifted = Consumer<CuesWithTiming> { cues ->
            output.accept(
                CuesWithTiming(
                    cues.cues,
                    (cues.startTimeUs + offsetUs).coerceAtLeast(0L),
                    cues.durationUs,
                )
            )
        }
        delegate.parse(data, offset, length, outputOptions, shifted)
    }
}
```

Notes on the API:
- `SubtitleParser.Factory` interface in Media3 1.10.0 typically exposes `supportsFormat`, `getCueReplacementBehavior`, and `create`. Verify the exact set on first build — if methods differ (added/removed), adapt the delegate forwarding.
- `CuesWithTiming` is the per-cue-group payload with `cues: List<Cue>`, `startTimeUs: Long`, `durationUs: Long`. Constructor may be `(cues, startTimeUs, durationUs)` or take the trio in a different order — match the actual signature.
- `Consumer<CuesWithTiming>` is `androidx.media3.common.util.Consumer`, a Java-style consumer accepting one arg.

If `SubtitleParser` is `sealed` or `final` in 1.10.0, fall back to using composition (own a wrapper that delegates) rather than implementation; or override only the methods that exist.

- [ ] **Step 3: Create the holder test**

```kotlin
package com.continuum.app.common.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleOffsetHolderTest {

    @Test
    fun `default offset is zero`() {
        val h = SubtitleOffsetHolder()
        assertEquals(0L, h.getOffsetUs())
        assertEquals(0, h.getOffsetMs())
    }

    @Test
    fun `setOffsetMs stores microseconds`() {
        val h = SubtitleOffsetHolder()
        h.setOffsetMs(250)
        assertEquals(250_000L, h.getOffsetUs())
        assertEquals(250, h.getOffsetMs())
    }

    @Test
    fun `negative offset preserves sign`() {
        val h = SubtitleOffsetHolder()
        h.setOffsetMs(-100)
        assertEquals(-100_000L, h.getOffsetUs())
        assertEquals(-100, h.getOffsetMs())
    }

    @Test
    fun `setOffsetMs is idempotent`() {
        val h = SubtitleOffsetHolder()
        h.setOffsetMs(42)
        h.setOffsetMs(42)
        assertEquals(42_000L, h.getOffsetUs())
    }
}
```

- [ ] **Step 4: Koin registration**

In `/opt/prairie-android/android-shared/src/androidMain/kotlin/com/continuum/app/common/di/PlayerModule.kt`:

```kotlin
import com.continuum.app.common.player.subtitle.SubtitleOffsetHolder

// inside module { ... } near DelayAudioProcessor:
single { SubtitleOffsetHolder() }
```

- [ ] **Step 5: Build + run tests**

```bash
cd /opt/prairie-android && ./gradlew :android-shared:compileDebugKotlin
cd /opt/prairie-android && ./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.subtitle.SubtitleOffsetHolderTest"
```

Expected: BUILD SUCCESSFUL + 4 tests pass.

- [ ] **Step 6: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android add \
  android-shared/src/androidMain/kotlin/com/continuum/app/common/player/subtitle/ \
  android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/subtitle/ \
  android-shared/src/androidMain/kotlin/com/continuum/app/common/di/PlayerModule.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android commit -m "feat(player-subtitle): SubtitleOffsetHolder + OffsetSubtitleParserFactory (A.3f)

Shared holder (AtomicLong) stores the current subtitle offset in
microseconds. OffsetSubtitleParserFactory wraps Media3's
DefaultSubtitleParserFactory and shifts each CuesWithTiming.startTimeUs
by the holder's current value at parse time.

Hot-readable — no parser recreation needed on offset change. The
factory swap into ContinuumPlayerFactory + service binding land in
the next commit."
```

---

### Task 2: Wire `OffsetSubtitleParserFactory` into `ContinuumPlayerFactory` + bind `subtitleSyncMsFlow`

**Files:**
- Modify: `/opt/prairie-android/android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlayerFactory.kt`
- Modify: `/opt/prairie-android/android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlaybackService.kt`

**Why:** Replace the plain `DefaultSubtitleParserFactory()` with the wrapper. Add a flow collector mirroring E's audio-sync binding.

- [ ] **Step 1: Inject holder into factory + swap parser factory**

In `ContinuumPlayerFactory.kt`:

```kotlin
import com.continuum.app.common.player.subtitle.OffsetSubtitleParserFactory
import com.continuum.app.common.player.subtitle.SubtitleOffsetHolder

class ContinuumPlayerFactory(
    private val context: Context,
    private val tokenManager: TokenManager,
    private val subtitleManager: SubtitleManager,
    okHttpClient: okhttp3.OkHttpClient,
    private val delayProcessor: DelayAudioProcessor,
    private val subtitleOffsetHolder: SubtitleOffsetHolder,  // NEW
)
```

In `createPlayer()`, change the `extractorsFactory` setup from:

```kotlin
.setSubtitleParserFactory(DefaultSubtitleParserFactory())
```

To:

```kotlin
.setSubtitleParserFactory(OffsetSubtitleParserFactory(subtitleOffsetHolder))
```

- [ ] **Step 2: Update Koin in both AndroidModule + AndroidTvModule**

Per E T3's discovery, factory registration lives in per-app modules. In `AndroidModule.kt` and `AndroidTvModule.kt`, add a `get()` for the new `SubtitleOffsetHolder` dependency.

- [ ] **Step 3: Bind `subtitleSyncMsFlow` in `ContinuumPlaybackService`**

After the audio-sync collector added in E T3:

```kotlin
private val subtitleOffsetHolder: SubtitleOffsetHolder by inject()
private var subtitleSyncJob: Job? = null

// inside onCreate(), alongside the existing audio-sync job:
subtitleSyncJob = scope.launch {
    playerSettingsStore.subtitleSyncMsFlow
        .distinctUntilChanged()
        .collect { offsetMs ->
            val previous = subtitleOffsetHolder.getOffsetMs()
            subtitleOffsetHolder.setOffsetMs(offsetMs)
            if (previous != offsetMs && player.isPlaying) {
                player.seekTo(player.currentPosition)
            }
        }
}

// cancel in onDestroy():
subtitleSyncJob?.cancel()
```

- [ ] **Step 4: Build**

```bash
cd /opt/prairie-android && ./gradlew :android-shared:compileDebugKotlin :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android add \
  android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlayerFactory.kt \
  android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlaybackService.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android commit -m "feat(player-subtitle): wire OffsetSubtitleParserFactory + bind sync flow (A.3f)

ContinuumPlayerFactory's extractors now use OffsetSubtitleParserFactory
instead of the bare DefaultSubtitleParserFactory. ContinuumPlaybackService
collects PlayerSettingsStore.subtitleSyncMsFlow (per-profile, ±500ms
coerced) and pushes each change into subtitleOffsetHolder; if the value
changed and the player is playing, seekTo(currentPosition) drops the
buffered cues so the new offset takes effect on the next parse.

HUD slider lands in T3."
```

---

### Task 3: HUD Subtitles pane delay stepper

**Files:**
- Modify: `/opt/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerViewModel.kt`
- Modify: `/opt/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt`
- Modify: `/opt/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt`

**Why:** UI counterpart. Mirror the E T4 audio stepper but for subtitles. Subtitles already render a picker (per A.3a); add a delay row below it.

- [ ] **Step 1: Expose subtitle delay in ViewModel**

```kotlin
val subtitleDelayMs: StateFlow<Int> = playerSettingsStore.subtitleSyncMsFlow
    .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

fun onSubtitleDelayChanged(delayMs: Int) {
    viewModelScope.launch {
        playerSettingsStore.setSubtitleSyncMs(delayMs)
    }
}
```

- [ ] **Step 2: Create `HudSubtitlesPane` mirroring `HudAudioPane`**

In `TvPlayerHud.kt`:

```kotlin
@Composable
private fun HudSubtitlesPane(
    subtitleTracks: List<TrackOption>,
    onSelectSubtitle: (Int) -> Unit,
    subtitleDelayMs: Int,
    onSubtitleDelayChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = "Subtitle track",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        HudPickerPane(
            options = subtitleTracks,
            onSelect = onSelectSubtitle,
        )

        Text(
            text = "Subtitle delay: ${subtitleDelayMs} ms",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        DelayStepperRow(
            valueMs = subtitleDelayMs,
            onChange = onSubtitleDelayChanged,
        )
    }
}
```

Where `DelayStepperRow` should be extracted as a sibling reusable composable (audio + subtitles share the same 5-button row pattern). If it doesn't already exist, factor out from the existing `HudAudioDelayRow` introduced in E T4:

```kotlin
@Composable
private fun DelayStepperRow(
    valueMs: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        DelayStepperButton("−50", onClick = { onChange((valueMs - 50).coerceIn(-500, 500)) })
        DelayStepperButton("−10", onClick = { onChange((valueMs - 10).coerceIn(-500, 500)) })
        DelayStepperButton("Reset", onClick = { onChange(0) })
        DelayStepperButton("+10", onClick = { onChange((valueMs + 10).coerceIn(-500, 500)) })
        DelayStepperButton("+50", onClick = { onChange((valueMs + 50).coerceIn(-500, 500)) })
    }
}
```

(If `HudAudioDelayRow` already exists from E T4, refactor it to `DelayStepperRow` and have `HudAudioPane` use the same reusable. Single source of truth for the stepper row.)

Update the `HudTab.Subtitles -> ...` branch in the `when` to call `HudSubtitlesPane(...)`. Pass through `subtitleDelayMs` + `onSubtitleDelayChanged` from new `TvPlayerHud` signature params.

- [ ] **Step 3: Update call-site in `TvPlayerScreen.kt`**

```kotlin
val subtitleDelayMs by viewModel.subtitleDelayMs.collectAsState()

TvPlayerHud(
    // ... existing args
    subtitleDelayMs = subtitleDelayMs,
    onSubtitleDelayChanged = viewModel::onSubtitleDelayChanged,
)
```

- [ ] **Step 4: Build**

```bash
cd /opt/prairie-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerViewModel.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android commit -m "feat(tv-player): subtitle delay stepper in HUD Subtitles pane (A.3f)

Mirrors the audio delay UI (E T4): 5-button stepper bound to
per-profile PlayerSettingsStore.subtitleSyncMsFlow. The flow is
collected by ContinuumPlaybackService (A.3f T2) which pushes each
value into the SubtitleOffsetHolder; OffsetSubtitleParserFactory
reads it at every cue parse so new cues come out time-shifted.

DelayStepperRow + DelayStepperButton extracted from HudAudioPane so
both audio + subtitles share a single source of truth for the
stepper UI."
```

---

## Self-Review

**Spec coverage** (A.3f line in spec):
- Subtitle delay slider in HUD Subtitles pane → T3 ✓
- Per-profile preference (already exists as `SubtitleSyncMs`) → reused ✓
- Player honors the value → T1 + T2 ✓

**Placeholder scan:** No "TBD." Two API verification gates (SubtitleParser.Factory signature, CuesWithTiming constructor) flagged in-task.

**Sequencing:** T1 (holder + parser wrapper + tests) → T2 (factory wiring + service binding) → T3 (UI stepper). Each independently buildable.

**Risk:** T2's parser-factory swap could surface unexpected Media3 behavior — the `DefaultSubtitleParserFactory` may have internal optimizations or format support we're inadvertently bypassing. Acceptable risk; rollback is reverting the factory swap.
