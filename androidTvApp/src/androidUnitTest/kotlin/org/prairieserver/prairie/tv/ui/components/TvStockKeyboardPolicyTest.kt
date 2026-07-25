package org.prairieserver.prairie.tv.ui.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

class TvStockKeyboardPolicyTest {
    @Test
    fun customTvKeyboardImplementationIsRemovedFromProductionSources() {
        val removedFiles = listOf(
            "src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvAnsiKeyboard.kt",
            "src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/auth/TvCredentialKeyboard.kt",
            "src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/auth/TvServerUrlKeyboard.kt",
            "src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/search/TvSearchKeyboard.kt",
        )

        removedFiles.forEach { path ->
            assertFalse(File(path).exists(), "$path should not remain as a production text-entry path")
        }
    }
}
