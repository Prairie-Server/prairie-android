package org.prairieserver.prairie.common.downloads

import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertIs<IllegalStateException>(downloadHttpStatusFailure(HttpStatusCode.Forbidden))
        assertIs<IllegalStateException>(downloadHttpStatusFailure(HttpStatusCode.Gone))
    }

    @Test
    fun `transient client statuses are retryable io failures`() {
        // Matches SyncEngine's transient classification: 401/408/429.
        assertIs<IOException>(downloadHttpStatusFailure(HttpStatusCode.Unauthorized))
        assertIs<IOException>(downloadHttpStatusFailure(HttpStatusCode.RequestTimeout))
        assertIs<IOException>(downloadHttpStatusFailure(HttpStatusCode.TooManyRequests))
    }

    @Test
    fun `server error download status is retryable io failure`() {
        assertIs<IOException>(downloadHttpStatusFailure(HttpStatusCode.ServiceUnavailable))
    }

    // ── 409 preparing detection (issue #20 GAP 1) ────────────────────────────
    // A 409 with error `download_inactive` means the remux/transcode artifact is
    // still preparing — the worker treats that as retriable (wait for `ready`)
    // rather than deleting the download. Other 409s stay fatal via
    // downloadHttpStatusFailure (Conflict is not in its retriable set).

    @Test
    fun `extractDownloadErrorCode reads the flat error envelope`() {
        assertEquals(
            DOWNLOAD_INACTIVE_ERROR,
            extractDownloadErrorCode("""{"error":"download_inactive","message":"still preparing"}"""),
        )
        // Whitespace / key ordering variations still parse.
        assertEquals(
            "download_inactive",
            extractDownloadErrorCode("""{ "message":"x", "error" : "download_inactive" }"""),
        )
    }

    @Test
    fun `extractDownloadErrorCode returns null when no error code present`() {
        assertNull(extractDownloadErrorCode("""{"message":"nope"}"""))
        assertNull(extractDownloadErrorCode(""))
        assertNull(extractDownloadErrorCode("not json"))
    }

    @Test
    fun `a non-preparing conflict is not the download_inactive code`() {
        // A different 409 error code must not be mistaken for "still preparing".
        assertEquals("revoked", extractDownloadErrorCode("""{"error":"revoked"}"""))
        // And a bare Conflict without the preparing code is still fatal.
        assertIs<IllegalStateException>(downloadHttpStatusFailure(HttpStatusCode.Conflict))
    }
}
