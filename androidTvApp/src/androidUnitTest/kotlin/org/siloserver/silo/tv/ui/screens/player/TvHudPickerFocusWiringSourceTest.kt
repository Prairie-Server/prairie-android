package org.siloserver.silo.tv.ui.screens.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class TvHudPickerFocusWiringSourceTest {
    private val source = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/player/TvPlayerHud.kt",
    ).readText()

    @Test
    fun focusedPickerRowsAreExplicitlyBroughtIntoView() {
        assertContains(source, "remember { BringIntoViewRequester() }")
        assertContains(source, ".bringIntoViewRequester(bringIntoViewRequester)")
        assertContains(source, "bringIntoViewRequester.bringIntoView()")
    }

    @Test
    fun pickerKeepsTheEagerFocusGraph() {
        val picker = source.substringAfter("internal fun HudPickerDialog")
            .substringBefore("private fun formatTime")
        assertContains(picker, ".verticalScroll(rememberScrollState())")
        assertFalse(picker.contains("LazyColumn"))
    }
}
