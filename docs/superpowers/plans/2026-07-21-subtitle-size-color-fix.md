# Subtitle Size and Color Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the standard subtitle-size presets and make TV HUD color swatches activate with the remote OK button.

**Architecture:** Keep the shared preset enum, persisted names, and standard size mappings stable. Make each color swatch a single Compose `clickable` focus target so D-pad focus and OK activation reach the same callback.

**Tech Stack:** Kotlin Multiplatform, Jetpack Compose for TV, Media3 `SubtitleView`, Kotlin/JUnit tests, Gradle, ADB.

## Global Constraints

- Preset sizes must be Large 56/0.050, XLarge 68/0.060, and XXLarge 82/0.072.
- Small 36/0.032 and Medium 44/0.040 remain unchanged.
- `Large` remains the default preset and serialized preset names remain unchanged.
- Color palette layout, labels, selection semantics, subtitle timing, backgrounds, font family, and position remain unchanged.
- Existing dirty-worktree changes must be preserved.

---

### Task 1: Restore the standard shared and Android subtitle sizes

**Files:**
- Modify: `shared/src/commonTest/kotlin/org/prairieserver/prairie/model/settings/SubtitleAppearanceTest.kt`
- Modify: `shared/src/commonMain/kotlin/org/prairieserver/prairie/model/settings/SubtitleAppearance.kt`
- Modify: `android-shared/src/androidUnitTest/kotlin/org/prairieserver/prairie/common/player/SubtitleManagerAppearanceTest.kt`
- Modify: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/player/SubtitleManager.kt`

**Interfaces:**
- Consumes: `SubtitleFontSizePreset.pointSize: Double` and `SubtitleManager.fractionalSizeFor(SubtitleFontSizePreset): Float`.
- Produces: stable preset names with the approved larger numeric mappings.

- [x] **Step 1: Write failing shared and Android mapping tests**

Update the expected upper preset values while retaining the existing Small, Medium, and default assertions:

```kotlin
assertEquals(56.0, SubtitleFontSizePreset.Large.pointSize)
assertEquals(68.0, SubtitleFontSizePreset.XLarge.pointSize)
assertEquals(82.0, SubtitleFontSizePreset.XXLarge.pointSize)
assertEquals(SubtitleFontSizePreset.Large, SubtitleAppearance.DEFAULT.fontSize)
assertEquals(56.0, SubtitleAppearance.DEFAULT.fontSize.pointSize)
```

```kotlin
assertEquals(0.050f, method.invoke(SubtitleManager(), SubtitleFontSizePreset.Large) as Float)
assertEquals(0.060f, method.invoke(SubtitleManager(), SubtitleFontSizePreset.XLarge) as Float)
assertEquals(0.072f, method.invoke(SubtitleManager(), SubtitleFontSizePreset.XXLarge) as Float)
```

- [x] **Step 2: Run tests and verify the expected failures**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests 'org.prairieserver.prairie.model.settings.SubtitleAppearanceTest' \
  :android-shared:testDebugUnitTest --tests 'org.prairieserver.prairie.common.player.SubtitleManagerAppearanceTest'
```

Expected: assertions report the enlarged `68/82/96` and `0.060/0.072/0.084` mappings.

- [x] **Step 3: Implement the approved mappings**

Set the shared point sizes to:

```kotlin
SubtitleFontSizePreset.Large -> 56.0
SubtitleFontSizePreset.XLarge -> 68.0
SubtitleFontSizePreset.XXLarge -> 82.0
```

Set the Android fractions to:

```kotlin
SubtitleFontSizePreset.Large -> 0.050f
SubtitleFontSizePreset.XLarge -> 0.060f
SubtitleFontSizePreset.XXLarge -> 0.072f
```

- [x] **Step 4: Run the focused tests and verify they pass**

Run the command from Step 2. Expected: both test classes pass.

### Task 2: Make TV color swatches activate with OK

**Files:**
- Modify: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerControlsUsabilityTest.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerHud.kt`

**Interfaces:**
- Consumes: `StyleColorSwatch(..., onClick: () -> Unit)` and Compose `Modifier.clickable` keyboard behavior.
- Produces: one focus/action target per text, background, or outline color swatch.

- [x] **Step 1: Add a failing focus/activation regression test**

Add this test to `TvPlayerControlsUsabilityTest`:

```kotlin
@Test
fun subtitleColorSwatchesUseOneActivatingFocusTarget() {
    val swatchBlock = hudSource
        .substringAfter("private fun StyleColorSwatch(")
        .substringBefore("private fun hexToColor")

    assertFalse(
        swatchBlock.contains(".focusable("),
        "A separate focusable target receives D-pad focus but cannot invoke the swatch callback on OK",
    )
    assertTrue(
        swatchBlock.contains(".clickable(enabled = enabled, interactionSource = interactionSource"),
        "The clickable target must own both focus and OK activation",
    )
}
```

- [x] **Step 2: Run the focused TV test and verify it fails**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest \
  --tests 'org.prairieserver.prairie.tv.ui.screens.player.TvPlayerControlsUsabilityTest.subtitleColorSwatchesUseOneActivatingFocusTarget'
```

Expected: failure reports that the swatch block still contains `.focusable(`.

- [x] **Step 3: Remove the redundant focus target**

In `StyleColorSwatch`, remove:

```kotlin
.focusable(enabled = enabled, interactionSource = interactionSource)
```

Retain the existing `clickable` modifier with the same `interactionSource`, and remove the now-unused `androidx.compose.foundation.focusable` import if no other code in the file uses it.

- [x] **Step 4: Run the focused TV test and verify it passes**

Run the command from Step 2. Expected: the regression test passes.

### Task 3: Full verification and Shield validation

**Files:**
- Verify: all files changed by Tasks 1 and 2.
- Build artifact: `androidTvApp/build/outputs/apk/debug/androidTvApp-arm64-v8a-debug.apk`

**Interfaces:**
- Consumes: the recalibrated appearance model and corrected HUD swatch callback.
- Produces: a verified APK installed on `192.168.1.128:5555`.

- [x] **Step 1: Run formatting and diagnostic-artifact checks**

```bash
git diff --check
rg -n 'TvSubtitleDebug|SubtitlePayloadDebugDataSource' androidTvApp/src android-shared/src
```

Expected: no whitespace errors and no diagnostic instrumentation matches.

- [x] **Step 2: Run the full relevant test/build suite**

```bash
./gradlew :shared:testDebugUnitTest :android-shared:testDebugUnitTest \
  :androidTvApp:testDebugUnitTest :androidApp:testDebugUnitTest \
  :androidTvApp:assembleDebug
```

Expected: `BUILD SUCCESSFUL` with zero test failures.

- [x] **Step 3: Install the ARM64 APK**

```bash
adb -s 192.168.1.128:5555 install -r \
  androidTvApp/build/outputs/apk/debug/androidTvApp-arm64-v8a-debug.apk
```

Expected: `Success`.

- [x] **Step 4: Verify color activation and live rendering**

Start playback with a text subtitle, open HUD → Subtitles, focus Yellow, and press OK. Verify the DataStore payload contains:

```json
"fontColor":"#facc15"
```

Dismiss the HUD and capture the Shield screen during a cue. Expected: the live cue is yellow, uses no background, and uses the selected standard preset size.
