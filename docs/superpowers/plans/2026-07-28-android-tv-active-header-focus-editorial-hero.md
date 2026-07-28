# Android TV Active Header Focus and Editorial Hero Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make content-to-header navigation land on the active TV section and replace browsing-hero stream badges with ordered editorial metadata.

**Architecture:** Keep `TvMainShell` as the route-aware focus coordinator, pass its active root explicitly through `TvShellFocusState`, and let `TvTopMenuBar` apply the existing requester after one composition frame. Keep hero transformation inside `TvMarqueeContent.from`, using only existing `SectionItem` and enrichment data and leaving player/detail surfaces unchanged.

**Tech Stack:** Kotlin 2.1, Jetpack Compose for TV, Compose focus APIs, Kotlin coroutines, Kotlin test/JUnit, Gradle.

## Global Constraints

- Android TV only; no Android phone behavior changes.
- No server, API, database, payload, schema, or production-configuration changes.
- No player-overlay, playback-settings, item-detail, stream-selection, transcoding, or subtitle changes.
- Preserve the held-Up boundary: repeated Up stops on the first content row and a fresh Up enters the menu.
- Search receives content-to-menu focus only while Search is the active route.
- Technical resolution, HDR, and audio data remains available to other consumers but is not rendered in browsing heroes.
- Preserve synopsis, cast/air-date enrichment, artwork, cache-first loading, and crossfade behavior.
- Missing or invalid metadata is omitted without placeholders or dangling separators.
- Do not merge or deploy; update open PR #126 only after all required verification is green.

---

## File Map

- `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvMainShell.kt`
  derives the active root and supplies one explicit focus target to both Up and
  Back paths.
- `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvShellFocusState.kt`
  carries the requested menu target without performing Compose focus itself.
- `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvTopMenuFocusRequest.kt`
  provides the small frame-ordered focus application seam.
- `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvTopMenuBar.kt`
  resolves the active target to an existing `FocusRequester` and applies it
  after composition.
- `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/components/TvFocusMarqueeModel.kt`
  builds browsing-hero editorial metadata from `SectionItem`.
- `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/shell/TvTopMenuFocusRequestTest.kt`
  proves frame ordering and request-result propagation.
- `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/shell/TvShellFocusStateTest.kt`
  proves Up/Back retain the requested active root.
- `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/components/TvFocusMarqueeModelTest.kt`
  proves movie/episode metadata ordering and technical-badge removal.

---

### Task 1: Make active-section header focus deterministic

**Files:**
- Create: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvTopMenuFocusRequest.kt`
- Create: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/shell/TvTopMenuFocusRequestTest.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvMainShell.kt:466-470,719-735,802-815`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvShellFocusState.kt:174-184,285-309`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvTopMenuBar.kt:225-242`
- Modify: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/shell/TvShellFocusStateTest.kt:90-125,245-280`

**Interfaces:**
- Consumes: `TvTopMenuPanel.Root(TvRootDestination)`, `TvShellFocusState.requestMenuFocus(TvTopMenuPanel?)`, and each existing top-menu `FocusRequester`.
- Produces: `internal suspend fun requestTopMenuFocusUntilApplied(awaitFrame: suspend () -> Unit, requestFocus: () -> Boolean)`.
- Produces: `TvShellFocusState.onBack(onTabRoot: Boolean, menuFocusTarget: TvTopMenuPanel? = null): TvShellBackAction`.
- Produces: one `selectedMenuFocusTarget: TvTopMenuPanel?` in `TvMainShell`, reused by content Up and Back.

- [ ] **Step 1: Write the frame-ordering regression test**

Create `TvTopMenuFocusRequestTest.kt`:

```kotlin
package org.siloserver.silo.tv.ui.shell

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TvTopMenuFocusRequestTest {
    @Test
    fun focusIsRequestedOnlyAfterTheTargetHasHadAFrameToCompose() = runTest {
        val events = mutableListOf<String>()

        requestTopMenuFocusUntilApplied(
            awaitFrame = { events += "frame" },
            requestFocus = { events += "focus"; true },
        )

        assertEquals(listOf("frame", "focus"), events)
    }

    @Test
    fun aTargetThatIsNotAttachedYetIsRetriedOnTheNextFrame() = runTest {
        val events = mutableListOf<String>()
        var attempts = 0

        requestTopMenuFocusUntilApplied(
            awaitFrame = { events += "frame" },
            requestFocus = {
                events += "focus"
                attempts += 1
                attempts == 2
            },
        )

        assertEquals(listOf("frame", "focus", "frame", "focus"), events)
    }
}
```

- [ ] **Step 2: Add the active-root Back regression**

Append to `TvShellFocusStateTest`:

```kotlin
@Test
fun backFromRootContentRetainsTheActiveRootAsItsMenuTarget() {
    val state = TvShellFocusState()

    assertEquals(
        TvShellBackAction.MoveFocusToMenu,
        state.onBack(
            onTabRoot = true,
            menuFocusTarget = moviesPanel,
        ),
    )

    assertEquals(moviesPanel, state.menuFocusTarget)
}
```

- [ ] **Step 3: Run the focused tests and verify RED**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest \
  --tests "org.siloserver.silo.tv.ui.shell.TvTopMenuFocusRequestTest" \
  --tests "org.siloserver.silo.tv.ui.shell.TvShellFocusStateTest" \
  --rerun-tasks --max-workers=2 --no-daemon
```

Expected: compilation fails because `requestTopMenuFocusUntilApplied` and the
`menuFocusTarget` argument do not exist.

- [ ] **Step 4: Implement the frame-ordered focus seam**

Create `TvTopMenuFocusRequest.kt`:

```kotlin
package org.siloserver.silo.tv.ui.shell

internal suspend fun requestTopMenuFocusUntilApplied(
    awaitFrame: suspend () -> Unit,
    requestFocus: () -> Boolean,
) {
    do {
        awaitFrame()
    } while (!requestFocus())
}
```

- [ ] **Step 5: Carry the active target through the state holder**

Change `TvShellFocusState.onBack` to:

```kotlin
fun onBack(
    onTabRoot: Boolean,
    menuFocusTarget: TvTopMenuPanel? = null,
): TvShellBackAction {
    val action = tvShellBackAction(
        panelOpen = openPanel != null,
        profileMenuOpen = profileMenuOpen,
        menuFocused = isMenuFocused,
        onTabRoot = onTabRoot,
    )
    when (action) {
        TvShellBackAction.ClosePanel -> closePanel(returnFocusToBar = true)
        TvShellBackAction.CloseProfileMenu -> dismissProfileMenu()
        TvShellBackAction.MoveFocusToMenu -> requestMenuFocus(menuFocusTarget)
        TvShellBackAction.MenuBack,
        TvShellBackAction.DelegateToNav -> Unit
    }
    return action
}
```

Do not alter the pure `tvShellBackAction` precedence.

- [ ] **Step 6: Derive one route-aware target and reuse it**

Immediately after `selectedRoot` in `TvMainShell`, add:

```kotlin
val selectedMenuFocusTarget = selectedRoot?.let(TvTopMenuPanel::Root)
```

Pass it to Back:

```kotlin
focusState.onBack(
    onTabRoot = selectedRoot != null,
    menuFocusTarget = selectedMenuFocusTarget,
)
```

Use it at both first-row Up handoffs:

```kotlin
focusState.requestMenuFocus(selectedMenuFocusTarget)
```

Leave `selectedMenuFocusTarget` null on Search. `TvTopMenuBar` must continue
using `isSearchActive` to select Search for that route; other secondary routes
must not silently select Home.

- [ ] **Step 7: Apply the requester after composition and only acknowledge success**

In `TvTopMenuBar`, replace the immediate focus call inside the
`LaunchedEffect(focusRequest, isFocusSuppressed)` with:

```kotlin
requestTopMenuFocusUntilApplied(
    awaitFrame = { androidx.compose.runtime.withFrameNanos { } },
    requestFocus = {
        runCatching { requester.requestFocus() }.getOrDefault(false)
    },
)
lastHandledFocusRequest = focusRequest
```

Move the existing `lastHandledFocusRequest = focusRequest` assignment out of
the pre-request path. The frame loop suspends rather than spins and is cancelled
automatically if the `LaunchedEffect` keys change. Keep the explicit-target
resolution and `dwellSuppressedButton` behavior unchanged.

- [ ] **Step 8: Run focused tests and verify GREEN**

Run the command from Step 3.

Expected: both test classes pass with zero failures.

- [ ] **Step 9: Run the existing Up-navigation regression**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest \
  --tests "org.siloserver.silo.tv.ui.components.TvSkylineUpNavigationTest" \
  --rerun-tasks --max-workers=2 --no-daemon
```

Expected: all four held-Up/row-relocation tests pass unchanged.

- [ ] **Step 10: Commit the focus correction**

```bash
git add \
  androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvMainShell.kt \
  androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvShellFocusState.kt \
  androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvTopMenuBar.kt \
  androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvTopMenuFocusRequest.kt \
  androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/shell/TvShellFocusStateTest.kt \
  androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/shell/TvTopMenuFocusRequestTest.kt
git commit -m "fix(tv): restore active header focus from content"
```

---

### Task 2: Replace stream badges with editorial hero metadata

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/components/TvFocusMarqueeModel.kt:75-165`
- Modify: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/components/TvFocusMarqueeModelTest.kt:1-32`

**Interfaces:**
- Consumes: `SectionItem.year`, `durationSeconds`, `ratingImdb`, `genres`, `contentRating`, `seriesTitle`, `seasonNumber`, `episodeNumber`, and existing `TvMarqueeEnrichment.detailLine`.
- Produces: `TvMarqueeContent.badges` containing only an optional uppercase content classification.
- Produces: ordered `TvMarqueeContent.metaParts`: movie `year → runtime → IMDb → genre`; episode `Sx Ey → episode title → runtime → IMDb`.

- [ ] **Step 1: Replace quality-badge tests with movie editorial-metadata RED**

Replace `TvFocusMarqueeModelTest` with:

```kotlin
package org.siloserver.silo.tv.ui.components

import org.siloserver.silo.model.catalog.OverlaySummary
import org.siloserver.silo.model.section.SectionItem
import kotlin.test.Test
import kotlin.test.assertEquals

class TvFocusMarqueeModelTest {
    @Test
    fun movieHeroPrioritizesEditorialMetadataAndOmitsStreamQuality() {
        val content = TvMarqueeContent.from(
            item = SectionItem(
                contentId = "movie-1",
                type = "movie",
                title = "Arrival",
                year = 2016,
                genres = listOf("Science Fiction"),
                ratingImdb = 7.9,
                contentRating = "PG-13",
                durationSeconds = 6_960.0,
                overlaySummary = OverlaySummary(
                    resolution = "2160p",
                    hdr = "Dolby Vision",
                    audio = "TrueHD Atmos",
                ),
            ),
            rowTitle = "Popular",
        )

        assertEquals(listOf("PG-13"), content.badges)
        assertEquals(
            listOf("2016", "1h 56m", "7.9", "Science Fiction"),
            content.metaParts,
        )
    }

    @Test
    fun episodeHeroUsesSeriesTitleAndEditorialEpisodeMetadata() {
        val content = TvMarqueeContent.from(
            item = SectionItem(
                contentId = "episode-1",
                type = "episode",
                title = "Long, Long Time",
                seriesTitle = "The Last of Us",
                seasonNumber = 1,
                episodeNumber = 3,
                ratingImdb = 8.6,
                contentRating = "TV-MA",
                durationSeconds = 4_560.0,
                overlaySummary = OverlaySummary(
                    resolution = "1080p",
                    audio = "EAC3",
                ),
            ),
            rowTitle = "Continue Watching",
        )

        assertEquals("The Last of Us", content.title)
        assertEquals(listOf("TV-MA"), content.badges)
        assertEquals(
            listOf("S1 E3", "Long, Long Time", "1h 16m", "8.6"),
            content.metaParts,
        )
    }

    @Test
    fun missingEditorialMetadataProducesNoEmptyTokensOrBadges() {
        val content = TvMarqueeContent.from(
            item = SectionItem(
                contentId = "movie-2",
                type = "movie",
                title = "Untitled",
                overlaySummary = OverlaySummary(
                    resolution = "2160p",
                    hdr = "HDR10",
                    audio = "Atmos",
                ),
            ),
            rowTitle = "Recently Added",
        )

        assertEquals(emptyList(), content.badges)
        assertEquals(emptyList(), content.metaParts)
    }
}
```

- [ ] **Step 2: Run the model test and verify RED**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest \
  --tests "org.siloserver.silo.tv.ui.components.TvFocusMarqueeModelTest" \
  --rerun-tasks --max-workers=2 --no-daemon
```

Expected: the movie order differs, the episode lacks its rating, and technical
quality badges are still present.

- [ ] **Step 3: Implement the approved metadata ordering**

In `TvMarqueeContent.from`, build metadata exactly as follows:

```kotlin
val meta = mutableListOf<String>()
if (isEpisode) {
    episodeToken(item.seasonNumber, item.episodeNumber)?.let(meta::add)
    if (item.title.isNotBlank()) meta.add(item.title)
    lengthText(item.durationSeconds)?.let(meta::add)
    item.ratingImdb?.let { meta.add(formatRating(it)) }
} else {
    if (item.year > 0) meta.add(item.year.toString())
    lengthText(item.durationSeconds)?.let(meta::add)
    item.ratingImdb?.let { meta.add(formatRating(it)) }
    item.genres.firstOrNull { it.isNotBlank() }?.let(meta::add)
}

val badges = item.contentRating
    ?.takeIf { it.isNotBlank() }
    ?.uppercase(Locale.US)
    ?.let(::listOf)
    .orEmpty()
```

Delete `qualityBadges`, `dynamicRangeBadge`, `audioBadge`, and
`prettyResolution`; they have no remaining callers. Do not remove
`SectionItem.overlaySummary` or change shared models.

- [ ] **Step 4: Preserve enrichment and rendering contracts**

Verify by inspection that `TvMarqueeEnrichment.from` still emits its existing
air-date/cast `detailLine`, `TvMarqueeContent.withEnrichment` still preserves
the content identity, and `TvFocusMarquee` still omits the badge/meta row when
both lists are empty. Make no changes to those paths.

- [ ] **Step 5: Run the focused model test and verify GREEN**

Run the command from Step 2.

Expected: all three tests pass with zero failures.

- [ ] **Step 6: Commit the editorial hero correction**

```bash
git add \
  androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/components/TvFocusMarqueeModel.kt \
  androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/components/TvFocusMarqueeModelTest.kt
git commit -m "fix(tv): show editorial metadata in browse heroes"
```

---

### Task 3: Verify, review, package, and update PR #126

**Files:**
- Verify only: all branch changes against `origin/main`
- Output only, not committed: Android TV universal minified release APK
- Update remotely after green: existing PR #126 description/checklist

**Interfaces:**
- Consumes: Tasks 1 and 2 commits.
- Produces: independently reviewed, fully verified PR #126 head and a clearly named tester APK.

- [ ] **Step 1: Run all focused regressions together**

```bash
./gradlew :androidTvApp:testDebugUnitTest \
  --tests "org.siloserver.silo.tv.ui.shell.TvTopMenuFocusRequestTest" \
  --tests "org.siloserver.silo.tv.ui.shell.TvShellFocusStateTest" \
  --tests "org.siloserver.silo.tv.ui.components.TvSkylineUpNavigationTest" \
  --tests "org.siloserver.silo.tv.ui.components.TvFocusMarqueeModelTest" \
  --rerun-tasks --max-workers=2 --no-daemon
```

Expected: all selected tests pass with zero failures.

- [ ] **Step 2: Run supply-chain policy checks**

```bash
./scripts/test-check-build-supply-chain.sh
./scripts/check-build-supply-chain.sh
```

Expected: both scripts exit 0.

- [ ] **Step 3: Run the complete fresh TV test and release gate**

```bash
./gradlew \
  :androidTvApp:testDebugUnitTest \
  :androidTvApp:assembleRelease \
  -PallowDebugReleaseSigning=true \
  --rerun-tasks --max-workers=2 --no-daemon
```

Expected: `BUILD SUCCESSFUL`; all TV unit XML results have zero failures and
the minified release APK is produced.

- [ ] **Step 4: Perform the required device/emulator smoke**

On a dedicated Android TV emulator or tester device, without touching an
unapproved physical device:

1. Open Home, press Down into content, move to the first row, then press a
   fresh Up; verify Home receives focus.
2. Repeat for one library section, For You, and Calendar; verify the active
   pill receives focus each time.
3. Hold Up from a lower row; verify focus stops on the first content row.
4. Open Search and verify its route still owns Search focus.
5. Focus one movie and one episode; verify no resolution/HDR/audio badges are
   shown and the approved editorial fields appear in order.

Record the emulator/device identity and pass/fail result in the PR. If no
approved target is available, mark this smoke as pending instead of claiming
it passed.

- [ ] **Step 5: Request independent focused review**

Provide the reviewer:

- the approved spec;
- `git diff origin/main...HEAD`;
- focused and full test results;
- the focus request timing/target contract;
- movie and episode metadata order;
- explicit instruction to flag production-semantic changes outside the TV
  shell/marquee scope.

Address only verified findings, rerun the affected focused test, and repeat
review until approved.

- [ ] **Step 6: Verify diff hygiene and branch state**

```bash
git diff --check origin/main...HEAD
git status --short --branch
git log --oneline origin/main..HEAD
```

Expected: no whitespace errors, no uncommitted files, and only the documented
TV navigation/hero commits plus their specs/plans.

- [ ] **Step 7: Copy and verify the tester APK**

Select the universal APK from the TV release output, verify it with
`apksigner verify --verbose`, inspect package/version/ABI metadata with
`apkanalyzer`, calculate `shasum -a 256`, and copy it without overwriting prior
artifacts:

```bash
cp androidTvApp/build/outputs/apk/release/androidTvApp-universal-release.apk \
  "/Users/jimcole/Desktop/Silo Releases/Silo-TV-Universal-0.3.11-TVFocusHeroFix-$(git rev-parse --short HEAD).apk"
```

If the generated filename differs, select the universal artifact explicitly;
never substitute an ABI-specific split. Report that
`-PallowDebugReleaseSigning=true` produces a debug-signed release build that
only upgrades installations signed by the same certificate.

- [ ] **Step 8: Push and update PR #126**

```bash
git push origin fix/tv-for-you-cold-navigation
gh pr view 126 --repo Silo-Server/silo-android \
  --json state,isDraft,baseRefName,headRefName,mergeable,statusCheckRollup
```

Update the PR description to include:

- active-section focus restoration;
- editorial-only movie/episode hero metadata;
- focused/full verification evidence;
- independent review verdict;
- device-smoke result or its explicit pending status;
- tester APK signing caveat.

Do not merge PR #126.
