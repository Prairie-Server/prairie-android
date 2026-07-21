package org.siloserver.silo.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay

/**
 * Retry-until-focused initial focus for popup overlays.
 *
 * A Popup window's focus lags composition on TV (Shield-class devices), so a
 * single delayed `requestFocus()` often fires before the window is focusable
 * and silently no-ops — the overlay opens with NOTHING focused and the D-pad
 * is dead (issue #64's root cause, originally fixed only in the PIN keypad).
 * This keeps requesting [target] until anything inside the overlay holds
 * focus, then stops so it never fights the user's navigation (including a
 * user who reached a different control before the first grab landed).
 *
 * Attach the returned [Modifier] to the overlay's content root:
 * `Column(modifier = rememberTvDialogInitialFocus(firstRowFocus)) { ... }`.
 */
@Composable
internal fun rememberTvDialogInitialFocus(target: FocusRequester): Modifier {
    var overlayHasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (!overlayHasFocus) {
            runCatching { target.requestFocus() }
            delay(60)
        }
    }
    return Modifier.onFocusChanged { overlayHasFocus = it.hasFocus }
}
