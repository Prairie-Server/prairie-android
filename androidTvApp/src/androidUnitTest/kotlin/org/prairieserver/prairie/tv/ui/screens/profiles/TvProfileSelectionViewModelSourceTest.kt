package org.prairieserver.prairie.tv.ui.screens.profiles

import kotlin.test.Test
import kotlin.test.assertFalse

class TvProfileSelectionViewModelSourceTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/profiles/TvProfileSelectionViewModel.kt",
    ).readText()

    @Test
    fun profileSelectionDoesNotWriteLegacyPlaintextProfileMirror() {
        assertFalse(
            source.contains("getSharedPreferences(\"prairie_auth\""),
            "TV profile selection must rely on ProfileRepository storage, not legacy plaintext prefs",
        )
        assertFalse(source.contains(".putString(\"profileId\""))
    }
}
