# Audiobook Player — Phase 5: Listening Quality (Skip Silence + Volume) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Add audiobook-only listening-quality processing to the shared Media3 playback service: ExoPlayer skip-silence, plus volume normalization and boost via `android.media.audiofx.LoudnessEnhancer` on the player's audio session id. Settings (from Phase 4's `AudiobookSettingsStore`) apply live without recreating the player. Video playback must be provably unaffected.

**Architecture:** `ContinuumPlaybackService` is shared by video and audiobook (a single `ExoPlayer` per process, exposed through one `MediaSession`). The service has no notion of "audiobook vs video" today — both UIs just hand it `MediaItem`s. Phase 5 introduces a media-type marker on the audiobook `MediaItem` (`MediaMetadata.mediaType = MEDIA_TYPE_AUDIO_BOOK`), and the service gates all new audio processing on that marker. Pure decision logic (settings → `LoudnessEnhancer` target gain in millibels; the audiobook predicate) is extracted into a unit-tested helper in `android-shared` so the device-only effect wiring stays thin. Settings `Flow`s are observed **inside the service** (the same pattern already used for `audioSyncMsFlow` / `subtitleSyncMsFlow`), not pushed via a custom `MediaSession` command — justified in Task 4.

**Tech Stack:** Kotlin, Media3 ExoPlayer 1.10.0, `android.media.audiofx.LoudnessEnhancer`, DataStore (Phase 4 `AudiobookSettingsStore`), Kotlin coroutines/Flow, JUnit + kotlin.test.

---

## File Structure

Real paths (repository root = `silo-android`, the cwd for all commands):

- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/audio/AudiobookAudioEffects.kt` — **NEW.** Pure, framework-free decision logic: `isAudiobookMediaItem(MediaItem?)` predicate and `targetGainMb(volumeNormalizationEnabled, volumeBoostDb)` mapper. No Android effect classes referenced; fully unit-testable.
- `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/audio/AudiobookAudioEffectsTest.kt` — **NEW.** JUnit tests for the predicate + mapper.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/audio/AudiobookLoudnessController.kt` — **NEW.** Thin lifecycle wrapper that owns one `LoudnessEnhancer`, (re)binds it to an audio session id, applies enable + target gain, and releases it. Device-touching; keeps `LoudnessEnhancer` API isolated from the service for readability.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlaybackService.kt` — **EDIT.** Observe `AudiobookSettingsStore` flows; on media-item transition / audio-session-id change, enable skip-silence + bind the loudness controller only for audiobook items; tear down on video items and `onDestroy`.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerScreen.kt` — **EDIT.** Tag the audiobook `MediaItem` with `MediaMetadata.mediaType = MEDIA_TYPE_AUDIO_BOOK` so the service can identify the session.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/di/PlayerInfraModule.kt` — **EDIT (only if Phase 4 did not already register `AudiobookSettingsStore` in Koin).** Confirm the store is injectable; the service uses `by inject()`.

Assumptions from Phase 4 (already shipped): `AudiobookSettingsStore` exposes `val skipSilenceEnabled: Flow<Boolean>`, `val volumeNormalizationEnabled: Flow<Boolean>`, `val volumeBoostDb: Flow<Int>` and is registered as a Koin `single`. Verify the exact `Flow` types in Task 4 before wiring; if Phase 4 used `StateFlow`/`Flow<Double>` for `volumeBoostDb`, adapt the collectors but keep the helper signatures (Task 1) integer-mB based.

---

### Task 1 — Pure decision logic: audiobook predicate + target-gain mapper (TDD)

**Files:**
- `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/audio/AudiobookAudioEffectsTest.kt` (new)
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/audio/AudiobookAudioEffects.kt` (new)

`LoudnessEnhancer.setTargetGain` takes gain in **millibels** (mB); `1 dB = 100 mB`. Boost is non-negative. When normalization is off **and** boost is 0, target gain is 0 (effect is effectively inert). When normalization is on, we still drive gain purely from the boost value in this phase (true content-loudness normalization needs measured LUFS we do not have; the toggle simply gates whether the enhancer is engaged at all alongside boost). Spec §4.4 wording: "targetGain in mB, driven by the normalization/boost settings."

- [ ] Write the failing test. Create `AudiobookAudioEffectsTest.kt`:

```kotlin
package com.continuum.app.common.player.audio

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@UnstableApi
class AudiobookAudioEffectsTest {

    private fun audiobookItem(): MediaItem =
        MediaItem.Builder()
            .setUri("https://example.test/book.m4b")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
                    .build(),
            )
            .build()

    private fun videoItem(): MediaItem =
        MediaItem.Builder()
            .setUri("https://example.test/movie.m3u8")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MOVIE)
                    .build(),
            )
            .build()

    @Test
    fun audiobookItem_isAudiobook() {
        assertTrue(AudiobookAudioEffects.isAudiobookMediaItem(audiobookItem()))
    }

    @Test
    fun videoItem_isNotAudiobook() {
        assertFalse(AudiobookAudioEffects.isAudiobookMediaItem(videoItem()))
    }

    @Test
    fun untaggedItem_isNotAudiobook() {
        val plain = MediaItem.Builder().setUri("https://example.test/x").build()
        assertFalse(AudiobookAudioEffects.isAudiobookMediaItem(plain))
    }

    @Test
    fun nullItem_isNotAudiobook() {
        assertFalse(AudiobookAudioEffects.isAudiobookMediaItem(null))
    }

    @Test
    fun gain_isZero_whenNormalizationOffAndNoBoost() {
        assertEquals(0, AudiobookAudioEffects.targetGainMb(normalizationEnabled = false, volumeBoostDb = 0))
    }

    @Test
    fun gain_convertsDbToMillibels() {
        assertEquals(600, AudiobookAudioEffects.targetGainMb(normalizationEnabled = false, volumeBoostDb = 6))
    }

    @Test
    fun gain_normalizationOnWithoutBoost_isZero() {
        // Normalization without measured loudness contributes no static gain in Phase 5.
        assertEquals(0, AudiobookAudioEffects.targetGainMb(normalizationEnabled = true, volumeBoostDb = 0))
    }

    @Test
    fun gain_clampsNegativeBoostToZero() {
        assertEquals(0, AudiobookAudioEffects.targetGainMb(normalizationEnabled = false, volumeBoostDb = -3))
    }

    @Test
    fun gain_clampsToMaxBoost() {
        // LoudnessEnhancer accepts large gains, but the UI tops out at +12 dB;
        // the mapper enforces the same ceiling so a corrupt setting can't deafen.
        assertEquals(1200, AudiobookAudioEffects.targetGainMb(normalizationEnabled = false, volumeBoostDb = 99))
    }
}
```

- [ ] Run the failing test (expect compile failure: `AudiobookAudioEffects` does not exist):

```
./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.audio.AudiobookAudioEffectsTest"
```

Expected: build/compile failure referencing unresolved `AudiobookAudioEffects`.

- [ ] Write the implementation. Create `AudiobookAudioEffects.kt`:

```kotlin
package com.continuum.app.common.player.audio

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi

/**
 * Pure, framework-free decision logic for Phase 5 audiobook listening quality.
 *
 * Kept Android-effect-free (no [android.media.audiofx.LoudnessEnhancer] here) so
 * it runs under plain JVM unit tests; [AudiobookLoudnessController] consumes
 * [targetGainMb] and owns the actual effect.
 */
@UnstableApi
object AudiobookAudioEffects {

    /** Boost ceiling in dB; mirrors the audiobook settings UI's max boost. */
    const val MAX_BOOST_DB = 12

    /** Millibels per decibel — the unit LoudnessEnhancer.setTargetGain expects. */
    const val MB_PER_DB = 100

    /**
     * True when [item]'s metadata marks it as an audiobook. The audiobook UI
     * tags its MediaItem with [MediaMetadata.MEDIA_TYPE_AUDIO_BOOK]; video items
     * use movie/episode types (or none). This is the single signal the playback
     * service uses to gate audiobook-only processing.
     */
    fun isAudiobookMediaItem(item: MediaItem?): Boolean =
        item?.mediaMetadata?.mediaType == MediaMetadata.MEDIA_TYPE_AUDIO_BOOK

    /**
     * Target gain in millibels for the LoudnessEnhancer.
     *
     * Boost is clamped to [0, [MAX_BOOST_DB]] then converted to mB. In Phase 5
     * the [normalizationEnabled] toggle does not add static gain on its own
     * (we have no measured content loudness), so the gain is driven entirely by
     * the clamped boost; a value of 0 leaves the enhancer inert.
     */
    fun targetGainMb(normalizationEnabled: Boolean, volumeBoostDb: Int): Int =
        volumeBoostDb.coerceIn(0, MAX_BOOST_DB) * MB_PER_DB
}
```

- [ ] Run the test again; expect pass:

```
./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.audio.AudiobookAudioEffectsTest"
```

Expected: `BUILD SUCCESSFUL`, all `AudiobookAudioEffectsTest` cases green.

- [ ] Commit: `test(player): add audiobook audio-effects decision logic (skip-silence predicate + loudness gain mapper)`.

---

### Task 2 — Tag the audiobook MediaItem with MEDIA_TYPE_AUDIO_BOOK

This is the load-bearing signal that lets the shared service tell audiobook sessions apart from video. The audiobook screen builds its `MediaItem` inline (it does not go through `ContinuumPlayerFactory.buildMediaItem`), so add the marker there. Video continues to build items without this type, so `isAudiobookMediaItem` returns false for it.

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerScreen.kt` (edit)

- [ ] In the `LaunchedEffect(controller, state.streamUrl)` block, add `.setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)` to the `MediaMetadata.Builder()`. The existing block is:

```kotlin
val mediaItem = MediaItem.Builder()
    .setUri(url)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(state.title)
            .setArtist(state.author ?: state.narrator)
            .also { mb ->
                state.coverUrl?.takeIf { it.isNotBlank() }
                    ?.let { mb.setArtworkUri(android.net.Uri.parse(it)) }
            }
            .build(),
    )
    .build()
```

Change the metadata builder to:

```kotlin
MediaMetadata.Builder()
    .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
    .setTitle(state.title)
    .setArtist(state.author ?: state.narrator)
    .also { mb ->
        state.coverUrl?.takeIf { it.isNotBlank() }
            ?.let { mb.setArtworkUri(android.net.Uri.parse(it)) }
    }
    .build()
```

`MediaMetadata` is already imported in this file (used for the existing builder). `MEDIA_TYPE_AUDIO_BOOK` is a constant on `androidx.media3.common.MediaMetadata`, no new import.

- [ ] Compile the phone app to confirm the marker compiles:

```
./gradlew :androidApp:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] Note for TV parity: when Phase 3's `TvAudiobookPlayerScreen` (or any future audiobook entry point) builds its own `MediaItem`, it must set the same `MEDIA_TYPE_AUDIO_BOOK`. Add a short code comment at the tag site referencing `AudiobookAudioEffects.isAudiobookMediaItem` so the dependency is discoverable. If the relocated shared VM (Phase 1) centralizes MediaItem construction, set the marker there instead so both UIs inherit it. At Phase 5 implementation time, the phone screen is the only audiobook MediaItem builder; tag it there.

- [ ] Commit: `feat(player): mark audiobook MediaItems with MEDIA_TYPE_AUDIO_BOOK for service-side gating`.

---

### Task 3 — LoudnessEnhancer lifecycle wrapper

A thin controller that owns at most one `LoudnessEnhancer`, binds it to a given audio session id, and applies an enabled flag + target gain (mB). Keeps the `audiofx` API and its `RuntimeException`/`UnsupportedOperationException` surface out of the service body. No new permissions are required for `LoudnessEnhancer` on an app-owned audio session.

**Files:**
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/audio/AudiobookLoudnessController.kt` (new)

- [ ] Create the controller:

```kotlin
package com.continuum.app.common.player.audio

import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi

/**
 * Owns a single [LoudnessEnhancer] bound to the active ExoPlayer audio session.
 *
 * The enhancer is recreated whenever the audio session id changes (ExoPlayer
 * can assign a new session on renderer reinit), and released when audiobook
 * playback ends or the service is destroyed. All effect operations are wrapped
 * in runCatching: audiofx construction can fail on some OEM builds, and a
 * failure to boost must never crash playback.
 *
 * Not thread-safe; call only from the service's main-thread scope.
 */
@UnstableApi
class AudiobookLoudnessController {

    private var enhancer: LoudnessEnhancer? = null
    private var boundSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    private var pendingEnabled: Boolean = false
    private var pendingTargetGainMb: Int = 0

    /**
     * Bind (or rebind) to [audioSessionId] and apply the current enabled /
     * gain state. A no-op when already bound to the same valid session. Pass an
     * unset/0 session id to release.
     */
    fun bind(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId == 0) {
            release()
            return
        }
        if (enhancer != null && boundSessionId == audioSessionId) {
            apply()
            return
        }
        release()
        enhancer = runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull()
        boundSessionId = if (enhancer != null) audioSessionId else C.AUDIO_SESSION_ID_UNSET
        apply()
    }

    /** Update the desired enabled flag + target gain (mB) and apply if bound. */
    fun setState(enabled: Boolean, targetGainMb: Int) {
        pendingEnabled = enabled
        pendingTargetGainMb = targetGainMb
        apply()
    }

    private fun apply() {
        val fx = enhancer ?: return
        runCatching {
            fx.setTargetGain(pendingTargetGainMb)
            // Only engage the effect when normalization/boost asks for it; a
            // disabled (or zero-gain) enhancer leaves the signal untouched.
            fx.enabled = pendingEnabled && pendingTargetGainMb > 0
        }
    }

    /** Release the enhancer; safe to call repeatedly. */
    fun release() {
        enhancer?.let { fx -> runCatching { fx.release() } }
        enhancer = null
        boundSessionId = C.AUDIO_SESSION_ID_UNSET
    }
}
```

- [ ] Compile the module:

```
./gradlew :android-shared:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] Commit: `feat(player): add AudiobookLoudnessController wrapping LoudnessEnhancer lifecycle`.

---

### Task 4 — Wire skip-silence + loudness into the playback service (live, audiobook-only)

**Plumbing decision (justify in code comment):** observe the settings `Flow`s **inside the service**, mirroring the existing `audioSyncMsFlow` / `subtitleSyncMsFlow` collectors in `onCreate`. Rationale: the service already owns the long-lived `scope` bounded by its lifecycle, already injects stores via Koin `by inject()`, and is the only component with a reference to the concrete `ExoPlayer` (needed for `setSkipSilenceEnabled` and `audioSessionId` — a `MediaController` exposes neither). A custom `MediaSession` command would add a round-trip and a second source of truth for state the service must hold anyway. So: in-service observation, no new session command.

**Audiobook-only gating:** skip-silence and loudness must apply only when the active item is an audiobook. Detect via `Player.Listener.onMediaItemTransition` (+ initial item at startup) using `AudiobookAudioEffects.isAudiobookMediaItem`. When the active item is video, force `setSkipSilenceEnabled(false)` and `loudnessController.release()` so the shared player returns to exact baseline behavior. Rebind loudness on `onAudioSessionIdChanged`.

**Files:**
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlaybackService.kt` (edit)
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/di/PlayerInfraModule.kt` (verify `AudiobookSettingsStore` is a Koin `single`; add it only if Phase 4 left it unregistered)

- [ ] Verify the Phase 4 store and its flow types:

```
./gradlew :android-shared:dependencies > /dev/null 2>&1 || true
/usr/bin/grep -rn "class AudiobookSettingsStore\|skipSilenceEnabled\|volumeNormalizationEnabled\|volumeBoostDb" android-shared/src/androidMain/kotlin
```

Expected: a single `AudiobookSettingsStore` declaration exposing `skipSilenceEnabled: Flow<Boolean>`, `volumeNormalizationEnabled: Flow<Boolean>`, `volumeBoostDb` (int-typed). Confirm it is registered in `PlayerInfraModule.kt` (or another loaded Koin module). If `volumeBoostDb` is `Flow<Double>` or a `StateFlow`, adjust the collector's mapping below to `.toInt()`; the helper stays integer-mB.

- [ ] Add imports to `ContinuumPlaybackService.kt`:

```kotlin
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.continuum.app.common.player.audio.AudiobookAudioEffects
import com.continuum.app.common.player.audio.AudiobookLoudnessController
import com.continuum.app.common.settings.AudiobookSettingsStore
import kotlinx.coroutines.flow.combine
```

- [ ] Add injected dependency + fields near the existing `by inject()` block and job fields:

```kotlin
private val audiobookSettingsStore: AudiobookSettingsStore by inject()
```

```kotlin
private val loudnessController = AudiobookLoudnessController()
private var listeningQualityJob: Job? = null
private var playerListener: Player.Listener? = null
```

- [ ] In `onCreate`, after `mediaSession = MediaSession.Builder(this, player).build()`, capture whether the active item is an audiobook and react to transitions and audio-session changes. Insert:

```kotlin
// Phase 5 listening quality. Skip-silence + LoudnessEnhancer apply ONLY to
// audiobook items (tagged MEDIA_TYPE_AUDIO_BOOK); video items are forced back
// to baseline so the shared player is byte-for-byte unchanged for video.
var activeIsAudiobook = AudiobookAudioEffects.isAudiobookMediaItem(player.currentMediaItem)

fun applyListeningQuality(
    skipSilence: Boolean,
    normalizationEnabled: Boolean,
    boostDb: Int,
) {
    if (activeIsAudiobook) {
        player.skipSilenceEnabled = skipSilence
        loudnessController.bind(player.audioSessionId)
        loudnessController.setState(
            enabled = normalizationEnabled || boostDb > 0,
            targetGainMb = AudiobookAudioEffects.targetGainMb(normalizationEnabled, boostDb),
        )
    } else {
        player.skipSilenceEnabled = false
        loudnessController.release()
    }
}

val listener = object : Player.Listener {
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        activeIsAudiobook = AudiobookAudioEffects.isAudiobookMediaItem(mediaItem)
        // Re-apply with the latest settings snapshot on every item change.
        scope.launch {
            applyListeningQuality(
                skipSilence = audiobookSettingsStore.skipSilenceEnabled.first(),
                normalizationEnabled = audiobookSettingsStore.volumeNormalizationEnabled.first(),
                boostDb = audiobookSettingsStore.volumeBoostDb.first().toInt(),
            )
        }
    }

    override fun onAudioSessionIdChanged(audioSessionId: Int) {
        if (activeIsAudiobook) loudnessController.bind(audioSessionId)
    }
}
player.addListener(listener)
playerListener = listener

// Live settings observation — same lifecycle-bounded pattern as the
// audioSync/subtitleSync collectors above. We observe in-service (not via a
// custom MediaSession command) because the service is the only holder of the
// concrete ExoPlayer needed for skipSilenceEnabled + audioSessionId.
listeningQualityJob = scope.launch {
    combine(
        audiobookSettingsStore.skipSilenceEnabled,
        audiobookSettingsStore.volumeNormalizationEnabled,
        audiobookSettingsStore.volumeBoostDb,
    ) { skipSilence, normalization, boostDb ->
        Triple(skipSilence, normalization, boostDb.toInt())
    }.distinctUntilChanged().collect { (skipSilence, normalization, boostDb) ->
        applyListeningQuality(skipSilence, normalization, boostDb)
    }
}
```

Add `import kotlinx.coroutines.flow.first` for the `.first()` calls.

- [ ] In `onDestroy`, before `scope.cancel()`, tear down the new job and effect, and remove the listener:

```kotlin
listeningQualityJob?.cancel()
playerListener?.let { mediaSession?.player?.removeListener(it) }
playerListener = null
loudnessController.release()
```

Place these alongside the existing `positionJob?.cancel()` / `audioSyncJob?.cancel()` / `subtitleSyncJob?.cancel()` lines (keep the `player.release()` in the existing `mediaSession?.run { ... }` block).

- [ ] Compile the module:

```
./gradlew :android-shared:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`. If `skipSilenceEnabled`, `audioSessionId`, or `onAudioSessionIdChanged` resolve against `Player` vs `ExoPlayer`: `setSkipSilenceEnabled` is on `ExoPlayer` only — confirm `player` here is typed `ExoPlayer` (it is: `playerFactory.createPlayer()` returns `ExoPlayer`). `audioSessionId` and `onAudioSessionIdChanged` are on `ExoPlayer`/`Player.Listener` respectively.

- [ ] Re-run the unit suite to confirm nothing regressed:

```
./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.audio.*"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] Commit: `feat(player): apply skip-silence + loudness boost for audiobook sessions, video unaffected`.

---

### Task 5 — Manual on-device verification (skip-silence + boost audible)

`LoudnessEnhancer` and skip-silence are audio effects; they cannot be asserted in JVM unit tests. Verify on a physical Pixel (emulator audio routing is unreliable for audiofx). Assumes Phase 4's audiobook settings UI exposes the three toggles; if not yet wired to a screen, temporarily set the DataStore values via the settings store in a debug build, or use the Phase 4 settings sheet.

**Files:** none (manual).

- [ ] Install a debug build on a connected device:

```
./gradlew :androidApp:installDebug
adb shell am start -n com.continuum.app.android.debug/com.continuum.app.android.MainActivity
```

(If the applicationId/launcher differ, resolve them: `adb shell cmd package resolve-activity --brief com.continuum.app.android.debug`.)

- [ ] Start logcat for the player tag in a second terminal:

```
adb logcat -c && adb logcat -s ContinuumPlayback:I AudioEffect:I
```

- [ ] **Skip-silence audible check.** Open an audiobook with audible gaps/pauses. With skip-silence OFF in audiobook settings, listen across a known pause and note its length. Toggle skip-silence ON (no playback restart should occur — the player is not recreated). The pause should audibly shorten/disappear within a second. Toggle OFF and confirm the pause returns. This exercises live application via the in-service collector.

- [ ] **Volume boost audible check.** With normalization/boost at 0 dB, note loudness. Raise boost to +6 dB; loudness should increase clearly without playback interruption. Raise to +12 dB (ceiling) and confirm a further increase with no clipping crash. Confirm `LoudnessEnhancer` engaged via `dumpsys`:

```
adb shell dumpsys media.audio_flinger | /usr/bin/grep -i "LE \|LoudnessEnhancer" | head
```

Expected: an effect entry present while the audiobook plays with boost > 0; absent (or disabled) at 0 dB.

- [ ] **Session rebind check.** Skip across a chapter / seek far enough to trigger a renderer reinit; confirm boost persists (the `onAudioSessionIdChanged` rebind path). No crash, no audio dropout beyond the normal seek gap.

- [ ] Record results (device model, OS version, pass/fail per check) in the PR description. No commit (manual step).

---

### Task 6 — Manual VIDEO regression (shared service unaffected)

The service is shared with video. Prove video is byte-for-byte unchanged: no skip-silence, no loudness effect, audio session clean.

**Files:** none (manual).

- [ ] With the same debug build, play a normal video item (a movie/episode) end-to-end for ~2 minutes. Confirm: audio plays normally, lock-screen controls work, headset play/pause works, and swiping the app away stops playback (existing `onTaskRemoved` teardown).

- [ ] While the video plays, confirm no loudness effect is attached to the video session:

```
adb shell dumpsys media.audio_flinger | /usr/bin/grep -i "LoudnessEnhancer" | head
```

Expected: no `LoudnessEnhancer` entry for the video session (the service released it / never bound it because `isAudiobookMediaItem` is false).

- [ ] Toggle the audiobook settings (skip-silence / boost) **while a video is playing** and confirm video audio is completely unaffected (the collector's `applyListeningQuality` takes the `else` branch: `skipSilenceEnabled = false`, controller released). No change in video loudness, no skipped audio.

- [ ] Transition audiobook → video within one session if possible (e.g., background the audiobook, start a video): confirm skip-silence and boost are dropped on the video item, then return to the audiobook and confirm they re-engage.

- [ ] Record video-regression results in the PR description. No commit (manual step).

---

## Self-review vs. spec (§4.4, §6 phase 5)

- "Skip silence via `ExoPlayer.setSkipSilenceEnabled(...)`, applied only for audiobook sessions (not video)." → Task 4: `player.skipSilenceEnabled = skipSilence` in the audiobook branch; forced `false` for video. Gated by `isAudiobookMediaItem` (Task 1). ✅
- "Volume normalization + boost via `android.media.audiofx.LoudnessEnhancer` on the player's audio session id (targetGain mB from volumeBoostDb; normalization toggle), released with the player, no extra permissions." → Tasks 1/3/4: `AudiobookLoudnessController` binds to `player.audioSessionId`, `setTargetGain` in mB from `targetGainMb`, released in `onDestroy` and on video items. No manifest permission added. ✅
- "Plumb the settings Flows into the service so changes apply live without recreating the player (observe in service or via custom MediaSession command — choose + justify)." → Task 4: in-service `combine` collector on the lifecycle scope, justified (only the service holds the concrete `ExoPlayer`). Player is never recreated; effects toggle live. ✅
- "Guard video playback is unaffected." → Task 4 `else` branch + Task 6 regression pass + `dumpsys` evidence. ✅
- Uses the exact Phase 4 flow names `skipSilenceEnabled`, `volumeNormalizationEnabled`, `volumeBoostDb`. ✅ (Task 4 verifies types and adapts collectors if `volumeBoostDb` is non-Int.)
- TDD applied to pure logic (predicate + mapper) with exact gradle commands and expected output; device-only wiring has explicit adb verify steps. ✅

**Inline fix applied during review:** the loudness controller's `enabled` flag is set to `pendingEnabled && pendingTargetGainMb > 0` so a 0 dB boost with normalization on does not engage an inert effect (avoids needless audiofx attachment on the audio path); the service passes `enabled = normalizationEnabled || boostDb > 0` to match the spec's "driven by the normalization/boost settings."

## Deferred / risk items

- **Phase 4 dependency:** plan assumes `AudiobookSettingsStore` exists with the three named flows and is Koin-registered. Task 4 has a verification step; if `volumeBoostDb` is `Double`/`StateFlow`, adapt the collectors (helper stays Int-mB).
- **True loudness normalization deferred:** Phase 5 drives gain from boost only; real LUFS-measured normalization (per-book target) is out of scope and would need server-side or on-device loudness measurement — noted as future work.
- **TV parity:** when Phase 3's `TvAudiobookPlayerScreen` (or a Phase 1 shared-VM MediaItem builder) lands, it must also set `MEDIA_TYPE_AUDIO_BOOK`; otherwise TV audiobooks silently miss Phase 5 processing. Flagged in Task 2.
- **OEM audiofx variance:** `LoudnessEnhancer` construction can throw on some devices; controller swallows failures (`runCatching`) so playback never crashes, at the cost of silently no-op boost on those devices. Acceptable for Phase 5.
- **Device-only verification:** Tasks 5–6 require a physical device; CI cannot assert audible skip-silence/boost or `dumpsys` effect attachment.
