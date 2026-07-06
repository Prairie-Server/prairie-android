package org.siloserver.silo.tv.ui.screens.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvSettingsUsabilityTest {
    private val source = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/settings/TvSettingsScreen.kt",
    ).readText()

    @Test
    fun settingsContentClearsTopMenuAndFocusedCardScale() {
        assertTrue(source.contains("top = TvTopMenuLayout.contentTopInset"))
        assertTrue(source.contains("start = 72.dp"))
        assertTrue(source.contains("end = 72.dp"))
    }

    @Test
    fun settingsUseTvOsSplitRailDetailLayout() {
        assertTrue(source.contains("SettingsRail("))
        assertTrue(source.contains("SettingsDetailPane("))
        assertTrue(source.contains("selectedCategory"))
        assertTrue(source.contains("enum class TvSettingsCategory"))
    }

    @Test
    fun settingsNoLongerCarryOldStackedSubScreens() {
        assertFalse(source.contains("private fun SettingsRootMenu("))
        assertFalse(source.contains("private fun TvPlaybackSettingsScreen("))
        assertFalse(source.contains("private fun TvSubtitleSettingsScreen("))
        assertFalse(source.contains("private fun TvSettingsSubScreenScaffold("))
    }

    @Test
    fun splitSettingsUseCompactTvDensity() {
        assertTrue(source.contains("modifier = Modifier.width(300.dp)"))
        assertTrue(source.contains("private val RowMaxWidth = 840.dp"))
        assertTrue(source.contains("private val RowHeight = 56.dp"))
        assertTrue(source.contains(".focusGroup()"))
    }
}
