package org.siloserver.silo.tv.ui.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvAuroraPrimaryButtonFocusTest {
    private val source = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/components/TvAuroraChrome.kt",
    ).readText()

    @Test
    fun primaryButtonStaysFocusableWhenActionIsTemporarilyDisabled() {
        val buttonBlock = source
            .substringAfter("fun AuroraPrimaryButton(")
            .substringBefore("/**\n * Numbered onboarding step")

        assertTrue(buttonBlock.contains("enabled = true"))
        assertTrue(buttonBlock.contains("if (enabled) onClick()"))
        assertFalse(buttonBlock.contains("enabled = enabled"))
    }
}
