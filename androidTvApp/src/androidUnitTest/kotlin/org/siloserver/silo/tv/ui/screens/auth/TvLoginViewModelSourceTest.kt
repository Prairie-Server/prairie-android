package org.siloserver.silo.tv.ui.screens.auth

import kotlin.test.Test
import kotlin.test.assertFalse

class TvLoginViewModelSourceTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/auth/TvLoginViewModel.kt",
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
}
