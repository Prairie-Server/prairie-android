package org.prairieserver.prairie.tv.ui.screens.auth

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvAuthFocusSourceTest {
    private val serverSetupSource = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/auth/TvServerSetupScreen.kt",
    ).readText()
    private val loginSource = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/auth/TvLoginScreen.kt",
    ).readText()
    private val setupSource = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/auth/TvSetupScreen.kt",
    ).readText()
    private val signupSource = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/auth/TvSignupScreen.kt",
    ).readText()

    @Test
    fun serverSetupAnchorsInitialFocusSafelyOnManualEntry() {
        assertTrue(serverSetupSource.contains(".imePadding()"))
        assertTrue(serverSetupSource.contains("keyboardController?.show()"))
        assertTrue(serverSetupSource.contains("LaunchedEffect(isActivePairing)"))
        // Initial focus always anchors on the server-address field (tvOS
        // .defaultFocus(.host)); the user chooses "Set up with phone" by
        // navigating to it — it is NOT auto-focused (Jim TV QA 2026-07-10).
        // Crash-guarded via runCatching.
        assertTrue(serverSetupSource.contains("runCatching { focusRequester.requestFocus() }"))
        assertFalse(serverSetupSource.contains("phoneSetupFocus.requestFocus()"))
    }

    @Test
    fun loginPasswordToggleFocusRequestsAreCrashGuarded() {
        assertTrue(loginSource.contains("runCatching { usernameFocus.requestFocus() }"))
        assertTrue(loginSource.contains("runCatching { usePasswordFocus.requestFocus() }"))
    }

    @Test
    fun loginUsesPlatformImeTextFieldsForCredentialEntry() {
        assertTrue(loginSource.contains("OutlinedTextField("))
        assertTrue(loginSource.contains("KeyboardType.Text"))
        assertTrue(loginSource.contains("KeyboardType.Password"))
        assertTrue(loginSource.contains("KeyboardActions("))
        assertFalse(loginSource.contains("CredentialDisplayField("))
        assertFalse(loginSource.contains("PrairieCredentialKeyboard("))
    }

    @Test
    fun loginUsesImeAwareStockKeyboardLayout() {
        assertTrue(loginSource.contains(".imePadding()"))
        assertTrue(loginSource.contains(".verticalScroll(rememberScrollState())"))
        assertFalse(loginSource.contains("loginScrollState.animateScrollTo(0)"))
    }

    @Test
    fun credentialKeyboardIsNotMountedByLoginScreen() {
        assertFalse(loginSource.contains("credentialKeyboardVisible"))
        assertFalse(loginSource.contains("activeCredentialField"))
        assertFalse(loginSource.contains("credentialKeyboardFirstKeyFocus"))
    }

    @Test
    fun setupAndSignupInitialFieldFocusRequestsAreCrashGuarded() {
        assertTrue(setupSource.contains("LaunchedEffect(Unit) { runCatching { usernameFocus.requestFocus() } }"))
        assertTrue(signupSource.contains("LaunchedEffect(Unit) { runCatching { usernameFocus.requestFocus() } }"))
    }

    @Test
    fun setupAndSignupUsePlatformImeTextFieldsForAuthEntry() {
        assertTrue(setupSource.contains("OutlinedTextField("))
        assertTrue(setupSource.contains("KeyboardType.Email"))
        assertTrue(setupSource.contains("KeyboardType.Password"))
        assertFalse(setupSource.contains("CredentialDisplayField("))
        assertFalse(setupSource.contains("TvSetupAnsiKeyboard("))

        assertTrue(signupSource.contains("OutlinedTextField("))
        assertTrue(signupSource.contains("KeyboardType.Email"))
        assertTrue(signupSource.contains("KeyboardType.Password"))
        assertFalse(signupSource.contains("CredentialDisplayField("))
        assertFalse(signupSource.contains("TvSignupAnsiKeyboard("))
    }

    @Test
    fun serverSetupDoesNotRestoreManualFocusWhilePairingOverlayIsActive() {
        assertTrue(serverSetupSource.contains("LaunchedEffect(isActivePairing)"))
        assertTrue(serverSetupSource.contains("if (!isActivePairing) {"))
        assertTrue(serverSetupSource.contains("runCatching {"))
    }
}
