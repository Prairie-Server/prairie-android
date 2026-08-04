package org.siloserver.silo.android.ui.components

import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class SiloKeyboardActionsTest {

    /** Records whether a field fell through to Compose's default handling. */
    private class RecordingScope : KeyboardActionScope {
        val defaulted = mutableListOf<ImeAction>()
        override fun defaultKeyboardAction(imeAction: ImeAction) {
            defaulted += imeAction
        }
    }

    @Test
    fun `a field with no callback installs no handler, leaving Compose's defaults`() {
        // The bug: an unconditional onAny took ownership of the event and ran a
        // no-op, so Next did nothing at all on every multi-field phone form.
        // Leaving the slots null is what hands the action back to Compose's
        // KeyboardActionRunner, which moves focus for Next. That traversal is
        // Compose's behavior, not this helper's, so it is not asserted here.
        val actions = siloKeyboardActions(onImeAction = null)

        assertSame(KeyboardActions.Default, actions)
        assertNull(actions.onNext)
        assertNull(actions.onDone)
        assertNull(actions.onGo)
    }

    @Test
    fun `a supplied callback is invoked exactly once`() {
        var invocations = 0
        val actions = siloKeyboardActions(onImeAction = { invocations++ })

        val onNext = assertNotNull(actions.onNext)
        val scope = RecordingScope()
        scope.onNext()

        assertEquals(1, invocations)
        // An explicit callback owns the event; it must not also fall through.
        assertEquals(emptyList(), scope.defaulted)
    }

    @Test
    fun `the callback owns every action the field can raise`() {
        // Go, Done and Search all route to the same supplied submit callback,
        // which is what the auth screens rely on for their final field.
        var invocations = 0
        val actions = siloKeyboardActions(onImeAction = { invocations++ })
        val scope = RecordingScope()

        // onAny fans out to every per-action slot, so no action can fall
        // through to a default the caller did not ask for.
        assertNotNull(actions.onGo).invoke(scope)
        assertNotNull(actions.onDone).invoke(scope)
        assertNotNull(actions.onSearch).invoke(scope)
        assertNotNull(actions.onSend).invoke(scope)
        assertNotNull(actions.onNext).invoke(scope)
        assertNotNull(actions.onPrevious).invoke(scope)

        assertEquals(6, invocations)
        assertEquals(emptyList(), scope.defaulted)
    }
}
