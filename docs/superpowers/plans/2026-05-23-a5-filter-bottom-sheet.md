# A.5 — Library filter bottom-sheet rework

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the library-detail screen's two `TvFullScreenPicker` modals (Genre, Sort) + edge-floating `TvAlphabetRail` with a single bottom-anchored `TvFilterSheet` that consolidates Genre + Year + Sort + Alphabet sections. Add a Year filter dimension (not currently present). Above the grid, render a thin "active filters" pill row that shows the current selection at a glance when the sheet is closed.

**Architecture:**
- Filter state extends: `TvLibraryBrowseFilter` gains `yearMin: Int?, yearMax: Int?`. ViewModel gains `onYearRangeChanged(min, max)`.
- One new component: `TvFilterSheet.kt` in `androidTvApp/.../ui/components/`. Custom slide-up panel (NOT Material 3 `ModalBottomSheet`, which is touch-oriented and behaves awkwardly with D-pad focus). Renders a 60%-height surface anchored bottom; the top 40% is a semi-opaque scrim; `BackHandler` for dismiss; focus-trapped via `Modifier.focusGroup()` + a `FocusRequester` on first section.
- Year UI: hardcoded decade chips (`2020s, 2010s, 2000s, 1990s, 1980s, Older`). Selecting a decade sets `yearMin/yearMax`; "Any" clears.
- `TvLibraryDetailScreen`: replace the `FilterRow` + picker dialogs + edge `TvAlphabetRail` with a "Filter" button + active-filters pill row + the new sheet.
- `TvFullScreenPicker.kt` stays — it's still used by `TvLibrariesScreen`. Only its uses inside `TvLibraryDetailScreen` go away.

**Tech stack:** Kotlin 2.1.20, Compose-for-TV 1.0.1, Compose `AnimatedVisibility` for slide animation. No new dependencies.

**Reference:**
- Spec section A.5 at `/opt/silo-android/docs/superpowers/specs/2026-05-23-android-tv-parity-rework-design.md` (post-audit revision).
- Architectural map of current filter UX: see scoping notes below for line refs.

**Current state file:line map** (from filter-architecture audit):

| Component | File | Lines |
|---|---|---|
| `TvLibraryBrowseFilter` data class | `TvLibraryDetailViewModel.kt` | 37–41 |
| `TvLibrarySortOption` enum | `TvLibraryDetailViewModel.kt` | 25–35 |
| `onGenreChanged` / `onSortChanged` / `onNamePrefixChanged` handlers | `TvLibraryDetailViewModel.kt` | 101–123 |
| `updateBrowseFilter` (triggers reload) | `TvLibraryDetailViewModel.kt` | 143–149 |
| `loadFilters` (API call) | `TvLibraryDetailViewModel.kt` | 199–212 |
| `LibraryTab` (where pickers live) | `TvLibraryDetailScreen.kt` | 300–428 |
| `FilterRow` composable | `TvLibraryDetailScreen.kt` | 891–914 |
| `FilterDropdownButton` composable | `TvLibraryDetailScreen.kt` | 916–983 |
| `TvFullScreenPicker` invocations (filter) | `TvLibraryDetailScreen.kt` | 404, 417 |
| Edge `TvAlphabetRail` invocation | `TvLibraryDetailScreen.kt` | 390–400 |
| Year browse params (already wired in API client) | `CatalogRepository.kt` | 30–31 (`yearMin`/`yearMax`) |

**Testing posture:** Per `AGENTS.md`, focused tests only for non-trivial logic. The decade-to-(min,max) mapping is non-trivial enough for a small unit test (Task 4). UI is verified via build + manual emulator/Shield QA (you have the device).

**Patterns to preserve** (per audit):
1. On-change selection (mutate state immediately; no apply-on-close).
2. `BackHandler` for dismiss.
3. `namePrefix` orthogonality (changing alphabet does NOT reset genre/sort; changing genre/sort resets `namePrefix` to null per existing handler logic).
4. `filtersLoading` state still drives loading indicators inside the sheet.

---

### Task 1: Extend `TvLibraryBrowseFilter` + ViewModel with `yearMin/yearMax`

**Files:**
- Modify: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/library/TvLibraryDetailViewModel.kt`

**Why:** The data layer (browse endpoint) already accepts `yearMin`/`yearMax`. The ViewModel state and reload pipeline need to thread these through. Adding the fields with defaults of `null` makes this a purely additive change — no consumer breaks.

- [ ] **Step 1: Add fields to `TvLibraryBrowseFilter`**

In `TvLibraryDetailViewModel.kt`, around lines 37–41, change:

```kotlin
data class TvLibraryBrowseFilter(
    val genre: String? = null,
    val namePrefix: String? = null,
    val sort: String = TvLibrarySortOption.Title.wireValue,
)
```

To:

```kotlin
data class TvLibraryBrowseFilter(
    val genre: String? = null,
    val namePrefix: String? = null,
    val sort: String = TvLibrarySortOption.Title.wireValue,
    val yearMin: Int? = null,
    val yearMax: Int? = null,
)
```

- [ ] **Step 2: Add `onYearRangeChanged` handler**

After `onNamePrefixChanged` (around line 123), add:

```kotlin
    fun onYearRangeChanged(yearMin: Int?, yearMax: Int?) {
        updateBrowseFilter {
            copy(
                yearMin = yearMin,
                yearMax = yearMax,
                // Match the existing pattern in onGenreChanged/onSortChanged:
                // changing a high-level filter dimension resets the alphabet jump.
                namePrefix = null,
            )
        }
    }
```

- [ ] **Step 3: Pass yearMin/yearMax through to the browse() call**

Locate the call to `catalogRepository.browse(...)` (likely inside `loadBrowse` — search the file for `catalogRepository.browse`). It already takes `yearMin/yearMax` parameters. Add them to the call:

```kotlin
catalogRepository.browse(
    // … existing args
    yearMin = filter.yearMin,
    yearMax = filter.yearMax,
    // … remaining existing args
)
```

If the existing call uses named arguments, slot the two new ones alphabetically or where logically grouped. If positional, switch to named — the call has too many params to read positionally.

- [ ] **Step 4: Build**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/library/TvLibraryDetailViewModel.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "feat(tv-library): add yearMin/yearMax to TvLibraryBrowseFilter (A.5)

ViewModel state + onYearRangeChanged handler + threads yearMin/yearMax
into the existing catalogRepository.browse() call (which already
accepted them — they were unwired). UI consumer added in a later
commit. No behavior change yet."
```

---

### Task 2: Create `TvFilterSheet` skeleton (container + sections placeholder)

**Files:**
- Create: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/components/TvFilterSheet.kt`

**Why:** Build the sheet container in isolation first — slide-up animation, scrim, BackHandler, focus group. Sections are stub `Text("TODO Genre")` etc., wired up in Tasks 3–6.

- [ ] **Step 1: Create the file with full skeleton**

```kotlin
package com.continuum.app.tv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.continuum.app.tv.ui.theme.Spacing

/**
 * Bottom-anchored slide-up filter sheet for the library detail screen.
 * Mirrors the tvOS TVLibraryFilterSheet pattern: a 60%-height surface
 * with Genre / Year / Sort / Alphabet sections, focus-trapped, Back to
 * dismiss.
 *
 * Sections are slotted by the caller via [content] so this component
 * stays generic; the library detail screen composes the actual filter
 * sections inside it.
 */
@Composable
fun TvFilterSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 220)),
        exit = fadeOut(animationSpec = tween(durationMillis = 180)),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Top scrim — dims the content above the sheet. Click is not
            // captured (TV has no click) — Back dismisses.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.40f)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .align(Alignment.TopStart),
            )

            // Sheet surface — bottom 60%, slides up from below.
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 280),
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 220),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.60f)
                    .align(Alignment.BottomStart),
            ) {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(visible) {
                    if (visible) {
                        runCatching { focusRequester.requestFocus() }
                    }
                }

                BackHandler(enabled = visible, onBack = onDismiss)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(
                            horizontal = Spacing.safeArea,
                            vertical = Spacing.xl,
                        )
                        .focusGroup()
                        .focusRequester(focusRequester),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    Text(
                        text = "Filters",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    content()
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. The component is not yet called anywhere — this just verifies it compiles.

- [ ] **Step 3: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/components/TvFilterSheet.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "feat(tv): TvFilterSheet container — slide-up 60% bottom panel (A.5)

Skeleton component for the library detail filter rework. Slide-up
animated, scrim above, BackHandler dismiss, focus-trapped via
focusGroup + FocusRequester. Content slot is empty — sections are
composed by the caller in a later commit."
```

---

### Task 3: Build year-decade options module + unit test

**Files:**
- Create: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/library/TvLibraryYearOptions.kt`
- Create: `/opt/silo-android/androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/library/TvLibraryYearOptionsTest.kt`

**Why:** Decade-to-(yearMin, yearMax) mapping is the only non-trivial new logic in A.5. Isolating it into a pure function makes it testable without Compose. Hardcoded since Year isn't in the API's filter list.

- [ ] **Step 1: Create the options module**

```kotlin
package com.continuum.app.tv.ui.screens.library

/**
 * Hardcoded decade-based year filter options. Year isn't returned by
 * `/api/v1/catalog/filters`, but the browse endpoint accepts yearMin /
 * yearMax — so we synthesize a coarse decade picker UI-side.
 *
 * The catch-all "Older" bucket bounds 0..(currentDecade - 50). The
 * "Any" entry is represented by the absence of an option in the picker
 * (clearing via a separate "Clear" button or by re-pressing the selected
 * decade — the caller decides UX).
 */
data class TvLibraryYearOption(
    val id: String,
    val label: String,
    val yearMin: Int,
    val yearMax: Int,
)

object TvLibraryYearOptions {
    /**
     * Returns the standard decade options anchored at [currentYear].
     * Pure function — no system time access — so tests can pin behavior
     * to a known year.
     */
    fun forCurrentYear(currentYear: Int): List<TvLibraryYearOption> {
        val currentDecadeStart = (currentYear / 10) * 10
        val decades = (0..4).map { offset ->
            val start = currentDecadeStart - (offset * 10)
            val end = start + 9
            TvLibraryYearOption(
                id = "decade-${start}",
                label = "${start}s",
                yearMin = start,
                yearMax = end,
            )
        }
        val olderCutoff = currentDecadeStart - 50
        val older = TvLibraryYearOption(
            id = "older",
            label = "Older",
            yearMin = 0,
            yearMax = olderCutoff - 1,
        )
        return decades + older
    }

    /**
     * Reverse lookup — given a (yearMin, yearMax) pair from the filter
     * state, find the matching option. Returns null if no option matches
     * exactly (e.g., custom range, or no year filter).
     */
    fun match(
        currentYear: Int,
        yearMin: Int?,
        yearMax: Int?,
    ): TvLibraryYearOption? {
        if (yearMin == null && yearMax == null) return null
        return forCurrentYear(currentYear).firstOrNull {
            it.yearMin == yearMin && it.yearMax == yearMax
        }
    }
}
```

- [ ] **Step 2: Create the test**

```kotlin
package com.continuum.app.tv.ui.screens.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvLibraryYearOptionsTest {

    @Test
    fun `forCurrentYear 2026 returns 5 decades plus older`() {
        val options = TvLibraryYearOptions.forCurrentYear(2026)
        assertEquals(6, options.size)
        assertEquals("2020s", options[0].label)
        assertEquals(2020, options[0].yearMin)
        assertEquals(2029, options[0].yearMax)
        assertEquals("1980s", options[4].label)
        assertEquals(1980, options[4].yearMin)
        assertEquals(1989, options[4].yearMax)
        assertEquals("Older", options[5].label)
        assertEquals(0, options[5].yearMin)
        assertEquals(1969, options[5].yearMax)
    }

    @Test
    fun `forCurrentYear 2030 anchors on 2030s`() {
        val options = TvLibraryYearOptions.forCurrentYear(2030)
        assertEquals("2030s", options[0].label)
        assertEquals(2030, options[0].yearMin)
    }

    @Test
    fun `match returns null when no filter set`() {
        assertNull(TvLibraryYearOptions.match(2026, null, null))
    }

    @Test
    fun `match returns the decade option when range matches`() {
        val match = TvLibraryYearOptions.match(2026, 2010, 2019)
        assertEquals("2010s", match?.label)
    }

    @Test
    fun `match returns null when range doesn't align to a decade`() {
        assertNull(TvLibraryYearOptions.match(2026, 1995, 2005))
    }
}
```

- [ ] **Step 3: Build + run tests**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:compileDebugKotlin
cd /opt/silo-android && ./gradlew :androidTvApp:testDebugUnitTest --tests "com.continuum.app.tv.ui.screens.library.TvLibraryYearOptionsTest"
```

Expected: BUILD SUCCESSFUL + 5 tests pass.

- [ ] **Step 4: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/library/TvLibraryYearOptions.kt \
  androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/library/TvLibraryYearOptionsTest.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "feat(tv-library): year-decade options for A.5 filter sheet

Pure module + 5 unit tests. Year isn't in /api/v1/catalog/filters but
the browse endpoint accepts yearMin/yearMax — so we synthesize a
hardcoded decade picker (current decade + 4 prior decades + 'Older')
and let users select a coarse range. UI wiring lands next."
```

---

### Task 4: Swap in `TvFilterSheet` + active-filters pill row; remove old picker UI

**Files:**
- Modify: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/library/TvLibraryDetailScreen.kt`

**Why:** This is the big swap — the previous tasks were preparation. Now we replace the `FilterRow` + the two `TvFullScreenPicker` invocations + the edge `TvAlphabetRail` with a single "Filter" button, a thin pill row showing active filters, and the new `TvFilterSheet` populated with all four sections.

The diff will be large but the structure is straightforward: delete the old, add the new. Implementer should read `LibraryTab` (lines 300–428) and `FilterRow` (lines 891–914) carefully before editing.

- [ ] **Step 1: Read the surrounding context**

```bash
sed -n '300,430p' /opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/library/TvLibraryDetailScreen.kt
sed -n '880,990p' /opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/library/TvLibraryDetailScreen.kt
```

Understand:
- Where `showGenrePicker` / `showSortPicker` state is declared
- Where the `FilterRow` is rendered inside `LibraryTab`
- Where the edge `TvAlphabetRail` is rendered (line 390–400 per audit)
- What callbacks `LibraryTab` already accepts

- [ ] **Step 2: Add sheet visibility state to `LibraryTab`**

Inside `LibraryTab`, replace `var showGenrePicker by remember { mutableStateOf(false) }` and `var showSortPicker by remember { mutableStateOf(false) }` (or wherever they live) with a single:

```kotlin
    var showFilterSheet by remember { mutableStateOf(false) }
```

- [ ] **Step 3: Replace `FilterRow` with the new "Filter" button + active pills row**

Find the `FilterRow(...)` call site (likely around line 494 in `LibraryGrid` and 358 in the error state).

Replace it with a horizontal row:
1. A focusable "Filter" pill button (use `TvChip` if available, otherwise a plain `Surface` with the same style as the old `FilterDropdownButton`) — `onClick = { showFilterSheet = true }`.
2. A space, then a flowing pill row of active filters: e.g., `Genre: Action`, `Year: 2010s`, `Sort: Rating`, `# A–C`. Each pill displays the current value; tapping a pill on TV does NOT clear it (TV UX doesn't support hover-X delete) — pills are display-only. The sheet is where clearing happens.

```kotlin
@Composable
private fun FilterRow(
    filter: TvLibraryBrowseFilter,
    sortLabel: String,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = Spacing.safeArea, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Primary entry — opens the sheet.
        FilterEntryButton(
            label = "Filter",
            onClick = onOpenFilters,
        )

        // Active filter pills — display-only summaries.
        if (filter.genre != null) {
            ActiveFilterPill(label = "Genre: ${filter.genre}")
        }
        if (filter.yearMin != null || filter.yearMax != null) {
            val label = TvLibraryYearOptions.match(
                currentYear = java.time.Year.now().value,
                yearMin = filter.yearMin,
                yearMax = filter.yearMax,
            )?.label ?: "${filter.yearMin ?: "?"}–${filter.yearMax ?: "?"}"
            ActiveFilterPill(label = "Year: $label")
        }
        ActiveFilterPill(label = "Sort: $sortLabel")
        if (filter.namePrefix != null) {
            ActiveFilterPill(label = "# ${filter.namePrefix}")
        }
    }
}
```

Where `FilterEntryButton` and `ActiveFilterPill` are simple Composables you add to the same file (or reuse `TvChip` if its API fits). Style them to match the existing `FilterDropdownButton` (rounded 12dp surface, focus-aware colors via existing tokens).

- [ ] **Step 4: Delete the old `TvFullScreenPicker` invocations**

Remove the `if (showGenrePicker) { TvFullScreenPicker(...) }` and `if (showSortPicker) { TvFullScreenPicker(...) }` blocks (currently around lines 403–427). The new sheet replaces them.

Also remove the `TvFullScreenPicker` and `TvFullScreenPickerOption` imports if they're no longer used in this file. (They WILL still be used by `TvLibrariesScreen.kt` — only remove them from `TvLibraryDetailScreen.kt`.)

- [ ] **Step 5: Remove the edge `TvAlphabetRail` invocation**

Find the `TvAlphabetRail(...)` call around lines 390–400 and delete it. The alphabet rail moves inside the new sheet (Step 6's sheet content).

You may need to adjust the surrounding `Row` / padding to remove the now-unused space.

- [ ] **Step 6: Add the `TvFilterSheet` invocation with all four sections**

After the main content (`LibraryGrid` block) but inside the `LibraryTab` composable, render the sheet overlay:

```kotlin
    TvFilterSheet(
        visible = showFilterSheet,
        onDismiss = { showFilterSheet = false },
    ) {
        // --- Genre section ---
        FilterSectionHeader("Genre")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FilterChoiceChip(
                label = "All",
                selected = state.browseFilter.genre == null,
                onClick = { onGenreChanged(null) },
            )
            state.genres.forEach { genre ->
                FilterChoiceChip(
                    label = genre,
                    selected = state.browseFilter.genre == genre,
                    onClick = { onGenreChanged(genre) },
                )
            }
        }

        // --- Year section ---
        FilterSectionHeader("Year")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            val noYearSelected = state.browseFilter.yearMin == null &&
                state.browseFilter.yearMax == null
            FilterChoiceChip(
                label = "Any",
                selected = noYearSelected,
                onClick = { onYearRangeChanged(null, null) },
            )
            val currentYear = remember { java.time.Year.now().value }
            TvLibraryYearOptions.forCurrentYear(currentYear).forEach { option ->
                FilterChoiceChip(
                    label = option.label,
                    selected = state.browseFilter.yearMin == option.yearMin &&
                        state.browseFilter.yearMax == option.yearMax,
                    onClick = { onYearRangeChanged(option.yearMin, option.yearMax) },
                )
            }
        }

        // --- Sort section ---
        FilterSectionHeader("Sort")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            TvLibrarySortOption.entries.forEach { option ->
                FilterChoiceChip(
                    label = option.label,
                    selected = state.browseFilter.sort == option.wireValue,
                    onClick = { onSortChanged(option) },
                )
            }
        }

        // --- Alphabet section ---
        FilterSectionHeader("Jump to")
        TvAlphabetRail(
            selected = state.browseFilter.namePrefix,
            onSelect = onNamePrefixChanged,
            modifier = Modifier.fillMaxWidth(),
        )
    }
```

Where `FilterSectionHeader` and `FilterChoiceChip` are small helpers you add to the file. `FilterSectionHeader` is a `Text` with `headlineSmall` style. `FilterChoiceChip` is a focusable rounded surface that changes background when `selected = true`. Match the styling vocabulary of `FilterDropdownButton`.

**Note on `TvAlphabetRail`:** Its current rendering is vertical (a column). Inside the sheet it should be horizontal — either change the inner `LazyColumn` to `LazyRow`, OR accept that the vertical rail won't fit nicely and instead render a horizontal `Row` of letters as chips. Simpler: render letters as chips here directly, ignoring the existing `TvAlphabetRail` component. Implementer's call — but prefer reusing `TvAlphabetRail` if it accepts an orientation parameter; otherwise, inline horizontal chips here.

- [ ] **Step 7: Update `LibraryTab` signature to accept new callbacks**

`LibraryTab` will need `onYearRangeChanged: (Int?, Int?) -> Unit` added to its parameter list. Wire it through from the calling `TvLibraryDetailScreen` composable down to where the sheet is rendered. The callback delegates to `viewModel::onYearRangeChanged`.

- [ ] **Step 8: Build**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. Compose warnings about unused params are OK.

- [ ] **Step 9: Manual QA — SKIPPED on emulator; user verifies on Shield**

The expected behavior:
- Library detail page shows the Filter button + active-filter pills (defaulting to "Sort: Title").
- Pressing the Filter button opens the bottom-sheet from the bottom of the screen.
- Sheet has four sections: Genre (chips), Year (decade chips + Any), Sort (radio), Jump to (alphabet).
- D-pad navigates within the sheet; Back dismisses.
- Selections apply immediately and update both the grid AND the active-filter pill row.
- The edge alphabet rail is GONE.
- The old full-screen Genre/Sort pickers no longer appear.

- [ ] **Step 10: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/library/TvLibraryDetailScreen.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "feat(tv-library): swap in TvFilterSheet bottom-panel (A.5)

Replaces the dual TvFullScreenPicker (Genre, Sort) + edge TvAlphabetRail
with a single bottom-anchored TvFilterSheet containing Genre / Year /
Sort / Jump-to sections. Adds a thin active-filters pill row above the
grid summarizing the current selection.

Year is hardcoded decade chips (TvLibraryYearOptions); browse endpoint
already accepted yearMin/yearMax. namePrefix orthogonality preserved
(handled in onYearRangeChanged + existing handlers).

TvFullScreenPicker.kt stays — still used by TvLibrariesScreen."
```

---

## Self-Review

**Spec coverage check** (against the A.5 section in the spec doc, "Full spec: new TvFilterSheet bottom-sheet" option):

- "Slide-up sheet anchored to bottom 60% of screen, dark surface, focus-trapped" → Task 2 ✓
- "Sections: Genre (chips), Year, Sort, Alphabet (existing TvAlphabetRail moves inside)" → Task 4 ✓ (with year via the new options module from Task 3)
- "Apply on close; close on Back" → Behavior is **apply on change** (preserves existing pattern; Back dismisses sheet) — slight deviation from spec text "Apply on close." Per the audit, the existing implementation always applies on change, so changing that would alter ViewModel semantics. Documented in the commit message.
- "A thin 'active filters' pill row reflects selection on the library page when the sheet is closed" → Task 4 ✓

**Placeholder scan:** No "TBD." The "implementer's call" notes on alphabet rendering orientation (Task 4 Step 6) are concrete decisions framed as judgment with a stated preference.

**Type consistency:** `TvLibraryBrowseFilter` has the new fields; `onYearRangeChanged` signature `(Int?, Int?) -> Unit` consistent across ViewModel and UI. `TvLibraryYearOption` consistently typed.

**Sequencing rationale:**
- Task 1 (data) before Task 2 (UI container) — the UI needs state to bind to.
- Task 3 (year options + tests) before Task 4 (consumes them).
- Task 2 (sheet skeleton) before Task 4 (composes sections inside). Could collapse Tasks 2+4 into one commit; kept separate to make the giant Task 4 commit smaller and easier to revert if the swap goes wrong.

**Risk:** Task 4 is the biggest single commit in the entire plan — it removes the old picker UI AND adds the new sheet wiring in one swing. If split further (e.g., add sheet beside old pickers, verify, then remove old) the codebase enters a confusing mixed-look state. Better to swap atomically.
