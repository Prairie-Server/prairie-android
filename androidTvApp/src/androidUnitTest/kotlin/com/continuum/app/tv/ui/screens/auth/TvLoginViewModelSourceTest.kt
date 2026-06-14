package com.continuum.app.tv.ui.screens.auth

import kotlin.test.Test
import kotlin.test.assertFalse

class TvLoginViewModelSourceTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/auth/TvLoginViewModel.kt",
    ).readText()

    @Test
    fun loginDoesNotWriteLegacyPlaintextAuthMirror() {
        assertFalse(
            source.contains("getSharedPreferences(\"continuum_auth\""),
            "TV login must not write the legacy plaintext auth preferences",
        )
        assertFalse(source.contains(".putString(\"accessToken\""))
        assertFalse(source.contains(".putString(\"refreshToken\""))
    }
}
