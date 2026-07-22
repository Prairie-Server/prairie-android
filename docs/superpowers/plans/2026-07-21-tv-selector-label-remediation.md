# TV Selector and Label Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every playback selector concise, distinguishable, correctly ordered, and state-consistent with tvOS.

**Architecture:** Shared pure formatters own audio labels, compact selector values, badges, and semantic subtitle order. Selector composables consume precomputed option models containing one authoritative selected flag and expose static pills for single-choice states.

**Tech Stack:** Kotlin 2.1, Jetpack Compose for TV, kotlin.test/JUnit.

## Global Constraints

- Combined subtitle indexes remain the public selector contract.
- Meaningful titles such as Commentary and Descriptive Audio must survive structured metadata formatting.
- Exactly one audio option may expose selected state.
- Long localized text uses ellipsis as a backstop, not clipping or unconstrained width.
- Every production behavior change starts with a failing test.

---

### Task 1: Preserve meaningful audio qualifiers

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/player/TvSubtitleChoiceLabels.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/player/TvSubtitleChoiceLabelsTest.kt`

**Interfaces:**
- Produces: `internal fun meaningfulAudioTrackTitle(displayLabel: String, language: String?, codec: String?, layout: String?): String?`.
- `audioChoiceLabel` appends the result in parentheses after language, codec, and layout.

- [ ] **Step 1: Add failing qualifier tests**

```kotlin
@Test fun meaningfulAudioTitleDistinguishesStructuredTracks() {
    assertEquals(
        "English AC3 5.1 (Commentary)",
        audioChoiceLabel(audio(language = "en", codecOrMime = "audio/ac3", channels = 6, label = "Commentary"), 0),
    )
    assertEquals(
        "English AC3 5.1 (Descriptive Audio)",
        audioChoiceLabel(audio(language = "en", codecOrMime = "audio/ac3", channels = 6, label = "Descriptive Audio"), 1),
    )
}

@Test fun redundantAudioTitleIsNotRepeated() {
    assertEquals(
        "English AC3 5.1",
        audioChoiceLabel(audio(language = "en", codecOrMime = "audio/ac3", channels = 6, label = "English AC3 5.1"), 0),
    )
}
```

- [ ] **Step 2: Run test and verify RED**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvAudioChoiceLabelsTest*AudioTitle*'`

Expected: meaningful-title assertions fail because `displayLabel` is discarded when metadata exists.

- [ ] **Step 3: Implement qualifier filtering**

Normalize title/base tokens case-insensitively, reject empty/server-identity/filename labels, and retain a title when it contains information not already present in language, codec, or layout. Build `baseParts + qualifier?.let { "($it)" }`.

- [ ] **Step 4: Run label tests**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvAudioChoiceLabelsTest'`

Expected: all audio formatter tests pass.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/player/TvSubtitleChoiceLabels.kt androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/player/TvSubtitleChoiceLabelsTest.kt
git commit -m "fix(tv): preserve audio track qualifiers"
```

### Task 2: Enforce exactly-one audio selection

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvPlaybackSelectorRow.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvPlaybackFormattingTest.kt`

**Interfaces:**
- Produces: `internal fun isAudioSelectorOptionSelected(optionIndex: Int?, selectedAudioTrackIndex: Int?): Boolean` where `null` option means Auto.

- [ ] **Step 1: Add failing state tests**

```kotlin
@Test fun autoModeChecksOnlyAutoRow() {
    assertTrue(isAudioSelectorOptionSelected(null, null))
    assertFalse(isAudioSelectorOptionSelected(1, null))
}

@Test fun explicitModeChecksOnlyPhysicalTrack() {
    assertFalse(isAudioSelectorOptionSelected(null, 1))
    assertTrue(isAudioSelectorOptionSelected(1, 1))
    assertFalse(isAudioSelectorOptionSelected(0, 1))
}
```

- [ ] **Step 2: Run test and verify RED**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvPlaybackFormattingTest*ModeChecksOnly*'`

Expected: missing helper compilation failure.

- [ ] **Step 3: Implement authoritative state**

```kotlin
internal fun isAudioSelectorOptionSelected(
    optionIndex: Int?,
    selectedAudioTrackIndex: Int?,
): Boolean = optionIndex == selectedAudioTrackIndex
```

Build Auto with `optionIndex = null`; do not mark the resolved runtime track when selection mode is Auto.

- [ ] **Step 4: Run selector tests and compile**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvPlaybackFormattingTest' :androidTvApp:compileDebugKotlinAndroid`

Expected: tests and compilation pass.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvPlaybackSelectorRow.kt androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvPlaybackFormattingTest.kt
git commit -m "fix(tv): show one audio selection checkmark"
```

### Task 3: Correct marquee quality badges

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/components/TvFocusMarqueeModel.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/components/TvFocusMarqueeModelTest.kt`

**Interfaces:**
- Expose `internal fun qualityBadges(summary: OverlaySummary?): List<String>` for direct testing.

- [ ] **Step 1: Add failing badge tests**

Create representative `OverlaySummary` fixtures and assert:

```kotlin
assertEquals(listOf("4K", "DOLBY VISION", "ATMOS"), qualityBadges(dolbyVisionAtmosSummary))
assertEquals(listOf("HDR10", "DTS-HD"), qualityBadges(hdr10DtsHdSummary))
```

- [ ] **Step 2: Run test and verify RED**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvFocusMarqueeModelTest*qualityBadges*'`

Expected: Dolby Vision and Atmos expectations fail against generic HDR/AUDIO output.

- [ ] **Step 3: Implement explicit classification**

Prefer Dolby Vision over generic HDR, keep HDR10 distinct when available, emit ATMOS for JOC/Atmos audio, and retain known audio codec identity. Deduplicate while preserving resolution, video dynamic range, then audio order.

- [ ] **Step 4: Run marquee tests**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvFocusMarqueeModelTest'`

Expected: badge and state tests pass.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/components/TvFocusMarqueeModel.kt androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/components/TvFocusMarqueeModelTest.kt
git commit -m "fix(tv): preserve dolby marquee badges"
```

### Task 4: Apply semantic subtitle ordering

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvPlaybackFormatting.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvPlaybackSelectorRow.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvPlaybackFormattingTest.kt`

**Interfaces:**
- Produces: `internal fun orderedSubtitleSelectionIndexes(subtitles, externalSubtitles, preferredLanguage): List<Int>` returning combined-space indexes.

- [ ] **Step 1: Add failing ordering test**

Build mixed English, Japanese, forced, SDH, external, and Off/Auto choices. Assert the live tvOS precedence exactly: preferred language group, remaining language groups by display name, format rank, full/forced/SDH variant rank, default flag, then original order. Returned values must still identify original combined indexes.

- [ ] **Step 2: Run test and verify RED**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvPlaybackFormattingTest*ordersSubtitles*'`

Expected: missing helper or raw-catalog-order assertion failure.

- [ ] **Step 3: Implement stable semantic sort**

Sort option records by preferred-language bucket, localized language label, format rank, forced/SDH rank, default flag, then original catalog order. External/embedded provenance does not participate in tvOS display order; it only determines the stable combined selection index that travels with each row. Render sorted records without renumbering those selection indexes.

- [ ] **Step 4: Run formatting tests and compile**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvPlaybackFormattingTest' :androidTvApp:compileDebugKotlinAndroid`

Expected: ordering, fingerprint, and selector tests pass.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvPlaybackFormatting.kt androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvPlaybackSelectorRow.kt androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvPlaybackFormattingTest.kt
git commit -m "fix(tv): order subtitle selector choices"
```

### Task 5: Align compact copy and single-option behavior

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvPlaybackFormatting.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvPlaybackSelectorRow.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvPlaybackFormattingTest.kt`

**Interfaces:**
- Produces: `internal fun selectorIsInteractive(optionCount: Int): Boolean = optionCount > 1`.
- Auto with no resolved track renders `Auto - None`.
- Compact version values use resolution class and dynamic range, with audio details left to the audio selector.

- [ ] **Step 1: Add failing copy and interaction tests**

```kotlin
@Test fun singleChoiceSelectorIsStatic() {
    assertFalse(selectorIsInteractive(0))
    assertFalse(selectorIsInteractive(1))
    assertTrue(selectorIsInteractive(2))
}

@Test fun automaticNoTrackCopyMatchesTvOs() {
    assertEquals("Auto - None", automaticTrackLabel(null))
}
```

Add compact-format expectations for 2160p Dolby Vision and 1080p SDR versions.

- [ ] **Step 2: Run tests and verify RED**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvPlaybackFormattingTest*singleChoice*' --tests '*TvPlaybackFormattingTest*automaticNoTrack*'`

Expected: missing helper and old `Auto: Off` copy failures.

- [ ] **Step 3: Implement static and compact selectors**

```kotlin
internal fun selectorIsInteractive(optionCount: Int): Boolean = optionCount > 1
internal fun automaticTrackLabel(resolvedLabel: String?): String =
    resolvedLabel?.let { "Auto - $it" } ?: "Auto - None"
```

Static pills remain focusable only if required by surrounding focus geometry and must not open a menu. Interactive pills use concise values and expose the full value through semantics where useful.

- [ ] **Step 4: Run formatting tests and compile**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvPlaybackFormattingTest' :androidTvApp:compileDebugKotlinAndroid`

Expected: tests and compilation pass.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvPlaybackFormatting.kt androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvPlaybackSelectorRow.kt androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvPlaybackFormattingTest.kt
git commit -m "fix(tv): align selector copy and interaction"
```

### Task 6: Constrain selector rows and menus

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvPlaybackSelectorRow.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/components/TvAnchoredSelectorMenu.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/player/TvPlayerControlsUsabilityTest.kt`

**Interfaces:**
- Trigger text and option text use `maxLines = 1` and `TextOverflow.Ellipsis`.
- Selector row allocates bounded widths or weights so its total width cannot exceed its parent.

- [ ] **Step 1: Add failing source-contract assertions**

Assert both files contain `TextOverflow.Ellipsis`, option rows contain `maxLines = 1`, and selector triggers use a bounded `weight`, `widthIn`, or parent-aware arrangement.

- [ ] **Step 2: Run source tests and verify RED**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvPlayerControlsUsabilityTest'`

Expected: assertions fail for unconstrained selector/menu text.

- [ ] **Step 3: Add width and ellipsis constraints**

Give trigger text `Modifier.weight(1f, fill = false)` inside a bounded pill and set option text to `maxLines = 1`, `overflow = TextOverflow.Ellipsis`. Keep check icons fixed-size and outside the text weight.

- [ ] **Step 4: Run source tests and compile**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvPlayerControlsUsabilityTest' :androidTvApp:compileDebugKotlinAndroid`

Expected: tests and compilation pass.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvPlaybackSelectorRow.kt androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/components/TvAnchoredSelectorMenu.kt androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/player/TvPlayerControlsUsabilityTest.kt
git commit -m "fix(tv): constrain playback selector text"
```

### Task 7: Selector-stage verification

- [ ] Run `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvSubtitleChoiceLabelsTest' --tests '*TvPlaybackFormattingTest' --tests '*TvFocusMarqueeModelTest' --tests '*TvPlayerControlsUsabilityTest'`.
- [ ] Run `./gradlew :androidTvApp:assembleDebug`.
- [ ] Run `git diff --check`.
- [ ] Confirm two otherwise-identical commentary/normal tracks render distinct labels and Auto mode shows one selected row.
