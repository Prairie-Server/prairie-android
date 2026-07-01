package org.siloserver.silo.tv.ui.screens.search

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvSearchLayoutReadabilityTest {
    private val source = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/search/TvSearchScreen.kt",
    ).readText()
    private val keyboardSource = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/search/TvSearchKeyboard.kt",
    ).takeIf { it.exists() }?.readText().orEmpty()

    @Test
    fun searchHeaderStartsBelowTopMenuChrome() {
        assertTrue(source.contains("top = TvTopMenuLayout.contentTopInset"))
    }

    @Test
    fun searchSupportsPredictableDpadHandoffFromFieldToResults() {
        assertTrue(source.contains(".focusProperties { down = firstFilterChipFocusRequester }"))
        assertTrue(source.contains("Modifier.focusProperties { down = firstResultFocusRequester }"))
        assertTrue(source.contains("up = firstFilterChipFocusRequester"))
        assertTrue(source.contains("firstResultFocusRequester.requestFocus()"))
        assertTrue(source.contains("var pendingSearchFocus by remember { mutableStateOf(false) }"))
        assertTrue(source.contains("LaunchedEffect(pendingSearchFocus, state.isLoading, state.items)"))
        assertTrue(source.contains("pendingSearchFocus = true"))
    }

    @Test
    fun searchUsesSiloOwnedKeyboardInsteadOfPlatformIme() {
        assertTrue(source.contains("SearchDisplayField("))
        assertTrue(source.contains("SiloSearchKeyboard("))
        assertFalse(source.contains("OutlinedTextField("))
        assertFalse(source.contains("KeyboardOptions("))
        assertFalse(source.contains("KeyboardActions("))
        assertFalse(source.contains("LocalSoftwareKeyboardController"))
    }

    @Test
    fun searchFieldKeepsDownForFocusNavigation() {
        assertFalse(keyboardSource.contains("Key.DirectionDown"))
    }

    @Test
    fun searchFieldOpensKeyboardOnlyFromActivationKeys() {
        val helper = keyboardSource
            .substringAfter("private fun shouldOpenSearchKeyboard")
            .substringBefore("private val tvSearchActivationKeyCodes")

        assertTrue(keyboardSource.contains("shouldOpenSearchKeyboard(event)"))
        assertTrue(helper.contains("if (event.type != KeyEventType.KeyDown) return false"))
        assertFalse(helper.contains("KeyEventType.KeyUp"))
        assertTrue(keyboardSource.contains("AndroidKeyEvent.KEYCODE_DPAD_CENTER"))
        assertTrue(keyboardSource.contains("AndroidKeyEvent.KEYCODE_ENTER"))
    }
}
