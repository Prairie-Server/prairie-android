# Audiobook Player — Phase 4: Sleep, Skip & Speed — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Deliver the Phase 4 slice of the audiobook player redesign (spec §4.2, §4.3, §6 phase 4): a new local DataStore-backed `AudiobookSettingsStore`, end-of-chapter / end-of-book sleep, a configurable skip interval (10/15/30/60s) replacing the fixed ±30s, speed presets (0.5×–3.0×) + fine adjust with a persisted default speed — all driven by the shared `AudiobookPlayerViewModel` and surfaced in **both** the phone (`androidApp`) and TV (`androidTvApp`) UIs. The audio-processing settings fields (`skipSilenceEnabled`, `volumeNormalizationEnabled`, `volumeBoostDb`) are **defined** in the store here but **consumed in Phase 5**.

**Architecture:** All logic lives in shared modules so both apps behave identically; only the picker surfaces differ (phone bottom sheets vs. TV focusable overlays).
- `shared` (commonMain, pure Kotlin): end-of-chapter / end-of-book trigger math added to the Phase-1 `AudiobookChapters`; unit-tested in `commonTest`.
- `android-shared` (androidMain): new `AudiobookSettingsStore` (DataStore) + the relocated `AudiobookPlayerViewModel` gains skip-interval, speed-default, and end-of-chapter/book sleep wiring.
- `androidApp`: phone `SkipIntervalSheet`; extend `AudiobookSpeedSheet` (default-speed persistence) and `AudiobookSleepTimerSheet` (End of book); transport reads the configured interval.
- `androidTvApp`: TV equivalents as focusable overlays inside the Phase-3 `TvAudiobookPlayerScreen`.

**Architecture note (real-tree state):** The spec describes Phases 1–3 as relocating the VM into `android-shared/.../common/player/` and adding `AudiobookChapters` to `shared`, plus a `TvAudiobookPlayerScreen`. In the current tree the VM and sheets still live under `androidApp/.../ui/screens/audiobook/`, and there is no TV audiobook screen yet. **This plan assumes Phases 1–3 have shipped** and therefore targets the post-Phase-1 paths the spec dictates (VM at `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/AudiobookPlayerViewModel.kt`, chapter math at `shared/src/commonMain/kotlin/com/continuum/app/common/audiobook/AudiobookChapters.kt`, TV screen under `androidTvApp/.../ui/screens/audiobook/`). If a path differs in the executing branch, adjust to the actual post-Phase-3 location — do not re-relocate code.

**Tech Stack:** Kotlin (KMP, `androidTarget`, `jvmTarget = 21`), Jetpack Compose (phone Material3 + TV Material), Media3 (interval/speed applied via the existing `MediaController`/pending-seek bridge), AndroidX DataStore Preferences (`libs.datastore.preferences`), kotlinx.coroutines (Flow + viewModelScope), JUnit + kotlin.test (`commonTest` / `androidUnitTest`). DI: Koin. Gradle test tasks: `:shared:testDebugUnitTest`, `:android-shared:testDebugUnitTest`, `:androidApp:testDebugUnitTest`, `:androidTvApp:testDebugUnitTest`.

Commands assume the repository root (`prairie-android`) is the cwd.

---

## File Structure

### Created

- `shared/src/commonMain/kotlin/com/continuum/app/common/audiobook/AudiobookChapters.kt` — **extended** (Phase 1 created it; Phase 4 adds the end-of-chapter / end-of-book boundary-crossing helpers). Pure chapter math, no Android deps.
- `shared/src/commonTest/kotlin/com/continuum/app/common/audiobook/AudiobookChaptersSleepTriggerTest.kt` — unit tests for the new boundary-crossing helpers.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/AudiobookSettingsStore.kt` — new local, per-profile DataStore store. Flows: `skipBackSecondsFlow`, `skipForwardSecondsFlow`, `defaultSpeedFlow`, `skipSilenceEnabledFlow`, `volumeNormalizationEnabledFlow`, `volumeBoostDbFlow`; setters that clamp + persist. Mirrors the DataStore mechanics of `AndroidPlayerSettingsStore` (per-profile file, profile-change re-derivation) but is **local-only** (no server flush) since these are device playback preferences.
- `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/AudiobookSettingsStoreTest.kt` — round-trip + clamp + default + profile-null tests, using an injected temp-file `dataStoreFactory` exactly like `AndroidPlayerSettingsStoreTest`.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookSkipIntervalSheet.kt` — phone bottom sheet to choose skip-back/skip-forward interval (10/15/30/60s).
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/audiobook/TvAudiobookSkipIntervalOverlay.kt` — TV focusable overlay equivalent.
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/audiobook/TvAudiobookSpeedOverlay.kt` — TV speed-preset + fine-adjust overlay.
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/audiobook/TvAudiobookSleepTimerOverlay.kt` — TV sleep-choice overlay (durations + End of chapter + End of book).

### Modified

- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/AudiobookPlayerViewModel.kt` — accept `AudiobookSettingsStore`; expose `skipBackSecondsFlow`/`skipForwardSecondsFlow`/`defaultSpeedFlow` (or fold into `uiState`); add `skipBack()` / `skipForward()` reading the configured interval; persist speed via `setSpeed`; extend `SleepTimerChoice` handling to `EndOfBook` and add the end-of-chapter/end-of-book position watcher (delegating to `AudiobookChapters`); seed `playbackSpeed` from `defaultSpeedFlow` on init.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/SleepTimerChoice.kt` — **relocate + extend** `SleepTimerChoice` (currently a nested type in the phone `AudiobookSleepTimerSheet.kt`) into `android-shared` so both apps share it; add `EndOfBook`.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/di/PlayerInfraModule.kt` — register `AudiobookSettingsStore` as a Koin `single`.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt` — pass the store into the `AudiobookPlayerViewModel` factory.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookSleepTimerSheet.kt` — add the **End of book** row; import the relocated `SleepTimerChoice`.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookSpeedSheet.kt` — add a "Set as default" affordance that persists `defaultSpeed`; keep the 0.5–3.0× slider + presets.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerScreen.kt` — replace `seekBy(-30.0)` / `seekBy(30.0)` with `viewModel.skipBack()` / `viewModel.skipForward()`; show the configured interval on the transport buttons; host the new `AudiobookSkipIntervalSheet`.
- `androidTvApp/.../ui/screens/audiobook/TvAudiobookPlayerScreen.kt` — wire skip-interval, speed, and sleep overlays into the Phase-3 TV screen; transport uses `skipBack()`/`skipForward()`.
- `androidTvApp/.../ui/screens/audiobook/TvAudiobookViewModelGlue` (or the existing Phase-3 TV factory) — pass the shared store-backed VM through (no new VM; same `AudiobookPlayerViewModel`).

---

### Task 1 — Chapter sleep-trigger math in `shared` (TDD)

**Files:**
- `shared/src/commonMain/kotlin/com/continuum/app/common/audiobook/AudiobookChapters.kt` (extend)
- `shared/src/commonTest/kotlin/com/continuum/app/common/audiobook/AudiobookChaptersSleepTriggerTest.kt` (create)

The VM's current `applySleepTimer` does ad-hoc chapter math inline (`AudiobookPlayerViewModel.kt:384-392`). Move the boundary logic into the pure `AudiobookChapters` object so it is unit-testable and shared with TV. Helpers needed:
- `currentChapterEndSeconds(chapters, positionSeconds): Double?` — end of the chapter containing `positionSeconds` (last chapter if past the end; `null` if no chapters).
- `bookEndSeconds(chapters, durationSeconds): Double` — `max(durationSeconds, last chapter endSeconds)` so a missing/short duration still terminates.
- `hasCrossedBoundary(previousSeconds, currentSeconds, boundarySeconds): Boolean` — true when polling steps from `< boundary` to `>= boundary` (the end-of-chapter/book watcher fires on the crossing, not every tick after).

- [ ] Write `AudiobookChaptersSleepTriggerTest` with failing cases:
  - `currentChapterEndSeconds` returns the containing chapter's `endSeconds` mid-chapter; returns the **last** chapter's `endSeconds` when `position` is past all starts; returns `null` for empty chapters.
  - Position exactly on a boundary (`position == chapter.endSeconds`) resolves to the **next** chapter's end (boundary belongs to the next chapter — matches the existing `>= start && < end` containment rule in the VM).
  - `bookEndSeconds` returns `durationSeconds` when it exceeds the last chapter end; returns the last chapter `endSeconds` when `durationSeconds` is 0/short.
  - `hasCrossedBoundary(10.0, 12.0, 11.0)` is `true`; `hasCrossedBoundary(12.0, 14.0, 11.0)` is `false` (already past); `hasCrossedBoundary(10.0, 10.5, 11.0)` is `false` (not yet); exact landing `hasCrossedBoundary(10.0, 11.0, 11.0)` is `true`.
  - Single-chapter degrade: one chapter spanning the whole book → `currentChapterEndSeconds` equals `bookEndSeconds`.
- [ ] Run (expect FAIL — symbols undefined):
  `./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.common.audiobook.AudiobookChaptersSleepTriggerTest"`
  Expected: compilation error / unresolved reference for the new helpers.
- [ ] Implement the three helpers in `AudiobookChapters.kt`. Use the same `VersionChapter` (`startSeconds`/`endSeconds`, both `Double`) the VM already consumes; reuse any existing `currentChapterIndex` helper Phase 1 added rather than re-deriving containment.
- [ ] Run the same command (expect PASS).
- [ ] Run the full shared suite to catch regressions:
  `./gradlew :shared:testDebugUnitTest`
- [ ] Commit: `feat(audiobook): add end-of-chapter/book sleep-trigger math to AudiobookChapters`

---

### Task 2 — Relocate + extend `SleepTimerChoice` with `EndOfBook`

**Files:**
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/SleepTimerChoice.kt` (create)
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookSleepTimerSheet.kt` (modify: remove the nested type, import the shared one, add the End-of-book row)

`SleepTimerChoice` is currently declared inside the phone sheet (`AudiobookSleepTimerSheet.kt:34-38`) so the TV app cannot reach it. Move it to `android-shared` and add `EndOfBook`.

- [ ] Create `SleepTimerChoice.kt` in `com.continuum.app.common.player`:
  ```kotlin
  package com.continuum.app.common.player

  /**
   * Sleep timer choices shared by the phone and TV audiobook players.
   * [EndOfChapter] / [EndOfBook] are resolved by the VM against
   * AudiobookChapters; [Off] cancels any active timer.
   */
  sealed class SleepTimerChoice {
      data object Off : SleepTimerChoice()
      data class Minutes(val minutes: Int) : SleepTimerChoice()
      data object EndOfChapter : SleepTimerChoice()
      data object EndOfBook : SleepTimerChoice()
  }
  ```
- [ ] Delete the nested `sealed class SleepTimerChoice` from `AudiobookSleepTimerSheet.kt` and add `import com.continuum.app.common.player.SleepTimerChoice`.
- [ ] Add the End-of-book row to the sheet's `items` list (between "End of chapter" and "Off"):
  `"End of book" to SleepTimerChoice.EndOfBook,`
- [ ] Extend the file's `choiceEquals` to handle `EndOfBook` (add the symmetric branch alongside `EndOfChapter`).
- [ ] No unit test (Compose picker — see Task 7 for adb manual-verify). Verify it compiles:
  `./gradlew :androidApp:compileDebugKotlinAndroid`
- [ ] Commit: `refactor(audiobook): share SleepTimerChoice across apps and add EndOfBook`

---

### Task 3 — `AudiobookSettingsStore` DataStore round-trips (TDD)

**Files:**
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/AudiobookSettingsStore.kt` (create)
- `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/AudiobookSettingsStoreTest.kt` (create)

Mirror the DataStore mechanics of `AndroidPlayerSettingsStore` **exactly** (per-profile file via injectable `dataStoreFactory`, `getActiveProfileId`-gated flows, profile-change re-derivation, `preferencesDataStoreFile`), but **local-only**: no `ServerSettingsFlusher`, no legacy migration, no scope-prefix/server keys — these are device playback prefs. Keep the same construction shape so the existing test pattern (temp-file factory, `mockContextStub`) transfers.

Store surface:
```kotlin
class AudiobookSettingsStore(
    private val context: Context,
    private val getActiveProfileId: suspend () -> String?,
    private val profileChangeSignal: Flow<Unit> = flowOf(Unit),
    private val dataStoreFactory: (profileId: String) -> DataStore<Preferences> = { profileId ->
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("continuum_audiobook_settings_${profileHash(profileId)}") },
        )
    },
) {
    val skipBackSecondsFlow: Flow<Int>          // default 30
    val skipForwardSecondsFlow: Flow<Int>       // default 30
    val defaultSpeedFlow: Flow<Float>           // default 1.0
    val skipSilenceEnabledFlow: Flow<Boolean>   // default false  (consumed Phase 5)
    val volumeNormalizationEnabledFlow: Flow<Boolean> // default false (Phase 5)
    val volumeBoostDbFlow: Flow<Int>            // default 0      (Phase 5)

    suspend fun setSkipBackSeconds(value: Int)      // coerce into ALLOWED_SKIP
    suspend fun setSkipForwardSeconds(value: Int)   // coerce into ALLOWED_SKIP
    suspend fun setDefaultSpeed(value: Float)       // coerceIn(0.5f, 3.0f)
    suspend fun setSkipSilenceEnabled(value: Boolean)
    suspend fun setVolumeNormalizationEnabled(value: Boolean)
    suspend fun setVolumeBoostDb(value: Int)        // coerceIn(0, 12)

    companion object { val ALLOWED_SKIP = listOf(10, 15, 30, 60) }
}
```
Use the `profileScopedFlow` / `currentScopeFlow.flatMapLatest` pattern from `AndroidPlayerSettingsStore` (a profile-id string is the scope; emit defaults when profile id is null). Persist `defaultSpeed` as a string (DataStore Preferences has no float key — `AndroidPlayerSettingsStore` stores `PlaybackSpeed` the same way via `stringPreferencesKey`).

- [ ] Write `AudiobookSettingsStoreTest` with failing cases (model the harness on `AndroidPlayerSettingsStoreTest`: `@get:Rule TemporaryFolder`, injected `dataStoreFactory` writing `File(tempFolder.root, "ds_$id.preferences_pb")`, `mockContextStub()`):
  - defaults: `skipBackSecondsFlow.first() == 30`, `skipForwardSecondsFlow.first() == 30`, `defaultSpeedFlow.first() == 1.0f`, `skipSilenceEnabledFlow.first() == false`, `volumeNormalizationEnabledFlow.first() == false`, `volumeBoostDbFlow.first() == 0`.
  - round-trip: `setSkipBackSeconds(15)` then `skipBackSecondsFlow.first() == 15`; `setSkipForwardSeconds(60)` → `60`; `setSkipSilenceEnabled(true)` → `true`; `setVolumeBoostDb(6)` → `6`.
  - skip clamp: `setSkipBackSeconds(99)` coerces to the nearest allowed value (define rule: not in `ALLOWED_SKIP` → fall back to `30`); assert result is `30`. `setSkipForwardSeconds(15)` stays `15`.
  - speed clamp: `setDefaultSpeed(10.0f)` → `3.0f`; `setDefaultSpeed(0.1f)` → `0.5f`.
  - boost clamp: `setVolumeBoostDb(99)` → `12`; `setVolumeBoostDb(-5)` → `0`.
  - profile-null: a store built with `getActiveProfileId = { null }` emits all defaults and `setSkipBackSeconds(10)` is a silent no-op (still default on read).
- [ ] Run (expect FAIL — class undefined):
  `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.AudiobookSettingsStoreTest"`
- [ ] Implement `AudiobookSettingsStore.kt` (real code, no server-sync surface). Reuse `intPreferencesKey` / `booleanPreferencesKey` / `stringPreferencesKey`; `profileHash` = first 16 hex of SHA-256 (copy the helper from `AndroidPlayerSettingsStore`).
- [ ] Run the same command (expect PASS).
- [ ] Run the module suite: `./gradlew :android-shared:testDebugUnitTest`
- [ ] Commit: `feat(audiobook): add local AudiobookSettingsStore (skip/speed/audio-processing prefs)`

---

### Task 4 — VM: configurable skip + speed default + end-of-chapter/book watcher (TDD where testable)

**Files:**
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/AudiobookPlayerViewModel.kt` (modify)
- `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/AudiobookPlayerSleepWatcherTest.kt` (create — see note on testability)

VM changes:
1. Constructor gains `private val audiobookSettings: AudiobookSettingsStore`.
2. On `init`, collect `defaultSpeedFlow` into `_uiState.playbackSpeed` (seed once; user changes still win) and collect skip flows into `uiState` fields `skipBackSeconds: Int` / `skipForwardSeconds: Int` (add to `AudiobookPlayerUiState`, defaults 30).
3. Replace the screens' direct `seekBy(±30.0)` with `fun skipBack()` = `seekBy(-uiState.value.skipBackSeconds.toDouble())` and `fun skipForward()` = `seekBy(+uiState.value.skipForwardSeconds.toDouble())`. Keep the existing `seekBy(delta)` for internal reuse.
4. `setSkipBackSeconds(Int)` / `setSkipForwardSeconds(Int)` delegate to the store (viewModelScope.launch); flow collection updates `uiState`.
5. `setSpeed(Float)` keeps clamping `0.5f..3.0f` (unchanged) and gains `fun setDefaultSpeed(Float)` that both applies live and persists via `audiobookSettings.setDefaultSpeed`.
6. **Sleep watcher:** extend `applySleepTimer` to handle `SleepTimerChoice.EndOfBook`, and replace the inline EndOfChapter math with a position watcher that uses `AudiobookChapters.currentChapterEndSeconds` / `bookEndSeconds` + `hasCrossedBoundary`. The watcher subscribes to position updates (the existing `onPositionChanged` 4 Hz path); when the boundary is crossed it pauses (`isPaused=true, isPlaying=false`) and resets the choice to `Off`. Resolve `EndOfChapter`'s target end **at apply time** against the then-current position (chapter boundaries don't move), then watch for the crossing.

**Testability note:** The VM is an Android `ViewModel` with Media3/session/network collaborators and is **not** practically unit-testable in `androidUnitTest` without a large fake graph. Per the spec's testing section the *trigger math* is what matters and it lives in `AudiobookChapters` (Task 1, already TDD'd). For the VM, write a **focused** test of the boundary-crossing decision only by extracting a tiny pure helper the watcher calls:

```kotlin
// in AudiobookPlayerViewModel companion (or a small internal object)
internal fun shouldPauseForSleep(
    choice: SleepTimerChoice,
    chapters: List<VersionChapter>,
    durationSeconds: Double,
    previousSeconds: Double,
    currentSeconds: Double,
): Boolean
```
This delegates to `AudiobookChapters` and is pure → unit-testable.

- [ ] Write `AudiobookPlayerSleepWatcherTest` (failing) covering `shouldPauseForSleep`:
  - `EndOfChapter`: returns `true` exactly when position crosses the containing chapter's end; `false` mid-chapter and `false` once already past.
  - `EndOfBook`: returns `true` only when crossing `bookEndSeconds`; `false` at a mid-book chapter boundary.
  - `Off` and `Minutes(n)`: always `false` (those are handled by the countdown path, not the watcher).
  - Empty chapters + `EndOfChapter`: degrades to book-end behavior (no spurious early pause).
- [ ] Run (expect FAIL):
  `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.AudiobookPlayerSleepWatcherTest"`
- [ ] Implement `shouldPauseForSleep` + wire it into the watcher; add the constructor param, `uiState` fields, `skipBack`/`skipForward`/`setSkipBackSeconds`/`setSkipForwardSeconds`/`setDefaultSpeed`, the `defaultSpeedFlow` seed, and the `EndOfBook` arm of `applySleepTimer`. Keep the existing `Minutes` countdown path intact.
- [ ] Run the same command (expect PASS), then the module suite:
  `./gradlew :android-shared:testDebugUnitTest`
- [ ] Verify both app modules still compile against the new VM signature:
  `./gradlew :androidApp:compileDebugKotlinAndroid :androidTvApp:compileDebugKotlinAndroid`
- [ ] Commit: `feat(audiobook): configurable skip interval, default speed, end-of-chapter/book sleep in VM`

---

### Task 5 — Koin wiring

**Files:**
- `android-shared/.../common/di/PlayerInfraModule.kt` (modify)
- `androidApp/.../android/di/AndroidModule.kt` (modify)

- [ ] In `PlayerInfraModule.kt`, register the store as a `single` (mirror the `PlayerSettingsStore` registration's profile-change-signal derivation from `ServerRegistry.activeEntry`):
  ```kotlin
  single {
      val registry = get<ServerRegistry>()
      val profileChangeSignal = registry.activeEntry
          .map { it?.profileId }
          .distinctUntilChanged()
          .map { Unit }
      AudiobookSettingsStore(
          context = androidContext(),
          getActiveProfileId = { get<ProfileRepository>().getActiveProfileId() },
          profileChangeSignal = profileChangeSignal,
      )
  }
  ```
- [ ] In `AndroidModule.kt`, add `audiobookSettings = get()` to the `AudiobookPlayerViewModel { ... }` factory (the VM now lives in `android-shared` but the phone factory still references it by its post-Phase-1 package).
- [ ] If the Phase-3 TV screen has its own VM factory in the TV Koin module, add `audiobookSettings = get()` there too. Confirm `playerInfraModule` is loaded by the TV app (it shares `android-shared`); if not, add it to the TV module list.
- [ ] Compile both apps:
  `./gradlew :androidApp:compileDebugKotlinAndroid :androidTvApp:compileDebugKotlinAndroid`
- [ ] Commit: `feat(audiobook): wire AudiobookSettingsStore into Koin for phone + TV`

---

### Task 6 — Phone UI: skip-interval sheet, speed default, sleep End-of-book, transport rewire

**Files:**
- `androidApp/.../ui/screens/audiobook/AudiobookSkipIntervalSheet.kt` (create)
- `androidApp/.../ui/screens/audiobook/AudiobookSpeedSheet.kt` (modify)
- `androidApp/.../ui/screens/audiobook/AudiobookPlayerScreen.kt` (modify)

Tests are **impractical** here (Material3 `ModalBottomSheet` + transport composition need an instrumented `ComposeTestRule`, out of scope for `androidUnitTest`). **Manual adb verification** per Task 8.

- [ ] Create `AudiobookSkipIntervalSheet` (model on `AudiobookSleepTimerSheet`'s `ModalBottomSheet` + `ChoiceRow` structure). Real composable:
  ```kotlin
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun AudiobookSkipIntervalSheet(
      skipBackSeconds: Int,
      skipForwardSeconds: Int,
      onSkipBackSelected: (Int) -> Unit,
      onSkipForwardSelected: (Int) -> Unit,
      onDismiss: () -> Unit,
  ) {
      val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
      ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState,
          containerColor = MaterialTheme.colorScheme.surface) {
          Column(Modifier.padding(24.dp).fillMaxWidth()) {
              Text("Skip back", style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.SemiBold)
              Spacer(Modifier.height(12.dp))
              listOf(10, 15, 30, 60).forEach { s ->
                  IntervalRow(label = "${s}s", selected = skipBackSeconds == s,
                      onClick = { onSkipBackSelected(s) })
              }
              Spacer(Modifier.height(20.dp))
              Text("Skip forward", style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.SemiBold)
              Spacer(Modifier.height(12.dp))
              listOf(10, 15, 30, 60).forEach { s ->
                  IntervalRow(label = "${s}s", selected = skipForwardSeconds == s,
                      onClick = { onSkipForwardSelected(s) })
              }
              Spacer(Modifier.height(8.dp))
          }
      }
  }
  ```
  Add a private `IntervalRow` reusing the selected-highlight + check-icon pattern from `AudiobookSleepTimerSheet.ChoiceRow` (independent copy so the sheets stay decoupled).
- [ ] In `AudiobookSpeedSheet.kt`, add a "Set as default" `TextButton` below the presets row that calls a new `onSetDefault: (Float) -> Unit` param with the current `pending` speed (keep `onSpeedChanged` for live preview). Update the call site (next step) to pass `viewModel::setDefaultSpeed`.
- [ ] In `AudiobookPlayerScreen.kt`:
  - Replace `onClick = { viewModel.seekBy(-30.0) }` → `onClick = { viewModel.skipBack() }` and `seekBy(30.0)` → `viewModel.skipForward()` (lines ~299 / ~324).
  - Label the skip buttons with the live interval from `uiState` (`"${uiState.skipBackSeconds}"` / `"${uiState.skipForwardSeconds}"`) instead of the hardcoded `15`/`30` glyphs.
  - Add an entry point (e.g. long-press the skip control, or a row item in the secondary bar) that opens `AudiobookSkipIntervalSheet`, wired to `viewModel.setSkipBackSeconds` / `viewModel.setSkipForwardSeconds`.
  - Pass `onSetDefault = viewModel::setDefaultSpeed` into `AudiobookSpeedSheet`.
- [ ] Compile: `./gradlew :androidApp:compileDebugKotlinAndroid`
- [ ] Commit: `feat(audiobook): phone skip-interval sheet, default-speed persistence, end-of-book sleep`

---

### Task 7 — TV UI: skip/speed/sleep overlays + transport rewire

**Files:**
- `androidTvApp/.../ui/screens/audiobook/TvAudiobookSkipIntervalOverlay.kt` (create)
- `androidTvApp/.../ui/screens/audiobook/TvAudiobookSpeedOverlay.kt` (create)
- `androidTvApp/.../ui/screens/audiobook/TvAudiobookSleepTimerOverlay.kt` (create)
- `androidTvApp/.../ui/screens/audiobook/TvAudiobookPlayerScreen.kt` (modify — Phase-3 screen)

TV uses **focusable overlays**, not phone bottom sheets (spec §4.9). Follow the focus + HUD patterns in `androidTvApp/.../ui/screens/player/TvPlayerHud.kt` and `TvPlayerControls.kt` (D-pad focus order, `Modifier.focusRequester`/`onFocusChanged`, TV `Surface`/`Button`). Tests **impractical** (D-pad focus + TV Material composition) → adb manual-verify (Task 8).

- [ ] `TvAudiobookSkipIntervalOverlay`: a focusable column/dialog presenting `listOf(10,15,30,60)` for skip-back and skip-forward, each a TV-focusable row; selected value highlighted. Params mirror the phone sheet (`skipBackSeconds`, `skipForwardSeconds`, `onSkipBackSelected`, `onSkipForwardSelected`, `onDismiss`). Use the TV focus idioms from `TvPlayerControls.kt`; ensure initial focus lands on the current selection.
- [ ] `TvAudiobookSpeedOverlay`: speed presets `listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)` as a focusable row plus D-pad fine-adjust (left/right nudges ±0.05 within `0.5f..3.0f`, reuse `formatSpeedLabel` if you expose it from the phone module or duplicate the tiny formatter). Params: `currentSpeed`, `onSpeedChanged`, `onSetDefault`, `onDismiss`.
- [ ] `TvAudiobookSleepTimerOverlay`: focusable list of `Minutes(5/10/15/30/45/60)` + `EndOfChapter` + `EndOfBook` + `Off`, using the shared `SleepTimerChoice`. Params: `currentChoice`, `minutesLeft`, `onChoiceSelected`, `onDismiss`.
- [ ] In `TvAudiobookPlayerScreen.kt`: rewire the transport prev/skip-back/play/skip-forward/next row so skip-back/forward call `viewModel.skipBack()` / `viewModel.skipForward()` and render the configured interval; add focusable affordances in the secondary cluster to open each overlay, wired to `setSkipBackSeconds`/`setSkipForwardSeconds`, `setSpeed`+`setDefaultSpeed`, and `applySleepTimer`.
- [ ] Compile: `./gradlew :androidTvApp:compileDebugKotlinAndroid`
- [ ] Commit: `feat(audiobook): TV skip/speed/sleep overlays + transport interval wiring`

---

### Task 8 — Full verification + manual on-device pass

**Files:** none (verification only).

- [ ] Whitespace + full gate (matches the repo's pre-MR convention):
  `git diff --check && ./gradlew :shared:testDebugUnitTest :android-shared:testDebugUnitTest :androidApp:testDebugUnitTest :androidApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest :androidTvApp:compileDebugKotlinAndroid`
  Expected: all test tasks green, both `compileDebugKotlinAndroid` succeed.
- [ ] adb manual-verify (phone, Pixel): change skip interval to 15s → skip buttons jump 15s and show "15"; set speed to 1.5× and "Set as default", kill+relaunch the player → resumes at 1.5×; pick **End of chapter** → playback pauses at the next chapter boundary; pick **End of book** → pauses at book end (use a short audiobook or seek near the end to verify quickly).
- [ ] adb manual-verify (TV, D-pad emulator or device): open each overlay with the remote, confirm focus order + selection highlight; skip-interval / speed / sleep choices apply identically to phone.
- [ ] Commit (docs/notes only if anything was adjusted): `test(audiobook): phase 4 verification pass`
- [ ] Use superpowers:finishing-a-development-branch to decide merge / PR.

---

## Self-review vs. spec

- **§4.3 store fields** — all six present (`skipBackSeconds`, `skipForwardSeconds`, `defaultSpeed`, `skipSilenceEnabled`, `volumeNormalizationEnabled`, `volumeBoostDb`); audio-processing trio defined here, consumed Phase 5. ✔ (Task 3)
- **"mirroring the existing player settings pattern"** — `AudiobookSettingsStore` copies the per-profile DataStore + injectable factory + profile-change re-derivation from `AndroidPlayerSettingsStore`, minus server-sync (correct: these are device-local playback prefs, like an iOS device override). ✔
- **§4.2 / §4.3 sleep End-of-chapter + End-of-book** — `SleepTimerChoice.EndOfBook` added + watcher delegates to pure `AudiobookChapters`. ✔ (Tasks 1, 2, 4)
- **Configurable skip 10/15/30/60 replacing ±30, both platforms** — `ALLOWED_SKIP`, `skipBack()/skipForward()`, phone sheet + TV overlay, transport rewired on both. ✔ (Tasks 3, 4, 6, 7)
- **Speed presets 0.5×–3.0× + fine adjust + persisted default** — existing `AudiobookSpeedSheet` slider/presets retained; `setDefaultSpeed` persists; VM seeds from `defaultSpeedFlow`; TV overlay added. ✔ (Tasks 3, 4, 6, 7)
- **"shared VM does the logic; each UI adds its picker surface"** — no logic in the screens; phone = sheets, TV = overlays; both call the same VM. ✔
- **Testing (§7)** — settings round-trips (Task 3), end-of-chapter/book trigger (Task 1), skip-interval application + speed clamp (Tasks 3/4) are TDD'd; picker UIs are explicitly adb-manual (instrumented Compose out of `androidUnitTest` scope). ✔
- **Out of scope kept out** — no notification/widget/Android Auto/audio-processing *consumption* here (Phases 5–8); store fields are defined-only. ✔

**Inline fix applied during review:** initial draft had the EndOfChapter watcher recompute the target every tick (would drift if the user seeks). Corrected in Task 4 to resolve the chapter-end target **at apply time** and watch a fixed boundary via `hasCrossedBoundary`, matching the spec's "pauses when the position crosses the current chapter's end."
