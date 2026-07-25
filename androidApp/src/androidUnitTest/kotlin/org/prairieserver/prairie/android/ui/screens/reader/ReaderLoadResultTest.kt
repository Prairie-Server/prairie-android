package org.prairieserver.prairie.android.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReaderLoadResultTest {

    @Test
    fun `reader result captures thrown load failures`() {
        val result = readerLoadResult<String> { error("bad pdf") }

        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertEquals("bad pdf", result.exceptionOrNull()?.message)
    }

    @Test
    fun `required reader result treats null as a load failure`() {
        val result = requiredReaderLoadResult("Missing comic page") { null }

        assertTrue(result.isFailure)
        assertEquals("Missing comic page", result.exceptionOrNull()?.message)
    }

    @Test
    fun `required reader result returns non null values`() {
        val result = requiredReaderLoadResult("Missing value") { "loaded" }

        assertEquals("loaded", result.getOrThrow())
    }

    @Test
    fun `reader result captures OutOfMemoryError as failure`() {
        val result = readerLoadResult<String> { throw OutOfMemoryError("page too big") }
        assertTrue(result.isFailure)
        assertIs<OutOfMemoryError>(result.exceptionOrNull())
    }
}
