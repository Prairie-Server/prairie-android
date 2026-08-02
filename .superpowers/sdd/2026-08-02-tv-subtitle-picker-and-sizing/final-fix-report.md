## Final review fix wave

### Findings addressed

1. Updated `SubtitleManager.applyAppearance` KDoc to describe both Media3 sizing modes accurately: phone subtitles use fractional view-height sizing and television subtitles use fixed SP sizing.
2. Added seam-level regression coverage for the Media3 setter selection and the quick-picker action sequence. The picker now delegates its valid-row resolution to a narrow dispatcher that applies the `SubtitleIdentity` callback before it invokes the selection-complete callback; an unknown stable ID invokes neither callback. The subtitle-size seam now delegates to a narrow Media3 applier that maps `FixedSp` to `setFixedTextSize(Dimension.SP, ...)` and `Fractional` to `setFractionalTextSize(..., false)`.

### Files

- `android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/AndroidSubtitleTextSizePolicy.kt`
- `android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/SubtitleManager.kt`
- `android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/player/AndroidSubtitleTextSizePolicyTest.kt`
- `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/player/TvQuickSubtitlePickerChromePolicy.kt`
- `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/player/TvPlayerScreen.kt`
- `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/player/TvQuickSubtitlePickerChromePolicyTest.kt`

### RED/GREEN evidence

The new production helpers were introduced test-first.

- RED: `./gradlew :androidTvApp:testDebugUnitTest --tests 'org.siloserver.silo.tv.ui.screens.player.TvQuickSubtitlePickerChromePolicyTest'` failed at `compileDebugUnitTestKotlinAndroid` with unresolved `dispatchTvQuickSubtitlePickerSelection`.
- RED: `./gradlew :android-shared:testDebugUnitTest --tests 'org.siloserver.silo.common.player.AndroidSubtitleTextSizePolicyTest'` failed at `compileDebugUnitTestKotlinAndroid` with unresolved `applyAndroidSubtitleTextSize`.
- GREEN: the focused quick-picker test passed after adding the dispatcher. Its Debug XML records 4 tests, 0 failures, 0 errors.
- GREEN: the focused subtitle-size test passed after adding the Media3 applier. Its Debug XML records 4 tests, 0 failures, 0 errors.

### Verification

Commands run and results:

```text
./gradlew :android-shared:testDebugUnitTest \
  --tests 'org.siloserver.silo.common.player.AndroidSubtitleTextSizePolicyTest' \
  --tests 'org.siloserver.silo.common.player.SubtitleManagerAppearanceTest'
BUILD SUCCESSFUL in 9s
```

Debug result XML: `AndroidSubtitleTextSizePolicyTest` 4/4 passing; `SubtitleManagerAppearanceTest` 28/28 passing.

```text
./gradlew :androidTvApp:testDebugUnitTest \
  --tests 'org.siloserver.silo.tv.ui.screens.player.TvQuickSubtitlePickerChromePolicyTest' \
  --tests 'org.siloserver.silo.tv.ui.screens.player.TvSubtitleHudStateTest'
BUILD SUCCESSFUL in 3s
```

Debug result XML: `TvQuickSubtitlePickerChromePolicyTest` 4/4 passing; `TvSubtitleHudStateTest` 13/13 passing.

```text
./gradlew :androidTvApp:compileDebugKotlinAndroid
BUILD SUCCESSFUL in 658ms
```

`git diff --check` completed with exit code 0.

### Self-review

- The selection callback remains before chrome dismissal; Off is a normal resolved identity and follows the same order.
- Unknown stable IDs return without selection or dismissal.
- Back continues to use the existing Back policy path, preserving playback controls.
- The Settings HUD picker is untouched.
- The existing phone fractional ladder, television fixed SP ladder, and libass path are unchanged.
- No device interaction, installation, push, or unrelated code was performed.

### Residual concern

Media3's `SubtitleView` does not expose its configured default text-size type or value. The regression test therefore inspects the real `SubtitleView`'s private configuration fields after calling the production applier; it does not inspect source text or mock the setter. This is the narrowest practical assertion of which Media3 sizing API took effect without refactoring the player or adding a wrapper around Media3. A Media3 internal-field rename would require updating the test despite unchanged app behavior.
