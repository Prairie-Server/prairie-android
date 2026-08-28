# Focus Foundations and Enabled Controls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver Series A of the whole-application focus hardening by introducing a bounded observed-focus policy, applying it to dialog startup, making disabled TV controls truly ineligible, preserving Cascade row identity, and deriving playback-selector interactivity from final actionable options.

**Architecture:** Put retry/exhaustion behavior in a pure Kotlin focus-policy unit so later screen migrations share one tested contract while Compose callers retain their own requesters and observed focus state. Keep control, Cascade, and playback changes local to their existing components; use JVM behavior tests where possible and source-wiring guards where the module's current pure-JVM harness cannot execute Compose focus semantics.

**Tech Stack:** Kotlin 2.1, Kotlin coroutines, Jetpack Compose for TV, Kotlin test/JUnit 4, Gradle, Java 21

## Global Constraints

- Android TV only; do not change phone behavior.
- Observed focus is authoritative; a successful call or `true` return is not focus acquisition.
- Retry loops are bounded and composition cancellation remains the disposal authority.
- Disabled controls must be skipped by D-pad focus search, expose disabled semantics, and reject activation at the primitive.
- Stable content identity uses library IDs rather than list positions.
- Selector interactivity is derived from the final enabled option model.
- Do not add a global focus coordinator or migrate screens assigned to Series B through E.
- Shield/Google TV and Fire TV device validation remains a release gate.

---

## File map

- Create `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/focus/TvObservedFocusPolicy.kt`: platform-free target, request-observation, retry, and terminal-result policy.
- Create `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/focus/TvObservedFocusPolicyTest.kt`: exhaustive policy behavior tests.
- Modify `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvDialogInitialFocus.kt`: bounded dialog-specific adapter and Compose wiring.
- Create `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/components/TvDialogInitialFocusTest.kt`: dialog attempt-budget and focus-observation tests.
- Modify the six reusable/control files listed in Task 3: propagate `enabled` into their actual TV `Surface`, `Card`, or `clickable` primitive.
- Create `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/components/TvDisabledControlWiringSourceTest.kt`: regression guard for those primitive-level enabled parameters.
- Modify `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvCascadeSelector.kt`: stable keys for eager and lazy library rows.
- Create `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/components/TvCascadeSelectorIdentitySourceTest.kt`: regression guard for both keyed branches.
- Modify `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/detail/TvPlaybackSelectorRow.kt`: materialize final option lists and compute actionability from them.
- Modify `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/detail/TvPlaybackFormattingTest.kt`: final-option actionability tests.

---

### Task 1: Add the bounded observed-focus policy

**Files:**
- Create: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/focus/TvObservedFocusPolicy.kt`
- Create: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/focus/TvObservedFocusPolicyTest.kt`

**Interfaces:**
- Produces: `TvFocusTargetState { NotReady, Ready, Disposed }`
- Produces: `TvFocusRequestOutcome { Rejected, AcceptedUnobserved, Focused }`
- Produces: `TvObservedFocusResult { Focused, Exhausted, Disposed }`
- Produces: `observeTvFocusRequest(requestAccepted: Boolean, isFocused: Boolean): TvFocusRequestOutcome`
- Produces: `suspend requestFocusUntilObserved(maxAttempts: Int, awaitAttempt: suspend () -> Unit, targetState: () -> TvFocusTargetState, requestFocus: () -> Boolean, isFocused: () -> Boolean): TvObservedFocusResult`

- [ ] **Step 1: Write the failing policy tests**

Create `TvObservedFocusPolicyTest.kt`:

```kotlin
package org.prairieserver.prairie.tv.ui.focus

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TvObservedFocusPolicyTest {
    @Test
    fun requestOutcomeDistinguishesRejectionAcceptanceAndObservation() {
        assertEquals(
            TvFocusRequestOutcome.Rejected,
            observeTvFocusRequest(requestAccepted = false, isFocused = false),
        )
        assertEquals(
            TvFocusRequestOutcome.AcceptedUnobserved,
            observeTvFocusRequest(requestAccepted = true, isFocused = false),
        )
        assertEquals(
            TvFocusRequestOutcome.Focused,
            observeTvFocusRequest(requestAccepted = true, isFocused = true),
        )
    }

    @Test
    fun rejectedAndThrowingRequestsRetryUntilFocusIsObserved() = runTest {
        var requests = 0
        var focused = false

        val result = requestFocusUntilObserved(
            maxAttempts = 5,
            awaitAttempt = {},
            targetState = { TvFocusTargetState.Ready },
            requestFocus = {
                requests++
                when (requests) {
                    1 -> false
                    2 -> error("detached")
                    else -> true.also { focused = true }
                }
            },
            isFocused = { focused },
        )

        assertEquals(TvObservedFocusResult.Focused, result)
        assertEquals(3, requests)
    }

    @Test
    fun acceptedButUnobservedRequestsExhaustTheBudget() = runTest {
        var requests = 0

        val result = requestFocusUntilObserved(
            maxAttempts = 4,
            awaitAttempt = {},
            targetState = { TvFocusTargetState.Ready },
            requestFocus = { true.also { requests++ } },
            isFocused = { false },
        )

        assertEquals(TvObservedFocusResult.Exhausted, result)
        assertEquals(4, requests)
    }

    @Test
    fun notReadyTargetsWaitWithoutRequesting() = runTest {
        var frames = 0
        var requests = 0
        var focused = false

        val result = requestFocusUntilObserved(
            maxAttempts = 5,
            awaitAttempt = { frames++ },
            targetState = {
                if (frames < 3) TvFocusTargetState.NotReady else TvFocusTargetState.Ready
            },
            requestFocus = {
                requests++
                true.also { focused = true }
            },
            isFocused = { focused },
        )

        assertEquals(TvObservedFocusResult.Focused, result)
        assertEquals(1, requests)
        assertEquals(3, frames)
    }

    @Test
    fun disposedTargetStopsWithoutRequestingAgain() = runTest {
        var frames = 0
        var requests = 0

        val result = requestFocusUntilObserved(
            maxAttempts = 5,
            awaitAttempt = { frames++ },
            targetState = {
                if (frames < 2) TvFocusTargetState.Ready else TvFocusTargetState.Disposed
            },
            requestFocus = { false.also { requests++ } },
            isFocused = { false },
        )

        assertEquals(TvObservedFocusResult.Disposed, result)
        assertEquals(1, requests)
    }

    @Test
    fun existingObservedFocusCompletesWithoutRequesting() = runTest {
        var requests = 0

        val result = requestFocusUntilObserved(
            maxAttempts = 3,
            awaitAttempt = {},
            targetState = { TvFocusTargetState.Ready },
            requestFocus = { true.also { requests++ } },
            isFocused = { true },
        )

        assertEquals(TvObservedFocusResult.Focused, result)
        assertEquals(0, requests)
    }
}
```

- [ ] **Step 2: Run the focused tests and verify RED**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests '*TvObservedFocusPolicyTest'
```

Expected: test compilation fails because the policy types and functions do not exist.

- [ ] **Step 3: Implement the pure policy**

Create `TvObservedFocusPolicy.kt`:

```kotlin
package org.prairieserver.prairie.tv.ui.focus

internal enum class TvFocusTargetState { NotReady, Ready, Disposed }

internal enum class TvFocusRequestOutcome { Rejected, AcceptedUnobserved, Focused }

internal enum class TvObservedFocusResult { Focused, Exhausted, Disposed }

internal fun observeTvFocusRequest(
    requestAccepted: Boolean,
    isFocused: Boolean,
): TvFocusRequestOutcome = when {
    isFocused -> TvFocusRequestOutcome.Focused
    requestAccepted -> TvFocusRequestOutcome.AcceptedUnobserved
    else -> TvFocusRequestOutcome.Rejected
}

internal suspend fun requestFocusUntilObserved(
    maxAttempts: Int,
    awaitAttempt: suspend () -> Unit,
    targetState: () -> TvFocusTargetState,
    requestFocus: () -> Boolean,
    isFocused: () -> Boolean,
): TvObservedFocusResult {
    require(maxAttempts > 0) { "maxAttempts must be positive" }

    repeat(maxAttempts) {
        awaitAttempt()
        if (isFocused()) return TvObservedFocusResult.Focused

        when (targetState()) {
            TvFocusTargetState.Disposed -> return TvObservedFocusResult.Disposed
            TvFocusTargetState.NotReady -> Unit
            TvFocusTargetState.Ready -> {
                val accepted = runCatching(requestFocus).getOrDefault(false)
                if (observeTvFocusRequest(accepted, isFocused()) == TvFocusRequestOutcome.Focused) {
                    return TvObservedFocusResult.Focused
                }
            }
        }
    }

    return when {
        isFocused() -> TvObservedFocusResult.Focused
        targetState() == TvFocusTargetState.Disposed -> TvObservedFocusResult.Disposed
        else -> TvObservedFocusResult.Exhausted
    }
}
```

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run the Task 1 command again. Expected: all six tests pass.

- [ ] **Step 5: Commit the focus-policy foundation**

```bash
git add androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/focus/TvObservedFocusPolicy.kt androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/focus/TvObservedFocusPolicyTest.kt
git commit -m "feat(tv): add observed focus retry policy"
```

### Task 2: Bound dialog initial-focus acquisition

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvDialogInitialFocus.kt`
- Create: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/components/TvDialogInitialFocusTest.kt`

**Interfaces:**
- Consumes: `TvFocusTargetState.Ready`, `TvObservedFocusResult`, and `requestFocusUntilObserved(...)` from Task 1
- Produces: `TvDialogInitialFocusMaxAttempts: Int = 40`
- Produces: `suspend requestTvDialogInitialFocus(awaitAttempt: suspend () -> Unit, isOverlayFocused: () -> Boolean, requestFocus: () -> Boolean): TvObservedFocusResult`
- Preserves: `rememberTvDialogInitialFocus(target: FocusRequester): Modifier`

- [ ] **Step 1: Write failing tests for the dialog adapter**

Create `TvDialogInitialFocusTest.kt`:

```kotlin
package org.prairieserver.prairie.tv.ui.components

import kotlinx.coroutines.test.runTest
import org.prairieserver.prairie.tv.ui.focus.TvObservedFocusResult
import kotlin.test.Test
import kotlin.test.assertEquals

class TvDialogInitialFocusTest {
    @Test
    fun unobservedDialogFocusStopsAtTheFixedBudget() = runTest {
        var attempts = 0

        val result = requestTvDialogInitialFocus(
            awaitAttempt = {},
            isOverlayFocused = { false },
            requestFocus = { true.also { attempts++ } },
        )

        assertEquals(TvObservedFocusResult.Exhausted, result)
        assertEquals(TvDialogInitialFocusMaxAttempts, attempts)
    }

    @Test
    fun focusOnAnyDialogChildStopsTargetRequests() = runTest {
        var overlayFocused = false
        var attempts = 0

        val result = requestTvDialogInitialFocus(
            awaitAttempt = {
                if (attempts == 1) overlayFocused = true
            },
            isOverlayFocused = { overlayFocused },
            requestFocus = { false.also { attempts++ } },
        )

        assertEquals(TvObservedFocusResult.Focused, result)
        assertEquals(1, attempts)
    }
}
```

- [ ] **Step 2: Run the focused tests and verify RED**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests '*TvDialogInitialFocusTest'
```

Expected: test compilation fails because the dialog adapter and attempt constant do not exist.

- [ ] **Step 3: Replace the unbounded loop with the tested adapter**

Replace `TvDialogInitialFocus.kt` with:

```kotlin
package org.prairieserver.prairie.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay
import org.prairieserver.prairie.tv.ui.focus.TvFocusTargetState
import org.prairieserver.prairie.tv.ui.focus.TvObservedFocusResult
import org.prairieserver.prairie.tv.ui.focus.requestFocusUntilObserved

internal const val TvDialogInitialFocusMaxAttempts = 40
private const val TvDialogInitialFocusRetryDelayMillis = 60L

internal suspend fun requestTvDialogInitialFocus(
    awaitAttempt: suspend () -> Unit,
    isOverlayFocused: () -> Boolean,
    requestFocus: () -> Boolean,
): TvObservedFocusResult = requestFocusUntilObserved(
    maxAttempts = TvDialogInitialFocusMaxAttempts,
    awaitAttempt = awaitAttempt,
    targetState = { TvFocusTargetState.Ready },
    requestFocus = requestFocus,
    isFocused = isOverlayFocused,
)

/**
 * Bounded retry-until-observed initial focus for popup overlays.
 *
 * Attach the returned modifier to the overlay content root. Focus on any child
 * completes acquisition; forty 60 ms attempts provide a 2.4 second ceiling.
 * Leaving composition cancels the effect through structured concurrency.
 */
@Composable
internal fun rememberTvDialogInitialFocus(target: FocusRequester): Modifier {
    var overlayHasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(target) {
        requestTvDialogInitialFocus(
            awaitAttempt = { delay(TvDialogInitialFocusRetryDelayMillis) },
            isOverlayFocused = { overlayHasFocus },
            requestFocus = target::requestFocus,
        )
    }
    return Modifier.onFocusChanged { overlayHasFocus = it.hasFocus }
}
```

- [ ] **Step 4: Run the dialog and policy tests and verify GREEN**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests '*TvDialogInitialFocusTest' --tests '*TvObservedFocusPolicyTest'
```

Expected: all eight tests pass and no loop can outlive the 40-attempt budget unless composition cancellation ends it sooner.

- [ ] **Step 5: Commit the bounded dialog behavior**

```bash
git add androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvDialogInitialFocus.kt androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/components/TvDialogInitialFocusTest.kt
git commit -m "fix(tv): bound dialog initial focus retries"
```

### Task 3: Propagate enabled state to TV input primitives

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvOptionDialog.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvAuroraChrome.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvPinEntryDialog.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/watchtogether/TvJoinCodeDialog.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/settings/TvCardOverlaySettingsScreen.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/admin/TvAdminScansScreen.kt`
- Create: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/components/TvDisabledControlWiringSourceTest.kt`

**Interfaces:**
- Preserves every public composable signature.
- Changes primitive contracts so `enabled = false` reaches TV Material `Surface`, TV Material `Card`, or Foundation `clickable` directly.
- Changes `PinKey` to consume `enabled: Boolean` and makes every `PinKeypad` call pass it.

- [ ] **Step 1: Add the failing primitive-wiring guard**

Create `TvDisabledControlWiringSourceTest.kt`:

```kotlin
package org.prairieserver.prairie.tv.ui.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class TvDisabledControlWiringSourceTest {
    @Test
    fun disabledStateReachesEveryInteractivePrimitive() {
        val optionDialog = source("ui/components/TvOptionDialog.kt")
        assertContains(optionDialog, "onClick = onClick,\n        enabled = enabled,")

        val aurora = source("ui/components/TvAuroraChrome.kt")
        assertContains(aurora, "enabled = enabled,\n                onClick = onClick,")

        val pin = source("ui/components/TvPinEntryDialog.kt")
        assertContains(pin, "enabled: Boolean,\n    onClick: () -> Unit,")
        assertContains(pin, "onClick = onDigitPressed")
        assertContains(pin, "onClick = onBackspacePressed")
        assertContains(pin, "enabled = enabled,")

        val join = source("ui/screens/watchtogether/TvJoinCodeDialog.kt")
        assertContains(join, "onClick = onClick,\n        enabled = enabled,")

        val overlays = source("ui/screens/settings/TvCardOverlaySettingsScreen.kt")
        assertContains(overlays, "onClick = onClick,\n        enabled = enabled,")

        val scans = source("ui/screens/admin/TvAdminScansScreen.kt")
        assertContains(scans, "onClick = onClick,\n        enabled = enabled,")

        listOf(optionDialog, aurora, pin, join, overlays, scans).forEach { text ->
            assertFalse(text.contains("onClick = { if (enabled) onClick() }"))
        }
    }

    private fun source(relativePath: String): String = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/tv/$relativePath",
    ).readText()
}
```

- [ ] **Step 2: Run the wiring guard and verify RED**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests '*TvDisabledControlWiringSourceTest'
```

Expected: the test fails because the listed controls guard callbacks while leaving their primitives enabled.

- [ ] **Step 3: Wire enabled state into each primitive**

Make these exact changes:

```kotlin
// TvOptionDialogRow
Surface(
    onClick = onClick,
    enabled = enabled,
    interactionSource = interactionSource,
```

```kotlin
// AuroraPrimaryButton
.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    enabled = enabled,
    onClick = onClick,
)
```

Change `PinKey` to accept `enabled` and pass it to its `Surface`:

```kotlin
private fun PinKey(
    label: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
```

Replace the keypad calls with direct callbacks and the shared state:

```kotlin
PinKey(
    label = digit.toString(),
    enabled = enabled,
    modifier = if (digit == '5') Modifier.focusRequester(fiveFocusRequester) else Modifier,
    onClick = { onDigitPressed(digit) },
)
```

```kotlin
PinKey(label = "0", enabled = enabled, onClick = { onDigitPressed('0') })
PinKey(
    label = null,
    enabled = enabled,
    icon = Icons.AutoMirrored.Filled.Backspace,
    onClick = onBackspacePressed,
)
```

In `JoinCodeKey` and `OverlayResetRow`, replace their guarded `Surface` callbacks with:

```kotlin
// JoinCodeKey and OverlayResetRow
Surface(
    onClick = onClick,
    enabled = enabled,
```

```kotlin
// ActionCard
Card(
    onClick = onClick,
    enabled = enabled,
```

Keep the existing disabled colors and alpha so visual behavior does not regress. Native primitive `enabled` supplies focus exclusion, disabled semantics, and activation rejection.

- [ ] **Step 4: Run the focused guard and compile production code**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests '*TvDisabledControlWiringSourceTest' :androidTvApp:compileDebugKotlinAndroid
```

Expected: the guard passes and the production source compiles against the TV Material enabled overloads.

- [ ] **Step 5: Commit enabled-state correctness**

```bash
git add androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvOptionDialog.kt androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvAuroraChrome.kt androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvPinEntryDialog.kt androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/watchtogether/TvJoinCodeDialog.kt androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/settings/TvCardOverlaySettingsScreen.kt androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/admin/TvAdminScansScreen.kt androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/components/TvDisabledControlWiringSourceTest.kt
git commit -m "fix(tv): remove disabled controls from focus"
```

### Task 4: Give Cascade library rows stable composition identity

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvCascadeSelector.kt`
- Create: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/components/TvCascadeSelectorIdentitySourceTest.kt`

**Interfaces:**
- Preserves Cascade selection and focus-requester maps keyed by `library.id`.
- Adds Compose identity `key(library.id)` to the eager branch and `items(libraries, key = { it.id })` to the lazy branch.

- [ ] **Step 1: Add a failing stable-identity guard**

Create `TvCascadeSelectorIdentitySourceTest.kt`:

```kotlin
package org.prairieserver.prairie.tv.ui.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains

class TvCascadeSelectorIdentitySourceTest {
    private val source = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvCascadeSelector.kt",
    ).readText()

    @Test
    fun eagerAndLazyLibraryRowsUseLibraryIdentity() {
        assertContains(source, "key(library.id) {")
        assertContains(source, "items(libraries, key = { it.id }) { library ->")
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests '*TvCascadeSelectorIdentitySourceTest'
```

Expected: the test fails because both branches currently use positional composition identity.

- [ ] **Step 3: Key both Cascade branches**

Add:

```kotlin
import androidx.compose.runtime.key
```

Wrap the eager branch's existing row body without changing it:

```kotlin
libraries.forEach { library ->
    key(library.id) {
        val requester = libraryRequesters.getOrPut(library.id) { FocusRequester() }
        CascadeLibraryRow(
            library = library,
            type = type,
            isCurrent = library.id == currentScopeId,
            entersPanel = entersPanel,
            focusRequester = requester,
            onFocusChanged = { focused ->
                focusedRowId = if (focused) {
                    library.id
                } else {
                    focusedRowId.takeUnless { it == library.id }
                }
            },
            onTopChanged = { top -> rowTops[library.id] = top },
            onMoveRight = {
                anchorId = library.id
                val firstPill = pills.firstOrNull()
                if (firstPill != null) {
                    flyoutVisible = true
                    focusFirstPillToken++
                    true
                } else {
                    false
                }
            },
            onSelect = {
                onCommitLibrary(library)
                true
            },
        )
    }
}
```

Replace the lazy items declaration with:

```kotlin
items(libraries, key = { it.id }) { library ->
```

- [ ] **Step 4: Run the focused test and compile production code**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests '*TvCascadeSelectorIdentitySourceTest' :androidTvApp:compileDebugKotlinAndroid
```

Expected: the guard passes and Cascade compiles with stable keys in both list-size branches.

- [ ] **Step 5: Commit Cascade identity**

```bash
git add androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvCascadeSelector.kt androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/components/TvCascadeSelectorIdentitySourceTest.kt
git commit -m "fix(tv): key Cascade rows by library"
```

### Task 5: Derive selector interactivity from final enabled options

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/detail/TvPlaybackSelectorRow.kt`
- Modify: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/detail/TvPlaybackFormattingTest.kt`

**Interfaces:**
- Replaces: `selectorIsInteractive(optionCount: Int): Boolean`
- Produces: `selectorIsInteractive(options: List<TvSelectorOption>): Boolean`
- Preserves all `TvPlaybackSelectorRow` callback and selection contracts.

- [ ] **Step 1: Replace the count tests with final-option tests**

Add this import to `TvPlaybackFormattingTest.kt`:

```kotlin
import org.prairieserver.prairie.tv.ui.components.TvSelectorOption
```

Replace `singleChoiceSelectorIsStatic` with:

```kotlin
@Test
fun selectorNeedsAtLeastTwoEnabledFinalOptions() {
    val onlyAction = selectorOption("auto")
    val unavailable = selectorOption("unknown", enabled = false)

    assertFalse(selectorIsInteractive(emptyList()))
    assertFalse(selectorIsInteractive(listOf(onlyAction, unavailable)))
    assertTrue(selectorIsInteractive(listOf(onlyAction, selectorOption("off"))))
}

@Test
fun onePhysicalSubtitleTrackStillLeavesThreeActions() {
    val options = listOf(
        selectorOption("subtitle:auto"),
        selectorOption("subtitle:off"),
        selectorOption("subtitle:track:1"),
    )

    assertTrue(selectorIsInteractive(options))
}

private fun selectorOption(key: String, enabled: Boolean = true) = TvSelectorOption(
    key = key,
    title = key,
    detail = "",
    selected = false,
    enabled = enabled,
    onSelect = {},
)
```

- [ ] **Step 2: Run the focused tests and verify RED**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests '*TvPlaybackFormattingTest'
```

Expected: test compilation fails because `selectorIsInteractive` still accepts an integer.

- [ ] **Step 3: Change the helper to inspect enabled final options**

Replace the helper with:

```kotlin
internal fun selectorIsInteractive(options: List<TvSelectorOption>): Boolean =
    options.count(TvSelectorOption::enabled) > 1
```

- [ ] **Step 4: Materialize and reuse each final selector option list**

Immediately after `scopedVersions`, add these `editionOptions`, `versionOptions`, `audioSelectorOptions`, and `subtitleSelectorOptions` declarations:

```kotlin
val editionOptions = editions.map { edition ->
    val count = edition.versions.size
    TvSelectorOption(
        key = "edition:${edition.id}",
        title = edition.label,
        detail = "$count version${if (count == 1) "" else "s"}",
        selected = currentEdition?.id == edition.id,
        onSelect = { onSelectVersion(edition.versions.firstOrNull()?.fileId) },
    )
}
val versionOptions = buildList {
    add(
        TvSelectorOption(
            key = "version:auto",
            title = "Auto",
            detail = "Best match for this device",
            selected = selectedVersionFileId == null,
            onSelect = { onSelectVersion(null) },
        ),
    )
    scopedVersions.forEach { version ->
        add(
            TvSelectorOption(
                key = "version:${version.fileId}",
                title = TvPlaybackFormatting.versionShortLabel(version),
                detail = TvPlaybackFormatting.versionDetailLabel(version),
                selected = selectedVersionFileId == version.fileId,
                onSelect = { onSelectVersion(version.fileId) },
            ),
        )
    }
}
val audioSelectorOptions = buildList {
    add(
        TvSelectorOption(
            key = "audio:auto",
            title = "Auto",
            detail = "Use the file default track",
            selected = isAudioSelectorOptionSelected(null, selectedAudioTrackIndex),
            onSelect = { onSelectAudioTrack(null) },
        ),
    )
    val formattedAudioOptions =
        TvPlaybackFormatting.audioOptions(currentVersion, selectedAudioTrackIndex)
    if (formattedAudioOptions.isEmpty()) {
        add(
            TvSelectorOption(
                key = "audio:unknown",
                title = "Unknown",
                detail = "",
                selected = false,
                onSelect = {},
                enabled = false,
            ),
        )
    } else {
        formattedAudioOptions.forEach { option ->
            add(
                TvSelectorOption(
                    key = "audio:${option.ordinal}",
                    title = option.title,
                    detail = option.detail,
                    selected = option.isSelected,
                    onSelect = { onSelectAudioTrack(option.ordinal) },
                ),
            )
        }
    }
}
val subtitleSelectorOptions = buildList {
    add(
        TvSelectorOption(
            key = "subtitle:auto",
            title = "Auto",
            detail = "Use your subtitle preferences",
            selected = selectedSubtitleTrackIndex == null,
            onSelect = { onSelectSubtitleTrack(null) },
        ),
    )
    add(
        TvSelectorOption(
            key = "subtitle:off",
            title = "Off",
            detail = "Start without subtitles",
            selected = selectedSubtitleTrackIndex == -1,
            onSelect = { onSelectSubtitleTrack(-1) },
        ),
    )
    TvPlaybackFormatting.subtitleOptions(
        currentVersion,
        selectedSubtitleTrackIndex,
        preferredLanguage = preferredSubtitleLanguage,
    ).forEach { option ->
        add(
            TvSelectorOption(
                key = "subtitle:${option.stableId}",
                title = option.title,
                detail = option.detail,
                selected = option.isSelected,
                onSelect = { onSelectSubtitleTrack(option.selectionIndex) },
            ),
        )
    }
}
```

Pass each list to both parameters of its menu:

```kotlin
options = editionOptions,
interactive = selectorIsInteractive(editionOptions),
```

```kotlin
options = versionOptions,
interactive = selectorIsInteractive(versionOptions),
```

```kotlin
options = audioSelectorOptions,
interactive = selectorIsInteractive(audioSelectorOptions),
```

```kotlin
options = subtitleSelectorOptions,
interactive = selectorIsInteractive(subtitleSelectorOptions),
```

This deliberately corrects Version and Audio along with Subtitles: Auto plus one physical option is actionable, while Auto plus a disabled Unknown row is not.

- [ ] **Step 5: Run the focused tests and verify GREEN**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests '*TvPlaybackFormattingTest'
```

Expected: all playback-formatting tests pass, including the single-physical-subtitle regression.

- [ ] **Step 6: Commit final-option actionability**

```bash
git add androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/detail/TvPlaybackSelectorRow.kt androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/detail/TvPlaybackFormattingTest.kt
git commit -m "fix(tv): derive selectors from actionable options"
```

### Task 6: Verify Series A as an integrated change

**Files:**
- Verify: all files changed in Tasks 1 through 5

**Interfaces:**
- Consumes all Series A production and test changes.
- Produces a green TV unit-test suite and installable debug APKs without expanding into later focus-hardening series.

- [ ] **Step 1: Run every new or changed focused test**

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests '*TvObservedFocusPolicyTest' --tests '*TvDialogInitialFocusTest' --tests '*TvDisabledControlWiringSourceTest' --tests '*TvCascadeSelectorIdentitySourceTest' --tests '*TvPlaybackFormattingTest'
```

Expected: all focused tests pass.

- [ ] **Step 2: Run the complete TV unit-test suite**

```bash
./gradlew :androidTvApp:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL` with no failing TV unit tests.

- [ ] **Step 3: Assemble every TV debug APK variant**

```bash
./gradlew :androidTvApp:assembleDebug
```

Expected: `BUILD SUCCESSFUL` and debug APK artifacts under `androidTvApp/build/outputs/apk/debug/`.

- [ ] **Step 4: Run repository hygiene checks**

```bash
git diff --check
git status --short
```

Expected: `git diff --check` prints nothing; status contains no unintended files.

- [ ] **Step 5: Perform the device focus matrix**

On one Shield/Google TV device and one Fire TV device, verify these exact cases:

1. Open an option dialog repeatedly from cold and warm screens; a row gains focus, D-pad works immediately, and focus is not stolen after moving to another row.
2. Leave a dialog open beyond 2.4 seconds; no repeated focus steal occurs after the attempt budget.
3. Enter PIN and join-code busy states; disabled keys are skipped by D-pad traversal and Select does not activate them.
4. Open Card Overlay Settings with defaults already selected; Reset is skipped and exposes disabled accessibility state.
5. Open Admin Scans during a state that disables an action; the action card is skipped and cannot activate.
6. Reorder or refresh libraries with Cascade open in both six-or-fewer and seven-or-more cases; the focused library retains identity.
7. Open playback selectors with one version, one audio track, and one subtitle track; Version, Audio, and Subtitles remain focusable when their final menus contain at least two enabled actions.
8. Open Audio with no tracks; Auto plus disabled Unknown is not a focusable no-op selector.

Expected: every case matches the stated result on both device families.

- [ ] **Step 6: Record verification without creating an empty commit**

Run:

```bash
git log --oneline -5
git status --short --branch
```

Expected: the five task commits are present and the worktree is clean. Record device models, OS versions, and pass/fail results in the pull-request description when the branch is published.
