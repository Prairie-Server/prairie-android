# TV Navigation and Focus Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Repair all open Android TV navigation, focus-return, and episode-selection findings while preserving the shell focus protocol.

**Architecture:** Pure functions model decisions that can be tested without Compose. Composables retain focus requesters and saveable indices, but delegate routing decisions to those functions. tvOS behavior is canonical unless `AGENTS.md` documents an Android exception.

**Tech Stack:** Kotlin 2.1, Jetpack Compose for TV, coroutines, kotlin.test/JUnit, Gradle.

## Global Constraints

- Rich admin screens and Watch Together remain inaccessible.
- Preserve the existing shell Up-fallback identity registration protocol.
- Use combined subtitle selection indexes; do not reinterpret them as catalog ordinals.
- Every behavioral production change begins with a failing test.
- Do not change calendar height finding 41 without a reproduced child constraint.

---

### Task 1: Shelf-aware calendar Up routing

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/calendar/TvCalendarScreen.kt`
- Create: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/calendar/TvCalendarFocusRoutingTest.kt`

**Interfaces:**
- Produces: `internal fun shouldReturnCalendarFocusToControls(focusedShelfIndex: Int?, firstFocusableShelfIndex: Int, isReturningToControls: Boolean): Boolean`

- [ ] **Step 1: Write the failing routing test**

```kotlin
package org.siloserver.silo.tv.ui.screens.calendar

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvCalendarFocusRoutingTest {
    @Test fun firstFocusableShelfReturnsToControls() {
        assertTrue(shouldReturnCalendarFocusToControls(2, 2, false))
    }

    @Test fun laterShelfUsesNormalUpMovement() {
        assertFalse(shouldReturnCalendarFocusToControls(4, 2, false))
    }

    @Test fun controlsAndReturnInFlightDoNotRestartChoreography() {
        assertFalse(shouldReturnCalendarFocusToControls(null, 2, false))
        assertFalse(shouldReturnCalendarFocusToControls(2, 2, true))
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvCalendarFocusRoutingTest'`

Expected: compilation fails because `shouldReturnCalendarFocusToControls` does not exist.

- [ ] **Step 3: Implement the decision and shelf identity tracking**

```kotlin
internal fun shouldReturnCalendarFocusToControls(
    focusedShelfIndex: Int?,
    firstFocusableShelfIndex: Int,
    isReturningToControls: Boolean,
): Boolean = focusedShelfIndex != null &&
    focusedShelfIndex == firstFocusableShelfIndex &&
    !isReturningToControls
```

Replace `focusedShelfCount` with `focusedShelfIndex: Int?`, pass each `itemsIndexed` index through `onShelfFocusChanged`, clear it only when the losing shelf still owns the recorded index, and call `focusManager.moveFocus(Up)` for later shelves.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvCalendarFocusRoutingTest'`

Expected: all three tests pass.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/calendar/TvCalendarScreen.kt androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/calendar/TvCalendarFocusRoutingTest.kt
git commit -m "fix(tv): preserve calendar shelf up navigation"
```

### Task 2: Saveable library-grid focus restoration

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/components/TvCatalogGrid.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/library/TvLibraryDetailScreen.kt`
- Create: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/library/TvLibraryFocusRestoreTest.kt`

**Interfaces:**
- Produces: `internal fun restoredLibraryFocusIndex(savedIndex: Int, itemCount: Int): Int?`
- `TvCatalogGrid` reports `onItemFocused(index: Int)` and accepts `restoreFocusIndex: Int?` plus `restoreFocusRequest: Int`.

- [ ] **Step 1: Add failing index-clamping tests**

```kotlin
package org.siloserver.silo.tv.ui.screens.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvLibraryFocusRestoreTest {
    @Test fun restoresSavedItemWhenStillPresent() {
        assertEquals(17, restoredLibraryFocusIndex(17, 40))
    }

    @Test fun clampsAfterLibraryShrinks() {
        assertEquals(4, restoredLibraryFocusIndex(17, 5))
    }

    @Test fun emptyGridHasNoRestoreTarget() {
        assertNull(restoredLibraryFocusIndex(3, 0))
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvLibraryFocusRestoreTest'`

Expected: compilation fails because the helper is missing.

- [ ] **Step 3: Implement saveable restoration**

```kotlin
internal fun restoredLibraryFocusIndex(savedIndex: Int, itemCount: Int): Int? =
    if (itemCount <= 0) null else savedIndex.coerceIn(0, itemCount - 1)
```

Store the last focused grid index with `rememberSaveable` in each library browse mode, update it from card focus, scroll to the clamped item before requesting focus after detail return, and consume each restoration token once. Initial entry may still target item zero.

- [ ] **Step 4: Run focused tests and compile**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvLibraryFocusRestoreTest' :androidTvApp:compileDebugKotlinAndroid`

Expected: tests and compilation pass.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/components/TvCatalogGrid.kt androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/library/TvLibraryDetailScreen.kt androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/library/TvLibraryFocusRestoreTest.kt
git commit -m "fix(tv): restore library grid focus position"
```

### Task 3: Search entry versus back-return focus

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/search/TvSearchScreen.kt`
- Create: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/search/TvSearchFocusPolicyTest.kt`

**Interfaces:**
- Produces: `internal fun shouldFocusSearchField(hasEnteredSearch: Boolean, hasResults: Boolean, explicitFieldRequest: Boolean): Boolean`

- [ ] **Step 1: Add failing policy tests**

```kotlin
package org.siloserver.silo.tv.ui.screens.search

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvSearchFocusPolicyTest {
    @Test fun firstEntryFocusesField() {
        assertTrue(shouldFocusSearchField(false, false, false))
    }

    @Test fun backReturnKeepsResultsVisible() {
        assertFalse(shouldFocusSearchField(true, true, false))
    }

    @Test fun explicitRequestAlwaysFocusesField() {
        assertTrue(shouldFocusSearchField(true, true, true))
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvSearchFocusPolicyTest'`

Expected: missing helper compilation failure.

- [ ] **Step 3: Implement one-time entry focus**

```kotlin
internal fun shouldFocusSearchField(
    hasEnteredSearch: Boolean,
    hasResults: Boolean,
    explicitFieldRequest: Boolean,
): Boolean = explicitFieldRequest || (!hasEnteredSearch && !hasResults)
```

Persist `hasEnteredSearch` with `rememberSaveable`, remove the unconditional requester effect, and show the IME only when this policy requests field focus. Existing Up navigation may still explicitly return to the field.

- [ ] **Step 4: Run focused tests and compile**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvSearchFocusPolicyTest' :androidTvApp:compileDebugKotlinAndroid`

Expected: tests and compilation pass.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/search/TvSearchScreen.kt androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/search/TvSearchFocusPolicyTest.kt
git commit -m "fix(tv): keep search results focused on return"
```

### Task 4: Profile-menu Back ownership

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvShellFocusState.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvMainShell.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/shell/TvShellFocusStateTest.kt`

**Interfaces:**
- `TvShellBackAction.CloseProfileMenu` means close the overlay and request avatar focus; it must not synthesize `Root(Home)`.

- [ ] **Step 1: Add a failing state test**

```kotlin
@Test fun backFromProfileMenuClosesToProfileControl() {
    val state = TvShellFocusState()
    state.openProfileMenu()
    assertEquals(TvShellBackAction.CloseProfileMenu, state.onBack(onTabRoot = false))
    assertEquals(TvShellFocusTarget.Profile, state.pendingFocusTarget)
}
```

- [ ] **Step 2: Run the test and verify RED**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvShellFocusStateTest.backFromProfileMenuClosesToProfileControl'`

Expected: assertion fails because the pending target is Home or absent.

- [ ] **Step 3: Implement avatar-return semantics**

Close the profile overlay in the shell, invoke the profile/avatar requester after disposal, and remove the hardcoded Home-root target. Keep Back priority above tab/content navigation.

- [ ] **Step 4: Run shell tests and compile**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvShellFocusStateTest' :androidTvApp:compileDebugKotlinAndroid`

Expected: shell tests and compilation pass.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvShellFocusState.kt androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvMainShell.kt androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/shell/TvShellFocusStateTest.kt
git commit -m "fix(tv): return profile menu back to avatar"
```

### Task 5: Geometric selector entry and cast-rail re-entry

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvItemDetailScreen.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvPlaybackSelectorRow.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvCastCrewSection.kt`
- Create: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvDetailFocusPolicyTest.kt`

**Interfaces:**
- Produces: `internal fun restoredRailIndex(lastFocusedIndex: Int, itemCount: Int): Int?`
- Each detail action receives a Down requester for its nearest selector, or delegates to Compose geometric focus when no corresponding selector exists.

- [ ] **Step 1: Add failing rail restore tests**

```kotlin
package org.siloserver.silo.tv.ui.screens.detail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvDetailFocusPolicyTest {
    @Test fun castRailRestoresLastCard() {
        assertEquals(5, restoredRailIndex(5, 8))
        assertEquals(2, restoredRailIndex(5, 3))
        assertNull(restoredRailIndex(0, 0))
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvDetailFocusPolicyTest'`

Expected: missing helper compilation failure.

- [ ] **Step 3: Implement focus routing**

```kotlin
internal fun restoredRailIndex(lastFocusedIndex: Int, itemCount: Int): Int? =
    if (itemCount <= 0) null else lastFocusedIndex.coerceIn(0, itemCount - 1)
```

Track cast-card focus with `rememberSaveable`, scroll the LazyRow to that index before re-entry, and attach the entry requester to the restored item. Replace the action-row-wide `down = selectorFocus` override with per-action requesters aligned to visible selectors; where alignment is ambiguous, use `FocusRequester.Default` so Compose chooses geometrically.

- [ ] **Step 4: Run focused tests and compile**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvDetailFocusPolicyTest' :androidTvApp:compileDebugKotlinAndroid`

Expected: tests and compilation pass.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvItemDetailScreen.kt androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvPlaybackSelectorRow.kt androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvCastCrewSection.kt androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvDetailFocusPolicyTest.kt
git commit -m "fix(tv): preserve detail navigation positions"
```

### Task 6: Browse-only episode selection

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvItemDetailScreen.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/performance/TvDetailOpenPerformanceSourceTest.kt`

**Interfaces:**
- Episode-card click invokes only `onOpenItemDetail(contentId)`; playback remains on explicit Play actions.

- [ ] **Step 1: Add a failing source-contract assertion**

Add an assertion that the episode rail callback contains `onOpenItemDetail(episode.contentId)` and does not invoke `onPlayEpisode` from the same callback.

- [ ] **Step 2: Run the source test and verify RED**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvDetailOpenPerformanceSourceTest'`

Expected: the new assertion fails against the combined browse-and-play callback.

- [ ] **Step 3: Remove implicit playback**

The episode-card `onClick` body must contain one navigation action. Keep explicit Play/Resume behavior and selector state unchanged.

- [ ] **Step 4: Run detail tests and compile**

Run: `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvDetailOpenPerformanceSourceTest' :androidTvApp:compileDebugKotlinAndroid`

Expected: tests and compilation pass.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvItemDetailScreen.kt androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/performance/TvDetailOpenPerformanceSourceTest.kt
git commit -m "fix(tv): browse episode details before playback"
```

### Task 7: Navigation-stage verification

- [ ] Run `./gradlew :androidTvApp:testDebugUnitTest --tests '*TvCalendarFocusRoutingTest' --tests '*TvLibraryFocusRestoreTest' --tests '*TvSearchFocusPolicyTest' --tests '*TvShellFocusStateTest' --tests '*TvDetailFocusPolicyTest' --tests '*TvDetailOpenPerformanceSourceTest'`.
- [ ] Run `./gradlew :androidTvApp:assembleDebug`.
- [ ] Run `git diff --check`.
- [ ] Confirm calendar later-shelf Up delegates to `moveFocus(Up)`, profile Back targets the avatar, and no inaccessible route was exposed.
