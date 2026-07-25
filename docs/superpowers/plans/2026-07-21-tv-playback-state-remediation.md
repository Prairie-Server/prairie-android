# TV Playback State Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete marquee, scrubber, next-up persistence, and PiP playback-state behavior from the TV audit.

**Architecture:** Mutable Compose state remains in existing view models/state holders. Small identity and visibility decisions are extracted as pure functions for JVM tests. Delayed marquee publication uses one cancellable coroutine keyed by the focused content ID.

**Tech Stack:** Kotlin 2.1, Jetpack Compose, Kotlin coroutines, Media3-facing Android TV player state, kotlin.test/JUnit.

## Global Constraints

- Preserve the fixed combined subtitle index mapping from baseline commit `29a258cb`.
- Persist next-up choices through the same repository/session APIs used by ordinary detail selectors.
- Only enrichment matching the currently displayed content ID may publish.
- Every behavior change begins with a failing focused test.

---

### Task 1: Publish active marquee enrichment safely

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvFocusMarqueeModel.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/components/TvFocusMarqueeModelTest.kt`

**Interfaces:**
- `TvFocusMarqueeState.applyEnrichment(contentId, enrichment)` always caches and also sets `enrichment` when `content?.contentId == contentId`.

- [ ] **Step 1: Add failing active/stale identity tests**

```kotlin
@Test fun activeEnrichmentPublishesImmediately() {
    val state = TvFocusMarqueeState(fetchDetail = null)
    state.preview(content("active"), "Continue")
    state.applyEnrichment("active", enrichment(detailLine = "Aired today"))
    assertEquals("Aired today", state.displayContent?.detailLine)
}

@Test fun staleEnrichmentOnlyWarmsCache() {
    val state = TvFocusMarqueeState(fetchDetail = null)
    state.preview(content("active"), "Continue")
    state.applyEnrichment("old", enrichment(detailLine = "Old"))
    assertNotEquals("Old", state.displayContent?.detailLine)
}
```

- [ ] **Step 2: Run tests and verify RED**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvFocusMarqueeModelTest*Enrichment*'`

Expected: active enrichment assertion fails because the implementation only caches.

- [ ] **Step 3: Implement identity-guarded publication**

```kotlin
internal fun applyEnrichment(contentId: String, value: TvMarqueeEnrichment) {
    enrichmentCache[contentId] = value
    if (content?.contentId == contentId) enrichment = value
}
```

- [ ] **Step 4: Run model tests and verify GREEN**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvFocusMarqueeModelTest'`

Expected: all marquee state tests pass.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvFocusMarqueeModel.kt androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/components/TvFocusMarqueeModelTest.kt
git commit -m "fix(tv): publish active marquee enrichment"
```

### Task 2: Add the 150 ms marquee focus-rest debounce

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvSkylineSectionFeed.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvFocusMarqueeModel.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/components/TvFocusMarqueeModelTest.kt`

**Interfaces:**
- Produces: `internal const val TvMarqueeFocusRestMillis = 150L`.
- The feed holds `previewCandidate`; a `LaunchedEffect(previewCandidate?.contentId)` delays 150 ms and commits only the unchanged candidate.

- [ ] **Step 1: Add a failing debounce contract test**

```kotlin
@Test fun focusRestDelayMatchesTvOs() {
    assertEquals(150L, TvMarqueeFocusRestMillis)
}
```

Add a source assertion in `TvSkylineSectionFeedSourceTest` requiring `delay(TvMarqueeFocusRestMillis)` inside a `LaunchedEffect` keyed by candidate identity.

- [ ] **Step 2: Run tests and verify RED**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvFocusMarqueeModelTest.focusRestDelayMatchesTvOs' --tests '*TvSkylineSectionFeedSourceTest'`

Expected: missing constant or source-contract assertion failure.

- [ ] **Step 3: Implement cancellable debounce**

```kotlin
internal const val TvMarqueeFocusRestMillis = 150L

LaunchedEffect(previewCandidate?.contentId) {
    val candidate = previewCandidate ?: return@LaunchedEffect
    delay(TvMarqueeFocusRestMillis)
    if (previewCandidate?.contentId == candidate.contentId) {
        marquee.preview(candidate.item, candidate.rowTitle)
    }
}
```

Card focus callbacks update `previewCandidate`; they no longer publish directly. Initial seeded content remains immediate so entry never shows a blank hero.

- [ ] **Step 4: Run focused tests and compile**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvFocusMarqueeModelTest' --tests '*TvSkylineSectionFeedSourceTest' :androidTvApp:compileDebugKotlinAndroid`

Expected: tests and compilation pass.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvSkylineSectionFeed.kt androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvFocusMarqueeModel.kt androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/components/TvFocusMarqueeModelTest.kt androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/components/TvSkylineSectionFeedSourceTest.kt
git commit -m "fix(tv): debounce marquee focus previews"
```

### Task 3: Scrubber labels follow preview position

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerScrubber.kt`
- Create: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerScrubberTest.kt`

**Interfaces:**
- Produces: `internal fun playerScrubberLabelPosition(positionSec: Double, scrubPreviewSec: Double?, isScrubbing: Boolean): Double`.

- [ ] **Step 1: Add failing source-selection tests**

```kotlin
package org.prairieserver.prairie.tv.ui.screens.player

import kotlin.test.Test
import kotlin.test.assertEquals

class TvPlayerScrubberTest {
    @Test fun previewDrivesLabelsWhileScrubbing() {
        assertEquals(42.0, playerScrubberLabelPosition(10.0, 42.0, true))
    }

    @Test fun playbackPositionDrivesLabelsOtherwise() {
        assertEquals(10.0, playerScrubberLabelPosition(10.0, 42.0, false))
        assertEquals(10.0, playerScrubberLabelPosition(10.0, null, true))
    }
}
```

- [ ] **Step 2: Run test and verify RED**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvPlayerScrubberTest'`

Expected: missing helper compilation failure.

- [ ] **Step 3: Implement label position selection**

```kotlin
internal fun playerScrubberLabelPosition(
    positionSec: Double,
    scrubPreviewSec: Double?,
    isScrubbing: Boolean,
): Double = if (isScrubbing) scrubPreviewSec ?: positionSec else positionSec
```

Use the result for elapsed and remaining labels while leaving progress drawing and seek dispatch unchanged.

- [ ] **Step 4: Run test and compile**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvPlayerScrubberTest' :androidTvApp:compileDebugKotlinAndroid`

Expected: tests and compilation pass.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerScrubber.kt androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerScrubberTest.kt
git commit -m "fix(tv): show scrub preview time labels"
```

### Task 4: Persist next-up selector choices

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/detail/TvItemDetailViewModel.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/detail/TvItemDetailViewModelTest.kt`

**Interfaces:**
- `onNextUpVersionSelected`, `onNextUpAudioTrackSelected`, and `onNextUpSubtitleTrackSelected` update UI state and call the same selection persistence abstraction as ordinary selections using the next-up content/file identity.
- Combined subtitle indexes are converted through `combinedSubtitleSelectionIndexes` before fingerprinting.

- [ ] **Step 1: Add failing persistence tests**

Add three tests using the existing fake preference/session repositories:

```kotlin
@Test fun nextUpVersionSelectionPersistsForEpisode() = runTest {
    viewModel.onNextUpVersionSelected("file-b")
    assertEquals("file-b", persistedSelection.lastFileId)
}

@Test fun nextUpAudioSelectionPersistsForEpisodeFile() = runTest {
    viewModel.onNextUpAudioTrackSelected(2)
    assertEquals(2, persistedSelection.audioTrackIndex)
}

@Test fun nextUpCombinedSubtitleSelectionPersistsCatalogFingerprint() = runTest {
    viewModel.onNextUpSubtitleTrackSelected(combinedIndexForCatalogTrack)
    assertEquals(expectedSubtitleFingerprint, persistedSelection.subtitleFingerprint)
}
```

- [ ] **Step 2: Run tests and verify RED**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvItemDetailViewModelTest*nextUp*Persists*'`

Expected: persistence assertions fail because next-up handlers currently update only in-memory UI state.

- [ ] **Step 3: Reuse the ordinary persistence path**

Extract one private persistence function parameterized by content ID, selected file ID, audio combined/index value, subtitle combined index, and the relevant playback detail. Invoke it from both ordinary and next-up handlers; do not duplicate fingerprint construction.

- [ ] **Step 4: Run detail-view-model tests**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvItemDetailViewModelTest'`

Expected: all detail selection and persistence tests pass.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/detail/TvItemDetailViewModel.kt androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/detail/TvItemDetailViewModelTest.kt
git commit -m "fix(tv): persist next-up track selections"
```

### Task 5: PiP reconnect visibility

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerScreen.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerControlsUsabilityTest.kt`

**Interfaces:**
- Produces: `internal fun shouldShowReconnectSpinner(isReconnecting: Boolean, showNextUp: Boolean, isInPip: Boolean): Boolean`.

- [ ] **Step 1: Add failing visibility tests**

```kotlin
@Test fun reconnectSpinnerIsHiddenInPip() {
    assertFalse(shouldShowReconnectSpinner(true, false, true))
    assertFalse(shouldShowReconnectSpinner(true, true, false))
    assertTrue(shouldShowReconnectSpinner(true, false, false))
}
```

- [ ] **Step 2: Run test and verify RED**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvPlayerControlsUsabilityTest.reconnectSpinnerIsHiddenInPip'`

Expected: missing helper compilation failure.

- [ ] **Step 3: Implement PiP gate**

```kotlin
internal fun shouldShowReconnectSpinner(
    isReconnecting: Boolean,
    showNextUp: Boolean,
    isInPip: Boolean,
): Boolean = isReconnecting && !showNextUp && !isInPip
```

Use the existing PiP state supplied to the player screen; do not derive it from window dimensions.

- [ ] **Step 4: Run focused tests and compile**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvPlayerControlsUsabilityTest' :androidTvApp:compileDebugKotlinAndroid`

Expected: tests and compilation pass.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerScreen.kt androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerControlsUsabilityTest.kt
git commit -m "fix(tv): hide reconnect spinner in pip"
```

### Task 6: Playback-state verification

- [ ] Run `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvFocusMarqueeModelTest' --tests '*TvSkylineSectionFeedSourceTest' --tests '*TvPlayerScrubberTest' --tests '*TvItemDetailViewModelTest' --tests '*TvPlayerControlsUsabilityTest'`.
- [ ] Run `./gradlew :androidTvApp:assembleDebug`.
- [ ] Run `git diff --check`.
- [ ] Confirm stale enrichment cannot replace active content and next-up persistence retains combined-index conversion.
