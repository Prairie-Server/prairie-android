# TV Subtitle Picker Dismissal and Sizing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close all playback chrome after a CC quick-picker selection and render plain-text television subtitles at consistent, couch-readable fixed SP sizes.

**Architecture:** Add a pure quick-picker chrome policy so selection and Back remain intentionally different, then consume it from the player screen's local quick-picker state. Add a pure Android subtitle text-size policy that keeps phone fractions intact but returns fixed SP values for television; `SubtitleManager` translates that policy into the appropriate Media3 API.

**Tech Stack:** Kotlin, Jetpack Compose for TV, Media3 `SubtitleView`, Robolectric/Kotlin test, Gradle.

## Global Constraints

- Selecting any CC quick-picker row, including Off, applies the selection, closes the picker, and hides playback controls.
- Back closes only the CC quick picker and leaves playback controls visible.
- The Settings HUD subtitle-track picker remains unchanged.
- TV plain-text subtitle sizes are exactly Small 18sp, Medium 22sp, Large 26sp, X-Large 32sp, and XX-Large 40sp.
- Phone subtitle fractions remain exactly 22.5/720, 29.25/720, 36/720, 45/720, and 54/720.
- ASS/SSA subtitles continue preserving authored libass styling.
- Do not change subtitle transactions, persistence, search, download, translation, remount, or failure behavior.

---

### Task 1: Close the CC quick picker and playback controls after selection

**Files:**
- Create: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/player/TvQuickSubtitlePickerChromePolicy.kt`
- Create: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/player/TvQuickSubtitlePickerChromePolicyTest.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/player/TvPlayerScreen.kt`

**Interfaces:**
- Produces: `TvQuickSubtitlePickerExit` with `Selection` and `Back`.
- Produces: `TvQuickSubtitlePickerChromeState(pickerVisible: Boolean, controlsVisible: Boolean)`.
- Produces: `tvQuickSubtitlePickerChromeState(exit: TvQuickSubtitlePickerExit): TvQuickSubtitlePickerChromeState`.
- Consumes: the policy in `TvPlayerScreen` after a valid quick-picker row resolves to a `SubtitleIdentity`.

- [ ] **Step 1: Write the failing quick-picker chrome policy test**

Create `TvQuickSubtitlePickerChromePolicyTest.kt`:

```kotlin
package org.siloserver.silo.tv.ui.screens.player

import kotlin.test.Test
import kotlin.test.assertEquals

class TvQuickSubtitlePickerChromePolicyTest {
    @Test
    fun selectionClosesPickerAndPlaybackControls() {
        assertEquals(
            TvQuickSubtitlePickerChromeState(
                pickerVisible = false,
                controlsVisible = false,
            ),
            tvQuickSubtitlePickerChromeState(TvQuickSubtitlePickerExit.Selection),
        )
    }

    @Test
    fun backClosesPickerButKeepsPlaybackControlsVisible() {
        assertEquals(
            TvQuickSubtitlePickerChromeState(
                pickerVisible = false,
                controlsVisible = true,
            ),
            tvQuickSubtitlePickerChromeState(TvQuickSubtitlePickerExit.Back),
        )
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests 'org.siloserver.silo.tv.ui.screens.player.TvQuickSubtitlePickerChromePolicyTest'
```

Expected: compilation fails because the policy types and function do not exist.

- [ ] **Step 3: Add the minimal pure chrome policy**

Create `TvQuickSubtitlePickerChromePolicy.kt`:

```kotlin
package org.siloserver.silo.tv.ui.screens.player

internal enum class TvQuickSubtitlePickerExit {
    Selection,
    Back,
}

internal data class TvQuickSubtitlePickerChromeState(
    val pickerVisible: Boolean,
    val controlsVisible: Boolean,
)

internal fun tvQuickSubtitlePickerChromeState(
    exit: TvQuickSubtitlePickerExit,
): TvQuickSubtitlePickerChromeState = when (exit) {
    TvQuickSubtitlePickerExit.Selection -> TvQuickSubtitlePickerChromeState(
        pickerVisible = false,
        controlsVisible = false,
    )
    TvQuickSubtitlePickerExit.Back -> TvQuickSubtitlePickerChromeState(
        pickerVisible = false,
        controlsVisible = true,
    )
}
```

- [ ] **Step 4: Wire distinct selection and Back outcomes into the quick picker**

In `TvPlayerScreen`, add a local helper beside `selectTvSubtitle`:

```kotlin
fun applyQuickSubtitlePickerExit(exit: TvQuickSubtitlePickerExit) {
    val chrome = tvQuickSubtitlePickerChromeState(exit)
    showQuickSubtitlePicker = chrome.pickerVisible
    viewModel.setControlsVisible(chrome.controlsVisible)
}
```

Change the `TvQuickSubtitlePicker` call to provide a selection callback that applies the existing selection first and then the selection exit:

```kotlin
TvQuickSubtitlePicker(
    presentation = subtitlePresentation,
    onSelect = { identity ->
        subtitlePresentation.onSelect(identity)
        applyQuickSubtitlePickerExit(TvQuickSubtitlePickerExit.Selection)
    },
    onDismiss = {
        applyQuickSubtitlePickerExit(TvQuickSubtitlePickerExit.Back)
    },
)
```

Change `TvQuickSubtitlePicker` to accept `onSelect: (SubtitleIdentity) -> Unit`, and forward only a successfully resolved row:

```kotlin
@Composable
private fun TvQuickSubtitlePicker(
    presentation: TvSubtitleHudPresentation,
    onSelect: (SubtitleIdentity) -> Unit,
    onDismiss: () -> Unit,
) {
    // existing setup remains
    HudPickerDialog(
        presentation = HudPickerPresentation(
            // existing title/options/selection/focus remain
            closeOnSelect = false,
            onFocused = presentation.onFocused,
            onSelect = { stableId ->
                presentation.rows
                    .firstOrNull { row -> row.stableId == stableId }
                    ?.let { row -> onSelect(row.identity) }
            },
        ),
        onClose = onDismiss,
    )
}
```

Keep the HUD subtitle picker and all shared subtitle transaction callbacks unchanged. Retain `closeOnSelect = false` because the local Compose state removes the quick picker after the valid selection callback; an invalid stable ID must not dismiss it.

- [ ] **Step 5: Run the focused policy and existing subtitle presentation tests**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest \
  --tests 'org.siloserver.silo.tv.ui.screens.player.TvQuickSubtitlePickerChromePolicyTest' \
  --tests 'org.siloserver.silo.tv.ui.screens.player.TvSubtitleHudStateTest'
```

Expected: PASS with no failures.

- [ ] **Step 6: Commit the quick-picker behavior**

```bash
git add androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/player/TvPlayerScreen.kt \
  androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/player/TvQuickSubtitlePickerChromePolicy.kt \
  androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/player/TvQuickSubtitlePickerChromePolicyTest.kt
git commit -m "fix(tv): dismiss player chrome after subtitle selection"
```

### Task 2: Render television plain-text subtitles with fixed SP presets

**Files:**
- Create: `android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/AndroidSubtitleTextSizePolicy.kt`
- Create: `android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/player/AndroidSubtitleTextSizePolicyTest.kt`
- Modify: `android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/SubtitleManager.kt`
- Modify: `android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/player/SubtitleManagerAppearanceTest.kt`

**Interfaces:**
- Produces: sealed `AndroidSubtitleTextSize` with `Fractional(fraction: Float)` and `FixedSp(sp: Float)`.
- Produces: `androidSubtitleTextSize(presentation: AndroidSubtitlePresentation, preset: SubtitleFontSizePreset): AndroidSubtitleTextSize`.
- Consumes: the result in `SubtitleManager.applyAppearance` via Media3 `setFractionalTextSize` or `setFixedTextSize`.

- [ ] **Step 1: Write the failing pure size-policy tests**

Create `AndroidSubtitleTextSizePolicyTest.kt`:

```kotlin
package org.siloserver.silo.common.player

import org.siloserver.silo.model.settings.SubtitleFontSizePreset
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidSubtitleTextSizePolicyTest {
    @Test
    fun televisionUsesFixedCouchReadableSpLadder() {
        val expected = mapOf(
            SubtitleFontSizePreset.Small to 18f,
            SubtitleFontSizePreset.Medium to 22f,
            SubtitleFontSizePreset.Large to 26f,
            SubtitleFontSizePreset.XLarge to 32f,
            SubtitleFontSizePreset.XXLarge to 40f,
        )

        expected.forEach { (preset, sp) ->
            assertEquals(
                AndroidSubtitleTextSize.FixedSp(sp),
                androidSubtitleTextSize(AndroidSubtitlePresentation.Television, preset),
            )
        }
    }

    @Test
    fun phonePreservesExistingFractionalLadder() {
        val expected = mapOf(
            SubtitleFontSizePreset.Small to 22.5f / 720f,
            SubtitleFontSizePreset.Medium to 29.25f / 720f,
            SubtitleFontSizePreset.Large to 36f / 720f,
            SubtitleFontSizePreset.XLarge to 45f / 720f,
            SubtitleFontSizePreset.XXLarge to 54f / 720f,
        )

        expected.forEach { (preset, fraction) ->
            assertEquals(
                AndroidSubtitleTextSize.Fractional(fraction),
                androidSubtitleTextSize(AndroidSubtitlePresentation.Phone, preset),
            )
        }
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests 'org.siloserver.silo.common.player.AndroidSubtitleTextSizePolicyTest'
```

Expected: compilation fails because `AndroidSubtitleTextSize` and `androidSubtitleTextSize` do not exist.

- [ ] **Step 3: Add the minimal pure subtitle-size policy**

Create `AndroidSubtitleTextSizePolicy.kt`:

```kotlin
package org.siloserver.silo.common.player

import org.siloserver.silo.model.settings.SubtitleFontSizePreset

internal sealed interface AndroidSubtitleTextSize {
    data class Fractional(val fraction: Float) : AndroidSubtitleTextSize
    data class FixedSp(val sp: Float) : AndroidSubtitleTextSize
}

internal fun androidSubtitleTextSize(
    presentation: AndroidSubtitlePresentation,
    preset: SubtitleFontSizePreset,
): AndroidSubtitleTextSize = when (presentation) {
    AndroidSubtitlePresentation.Phone -> AndroidSubtitleTextSize.Fractional(
        when (preset) {
            SubtitleFontSizePreset.Small -> 22.5f
            SubtitleFontSizePreset.Medium -> 29.25f
            SubtitleFontSizePreset.Large -> 36f
            SubtitleFontSizePreset.XLarge -> 45f
            SubtitleFontSizePreset.XXLarge -> 54f
        } / 720f,
    )
    AndroidSubtitlePresentation.Television -> AndroidSubtitleTextSize.FixedSp(
        when (preset) {
            SubtitleFontSizePreset.Small -> 18f
            SubtitleFontSizePreset.Medium -> 22f
            SubtitleFontSizePreset.Large -> 26f
            SubtitleFontSizePreset.XLarge -> 32f
            SubtitleFontSizePreset.XXLarge -> 40f
        },
    )
}
```

- [ ] **Step 4: Make `SubtitleManager` consume the size policy**

Import `androidx.annotation.Dimension`. In `applyAppearance`, replace the unconditional fractional call with:

```kotlin
when (val textSize = androidSubtitleTextSize(presentation, safe.fontSize)) {
    is AndroidSubtitleTextSize.Fractional -> subtitleView.setFractionalTextSize(
        textSize.fraction,
        /* fractionalRelativeToTextSize = */ false,
    )
    is AndroidSubtitleTextSize.FixedSp -> subtitleView.setFixedTextSize(
        Dimension.SP,
        textSize.sp,
    )
}
```

Delete the now-unused private `fractionalSizeFor` method. Do not change `setApplyEmbeddedStyles(false)`, `setApplyEmbeddedFontSizes(false)`, libass attachment, style, position, or video-bound synchronization.

- [ ] **Step 5: Remove the obsolete reflection assertions from the appearance test**

In `SubtitleManagerAppearanceTest.kt`, remove `phoneSubtitleTextFractionsAreOneEighthLarger`, `televisionSubtitleTextFractionsPreserveExistingScale`, and their private `fractionalSize` reflection helper. The new pure policy test replaces those exact-value assertions; keep every style, padding, libass, and video-bound test unchanged.

- [ ] **Step 6: Run size-policy and appearance tests**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest \
  --tests 'org.siloserver.silo.common.player.AndroidSubtitleTextSizePolicyTest' \
  --tests 'org.siloserver.silo.common.player.SubtitleManagerAppearanceTest'
```

Expected: PASS with no failures.

- [ ] **Step 7: Run the TV wiring test and compile the TV app**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest \
  --tests 'org.siloserver.silo.tv.ui.screens.player.TvSubtitleAspectSyncWiringTest' \
  :androidTvApp:compileDebugKotlinAndroid
```

Expected: PASS and BUILD SUCCESSFUL.

- [ ] **Step 8: Commit the fixed television sizing**

```bash
git add android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/AndroidSubtitleTextSizePolicy.kt \
  android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/SubtitleManager.kt \
  android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/player/AndroidSubtitleTextSizePolicyTest.kt \
  android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/player/SubtitleManagerAppearanceTest.kt
git commit -m "fix(tv): use readable fixed subtitle sizes"
```

### Task 3: Full verification and debug APK assembly

**Files:**
- Verify all files changed in Tasks 1 and 2.
- Build artifact: `androidTvApp/build/outputs/apk/debug/androidTvApp-arm64-v8a-debug.apk`.

**Interfaces:**
- Consumes: the completed quick-picker chrome and subtitle-size policies.
- Produces: a verified ARM64 TV debug APK; installation is intentionally not performed without separate user authorization.

- [ ] **Step 1: Run the full Android test and TV build command**

Run:

```bash
./gradlew test :androidTvApp:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Check repository cleanliness and patch formatting**

Run:

```bash
git diff --check
git status --short --branch
git rev-list --left-right --count upstream/main...main
```

Expected: no formatting errors, no uncommitted source changes, and local `main` remains zero commits behind `upstream/main`.

- [ ] **Step 3: Verify the ARM64 debug artifact exists**

Run:

```bash
test -f androidTvApp/build/outputs/apk/debug/androidTvApp-arm64-v8a-debug.apk
```

Expected: exit code 0.

- [ ] **Step 4: Report completion**

Report the two implementation commits, focused and full test results, APK path, repository divergence, and whether anything was pushed or installed.
