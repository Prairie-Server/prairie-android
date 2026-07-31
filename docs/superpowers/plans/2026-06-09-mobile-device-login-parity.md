# Mobile Device Login Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Android mobile a reliable landing and approval surface for TV QR/device-login links.

**Architecture:** Keep the existing `DevicePairingScreen` and `DevicePairingViewModel`. Add a small, testable route parser for device-login URIs, use it for app startup and deep-link navigation, and keep NavHost deep-link declarations as a secondary direct path.

**Tech Stack:** Kotlin, Android intents/deep links, Jetpack Navigation Compose, Gradle Android unit tests.

---

### Task 1: Device Login URI Parser

**Files:**
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/DeviceLoginRouteParser.kt`
- Test: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/navigation/DeviceLoginRouteParserTest.kt`

- [ ] **Step 1: Write failing parser tests**

Assert these inputs resolve to `Route.PairDevice(...).route`:
- `prairie://device?token=t1`
- `continuum://device?code=ABCD-1234`
- `https://prairie.example/device?token=t1`
- `https://prairie.example/auth/device?code=ABCD`

Assert unrelated URLs return null.

- [ ] **Step 2: Run focused test and verify RED**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.navigation.DeviceLoginRouteParserTest"`

Expected: fail because `deviceLoginPairRouteOrNull` is missing.

- [ ] **Step 3: Implement parser**

Implement `internal fun deviceLoginPairRouteOrNull(rawUri: String?): String?` using `android.net.Uri.parse`. Accept custom schemes with host `device`, and http/https URLs whose path ends in `/device` or `/auth/device`. Prefer nonblank `token`; otherwise use nonblank `code`.

- [ ] **Step 4: Run focused test and verify GREEN**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.navigation.DeviceLoginRouteParserTest"`

Expected: pass.

### Task 2: Startup Route Wiring

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/MainActivity.kt`

- [ ] **Step 1: Route launch intent through parser**

Before normal auth start-destination resolution, check `intent?.dataString`. If it maps to a device-login route, return that route.

- [ ] **Step 2: Add warm-intent handling**

Handle `onNewIntent` by updating Compose state with the latest `dataString` and navigating via `AppNavigation` when a device-login URI arrives while the app is already open.

- [ ] **Step 3: Compile mobile**

Run: `./gradlew :androidApp:compileDebugKotlinAndroid`

Expected: build successful.

### Task 3: Verification and Commit

**Files:**
- Modify: all files above.

- [ ] **Step 1: Run full verification**

Run: `git diff --check && ./gradlew :shared:testDebugUnitTest :android-shared:testDebugUnitTest :androidApp:testDebugUnitTest :androidApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest :androidTvApp:compileDebugKotlinAndroid`

Expected: build successful.

- [ ] **Step 2: Commit**

Run:
```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/MainActivity.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/DeviceLoginRouteParser.kt androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/navigation/DeviceLoginRouteParserTest.kt docs/superpowers/plans/2026-06-09-mobile-device-login-parity.md
git commit -m "fix: harden mobile device login links"
```
