package org.prairieserver.prairie.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class ApiResultHelpersTest {

    @Test
    fun getOrNullAndGetOrThrowAndMapAndErrorMessage() = runTest {
        val ok = ApiResult.Success(7)
        assertEquals(7, ok.getOrNull())
        assertEquals(7, ok.getOrThrow())
        assertEquals(14, (ok.map { it * 2 } as ApiResult.Success).data)
        assertEquals("fallback", ok.errorMessage("fallback"))

        val err = ApiResult.Error(400, "bad", "nope")
        assertNull(err.getOrNull())
        assertFailsWith<RuntimeException> { err.getOrThrow() }
        assertEquals(err, err.map { it })
        assertEquals("nope", err.errorMessage("fallback"))
        assertEquals("fallback", ApiResult.Error(400, "bad", "").errorMessage("fallback"))

        val net = ApiResult.NetworkError(IllegalStateException("boom"))
        assertNull(net.getOrNull())
        assertFailsWith<IllegalStateException> { net.getOrThrow() }
        assertEquals(net, net.map { it })
        assertEquals(NETWORK_ERROR_MESSAGE, net.errorMessage("fallback"))
    }
}
