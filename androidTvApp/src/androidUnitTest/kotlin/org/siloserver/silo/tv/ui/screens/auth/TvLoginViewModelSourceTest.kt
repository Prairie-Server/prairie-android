package org.siloserver.silo.tv.ui.screens.auth

import kotlin.test.Test
import kotlin.test.assertFalse

class TvLoginViewModelSourceTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/auth/TvLoginViewModel.kt",
    ).readText()
    private val screenSource = java.io.File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/auth/TvLoginScreen.kt",
    ).readText()

    @Test
    fun loginDoesNotWriteLegacyPlaintextAuthMirror() {
        assertFalse(
            source.contains("getSharedPreferences(\"silo_auth\""),
            "TV login must not write the legacy plaintext auth preferences",
        )
        assertFalse(source.contains(".putString(\"accessToken\""))
        assertFalse(source.contains(".putString(\"refreshToken\""))
    }

    @Test
    fun credentialKeyboardActionsAreAppliedInsideViewModelStateUpdate() {
        kotlin.test.assertTrue(
            screenSource.contains("viewModel.onCredentialKeyboardAction(field, action)"),
            "Credential keyboard actions must be forwarded to the ViewModel so rapid key repeats do not use stale Compose text snapshots.",
        )
        assertFalse(screenSource.contains("applyTvCredentialKeyboardAction(state.username, action)"))
        assertFalse(screenSource.contains("applyTvCredentialKeyboardAction(state.password, action)"))
    }
}
