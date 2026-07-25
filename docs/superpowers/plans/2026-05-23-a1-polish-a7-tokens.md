# A.1 polish + A.7 placeholder tokens — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish sub-projects A.7 (add three forward-looking `Spacing` tokens) and A.1 (delete the unreferenced legacy drawer; add scroll-hide-on-content-scroll behavior to the existing `TvTopMenuBar`). This is the housekeeping warm-up that closes out the two sub-projects already 90–95% shipping per the audit, leaving a clean foundation for the larger pieces (A.2 backdrop finish, A.3 HUD redesign, etc.).

**Architecture:** All changes inside `androidTvApp/`. No new dependencies. Five discrete commits delivered in this order:
1. Add three placeholder `Spacing` tokens.
2. Verify drawer-only color tokens are truly orphaned (read-only grep).
3. Delete `TvNavigationDrawer.kt`.
4. Delete the nine drawer-only color tokens from `Color.kt`.
5. Add scroll-hide animation to `TvTopMenuBar` (driven by a `NestedScrollConnection` hoisted in `TvMainShell`).

**Tech stack:** Kotlin 2.1.20, Compose-for-TV 1.0.1, existing `androidx.compose.ui.input.nestedscroll` APIs, existing `androidx.compose.animation.core.Animatable` for menu visibility.

**Reference:** Spec `/opt/prairie-android/docs/superpowers/specs/2026-05-23-android-tv-parity-rework-design.md` sections A.1 and A.7. Audit confirmed `FocusedContainer` and `FocusedContent` color tokens are NOT drawer-only (used by `TvHeroToggleIconButton`, `TvChip`, `TvFullScreenPicker`, `TvAlphabetRail`) — leave them alone.

**Testing posture:** Per `AGENTS.md` ("no tests for small UI changes"), Tasks 1, 3, 4 verify via build success; Task 5's scroll-hide is manually verified on an Android TV emulator. The plan adds no Compose UI test framework — out of pattern for this repo.

---

### Task 1: Add three placeholder Spacing tokens

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/theme/Spacing.kt`

**Why:** Future consumers (A.3 HUD, A.2 backdrop fade, scroll-hide offset math) will reference these. Adding them now avoids three trivial follow-up commits later. Values are conservative defaults; downstream tuning is expected.

- [ ] **Step 1: Add the three tokens to the `Spacing` object**

Open `/opt/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/theme/Spacing.kt`. Inside the existing `object Spacing { … }` block, after the existing `sectionSpacing = 30.dp` line, add:

```kotlin
    /** Inset applied around the Infuse-style player HUD panel (A.3). */
    val hudPanelInset = 24.dp

    /** Reserved height for the floating top menu bar (A.1 scroll-hide math). */
    val topMenuBarHeight = 64.dp

    /** Vertical distance over which the home-hero backdrop fades into the rows (A.2). */
    val heroBackdropFade = 120.dp
```

- [ ] **Step 2: Build the androidTvApp module to verify Spacing.kt compiles**

Run from `/opt/prairie-android`:

```bash
./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. No warnings about Spacing.kt.

- [ ] **Step 3: Commit**

```bash
git -C /opt/prairie-android add androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/theme/Spacing.kt
git -C /opt/prairie-android commit -m "feat(tv-theme): add hudPanelInset, topMenuBarHeight, heroBackdropFade spacing tokens

Placeholder tokens for upcoming A.1 scroll-hide, A.2 backdrop fade, and
A.3 player HUD work. No current consumers."
```

---

### Task 2: Verify drawer-only color tokens are truly orphaned

**Files:** None modified. Pure verification.

**Why:** The audit identified nine `Drawer*` color tokens as drawer-only, but a previous grep showed `FocusedContainer` and `FocusedContent` are widely used elsewhere — the audit conflated them. Re-verify each `Drawer*` token before any deletion in Task 3/4.

- [ ] **Step 1: Grep for each drawer-only token across all sources**

Run from `/opt/prairie-android`:

```bash
grep -rnE "DrawerSurface|DrawerMenuSurface|DrawerOutline|DrawerScrimStart|DrawerScrimMid|DrawerScrimEnd|DrawerSelectedSurface|DrawerSelectedBorder|DrawerIconSurface" \
  androidTvApp/src android-shared/src shared/src
```

Expected: only references inside `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/theme/Color.kt` (the definitions, lines 60–68) and `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/shell/TvNavigationDrawer.kt` (imports + use sites). No references elsewhere.

- [ ] **Step 2: Grep for `TvNavigationDrawer` to confirm zero external callers**

```bash
grep -rn "TvNavigationDrawer\|TvDrawer" \
  androidTvApp/src android-shared/src shared/src \
  | grep -v "TvNavigationDrawer.kt:"
```

Expected: exactly one match, in `Color.kt:59`, which is a code comment about Phase 2 (no compile-time reference).

If either grep returns unexpected external references, STOP and report them — Task 3 / Task 4 cannot proceed without updating those callers first.

- [ ] **Step 3: (no commit — verification only)**

---

### Task 3: Delete `TvNavigationDrawer.kt`

**Files:**
- Delete: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/shell/TvNavigationDrawer.kt` (737 lines)

**Why:** Replaced by `TvTopMenuBar` in `TvMainShell` per audit; zero call sites.

- [ ] **Step 1: Delete the file**

```bash
git -C /opt/prairie-android rm androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/shell/TvNavigationDrawer.kt
```

- [ ] **Step 2: Build the androidTvApp module**

```bash
./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. If any import of `TvNavigationDrawer`, `TvNavigationDrawerKt`, or any of its exported members surfaces a `Unresolved reference` error, STOP — Task 2 missed a caller; fix it before proceeding.

- [ ] **Step 3: Commit**

```bash
git -C /opt/prairie-android commit -m "chore(tv-shell): delete unreferenced legacy TvNavigationDrawer

The drawer was superseded by TvTopMenuBar (already wired in TvMainShell).
Confirmed zero external references prior to deletion."
```

---

### Task 4: Remove drawer-only color tokens from `Color.kt`

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/theme/Color.kt`

**Why:** Token defs were kept alive only because `TvNavigationDrawer` referenced them. With Task 3 done, they are dead code.

- [ ] **Step 1: Remove the drawer comment block and the nine token defs**

Open `Color.kt`. Find lines 56–68 (the block beginning with the comment `// Drawer surfaces are temporarily preserved …`) and delete the entire block:

```kotlin
// Drawer surfaces are temporarily preserved so the legacy left rail still
// compiles; Phase 2 replaces the drawer with a top menu bar and these become
// dead code that gets pruned along with TvNavigationDrawer.
val DrawerSurface = Color(0xF60A0D13)
val DrawerMenuSurface = Color(0xF015171C)
val DrawerOutline = Color.White.copy(alpha = 0.08f)
val DrawerScrimStart = Color.Black.copy(alpha = 0.75f)
val DrawerScrimMid = Color.Black.copy(alpha = 0.32f)
val DrawerScrimEnd = Color(0x00000000)
val DrawerSelectedSurface = Color.White.copy(alpha = 0.10f)
val DrawerSelectedBorder = ContinuumOnSurface
val DrawerIconSurface = Color.White.copy(alpha = 0.06f)
```

DO NOT remove `FocusedContainer`, `FocusedContent`, `SelectedContainer`, `SubtleSurface`, or `ElevatedSurface` — all are used by non-drawer components.

- [ ] **Step 2: Build the androidTvApp module**

```bash
./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. If any `Unresolved reference: Drawer…` shows up, you removed something still referenced — restore that single token and rerun Task 2.

- [ ] **Step 3: Commit**

```bash
git -C /opt/prairie-android add androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/theme/Color.kt
git -C /opt/prairie-android commit -m "chore(tv-theme): remove drawer-only color tokens

DrawerSurface, DrawerMenuSurface, DrawerOutline, DrawerScrimStart,
DrawerScrimMid, DrawerScrimEnd, DrawerSelectedSurface, DrawerSelectedBorder,
DrawerIconSurface are unreferenced after TvNavigationDrawer was deleted.

FocusedContainer / FocusedContent kept — used by TvHeroToggleIconButton,
TvChip, TvFullScreenPicker, TvAlphabetRail."
```

---

### Task 5: Add scroll-hide animation to `TvTopMenuBar`

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/shell/TvMainShell.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/shell/TvTopMenuBar.kt`

**Why:** The audit identified this as the one missing piece of A.1 vs. spec. Apple's `TVTopMenuBar` hides when content scrolls down and reappears on scroll up. The plumbing is: a hoisted `NestedScrollConnection` in `TvMainShell` updates a `menuVisibility` animation state (0f hidden ↔ 1f visible); the menu translates and fades by that amount.

The implementation deliberately does NOT use `androidx.compose.material3.SnackbarHostState`-style scaffolds — `TvMainShell` is bespoke and we keep it that way.

- [ ] **Step 1: Read the current `TvMainShell.kt` to understand the content-hosting Box and the existing `TvTopMenuBar` invocation**

```bash
sed -n '1,330p' /opt/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/shell/TvMainShell.kt
```

Locate (around line 289) the `TvTopMenuBar(…)` call and the inner content (likely a `NavHost(…)`) it sits above. Both live inside a common `Box { … }`. The plan modifies the wrapping `Box`'s child layout: add `Modifier.nestedScroll(scrollConnection)` to the inner content container; pass a `menuVisibility: Float` to `TvTopMenuBar`.

- [ ] **Step 2: In `TvMainShell.kt`, hoist a `menuVisibility` `Animatable` and a `NestedScrollConnection` at the top of the composable**

Add these imports if not present:

```kotlin
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlinx.coroutines.launch
```

Inside the `TvMainShell` composable body (after the existing `remember`/`State` blocks and before the `Box {`), add:

```kotlin
    val menuVisibility = remember { Animatable(1f) }
    val scrollScope = rememberCoroutineScope()
    val nestedScrollConnection = remember(menuVisibility, scrollScope) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // available.y < 0 means the user is scrolling content downward
                // (revealing items below the fold) — fade the menu out.
                // available.y > 0 means scrolling upward — fade it in.
                // We don't consume any scroll; the inner LazyColumn handles it fully.
                if (source == NestedScrollSource.UserInput) {
                    val deltaProgress = available.y / 240f
                    val target = (menuVisibility.value + deltaProgress).coerceIn(0f, 1f)
                    scrollScope.launch { menuVisibility.snapTo(target) }
                }
                return Offset.Zero
            }
        }
    }
```

The `240f` divisor maps roughly 240px of scroll to a full hide/show transition — tune during manual QA.

- [ ] **Step 3: Wrap the inner content (the `NavHost` block) with `Modifier.nestedScroll(nestedScrollConnection)`**

In the existing layout body, locate the `Box` (or container) that hosts the inner `NavHost(...)` content (NOT the outer `Box` that also hosts the menu). Add `Modifier.nestedScroll(nestedScrollConnection)` to that container.

If the inner content is currently `NavHost(navController = innerNav, ...)` without a wrapping `Box`, wrap it:

```kotlin
Box(modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
    NavHost(navController = innerNav, ...) {
        // existing composable(...) entries unchanged
    }
}
```

Do NOT attach `.nestedScroll(...)` to the outermost `Box` — that container also hosts `TvTopMenuBar`, and we don't want the menu's own focus changes feeding back into its own visibility.

- [ ] **Step 4: Pass `menuVisibility.value` into `TvTopMenuBar` via a new `visibility: Float` parameter**

Modify the existing call site (currently around line 289):

```kotlin
        TvTopMenuBar(
            selectedRoot = selectedRoot,
            accountState = accountSnapshot,
            onSelectRoot = onSelectRoot,
            onProfileClick = { profileMenuOpen = !profileMenuOpen },
            onMoveDown = { moveFocusToContent(currentRoute) },
            isMenuFocused = isMenuFocused,
            onMenuFocusChange = { isMenuFocused = it },
            isFocusSuppressed = profileMenuOpen,
            focusRequest = menuFocusRequest,
            visibility = menuVisibility.value, // NEW
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .zIndex(1f),
        )
```

- [ ] **Step 5: Add the `visibility` parameter to `TvTopMenuBar` and apply it via `graphicsLayer`**

Open `TvTopMenuBar.kt`. Find the function signature for `TvTopMenuBar` (top-level `@Composable fun TvTopMenuBar(...)`). Add a new parameter (default `1f` so callers that don't pass it still work):

```kotlin
@Composable
fun TvTopMenuBar(
    selectedRoot: TvRootDestination,
    accountState: TvAccountState,
    onSelectRoot: (TvRootDestination) -> Unit,
    onProfileClick: () -> Unit,
    onMoveDown: () -> Unit,
    isMenuFocused: Boolean,
    onMenuFocusChange: (Boolean) -> Unit,
    isFocusSuppressed: Boolean,
    focusRequest: Int,
    visibility: Float = 1f,            // NEW
    modifier: Modifier = Modifier,
) {
```

Inside the function, locate the outermost composable (a `Row` or `Box` wrapping the menu pills — likely around line 155–162 per the audit). Add a `graphicsLayer` to its modifier chain:

```kotlin
    // Add this import at the top of TvTopMenuBar.kt if not present.
    // import androidx.compose.ui.graphics.graphicsLayer

    Box(
        modifier = modifier
            .graphicsLayer {
                // Slide the menu up by its own height as visibility drops to 0.
                translationY = -size.height * (1f - visibility)
                alpha = visibility
            }
            // ... existing modifiers (background, padding, etc.) unchanged
    ) {
        // existing menu contents unchanged
    }
```

If the outermost composable is a `Row` rather than a `Box`, apply `graphicsLayer` to the `Row`'s modifier the same way.

- [ ] **Step 6: Build to verify nothing is broken**

```bash
./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Manual verification on an Android TV emulator**

Build and install on an Android TV emulator (or device):

```bash
./gradlew :androidTvApp:installDebug
```

Then:
1. Launch the TV app, sign in to a server, land on Home.
2. D-pad down from the menu into the first row.
3. D-pad down again into the second row. The top menu bar should fade and slide upward off-screen.
4. D-pad back up. Menu should slide and fade back in as you cross the threshold.
5. Verify menu remains hidden if you keep traversing down; remains visible if you stay in the first row.

If the menu hides too aggressively (e.g. on the first D-pad press), increase the `240f` divisor in Step 2 (try `360f` or `480f`). If it never hides, decrease it (try `160f` or `120f`).

If verification passes, proceed. If the menu interferes with focus or causes visual artifacts (clipped pills, ghosted text during alpha fade), STOP and investigate before committing.

- [ ] **Step 8: Commit**

```bash
git -C /opt/prairie-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/shell/TvMainShell.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/shell/TvTopMenuBar.kt
git -C /opt/prairie-android commit -m "feat(tv-shell): hide TvTopMenuBar on content scroll-down

TvMainShell hoists a NestedScrollConnection that maps content scroll
into a 0..1 visibility animation; TvTopMenuBar applies it via
graphicsLayer (translationY + alpha). Matches Apple TVTopMenuBar
hide-on-scroll behavior (spec A.1).

Tuning constant (240f) chosen by feel on emulator; revisit during
A.2 hero rework if interaction with hero focus changes feels off."
```

---

## Self-Review

**Spec coverage check:**
- A.7 — "add `Spacing.hudPanelInset`, `Spacing.topMenuBarHeight`, `Spacing.heroBackdropFade`" → Task 1 ✓
- A.1 — "delete `TvNavigationDrawer.kt`" → Tasks 2–3 ✓
- A.1 — "delete drawer-only color tokens" → Task 4 ✓
- A.1 — "Hides on scroll-down inside a focused row (`nestedScroll`); reappears on focus-up" → Task 5 ✓

No spec requirements for A.1 or A.7 remain uncovered.

**Placeholder scan:** No "TBD", "add validation", "similar to Task N", or vague guidance present. The one numeric tuning value (`240f`) is explicitly called out as something to tune during manual QA with concrete fallback options.

**Type consistency:** New `visibility: Float` parameter has identical name and type in both the caller (`TvMainShell`) and definition (`TvTopMenuBar`). `menuVisibility` consistently typed as `Animatable<Float, *>` (via `Animatable(1f)`). `nestedScrollConnection` consistently typed as `NestedScrollConnection`.

**Sequencing:** Tasks are ordered so each verification step protects the next: Task 2 (grep) guards Task 3 (delete file); Task 3 (file deleted) guards Task 4 (delete tokens — would fail to compile otherwise); Tasks 1+3+4 must all be in before Task 5 to keep build green per-commit.
