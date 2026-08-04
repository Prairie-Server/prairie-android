package org.siloserver.silo.tv.ui.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the disabled-control wiring rules that no unit test can reach without
 * a Compose harness: whether a disabled control leaves the focus graph.
 *
 * Matching is whitespace-tolerant on purpose — these assert the RULE, not a
 * formatting snapshot, so reformatting the sources does not fail the build.
 */
class TvDisabledControlWiringSourceTest {

    /**
     * Structurally unavailable controls hand `enabled` to the real interactive
     * primitive, so they drop out of D-pad traversal instead of sitting in it
     * as dead stops.
     */
    @Test
    fun structurallyDisabledControlsLeaveTheFocusGraph() {
        val structural = mapOf(
            "ui/components/TvOptionDialog.kt" to "enabled = enabled",
            "ui/components/TvAuroraChrome.kt" to "enabled = enabled",
            "ui/screens/settings/TvCardOverlaySettingsScreen.kt" to "enabled = enabled",
            "ui/components/TvAnchoredSelectorMenu.kt" to "enabled = interactive",
            "ui/components/TvSquaredButtons.kt" to "enabled = enabled",
        )
        structural.forEach { (path, wiring) ->
            assertTrue(
                source(path).containsLoosely(wiring),
                "$path should pass its disabled state to the interactive primitive",
            )
        }
    }

    /**
     * The inverse rule, and the one that actually strands viewers: a control
     * gated by work in flight must NOT leave the focus graph. Android TV does
     * not re-home focus when the focused node stops being focusable, and every
     * initial-focus policy here is one-shot.
     */
    @Test
    fun transientlyGatedControlsStayFocusable() {
        listOf(
            "ui/components/TvPinEntryDialog.kt",
            "ui/screens/watchtogether/TvJoinCodeDialog.kt",
            "ui/screens/admin/TvAdminScansScreen.kt",
        ).forEach { path ->
            val text = source(path)
            assertTrue(
                text.containsLoosely("TvControlState.transient("),
                "$path gates on work in flight and must stay focusable",
            )
            assertFalse(
                text.containsLoosely("enabled = enabled"),
                "$path must not hand in-flight gating to the focus graph",
            )
        }
    }

    /** The pre-existing anti-pattern: focusable, but silently inert. */
    @Test
    fun noControlFakesDisabledStateInsideItsClickHandler() {
        listOf(
            "ui/components/TvOptionDialog.kt",
            "ui/components/TvAuroraChrome.kt",
            "ui/components/TvPinEntryDialog.kt",
            "ui/screens/watchtogether/TvJoinCodeDialog.kt",
            "ui/screens/settings/TvCardOverlaySettingsScreen.kt",
            "ui/screens/admin/TvAdminScansScreen.kt",
        ).forEach { path ->
            assertFalse(
                source(path).containsLoosely("onClick = { if (enabled) onClick() }"),
                "$path should express disabled state, not swallow the click",
            )
        }
    }

    /**
     * Interactivity loss must collapse the dropdown in the SAME composition, not
     * a frame later via an effect — otherwise the menu is briefly drawn over a
     * trigger that has already left the focus graph.
     */
    @Test
    fun selectorCollapsesSynchronouslyAndClearsItsStoredExpansion() {
        val selectorMenu = source("ui/components/TvAnchoredSelectorMenu.kt")

        assertTrue(
            selectorMenu.containsLoosely(
                "val expanded = selectorExpansionAfterInteractivityChange(expansionRequested, interactive)",
            ),
            "expansion should be derived at read time",
        )
        assertTrue(
            selectorMenu.containsLoosely("LaunchedEffect(interactive) {"),
            "the stored expansion bit should still be cleared",
        )
    }

    private fun source(relativePath: String): String = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/$relativePath",
    ).readText()

    /** Compares ignoring all whitespace runs, so formatting never fails a rule. */
    private fun String.containsLoosely(needle: String): Boolean =
        collapseWhitespace().contains(needle.collapseWhitespace())

    private fun String.collapseWhitespace(): String = replace(WHITESPACE, " ").trim()

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
