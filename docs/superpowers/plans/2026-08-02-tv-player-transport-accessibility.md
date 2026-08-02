# TV Player Transport Accessibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make D-pad Down reveal and focus the transport controls, and enlarge the existing icon-only controls without changing their visual style.

**Architecture:** Change the shared remote-key classifier so hidden and visible playback both route Down to the transport focus target. Extract transport dimensions into a small pure policy consumed by the Compose row, allowing JVM tests to enforce minimum legibility while the UI retains its existing circles, grouping, and focus inversion.

**Tech Stack:** Kotlin, Jetpack Compose for TV, Android `KeyEvent`, Kotlin test, Gradle, ADB.

## Global Constraints

- D-pad Down while playback controls are hidden reveals the idle overlay and focuses Play/Pause.
- Menu and Settings remote keys continue opening the information/settings HUD.
- Preserve circular controls, grouping, icon-only presentation, borders, colors, and white/black focus inversion.
- Every transport button is 44dp; Play/Pause is 22dp; every secondary glyph is 20dp.
- Keep the 5dp inter-button gap and existing left/right group layout.
- Do not change subtitle selection, HUD content, player state, or transport actions.
- Build and install the ARM64 debug APK without launching it.

---

### Task 1: Route D-pad Down to Play/Pause

**Files:**
- Modify: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/player/TvPlayerRemoteKeyActionTest.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/player/TvPlayerRemoteKeyAction.kt`

**Interfaces:**
- Consumes: `tvPlayerRemoteKeyAction(keyCode: Int, action: Int, repeatCount: Int, dpadHorizontalSeek: Boolean)`.
- Produces: `TvPlayerRemoteKeyAction.FocusTransport` for the initial D-pad Down press in both hidden- and visible-overlay states.

- [ ] **Step 1: Change the existing Down-key test to express the desired behavior**

Rename the test to `down always moves focus to transport while menu and settings open hud`. Require `FocusTransport` for both default and `dpadHorizontalSeek = false` calls. Keep the existing Menu and Settings assertions requiring `OpenHud`.

- [ ] **Step 2: Run the focused test and verify the regression assertion fails**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests 'org.siloserver.silo.tv.ui.screens.player.TvPlayerRemoteKeyActionTest.down always moves focus to transport while menu and settings open hud'
```

Expected: FAIL because hidden-overlay Down currently returns `OpenHud`.

- [ ] **Step 3: Implement the minimal mapping change**

In `tvPlayerRemoteKeyAction`, map `KEYCODE_DPAD_DOWN` on the initial `ACTION_DOWN` directly to `FocusTransport`, independent of `dpadHorizontalSeek`. Continue returning `null` for KeyUp. Update the nearby comment to describe transport-first behavior; leave Menu and Settings handling unchanged.

- [ ] **Step 4: Run the complete remote-key test class**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests 'org.siloserver.silo.tv.ui.screens.player.TvPlayerRemoteKeyActionTest'
```

Expected: PASS with no failures.

- [ ] **Step 5: Commit the navigation fix**

```bash
git add androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/player/TvPlayerRemoteKeyAction.kt androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/player/TvPlayerRemoteKeyActionTest.kt
git commit -m "fix(tv): focus player transport on dpad down"
```

### Task 2: Enforce legible transport dimensions

**Files:**
- Create: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/player/TvPlayerTransportVisualPolicy.kt`
- Create: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/player/TvPlayerTransportVisualPolicyTest.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/player/TvPlayerTransportCluster.kt`

**Interfaces:**
- Produces: `TvTransportControlMetrics(buttonSizeDp: Float, symbolSizeDp: Float)`.
- Produces: `tvTransportControlMetrics(isPrimary: Boolean): TvTransportControlMetrics`.
- Consumes: those metrics in `TransportIconButton` before converting each Float to Compose `Dp`.

- [ ] **Step 1: Extract the current dimensions without changing behavior**

Create the pure policy with the existing values: button `33f`, primary glyph `15f`, secondary glyph `12.5f`. Replace the local constants in `TransportIconButton` with values returned by `tvTransportControlMetrics(isPrimary)`.

- [ ] **Step 2: Verify the behavior-preserving extraction compiles**

Run:

```bash
./gradlew :androidTvApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Add a failing legibility test**

Create tests that require both primary and secondary button targets to be at least `44f`, require the secondary glyph to be at least `20f`, and require the primary glyph to be at least `22f`. These thresholds independently encode the approved television legibility contract.

- [ ] **Step 4: Run the policy test and verify it fails on the current sizes**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests 'org.siloserver.silo.tv.ui.screens.player.TvPlayerTransportVisualPolicyTest'
```

Expected: FAIL because the extracted policy still returns 33dp buttons and 12.5dp/15dp glyphs.

- [ ] **Step 5: Update the policy to the approved dimensions**

Return `44f` for every button, `22f` for the primary glyph, and `20f` for secondary glyphs. Do not alter gaps, colors, focus behavior, grouping, or descriptions.

- [ ] **Step 6: Run the policy and remote-key tests**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests 'org.siloserver.silo.tv.ui.screens.player.TvPlayerTransportVisualPolicyTest' --tests 'org.siloserver.silo.tv.ui.screens.player.TvPlayerRemoteKeyActionTest'
```

Expected: PASS with no failures.

- [ ] **Step 7: Commit the visual sizing fix**

```bash
git add androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/player/TvPlayerTransportCluster.kt androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/player/TvPlayerTransportVisualPolicy.kt androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/player/TvPlayerTransportVisualPolicyTest.kt
git commit -m "fix(tv): enlarge player transport controls"
```

### Task 3: Full verification and Shield installation

**Files:**
- Verify all files changed in Tasks 1 and 2.
- Build artifact: `androidTvApp/build/outputs/apk/debug/androidTvApp-arm64-v8a-debug.apk`.

**Interfaces:**
- Consumes: completed navigation and dimension policies.
- Produces: a verified debug APK installed on the Shield with the app stopped.

- [ ] **Step 1: Run the full Android test and TV build command**

Run:

```bash
./gradlew test :androidTvApp:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Check repository cleanliness and patch formatting**

Run `git diff --check`, inspect `git status --short --branch`, and confirm local `main` remains zero commits behind `upstream/main`.

- [ ] **Step 3: Install the ARM64 debug APK without launching**

Run:

```bash
adb -s 192.168.1.128:5555 install -r androidTvApp/build/outputs/apk/debug/androidTvApp-arm64-v8a-debug.apk
```

Expected: `Success`. Do not issue `am start`, `monkey`, or any other launch command.

- [ ] **Step 4: Verify installed package and stopped state**

Read `dumpsys package org.siloserver.silo` for version information and run `pidof org.siloserver.silo`. The package query must succeed and `pidof` must return no process immediately after installation.

- [ ] **Step 5: Report the result**

Report the two implementation commits, full test/build result, installed debug version, stopped app state, and whether anything was pushed.
