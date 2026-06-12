# A.6a — Card lift default focusedScale bump

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Change `continuumCardDefaults(focusedScale = 1f)` default to `1.08f` so that consumers (notably `TvMediaCard`, the poster card used across Home and Library grids) lift on focus by default instead of staying static. Audit existing explicit overrides and drop the ones that become redundant.

**Architecture:** One signature change in `FocusModifier.kt`. Five call-site audits, one cleanup. No new files, no new dependencies.

**Scope split:** This is **A.6a** only. The spec's A.6 also calls for per-card Palette-derived glow color (replacing the hardcoded `ContinuumBlueGlow`). That requires building per-card Palette extraction with caching — A.2's tint state is hero-only and has no per-poster cache. Deferred to a follow-up plan (**A.6b**) when we want to invest in that. Card lift alone delivers the most visible focus-treatment change.

**Tech stack:** Kotlin 2.1.20, Compose-for-TV 1.0.1, existing `FocusModifier.kt` infrastructure.

**Reference:** Spec section A.6 at `/opt/silo-android/docs/superpowers/specs/2026-05-23-android-tv-parity-rework-design.md`. Audit found 5 call sites of `continuumCardDefaults`:
- `TvMediaCard.kt:83` — no override (uses default 1f → will start lifting after this change)
- `TvEpisodeCard.kt:80` — explicit `focusedScale = 1.04f`
- `TvReferenceShelfCard.kt:58` — explicit `focusedScale = 1f`
- `TvProfileSelectionScreen.kt:174` — explicit `focusedScale = 1.08f` (becomes redundant)
- `TvCastCrewSection.kt:126` — explicit `focusedScale = 1.06f`

**Testing posture:** Per `AGENTS.md`, no tests for default-value tuning. Verification = build success + manual emulator QA (the user has the Shield setup and can verify directly).

---

### Task 1: Bump the `focusedScale` default in `continuumCardDefaults`

**Files:**
- Modify: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/theme/FocusModifier.kt`

**Why:** This is the entire visible change. Card consumers that don't pass an explicit `focusedScale` will start lifting 1.08× on focus.

- [ ] **Step 1: Change the default value from `1f` to `1.08f`**

In `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/theme/FocusModifier.kt`, locate the `continuumCardDefaults` function (around line 104). Change:

```kotlin
fun continuumCardDefaults(
    shape: Shape,
    focusedScale: Float = 1f,
): ContinuumCardFocus = ContinuumCardFocus(
```

To:

```kotlin
fun continuumCardDefaults(
    shape: Shape,
    focusedScale: Float = 1.08f,
): ContinuumCardFocus = ContinuumCardFocus(
```

That is the only change to this file.

- [ ] **Step 2: Build the androidTvApp module**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/theme/FocusModifier.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "feat(tv-theme): default continuumCardDefaults focusedScale to 1.08f (A.6a)

TvMediaCard (and any other consumer using the default) will now lift
on focus instead of staying static. Matches Apple's tvOS card focus
treatment closer to the platform default of ~1.10×; held slightly
under to avoid pixel-grid wobble in Compose Canvas.

Per-card Palette-derived glow color (other half of A.6) deferred
to a follow-up plan (A.6b) since it requires per-poster extraction
infrastructure not introduced by A.2."
```

---

### Task 2: Drop the redundant `focusedScale = 1.08f` override in `TvProfileSelectionScreen`

**Files:**
- Modify: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/profiles/TvProfileSelectionScreen.kt`

**Why:** This call site (line 174) explicitly passed `focusedScale = 1.08f`. After Task 1, that matches the default — the override is now noise. Cleanup keeps the codebase consistent with "use the default unless you have a reason to deviate."

The other overrides at `TvEpisodeCard.kt:80` (1.04f), `TvReferenceShelfCard.kt:58` (1f), and `TvCastCrewSection.kt:126` (1.06f) are **kept** — they're explicit choices for non-default lift values, presumably tuned for the specific visual context of each card type.

- [ ] **Step 1: Read the current call site to know the surrounding context**

```bash
sed -n '170,180p' /opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/profiles/TvProfileSelectionScreen.kt
```

You should see something like:
```kotlin
    val cardFocus = continuumCardDefaults(shape = shape, focusedScale = 1.08f)
```

- [ ] **Step 2: Remove the `focusedScale = 1.08f` named argument**

Edit the line to drop the redundant argument:

```kotlin
    val cardFocus = continuumCardDefaults(shape = shape)
```

(`shape` stays as a named argument since it's the only one being passed — Kotlin allows that.)

- [ ] **Step 3: Build**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/profiles/TvProfileSelectionScreen.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "chore(tv-profiles): drop redundant focusedScale=1.08f override

This override matched the post-A.6a default in continuumCardDefaults,
so it's now noise. The other explicit overrides in TvEpisodeCard
(1.04f), TvReferenceShelfCard (1f), and TvCastCrewSection (1.06f)
are intentional deviations from the default and stay."
```

---

## Self-Review

**Spec coverage** (against A.6 status in the spec doc):
- "Change `continuumCardDefaults(focusedScale: Float = 1f)` default to `1.08f`" → Task 1 ✓
- "Audit overrides — any that match `1.08f` become redundant and can be dropped" → Task 2 ✓ (TvProfileSelectionScreen was the one match)
- "Spring/shadow tune (optional)" → SKIPPED per spec ("defer unless visual comparison against tvOS shows a mismatch")
- "Palette-derived glow (depends on A.2): add an optional `paletteColor: Color?` parameter…" → DEFERRED to A.6b plan; rationale in Goal section above

**Placeholder scan:** No "TBD" / "fill in later." A.6b deferral is explicit, not a placeholder.

**Type consistency:** `focusedScale: Float` consistent. `ContinuumCardFocus` returned shape unchanged. `continuumCardDefaults` callers unaffected (default value change is binary-compatible at the source level; existing overrides keep working).

**Sequencing:** Task 1 (default change) then Task 2 (drop redundant). Order matters: Task 2's "redundant" justification only holds after Task 1 lands.
