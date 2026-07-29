# Android Specials-First Season Order Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Display Specials first in Android phone and TV season selectors while ordinary series openings continue to select the first regular season.

**Architecture:** Put Specials detection, deterministic display sorting, and initial-season choice in shared catalog-model helpers. Both Android detail view models consume the same helpers, preventing phone/TV drift while leaving composables, routes, server responses, and playback sequencing unchanged.

**Tech Stack:** Kotlin 2.1, Kotlin Multiplatform common code, Android ViewModel/coroutines, Kotlin Test/JUnit, Gradle.

## Global Constraints

- Visible order is `Specials, Season 1, Season 2, …`.
- Keep the visible label **Specials**; never relabel it “Season 0.”
- Treat a season as Specials when `isSpecials == true` or `seasonNumber == 0`.
- Honor a requested/deep-linked season, including Specials.
- Without a requested season, select the first regular season; select Specials only when no regular season exists.
- Do not change the Silo server, web client, Apple clients, API schema, routes, or playback sequencing.

---

### Task 1: Shared Ordering and Initial-Selection Contract

**Files:**
- Create: `shared/src/commonTest/kotlin/org/siloserver/silo/model/catalog/SeasonDisplayOrderTest.kt`
- Modify: `shared/src/commonMain/kotlin/org/siloserver/silo/model/catalog/CatalogModels.kt:354-381`

**Interfaces:**
- Consumes: existing `Season(contentId, seasonNumber, isSpecials, title, …)`.
- Produces:
  - `fun Season.isSpecialsForDisplay(): Boolean`
  - `fun List<Season>.sortedForDisplay(): List<Season>`
  - `fun List<Season>.initialSeasonForDisplay(preferredSeasonNumber: Int?): Season?`

- [ ] **Step 1: Write failing shared ordering tests**

Create `SeasonDisplayOrderTest.kt` with a local factory and these cases:

```kotlin
package org.siloserver.silo.model.catalog

import kotlin.test.Test
import kotlin.test.assertEquals

class SeasonDisplayOrderTest {
    private fun season(
        number: Int,
        specials: Boolean = false,
        id: String = "season-$number-$specials",
    ) = Season(
        contentId = id,
        seasonNumber = number,
        isSpecials = specials,
    )

    @Test
    fun `specials sort before regular seasons`() {
        val result = listOf(season(2), season(0), season(1)).sortedForDisplay()
        assertEquals(listOf(0, 1, 2), result.map(Season::seasonNumber))
    }

    @Test
    fun `specials flag is authoritative even for nonzero season number`() {
        val result = listOf(season(1), season(99, specials = true), season(2)).sortedForDisplay()
        assertEquals(listOf(99, 1, 2), result.map(Season::seasonNumber))
    }

    @Test
    fun `ordinary opening selects first regular season`() {
        val result = listOf(season(0), season(2), season(1))
            .initialSeasonForDisplay(preferredSeasonNumber = null)
        assertEquals(1, result?.seasonNumber)
    }

    @Test
    fun `requested specials remains selected`() {
        val result = listOf(season(2), season(0), season(1))
            .initialSeasonForDisplay(preferredSeasonNumber = 0)
        assertEquals(0, result?.seasonNumber)
    }

    @Test
    fun `specials-only series selects specials`() {
        val result = listOf(season(0))
            .initialSeasonForDisplay(preferredSeasonNumber = null)
        assertEquals(0, result?.seasonNumber)
    }
}
```

- [ ] **Step 2: Run the shared test and verify RED**

Run:

```bash
./gradlew :shared:testDebugUnitTest \
  --tests org.siloserver.silo.model.catalog.SeasonDisplayOrderTest \
  --no-daemon
```

Expected: compilation fails because `initialSeasonForDisplay` and
`isSpecialsForDisplay` do not exist, or the Specials-first assertion fails
against the current Specials-last comparator.

- [ ] **Step 3: Implement the minimal shared helpers**

Replace the current comparator and add:

```kotlin
fun Season.isSpecialsForDisplay(): Boolean =
    isSpecials || seasonNumber == 0

fun List<Season>.sortedForDisplay(): List<Season> =
    sortedWith(
        compareByDescending<Season> { it.isSpecialsForDisplay() }
            .thenBy { it.seasonNumber }
            .thenBy { it.title.orEmpty() }
            .thenBy { it.contentId },
    )

fun List<Season>.initialSeasonForDisplay(preferredSeasonNumber: Int?): Season? {
    val ordered = sortedForDisplay()
    return preferredSeasonNumber
        ?.let { preferred -> ordered.firstOrNull { it.seasonNumber == preferred } }
        ?: ordered.firstOrNull { !it.isSpecialsForDisplay() }
        ?: ordered.firstOrNull()
}
```

- [ ] **Step 4: Run the shared test and verify GREEN**

Run the Step 2 command again. Expected: all five tests pass.

- [ ] **Step 5: Commit the shared contract**

```bash
git add \
  shared/src/commonMain/kotlin/org/siloserver/silo/model/catalog/CatalogModels.kt \
  shared/src/commonTest/kotlin/org/siloserver/silo/model/catalog/SeasonDisplayOrderTest.kt
git commit -m "fix(catalog): place specials first in season order"
```

---

### Task 2: Wire Phone and TV Initial Selection

**Files:**
- Create: `androidApp/src/androidUnitTest/kotlin/org/siloserver/silo/android/ui/screens/detail/SeasonInitialSelectionWiringSourceTest.kt`
- Create: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvSeasonInitialSelectionWiringSourceTest.kt`
- Modify: `androidApp/src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/detail/ItemDetailViewModel.kt:463-480`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvItemDetailViewModel.kt:652-673`

**Interfaces:**
- Consumes: `List<Season>.sortedForDisplay()` and
  `List<Season>.initialSeasonForDisplay(preferredSeasonNumber: Int?)` from
  Task 1.
- Produces: identical phone/TV automatic selection behavior with existing
  `selectedSeasonNumber` and `selectedSeason` state fields.

- [ ] **Step 1: Write failing wiring tests**

The phone test reads `ItemDetailViewModel.kt` and asserts:

```kotlin
assertTrue(
    source.contains(
        "val selectedSeason = seasons.initialSeasonForDisplay(initialSeasonNumber)",
    ),
)
```

The TV test reads `TvItemDetailViewModel.kt` and asserts:

```kotlin
assertTrue(
    source.contains(
        "val selectedSeason = seasons.initialSeasonForDisplay(preferredSeasonNumber)",
    ),
)
```

Each source-test file resolves its module-relative production file with
`File("src/androidMain/kotlin/…").readText()`.

- [ ] **Step 2: Run both wiring tests and verify RED**

Run:

```bash
./gradlew \
  :androidApp:testDebugUnitTest \
  :androidTvApp:testDebugUnitTest \
  --tests '*SeasonInitialSelectionWiringSourceTest' \
  --no-daemon
```

Expected: both tests fail because the view models still implement selection
inline.

- [ ] **Step 3: Wire the shared selection helper**

In the phone view model, import `initialSeasonForDisplay` and replace the
inline requested-or-first choice with:

```kotlin
val seasons = result.data.seasons.sortedForDisplay()
val selectedSeason = seasons.initialSeasonForDisplay(initialSeasonNumber)
```

In the TV view model, import `initialSeasonForDisplay` and replace
`selectedSeason`/`firstRegular` with:

```kotlin
val seasons = r.data.seasons.sortedForDisplay()
val selectedSeason = seasons.initialSeasonForDisplay(preferredSeasonNumber)
```

Use `selectedSeason` for `selectedSeason`, episode loading, and null fallback.
Do not change routing, state field types, or episode-loading behavior.

- [ ] **Step 4: Run both wiring tests and verify GREEN**

Run the Step 2 command again. Expected: both tests pass.

- [ ] **Step 5: Run focused and compile verification**

Run:

```bash
./gradlew \
  :shared:testDebugUnitTest \
  :androidApp:testDebugUnitTest \
  :androidTvApp:testDebugUnitTest \
  :androidApp:assembleDebug \
  :androidTvApp:assembleDebug \
  --no-daemon
```

Expected: `BUILD SUCCESSFUL` with no failed tests.

- [ ] **Step 6: Check the final diff and commit**

```bash
git diff --check
git status --short
git add \
  androidApp/src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/detail/ItemDetailViewModel.kt \
  androidApp/src/androidUnitTest/kotlin/org/siloserver/silo/android/ui/screens/detail/SeasonInitialSelectionWiringSourceTest.kt \
  androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvItemDetailViewModel.kt \
  androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvSeasonInitialSelectionWiringSourceTest.kt
git commit -m "fix(android): keep regular season selected by default"
```

Confirm the final branch contains only the design, plan, shared ordering,
phone/TV wiring, and focused tests.
