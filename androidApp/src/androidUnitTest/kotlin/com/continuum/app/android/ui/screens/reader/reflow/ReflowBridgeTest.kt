package com.continuum.app.android.ui.screens.reader.reflow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReflowBridgeTest {
    @Test fun `decodes paginated`() {
        val e = decodeReflowEvent("""{"type":"paginated","pageCount":12}""")
        assertEquals(ReflowEvent.Paginated(12), e)
    }
    @Test fun `decodes relocated`() {
        val e = decodeReflowEvent("""{"type":"relocated","page":3,"pageProgression":0.25}""")
        assertEquals(ReflowEvent.Relocated(3, 0.25), e)
    }
    @Test fun `unknown type decodes to null`() {
        assertEquals(null, decodeReflowEvent("""{"type":"nope"}"""))
        assertEquals(null, decodeReflowEvent("{garbage"))
    }
    @Test fun `encodes goToPage command`() {
        assertTrue(encodeReflowCommand(ReflowCommand.GoToPage(4)).contains("\"page\":4"))
    }
}
