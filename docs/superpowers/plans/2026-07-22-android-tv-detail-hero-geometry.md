# Android TV Detail Hero Geometry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Android TV detail heroes use the fixed tvOS 980/1080 viewport proportion without collapsing action or selector controls.

**Architecture:** Replace the adaptive minimum-height hero with a clipped fixed frame. A focused `SubcomposeLayout` measures and reserves the action cluster before measuring the editorial column, while constrained logical viewports use tighter spacing and a two-line collapsed synopsis without reducing typography sizes.

**Tech Stack:** Kotlin 2.1, Jetpack Compose, Compose `SubcomposeLayout`, Android TV Material 3, Gradle/JUnit

## Global Constraints

- Keep `HERO_HEIGHT_FRACTION = 0.907f`.
- Keep all action and selector controls visible and focusable.
- Do not reduce current Android TV font-size floors.
- Do not change `TvDetailHeroBottomInset`, `TvDetailSectionGap`, audiobook detail geometry, root Skyline hero geometry, metadata ordering, or focus ordering.
- Do not add a committed UI test for this small UI change, per `AGENTS.md`; use red/green source assertions plus the existing test and build tasks.
- Do not install or launch the application on the Shield.

---

## File map

- Create `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvDetailHeroContentLayout.kt`: action-first measurement and placement boundary for the fixed hero.
- Modify `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvDetailHero.kt`: fixed/clipped frame, constrained-mode selection, and content-layout wiring.
- Modify `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvExpandableSynopsis.kt`: configurable collapsed line limit with the existing three-line behavior as the default.

### Task 1: Implement fixed, action-safe detail geometry

**Files:**
- Create: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvDetailHeroContentLayout.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvDetailHero.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvExpandableSynopsis.kt`

**Interfaces:**
- Consumes: `TvDetailHeroBottomInset`, `HERO_HEIGHT_FRACTION`, the existing editorial lambda, and the existing action/selector lambda.
- Produces: `TvDetailHeroContentLayout(modifier, editorial, actions)` and `TvExpandableSynopsis(..., collapsedMaxLines: Int = 3)`.

- [ ] **Step 1: Run the source assertions and verify the desired behavior is absent**

Run:

```bash
rg -q '\.height\(heroHeight\)' androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvDetailHero.kt
```

Expected: exit 1 because the hero still uses `.heightIn(min = heroHeight)`.

Run:

```bash
rg -q 'collapsedMaxLines: Int = 3' androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvExpandableSynopsis.kt
```

Expected: exit 1 because the synopsis line budget is not configurable.

- [ ] **Step 2: Add the action-first content layout**

Create `TvDetailHeroContentLayout.kt` with this implementation:

```kotlin
package org.siloserver.silo.tv.ui.screens.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.dp

private enum class DetailHeroContentSlot {
    Editorial,
    Actions,
}

@Composable
internal fun TvDetailHeroContentLayout(
    editorial: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editorialActionGap = 12.dp

    SubcomposeLayout(modifier = modifier) { constraints ->
        check(constraints.hasBoundedHeight) {
            "TvDetailHeroContentLayout requires a bounded hero height"
        }

        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        // Compose editorial first so semantics/focus traversal keep the visual
        // order, but defer its measurement until the action budget is known.
        val editorialMeasurable = subcompose(DetailHeroContentSlot.Editorial) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.BottomStart,
            ) {
                editorial()
            }
        }.single()
        val actionPlaceable = subcompose(DetailHeroContentSlot.Actions) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart,
            ) {
                actions()
            }
        }.single().measure(looseConstraints)

        val gapPx = editorialActionGap.roundToPx()
        val editorialMaxHeight = (
            constraints.maxHeight - actionPlaceable.height - gapPx
        ).coerceAtLeast(0)
        val editorialPlaceable = editorialMeasurable.measure(
            looseConstraints.copy(maxHeight = editorialMaxHeight),
        )

        layout(constraints.maxWidth, constraints.maxHeight) {
            val actionY = constraints.maxHeight - actionPlaceable.height
            val editorialY = (
                actionY - gapPx - editorialPlaceable.height
            ).coerceAtLeast(0)
            editorialPlaceable.placeRelative(0, editorialY)
            actionPlaceable.placeRelative(0, actionY)
        }
    }
}
```

- [ ] **Step 3: Make the synopsis collapsed line limit configurable**

Update the signature and collapsed `maxLines` expression in `TvExpandableSynopsis.kt`:

```kotlin
@Composable
internal fun TvExpandableSynopsis(
    overview: String,
    tagline: String?,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 3,
) {
    require(collapsedMaxLines > 0) { "collapsedMaxLines must be positive" }
```

```kotlin
maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
```

Keep expanded synopsis behavior and every other style/focus property unchanged.

- [ ] **Step 4: Replace the adaptive hero with the fixed content budget**

In `TvDetailHero.kt`, replace the outer `.heightIn(min = heroHeight)` with:

```kotlin
.height(heroHeight)
.clipToBounds()
```

Define constrained presentation from the fixed logical hero height:

```kotlin
val usesConstrainedEditorial = heroHeight < DetailHeroComfortableHeight
val editorialSpacing = if (usesConstrainedEditorial) 8.dp else 12.dp
val collapsedSynopsisLines = if (usesConstrainedEditorial) 2 else 3
```

Replace the existing bottom-anchored `Column` with:

```kotlin
TvDetailHeroContentLayout(
    modifier = Modifier
        .fillMaxSize()
        .padding(
            start = Spacing.safeArea,
            end = Spacing.safeArea,
            bottom = TvDetailHeroBottomInset,
        ),
    editorial = {
        EditorialColumn(
            title = title,
            seriesTitle = seriesTitle,
            logoUrl = logoUrl,
            sourceTokens = sourceTokens,
            ratingChip = ratingChip,
            overview = overview,
            tagline = tagline,
            factsLine = factsLine,
            contentMaxWidth = contentMaxWidth,
            verticalSpacing = editorialSpacing,
            collapsedSynopsisLines = collapsedSynopsisLines,
            translation = translation,
        )
    },
    actions = {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .focusGroup(),
            contentAlignment = Alignment.CenterStart,
        ) {
            actions()
        }
    },
)
```

Extend `EditorialColumn` with:

```kotlin
verticalSpacing: androidx.compose.ui.unit.Dp,
collapsedSynopsisLines: Int,
```

Use those values in its `Column` and synopsis call:

```kotlin
verticalArrangement = Arrangement.spacedBy(verticalSpacing),
```

```kotlin
TvExpandableSynopsis(
    overview = line,
    tagline = tagline,
    collapsedMaxLines = collapsedSynopsisLines,
)
```

Add the comfortable-height token beside `HERO_HEIGHT_FRACTION`:

```kotlin
private val DetailHeroComfortableHeight = 500.dp
```

Remove the unused `heightIn` import and add imports for `clipToBounds` and
`fillMaxSize`. Do not change any title, metadata, facts, action, or focus styles.

- [ ] **Step 5: Re-run the red/green assertions**

Run:

```bash
rg -q '\.height\(heroHeight\)' androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvDetailHero.kt
rg -q 'collapsedMaxLines: Int = 3' androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvExpandableSynopsis.kt
! rg -q '\.heightIn\(min = heroHeight\)' androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvDetailHero.kt
```

Expected: all three commands exit 0.

- [ ] **Step 6: Verify normal and constrained logical-height selection**

Run:

```bash
awk 'BEGIN { shieldHero = 540 * 0.907; largeHero = 720 * 0.907; if (!(shieldHero < 500 && largeHero >= 500)) exit 1; printf "constrained=%.2fdp normal=%.2fdp\n", shieldHero, largeHero }'
```

Expected:

```text
constrained=489.78dp normal=653.04dp
```

This confirms the Shield-class 540dp canvas receives the compact editorial
budget while a taller logical canvas retains the normal 12dp/three-line layout.

- [ ] **Step 7: Compile and run Android TV tests**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest :androidTvApp:assembleDebug
```

Expected: `BUILD SUCCESSFUL`; existing deprecation warnings are acceptable, but
there must be no compilation or test failures.

- [ ] **Step 8: Inspect and commit the implementation**

Run:

```bash
git diff --check
git diff -- androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvDetailHeroContentLayout.kt androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvDetailHero.kt androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvExpandableSynopsis.kt
```

Expected: no whitespace errors; the diff contains only the fixed frame,
action-first layout, constrained spacing, and configurable collapsed synopsis.

Commit:

```bash
git add androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvDetailHeroContentLayout.kt androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvDetailHero.kt androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvExpandableSynopsis.kt
git commit -m "fix(tv): lock detail hero to viewport geometry"
```

### Task 2: Verify and update the stacked PRs

**Files:**
- Verify: `docs/superpowers/specs/2026-07-22-android-tv-detail-hero-geometry-design.md`
- Verify: `docs/superpowers/plans/2026-07-22-android-tv-detail-hero-geometry.md`
- Verify: implementation files from Task 1

**Interfaces:**
- Consumes: the verified PR #89 implementation commit.
- Produces: rebased local branches `pr-90` through `pr-93` and lease-protected remote PR heads.

- [ ] **Step 1: Run fresh final verification at the PR #89 tip**

Run:

```bash
git status --short --branch
git diff --check main...pr-89
./gradlew :androidTvApp:testDebugUnitTest :androidTvApp:assembleDebug
```

Expected: clean `pr-89`, no whitespace errors, and `BUILD SUCCESSFUL`.

- [ ] **Step 2: Restack downstream branches**

Run these commands in order, stopping on any conflict:

```bash
git rebase --onto pr-89 dd3ad247d150daa3593a57fbad83f285ae941c82 pr-90
git rebase --onto pr-90 ed32c553252c28df718cc10c6b00126fef7d8abf pr-91
git rebase --onto pr-91 0c3690158b2820ac1a921991d7beef6f896f28e8 pr-92
git rebase --onto pr-92 848faf5bde759abc2583a5ac224bb801d448cbe4 pr-93
```

Expected: each rebase completes without conflict and `pr-93` contains the new
PR #89 commits in its ancestry.

- [ ] **Step 3: Verify the top of the rebuilt stack**

Run:

```bash
git switch pr-93
git status --short --branch
git merge-base --is-ancestor pr-89 pr-93
./gradlew :androidTvApp:testDebugUnitTest :androidTvApp:assembleDebug
```

Expected: clean `pr-93`, ancestor check exit 0, and `BUILD SUCCESSFUL`.

- [ ] **Step 4: Confirm remote heads and push with exact leases**

Run:

```bash
git ls-remote git@github.com:RXWatcher/silo-android.git refs/heads/pr/tv-navigation-detail-v2 refs/heads/pr/playback-reliability-v2 refs/heads/pr/tv-track-selection-v2 refs/heads/pr/chromecast-v2 refs/heads/pr/subtitle-presentation-v2
```

Expected remote tips before pushing:

```text
dd3ad247d150daa3593a57fbad83f285ae941c82  refs/heads/pr/tv-navigation-detail-v2
ed32c553252c28df718cc10c6b00126fef7d8abf  refs/heads/pr/playback-reliability-v2
0c3690158b2820ac1a921991d7beef6f896f28e8  refs/heads/pr/tv-track-selection-v2
848faf5bde759abc2583a5ac224bb801d448cbe4  refs/heads/pr/chromecast-v2
9d0cb3ab923d950ee16263f1af7632d153f22bb6  refs/heads/pr/subtitle-presentation-v2
```

Push PR #89 normally and downstream PRs with exact lease protection:

```bash
git push git@github.com:RXWatcher/silo-android.git \
  --force-with-lease=refs/heads/pr/tv-navigation-detail-v2:dd3ad247d150daa3593a57fbad83f285ae941c82 \
  --force-with-lease=refs/heads/pr/playback-reliability-v2:ed32c553252c28df718cc10c6b00126fef7d8abf \
  --force-with-lease=refs/heads/pr/tv-track-selection-v2:0c3690158b2820ac1a921991d7beef6f896f28e8 \
  --force-with-lease=refs/heads/pr/chromecast-v2:848faf5bde759abc2583a5ac224bb801d448cbe4 \
  --force-with-lease=refs/heads/pr/subtitle-presentation-v2:9d0cb3ab923d950ee16263f1af7632d153f22bb6 \
  pr-89:refs/heads/pr/tv-navigation-detail-v2 \
  pr-90:refs/heads/pr/playback-reliability-v2 \
  pr-91:refs/heads/pr/tv-track-selection-v2 \
  pr-92:refs/heads/pr/chromecast-v2 \
  pr-93:refs/heads/pr/subtitle-presentation-v2
```

Expected: PR #89 advances and PRs #90 through #93 report forced updates; any
lease mismatch stops the task for remote-review inspection instead of overwrite.

- [ ] **Step 5: Confirm the published tips**

Run:

```bash
git ls-remote git@github.com:RXWatcher/silo-android.git refs/heads/pr/tv-navigation-detail-v2 refs/heads/pr/playback-reliability-v2 refs/heads/pr/tv-track-selection-v2 refs/heads/pr/chromecast-v2 refs/heads/pr/subtitle-presentation-v2
```

Expected: each remote SHA equals the corresponding local `pr-89` through
`pr-93` SHA. Do not install or launch the app on the Shield.
