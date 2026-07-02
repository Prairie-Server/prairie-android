package org.siloserver.silo.tv.ui.screens.auth

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class TvAuthSingleFlightSourceTest {
    private val loginViewModel = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/auth/TvLoginViewModel.kt",
    ).readText()
    private val setupViewModel = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/auth/TvSetupViewModel.kt",
    ).readText()
    private val signupViewModel = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/auth/TvSignupViewModel.kt",
    ).readText()
    private val auroraChrome = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/components/TvAuroraChrome.kt",
    ).readText()
    private val heroActionPill = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/components/TvHeroActionPill.kt",
    ).readText()

    @Test
    fun tvAuthViewModelsRejectDuplicateSubmitsWhileLoading() {
        assertTrue(loginViewModel.contains("if (_uiState.value.isLoading) return"))
        assertTrue(setupViewModel.contains("if (_uiState.value.isLoading) return"))
        assertTrue(signupViewModel.contains("if (_uiState.value.isLoading) return"))
    }

    @Test
    fun tvAuthButtonsExposeDisabledState() {
        assertTrue(auroraChrome.contains("enabled: Boolean = true"))
        assertTrue(auroraChrome.contains(".clickable(") && auroraChrome.contains("enabled = enabled"))
        assertTrue(heroActionPill.contains("enabled: Boolean = true"))
        assertTrue(heroActionPill.contains("Surface(") && heroActionPill.contains("enabled = enabled"))
    }

    @Test
    fun tvLoginUsesSingleWinnerGateForQrAndCredentialAuth() {
        assertTrue(loginViewModel.contains("private var credentialLoginJob"))
        assertTrue(loginViewModel.contains("private var authCompleted"))
        assertTrue(loginViewModel.contains("tryCompleteAuth()"))
    }
}
