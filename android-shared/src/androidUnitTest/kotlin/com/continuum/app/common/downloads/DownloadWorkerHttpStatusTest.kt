package com.continuum.app.common.downloads

import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull

class DownloadWorkerHttpStatusTest {
    @Test
    fun `successful download status has no failure`() {
        assertNull(downloadHttpStatusFailure(HttpStatusCode.OK))
        assertNull(downloadHttpStatusFailure(HttpStatusCode.PartialContent))
    }

    @Test
    fun `client error download status is permanent failure`() {
        assertIs<IllegalStateException>(downloadHttpStatusFailure(HttpStatusCode.NotFound))
    }

    @Test
    fun `server error download status is retryable io failure`() {
        assertIs<IOException>(downloadHttpStatusFailure(HttpStatusCode.ServiceUnavailable))
    }
}
