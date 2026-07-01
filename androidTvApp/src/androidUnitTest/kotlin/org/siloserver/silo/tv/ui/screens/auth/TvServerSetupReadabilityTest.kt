package org.siloserver.silo.tv.ui.screens.auth

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvServerSetupReadabilityTest {
    private val source = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/auth/TvServerSetupScreen.kt",
    ).readText()
    private val keyboardSource = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/auth/TvServerUrlKeyboard.kt",
    ).readText()
    private val ansiKeyboardSource = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/components/TvAnsiKeyboard.kt",
    ).readText()

    @Test
    fun setupTextUsesBrightTvReadableColors() {
        assertTrue(source.contains("ServerUrlDisplayField("))
        assertTrue(source.contains("SiloServerUrlKeyboard("))
        assertTrue(keyboardSource.contains("Color.White.copy(alpha = if (value.isBlank()) 0.56f else 0.96f)"))
        assertTrue(keyboardSource.contains("color = Color.White.copy(alpha = if (isFocused) 0.92f else 0.26f)"))
        assertTrue(keyboardSource.contains("AuroraAccent.copy(alpha = 0.92f)"))
        assertTrue(ansiKeyboardSource.contains("Color.White.copy(alpha = 0.88f)"))
    }

    @Test
    fun setupTextUsesTenFootReadableSizes() {
        assertTrue(keyboardSource.contains("fontSize = 17.sp"))
        assertTrue(ansiKeyboardSource.contains("fontSize = when"))
        assertTrue(ansiKeyboardSource.contains("else -> 16.sp"))
        assertTrue(source.contains("fontSize = 16.sp"))
    }

    @Test
    fun phoneSetupCardUsesCompactLayoutThatDoesNotClipCopy() {
        assertTrue(source.contains("PHONE_SETUP_BEACON_SIZE"))
        assertTrue(source.contains("maxLines = 2"))
        assertTrue(source.contains("PhoneSetupBody("))
        assertTrue(source.contains("contentAlignment = Alignment.Center"))
        assertTrue(source.contains("horizontalAlignment = Alignment.CenterHorizontally"))
        assertTrue(source.contains("textAlign = TextAlign.Center"))
    }

    @Test
    fun phoneSetupPillIsCenteredAndUsesSetupLabel() {
        assertTrue(source.contains("text = \"SETUP WITH PHONE\""))
        assertTrue(source.contains(".align(Alignment.CenterHorizontally)"))
    }

    @Test
    fun serverUrlFieldKeepsDownForFocusNavigation() {
        assertFalse(keyboardSource.contains("Key.DirectionDown"))
    }

    @Test
    fun serverUrlFieldOpensKeyboardOnlyFromActivationKeys() {
        val helper = keyboardSource
            .substringAfter("private fun shouldOpenServerUrlKeyboard")
            .substringBefore("private val tvServerUrlActivationKeyCodes")

        assertTrue(keyboardSource.contains("shouldOpenServerUrlKeyboard(event)"))
        assertTrue(helper.contains("if (event.type != KeyEventType.KeyDown) return false"))
        assertFalse(helper.contains("KeyEventType.KeyUp"))
        assertTrue(keyboardSource.contains("AndroidKeyEvent.KEYCODE_DPAD_CENTER"))
        assertTrue(keyboardSource.contains("AndroidKeyEvent.KEYCODE_ENTER"))
    }

    @Test
    fun serverUrlKeyboardKeepsUrlProtocolShortcuts() {
        assertTrue(keyboardSource.contains("TvAnsiShortcutKey(\"https://\""))
        assertTrue(keyboardSource.contains("TvAnsiShortcutKey(\"http://\""))
        assertTrue(keyboardSource.contains("TvAnsiShortcutKey(\".com\""))
    }

    @Test
    fun serverUrlFieldInspectsRemoteKeysBeforeFocusableCanConsumeCenter() {
        val urlFieldBlock = keyboardSource
            .substringAfter("fun ServerUrlDisplayField(")
            .substringBefore("private fun shouldOpenServerUrlKeyboard")
        val keyHandlerIndex = urlFieldBlock.indexOf(".onPreviewKeyEvent { event ->")
        val focusableIndex = urlFieldBlock.indexOf(
            ".focusable(enabled = enabled, interactionSource = interactionSource)",
        )

        assertTrue(keyHandlerIndex in 0 until focusableIndex)
    }
}
