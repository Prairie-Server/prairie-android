package org.siloserver.silo.tv.ui.screens.search

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class TvSearchLayoutReadabilityTest {
    private val source = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/search/TvSearchScreen.kt",
    ).readText()

    @Test
    fun searchHeaderStartsBelowTopMenuChrome() {
        assertTrue(source.contains("top = TvTopMenuLayout.contentTopInset"))
    }

    @Test
    fun searchSupportsPredictableDpadHandoffFromFieldToResults() {
        assertTrue(source.contains(".focusProperties { down = firstFilterChipFocusRequester }"))
        assertTrue(source.contains("Modifier.focusProperties { down = firstResultFocusRequester }"))
        assertTrue(source.contains("up = firstFilterChipFocusRequester"))
        assertTrue(source.contains("keyboardController?.hide()"))
        assertTrue(source.contains("onResultsFocusRequested()"))
    }
}
