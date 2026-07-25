package org.prairieserver.prairie.tv.ui.components

import kotlin.test.Test
import kotlin.test.assertTrue

class TvTextInputDialogSourceTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvTextInputDialog.kt",
    ).readText()

    @Test
    fun textEntryDialogUsesPlatformImeInsteadOfPrairieAnsiKeyboard() {
        assertTrue(
            source.contains("OutlinedTextField("),
            "TV text-entry dialogs should use stock Compose text fields so the platform IME can provide Fire TV phone/QR input.",
        )
        assertTrue(
            source.contains("KeyboardOptions("),
            "Dialogs should configure the platform keyboard type/IME action.",
        )
        assertTrue(
            source.contains("KeyboardActions("),
            "The IME Done action should confirm when the dialog is valid.",
        )
        assertTrue(
            !source.contains("TvAnsiKeyboard(") && !source.contains("shouldOpenTvDialogKeyboard(event)"),
            "The dialog should not mount or reopen the Prairie-owned keyboard.",
        )
        assertTrue(
            !source.contains("AndroidView(") && !source.contains("EditText(context)") && !source.contains("InputMethodManager"),
            "Use Compose stock fields rather than manually bridging Android views.",
        )
        assertTrue(
            !source.contains("Card(\n                onClick = {}"),
            "The dialog panel must not use a clickable TV Card that can steal initial text focus.",
        )
    }
}
