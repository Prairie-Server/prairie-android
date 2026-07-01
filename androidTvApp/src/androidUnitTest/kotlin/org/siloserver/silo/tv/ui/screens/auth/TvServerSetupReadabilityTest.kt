package org.siloserver.silo.tv.ui.screens.auth

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class TvServerSetupReadabilityTest {
    private val source = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/auth/TvServerSetupScreen.kt",
    ).readText()
    private val textFieldDefaults = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/components/TvTextFieldDefaults.kt",
    ).readText()

    @Test
    fun setupTextUsesBrightTvReadableColors() {
        assertTrue(source.contains("tvOutlinedTextFieldColors("))
        assertTrue(textFieldDefaults.contains("focusedTextColor = Color.White"))
        assertTrue(textFieldDefaults.contains("unfocusedTextColor = Color.White"))
        assertTrue(textFieldDefaults.contains("focusedLabelColor = Color.White"))
        assertTrue(textFieldDefaults.contains("unfocusedLabelColor = Color.White.copy(alpha = 0.78f)"))
        assertTrue(textFieldDefaults.contains("focusedPlaceholderColor = Color.White.copy(alpha = 0.64f)"))
        assertTrue(textFieldDefaults.contains("unfocusedPlaceholderColor = Color.White.copy(alpha = 0.56f)"))
    }

    @Test
    fun setupTextUsesTenFootReadableSizes() {
        assertTrue(source.contains("fontSize = 17.sp"))
        assertTrue(source.contains("fontSize = 16.sp"))
    }
}
