# PR #164 Review Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Correct the four verified Android TV focus-state defects blocking PR #164 without broadening its navigation behavior.

**Architecture:** Keep Calendar and Diagnostics corrections inside their existing pure policy helpers. Move Home retry lifetime into a small immutable shell state model, and extend the existing For You return-state helper with an explicit-selection reset so both flows are deterministic and unit-testable without Compose instrumentation.

**Tech Stack:** Kotlin 2.1, Jetpack Compose for TV, Kotlin test/JUnit, Gradle, Java 21

## Global Constraints

- Android TV only; do not change phone behavior.
- Preserve one-layer-per-press behavior for repeated D-pad events.
- Focus requests remain bounded and best-effort.
- Coroutine cancellation is authoritative for composable disposal.
- Explicit top-menu navigation overrides stale detail-return state.
- Do not redesign recommendation identities or perform unrelated focus refactors.
- A physical Shield D-pad smoke test remains the release gate.

---

### Task 1: Restore Calendar held-Up movement below the boundary

**Files:**
- Modify: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/calendar/TvCalendarFocusRoutingTest.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/calendar/TvCalendarScreen.kt:714-734`

**Interfaces:**
- Consumes: `calendarUpFallbackAction(focusedShelfIndex, firstFocusableShelfIndex, isReturningToControls, focusedControlZone, isRepeat)`
- Produces: the existing `CalendarUpFallbackAction.MoveWithinContent` result for repeated Up below the first focusable shelf

- [x] **Step 1: Add the failing regression test**

Add this test to `TvCalendarFocusRoutingTest`:

```kotlin
@Test
fun heldUpBelowFirstShelfContinuesContentMovement() {
    assertEquals(
        CalendarUpFallbackAction.MoveWithinContent,
        calendarUpFallbackAction(
            focusedShelfIndex = 4,
            firstFocusableShelfIndex = 2,
            isReturningToControls = false,
            focusedControlZone = null,
            isRepeat = true,
        ),
    )
}
```

- [x] **Step 2: Run the focused test and verify RED**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests '*TvCalendarFocusRoutingTest'
```

Expected: `heldUpBelowFirstShelfContinuesContentMovement` fails because the current unconditional `isRepeat` branch returns `StayInContent`.

- [x] **Step 3: Restore the content-aware repeat guard**

Change the broad repeat branch in `calendarUpFallbackAction` to:

```kotlin
focusedShelfIndex == null && isRepeat -> CalendarUpFallbackAction.StayInContent
```

Keep the first-shelf boundary branch above it unchanged so a held event cannot skip from the first shelf into controls.

- [x] **Step 4: Run the focused test and verify GREEN**

Run the Task 1 command again. Expected: all `TvCalendarFocusRoutingTest` tests pass.

- [x] **Step 5: Commit the Calendar correction**

```bash
git add androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/calendar/TvCalendarScreen.kt androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/calendar/TvCalendarFocusRoutingTest.kt
git commit -m "fix(tv): preserve held Calendar shelf movement"
```

### Task 2: Retry transient Diagnostics focus-request failures

**Files:**
- Modify: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/settings/diagnostics/TvDiagnosticsStateTest.kt:18-34`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/settings/diagnostics/TvDiagnosticsSettingsScreen.kt:71-85,238-246`

**Interfaces:**
- Consumes: `tvDiagnosticsCrashFocusRequestResult(Result<Boolean>)`
- Produces: `FOCUSED` for `true`; `RETRY` for `false` and caught exceptions

- [x] **Step 1: Change the failure test to the required behavior**

Replace `failedFocusRequestIsTerminalBecauseTheScreenWasDisposed` with:

```kotlin
@Test
fun detachedFocusRequesterFailureIsRetryable() {
    assertEquals(
        TvDiagnosticsCrashFocusRequestResult.RETRY,
        tvDiagnosticsCrashFocusRequestResult(
            Result.failure(IllegalStateException("Focus requester is detached")),
        ),
    )
}
```

- [x] **Step 2: Run the focused test and verify RED**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests '*TvDiagnosticsStateTest'
```

Expected: `detachedFocusRequesterFailureIsRetryable` fails because the current function returns `DISPOSED`.

- [x] **Step 3: Remove synthetic disposal classification**

Reduce the enum and classifier to:

```kotlin
internal enum class TvDiagnosticsCrashFocusRequestResult { FOCUSED, RETRY }

internal fun tvDiagnosticsCrashFocusRequestResult(
    result: Result<Boolean>,
): TvDiagnosticsCrashFocusRequestResult = if (result.getOrDefault(false)) {
    TvDiagnosticsCrashFocusRequestResult.FOCUSED
} else {
    TvDiagnosticsCrashFocusRequestResult.RETRY
}
```

Update the `LaunchedEffect` `when` so only `FOCUSED` exits and `RETRY` continues to the next bounded frame. Disposal continues to cancel the effect through structured concurrency.

- [x] **Step 4: Run the focused test and verify GREEN**

Run the Task 2 command again. Expected: all `TvDiagnosticsStateTest` tests pass.

- [x] **Step 5: Commit the Diagnostics correction**

```bash
git add androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/settings/diagnostics/TvDiagnosticsSettingsScreen.kt androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/settings/diagnostics/TvDiagnosticsStateTest.kt
git commit -m "fix(tv): retry detached Diagnostics focus"
```

### Task 3: Preserve Home’s card fallback through deferred retry

**Files:**
- Create: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvDetailReturnFocusState.kt`
- Create: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/shell/TvDetailReturnFocusStateTest.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvMainShell.kt:341-404,609-616`

**Interfaces:**
- Produces: `HomeDetailReturnFocusState(requestId: Int, needsRetry: Boolean, fallbackPending: Boolean)`
- Produces: `beginHomeDetailReturnRetry(previousRequestId: Int, needsRetry: Boolean): HomeDetailReturnFocusState`
- Produces: `completeHomeDetailReturnRetry(state: HomeDetailReturnFocusState): HomeDetailReturnFocusState`
- Produces: `resetHomeDetailReturnFocus(): HomeDetailReturnFocusState`

- [x] **Step 1: Add failing tests for the Home retry lifetime**

Create `TvDetailReturnFocusStateTest.kt`:

```kotlin
package org.siloserver.silo.tv.ui.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvDetailReturnFocusStateTest {
    @Test
    fun requestedHomeRetryKeepsCardFallbackPending() {
        val state = beginHomeDetailReturnRetry(previousRequestId = 7, needsRetry = true)

        assertEquals(8, state.requestId)
        assertTrue(state.needsRetry)
        assertTrue(state.fallbackPending)
    }

    @Test
    fun completedHomeRetryClearsRetryAndFallback() {
        val completed = completeHomeDetailReturnRetry(
            HomeDetailReturnFocusState(requestId = 8, needsRetry = true, fallbackPending = true),
        )

        assertEquals(8, completed.requestId)
        assertFalse(completed.needsRetry)
        assertFalse(completed.fallbackPending)
    }

    @Test
    fun explicitHomeSelectionResetsReturnState() {
        assertEquals(
            HomeDetailReturnFocusState(),
            resetHomeDetailReturnFocus(),
        )
    }
}
```

- [x] **Step 2: Run the focused test and verify RED**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests '*TvDetailReturnFocusStateTest'
```

Expected: compilation fails because the Home state type and transition functions do not exist.

- [x] **Step 3: Add the minimal immutable state model**

Create `TvDetailReturnFocusState.kt`:

```kotlin
package org.siloserver.silo.tv.ui.shell

internal data class HomeDetailReturnFocusState(
    val requestId: Int = 0,
    val needsRetry: Boolean = false,
    val fallbackPending: Boolean = false,
)

internal fun beginHomeDetailReturnRetry(
    previousRequestId: Int,
    needsRetry: Boolean,
): HomeDetailReturnFocusState = HomeDetailReturnFocusState(
    requestId = previousRequestId + 1,
    needsRetry = needsRetry,
    fallbackPending = needsRetry,
)

internal fun completeHomeDetailReturnRetry(
    state: HomeDetailReturnFocusState,
): HomeDetailReturnFocusState = state.copy(
    needsRetry = false,
    fallbackPending = false,
)

internal fun resetHomeDetailReturnFocus(): HomeDetailReturnFocusState =
    HomeDetailReturnFocusState()
```

- [x] **Step 4: Wire the state model into `TvMainShell`**

Replace `homeDetailReturnFocusRequest` and `homeDetailReturnNeedsRetry` with one remembered `HomeDetailReturnFocusState`. Include `homeDetailReturnFocusState.fallbackPending` in the Home branch of `detailReturnFallback`. After the synchronous resume request, call `beginHomeDetailReturnRetry`; after the optional deferred request, call `completeHomeDetailReturnRetry`. On explicit Home selection, assign `resetHomeDetailReturnFocus()`.

Pass `homeDetailReturnFocusState.requestId` to both Home screen call sites. Do not change the For You flow in this task.

- [x] **Step 5: Run focused shell and Home tests and verify GREEN**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests '*TvDetailReturnFocusStateTest' --tests '*TvShellFocusStateTest'
```

Expected: both test classes pass.

- [x] **Step 6: Commit the Home correction**

```bash
git add androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvDetailReturnFocusState.kt androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvMainShell.kt androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/shell/TvDetailReturnFocusStateTest.kt
git commit -m "fix(tv): retain Home detail fallback through retry"
```

### Task 4: Reset stale For You return state on explicit selection

**Files:**
- Modify: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/recommendations/TvRecommendationsFocusBridgeTest.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/recommendations/TvRecommendationsFocusBridge.kt:21-25,91-105`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvMainShell.kt:600-620`

**Interfaces:**
- Consumes: `ForYouDetailReturnState(requestId: Int, pending: Boolean)`
- Produces: `resetForExplicitForYouSelection(): ForYouDetailReturnState`

- [x] **Step 1: Add the failing explicit-reset test**

Add to `TvRecommendationsFocusBridgeTest`:

```kotlin
@Test
fun explicitForYouSelectionClearsStaleReturnState() {
    assertEquals(
        ForYouDetailReturnState(requestId = 0, pending = false),
        resetForExplicitForYouSelection(),
    )
}
```

- [x] **Step 2: Run the focused test and verify RED**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests '*TvRecommendationsFocusBridgeTest'
```

Expected: compilation fails because `resetForExplicitForYouSelection` does not exist.

- [x] **Step 3: Add and wire the explicit reset**

Add to `TvRecommendationsFocusBridge.kt`:

```kotlin
internal fun resetForExplicitForYouSelection(): ForYouDetailReturnState =
    ForYouDetailReturnState(requestId = 0, pending = false)
```

In the `TvRootDestination.ForYou` branch of `onSelectRoot`, call the helper and assign both `forYouDetailReturnFocusRequest` and `forYouDetailReturnFocusPending` from the returned state before creating the top-level For You entry request.

- [x] **Step 4: Run the focused test and verify GREEN**

Run the Task 4 command again. Expected: all `TvRecommendationsFocusBridgeTest` tests pass.

- [x] **Step 5: Commit the For You correction**

```bash
git add androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/recommendations/TvRecommendationsFocusBridge.kt androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvMainShell.kt androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/recommendations/TvRecommendationsFocusBridgeTest.kt
git commit -m "fix(tv): reset stale For You detail return"
```

### Task 5: Verify the complete PR remediation

**Files:**
- Verify only: all files changed in Tasks 1-4

**Interfaces:**
- Consumes: all four independently passing fixes
- Produces: a review-ready PR #164 branch with focused and full validation evidence

- [x] **Step 1: Run all focused regression classes together**

```bash
./gradlew :androidTvApp:testDebugUnitTest \
  --tests '*TvCalendarFocusRoutingTest' \
  --tests '*TvDiagnosticsStateTest' \
  --tests '*TvDetailReturnFocusStateTest' \
  --tests '*TvRecommendationsFocusBridgeTest' \
  --tests '*TvShellFocusStateTest'
```

Expected: all focused tests pass.

- [x] **Step 2: Run the full Android TV unit suite**

```bash
./gradlew :androidTvApp:testDebugUnitTest
```

Expected: zero failures.

- [x] **Step 3: Assemble the Android TV debug APK**

```bash
./gradlew :androidTvApp:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 4: Run repository hygiene checks**

```bash
git diff --check origin/main...HEAD
git status --short
```

Expected: no whitespace errors and no uncommitted files.

- [x] **Step 5: Review the final diff against the approved scope**

```bash
git diff --stat origin/main...HEAD
git diff origin/main...HEAD -- \
  androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/calendar/TvCalendarScreen.kt \
  androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/settings/diagnostics/TvDiagnosticsSettingsScreen.kt \
  androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/recommendations/TvRecommendationsFocusBridge.kt \
  androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvDetailReturnFocusState.kt \
  androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvMainShell.kt
```

Confirm the diff implements only the four approved corrections and their regression coverage.

- [x] **Step 6: Record the remaining device gate**

Report that automated validation is complete while Shield smoke checks remain required for held Calendar movement, Diagnostics initial focus, and Home/For You detail-return restoration.
