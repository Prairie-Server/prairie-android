package org.siloserver.silo.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * Dismiss the stock TV keyboard when the caller leaves composition.
 *
 * Android TV does not take the IME down on its own when the surface that
 * raised it goes away, so without this the keyboard floats over whatever screen
 * comes next — with the D-pad still feeding it rather than the content behind.
 *
 * Every surface that calls `show()` needs this, which is why it is a shared
 * composable rather than a comment: the two implementations that copied the
 * focus-and-show half of the policy both omitted the disposal half.
 * `TvStockKeyboardPolicyTest` pins that pairing.
 *
 * Callers that can be covered by the IME also need `Modifier.imePadding()` on
 * their container; that cannot be enforced from here.
 */
@Composable
internal fun TvHideStockImeOnDispose() {
    val keyboardController = LocalSoftwareKeyboardController.current
    DisposableEffect(keyboardController) {
        onDispose { runCatching { keyboardController?.hide() } }
    }
}
