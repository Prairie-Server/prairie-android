# A.2 finish — Palette + AmbientBackdropTint propagation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining 60% of sub-project A.2. The existing `TvRootHeroBackdrop.kt` already renders a blurred backdrop with a vertical gradient. This plan adds: (1) the `androidx.palette:palette` dependency, (2) an `AmbientBackdropTintState` + `LocalAmbientBackdropTint` `CompositionLocal` so Home content can publish "the focused hero's accent color" and other components can read it, (3) hooking the carousel's already-existing `onActiveItemChanged` callback into the tint state, (4) a Palette extraction coroutine that loads the backdrop bitmap via Coil and extracts a vibrant accent, and (5) applying the accent as a multiply tint layer on the backdrop.

**Architecture:**
- One new dep (`androidx.palette:palette:1.0.0`).
- One new file (`AmbientBackdropTint.kt` in `androidTvApp/.../ui/components/`) — pure state holder + CompositionLocal.
- Modifications to two existing files (`TvRootHeroBackdrop.kt`, `TvHomeScreen.kt`).
- Carousel (`TvHomeHeroCarousel.kt`) needs **zero** changes — it already exposes `onActiveItemChanged: (SectionItem) -> Unit`; we just start passing it from `TvHomeScreen`.

**Tech stack:** Kotlin 2.1.20, Compose-for-TV 1.0.1, Coil 3.1.0 (already in deps), new `androidx.palette:palette:1.0.0`.

**Reference:** Spec section A.2 at `/opt/silo-android/docs/superpowers/specs/2026-05-23-android-tv-parity-rework-design.md`. Audit confirmed the carousel's `onActiveItemChanged` callback already exists with the right signature (`(SectionItem) -> Unit`).

**Testing posture:** Per `AGENTS.md` ("no tests for small UI changes"), only the pure Palette extraction logic warrants a unit test (Task 3 has it). UI verification is manual on an Android TV emulator post-merge.

**Branch base:** continue on `feature/tv-a1-polish-a7-tokens` from current HEAD (`bdc8878`). Alternative: create a new feature branch — implementer's call, but staying on the current branch is fine since A.1 + A.2 are siblings under sub-project A.

---

### Task 1: Add `androidx.palette:palette` dependency

**Files:**
- Modify: `/opt/silo-android/gradle/libs.versions.toml`
- Modify: `/opt/silo-android/androidTvApp/build.gradle.kts`

**Why:** Palette is the Android API for extracting representative colors from a bitmap. Used by the tint state in Task 2 and the backdrop overlay in Task 3.

- [ ] **Step 1: Add the version + library alias to `libs.versions.toml`**

In the `[versions]` block, after `security-crypto = "1.1.0-alpha06"`, add:

```toml
palette = "1.0.0"
```

In the `[libraries]` block, after the `androidx-security-crypto` line, add:

```toml
androidx-palette = { module = "androidx.palette:palette-ktx", version.ref = "palette" }
```

Note: `palette-ktx` is the Kotlin-friendly artifact (provides extension functions like `bitmap.generatePalette()`). It pulls in `palette` as a transitive.

- [ ] **Step 2: Add the dep to `androidTvApp/build.gradle.kts`**

Inside `androidMain.dependencies { … }` (around line 18–49), after `implementation(libs.tv.material)`, add:

```kotlin
            // Palette — bitmap accent-color extraction for the ambient hero backdrop (A.2).
            implementation(libs.androidx.palette)
```

- [ ] **Step 3: Build to verify dep resolves**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. If gradle can't resolve `androidx.palette:palette-ktx:1.0.0`, double-check the version (Maven Central authoritative) and the module name (`palette-ktx`, not `palette`).

- [ ] **Step 4: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android add \
  gradle/libs.versions.toml \
  androidTvApp/build.gradle.kts

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "build(tv): add androidx.palette dep for A.2 backdrop tint extraction

Pulls in androidx.palette:palette-ktx 1.0.0 for the upcoming ambient
backdrop tint propagation (A.2). No call sites yet — wired up in the
next two commits."
```

---

### Task 2: Create `AmbientBackdropTint.kt` (state + CompositionLocal + extraction coroutine)

**Files:**
- Create: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/components/AmbientBackdropTint.kt`

**Why:** A single source of truth for "what's the current accent color." Other components (the backdrop in Task 3; A.6's card glow later) read from `LocalAmbientBackdropTint.current.accent`. The state holder owns: (a) the current active item, (b) the extracted accent color (nullable while loading or if extraction fails), (c) the coroutine that fetches the bitmap and runs Palette.

- [ ] **Step 1: Create the file with state class + CompositionLocal + helper composable**

```kotlin
package com.continuum.app.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmapOrNull
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.continuum.app.model.section.SectionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared "ambient backdrop accent" published by the focused hero on Home and
 * consumed by [TvRootHeroBackdrop] (and eventually A.6's card glow). Mirrors
 * the tvOS TVRootHeroBackdrop tint behavior — a single accent extracted from
 * the active hero's backdrop image, tinted at low alpha across the page.
 *
 * Default is [Empty] (no item, no accent) — components reading [accent]
 * should render an untinted variant in that case.
 */
@Stable
class AmbientBackdropTintState internal constructor(
    initialAccent: Color? = null,
) {
    private val _accent = mutableStateOf(initialAccent)
    val accent: Color? get() = _accent.value

    private val _currentItem = mutableStateOf<SectionItem?>(null)
    val currentItem: SectionItem? get() = _currentItem.value

    internal val pendingItem: MutableState<SectionItem?> = mutableStateOf(null)

    fun set(item: SectionItem?) {
        _currentItem.value = item
        pendingItem.value = item
    }

    internal fun acceptAccent(item: SectionItem?, accent: Color?) {
        // Guard against stale extraction completing after the user has scrolled to a different item.
        if (_currentItem.value?.contentId == item?.contentId) {
            _accent.value = accent
        }
    }

    companion object {
        val Empty: AmbientBackdropTintState = AmbientBackdropTintState()
    }
}

/**
 * Composition-scope publisher for the ambient backdrop tint. Default value is
 * [AmbientBackdropTintState.Empty] so consumers don't crash outside Home.
 */
val LocalAmbientBackdropTint = compositionLocalOf { AmbientBackdropTintState.Empty }

/**
 * Creates a remembered [AmbientBackdropTintState] that re-extracts the
 * accent color whenever the published item changes. Use exactly once per
 * page that wants to publish a tint — typically wrap the page content in
 * a `CompositionLocalProvider(LocalAmbientBackdropTint provides …) { … }`.
 */
@Composable
fun rememberAmbientBackdropTintState(): AmbientBackdropTintState {
    val context = LocalContext.current
    val state = remember { AmbientBackdropTintState() }

    LaunchedEffect(state.pendingItem.value?.contentId) {
        val item = state.pendingItem.value
        if (item == null) {
            state.acceptAccent(null, null)
            return@LaunchedEffect
        }
        val url = item.backdropUrl ?: item.posterUrl
        if (url.isNullOrBlank()) {
            state.acceptAccent(item, null)
            return@LaunchedEffect
        }

        val accent: Color? = withContext(Dispatchers.IO) {
            runCatching {
                val loader: ImageLoader = SingletonImageLoader.get(context)
                val result = loader.execute(
                    ImageRequest.Builder(context)
                        .data(url)
                        .allowHardware(false) // Palette requires a software bitmap.
                        .build(),
                )
                val bitmap = (result as? SuccessResult)?.image?.toBitmap()
                    ?: return@runCatching null
                val palette = Palette.from(bitmap).generate()
                val swatch = palette.vibrantSwatch
                    ?: palette.mutedSwatch
                    ?: palette.dominantSwatch
                swatch?.rgb?.let(::Color)
            }.getOrNull()
        }
        state.acceptAccent(item, accent)
    }
    return state
}
```

A few non-obvious design points worth noting in case the implementer wants to revisit:

- The `pendingItem` / `acceptAccent` indirection guards against a stale Palette result landing after the user has navigated to a different hero. We compare `contentId` at delivery time and drop if mismatched.
- `allowHardware(false)` is required — Palette's `BitmapDrawable` decoding doesn't work on hardware-backed bitmaps.
- We prefer `vibrantSwatch` → `mutedSwatch` → `dominantSwatch` in that order. Vibrant gives the most accent-like color; muted falls back when the image is desaturated; dominant is the last-resort tonal average.
- `SingletonImageLoader.get(context)` reads Coil's app-level loader, which the rest of the app already uses — same cache, no double-fetch.

- [ ] **Step 2: Build**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. If `coil3.toBitmap` is unresolved, the import may need to be `coil3.toBitmap` (extension on `coil3.Image`) or `coil3.asDrawable(context.resources).toBitmapOrNull()` — check Coil 3.1 API docs; the extension lives in `io.coil-kt.coil3:coil-core`.

If `androidx.palette.graphics.Palette` is unresolved, double-check Task 1 actually added the dep correctly (`palette-ktx`).

- [ ] **Step 3: Add a unit test for the stale-result guard**

`AGENTS.md` allows tests for non-trivial logic. The `acceptAccent` staleness guard is non-trivial — it's the kind of thing that breaks subtly during rapid hero advances. Add a focused test.

**File:** `/opt/silo-android/androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/components/AmbientBackdropTintStateTest.kt`

```kotlin
package com.continuum.app.tv.ui.components

import androidx.compose.ui.graphics.Color
import com.continuum.app.model.section.SectionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmbientBackdropTintStateTest {

    private fun item(id: String) = SectionItem(
        contentId = id,
        title = "t",
        year = null,
        type = "movie",
        posterUrl = null,
        posterThumbhash = null,
        backdropUrl = null,
        backdropThumbhash = null,
        progressUpdatedAt = null,
        progress = null,
    )

    @Test
    fun `accent updates when result matches current item`() {
        val state = AmbientBackdropTintState()
        val a = item("a")
        state.set(a)
        state.acceptAccent(a, Color.Red)
        assertEquals(Color.Red, state.accent)
        assertEquals(a, state.currentItem)
    }

    @Test
    fun `accent ignored when stale result arrives after item changed`() {
        val state = AmbientBackdropTintState()
        val a = item("a")
        val b = item("b")
        state.set(a)
        state.set(b)
        // Stale extraction for `a` arrives after we already moved to `b`.
        state.acceptAccent(a, Color.Red)
        assertNull(state.accent)
        assertEquals(b, state.currentItem)
    }

    @Test
    fun `setting null item clears current and pending`() {
        val state = AmbientBackdropTintState()
        state.set(item("a"))
        state.set(null)
        assertNull(state.currentItem)
    }
}
```

Note: the `SectionItem` constructor signature may differ from what's shown above — the test should match the actual `SectionItem` `data class` in `shared/src/commonMain/.../model/section/SectionItem.kt`. Read that file first to use the correct parameter names; the test body shape stays the same.

- [ ] **Step 4: Run the test**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:testDebugUnitTest --tests "com.continuum.app.tv.ui.components.AmbientBackdropTintStateTest"
```

Expected: 3 tests passing.

- [ ] **Step 5: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/components/AmbientBackdropTint.kt \
  androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/components/AmbientBackdropTintStateTest.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "feat(tv): AmbientBackdropTintState + LocalAmbientBackdropTint (A.2)

State holder + CompositionLocal for publishing the current hero's
extracted accent color. Loads bitmap via Coil's singleton ImageLoader,
runs androidx.palette to pick a vibrant/muted/dominant swatch, guards
against stale results racing with rapid hero advances.

Not wired in yet — TvRootHeroBackdrop and TvHomeScreen consume it in
the next two commits."
```

---

### Task 3: Apply the tint multiply layer in `TvRootHeroBackdrop`

**Files:**
- Modify: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/components/TvRootHeroBackdrop.kt`

**Why:** The backdrop currently renders image → 220dp top gradient → flat 0.34α scrim → vertical fade. Add a fourth layer: a low-alpha multiply of the accent color from `LocalAmbientBackdropTint`. When the accent is null (off-Home or extraction in flight) the layer collapses to fully transparent.

- [ ] **Step 1: Modify `TvRootHeroBackdrop` to read from the CompositionLocal and apply the multiply**

Add this import:

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.tween
```

Inside the `Box(modifier = Modifier.fillMaxSize()) { … }` (the one starting around line 50), after the existing four overlays (image, top gradient, flat scrim, vertical fade) and before the closing brace, add a fifth overlay:

```kotlin
                val tintState = LocalAmbientBackdropTint.current
                val targetAccent = tintState.accent ?: Color.Transparent
                val animatedAccent by animateColorAsState(
                    targetValue = targetAccent,
                    animationSpec = tween(durationMillis = 600),
                    label = "tvRootHeroBackdropAccent",
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(animatedAccent.copy(alpha = 0.18f)),
                )
```

The `0.18f` alpha is the multiply strength — a deliberate ceiling that keeps the tint perceptible without overwhelming the underlying art. Adjust during manual QA if needed; record any change in the commit message.

- [ ] **Step 2: Build**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/components/TvRootHeroBackdrop.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "feat(tv): tint TvRootHeroBackdrop with LocalAmbientBackdropTint accent

Adds a fifth overlay layer that multiplies the current accent color
(from LocalAmbientBackdropTint) at 0.18 alpha across the backdrop.
Color crossfades over 600ms when the hero changes. No-op when the
tint state is empty (off-Home or extraction pending)."
```

---

### Task 4: Wire `TvHomeScreen` to provide the tint state and feed the carousel callback

**Files:**
- Modify: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/home/TvHomeScreen.kt`

**Why:** This is the final wiring step that makes the tint actually update. Today `TvHomeScreen` passes the *first* featured item to the backdrop forever (line 92: `val featuredItem = featuredSection?.items?.firstOrNull()`). The carousel's `onActiveItemChanged` callback is unused. We:
1. Provide `LocalAmbientBackdropTint` via `CompositionLocalProvider`.
2. Hold the active item in state at this composable's level.
3. Pass `onActiveItemChanged = { activeItem = it; tintState.set(it) }` into the carousel.
4. Pass the live active item to the backdrop instead of the static first item.

- [ ] **Step 1: Add imports and the tint-state wiring to `TvHomeContent`**

Add these imports near the top of the file:

```kotlin
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.continuum.app.tv.ui.components.LocalAmbientBackdropTint
import com.continuum.app.tv.ui.components.rememberAmbientBackdropTintState
```

(Some of these — `mutableStateOf`, `setValue`, `remember` — are already imported. Don't duplicate.)

Modify `TvHomeContent` (around line 81). Where it currently computes `val featuredItem = featuredSection?.items?.firstOrNull()` (line 92), replace with active-item tracking:

```kotlin
    val (featuredSection, restSections) = sections.splitFeatured().let { it.featured to it.rest }
    val rows = restSections.filter { it.items.isNotEmpty() }

    val tintState = rememberAmbientBackdropTintState()
    var activeHeroItem by remember(featuredSection?.id) {
        mutableStateOf(featuredSection?.items?.firstOrNull())
    }
    // Seed the tint state with the initial featured item on (re)entry.
    LaunchedEffect(activeHeroItem?.contentId) {
        tintState.set(activeHeroItem)
    }
```

(The `LaunchedEffect` above runs once when the featured item is first known, then again any time the carousel advances. The previous `LaunchedEffect`s in this function for focus management stay untouched.)

- [ ] **Step 2: Wrap the existing `Box` content in `CompositionLocalProvider` and use the live item**

Locate the `Box(modifier = Modifier.fillMaxSize().background(...))` around line 120 — that's the outer container. Wrap its body in a `CompositionLocalProvider`, change the backdrop to use `activeHeroItem`, and pass the active-change callback to the carousel.

Replace this block:

```kotlin
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TvRootHeroBackdrop(
            item = featuredItem,
            modifier = Modifier.fillMaxWidth(),
        )
        // … LazyColumn …
    }
```

With:

```kotlin
    CompositionLocalProvider(LocalAmbientBackdropTint provides tintState) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            TvRootHeroBackdrop(
                item = activeHeroItem,
                modifier = Modifier.fillMaxWidth(),
            )
            // … LazyColumn body unchanged …
        }
    }
```

(Indent everything inside `Box { … }` by one extra level under the new `CompositionLocalProvider`.)

- [ ] **Step 3: Pass `onActiveItemChanged` to the carousel**

Inside the `featuredSection?.let { section -> item(key = "featured:${section.id}") { TvHomeHeroCarousel(...) } }` block (around lines 138–156), add the new parameter to the carousel call:

```kotlin
                    TvHomeHeroCarousel(
                        items = section.items,
                        onItemClick = onItemClick,
                        heroHeight = HeroDimens.HomeHeight,
                        autoFocus = !initialFocusRequested,
                        focusRequest = focusRequest,
                        initialFocusRequester = heroFocusRequester,
                        downFocusRequester = firstRowFocusRequester,
                        onDirectionDown = ::requestFirstRowFocus,
                        onAutoFocusClaimed = {
                            initialFocusRequested = true
                            onInitialContentFocus()
                        },
                        onFocusEntered = onInitialContentFocus,
                        onActiveItemChanged = { item ->
                            activeHeroItem = item
                            tintState.set(item)
                        },
                    )
```

(The carousel already has `onActiveItemChanged: (SectionItem) -> Unit = {}` as a default-empty parameter. We're just no longer leaving it empty.)

- [ ] **Step 4: Build**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manual emulator verification — skip on CI, flag in commit**

The full visual effect (tint multiply animating as hero advances) can only be verified on an Android TV emulator or device. Implementer should skip and report DONE_WITH_CONCERNS noting:
- User must verify: tint animates on hero auto-advance and on manual D-pad nav across hero cards.
- The 0.18α multiply strength is the primary knob; revisit if too subtle/strong.
- The 600ms crossfade may need to align with the carousel's `HOME_HERO_AUTO_ADVANCE_MS = 8_000L` rhythm — currently independent, which is fine.

- [ ] **Step 6: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/home/TvHomeScreen.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "feat(tv-home): publish active hero item to LocalAmbientBackdropTint (A.2)

Home now tracks which hero card is active and pushes it both to the
backdrop (replacing the static 'first item' reference) and to the
ambient tint state. TvHomeHeroCarousel's already-existing
onActiveItemChanged callback is finally consumed.

Visual effect (accent-color tint multiply on the backdrop) requires
emulator/device verification — flagged for QA before merge."
```

---

## Self-Review

**Spec coverage check** (against A.2 status in the spec doc):
- "Add dependency: `androidx.palette:palette`" → Task 1 ✓
- "New file: `TvAmbientBackdropTint.kt` exposing `LocalAmbientBackdropTint`…" → Task 2 ✓ (named `AmbientBackdropTint.kt` instead of `TvAmbientBackdropTint.kt` for naming consistency with peer files like `TvRootHeroBackdrop.kt` which the spec also references unprefixed)
- "Modify `TvRootHeroBackdrop.kt`: add a third overlay box that multiplies the current `LocalAmbientBackdropTint.current.accent` (when non-null) at low alpha" → Task 3 ✓ (fifth overlay, not third — counting the four existing layers)
- "Modify `TvHomeHeroCarousel.kt`: in `onActiveItemChanged` … additionally invoke `LocalAmbientBackdropTint.current.set(item)`" → **NOT done in carousel**; spec was slightly off here. Carousel already emits the callback; the *caller* (TvHomeScreen) does the `set`. This is the correct pattern because the CompositionLocal isn't readable inside the carousel without it being a composable scope, and we want the tint state owned by the page, not the carousel. Plan documents this in Task 4 rationale.
- "Modify `TvHomeScreen.kt`: wrap the content tree in `CompositionLocalProvider(LocalAmbientBackdropTint provides …) { … }`" → Task 4 ✓

**Placeholder scan:** No "TBD" or "fill in later." The one Coil API uncertainty (`coil3.toBitmap` vs alternate) is explicitly flagged in Task 2 with a workaround.

**Type consistency:** `AmbientBackdropTintState` referenced identically across tasks; `LocalAmbientBackdropTint` consistent; `accent: Color?` consistent.

**Sequencing:** Task 1 (dep) → Task 2 (state + tests) → Task 3 (consumer) → Task 4 (provider). Each commit leaves the codebase compiling. Tasks 1–3 don't change visible behavior; Task 4 turns the feature on.
