package org.prairieserver.prairie.tv.ui.screens.requests

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A focused pill leaving the request detail's action row (Cancel after a
 * cancel, Notify me once the title is requestable) must hand focus back to the
 * primary action, and nothing else may trigger that.
 */
class TvRequestActionFocusFallbackTest {

    @Test
    fun aFocusedPillLeavingTheRowReclaimsFocusEvenWhenComposeMovedItToTheSynopsis() {
        // Compose reports the loss first: focus already moved to the synopsis,
        // so the page still has focus. The reclaim must run anyway.
        val fallback = RequestActionFocusFallback()
        fallback.onRowFocusChanged(true)
        fallback.onRowFocusChanged(false)
        fallback.onPillRemoved()
        assertTrue(fallback.takeShouldReclaim())
        // The signals are spent.
        assertFalse(fallback.takeShouldReclaim())
    }

    @Test
    fun aPillDisposedWhileTheRowStillHasFocusReclaims() {
        // The pill is disposed before the row hears about the focus change.
        val fallback = RequestActionFocusFallback()
        fallback.onRowFocusChanged(true)
        fallback.onPillRemoved()
        fallback.onRowFocusChanged(false)
        assertTrue(fallback.takeShouldReclaim())
        assertFalse(fallback.takeShouldReclaim())
    }

    @Test
    fun movingOutOfTheRowOrAnUnfocusedPillLeavingDoesNot() {
        val fallback = RequestActionFocusFallback()
        // D-pad up to the synopsis or the top bar: the row loses focus, no pill goes.
        fallback.onRowFocusChanged(true)
        fallback.onRowFocusChanged(false)
        assertFalse(fallback.takeShouldReclaim())
        // A pill leaves later while focus is outside the row.
        fallback.onPillRemoved()
        assertFalse(fallback.takeShouldReclaim())
        // The first focus report on attach is not a loss.
        fallback.onRowFocusChanged(false)
        fallback.onPillRemoved()
        assertFalse(fallback.takeShouldReclaim())
    }

    @Test
    fun theDecisionNeedsARemovedPillThatHeldRowFocus() {
        assertTrue(shouldReclaimRequestActionFocus(pillRemoved = true, rowFocusedAtRemoval = true, rowLostFocus = false))
        assertTrue(shouldReclaimRequestActionFocus(pillRemoved = true, rowFocusedAtRemoval = false, rowLostFocus = true))
        assertFalse(shouldReclaimRequestActionFocus(pillRemoved = true, rowFocusedAtRemoval = false, rowLostFocus = false))
        assertFalse(shouldReclaimRequestActionFocus(pillRemoved = false, rowFocusedAtRemoval = false, rowLostFocus = true))
    }

    @Test
    fun theScreenWiresTheFallbackToTheRowAndThePills() {
        val source = File(SCREEN).readText()
        assertTrue(source.contains("onFocusChanged { actionFocusFallback.onRowFocusChanged(it.hasFocus) }"))
        assertTrue(source.contains("onDispose { focusFallback?.onPillRemoved() }"))
        assertTrue(source.contains("requestFocus = primaryActionFocusRequester::requestFocus"))
        // The page keeping focus (on the synopsis) must not block the reclaim.
        assertTrue(source.contains("if (!actionFocusFallback.takeShouldReclaim()) return@LaunchedEffect"))
        // Notify me keeps one node across toggles, so focus stays on it.
        assertTrue(source.contains("key(\"follow\")"))
    }

    private companion object {
        const val SCREEN = "src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/requests/TvRequestDetailScreen.kt"
    }
}
