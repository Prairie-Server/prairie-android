# For You Late Focus-Relocation Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the For You list at its true top when a delayed focus-relocation pass moves it after the first recommendation row has already gained focus.

**Architecture:** Convert the focus-scoped early-exit loop into an event-driven observer over `LazyListState` position changes. A small suspend helper owns the timing policy and is exercised with real coroutine flows so the delayed-displacement regression is testable without a Compose UI harness.

**Tech Stack:** Kotlin, Jetpack Compose `snapshotFlow`, Kotlin coroutines `Flow`, `kotlinx-coroutines-test`, Gradle.

## Global Constraints

- Observe scroll changes only while the first recommendation row owns focus.
- Do no work while the list remains at item zero with offset zero.
- Re-check focus and position after the 80 ms relocation-settling delay.
- Do not change the shared bring-into-view policy or other recommendation rows.
- Preserve the full RC display version during verification.

---

### Task 1: Event-driven top-anchor recovery

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/recommendations/TvRecommendationsScreen.kt`
- Create: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/recommendations/TvRecommendationsTopAnchorTest.kt`

**Interfaces:**
- Consumes: a `Flow<ForYouListPosition>`, focus/position readers, an 80 ms settling callback, and a suspend scroll callback.
- Produces: `ForYouListPosition(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int)` and `maintainForYouTopAnchor(...)` for the screen effect and focused unit tests.

- [ ] **Step 1: Write the failing delayed-displacement regression test**

```kotlin
@Test
fun delayedRelocationAfterAnInitiallyCorrectTopIsReanchored() = runTest {
    var current = ForYouListPosition(0, 0)
    var corrections = 0

    maintainForYouTopAnchor(
        positionEvents = flow {
            emit(current)
            current = ForYouListPosition(1, 24)
            emit(current)
        },
        isFirstRowFocused = { true },
        awaitRelocation = {},
        currentPosition = { current },
        scrollToTop = {
            corrections += 1
            current = ForYouListPosition(0, 0)
        },
    )

    assertEquals(1, corrections)
}
```

Add two neighboring tests using `flowOf(...)`: a top-only sequence produces zero corrections, and focus becoming false inside `awaitRelocation` prevents a pending correction.

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest \
  --tests 'org.siloserver.silo.tv.ui.screens.recommendations.TvRecommendationsTopAnchorTest' \
  --no-daemon
```

Expected: compilation fails because `ForYouListPosition` and `maintainForYouTopAnchor` do not exist.

- [ ] **Step 3: Implement the minimal event-driven helper**

```kotlin
internal data class ForYouListPosition(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
) {
    val isAtTop: Boolean
        get() = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0
}

internal suspend fun maintainForYouTopAnchor(
    positionEvents: Flow<ForYouListPosition>,
    isFirstRowFocused: () -> Boolean,
    awaitRelocation: suspend () -> Unit,
    currentPosition: () -> ForYouListPosition,
    scrollToTop: suspend () -> Unit,
) {
    positionEvents.collect { observed ->
        if (!isFirstRowFocused() || observed.isAtTop) return@collect
        awaitRelocation()
        if (isFirstRowFocused() && !currentPosition().isAtTop) scrollToTop()
    }
}
```

In `LaunchedEffect(firstRecommendationRowFocused)`, return immediately when focus is false. Otherwise pass a `snapshotFlow` of the real lazy-list position to the helper, retain the existing 80 ms settling delay, and call `recommendationsListState.animateScrollToItem(0)` only from `scrollToTop`.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the Step 2 command.

Expected: all three `TvRecommendationsTopAnchorTest` cases pass.

- [ ] **Step 5: Commit the behavior and tests**

```bash
git add \
  androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/recommendations/TvRecommendationsScreen.kt \
  androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/recommendations/TvRecommendationsTopAnchorTest.kt
git commit -m "fix(tv): recover late For You focus relocation"
```

### Task 2: Full verification

**Files:**
- Modify: `docs/superpowers/plans/2026-08-03-for-you-late-focus-relocation.md`

**Interfaces:**
- Consumes: the Task 1 helper, integration, and regression suite.
- Produces: a verified Android TV debug APK and completed plan checklist.

- [ ] **Step 1: Run the complete TV verification gate**

```bash
./gradlew \
  :shared:testDebugUnitTest \
  :androidTvApp:testDebugUnitTest \
  :androidTvApp:assembleDebug \
  -PsiloVersionName=1.0.0 \
  -PsiloDisplayVersion=1.0.0-rc.2+5 \
  --no-daemon
bash scripts/test-release-workflow.sh
bash scripts/test-check-build-supply-chain.sh
bash scripts/check-build-supply-chain.sh
git diff --check
```

Expected: Gradle reports `BUILD SUCCESSFUL`, both workflow self-tests pass, the supply-chain check passes, and `git diff --check` emits no errors.

- [ ] **Step 2: Mark the plan complete and commit verification metadata**

Change every task checkbox in this plan from `[ ]` to `[x]`, then run:

```bash
git add docs/superpowers/plans/2026-08-03-for-you-late-focus-relocation.md
git commit -m "docs(tv): complete For You relocation plan"
```

- [ ] **Step 3: Review branch scope**

```bash
git status --short
git diff --stat upstream/main...HEAD
git log --oneline upstream/main..HEAD
```

Expected: the worktree is clean and the branch contains only the approved design, focused implementation/tests, and completed implementation plan.
