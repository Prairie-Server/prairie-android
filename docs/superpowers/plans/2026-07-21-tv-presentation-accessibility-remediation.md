# TV Presentation and Accessibility Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete all open TV layout, visual-state, accessibility, and cleanup findings without exposing parked routes.

**Architecture:** Canonical shell spacing tokens replace local literals. UI state remains in existing components, while small empty/visibility decisions are pure and tested. Accessibility semantics are attached at the selectable surface so focus, selected state, and spoken labels describe the same control.

**Tech Stack:** Kotlin 2.1, Jetpack Compose for TV, Compose semantics, kotlin.test/JUnit.

## Global Constraints

- Do not expose rich admin or Watch Together routes.
- Do not change finding 41's calendar minimum height without a reproduced child constraint.
- Selection cannot be communicated by color alone.
- Decorative images keep `contentDescription = null`; actionable swatches and selector rows do not.
- Every production behavior change begins with a failing test or source-contract test.

---

### Task 1: Separate For You controls from scrolling content

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/recommendations/TvRecommendationsScreen.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/performance/TvListPerformanceSourceTest.kt`

**Interfaces:**
- The fixed filter row owns an explicit measured/reserved vertical band; the section feed starts below that band and does not scroll beneath a transparent overlay.

- [ ] **Step 1: Add a failing source contract**

Require the recommendations content padding/top inset to include a named filter-band height token and reject a feed using only `TvTopMenuLayout.contentTopInset` beneath the pill overlay.

- [ ] **Step 2: Run source test and verify RED**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvListPerformanceSourceTest'`

Expected: the new assertion fails because the first section starts under the current pill row.

- [ ] **Step 3: Reserve the controls band**

Define one `RecommendationsFilterBandHeight` value from the pill height plus vertical spacing and add it to the feed inset. Keep the controls fixed and add the existing dark scrim treatment if content can still approach the band during overscroll.

- [ ] **Step 4: Run source tests and compile**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvListPerformanceSourceTest' :androidTvApp:compileDebugKotlinAndroid`

Expected: tests and compilation pass.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/recommendations/TvRecommendationsScreen.kt androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/performance/TvListPerformanceSourceTest.kt
git commit -m "fix(tv): reserve for-you filter space"
```

### Task 2: Canonicalize Skyline horizontal inset

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvSkylineSectionFeed.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/theme/TvSkylineTokenParityTest.kt`

**Interfaces:**
- Skyline content uses the same horizontal safe-area token as the top-bar chrome.

- [ ] **Step 1: Add a failing token-parity assertion**

Assert the Skyline source references `TvTopMenuLayout`'s canonical horizontal/trailing inset or `TvSpacing.safeAreaX`, and no local `40.dp` literal controls its section start.

- [ ] **Step 2: Run test and verify RED**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvSkylineTokenParityTest'`

Expected: assertion fails against the 40 dp local inset.

- [ ] **Step 3: Replace the literal with the canonical token**

Use the same 44 dp token consumed by top-bar chrome and profile overlay alignment. Do not change card widths or row gaps.

- [ ] **Step 4: Run token tests and compile**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvSkylineTokenParityTest' :androidTvApp:compileDebugKotlinAndroid`

Expected: tests and compilation pass.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvSkylineSectionFeed.kt androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/theme/TvSkylineTokenParityTest.kt
git commit -m "fix(tv): align skyline safe-area inset"
```

### Task 3: Render a focusable Home empty state

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/home/TvHomeScreen.kt`
- Create: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/home/TvHomeContentStateTest.kt`

**Interfaces:**
- Produces: `internal fun shouldShowHomeEmptyState(isLoading: Boolean, error: String?, visibleSectionCount: Int): Boolean`.
- Empty state offers a focusable Refresh action.

- [ ] **Step 1: Add failing empty-state tests**

```kotlin
package org.prairieserver.prairie.tv.ui.screens.home

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvHomeContentStateTest {
    @Test fun filteredEmptyHomeShowsEmptyState() {
        assertTrue(shouldShowHomeEmptyState(false, null, 0))
    }

    @Test fun loadingAndErrorOwnTheirStates() {
        assertFalse(shouldShowHomeEmptyState(true, null, 0))
        assertFalse(shouldShowHomeEmptyState(false, "offline", 0))
        assertFalse(shouldShowHomeEmptyState(false, null, 2))
    }
}
```

- [ ] **Step 2: Run test and verify RED**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvHomeContentStateTest'`

Expected: missing helper compilation failure.

- [ ] **Step 3: Implement empty content**

```kotlin
internal fun shouldShowHomeEmptyState(
    isLoading: Boolean,
    error: String?,
    visibleSectionCount: Int,
): Boolean = !isLoading && error == null && visibleSectionCount == 0
```

Render `CalendarMessage`-equivalent Home copy and a Refresh button with the initial content focus requester.

- [ ] **Step 4: Run tests and compile**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvHomeContentStateTest' :androidTvApp:compileDebugKotlinAndroid`

Expected: tests and compilation pass.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/home/TvHomeScreen.kt androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/home/TvHomeContentStateTest.kt
git commit -m "fix(tv): show empty home state"
```

### Task 4: Remove fixed-height Search status clipping

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/search/TvSearchScreen.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerControlsUsabilityTest.kt`

**Interfaces:**
- Search status uses text-driven height with at least one line and no fixed 18 dp container.

- [ ] **Step 1: Add a failing source-contract assertion**

Require the status text to use `minLines = 1` and reject `.height(18.dp)` in the result-status block.

- [ ] **Step 2: Run test and verify RED**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvPlayerControlsUsabilityTest'`

Expected: the source assertion fails against the fixed height.

- [ ] **Step 3: Use text-driven sizing**

Remove the fixed height, retain stable layout with `minLines = 1`, and let the existing typography determine line height at accessibility scale.

- [ ] **Step 4: Run source tests and compile**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvPlayerControlsUsabilityTest' :androidTvApp:compileDebugKotlinAndroid`

Expected: tests and compilation pass.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/search/TvSearchScreen.kt androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerControlsUsabilityTest.kt
git commit -m "fix(tv): let search status scale"
```

### Task 5: Keep marquee title visible until logo succeeds

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvSkylineSectionFeed.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/components/TvSkylineSectionFeedSourceTest.kt`

**Interfaces:**
- Text title is the default state. Successful image loading crossfades to the logo; loading/error keeps title text.

- [ ] **Step 1: Add a failing source-contract test**

Require an explicit successful-logo state and text-title rendering outside the `logoUrl == null` branch.

- [ ] **Step 2: Run test and verify RED**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvSkylineSectionFeedSourceTest'`

Expected: assertion fails because any nonblank URL currently reserves a blank image slot.

- [ ] **Step 3: Implement success-gated crossfade**

Reset `logoLoaded` when URL changes, set it only from the image success callback, render text while false, and animate/crossfade the logo after success. Error leaves text visible.

- [ ] **Step 4: Run source tests and compile**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvSkylineSectionFeedSourceTest' :androidTvApp:compileDebugKotlinAndroid`

Expected: tests and compilation pass.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvSkylineSectionFeed.kt androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/components/TvSkylineSectionFeedSourceTest.kt
git commit -m "fix(tv): retain marquee title while logo loads"
```

### Task 6: Distinguish HUD focus and selection states

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerHud.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerControlsUsabilityTest.kt`

**Interfaces:**
- Focused rows use opaque white with dark foreground.
- Selected-unfocused rows use `Color.White.copy(alpha = 0.14f)` with light foreground and a check/selected semantic.
- Ordinary rows remain transparent.

- [ ] **Step 1: Add a failing source-contract test**

Require a three-branch focus/selected/default color expression and the 0.14 selected-unfocused alpha.

- [ ] **Step 2: Run test and verify RED**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvPlayerControlsUsabilityTest'`

Expected: assertion fails against `isFocused || isSelected` sharing white.

- [ ] **Step 3: Implement distinct visuals**

Use separate background and foreground branches and retain the check icon. Do not reduce selected state to tint alone.

- [ ] **Step 4: Run source tests and compile**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvPlayerControlsUsabilityTest' :androidTvApp:compileDebugKotlinAndroid`

Expected: tests and compilation pass.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerHud.kt androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerControlsUsabilityTest.kt
git commit -m "fix(tv): separate hud focus and selection"
```

### Task 7: Render faithful subtitle appearance preview

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerHud.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvSubtitleAppearanceOptions.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvSubtitleAppearanceOptionsTest.kt`

**Interfaces:**
- Produces preview style from selected size, font family, foreground/background, vertical position, and outline mode.
- Outline uses text shadow/stroke approximation, never a rectangular border.

- [ ] **Step 1: Add failing style-mapping tests**

Add tests proving Small/Medium/Large map to distinct preview text sizes, selected font maps to the corresponding Compose family, Top/Middle/Bottom map to distinct alignments, and Outline maps to shadow/stroke configuration.

- [ ] **Step 2: Run tests and verify RED**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvSubtitleAppearanceOptionsTest'`

Expected: missing preview mappings or outline assertion failure.

- [ ] **Step 3: Apply all selected appearance values**

Build the preview from the shared appearance option model. Remove the Box border used as outline, apply text shadows/stroke approximation, and align the preview sample vertically in its stage.

- [ ] **Step 4: Run tests and compile**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvSubtitleAppearanceOptionsTest' :androidTvApp:compileDebugKotlinAndroid`

Expected: tests and compilation pass.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerHud.kt androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvSubtitleAppearanceOptions.kt androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvSubtitleAppearanceOptionsTest.kt
git commit -m "fix(tv): mirror subtitle appearance in preview"
```

### Task 8: Complete selectable semantics and swatch descriptions

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvOptionDialog.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/settings/TvSettingsScreen.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerHud.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvAiTranslateDialog.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerControlsUsabilityTest.kt`

**Interfaces:**
- Selectable surfaces apply `Modifier.semantics { selected = isSelected }`.
- Color swatches expose `contentDescription = "$label${if (isSelected) ", selected" else ""}"` or equivalent merged text and selected semantics.

- [ ] **Step 1: Add failing source-contract assertions**

Require `selected =` semantics in each picker implementation and a non-null semantic label at color-swatch call sites.

- [ ] **Step 2: Run source tests and verify RED**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvPlayerControlsUsabilityTest'`

Expected: assertions fail for settings/HUD/AI rows and swatches not covered by the baseline option-dialog fix.

- [ ] **Step 3: Attach semantics at actionable surfaces**

Apply semantics after clickable/selectable modifiers so the row is one merged control. Keep decorative check icons and purely decorative images at null descriptions.

- [ ] **Step 4: Run source tests and compile**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvPlayerControlsUsabilityTest' :androidTvApp:compileDebugKotlinAndroid`

Expected: tests and compilation pass.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvOptionDialog.kt androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/settings/TvSettingsScreen.kt androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerHud.kt androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvAiTranslateDialog.kt androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerControlsUsabilityTest.kt
git commit -m "fix(tv): expose picker selection semantics"
```

### Task 9: Delete dead picker and correct index contracts

**Files:**
- Delete: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvFullScreenPicker.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/detail/TvPlaybackFormatting.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/detail/TvPlaybackSelectorRow.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerControlsUsabilityTest.kt`

**Interfaces:**
- Comments state that subtitle selector values are combined-space selection indexes.

- [ ] **Step 1: Add failing cleanup assertions**

Assert there are no `TvFullScreenPicker(` call sites, the dead source file is absent, and the two index-contract comments contain `combined` and do not claim direct `subtitleTracks` ordinal indexing.

- [ ] **Step 2: Run source tests and verify RED**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvPlayerControlsUsabilityTest'`

Expected: file-presence or stale-comment assertion fails.

- [ ] **Step 3: Remove dead code and update comments**

Delete only the unreferenced component. Update contract comments without changing runtime mapping.

- [ ] **Step 4: Run tests and compile**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvPlayerControlsUsabilityTest' :androidTvApp:compileDebugKotlinAndroid`

Expected: tests and compilation pass.

- [ ] **Step 5: Commit**

```bash
git add -A androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvFullScreenPicker.kt androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/detail/TvPlaybackFormatting.kt androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/detail/TvPlaybackSelectorRow.kt androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerControlsUsabilityTest.kt
git commit -m "chore(tv): remove dead picker and fix index docs"
```

### Task 10: Presentation-stage and full audit verification

- [ ] Run `./gradlew :androidTvApp:testDebugUnitTest --rerun-tasks`.
- [ ] Run `./gradlew :androidTvApp:assembleDebug`.
- [ ] Run `git diff --check`.
- [ ] Re-read `docs/superpowers/specs/2026-07-21-tv-ui-audit-remediation-design.md` and account for findings 1 through 51.
- [ ] Confirm finding 41 received no arbitrary parent minimum-height change.
- [ ] Confirm no rich admin or Watch Together route was exposed.
