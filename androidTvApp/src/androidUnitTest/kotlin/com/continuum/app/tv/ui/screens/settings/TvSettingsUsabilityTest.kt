package com.continuum.app.tv.ui.screens.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class TvSettingsUsabilityTest {
    private val source = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/settings/TvSettingsScreen.kt",
    ).readText()

    @Test
    fun settingsContentClearsTopMenuAndFocusedCardScale() {
        assertTrue(source.contains("top = TvTopMenuLayout.contentTopInset"))
        assertTrue(source.contains("start = 72.dp"))
        assertTrue(source.contains("end = 72.dp"))
    }
}
