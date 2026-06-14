package com.continuum.app.android.ui.screens.reader.reflow

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReflowInitialRelocationGateTest {
    @Test
    fun `initial page zero relocation is suppressed until saved target page arrives`() {
        val gate = ReflowInitialRelocationGate(initialPageProgression = 0.5)
        gate.onPaginated(pageCount = 11)

        assertFalse(gate.shouldPersistRelocation(page = 0))
        assertTrue(gate.shouldPersistRelocation(page = 5))
        assertTrue(gate.shouldPersistRelocation(page = 6))
    }

    @Test
    fun `no saved progression persists relocations immediately`() {
        val gate = ReflowInitialRelocationGate(initialPageProgression = 0.0)
        gate.onPaginated(pageCount = 11)

        assertTrue(gate.shouldPersistRelocation(page = 0))
    }
}
