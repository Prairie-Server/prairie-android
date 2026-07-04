package org.siloserver.silo.common.player

import androidx.media3.common.C
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaLoadRetryPolicyTest {

    @Test
    fun `gateway and missing-session statuses retry with exponential backoff`() {
        assertEquals(1_000L, siloMediaLoadRetryDelayMs(responseCode = 503, errorCount = 1))
        assertEquals(2_000L, siloMediaLoadRetryDelayMs(responseCode = 503, errorCount = 2))
        assertEquals(4_000L, siloMediaLoadRetryDelayMs(responseCode = 503, errorCount = 3))
        assertEquals(8_000L, siloMediaLoadRetryDelayMs(responseCode = 503, errorCount = 4))
        assertEquals(8_000L, siloMediaLoadRetryDelayMs(responseCode = 404, errorCount = 5))
        assertEquals(8_000L, siloMediaLoadRetryDelayMs(responseCode = 530, errorCount = 5))
    }

    @Test
    fun `retry-after header wins when it fits retry window`() {
        assertEquals(
            3_000L,
            siloMediaLoadRetryDelayMs(
                responseCode = 503,
                retryAfterHeaders = listOf("3"),
                errorCount = 1,
            ),
        )
    }

    @Test
    fun `retry-after beyond outage window stops retrying`() {
        assertEquals(
            C.TIME_UNSET,
            siloMediaLoadRetryDelayMs(
                responseCode = 503,
                retryAfterHeaders = listOf("120"),
                errorCount = 1,
            ),
        )
    }

    @Test
    fun `non transient response codes do not retry`() {
        assertEquals(C.TIME_UNSET, siloMediaLoadRetryDelayMs(responseCode = 401, errorCount = 1))
        assertEquals(C.TIME_UNSET, siloMediaLoadRetryDelayMs(responseCode = 418, errorCount = 1))
    }

    @Test
    fun `connection failures retry but generic IO does not`() {
        assertEquals(
            1_000L,
            siloMediaLoadRetryDelayMs(
                cause = SocketTimeoutException("timeout"),
                errorCount = 1,
            ),
        )
        assertEquals(
            1_000L,
            siloMediaLoadRetryDelayMs(
                cause = ConnectException("connection refused"),
                errorCount = 1,
            ),
        )
        assertEquals(
            C.TIME_UNSET,
            siloMediaLoadRetryDelayMs(
                cause = IOException("parser failed"),
                errorCount = 1,
            ),
        )
    }

    @Test
    fun `retry window eventually expires`() {
        assertEquals(C.TIME_UNSET, siloMediaLoadRetryDelayMs(responseCode = 503, errorCount = 20))
    }
}
