package org.siloserver.silo.tv.ui.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class TvDisabledControlWiringSourceTest {
    @Test
    fun disabledStateReachesEveryInteractivePrimitive() {
        val optionDialog = source("ui/components/TvOptionDialog.kt")
        assertContains(optionDialog, "onClick = onClick,\n        enabled = enabled,")

        val aurora = source("ui/components/TvAuroraChrome.kt")
        assertContains(aurora, "enabled = enabled,\n                onClick = onClick,")

        val pin = source("ui/components/TvPinEntryDialog.kt")
        assertContains(pin, "enabled: Boolean,\n    onClick: () -> Unit,")
        assertContains(pin, "onClick = { onDigitPressed")
        assertContains(pin, "onClick = onBackspacePressed")
        assertContains(pin, "enabled = enabled,")

        val join = source("ui/screens/watchtogether/TvJoinCodeDialog.kt")
        assertContains(join, "onClick = onClick,\n        enabled = enabled,")

        val overlays = source("ui/screens/settings/TvCardOverlaySettingsScreen.kt")
        assertContains(overlays, "onClick = onClick,\n        enabled = enabled,")

        val scans = source("ui/screens/admin/TvAdminScansScreen.kt")
        assertContains(scans, "onClick = onClick,\n        enabled = enabled,")

        listOf(optionDialog, aurora, pin, join, overlays, scans).forEach { text ->
            assertFalse(text.contains("onClick = { if (enabled) onClick() }"))
        }
    }

    @Test
    fun actionCardRetainsCardColorsAndFocusBorder() {
        val scans = source("ui/screens/admin/TvAdminScansScreen.kt")

        assertContains(scans, "containerColor = MaterialTheme.colorScheme.surfaceVariant,")
        assertContains(scans, "focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,")
        assertContains(scans, "pressedContainerColor = MaterialTheme.colorScheme.surfaceVariant,")
        assertContains(scans, "disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,")
        assertContains(scans, "border = Border.None,")
        assertContains(scans, "border = BorderStroke(3.dp, MaterialTheme.colorScheme.border),")
        assertContains(scans, "pressedBorder = focusedBorder,")
    }

    @Test
    fun pinKeyRetainsItsCustomColorsWhenDisabled() {
        val pin = source("ui/components/TvPinEntryDialog.kt")

        assertContains(pin, "disabledContainerColor = Color.White.copy(alpha = 0.10f),")
        assertContains(pin, "disabledContentColor = SiloOnSurface,")
    }

    @Test
    fun noninteractiveSelectorDisablesItsFocusableTrigger() {
        val selectorMenu = source("ui/components/TvAnchoredSelectorMenu.kt")
        val squaredPills = source("ui/components/TvSquaredButtons.kt")

        assertContains(selectorMenu, "enabled = interactive,")
        assertContains(squaredPills, "enabled: Boolean = true,")
        assertContains(squaredPills, ".clickable(\n                enabled = enabled,")
    }

    @Test
    fun selectorAppliesInteractivityChangesToStoredExpansion() {
        val selectorMenu = source("ui/components/TvAnchoredSelectorMenu.kt")

        assertContains(selectorMenu, "LaunchedEffect(interactive) {")
        assertContains(
            selectorMenu,
            "expanded = selectorExpansionAfterInteractivityChange(expanded, interactive)",
        )
    }

    private fun source(relativePath: String): String = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/$relativePath",
    ).readText()
}
