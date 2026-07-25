# E — Player route taxonomy + DelayAudioProcessor (audio delay slider)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the architecturally-meaningful pieces of sub-project E:
1. First-class `PlaybackRoute` taxonomy + `RouteCapability` matrix in `android-shared/` — matches Apple's `docs/tvos-player/05-route-capability-matrix.md` field set so HUD labels and future code can speak the same language.
2. Working audio delay (`DelayAudioProcessor` + audio sink wiring + HUD slider) — reuses the existing `AudioSyncMs` per-profile preference (already in `AndroidPlayerSettingsStore`, ±500ms range).
3. Observational route display in the HUD Stats pane (inferred from current player config + media MIME).

**Scope intentionally smaller than spec's full vision:** the spec's `RouteSelector` that rebuilds the player per session is *deferred* — it requires `ContinuumPlayerFactory.createPlayer()` to be called per playback (today: once per service lifetime), which is invasive without a QA-confirmed routing bug to fix. The audio delay is the genuine user-visible win; everything else here is architecture.

**Architecture:**
- New `android-shared/.../player/route/` package: `PlaybackRoute` (sealed class), `RouteCapability` (data class), `RouteCapabilityMatrix` (map), pure functions.
- New `android-shared/.../player/audio/DelayAudioProcessor.kt` — Media3 `AudioProcessor` impl.
- `ContinuumPlayerFactory.createPlayer()` gains an audio-processor chain via `DefaultAudioSink.Builder.setAudioProcessorChain(...)` containing the `DelayAudioProcessor`.
- The processor instance is exposed (Koin singleton) so the HUD slider can call `processor.setDelayMs(ms)` reactively from the `PlayerSettingsStore.audioSyncMsFlow`.
- HUD Audio pane: replace the spec'd "audio delay unsupported" empty-state with a working slider bound to `audioSyncMsFlow` + `setAudioSyncMs()`.
- HUD Stats pane (from A.3c): extend `PlayerStatsSnapshot` with `route: String?` populated by a small inference function on the existing fields.

**Tech stack:** Kotlin 2.1.20, Media3 1.10.0, existing `AndroidPlayerSettingsStore` per-profile DataStore (D), existing `PlaybackAnalyticsListener` + `PlayerStatsSnapshot` (A.3c).

**Reference:**
- Apple matrix: `/opt/prairie-apple/docs/tvos-player/05-route-capability-matrix.md`.
- Audit: `ContinuumPlayerFactory.kt` at `android-shared/.../player/` already takes `preferFfmpegAudio: Boolean` constructor-time, uses `DefaultMediaSourceFactory` for MIME-driven routing, uses `EXTENSION_RENDERER_MODE_PREFER` when FFmpeg enabled. No audio processor chain wired today.

**KEY DECISION: Reuse `PlaybackSettingsKeys.AudioSyncMs` for the delay value.** It already exists as a per-profile preference with ±500ms range and per-server flush. Adding a new `AudioDelayMs` key would be redundant and would confuse the existing `audioSyncMsFlow`/`setAudioSyncMs()` API.

**Testing posture:** Pure logic tests for `RouteSelector`/`RouteCapabilityMatrix` + `DelayAudioProcessor`. UI bindings verified manually on Shield.

---

### Task 1: `PlaybackRoute` taxonomy + `RouteCapability` matrix + tests

**Files:**
- Create: `/opt/prairie-android/android-shared/src/androidMain/kotlin/com/continuum/app/common/player/route/PlaybackRoute.kt`
- Create: `/opt/prairie-android/android-shared/src/androidMain/kotlin/com/continuum/app/common/player/route/RouteCapability.kt`
- Create: `/opt/prairie-android/android-shared/src/androidMain/kotlin/com/continuum/app/common/player/route/RouteCapabilityMatrix.kt`
- Create: `/opt/prairie-android/android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/route/RouteCapabilityMatrixTest.kt`

**Why:** The architecture lives here. Pure code; no Media3 dependency; easy to test on JVM.

- [ ] **Step 1: Create `PlaybackRoute.kt`**

```kotlin
package com.continuum.app.common.player.route

/**
 * What kind of playback engine + media source combination is in use.
 * Mirrors Apple's tvOS route taxonomy (see `/opt/prairie-apple/docs/tvos-player/05-route-capability-matrix.md`).
 *
 * Today's Android player decides MIME-driven via DefaultMediaSourceFactory.
 * This enum is observational — it labels what's actually running so the HUD
 * Stats pane can surface it, and provides a vocabulary for a future
 * client-side route selector.
 */
enum class PlaybackRoute(val displayName: String) {
    /** ProgressiveMediaSource + RenderersFactory with FFmpeg audio extension (`EXTENSION_RENDERER_MODE_PREFER`). */
    Compatibility("Compatibility"),

    /** ProgressiveMediaSource + platform-only renderers (`EXTENSION_RENDERER_MODE_OFF`). Narrower codec breadth. */
    NativeDirect("Native Direct"),

    /** HlsMediaSource. Used when the server delivers HLS or transcodes to it. */
    Hls("HLS"),
}
```

- [ ] **Step 2: Create `RouteCapability.kt`**

```kotlin
package com.continuum.app.common.player.route

/**
 * Per-route feature capability. Field names mirror Apple's
 * `tvos-player/05-route-capability-matrix.md` so HUD labels and code
 * vocabularies match across platforms.
 */
data class RouteCapability(
    /** Can we report player-side buffer state to the HUD? */
    val buffersReported: Boolean,

    /** User can toggle video-gravity (letterbox vs zoom). */
    val videoGravityToggle: Boolean,

    /** User can force-disable HDR (force SDR output). */
    val hdrToggle: Boolean,

    /** Audio delay slider in milliseconds is supported. */
    val audioDelaySupported: Boolean,

    /** Subtitle delay slider in milliseconds is supported. */
    val subtitleDelaySupported: Boolean,

    /** What kinds of subtitle styling are available. */
    val subtitleStyling: SubtitleStyling,

    /** Decoder tunneling supported (low-latency game-mode-ish playback). */
    val supportsTunneling: Boolean,

    /** Bitstream passthrough to AVR (Atmos/DTS:X) supported. */
    val supportsPassthrough: Boolean,
)

enum class SubtitleStyling {
    /** No client-side subtitle styling. */
    None,
    /** Limited — only what the system caption settings expose. */
    SystemOnly,
    /** Full — color/size/font/background can be overridden client-side. */
    Full,
}
```

- [ ] **Step 3: Create `RouteCapabilityMatrix.kt`**

```kotlin
package com.continuum.app.common.player.route

/**
 * Static capability table mapping each [PlaybackRoute] to its feature set.
 * Single source of truth for "can route X do feature Y."
 *
 * Built from Apple's `tvos-player/05-route-capability-matrix.md` adapted
 * to Android's Media3 reality. Differences from Apple's matrix are
 * documented per row.
 */
object RouteCapabilityMatrix {

    private val map: Map<PlaybackRoute, RouteCapability> = mapOf(
        // Compatibility: ProgressiveMediaSource + FFmpeg audio extension.
        // Same capabilities as NativeDirect; differs only in codec breadth
        // (FFmpeg unlocks TrueHD/EAC-3 JOC/AC-4 software-decoded).
        PlaybackRoute.Compatibility to RouteCapability(
            buffersReported = true,
            videoGravityToggle = true,
            hdrToggle = true,
            audioDelaySupported = true,
            subtitleDelaySupported = true,
            subtitleStyling = SubtitleStyling.Full,
            supportsTunneling = true,
            supportsPassthrough = true,
        ),
        // NativeDirect: ProgressiveMediaSource + platform decoders only.
        PlaybackRoute.NativeDirect to RouteCapability(
            buffersReported = true,
            videoGravityToggle = true,
            hdrToggle = true,
            audioDelaySupported = true,
            subtitleDelaySupported = true,
            subtitleStyling = SubtitleStyling.Full,
            supportsTunneling = true,
            supportsPassthrough = true,
        ),
        // Hls: HlsMediaSource. Server-baked HDR (can't toggle off);
        // tunneling generally not supported by HLS pipeline; passthrough
        // is segment-level so depends on the segment's codec.
        PlaybackRoute.Hls to RouteCapability(
            buffersReported = true,
            videoGravityToggle = true,
            hdrToggle = false,
            audioDelaySupported = true,
            subtitleDelaySupported = true,
            subtitleStyling = SubtitleStyling.SystemOnly,
            supportsTunneling = false,
            supportsPassthrough = true,  // best-effort; depends on segment
        ),
    )

    /** Returns the capability flags for [route]. */
    fun get(route: PlaybackRoute): RouteCapability = map.getValue(route)

    /** Sanity check: every [PlaybackRoute] enum value has a matrix entry. */
    fun isExhaustive(): Boolean = PlaybackRoute.entries.all { it in map }
}
```

- [ ] **Step 4: Create the tests**

```kotlin
package com.continuum.app.common.player.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCapabilityMatrixTest {

    @Test
    fun `matrix has an entry for every route`() {
        assertTrue(RouteCapabilityMatrix.isExhaustive())
    }

    @Test
    fun `compatibility route supports audio delay`() {
        val cap = RouteCapabilityMatrix.get(PlaybackRoute.Compatibility)
        assertTrue(cap.audioDelaySupported)
    }

    @Test
    fun `hls route disables HDR toggle (server-baked)`() {
        val cap = RouteCapabilityMatrix.get(PlaybackRoute.Hls)
        assertFalse(cap.hdrToggle)
    }

    @Test
    fun `hls route uses SystemOnly subtitle styling`() {
        val cap = RouteCapabilityMatrix.get(PlaybackRoute.Hls)
        assertEquals(SubtitleStyling.SystemOnly, cap.subtitleStyling)
    }

    @Test
    fun `compatibility and native direct have identical capability flags`() {
        // The differentiator is codec breadth, not capability flags.
        val a = RouteCapabilityMatrix.get(PlaybackRoute.Compatibility)
        val b = RouteCapabilityMatrix.get(PlaybackRoute.NativeDirect)
        assertEquals(a, b)
    }

    @Test
    fun `displayName is human-readable`() {
        assertEquals("Compatibility", PlaybackRoute.Compatibility.displayName)
        assertEquals("Native Direct", PlaybackRoute.NativeDirect.displayName)
        assertEquals("HLS", PlaybackRoute.Hls.displayName)
    }
}
```

- [ ] **Step 5: Build + run tests**

```bash
cd /opt/prairie-android && ./gradlew :android-shared:compileDebugKotlin
cd /opt/prairie-android && ./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.route.RouteCapabilityMatrixTest"
```

Expected: BUILD SUCCESSFUL + 6 tests pass.

- [ ] **Step 6: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android add \
  android-shared/src/androidMain/kotlin/com/continuum/app/common/player/route/ \
  android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/route/

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android commit -m "feat(player-route): PlaybackRoute taxonomy + RouteCapabilityMatrix (E)

Mirrors Apple's tvos-player/05-route-capability-matrix.md vocabulary
in pure Kotlin: PlaybackRoute (Compatibility/NativeDirect/Hls),
RouteCapability (8 boolean/enum flags), RouteCapabilityMatrix
(static table) with 6 unit tests.

Observational only at this stage — DefaultMediaSourceFactory still
drives actual routing via server-supplied MIME hints. Wire to HUD
Stats pane in a later commit; client-side route selector deferred."
```

---

### Task 2: `DelayAudioProcessor` + Koin singleton

**Files:**
- Create: `/opt/prairie-android/android-shared/src/androidMain/kotlin/com/continuum/app/common/player/audio/DelayAudioProcessor.kt`
- Create: `/opt/prairie-android/android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/audio/DelayAudioProcessorTest.kt`
- Modify: `/opt/prairie-android/android-shared/src/androidMain/kotlin/com/continuum/app/common/di/PlayerModule.kt` (Koin reg)

**Why:** The audio delay primitive. Pure Media3 `AudioProcessor`; tested in isolation.

- [ ] **Step 1: Create the processor**

```kotlin
package com.continuum.app.common.player.audio

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Media3 [AudioProcessor] that adds a configurable head delay (positive →
 * prepend silence; negative → drop initial frames). Hot-reconfigurable
 * via [setDelayMs]; pending change applied on next [flush].
 *
 * Reused by the HUD Audio pane's delay slider (E). The currently active
 * value is mirrored from the per-profile [AudioSyncMs] preference.
 *
 * Range is clamped to ±500ms to match the existing `setAudioSyncMs(value)`
 * coercion in [AndroidPlayerSettingsStore].
 */
@UnstableApi
class DelayAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var pendingDelayMs: Int = 0

    /** Active delay applied to the current stream, captured at flush. */
    @Volatile
    private var activeDelayMs: Int = 0

    /** Cached bytes-per-second of the active format. */
    @Volatile
    private var bytesPerSecond: Int = 0

    /** Remaining bytes of silence to prepend (positive delay) or drop (negative). */
    @Volatile
    private var remainingHeadBytes: Int = 0

    /**
     * Range: [MIN_DELAY_MS, MAX_DELAY_MS]. Out-of-range values clamp.
     * Takes effect on the next [flush] (caller typically does a
     * `player.seekTo(player.currentPosition)` to apply mid-playback).
     */
    fun setDelayMs(delayMs: Int) {
        pendingDelayMs = delayMs.coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
    }

    fun getActiveDelayMs(): Int = activeDelayMs

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        // Output format == input; we don't resample or rechannel.
        bytesPerSecond = inputAudioFormat.sampleRate *
            inputAudioFormat.channelCount *
            inputAudioFormat.bytesPerFrame / inputAudioFormat.channelCount
        return inputAudioFormat
    }

    override fun onFlush() {
        activeDelayMs = pendingDelayMs
        remainingHeadBytes = if (bytesPerSecond > 0) {
            (activeDelayMs.toLong() * bytesPerSecond / 1_000L).toInt()
        } else 0
        // remainingHeadBytes is positive (silence-to-prepend) or negative
        // (bytes-to-drop). Computed once at flush; consumed during queueInput.
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        when {
            remainingHeadBytes > 0 -> {
                // Positive delay: emit silence equal to remaining head bytes,
                // then pass input through.
                val silenceLen = minOf(remainingHeadBytes, inputBuffer.remaining())
                val outBuffer = replaceOutputBuffer(silenceLen + inputBuffer.remaining())
                outBuffer.order(ByteOrder.nativeOrder())
                // Emit silence.
                val silenceBytes = ByteArray(silenceLen)  // zero-initialized
                outBuffer.put(silenceBytes)
                // Then emit input.
                outBuffer.put(inputBuffer)
                outBuffer.flip()
                remainingHeadBytes -= silenceLen
            }
            remainingHeadBytes < 0 -> {
                // Negative delay: drop |remainingHeadBytes| input bytes.
                val dropLen = minOf(-remainingHeadBytes, inputBuffer.remaining())
                inputBuffer.position(inputBuffer.position() + dropLen)
                remainingHeadBytes += dropLen
                if (inputBuffer.hasRemaining()) {
                    val outBuffer = replaceOutputBuffer(inputBuffer.remaining())
                    outBuffer.order(ByteOrder.nativeOrder())
                    outBuffer.put(inputBuffer)
                    outBuffer.flip()
                }
            }
            else -> {
                // No delay (or fully consumed): pass through.
                val outBuffer = replaceOutputBuffer(inputBuffer.remaining())
                outBuffer.order(ByteOrder.nativeOrder())
                outBuffer.put(inputBuffer)
                outBuffer.flip()
            }
        }
    }

    companion object {
        const val MIN_DELAY_MS = -500
        const val MAX_DELAY_MS = 500
    }
}
```

Note: `BaseAudioProcessor` is in `androidx.media3.common.audio`. Verify imports against the actual Media3 1.10.0 API. If `BaseAudioProcessor` is in a different package or has been renamed, adapt — the `AudioProcessor` interface itself has been stable.

The `replaceOutputBuffer(size: Int): ByteBuffer` helper is part of `BaseAudioProcessor` and returns a buffer of at least `size` bytes positioned at 0.

- [ ] **Step 2: Create tests**

```kotlin
package com.continuum.app.common.player.audio

import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DelayAudioProcessorTest {

    private fun makeStereo16Pcm44k() = AudioFormat(
        /* sampleRate = */ 44_100,
        /* channelCount = */ 2,
        /* encoding = */ C.ENCODING_PCM_16BIT,
    )

    private fun inputBytes(n: Int): ByteBuffer {
        val b = ByteBuffer.allocateDirect(n).order(ByteOrder.nativeOrder())
        repeat(n) { b.put(((it % 127) + 1).toByte()) }  // non-zero pattern
        b.flip()
        return b
    }

    @Test
    fun `clamps delay to MAX_DELAY_MS on overflow`() {
        val p = DelayAudioProcessor()
        p.setDelayMs(99_999)
        p.configure(makeStereo16Pcm44k())
        p.flush()
        assertEquals(DelayAudioProcessor.MAX_DELAY_MS, p.getActiveDelayMs())
    }

    @Test
    fun `clamps delay to MIN_DELAY_MS on underflow`() {
        val p = DelayAudioProcessor()
        p.setDelayMs(-99_999)
        p.configure(makeStereo16Pcm44k())
        p.flush()
        assertEquals(DelayAudioProcessor.MIN_DELAY_MS, p.getActiveDelayMs())
    }

    @Test
    fun `zero delay is identity pass-through`() {
        val p = DelayAudioProcessor()
        p.configure(makeStereo16Pcm44k())
        p.flush()
        val input = inputBytes(200)
        p.queueInput(input)
        val out = p.output
        assertEquals(200, out.remaining())
    }

    @Test
    fun `positive delay prepends silence frames`() {
        val p = DelayAudioProcessor()
        p.setDelayMs(10)  // 10ms @ 44.1kHz stereo 16-bit = 1764 bytes
        p.configure(makeStereo16Pcm44k())
        p.flush()
        val input = inputBytes(4_000)
        p.queueInput(input)
        val out = p.output
        // Expect 1764 silence bytes + 4000 input bytes
        assertEquals(5_764, out.remaining())
        // First 1764 bytes should be zero
        repeat(1_764) {
            assertEquals(0.toByte(), out.get())
        }
    }

    @Test
    fun `negative delay drops initial frames`() {
        val p = DelayAudioProcessor()
        p.setDelayMs(-10)  // drop ~1764 bytes
        p.configure(makeStereo16Pcm44k())
        p.flush()
        val input = inputBytes(4_000)
        p.queueInput(input)
        val out = p.output
        assertEquals(4_000 - 1_764, out.remaining())
    }

    @Test
    fun `re-flush re-applies new pending delay`() {
        val p = DelayAudioProcessor()
        p.setDelayMs(0)
        p.configure(makeStereo16Pcm44k())
        p.flush()
        assertEquals(0, p.getActiveDelayMs())

        p.setDelayMs(50)
        p.flush()
        assertEquals(50, p.getActiveDelayMs())
    }
}
```

- [ ] **Step 3: Register in Koin**

In `/opt/prairie-android/android-shared/src/androidMain/kotlin/com/continuum/app/common/di/PlayerModule.kt`:

```kotlin
import com.continuum.app.common.player.audio.DelayAudioProcessor

// inside module { ... } near other player singletons:
single { DelayAudioProcessor() }
```

- [ ] **Step 4: Build + run tests**

```bash
cd /opt/prairie-android && ./gradlew :android-shared:compileDebugKotlin
cd /opt/prairie-android && ./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.audio.DelayAudioProcessorTest"
```

Expected: BUILD SUCCESSFUL + 6 tests pass. If `BaseAudioProcessor`'s constructor or `replaceOutputBuffer` shape is different in Media3 1.10.0, adapt.

- [ ] **Step 5: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android add \
  android-shared/src/androidMain/kotlin/com/continuum/app/common/player/audio/ \
  android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/audio/ \
  android-shared/src/androidMain/kotlin/com/continuum/app/common/di/PlayerModule.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android commit -m "feat(player-audio): DelayAudioProcessor for ±500ms audio delay (E)

Media3 BaseAudioProcessor subclass that prepends silence (positive
delay) or drops initial frames (negative delay) at each flush.
Hot-reconfigurable; ±500ms range matches the existing AudioSyncMs
per-profile preference's coercion. 6 unit tests cover clamping,
pass-through, positive/negative paths, and re-flush behavior.

Koin singleton so the HUD slider can mutate the same instance the
audio sink uses. Audio-sink wiring + HUD slider land in T3."
```

---

### Task 3: Wire `DelayAudioProcessor` into `ContinuumPlayerFactory` + bind to `AudioSyncMs` flow

**Files:**
- Modify: `/opt/prairie-android/android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlayerFactory.kt`
- Modify: `/opt/prairie-android/android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlaybackService.kt` (or wherever the player's lifecycle hooks let us bind the flow)

**Why:** Plumb the processor into the audio sink, then subscribe to `PlayerSettingsStore.audioSyncMsFlow` to push slider changes into `processor.setDelayMs()` reactively.

- [ ] **Step 1: Add `delayProcessor: DelayAudioProcessor` to `ContinuumPlayerFactory` constructor**

In `ContinuumPlayerFactory.kt`:
```kotlin
class ContinuumPlayerFactory(
    private val context: Context,
    private val tokenManager: TokenManager,
    private val subtitleManager: SubtitleManager,
    okHttpClient: okhttp3.OkHttpClient,
    private val delayProcessor: DelayAudioProcessor,
) {
```

Update Koin's `single { ContinuumPlayerFactory(...) }` registration to inject `get()` for the new param.

- [ ] **Step 2: Wire the audio sink in `createPlayer()`**

Inside `createPlayer()`, the `ExoPlayer.Builder` configuration needs a custom `DefaultAudioSink` with our processor chain. Add (or modify if present):

```kotlin
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.common.audio.DefaultAudioProcessorChain

// inside createPlayer(...), before the ExoPlayer.Builder(...).build():
val audioSink: AudioSink = DefaultAudioSink.Builder(context)
    .setAudioProcessorChain(DefaultAudioProcessorChain(delayProcessor))
    .build()

// The DefaultRenderersFactory needs a hook to use this sink. The cleanest
// path is to override DefaultRenderersFactory.buildAudioRenderers() to
// inject the audio sink. Subclass inline:
val renderersFactory = object : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink? = audioSink
}.apply {
    setExtensionRendererMode(extensionMode)
    setEnableDecoderFallback(true)
}
```

Replace the existing `DefaultRenderersFactory(context)` instantiation with this override.

If `buildAudioSink` signature differs in Media3 1.10.0 (it's been stable for several versions, but worth verifying), adapt.

- [ ] **Step 3: Bind `audioSyncMsFlow` to the processor in `ContinuumPlaybackService`**

In `ContinuumPlaybackService.onCreate()`, after the player is created, subscribe to the per-profile flow:

```kotlin
import com.continuum.app.common.settings.PlayerSettingsStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

private val playerSettingsStore: PlayerSettingsStore by inject()
private val delayProcessor: DelayAudioProcessor by inject()

// inside onCreate(), after player setup:
serviceScope.launch {
    playerSettingsStore.audioSyncMsFlow
        .distinctUntilChanged()
        .collect { delayMs ->
            val previous = delayProcessor.getActiveDelayMs()
            delayProcessor.setDelayMs(delayMs)
            // Re-engage the processor on the next sample boundary by
            // forcing a flush — easiest is a no-op seekTo(currentPosition).
            if (previous != delayMs && player.isPlaying) {
                player.seekTo(player.currentPosition)
            }
        }
}
```

Use the existing service `serviceScope` (Koin-injected `CoroutineScope` or one created via `CoroutineScope(Dispatchers.Main + SupervisorJob())`). Match what the service already uses.

- [ ] **Step 4: Build**

```bash
cd /opt/prairie-android && ./gradlew :android-shared:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. If the audio sink override signature is off, the compiler tells you — adapt.

- [ ] **Step 5: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android add \
  android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlayerFactory.kt \
  android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlaybackService.kt \
  android-shared/src/androidMain/kotlin/com/continuum/app/common/di/PlayerModule.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android commit -m "feat(player-audio): wire DelayAudioProcessor into ExoPlayer audio sink (E)

ContinuumPlayerFactory now constructs a DefaultAudioSink with a
single-processor chain containing the injected DelayAudioProcessor,
attached to the player via a DefaultRenderersFactory.buildAudioSink
override.

ContinuumPlaybackService collects PlayerSettingsStore.audioSyncMsFlow
(per-profile from AndroidPlayerSettingsStore) and pushes each change
through delayProcessor.setDelayMs() + seekTo(currentPosition) to
force a flush mid-playback. Delay range matches the AudioSyncMs
preference's ±500ms coercion.

HUD Audio pane slider lands in T4."
```

---

### Task 4: HUD Audio pane slider — replace empty-state with working delay control

**Files:**
- Modify: `/opt/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt`
- Modify: `/opt/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerViewModel.kt`
- Modify: `/opt/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt` (call-site update)

**Why:** Surface the delay slider in the HUD. Read + write via `PlayerSettingsStore.audioSyncMsFlow` / `setAudioSyncMs`. After T3 the change propagates automatically into the processor.

- [ ] **Step 1: Add `audioDelayMs: Int` + `onAudioDelayChanged: (Int) -> Unit` to `TvPlayerViewModel`**

In `TvPlayerViewModel.kt`, expose:

```kotlin
val audioDelayMs: StateFlow<Int> = playerSettingsStore.audioSyncMsFlow
    .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

fun onAudioDelayChanged(delayMs: Int) {
    viewModelScope.launch {
        playerSettingsStore.setAudioSyncMs(delayMs)
    }
}
```

Inject `PlayerSettingsStore` into the ViewModel constructor (likely already available via the DI module — check `AndroidTvModule.kt`).

- [ ] **Step 2: Replace HUD Audio pane's empty-state with the slider**

In `TvPlayerHud.kt`, find the Audio tab's current rendering (per A.3a it's `if (audioTracks.size > 1) HudPickerPane else HudEmptyStatePane(...)`). Expand into a `Column` with the picker (when present) + a delay slider section:

```kotlin
@Composable
private fun HudAudioPane(
    audioTracks: List<TrackOption>,
    selectedAudioId: Int?,
    onSelectAudio: (Int) -> Unit,
    audioDelayMs: Int,
    onAudioDelayChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        if (audioTracks.size > 1) {
            Text(
                text = "Audio track",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            HudPickerPane(
                options = audioTracks,
                selectedId = selectedAudioId,
                onSelect = onSelectAudio,
            )
        }

        // Audio delay slider — always shown; works regardless of track count.
        Text(
            text = "Audio delay: ${audioDelayMs} ms",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        HudAudioDelayRow(
            valueMs = audioDelayMs,
            onChange = onAudioDelayChanged,
        )
    }
}

@Composable
private fun HudAudioDelayRow(
    valueMs: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // D-pad friendly stepper: −50 / −10 / 0 / +10 / +50 ms buttons.
    // Slider doesn't work great on TV remotes; explicit steppers are
    // the established Infuse pattern for delay tuning.
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DelayStepperButton(label: String, onClick: () -> Unit) {
    // Match the local chip idiom (focus-driven scale, etc).
    // Implementer: use the same pattern as VideoFillModeChip from A.3d-gravity.
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.surface,
            pressedContainerColor = MaterialTheme.colorScheme.onSurface,
            pressedContentColor = MaterialTheme.colorScheme.surface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        modifier = Modifier.padding(horizontal = 2.dp),
    ) {
        Box(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
```

Replace the existing `HudTab.Audio -> ...` branch with:

```kotlin
HudTab.Audio -> HudAudioPane(
    audioTracks = audioTracks,
    selectedAudioId = selectedAudioId,  // whatever the existing call passed
    onSelectAudio = onSelectAudio,       // existing callback
    audioDelayMs = audioDelayMs,
    onAudioDelayChanged = onAudioDelayChanged,
)
```

Add `audioDelayMs: Int` and `onAudioDelayChanged: (Int) -> Unit` parameters to `TvPlayerHud`'s signature.

- [ ] **Step 3: Update call-site in `TvPlayerScreen.kt`**

Pass the new params from ViewModel state:

```kotlin
TvPlayerHud(
    // ... existing args
    audioDelayMs = state.audioDelayMs,  // (or however ViewModel exposes it — adapt)
    onAudioDelayChanged = viewModel::onAudioDelayChanged,
)
```

If the existing `state` doesn't carry `audioDelayMs` (it lives in a separate StateFlow per Step 1), collect both flows and pass the value through:

```kotlin
val audioDelayMs by viewModel.audioDelayMs.collectAsState()

TvPlayerHud(
    // ...
    audioDelayMs = audioDelayMs,
    onAudioDelayChanged = viewModel::onAudioDelayChanged,
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

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android commit -m "feat(tv-player): audio delay slider in HUD Audio pane (E)

Replaces A.3a's audio-tab fallback with a HudAudioPane that combines
the existing track picker (when >1 track) with a 5-button stepper row
(−50 / −10 / Reset / +10 / +50 ms). Values bound to per-profile
PlayerSettingsStore.audioSyncMsFlow; writes go through
setAudioSyncMs() which is already ±500ms-coerced.

The audio-sync flow is consumed by ContinuumPlaybackService (T3)
which pushes each value into DelayAudioProcessor.setDelayMs() and
forces a seek-to-current to apply mid-playback.

Stepper buttons over a continuous slider per Infuse convention —
remote control + 10ms granularity is more usable than precise drag."
```

---

## Self-Review

**Spec coverage** (E section):
- E.1 honest framing → addressed in plan front matter ✓
- E.2 route taxonomy → T1 ✓
- E.4 RouteCapability fields → T1 ✓ (8 fields matching Apple's matrix)
- E.6 audio delay (`DelayAudioProcessor`) → T2 + T3 ✓
- E.5 RouteSelector → **DEFERRED** (requires per-session player rebuild — not justified without QA-confirmed routing bugs)
- E.7 route persistence override → **DEFERRED** with RouteSelector
- E.9 testing → T1 (matrix tests) + T2 (processor tests) ✓
- E.11 explicit non-implementation → spec accepted "no DV loopback / no new engine"; we go further and defer the selector

**Placeholder scan:** No "TBD." The "ContinuumPlaybackService binding" in T3 references `serviceScope` — implementer adapts to whatever the actual service uses.

**Type consistency:** `PlaybackRoute`, `RouteCapability`, `DelayAudioProcessor` consistent. `AudioSyncMs` reuse documented.

**Sequencing:** T1 (pure types) → T2 (processor + tests) → T3 (player wiring) → T4 (UI slider). Each commit independently buildable. T4 depends on T3's flow plumbing.

**Risk:**
- T2's `BaseAudioProcessor` subclass touches Media3's audio internals. The `replaceOutputBuffer` shape may differ slightly between Media3 minor versions. Tests should catch regressions.
- T3's `DefaultRenderersFactory.buildAudioSink` override path is the most fragile — if Media3 1.10.0 deprecated it, an alternate path (custom `AudioSink` injection via `AudioRendererEventListener`) may be needed.
- T3 binds to `audioSyncMsFlow` from `ContinuumPlaybackService.onCreate` which lives across profile changes. The flow already re-emits on profile change (per `AndroidPlayerSettingsStore.profileScopedFlow`), so each profile's preference takes effect on the next sample boundary.
