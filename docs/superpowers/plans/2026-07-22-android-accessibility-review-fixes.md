# Android Accessibility Review Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve all eight valid review findings on Android PR #87 and propagate the verified fix commit through PRs #88–#93.

**Architecture:** Keep Compose rendering changes local to the affected screens, but isolate the two responsive decisions as pure Kotlin policies so narrow-width behavior can be tested without UI instrumentation. Land and verify the fixes once on `pr/accessibility-foundations-v2`, then rebase the existing linear stack in order.

**Tech Stack:** Kotlin 2.1, Jetpack Compose, Compose for TV, Kotlin/JUnit tests, Gradle, GitHub stacked PR branches.

## Global Constraints

- Preserve every existing audiobook and player action; compact layouts may move actions but cannot remove them.
- Interactive phone targets remain at least 48dp; the audiobook play target remains 82dp.
- Android TV synopsis text must retain a 60dp minimum while allowing accessibility font scaling.
- Do not expose Android TV admin screens that are currently product-gated.
- Do not add observability, telemetry, signing material, generated output, or media fixtures.
- Push rewritten stacked branches only with `--force-with-lease`.

---

### Task 1: Test and implement pure responsive layout policies

**Files:**
- Create: `androidApp/src/androidMain/kotlin/org/siloserver/silo/android/ui/layout/ResponsiveControlLayout.kt`
- Create: `androidApp/src/androidUnitTest/kotlin/org/siloserver/silo/android/ui/layout/ResponsiveControlLayoutTest.kt`

**Interfaces:**
- Produces: `AudiobookTransportLayout`, `resolveAudiobookTransportLayout(availableWidthDp, hasChapters)`, and `useCompactPlayerToolbar(availableWidthDp, trailingActionCount)`.
- Consumed by: `AudiobookTransport.kt` and `PlayerControls.kt` in Task 2.

- [ ] **Step 1: Write the failing policy tests**

```kotlin
package org.siloserver.silo.android.ui.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResponsiveControlLayoutTest {
    @Test
    fun audiobookTransportKeepsPreferredSpacingWhenItFits() {
        assertEquals(
            AudiobookTransportLayout(spacingDp = 28f, requiresHorizontalScroll = false),
            resolveAudiobookTransportLayout(availableWidthDp = 390f, hasChapters = true),
        )
    }

    @Test
    fun audiobookTransportCompactsSpacingAtTypicalNarrowPhoneWidth() {
        assertEquals(
            AudiobookTransportLayout(spacingDp = 8.5f, requiresHorizontalScroll = false),
            resolveAudiobookTransportLayout(availableWidthDp = 312f, hasChapters = true),
        )
    }

    @Test
    fun audiobookTransportScrollsWhenMinimumSpacingCannotFit() {
        assertEquals(
            AudiobookTransportLayout(spacingDp = 8f, requiresHorizontalScroll = true),
            resolveAudiobookTransportLayout(availableWidthDp = 300f, hasChapters = true),
        )
    }

    @Test
    fun toolbarCompactsWhenActionsWouldEraseTheTitle() {
        assertTrue(useCompactPlayerToolbar(availableWidthDp = 328f, trailingActionCount = 5))
        assertFalse(useCompactPlayerToolbar(availableWidthDp = 800f, trailingActionCount = 5))
        assertTrue(useCompactPlayerToolbar(availableWidthDp = 328f, trailingActionCount = 3))
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests '*ResponsiveControlLayoutTest'
```

Expected: compilation fails because `AudiobookTransportLayout`, `resolveAudiobookTransportLayout`, and `useCompactPlayerToolbar` do not exist.

- [ ] **Step 3: Implement the minimal policies**

```kotlin
package org.siloserver.silo.android.ui.layout

internal data class AudiobookTransportLayout(
    val spacingDp: Float,
    val requiresHorizontalScroll: Boolean,
)

private const val PreferredTransportSpacingDp = 28f
private const val MinimumTransportSpacingDp = 8f
private const val ChapterTransportControlWidthDp = 278f
private const val CompactTransportControlWidthDp = 182f
private const val MinimumToolbarTitleWidthDp = 96f
private const val ToolbarButtonWidthDp = 48f
private const val ToolbarSpacingDp = 12f

internal fun resolveAudiobookTransportLayout(
    availableWidthDp: Float,
    hasChapters: Boolean,
): AudiobookTransportLayout {
    val controlWidth = if (hasChapters) ChapterTransportControlWidthDp else CompactTransportControlWidthDp
    val gapCount = if (hasChapters) 4 else 2
    val fittedSpacing = ((availableWidthDp - controlWidth) / gapCount)
        .coerceIn(MinimumTransportSpacingDp, PreferredTransportSpacingDp)
    return AudiobookTransportLayout(
        spacingDp = fittedSpacing,
        requiresHorizontalScroll = availableWidthDp < controlWidth + MinimumTransportSpacingDp * gapCount,
    )
}

internal fun useCompactPlayerToolbar(
    availableWidthDp: Float,
    trailingActionCount: Int,
): Boolean {
    val buttonCount = trailingActionCount + 1
    val gapCount = trailingActionCount + 1
    val expandedWidth = buttonCount * ToolbarButtonWidthDp +
        gapCount * ToolbarSpacingDp +
        MinimumToolbarTitleWidthDp
    return expandedWidth > availableWidthDp
}
```

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests '*ResponsiveControlLayoutTest'
```

Expected: four tests pass.

- [ ] **Step 5: Commit the policy slice**

```bash
git add androidApp/src/androidMain/kotlin/org/siloserver/silo/android/ui/layout/ResponsiveControlLayout.kt \
  androidApp/src/androidUnitTest/kotlin/org/siloserver/silo/android/ui/layout/ResponsiveControlLayoutTest.kt
git commit -m "test(phone): define responsive control layout policy"
```

### Task 2: Apply responsive policies to audiobook and player controls

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/audiobook/AudiobookTransport.kt`
- Modify: `androidApp/src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/player/PlayerControls.kt`

**Interfaces:**
- Consumes: Task 1's responsive policy functions.
- Produces: a five-action audiobook transport that fits or scrolls safely and a compact player toolbar that preserves every action.

- [ ] **Step 1: Run the focused policy tests as the pre-change guard**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests '*ResponsiveControlLayoutTest'
```

Expected: all four tests pass before Compose integration.

- [ ] **Step 2: Make the audiobook transport width-aware**

Wrap the existing `Row` in `BoxWithConstraints`, resolve the policy using `maxWidth.value`, create one remembered scroll state, and apply horizontal scrolling only when required:

```kotlin
BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    val layout = resolveAudiobookTransportLayout(
        availableWidthDp = maxWidth.value,
        hasChapters = hasChapters,
    )
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (layout.requiresHorizontalScroll) {
                    Modifier.horizontalScroll(scrollState)
                } else {
                    Modifier
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(
            layout.spacingDp.dp,
            Alignment.CenterHorizontally,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasChapters) {
            IconButton(
                onClick = onPrevChapter,
                enabled = enabled,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Previous Chapter",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        IconButton(
            onClick = onSkipBack,
            enabled = enabled,
            modifier = Modifier.size(50.dp),
        ) {
            Icon(
                imageVector = skipBackIcon(skipBackSeconds),
                contentDescription = "Back $skipBackSeconds seconds",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(32.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(82.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                )
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(enabled = enabled, onClick = onTogglePlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(32.dp),
            )
        }
        IconButton(
            onClick = onSkipForward,
            enabled = enabled,
            modifier = Modifier.size(50.dp),
        ) {
            Icon(
                imageVector = skipForwardIcon(skipForwardSeconds),
                contentDescription = "Forward $skipForwardSeconds seconds",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(32.dp),
            )
        }
        if (hasChapters) {
            IconButton(
                onClick = onNextChapter,
                enabled = enabled,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Next Chapter",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
```

Add the exact imports for `BoxWithConstraints`, `horizontalScroll`, `rememberScrollState`, and both Task 1 policy symbols. Remove the old outer `modifier.fillMaxWidth()` from the inner `Row`.

- [ ] **Step 3: Add a compact overflow variant to the player toolbar**

Use `BoxWithConstraints` around the top `Row`, compute `trailingActionCount` as orientation + tracks + settings + optional chapters + optional quality, and switch presentation with `useCompactPlayerToolbar(maxWidth.value, trailingActionCount)`.

The compact trailing control is:

```kotlin
var overflowExpanded by remember { mutableStateOf(false) }

ControlButton(
    icon = Icons.Default.MoreVert,
    contentDescription = "More playback actions",
    onClick = { overflowExpanded = true },
)
DropdownMenu(
    expanded = overflowExpanded,
    onDismissRequest = { overflowExpanded = false },
) {
    PlayerToolbarMenuItem("Rotate", onToggleOrientationLock) { overflowExpanded = false }
    if (hasChapters) {
        PlayerToolbarMenuItem("Chapters", onOpenChapters) { overflowExpanded = false }
    }
    PlayerToolbarMenuItem(
        label = "Audio and subtitles",
        onClick = onOpenTracks,
        enabled = hasTracks,
        onDismiss = { overflowExpanded = false },
    )
    if (hasMultipleVersions) {
        PlayerToolbarMenuItem("Quality", onOpenQuality) { overflowExpanded = false }
    }
    PlayerToolbarMenuItem("Playback settings", onOpenSettings) { overflowExpanded = false }
}
```

Add this file-local helper so every selection dismisses before invoking its action:

```kotlin
@Composable
private fun PlayerToolbarMenuItem(
    label: String,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    enabled: Boolean = true,
) {
    DropdownMenuItem(
        text = { Text(label) },
        enabled = enabled,
        onClick = {
            onDismiss()
            onClick()
        },
    )
}
```

Move the existing expanded icon row into the `else` branch without changing its callbacks, visibility rules, enabled rules, target sizes, or descriptions:

```kotlin
BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val trailingActionCount = 3 +
        (if (hasChapters) 1 else 0) +
        (if (hasMultipleVersions) 1 else 0)
    val compact = useCompactPlayerToolbar(maxWidth.value, trailingActionCount)

    if (compact) {
        CompactPlayerToolbar(
            title = title,
            isOrientationLocked = isOrientationLocked,
            hasChapters = hasChapters,
            hasTracks = hasTracks,
            hasMultipleVersions = hasMultipleVersions,
            onBack = onBack,
            onToggleOrientationLock = onToggleOrientationLock,
            onOpenChapters = onOpenChapters,
            onOpenTracks = onOpenTracks,
            onOpenQuality = onOpenQuality,
            onOpenSettings = onOpenSettings,
        )
    } else {
        ExpandedPlayerToolbar(
            title = title,
            isOrientationLocked = isOrientationLocked,
            hasChapters = hasChapters,
            hasTracks = hasTracks,
            hasMultipleVersions = hasMultipleVersions,
            onBack = onBack,
            onToggleOrientationLock = onToggleOrientationLock,
            onOpenChapters = onOpenChapters,
            onOpenTracks = onOpenTracks,
            onOpenQuality = onOpenQuality,
            onOpenSettings = onOpenSettings,
        )
    }
}
```

`ExpandedPlayerToolbar` is a direct extraction of the current top `Row`. `CompactPlayerToolbar` renders the same Back `ControlButton`, the same weighted title `Text`, and the overflow block shown above. Both helpers use the callback and visibility parameters in the snippet, so no action reads state from outside its declared interface.

- [ ] **Step 4: Compile phone sources**

Run:

```bash
./gradlew :androidApp:assembleDebug
```

Expected: `BUILD SUCCESSFUL` with no Kotlin compilation errors.

- [ ] **Step 5: Re-run policy tests**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests '*ResponsiveControlLayoutTest'
```

Expected: four tests pass.

- [ ] **Step 6: Commit responsive Compose integration**

```bash
git add androidApp/src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/audiobook/AudiobookTransport.kt \
  androidApp/src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/player/PlayerControls.kt
git commit -m "fix(phone): keep enlarged controls reachable on narrow screens"
```

### Task 3: Fix phone touch targets and scrub-preview bounds

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/browse/CatalogGrid.kt`
- Modify: `androidApp/src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/player/PlayerProgressBar.kt`

**Interfaces:**
- Produces: a scrollable 48dp alphabet rail and a bounded one-line chapter preview.

- [ ] **Step 1: Enlarge the existing scrollable alphabet rail**

Change the rail width and row target from 40×24dp to 48×48dp, update the rail corner radius to 24dp and row radius to 24dp, and revise the comment to state that scrolling is intentional. Keep `LazyColumn`; do not replace it with a non-scrollable `Column`.

```kotlin
LazyColumn(
    modifier = modifier
        .width(48.dp)
        .clip(RoundedCornerShape(24.dp))
        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
        .padding(vertical = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    items(CatalogLetterOptions, key = { it ?: "all" }) { prefix ->
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                    },
                )
                .clickable { onNamePrefixSelected(prefix) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = prefix ?: "All",
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                lineHeight = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
```

Increase any catalog trailing inset tied to the rail from 40dp to 48dp so cards do not sit under the target column.

- [ ] **Step 2: Bound the scrub-preview chapter title**

Add `widthIn(max = 160.dp)` and ellipsis to the chapter title:

```kotlin
Text(
    text = title,
    modifier = Modifier.widthIn(max = 160.dp),
    fontSize = 12.sp,
    color = Color.White,
    textAlign = TextAlign.Center,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
)
```

- [ ] **Step 3: Compile and commit the phone accessibility slice**

Run:

```bash
./gradlew :androidApp:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

Commit:

```bash
git add androidApp/src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/browse/CatalogGrid.kt \
  androidApp/src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/player/PlayerProgressBar.kt
git commit -m "fix(phone): enforce accessible rail and preview bounds"
```

### Task 4: Strengthen the TV typography floor test-first

**Files:**
- Modify: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/theme/TvTypographyReadabilityTest.kt`

**Interfaces:**
- Produces: one `tinyFontPattern` used consistently by theme and source-tree checks.

- [ ] **Step 1: Add a failing fractional-value test**

Move the existing integer-only matcher to a class property and add the sample test. Keep the old matcher for the RED run:

```kotlin
private val tinyFontPattern = Regex(
    """fontSize\s*=\s*(9|10|11|12|13)\.sp""",
)

@Test
fun tinyFontPatternCatchesEveryLiteralBelowFourteenSp() {
    listOf("0.5.sp", "8.sp", "9.sp", "13.sp", "13.5.sp").forEach { value ->
        assertTrue(tinyFontPattern.containsMatchIn("fontSize = $value"), value)
    }
    listOf("14.sp", "14.5.sp", "16.sp").forEach { value ->
        assertFalse(tinyFontPattern.containsMatchIn("fontSize = $value"), value)
    }
}
```

Leave `sharedTvTypographyAvoidsTinyTenFootText()` using its current `source.contains` checks for the RED run so the new test demonstrates the matcher defect independently.

- [ ] **Step 2: Verify RED against the existing integer-only matcher**

First add only the new sample test while retaining the old pattern `Regex("""fontSize\s*=\s*(9|10|11|12|13)\.sp""")`.

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests '*TvTypographyReadabilityTest.tinyFontPatternCatchesEveryLiteralBelowFourteenSp'
```

Expected: FAIL for `0.5.sp` or `13.5.sp`.

- [ ] **Step 3: Replace the matcher and use it for both scans**

Set `tinyFontPattern` to the complete regex shown in Step 1. Replace the five theme `source.contains` assertions with:

```kotlin
assertFalse(
    tinyFontPattern.containsMatchIn(source),
    "Shared TV typography must keep every fontSize literal at or above 14sp",
)
```

Remove the function-local integer-only matcher from `tvScreensAvoidHardcodedTinyTextOutsideTheTheme()` so it uses the class property.

- [ ] **Step 4: Verify GREEN and commit**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests '*TvTypographyReadabilityTest'
```

Expected: all `TvTypographyReadabilityTest` tests pass.

Commit:

```bash
git add androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/theme/TvTypographyReadabilityTest.kt
git commit -m "test(tv): enforce fractional typography floor"
```

### Task 5: Apply the remaining TV accessibility modifiers

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/components/TvPinEntryDialog.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/admin/TvAdminHubScreen.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/admin/TvAdminScansScreen.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/admin/TvAdminSessionsScreen.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/admin/TvAdminUsersScreen.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvDetailEpisodeRail.kt`

**Interfaces:**
- Produces: a 48dp Cancel target, one safe-area gap per admin list, and a scalable episode synopsis.

- [ ] **Step 1: Enforce the Cancel target minimum**

Add `modifier = Modifier.heightIn(min = 48.dp)` to the clickable `Surface` that contains “Cancel”. Keep the existing text padding and surface focus styling.

- [ ] **Step 2: Remove the duplicate admin-list spacers**

Delete only these terminal list items, leaving each list's `contentPadding.bottom = 24.dp` intact:

```kotlin
item { Spacer(Modifier.height(24.dp)) }
```

Apply the deletion in all four listed admin files and remove now-unused `Spacer` or `height` imports only where no other call site remains.

- [ ] **Step 3: Let the synopsis expand**

Change:

```kotlin
.height(60.dp)
```

to:

```kotlin
.heightIn(min = 60.dp)
```

in `TvDetailEpisodeRail.kt`, preserving `maxLines = 3`, `overflow = TextOverflow.Ellipsis`, and `lineHeight = 20.sp`.

- [ ] **Step 4: Compile TV sources and run the typography tests**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests '*TvTypographyReadabilityTest'
./gradlew :androidTvApp:assembleDebug
```

Expected: both commands finish with `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the TV modifier slice**

```bash
git add androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/components/TvPinEntryDialog.kt \
  androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/admin/TvAdminHubScreen.kt \
  androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/admin/TvAdminScansScreen.kt \
  androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/admin/TvAdminSessionsScreen.kt \
  androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/admin/TvAdminUsersScreen.kt \
  androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvDetailEpisodeRail.kt
git commit -m "fix(tv): complete accessibility review follow-ups"
```

### Task 6: Verify PR #87 as a complete unit

**Files:**
- Verify only; no planned source changes.

**Interfaces:**
- Consumes: all prior tasks.
- Produces: a verified #87 head suitable for propagation.

- [ ] **Step 1: Run the full unit suite**

```bash
./gradlew test
```

Expected: all Gradle test tasks pass.

- [ ] **Step 2: Build phone and TV debug artifacts**

```bash
./gradlew :androidApp:assembleDebug :androidTvApp:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run repository hygiene checks**

```bash
git diff --check origin/main...HEAD
git status --short
```

Expected: no whitespace errors and no uncommitted changes.

- [ ] **Step 4: Review the final commit range**

```bash
git log --oneline origin/main..HEAD
git diff --stat origin/main...HEAD
```

Expected: the original #87 commits plus the design, policy, phone, TV, and test follow-up commits; no files outside the approved scope.

### Task 7: Push #87 and propagate through the stack

**Files:**
- Git history only.

**Interfaces:**
- Consumes: verified #87 head.
- Produces: updated remote heads for PRs #87–#93.

- [ ] **Step 1: Record exact old heads for force-with-lease safety**

```bash
git ls-remote git@github.com:RXWatcher/silo-android.git \
  refs/heads/pr/accessibility-foundations-v2 \
  refs/heads/pr/download-reachability-v2 \
  refs/heads/pr/tv-navigation-detail-v2 \
  refs/heads/pr/playback-reliability-v2 \
  refs/heads/pr/tv-track-selection-v2 \
  refs/heads/pr/chromecast-v2 \
  refs/heads/pr/subtitle-presentation-v2
```

Expected old #87–#93 heads are respectively `fda092f6`, `cefa8f99`, `dffc5307`, `2607e27d`, `ce51f03f`, `dcb9eb80`, and `d5bd3016` unless a collaborator has updated them. If any differ, stop propagation and inspect the new commits.

- [ ] **Step 2: Push #87 normally**

```bash
git push git@github.com:RXWatcher/silo-android.git \
  HEAD:refs/heads/pr/accessibility-foundations-v2
```

Expected: fast-forward update of PR #87.

- [ ] **Step 3: Rebase each descendant branch in order**

For each pair, check out the descendant branch and rebase its unique commits onto the newly rewritten parent:

```bash
git switch pr-88
git rebase --onto pr-87 fda092f6755df9a8885a94a3b903b71bd8e5dd9b pr-88

git switch pr-89
git rebase --onto pr-88 cefa8f9988f0d656d308bb728980b067950adee0 pr-89

git switch pr-90
git rebase --onto pr-89 dffc530718b6a0251899063dd9fc2033a31ae1e5 pr-90

git switch pr-91
git rebase --onto pr-90 2607e27d44b1c1c0ea4391afea21b14879e0f50e pr-91

git switch pr-92
git rebase --onto pr-91 ce51f03f5996c825237cba635d1e75bb11ca0a74 pr-92

git switch pr-93
git rebase --onto pr-92 dcb9eb80b03318521e6b55507a9ff8b3025a140d pr-93
```

Expected: each rebase completes without dropping its PR-specific commits. Resolve conflicts only when they touch the same approved UI code, preserving both the #87 fix and the later PR's intentional behavior.

- [ ] **Step 4: Verify the final stack head**

```bash
./gradlew test
./gradlew :androidApp:assembleDebug :androidTvApp:assembleDebug
git diff --check origin/main...pr-93
```

Expected: tests and both builds pass; no whitespace errors.

- [ ] **Step 5: Push descendants with exact force-with-lease protection**

Push each rewritten local branch to its matching remote branch using `--force-with-lease=<ref>:<recorded-old-oid>`. For example:

```bash
git push git@github.com:RXWatcher/silo-android.git \
  --force-with-lease=refs/heads/pr/download-reachability-v2:cefa8f9988f0d656d308bb728980b067950adee0 \
  pr-88:refs/heads/pr/download-reachability-v2
```

Push every remaining branch with its recorded lease:

```bash
git push git@github.com:RXWatcher/silo-android.git \
  --force-with-lease=refs/heads/pr/tv-navigation-detail-v2:dffc530718b6a0251899063dd9fc2033a31ae1e5 \
  pr-89:refs/heads/pr/tv-navigation-detail-v2
git push git@github.com:RXWatcher/silo-android.git \
  --force-with-lease=refs/heads/pr/playback-reliability-v2:2607e27d44b1c1c0ea4391afea21b14879e0f50e \
  pr-90:refs/heads/pr/playback-reliability-v2
git push git@github.com:RXWatcher/silo-android.git \
  --force-with-lease=refs/heads/pr/tv-track-selection-v2:ce51f03f5996c825237cba635d1e75bb11ca0a74 \
  pr-91:refs/heads/pr/tv-track-selection-v2
git push git@github.com:RXWatcher/silo-android.git \
  --force-with-lease=refs/heads/pr/chromecast-v2:dcb9eb80b03318521e6b55507a9ff8b3025a140d \
  pr-92:refs/heads/pr/chromecast-v2
git push git@github.com:RXWatcher/silo-android.git \
  --force-with-lease=refs/heads/pr/subtitle-presentation-v2:d5bd301616379b91ade6a355d293192e0435a6ee \
  pr-93:refs/heads/pr/subtitle-presentation-v2
```

Expected: all six remote branches update and no lease is rejected.

- [ ] **Step 6: Confirm PR checks and only then resolve review threads**

```bash
for n in 87 88 89 90 91 92 93; do
  gh pr checks "$n" --repo Silo-Server/silo-android
done
```

Expected: Unit tests succeed on every updated PR. Leave #93 as a draft. Resolve #87's eight review threads only after GitHub reflects the verified head; post no new top-level comment unless the user requests one.
