# Android TV Auth IME Relocation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the stock Android TV keyboard while reliably revealing the focused field and its label across every TV authentication form.

**Architecture:** Add one reusable Compose modifier that reacts to focus plus the measured IME inset after layout, and one reusable scroll-state helper that restores the normal top position when the IME closes. Apply those primitives to the existing auth screens and the shared TV text-input dialog, while giving the server screen one outer vertical scroll owner.

**Tech Stack:** Kotlin 2.1, Jetpack Compose Foundation, Android `WindowInsets.ime`, Kotlin/JUnit 4, Gradle, ADB.

## Global Constraints

- Continue using the stock Shield/Android TV IME.
- Preserve every keyboard-closed composition, style, focus order, and D-pad action.
- Reveal the focused field context with exactly 32dp of bottom clearance.
- Do not change server APIs, authentication logic, validation, or credential storage.
- Install the verified debug APK on `192.168.1.128:5555` without launching it.

---

### Task 1: Shared IME-aware relocation primitives

**Files:**
- Create: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/components/TvImeAwareForm.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/components/TvImeAwareFormTest.kt`

**Interfaces:**
- Produces: `Modifier.tvImeAwareFieldContext(bottomClearance: Dp = 32.dp)`.
- Produces: `rememberTvImeAwareFormScrollState(): ScrollState`.
- Produces: pure internal relocation-key and keyboard-transition policies used by the Compose helpers and JVM tests.

- [ ] **Step 1: Write failing JVM tests for relocation eligibility and keyboard-close restoration**

Cover focus-before-IME, IME-before-focus, zero-size fields, non-zero IME size changes, duplicate snapshots, and visible-to-hidden restoration. Each expected result is a literal and exercises the production policy.

- [ ] **Step 2: Run the focused test and verify RED**

Run: `JAVA_HOME=/Users/jimcole/.local/share/mise/installs/java/temurin-21.0.11+10.0.LTS ./gradlew :androidTvApp:testDebugUnitTest --tests '*TvImeAwareFormTest'`

Expected: compilation failure because the shared production policy does not exist.

- [ ] **Step 3: Implement the minimal shared helpers**

The modifier records descendant focus and measured bounds, reads `WindowInsets.ime`, waits one frame after a valid key change, then requests a rectangle extending 32dp below the field context. The scroll helper resets only on a visible-to-hidden IME transition. Lifecycle cancellation and `runCatching` make disposal/navigation harmless.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the command from Step 2 and require zero failures.

### Task 2: Apply the shared behavior to all TV entry forms

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/auth/TvServerSetupScreen.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/auth/TvLoginScreen.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/auth/TvSetupScreen.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/auth/TvSignupScreen.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/components/TvTextInputDialog.kt`

**Interfaces:**
- Consumes: `Modifier.tvImeAwareFieldContext()` and `rememberTvImeAwareFormScrollState()` from Task 1.
- Preserves: existing `FocusRequester`, `KeyboardOptions`, `KeyboardActions`, validation callbacks, and stock-IME invocation.

- [ ] **Step 1: Replace per-field focus-time requests with the shared modifier**

Attach the modifier to each label-and-field context for server URL, login username/password, setup username/email/password, signup username/email/password/invite, and the shared text-input dialog field.

- [ ] **Step 2: Give each screen the shared outer scroll state**

Replace anonymous `rememberScrollState()` calls with `rememberTvImeAwareFormScrollState()`.

- [ ] **Step 3: Remove the server card's competing vertical scroll owner**

Allow the chooser row to keep a 300dp minimum height and grow for validation content; remove `ManualEntryCard`'s nested `verticalScroll` so the outer page owns IME relocation.

- [ ] **Step 4: Compile and run Android TV unit tests**

Run: `JAVA_HOME=/Users/jimcole/.local/share/mise/installs/java/temurin-21.0.11+10.0.LTS ./gradlew :androidTvApp:testDebugUnitTest :androidTvApp:assembleDebug`

Expected: all tests pass and the universal debug APK is produced.

### Task 3: Shield install and visual verification

**Files:**
- Verify: `androidTvApp/build/outputs/apk/debug/androidTvApp-universal-debug.apk`

**Interfaces:**
- Target package: `org.siloserver.silo`.
- Target device: `192.168.1.128:5555`.

- [ ] **Step 1: Replace the existing debug installation without launching it**

Run `adb -s 192.168.1.128:5555 install -r androidTvApp/build/outputs/apk/debug/androidTvApp-universal-debug.apk`. If the signing identity differs, uninstall only `org.siloserver.silo` and reinstall, as already authorized for this Shield task.

- [ ] **Step 2: Verify package state**

Use `dumpsys package org.siloserver.silo` to verify the expected version and `stopped=true notLaunched=true` immediately after installation.

- [ ] **Step 3: Hand off visual QA**

Do not launch Silo. Ask the user to open the server and login fields; once they do, capture Shield screenshots through ADB and confirm the label, complete field, and 32dp clearance are visible without layout oscillation.
