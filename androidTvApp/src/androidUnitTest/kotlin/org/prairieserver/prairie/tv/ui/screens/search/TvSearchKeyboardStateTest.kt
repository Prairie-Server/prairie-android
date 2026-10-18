package org.prairieserver.prairie.tv.ui.screens.search

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvSearchKeyboardStateTest {
    private val opened = TvSearchKeyboardState(keyboardOpen = true)

    @Test
    fun imeNotYetVisibleAfterSelectKeepsTheFieldOpen() {
        // show() is asynchronous: the first frames after Select still report
        // the IME hidden, and that must not close the field it just opened.
        assertEquals(opened, opened.onImeVisibilityChanged(imeVisible = false))
    }

    @Test
    fun imeDismissedByBackClosesTheField() {
        // Back while the IME is up is consumed by the IME, so the screen's
        // BackHandler never runs. The IME going away has to close the field,
        // or the editable field keeps swallowing D-pad Down.
        val shown = opened.onImeVisibilityChanged(imeVisible = true)
        assertTrue(shown.keyboardOpen)

        val dismissed = shown.onImeVisibilityChanged(imeVisible = false)
        assertFalse(dismissed.keyboardOpen)
        assertFalse(dismissed.imeSeen)
    }

    @Test
    fun reopeningAfterDismissWaitsForTheImeAgain() {
        val dismissed = opened
            .onImeVisibilityChanged(imeVisible = true)
            .onImeVisibilityChanged(imeVisible = false)
        val reopened = dismissed.copy(keyboardOpen = true)

        assertTrue(reopened.onImeVisibilityChanged(imeVisible = false).keyboardOpen)
    }

    @Test
    fun closedFieldForgetsTheIme() {
        val closed = TvSearchKeyboardState(keyboardOpen = false, imeSeen = true)

        assertEquals(TvSearchKeyboardState(), closed.onImeVisibilityChanged(imeVisible = true))
    }

    @Test
    fun searchScreenFollowsImeVisibilityForTheKeyboardFlag() {
        val screen = File(
            "src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/search/TvSearchScreen.kt",
        ).readText()

        assertTrue(screen.contains("val imeVisible = WindowInsets.isImeVisible"))
        assertTrue(screen.contains("keyboardState.onImeVisibilityChanged(imeVisible)"))
        // DOWN from the field still keys off the same flag.
        assertTrue(screen.contains("!isKeyboardOpen -> {"))
    }
}
