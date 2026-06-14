package com.continuum.app.tv.ui.screens.profiles

import kotlin.test.Test
import kotlin.test.assertFalse

class TvProfileSelectionViewModelSourceTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/profiles/TvProfileSelectionViewModel.kt",
    ).readText()

    @Test
    fun profileSelectionDoesNotWriteLegacyPlaintextProfileMirror() {
        assertFalse(
            source.contains("getSharedPreferences(\"continuum_auth\""),
            "TV profile selection must rely on ProfileRepository storage, not legacy plaintext prefs",
        )
        assertFalse(source.contains(".putString(\"profileId\""))
    }
}
