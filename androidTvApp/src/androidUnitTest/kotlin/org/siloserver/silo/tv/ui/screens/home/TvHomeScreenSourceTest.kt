package org.siloserver.silo.tv.ui.screens.home

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class TvHomeScreenSourceTest {
    private val source = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/home/TvHomeScreen.kt",
    ).readText()

    @Test
    fun homeUsesSharedSkylineFeedInsteadOfOwningItsOwnRowBand() {
        assertTrue(source.contains("TvSkylineSectionFeed("))
        assertTrue(source.contains("cardActions = { section, item ->"))
        assertTrue(source.contains("iconForSection = { section ->"))
        assertTrue(source.contains("onSeeAllClickForSection = { section ->"))
    }
}
