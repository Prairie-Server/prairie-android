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
    fun loginUsesStockTextFieldsInsteadOfCredentialKeyboardActions() {
        kotlin.test.assertTrue(
            screenSource.contains("onUsernameChanged = viewModel::onUsernameChanged"),
            "Username entry should use the platform text field callback.",
        )
        kotlin.test.assertTrue(
            screenSource.contains("onPasswordChanged = viewModel::onPasswordChanged"),
            "Password entry should use the platform text field callback.",
        )
        assertFalse(screenSource.contains("viewModel.onCredentialKeyboardAction(field, action)"))
        assertFalse(screenSource.contains("applyTvCredentialKeyboardAction(state.username, action)"))
        assertFalse(screenSource.contains("applyTvCredentialKeyboardAction(state.password, action)"))
    }
}
