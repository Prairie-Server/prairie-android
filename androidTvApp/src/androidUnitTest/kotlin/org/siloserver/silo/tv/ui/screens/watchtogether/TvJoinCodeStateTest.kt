package org.siloserver.silo.tv.ui.screens.watchtogether

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvJoinCodeStateTest {
    @Test fun appendsUppercaseAndCapsAtEight() {
        var s = JoinCodeState(); "abcd1234ef".forEach { s = s.append(it) }
        assertEquals("ABCD1234", s.code); assertTrue(s.isComplete)
    }
    @Test fun rejectsNonAlphanumeric() {
        val s = JoinCodeState().append('-').append(' ').append('A')
        assertEquals("A", s.code); assertFalse(s.isComplete)
    }
    @Test fun backspaceRemovesLast() {
        assertEquals("A", JoinCodeState().append('A').append('B').backspace().code)
    }
    @Test fun clearEmpties() {
        assertEquals("", JoinCodeState().append('A').append('B').clear().code)
    }
    @Test fun incompleteUntilEight() {
        assertFalse(JoinCodeState().append('A').isComplete)
        var t = JoinCodeState(); "ABCD1234".forEach { t = t.append(it) }; assertTrue(t.isComplete)
    }
}
