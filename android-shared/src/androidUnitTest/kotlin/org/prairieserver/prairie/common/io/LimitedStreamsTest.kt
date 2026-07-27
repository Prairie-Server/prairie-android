package org.prairieserver.prairie.common.io

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LimitedStreamsTest {
    @Test
    fun `copy accepts exactly the byte limit`() {
        val output = ByteArrayOutputStream()

        val copied = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4))
            .copyToLimited(output, maxBytes = 4)

        assertEquals(4, copied)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), output.toByteArray())
    }

    @Test
    fun `copy rejects limit plus one without writing the overflowing chunk`() {
        val output = ByteArrayOutputStream()

        val error = assertFailsWith<ContentLimitExceeded> {
            ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5))
                .copyToLimited(output, maxBytes = 4, bufferSize = 2)
        }

        assertEquals(4, error.maxBytes)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), output.toByteArray())
    }

    @Test
    fun `checked byte count rejects overflow instead of wrapping`() {
        assertFailsWith<ContentLimitExceeded> {
            checkedLimitedByteCount(
                currentBytes = Long.MAX_VALUE - 1,
                additionalBytes = 2,
                maxBytes = Long.MAX_VALUE,
            )
        }
    }

    @Test
    fun `negative limits are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ByteArrayInputStream(byteArrayOf()).copyToLimited(ByteArrayOutputStream(), -1)
        }
    }
}
