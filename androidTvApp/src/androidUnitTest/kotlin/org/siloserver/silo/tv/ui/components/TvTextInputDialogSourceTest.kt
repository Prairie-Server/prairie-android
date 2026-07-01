package org.siloserver.silo.tv.ui.components

import kotlin.test.Test
import kotlin.test.assertTrue

class TvTextInputDialogSourceTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/components/TvTextInputDialog.kt",
    ).readText()

    @Test
    fun textEntryDialogUsesPlatformEditTextForReliableTvImeInput() {
        assertTrue(
            source.contains("AndroidView("),
            "TV text-entry dialogs must host a platform view so Shield/Leanback IME receives a real editable target.",
        )
        assertTrue(
            source.contains("EditText(context)"),
            "TV text-entry dialogs must use a real EditText instead of a Compose TextField on Android TV.",
        )
        assertTrue(
            source.contains("InputMethodManager") &&
                source.contains("showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)"),
            "Opening the dialog must focus the platform EditText and explicitly show the TV keyboard.",
        )
        assertTrue(
            source.contains("tvInputTypeFor(keyboardType)"),
            "The dialog must map profile name/PIN keyboard types onto platform input types.",
        )
        assertTrue(
            source.contains("rememberUpdatedState(onDone)") &&
                source.contains("currentOnDone()"),
            "IME Done must use the latest typed value instead of the AndroidView factory's initial lambda.",
        )
        assertTrue(
            source.contains("TYPE_TEXT_FLAG_NO_SUGGESTIONS"),
            "Profile names should not be altered by TV keyboard suggestions/autocorrect.",
        )
        assertTrue(
            !source.contains("OutlinedTextField"),
            "Compose TextField focus reports as AndroidComposeView inputType=0 on Shield, so this dialog must not use it.",
        )
        assertTrue(
            !source.contains("Card(\n                onClick = {}"),
            "The dialog panel must not use a clickable TV Card that can steal initial text focus.",
        )
    }
}
