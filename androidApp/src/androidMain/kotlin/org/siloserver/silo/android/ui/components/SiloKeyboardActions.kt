package org.siloserver.silo.android.ui.components

import androidx.compose.foundation.text.KeyboardActions

/**
 * Keyboard actions for a Silo phone text field.
 *
 * Installing `KeyboardActions(onAny = ...)` takes ownership of the IME event.
 * The shared field families did that unconditionally against a no-op default
 * callback, which silently replaced Compose's default handling with nothing:
 * pressing Next on Login, Signup, Setup, Create Profile or Edit Profile ran an
 * empty lambda and left the cursor where it was, so every multi-field form
 * needed a tap to advance.
 *
 * With a callback supplied the field keeps its explicit behavior and invokes it
 * once. With none, the platform defaults apply: Next and Previous move focus,
 * Done closes the IME, and Go, Search and Send do nothing.
 */
fun siloKeyboardActions(onImeAction: (() -> Unit)?): KeyboardActions =
    if (onImeAction == null) {
        KeyboardActions.Default
    } else {
        KeyboardActions(onAny = { onImeAction() })
    }
