package org.siloserver.silo.tv.ui.screens.requests

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class TvRequestsUsabilityTest {
    private val source = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/requests/TvRequestsScreen.kt",
    ).readText()

    @Test
    fun requestsHeaderStaysBelowTopMenuChrome() {
        assertTrue(source.contains("top = TvTopMenuLayout.contentTopInset"))
    }

    @Test
    fun requestsSupportsPredictableDpadHandoffFromFieldToResults() {
        assertTrue(source.contains(".focusProperties { down = firstFilterChipFocusRequester }"))
        assertTrue(source.contains("if (index == 0 && hasFocusableResult)"))
        assertTrue(source.contains("Modifier.focusProperties { down = firstResultFocusRequester }"))
        assertTrue(source.contains("keyboardController?.hide()"))
    }
}
