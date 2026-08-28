# Fire TV Playback Selection UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix Android TV detail-selector contrast, preserve semantic source/subtitle intent across episode transitions, and keep long in-player subtitle pickers scrolled to D-pad focus.

**Architecture:** Keep the existing detail popup, eager HUD focus graph, playback coordinator, and per-item durable preferences. Add pure visual-state and episode-handoff policies, pass the handoff through the existing TV route/start request, resolve it only after the target episode's catalog detail is known, and apply it without transferring raw file IDs or track indexes.

**Tech Stack:** Kotlin 2.1, Kotlin serialization, Jetpack Compose/Compose for TV, Navigation Compose, Media3, Kotlin test/JUnit, Gradle, repository supply-chain scripts.

## Global Constraints

- Android TV behavior only; phone production behavior remains unchanged.
- No server, API, schema, database, proxy, or production-configuration changes.
- Keep durable track selections scoped to `(server, profile, contentId, fileId)`.
- Never transfer a raw file ID or subtitle index between episodes.
- Explicit subtitle Off remains Off; Auto remains Auto; a missing explicit match falls back to profile Auto.
- Source matching uses resolution first, then codec, Dolby Vision/HDR, and container as deterministic tie-breakers.
- Do not add cross-episode audio continuity.
- Keep Watch Together auto-advance suppression and playback shutdown ordering unchanged.
- Keep the eager HUD option `Column`; do not restore the removed lazy focus graph.
- Do not install on a physical Fire TV, Shield, phone, or other device without a new explicit request.

---

## File Map

- `androidTvApp/.../ui/components/TvSelectorRowVisualState.kt`: pure focused/selected/disabled color policy for anchored selector rows.
- `androidTvApp/.../ui/components/TvAnchoredSelectorMenu.kt`: renders the existing anchored popup with explicit TV focus state.
- `androidTvApp/.../ui/screens/player/TvPlayerHud.kt`: explicitly brings a focused HUD picker row into the clipped viewport.
- `android-shared/.../player/video/EpisodeSelectionHandoff.kt`: serializable semantic source/subtitle intent plus pure capture/resolve policy.
- `android-shared/.../player/video/VideoPlaybackStartRequest.kt`: optional episode handoff on the existing coordinator request.
- `android-shared/.../player/video/VideoPlaybackStartResult.kt`: reports the target decision back to the TV view model.
- `androidTvApp/.../ui/navigation/TvRoute.kt`: carries one URL-encoded handoff payload through player replacement.
- `androidTvApp/.../ui/navigation/TvAppNavigation.kt`: decodes the payload and passes it into the next TV player.
- `androidTvApp/.../ui/screens/player/TvVideoPlaybackStarter.kt`: resolves source and subtitle against the target episode before session start.
- `androidTvApp/.../ui/screens/player/TvPlayerViewModel.kt`: captures outgoing intent and prevents a stale target override.
- `androidTvApp/.../ui/screens/player/TvPlayerScreen.kt`: threads the handoff through launch arguments and next navigation.
- `androidTvApp/.../ui/screens/detail/TvItemDetailViewModel.kt`: resolves old next-up selection after the new watch detail loads.

---

### Task 1: Define and test a semantic episode-selection handoff

**Files:**
- Create: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/player/video/EpisodeSelectionHandoff.kt`
- Create test: `android-shared/src/androidUnitTest/kotlin/org/prairieserver/prairie/common/player/video/EpisodeSelectionHandoffTest.kt`

**Interfaces:**
- Produces: a serializable `EpisodeSelectionHandoff` containing semantic source and subtitle intent only.
- Consumes: `FileVersion`, `PlayerSubtitleInfo`, and the existing normalized codec/language metadata.
- Security boundary: payloads contain no server URL, token, file ID, download ID, track ID, or track index.

- [ ] **Step 1: Write failing source-resolution tests**

Cover these cases in `EpisodeSelectionHandoffTest`:

```kotlin
@Test fun sourceUsesResolutionBeforeCodecAndContainer() { /* 2160p remains 2160p */ }
@Test fun sourceUsesCodecDynamicRangeAndContainerAsTieBreakers() { /* exact semantic candidate wins */ }
@Test fun ambiguousBestSourceFallsBackToAutomaticSelection() { /* tied best candidates return null */ }
@Test fun unavailableResolutionFallsBackToAutomaticSelection() { /* no forced upscale/downgrade */ }
@Test fun sourceIntentNeverSerializesTheOriginalFileId() { /* encoded payload omits raw IDs */ }
```

Use actual `FileVersion` fixtures with different IDs so the test proves resolution returns a target ID selected from the target list rather than the source episode's ID.

- [ ] **Step 2: Write failing subtitle-resolution tests**

```kotlin
@Test fun explicitSubtitleMatchesSemanticsAtADifferentTargetIndex() { /* language/accessibility/source/codec */ }
@Test fun explicitOffRemainsOff() { /* result is -1 and intentSpecified=true */ }
@Test fun automaticSubtitleRemainsUnspecified() { /* null and intentSpecified=false */ }
@Test fun unavailableExplicitSubtitleUsesProfileAutoWithoutTargetDurableRestore() {
    /* null and intentSpecified=true */
}
@Test fun malformedPayloadDecodesToNull() { /* navigation cannot crash */ }
```

The explicit-missing case is load-bearing: `subtitleTrackIndex = null` selects profile Auto, while `intentSpecified = true` prevents the target episode's durable per-file subtitle from overriding that fallback.

- [ ] **Step 3: Run the focused tests and verify RED**

```bash
./gradlew :android-shared:testDebugUnitTest \
  --tests '*EpisodeSelectionHandoffTest' --no-daemon
```

Expected: compilation fails because the handoff contract and policy do not exist.

- [ ] **Step 4: Implement the minimal serializable contract**

```kotlin
@Serializable
data class EpisodeSelectionHandoff(
    val source: EpisodeSourceIntent? = null,
    val subtitle: EpisodeSubtitleIntent = EpisodeSubtitleIntent.auto(),
)

@Serializable
data class EpisodeSourceIntent(
    val resolution: String,
    val videoCodec: String? = null,
    val dynamicRange: EpisodeDynamicRange? = null,
    val container: String? = null,
)

@Serializable enum class EpisodeDynamicRange { SDR, HDR, DOLBY_VISION }
@Serializable enum class EpisodeSubtitleMode { AUTO, OFF, TRACK }

@Serializable
data class EpisodeSubtitleIntent(
    val mode: EpisodeSubtitleMode,
    val language: String? = null,
    val codecFamily: String? = null,
    val forced: Boolean? = null,
    val hearingImpaired: Boolean? = null,
    val external: Boolean? = null,
) {
    companion object {
        fun auto() = EpisodeSubtitleIntent(EpisodeSubtitleMode.AUTO)
        fun off() = EpisodeSubtitleIntent(EpisodeSubtitleMode.OFF)
    }
}

data class ResolvedEpisodeSubtitle(val trackIndex: Int?, val intentSpecified: Boolean)
data class ResolvedEpisodeSelection(
    val fileId: Int?,
    val subtitleTrackIndex: Int?,
    val subtitleIntentSpecified: Boolean,
)
```

Add pure capture/resolve helpers and JSON encode/decode helpers. Source resolution must require an exact normalized resolution, then score codec, Dolby Vision/HDR, and container. Return a target file ID only when the highest-scoring candidate is unique. Subtitle resolution must compare normalized language, codec family, forced, hearing-impaired, and embedded/external semantics. Do not reuse `TrackSelectionFingerprint`: its index is intentionally file-scoped.

- [ ] **Step 5: Verify the contract and serialization boundary**

```bash
./gradlew :android-shared:testDebugUnitTest \
  --tests '*EpisodeSelectionHandoffTest' --no-daemon
rg -n 'fileId|downloadId|trackId|trackIndex|accessToken|serverUrl' \
  android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/player/video/EpisodeSelectionHandoff.kt
```

Expected: tests pass; restricted names appear only in resolved target result types where needed, never in serialized intent fields.

- [ ] **Step 6: Commit Task 1**

```bash
git add android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/player/video/EpisodeSelectionHandoff.kt \
  android-shared/src/androidUnitTest/kotlin/org/prairieserver/prairie/common/player/video/EpisodeSelectionHandoffTest.kt
git commit -m "feat(tv): define semantic episode selection handoff"
```

---

### Task 2: Resolve the handoff at the playback-start boundary

**Files:**
- Modify: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/player/video/VideoPlaybackStartRequest.kt`
- Modify: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/player/video/VideoPlaybackStartResult.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvVideoPlaybackStarter.kt`
- Create test: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvEpisodeHandoffPlaybackStartTest.kt`

**Interfaces:**
- `VideoPlaybackStartRequest.episodeSelectionHandoff: EpisodeSelectionHandoff? = null` keeps phone and existing callers source-compatible.
- `VideoPlaybackStartResult.Ready.resolvedEpisodeSelection: ResolvedEpisodeSelection? = null` reports exactly what was chosen after target catalog resolution.

- [ ] **Step 1: Write failing precedence and fallback tests**

Extract a pure `resolveTvPlaybackStartSelection(...)` policy and test:

```kotlin
@Test fun explicitDetailFileIdWinsOverEpisodeHandoff() { /* manual launch remains authoritative */ }
@Test fun episodeHandoffWinsOverTargetLastFileAndQuality() { /* autoplay carries current intent */ }
@Test fun noHandoffPreservesExistingLastFileAndQualitySelection() { /* regression guard */ }
@Test fun subtitleIsResolvedAgainstTheChosenTargetVersion() { /* not another version's indexes */ }
@Test fun missingExplicitSubtitleReturnsSpecifiedProfileAuto() { /* null + true */ }
@Test fun explicitOffIsRetainedClientSide() { /* -1 is not sent to server */ }
```

- [ ] **Step 2: Run the focused test and verify RED**

```bash
./gradlew :androidTvApp:testDebugUnitTest \
  --tests '*TvEpisodeHandoffPlaybackStartTest' --no-daemon
```

Expected: compilation fails because the request/result fields and resolver do not exist.

- [ ] **Step 3: Add the optional request/result fields**

Add the two nullable fields with defaults. Do not change constructor behavior for Android phone, explicit detail launches, retries, Watch Together, or download playback.

- [ ] **Step 4: Resolve only after target watch detail is available**

In `TvVideoPlaybackStarter`, feed target `FileVersion` and subtitle lists into `resolveTvPlaybackStartSelection`. Apply precedence in this exact order:

1. explicit `preferredFileId` from the detail screen;
2. unique semantic source handoff match;
3. existing target `lastFileId` / quality / automatic selection.

Resolve subtitle against the selected target version. Forward only a non-negative target subtitle index to the server start request because the server rejects `-1`; retain Off as `-1` in `resolvedEpisodeSelection` for the client-side Media3 selection. Populate the resolved result on `Ready`.

- [ ] **Step 5: Run focused and neighboring coordinator tests**

```bash
./gradlew :androidTvApp:testDebugUnitTest \
  --tests '*TvEpisodeHandoffPlaybackStartTest' \
  --tests '*TvPlaybackFreshLoadOwnershipTest' \
  :androidTvApp:compileDebugKotlinAndroid --no-daemon
```

Expected: all selected tests pass and TV compilation succeeds.

- [ ] **Step 6: Commit Task 2**

```bash
git add android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/player/video/VideoPlaybackStartRequest.kt \
  android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/player/video/VideoPlaybackStartResult.kt \
  androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvVideoPlaybackStarter.kt \
  androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvEpisodeHandoffPlaybackStartTest.kt
git commit -m "feat(tv): resolve episode selection during playback start"
```

---

### Task 3: Give anchored selector rows an explicit TV focus state

**Files:**
- Create: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvSelectorRowVisualState.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvAnchoredSelectorMenu.kt:152-214`
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/detail/TvPlaybackSelectorRow.kt:80-220`
- Create test: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/components/TvSelectorRowVisualStateTest.kt`

**Interfaces:**
- Produces: `tvSelectorRowVisualState(focused: Boolean, selected: Boolean, enabled: Boolean): TvSelectorRowVisualState`.
- Consumes: existing `FocusedContainer`, `FocusedContent`, `DarkSurfaceElevated`, and `PrairieOnSurface` theme colors.

- [ ] **Step 1: Write failing visual-state tests**

```kotlin
class TvSelectorRowVisualStateTest {
    @Test fun focusedRowsUseInvertedTvContrast() {
        val state = tvSelectorRowVisualState(focused = true, selected = false, enabled = true)
        assertEquals(FocusedContainer, state.container)
        assertEquals(FocusedContent, state.content)
        assertTrue(state.border.alpha > 0f)
    }

    @Test fun selectedIdleRowsRemainDistinctFromIdleRows() {
        val selected = tvSelectorRowVisualState(false, true, true)
        val idle = tvSelectorRowVisualState(false, false, true)
        assertNotEquals(idle.container, selected.container)
        assertNotEquals(idle.border, selected.border)
    }

    @Test fun disabledRowsStayMutedEvenWhenSelected() {
        val state = tvSelectorRowVisualState(false, true, false)
        assertTrue(state.content.alpha < 0.5f)
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

```bash
./gradlew :androidTvApp:testDebugUnitTest \
  --tests '*TvSelectorRowVisualStateTest' --no-daemon
```

Expected: compilation fails because the visual-state types do not exist.

- [ ] **Step 3: Implement the minimal pure policy**

```kotlin
internal data class TvSelectorRowVisualState(
    val container: Color,
    val content: Color,
    val border: Color,
)

internal fun tvSelectorRowVisualState(
    focused: Boolean,
    selected: Boolean,
    enabled: Boolean,
): TvSelectorRowVisualState = when {
    !enabled -> TvSelectorRowVisualState(
        DarkSurfaceElevated,
        PrairieOnSurface.copy(alpha = 0.38f),
        Color.Transparent,
    )
    focused -> TvSelectorRowVisualState(
        FocusedContainer,
        FocusedContent,
        FocusedContent.copy(alpha = 0.22f),
    )
    selected -> TvSelectorRowVisualState(
        PrairieOnSurface.copy(alpha = 0.14f),
        PrairieOnSurface,
        PrairieOnSurface.copy(alpha = 0.28f),
    )
    else -> TvSelectorRowVisualState(DarkSurfaceElevated, PrairieOnSurface, Color.Transparent)
}
```

- [ ] **Step 4: Wire the policy into every anchored menu row**

Add a stable `key: String` to `TvSelectorOption` and populate it from file ID, audio/subtitle stable identity, or edition key at all `TvPlaybackSelectorRow` call sites. For each option, remember a `MutableInteractionSource` by key, collect focus, pass that interaction source to `DropdownMenuItem`, and apply the resolved background, border, text, and icon colors. Keep anchoring, semantics, enablement, callbacks, and trigger focus restoration unchanged.

```kotlin
val interactionSource = remember(option.key) { MutableInteractionSource() }
val focused by interactionSource.collectIsFocusedAsState()
val visual = tvSelectorRowVisualState(focused, option.selected, option.enabled)

DropdownMenuItem(
    interactionSource = interactionSource,
    modifier = Modifier
        .padding(horizontal = 6.dp, vertical = 2.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(visual.container)
        .border(1.dp, visual.border, RoundedCornerShape(8.dp))
        .semantics { selected = option.selected },
    colors = MenuDefaults.itemColors(
        textColor = visual.content,
        leadingIconColor = visual.content,
        disabledTextColor = visual.content,
        disabledLeadingIconColor = visual.content,
    ),
    // retain the existing text, leading icon, enabled value, and onClick body
)
```

- [ ] **Step 5: Run focused tests and compile**

```bash
./gradlew :androidTvApp:testDebugUnitTest \
  --tests '*TvSelectorRowVisualStateTest' \
  --tests '*TvPlaybackFormattingTest' \
  :androidTvApp:compileDebugKotlinAndroid --no-daemon
```

Expected: selected tests pass and Android TV Kotlin compilation succeeds.

- [ ] **Step 6: Commit Task 3**

```bash
git add androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components \
  androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/detail/TvPlaybackSelectorRow.kt \
  androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/components/TvSelectorRowVisualStateTest.kt
git commit -m "fix(tv): make detail selector focus legible"
```

---

### Task 4: Keep long HUD picker lists aligned with D-pad focus

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerHud.kt:2060-2190`
- Create test: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvHudPickerFocusWiringSourceTest.kt`

**Interfaces:**
- Produces: every `HudPickerOptionRow` owns one `BringIntoViewRequester` and relocates only when focus enters.
- Preserves: eager `Column.verticalScroll`, modal focus trap, stable option keys, and Select-to-commit behavior.

- [ ] **Step 1: Write the failing wiring regression**

```kotlin
class TvHudPickerFocusWiringSourceTest {
    private val source = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerHud.kt",
    ).readText()

    @Test fun focusedPickerRowsAreExplicitlyBroughtIntoView() {
        assertContains(source, "remember { BringIntoViewRequester() }")
        assertContains(source, ".bringIntoViewRequester(bringIntoViewRequester)")
        assertContains(source, "bringIntoViewRequester.bringIntoView()")
    }

    @Test fun pickerKeepsTheEagerFocusGraph() {
        val picker = source.substringAfter("internal fun HudPickerDialog")
            .substringBefore("private fun formatTime")
        assertContains(picker, ".verticalScroll(rememberScrollState())")
        assertFalse(picker.contains("LazyColumn"))
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

```bash
./gradlew :androidTvApp:testDebugUnitTest \
  --tests '*TvHudPickerFocusWiringSourceTest' --no-daemon
```

Expected: bring-into-view assertions fail against the implicit-scroll implementation.

- [ ] **Step 3: Add focused-row relocation**

```kotlin
val bringIntoViewRequester = remember { BringIntoViewRequester() }
val scope = rememberCoroutineScope()

Modifier
    .bringIntoViewRequester(bringIntoViewRequester)
    .onFocusChanged { state ->
        if (state.isFocused) {
            onFocused()
            scope.launch { bringIntoViewRequester.bringIntoView() }
        }
    }
```

Remove the old one-line `onFocusChanged` so `onFocused()` fires exactly once per focus entry.

- [ ] **Step 4: Run the regression and neighboring HUD tests**

```bash
./gradlew :androidTvApp:testDebugUnitTest \
  --tests '*TvHudPickerFocusWiringSourceTest' \
  --tests '*TvPlayerHudTabsTest' \
  --tests '*TvSubtitleHudStateTest' \
  :androidTvApp:compileDebugKotlinAndroid --no-daemon
```

Expected: all tests and compilation pass.

- [ ] **Step 5: Commit Task 4**

```bash
git add androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerHud.kt \
  androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvHudPickerFocusWiringSourceTest.kt
git commit -m "fix(tv): scroll HUD pickers with focus"
```

---

### Task 5: Carry the semantic handoff through next-episode navigation

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/navigation/TvRoute.kt:84-145`
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/navigation/TvAppNavigation.kt:780-835`
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerScreen.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerViewModel.kt:412-433,1431-1515,2879-2910`
- Modify test: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/navigation/TvPlayerRouteTest.kt`
- Create test: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayNextSelectionHandoffTest.kt`

**Interfaces:**
- `TvRoute.Player` adds one optional URL-encoded `episodeSelectionHandoff` query value.
- `TvPlayerLaunchArgs` and `PlayNextRequest` add `episodeSelectionHandoff: EpisodeSelectionHandoff?`.
- `onPlayNext` passes one semantic handoff object instead of using `preferredQuality` as cross-episode authority.

- [ ] **Step 1: Extend route tests and verify RED**

Test payload round-trip, absent-payload compatibility, malformed-payload fallback, and query-value encoding:

```kotlin
@Test fun playerRouteRoundTripsEpisodeSelectionHandoff() { /* semantic payload survives replacement */ }
@Test fun playerRouteWithoutHandoffKeepsExistingDefaults() { /* existing deep links work */ }
@Test fun malformedEpisodeHandoffIsIgnored() { /* player still opens */ }
```

```bash
./gradlew :androidTvApp:testDebugUnitTest \
  --tests '*TvPlayerRouteTest' --no-daemon
```

Expected: new route assertions fail.

- [ ] **Step 2: Add the optional route and launch fields**

Serialize with `encodeEpisodeSelectionHandoff`, route-encode the result once, declare a nullable string navigation argument, route-decode it once, and parse with `decodeEpisodeSelectionHandoff`. A malformed value becomes null rather than aborting navigation. Thread it through `TvPlayerScreen`, `TvPlayerLaunchArgs`, and `VideoPlaybackStartRequest`.

- [ ] **Step 3: Write failing outgoing-handoff tests**

Use production-shaped `FileVersion`, downloaded playback identity, and `PlayerSubtitleInfo` fixtures:

```kotlin
@Test fun nextEpisodeCapturesCurrentSourceAndCommittedSubtitleSemantics() { /* no IDs */ }
@Test fun nextEpisodeCarriesExplicitOff() { /* OFF survives */ }
@Test fun nextEpisodeCarriesAutoWhenNoExplicitSubtitleWasCommitted() { /* AUTO */ }
@Test fun downloadedPlaybackDropsDownloadIdentityButKeepsMediaSemantics() { /* safe handoff */ }
@Test fun watchTogetherStillSuppressesSoloAutoAdvance() { /* unchanged guard */ }
@Test fun profileOrServerReplacementDoesNotReuseAnOldHandoff() { /* session boundary */ }
```

```bash
./gradlew :androidTvApp:testDebugUnitTest \
  --tests '*TvPlayNextSelectionHandoffTest' --no-daemon
```

Expected: the new continuity tests fail.

- [ ] **Step 4: Capture and emit semantic intent**

At `advanceToNextEpisode`, capture source intent from the active `FileVersion` and subtitle intent from the committed subtitle identity. Strip `fileId`, `downloadId`, `trackId`, server index, and Media3 index. Preserve `autoAdvanceCount`. Keep Watch Together suppression and shutdown order untouched. Navigate with the handoff payload and remove `preferredQuality` as the episode-continuity mechanism; it remains available for ordinary playback-quality semantics.

- [ ] **Step 5: Apply the resolved target decision without stale override**

When `VideoPlaybackStartResult.Ready.resolvedEpisodeSelection` exists:

- set the pending initial subtitle index, including `-1` for Off;
- apply an explicit target match or Off after Media3 tracks appear;
- when `subtitleIntentSpecified` is true, skip the target file's durable `localTrackSelection.subtitleFingerprint` restore;
- for a missing explicit match, leave the index null so profile Auto applies;
- keep audio restore and all no-handoff launch behavior unchanged.

- [ ] **Step 6: Run focused player and route tests**

```bash
./gradlew :androidTvApp:testDebugUnitTest \
  --tests '*TvPlayerRouteTest' \
  --tests '*TvPlayNextSelectionHandoffTest' \
  --tests '*TvTrackSelectionPersistenceTest' \
  --tests '*TvPlaybackFreshLoadOwnershipTest' \
  :androidTvApp:compileDebugKotlinAndroid --no-daemon
```

Expected: all selected tests pass and TV compilation succeeds.

- [ ] **Step 7: Commit Task 5**

```bash
git add androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/navigation \
  androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player \
  androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/navigation/TvPlayerRouteTest.kt \
  androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayNextSelectionHandoffTest.kt
git commit -m "fix(tv): preserve episode source and subtitle intent"
```

---

### Task 6: Preserve the same intent when choosing Next Up on detail pages

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/detail/TvItemDetailViewModel.kt:877-1110`
- Modify test: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/detail/TvTrackSelectionPersistenceTest.kt`
- Create test: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/detail/TvNextUpSelectionHandoffTest.kt`

**Interfaces:**
- Produces: an in-memory pending `EpisodeSelectionHandoff` tied to the expected target content/generation.
- Preserves: per-item Room persistence and audio restore; the carried selection is not persisted until the user explicitly changes it on the target item.

- [ ] **Step 1: Write failing target-refresh tests**

```kotlin
@Test fun changingNextUpResolvesOldSourceAgainstNewEpisodeFiles() { /* semantic source carries */ }
@Test fun changingNextUpResolvesSubtitleAtDifferentCombinedIndex() { /* semantic track carries */ }
@Test fun explicitOffRemainsOffAcrossNextUpRefresh() { /* -1 */ }
@Test fun missingExplicitSubtitleUsesAutoAndDoesNotRestoreTargetDurableSubtitle() { /* null */ }
@Test fun autoAllowsExistingTargetDurableSubtitleRestore() { /* existing behavior */ }
@Test fun staleRefreshCompletionCannotApplyHandoffToAnotherEpisode() { /* generation fence */ }
@Test fun carriedSelectionIsNotPersistedBeforeExplicitUserInput() { /* Room remains item-scoped */ }
@Test fun profileOrServerChangeClearsPendingNextUpHandoff() { /* identity boundary */ }
```

- [ ] **Step 2: Run the focused tests and verify RED**

```bash
./gradlew :androidTvApp:testDebugUnitTest \
  --tests '*TvNextUpSelectionHandoffTest' \
  --tests '*TvTrackSelectionPersistenceTest' --no-daemon
```

Expected: continuity assertions fail against the current reset-to-null behavior.

- [ ] **Step 3: Capture before clearing and resolve after loading**

Before `refreshNextUp` clears `selectedNextUpFileId` and `selectedNextUpSubtitleIndex`, capture semantic intent from the old selected version and subtitle. Store it with the expected target content ID and refresh generation. After the new watch detail loads, resolve it against the new file list and selected target version before merging session/durable state.

Merge rules:

1. carried source/subtitle intent for the new target;
2. existing in-memory target session selection where the handoff is Auto/unspecified;
3. existing per-item durable file/audio/subtitle restore where not suppressed;
4. profile Auto fallback.

Keep durable audio behavior unchanged. If a subtitle handoff was specified, block target durable subtitle restore even when no match exists. Do not save the resolved handoff to Room until an explicit selector callback occurs. Clear pending intent on success, error, content mismatch, or generation mismatch.

- [ ] **Step 4: Run focused detail tests and compile**

```bash
./gradlew :androidTvApp:testDebugUnitTest \
  --tests '*TvNextUpSelectionHandoffTest' \
  --tests '*TvTrackSelectionPersistenceTest' \
  --tests '*TvItemDetailSubtitlePreferenceTest' \
  :androidTvApp:compileDebugKotlinAndroid --no-daemon
```

Expected: all selected tests pass and TV compilation succeeds.

- [ ] **Step 5: Commit Task 6**

```bash
git add androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/detail/TvItemDetailViewModel.kt \
  androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/detail/TvTrackSelectionPersistenceTest.kt \
  androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/detail/TvNextUpSelectionHandoffTest.kt
git commit -m "fix(tv): retain selection across next-up detail refresh"
```

---

### Task 7: Verify the complete Android TV change and publish a draft

**Files:**
- Modify only if evidence changes: tests or production files from Tasks 1-6.
- Review: `docs/superpowers/specs/2026-07-31-fire-tv-playback-selection-ux-design.md`
- Review: `docs/superpowers/plans/2026-08-01-fire-tv-playback-selection-ux.md`

- [ ] **Step 1: Audit scope and forbidden changes**

```bash
git diff --check upstream/main...HEAD
git diff --name-only upstream/main...HEAD
git diff --stat upstream/main...HEAD
git diff upstream/main...HEAD -- androidApp silo-server
```

Expected: no whitespace errors; no phone production, server, API, schema, database, proxy, or production-configuration diff.

- [ ] **Step 2: Run supply-chain policy checks**

```bash
./scripts/test-check-build-supply-chain.sh
./scripts/check-build-supply-chain.sh
```

Expected: both scripts exit zero without changing verification metadata.

- [ ] **Step 3: Run the full shared and TV unit-test gate**

```bash
./gradlew --no-daemon --max-workers=2 \
  :android-shared:testDebugUnitTest \
  :androidTvApp:testDebugUnitTest
```

Expected: both unit-test tasks pass. If a failure appears, use `superpowers:systematic-debugging`; do not widen timeouts or rerun blindly.

- [ ] **Step 4: Build debug and minified release variants**

```bash
./gradlew --no-daemon --max-workers=2 \
  :androidTvApp:assembleDebug \
  :androidTvApp:assembleRelease \
  -PallowDebugReleaseSigning=true
```

Expected: both Android TV assemblies succeed. This gate builds artifacts only; it does not install them.

- [ ] **Step 5: Run emulator-only D-pad smoke if a dedicated TV emulator is already available**

Use only an explicitly identified emulator serial. Do not target a physical Shield, Fire TV, phone, or unknown ADB device. Verify:

1. Version, Audio, Subtitle, and Edition popup focus is clearly visible over bright and dark art.
2. A subtitle list longer than the viewport follows focus to its first and last options and Back restores the trigger.
3. Episode 1 to Episode 2 keeps a semantically matching resolution and subtitle even when IDs/indexes differ.
4. Explicit Off remains Off.
5. A missing explicit subtitle falls back to profile Auto.
6. Manual target selection and ordinary no-handoff launches remain unchanged.

Capture serial-scoped screenshots/logs in a temporary directory outside the repository. If no suitable emulator is available, record that limitation in the PR instead of touching a physical device.

- [ ] **Step 6: Request independent focused review**

Use `superpowers:requesting-code-review` with the spec, plan, and `upstream/main...HEAD` diff. Require the reviewer to check:

- serialized intent contains no raw IDs/indexes or credentials;
- explicit detail selection beats handoff, which beats target durable/automatic source choice;
- explicit missing subtitle cannot resurrect a target durable subtitle;
- Auto still permits existing target behavior;
- durable preference scope and write timing remain unchanged;
- generation fencing prevents a stale next-up refresh;
- Watch Together and playback shutdown sequencing remain unchanged;
- selector focus and HUD scroll fixes retain Back/focus behavior;
- Android phone behavior is unchanged.

Address every substantive finding with a focused regression and rerun the smallest affected gate, then repeat review until approved.

- [ ] **Step 7: Re-run final evidence after review fixes**

```bash
git diff --check upstream/main...HEAD
./scripts/check-build-supply-chain.sh
./gradlew --no-daemon --max-workers=2 \
  :android-shared:testDebugUnitTest \
  :androidTvApp:testDebugUnitTest \
  :androidTvApp:assembleRelease \
  -PallowDebugReleaseSigning=true
git status --short --branch
```

Expected: clean diff checks, green tests/build, and a clean branch.

- [ ] **Step 8: Push and open a draft pull request**

```bash
git push -u origin fix/firetv-playback-selection-ux
gh pr create --draft --base main --head fix/firetv-playback-selection-ux \
  --title "fix(tv): improve Fire TV selection and episode continuity" \
  --body-file /tmp/firetv-playback-selection-ux-pr.md
```

The PR body must list the three original defects, behavior decisions, exact test/build evidence, emulator limitation or evidence, security/privacy boundary, and confirmation that phone/server behavior is unchanged. Do not merge.

---

## Plan Self-Review Checklist

- [x] Every approved behavior in the design spec maps to a production step and a regression test.
- [x] Every named type and file exists now or is explicitly created by an earlier task.
- [x] No placeholder instructions, deferred hardening, arbitrary timeout changes, or raw cross-episode IDs/indexes remain.
- [x] Task ordering is dependency-safe and each task ends with focused verification and a small commit.
- [x] Full verification covers supply-chain policy, shared tests, TV tests, debug/release compilation, review, and emulator-only smoke without physical-device installation.

Self-review found and corrected task-number drift introduced while composing the plan, removed test selectors for classes that do not exist on current `upstream/main`, and added explicit profile/server identity-boundary regressions. No unresolved contradiction or material ambiguity remains.
