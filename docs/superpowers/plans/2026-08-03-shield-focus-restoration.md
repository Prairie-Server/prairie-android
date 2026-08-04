# Shield Focus Restoration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore deterministic D-pad focus on For You, Calendar, and Diagnostics on Shield Pro, and make only For You use the solid top-bar treatment already seen in Watchlist/Favorites.

**Architecture:** Keep the existing Navigation Compose and TV shell structure. Add small pure routing/resolution functions with unit coverage, then wire stable `FocusRequester`s and bounded frame-based handoffs into the three affected screens. For You reuses `TvMediaRow`'s exact-card restore interface; Calendar and Diagnostics make their local focus zones explicit.

**Tech Stack:** Kotlin 2.1, Jetpack Compose for TV, Navigation Compose, coroutines, Kotlin test/JUnit, Gradle, Android Debug Bridge.

## Global Constraints

- Do not promote Watchlist to a top-level tab.
- Do not alter Watchlist or Favorites behavior, navigation, layout, or rendering.
- Do not redesign For You, Calendar, Diagnostics, or the global shell.
- Keep PR #162's event-driven first-row top-anchor correction.
- Use stable IDs before indices when resolving refreshed recommendation content.
- Bound every frame-based focus retry and stop immediately on success or disposal.
- Preserve the existing one-layer-per-press behavior for repeated D-pad events.
- Installation on the Shield and opening the app are separate explicit delivery steps after verification.

---

## File Map

- `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/recommendations/TvRecommendationsFocusBridge.kt`: pure For You focus-target resolution and the existing row-to-card focus bridge.
- `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/recommendations/TvRecommendationsScreen.kt`: save the focused recommendation identity, attach exact return requesters, perform post-return handoff, and report For You's top-bar treatment.
- `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvMainShell.kt`: distinguish For You detail returns from Home, expose the return token/requester, and draw a solid scrim only for the recommendations selection.
- `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/calendar/TvCalendarScreen.kt`: track filter/week-strip zones, route Up deterministically, and acknowledge any successful Calendar control focus.
- `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/settings/diagnostics/TvDiagnosticsSettingsScreen.kt`: model and wire deterministic crash-report focus order and reliable initial focus.
- Existing unit-test files beside each feature validate the pure decisions without introducing UI instrumentation.

---

### Task 1: Resolve For You return targets by stable identity

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/recommendations/TvRecommendationsFocusBridge.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/recommendations/TvRecommendationsFocusBridgeTest.kt`

**Interfaces:**
- Produces: `ForYouFocusTarget`, `ForYouFocusRow`, `ResolvedForYouFocusTarget`, and `resolveForYouReturnTarget(target, rows)`.
- Consumed by: Task 2's recommendation-screen focus restoration.

- [ ] **Step 1: Write failing stable-ID and fallback tests**

Add tests that exercise exact resolution, reorder handling, missing-card fallback, missing-section fallback, and an empty feed:

```kotlin
private val target = ForYouFocusTarget("because-you-watched", "movie-b", 1, 2)

@Test
fun exactReturnTargetUsesStableIdsAfterReorder() {
    val resolved = resolveForYouReturnTarget(
        target,
        listOf(
            ForYouFocusRow("because-you-watched", listOf("movie-c", "movie-b", "movie-a")),
            ForYouFocusRow("trending", listOf("movie-d")),
        ),
    )
    assertEquals(ResolvedForYouFocusTarget(0, 1, true), resolved)
}

@Test
fun missingCardUsesClosestIndexInSameSection() {
    val resolved = resolveForYouReturnTarget(
        target,
        listOf(ForYouFocusRow("because-you-watched", listOf("movie-a", "movie-c"))),
    )
    assertEquals(ResolvedForYouFocusTarget(0, 1, false), resolved)
}

@Test
fun missingSectionUsesClosestRowFirstCard() {
    val resolved = resolveForYouReturnTarget(
        target,
        listOf(
            ForYouFocusRow("row-a", listOf("a")),
            ForYouFocusRow("row-b", listOf("b")),
        ),
    )
    assertEquals(ResolvedForYouFocusTarget(1, 0, false), resolved)
}

@Test
fun emptyFeedHasNoCardReturnTarget() {
    assertEquals(null, resolveForYouReturnTarget(target, emptyList()))
}
```

- [ ] **Step 2: Run the focused test and confirm it fails**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests '*TvRecommendationsFocusBridgeTest'
```

Expected: compilation fails because the new target types and resolver do not exist.

- [ ] **Step 3: Implement the minimal resolver**

Add the following pure models and algorithm:

```kotlin
internal data class ForYouFocusTarget(
    val sectionId: String,
    val contentId: String,
    val rowIndex: Int,
    val cardIndex: Int,
)

internal data class ForYouFocusRow(
    val sectionId: String,
    val contentIds: List<String>,
)

internal data class ResolvedForYouFocusTarget(
    val rowIndex: Int,
    val cardIndex: Int,
    val exact: Boolean,
)

internal fun resolveForYouReturnTarget(
    target: ForYouFocusTarget,
    rows: List<ForYouFocusRow>,
): ResolvedForYouFocusTarget? {
    if (rows.isEmpty()) return null
    val stableRowIndex = rows.indexOfFirst { it.sectionId == target.sectionId }
    if (stableRowIndex >= 0) {
        val cards = rows[stableRowIndex].contentIds
        if (cards.isEmpty()) return null
        val stableCardIndex = cards.indexOf(target.contentId)
        return if (stableCardIndex >= 0) {
            ResolvedForYouFocusTarget(stableRowIndex, stableCardIndex, true)
        } else {
            ResolvedForYouFocusTarget(
                stableRowIndex,
                target.cardIndex.coerceIn(cards.indices),
                false,
            )
        }
    }
    val fallbackRowIndex = target.rowIndex.coerceIn(rows.indices)
    val fallbackCards = rows[fallbackRowIndex].contentIds
    if (fallbackCards.isEmpty()) return null
    return ResolvedForYouFocusTarget(fallbackRowIndex, 0, false)
}
```

- [ ] **Step 4: Run the focused tests**

Run the command from Step 2. Expected: all `TvRecommendationsFocusBridgeTest` tests pass. PR #162's separate `TvRecommendationsTopAnchorTest` remains covered by the full TV test task in Task 2 and Task 5.

- [ ] **Step 5: Commit the resolver**

```bash
git add androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/recommendations/TvRecommendationsFocusBridge.kt androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/recommendations/TvRecommendationsFocusBridgeTest.kt
git commit -m "fix(tv): resolve For You return targets"
```

---

### Task 2: Restore For You to the launch card and solidify only its top bar

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/recommendations/TvRecommendationsScreen.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvMainShell.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/recommendations/TvRecommendationsFocusBridgeTest.kt`

**Interfaces:**
- Consumes: Task 1's `resolveForYouReturnTarget` and existing `requestRecommendationRowFocus`.
- Adds to `TvRecommendationsScreen`: `onRecommendationItemClick: (String) -> Unit`, `detailReturnFocusRequest: Int`, `detailReturnCardFocusRequester: FocusRequester`, and `onSolidTopBarChanged: (Boolean) -> Unit`. The existing `onItemClick` remains the unchanged saved-list callback.
- Produces in the shell: a For You-specific pending flag, requester, and return token; Home's existing path remains unchanged.

- [ ] **Step 1: Add a failing bridge test for the filter fallback**

Extend the focus-bridge tests to make both the no-row contract and a failed card request explicit:

```kotlin
@Test
fun emptyFeedFallsBackToForYouFilter() {
    assertTrue(shouldFallbackForYouReturnToFilter(resolveForYouReturnTarget(target, emptyList())))
    assertFalse(
        shouldFallbackForYouReturnToFilter(
            ResolvedForYouFocusTarget(rowIndex = 0, cardIndex = 0, exact = true),
        ),
    )
}

@Test
fun rejectedCardRequestCanBeRetried() = runTest {
    val handled = requestRecommendationRowFocus(
        requestRowContainer = { true },
        awaitFrame = {},
        requestFirstCard = { false },
    )
    assertFalse(handled)
}
```

- [ ] **Step 2: Run the focused test and confirm it fails**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests '*TvRecommendationsFocusBridgeTest'
```

Expected: compilation fails because `shouldFallbackForYouReturnToFilter` is undefined; after that symbol is introduced, the new card-rejection assertion still fails against the old bridge semantics.

- [ ] **Step 3: Add the minimal fallback predicate**

```kotlin
internal fun shouldFallbackForYouReturnToFilter(
    resolved: ResolvedForYouFocusTarget?,
): Boolean = resolved == null
```

Also make `requestRecommendationRowFocus` return the card request result after the row hop and frame:

```kotlin
if (!requestRowContainer()) return false
awaitFrame()
return requestFirstCard()
```

- [ ] **Step 4: Wire saveable focus identity and exact requesters in For You**

In `TvRecommendationsScreen`, add saveable primitive fields for the last focused section/content IDs and indices. Construct `ForYouFocusTarget` only when both IDs are nonblank, and map `visibleSections` to `ForYouFocusRow` before calling the resolver.

Use one row requester and the shell-provided card requester:

```kotlin
val detailReturnRowFocusRequester = remember { FocusRequester() }
val returnRows = remember(visibleSections) {
    visibleSections.map { section ->
        ForYouFocusRow(section.id, section.items.map { it.contentId })
    }
}
val resolvedReturnTarget = lastFocusedTarget?.let { resolveForYouReturnTarget(it, returnRows) }
```

For each `TvMediaRow`, set:

```kotlin
rowContainerFocusRequester = detailReturnRowFocusRequester
    .takeIf { index == resolvedReturnTarget?.rowIndex },
restoreFocusIndex = resolvedReturnTarget?.cardIndex ?: -1,
restoreFocusRequester = detailReturnCardFocusRequester
    .takeIf { index == resolvedReturnTarget?.rowIndex },
onItemFocusedAtIndex = { item, cardIndex ->
    lastFocusedSectionId = section.id
    lastFocusedContentId = item.contentId
    lastFocusedRowIndex = index
    lastFocusedCardIndex = cardIndex
},
```

Wrap only recommendation-row clicks with `onRecommendationItemClick` so the focus identity is saved before navigation. Continue passing the existing `onItemClick` unchanged to `TvWatchlistInline` and `TvFavoritesInline`; this keeps saved-list detail returns on their current generic path. Do not reset `recommendationsListState` or a row's horizontal state.

- [ ] **Step 5: Add the bounded post-return handoff**

On a nonzero `detailReturnFocusRequest`, resolve the current target. If the row is not in `recommendationsListState.layoutInfo.visibleItemsInfo`, bring only that row into composition. Await frames, request the row container, await one more frame, then request the card through `requestRecommendationRowFocus`. If resolution returns null, request `forYouFocusRequester`. Retry for at most six frames and stop after the first success.

```kotlin
LaunchedEffect(detailReturnFocusRequest, resolvedReturnTarget) {
    if (detailReturnFocusRequest == 0) return@LaunchedEffect
    val target = resolvedReturnTarget
    if (target == null) {
        repeat(6) {
            withFrameNanos { }
            if (forYouFocusRequester.requestFocus()) return@LaunchedEffect
        }
        return@LaunchedEffect
    }
    val rowVisible = recommendationsListState.layoutInfo.visibleItemsInfo
        .any { it.index == target.rowIndex }
    if (!rowVisible) recommendationsListState.scrollToItem(target.rowIndex)
    repeat(6) {
        withFrameNanos { }
        val handled = requestRecommendationRowFocus(
            requestRowContainer = { detailReturnRowFocusRequester.requestFocus() },
            awaitFrame = { withFrameNanos { } },
            requestFirstCard = { detailReturnCardFocusRequester.requestFocus() },
        )
        if (handled) return@LaunchedEffect
    }
}
```

Use `runCatching` around requester calls in production so a disposed node ends the attempt safely rather than crashing.

- [ ] **Step 6: Add the For You-specific shell return path**

In `TvMainShell`, add `restoreForYouContentAfterDetail`, `forYouDetailReturnCardFocusRequester`, and `forYouDetailReturnFocusRequest`. A new recommendation-only click callback sets the pending flags before opening detail. Keep the existing generic `openContentItemDetail` callback for Watchlist/Favorites. On resume, use the For You requester as the content restorer fallback while its flag is set, increment the For You token, then clear the pending flag. Pass both click callbacks plus the token/requester into `TvRecommendationsScreen`.

Keep the fallback priority explicit:

```kotlin
val detailReturnFallback = when {
    restoreHomeContentAfterDetail -> homeDetailReturnCardFocusRequester
    restoreForYouContentAfterDetail -> forYouDetailReturnCardFocusRequester
    else -> FocusRequester.Default
}
```

Do not change `openHomeItemDetail`, Home's request token, or Home's requester attachment.

- [ ] **Step 7: Make only recommendations request a solid top scrim**

Have `TvRecommendationsScreen` report `savedListSelection == null` through `onSolidTopBarChanged`, and reset the signal on disposal. In the shell, use an opaque theme-background scrim only when the current route is For You and that signal is true; otherwise retain the existing gradient.

```kotlin
val useSolidForYouTopBar =
    currentRoute == TvMainRoute.ForYou.route && forYouRequestsSolidTopBar
```

Do not edit `TvWatchlistInline`, `TvFavoritesInline`, or `TvPersonalScreens.kt`.

- [ ] **Step 8: Run focused and full TV tests**

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests '*TvRecommendationsFocusBridgeTest'
./gradlew :androidTvApp:testDebugUnitTest
```

Expected: both commands succeed with no failures.

- [ ] **Step 9: Commit the For You restoration**

```bash
git add androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/recommendations/TvRecommendationsFocusBridge.kt androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/recommendations/TvRecommendationsScreen.kt androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvMainShell.kt androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/recommendations/TvRecommendationsFocusBridgeTest.kt
git commit -m "fix(tv): restore For You focus after detail"
```

---

### Task 3: Route Calendar Up through explicit control zones

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/calendar/TvCalendarScreen.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/calendar/TvCalendarFocusRoutingTest.kt`

**Interfaces:**
- Produces: `CalendarControlFocusZone` and an expanded `calendarUpFallbackAction` that distinguishes filters from the week strip.
- Preserves: the shell's existing `onMoveUpToMenu` and content-up fallback registration protocol.

- [ ] **Step 1: Replace the ambiguous control tests with failing zone tests**

```kotlin
@Test
fun weekStripMovesUpToActiveFilter() {
    assertEquals(
        CalendarUpFallbackAction.FocusFilter,
        calendarUpFallbackAction(null, 0, false, CalendarControlFocusZone.WeekStrip),
    )
}

@Test
fun filterMovesUpToCalendarMenuTab() {
    assertEquals(
        CalendarUpFallbackAction.EnterMenu,
        calendarUpFallbackAction(null, 0, false, CalendarControlFocusZone.Filter),
    )
}

@Test
fun heldUpOnControlsDoesNotSkipALayer() {
    assertEquals(
        CalendarUpFallbackAction.StayInContent,
        calendarUpFallbackAction(
            null,
            0,
            false,
            CalendarControlFocusZone.WeekStrip,
            isRepeat = true,
        ),
    )
}
```

- [ ] **Step 2: Run the Calendar test and confirm it fails**

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests '*TvCalendarFocusRoutingTest'
```

Expected: compilation fails because the zone and `FocusFilter` action do not exist.

- [ ] **Step 3: Implement the pure zone routing**

```kotlin
internal enum class CalendarControlFocusZone { Filter, WeekStrip }

internal enum class CalendarUpFallbackAction {
    EnterMenu,
    FocusFilter,
    ReturnToControls,
    StayInContent,
    MoveWithinContent,
}
```

Extend `calendarUpFallbackAction` with `focusedControlZone: CalendarControlFocusZone?`. Preserve the shelf cases first, return `StayInContent` for repeats, then map `WeekStrip -> FocusFilter`, `Filter -> EnterMenu`, and null to the existing content movement behavior. Clear the control zone when a shelf gains focus so stale control state cannot affect shelf routing.

- [ ] **Step 4: Track and acknowledge Calendar control focus**

Maintain `focusedControlZone` in `CalendarList`. Pass zone-aware callbacks into `FilterSegment`, every `DayCell`, both chevrons, and `TodayButton`. When a control gains focus:

1. Update the zone.
2. Run the existing snap-to-controls callback.
3. If the current `focusRequest` has not been acknowledged, record it and call `onInitialContentFocus()`.

This makes a successful default weekday focus release `calendarFocusHandoffPending` even if the earlier imperative filter request returned false.

- [ ] **Step 5: Wire the active filter requester into the Up fallback**

Pass `filterFocusRequesters[state.filter] ?: filterFocusRequester` into `CalendarList`. Handle the new action with a safe direct request:

```kotlin
CalendarUpFallbackAction.FocusFilter -> {
    runCatching { activeFilterFocusRequester.requestFocus() }.getOrDefault(false)
}
```

Keep `EnterMenu` routed through `onMoveUpToMenu`, and keep the existing shelf-to-selected-day choreography unchanged.

- [ ] **Step 6: Run focused and full TV tests**

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests '*TvCalendarFocusRoutingTest'
./gradlew :androidTvApp:testDebugUnitTest
```

Expected: both commands succeed.

- [ ] **Step 7: Commit Calendar routing**

```bash
git add androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/calendar/TvCalendarScreen.kt androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/calendar/TvCalendarFocusRoutingTest.kt
git commit -m "fix(tv): route Calendar focus back to menu"
```

---

### Task 4: Make Diagnostics crash-report focus deterministic

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/settings/diagnostics/TvDiagnosticsSettingsScreen.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/settings/diagnostics/TvDiagnosticsStateTest.kt`

**Interfaces:**
- Produces: `TvDiagnosticsCrashFocus`, `tvDiagnosticsCrashFocusOrder`, and `nextTvDiagnosticsCrashFocus`.
- Consumes: `DiagnosticsConsentMode` and the existing `TvDiagnosticsAction` modifier hook.

- [ ] **Step 1: Add failing focus-order tests**

```kotlin
@Test
fun selectedConsentIsTheInitialCrashReportFocus() {
    assertEquals(
        TvDiagnosticsCrashFocus.ALWAYS,
        initialTvDiagnosticsCrashFocus(DiagnosticsConsentMode.ALWAYS),
    )
}

@Test
fun downTraversesConsentChoicesThenDebugLogging() {
    assertEquals(
        TvDiagnosticsCrashFocus.DEBUG_LOGGING,
        nextTvDiagnosticsCrashFocus(
            current = TvDiagnosticsCrashFocus.NEVER,
            direction = TvDiagnosticsFocusDirection.Down,
            debugLoggingEnabled = true,
        ),
    )
}

@Test
fun disabledDebugLoggingIsSkipped() {
    assertEquals(
        null,
        nextTvDiagnosticsCrashFocus(
            current = TvDiagnosticsCrashFocus.NEVER,
            direction = TvDiagnosticsFocusDirection.Down,
            debugLoggingEnabled = false,
        ),
    )
}

@Test
fun firstChoiceHoldsAtUpperBoundary() {
    assertEquals(
        TvDiagnosticsCrashFocus.ASK,
        nextTvDiagnosticsCrashFocus(
            current = TvDiagnosticsCrashFocus.ASK,
            direction = TvDiagnosticsFocusDirection.Up,
            debugLoggingEnabled = true,
        ),
    )
}

@Test
fun downFromLastEnabledChoiceFallsThroughToCaptureSection() {
    assertEquals(
        null,
        nextTvDiagnosticsCrashFocus(
            current = TvDiagnosticsCrashFocus.DEBUG_LOGGING,
            direction = TvDiagnosticsFocusDirection.Down,
            debugLoggingEnabled = true,
        ),
    )
}
```

- [ ] **Step 2: Run the Diagnostics test and confirm it fails**

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests '*TvDiagnosticsStateTest'
```

Expected: compilation fails because the crash-focus types and functions do not exist.

- [ ] **Step 3: Implement the pure focus order**

```kotlin
internal enum class TvDiagnosticsCrashFocus { ASK, ALWAYS, NEVER, DEBUG_LOGGING }
internal enum class TvDiagnosticsFocusDirection { Up, Down }

internal fun initialTvDiagnosticsCrashFocus(mode: DiagnosticsConsentMode) = when (mode) {
    DiagnosticsConsentMode.ASK -> TvDiagnosticsCrashFocus.ASK
    DiagnosticsConsentMode.ALWAYS -> TvDiagnosticsCrashFocus.ALWAYS
    DiagnosticsConsentMode.NEVER -> TvDiagnosticsCrashFocus.NEVER
}

internal fun tvDiagnosticsCrashFocusOrder(debugLoggingEnabled: Boolean) = buildList {
    add(TvDiagnosticsCrashFocus.ASK)
    add(TvDiagnosticsCrashFocus.ALWAYS)
    add(TvDiagnosticsCrashFocus.NEVER)
    if (debugLoggingEnabled) add(TvDiagnosticsCrashFocus.DEBUG_LOGGING)
}

internal fun nextTvDiagnosticsCrashFocus(
    current: TvDiagnosticsCrashFocus,
    direction: TvDiagnosticsFocusDirection,
    debugLoggingEnabled: Boolean,
): TvDiagnosticsCrashFocus? {
    val order = tvDiagnosticsCrashFocusOrder(debugLoggingEnabled)
    val index = order.indexOf(current).coerceAtLeast(0)
    return when (direction) {
        TvDiagnosticsFocusDirection.Up -> order[(index - 1).coerceAtLeast(0)]
        TvDiagnosticsFocusDirection.Down -> order.getOrNull(index + 1)
    }
}
```

- [ ] **Step 4: Attach stable requesters and key routing**

Create a stable requester for each `TvDiagnosticsCrashFocus`. Map each consent mode to its focus target. Give each consent action and Debug logging its requester plus an `onPreviewKeyEvent` handler that:

1. Handles only `KeyDown` Up/Down.
2. Calls `nextTvDiagnosticsCrashFocus` with `debugLoggingEnabled = state.consent != DiagnosticsConsentMode.NEVER`.
3. Requests the returned enabled target when nonnull.
4. Consumes the event only when a request was attempted. A null Down result from the last enabled crash-report action returns `false`, allowing normal focus search to enter the Capture section.

Do not alter labels, consent callbacks, enabled state, sizes, or colors.

- [ ] **Step 5: Replace the one-shot initial request with a bounded frame handoff**

Key the effect by the selected consent mode. Await one frame, then request the selected option for at most six frames:

```kotlin
LaunchedEffect(state.consent) {
    val target = initialTvDiagnosticsCrashFocus(state.consent)
    repeat(6) {
        withFrameNanos { }
        val focused = runCatching {
            crashFocusRequesters.getValue(target).requestFocus()
        }.getOrDefault(false)
        if (focused) return@LaunchedEffect
    }
}
```

Remove the old `firstFocus` requester and its unchecked `LaunchedEffect(Unit)`.

- [ ] **Step 6: Run focused and full TV tests**

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests '*TvDiagnosticsStateTest'
./gradlew :androidTvApp:testDebugUnitTest
```

Expected: both commands succeed.

- [ ] **Step 7: Commit Diagnostics routing**

```bash
git add androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/settings/diagnostics/TvDiagnosticsSettingsScreen.kt androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/settings/diagnostics/TvDiagnosticsStateTest.kt
git commit -m "fix(tv): focus Diagnostics crash-report controls"
```

---

### Task 5: Verify the integrated TV build

**Files:**
- Verify only; no production files should change.

**Interfaces:**
- Consumes: all prior tasks.
- Produces: a tested TV debug APK ready for an explicitly requested Shield installation.

- [ ] **Step 1: Confirm Watchlist/Favorites were not edited**

```bash
git diff 8a4bdb9c...HEAD -- androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/personal/TvPersonalScreens.kt
```

Expected: no output.

- [ ] **Step 2: Run whitespace and complete TV unit-test gates**

```bash
git diff --check
./gradlew :androidTvApp:testDebugUnitTest
```

Expected: no whitespace errors and `BUILD SUCCESSFUL`.

- [ ] **Step 3: Assemble the TV debug APK**

```bash
./gradlew :androidTvApp:assembleDebug
```

Expected: `BUILD SUCCESSFUL` and an APK under `androidTvApp/build/outputs/apk/debug/`.

- [ ] **Step 4: Inspect the final branch**

```bash
git status --short --branch
git log --oneline --decorate 8a4bdb9c..HEAD
```

Expected: a clean `fix/shield-focus-restoration` branch containing the design, plan, and four focused implementation commits.

- [ ] **Step 5: Stop before device mutation**

Report the verified APK path and ask for explicit authorization before installing it on `192.168.1.128:5555`. Do not launch Silo after installation unless separately requested.
