package org.prairieserver.prairie.common.downloads

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadWorkerLifetimeTest {

    @Test
    fun `cancellation waits for worker cleanup and blocks a late worker start`() = runTest {
        val id = "lifetime-${System.nanoTime()}"
        val lease = requireNotNull(DownloadWorkerLifetime.acquire(id))
        DownloadWorkerLifetime.beginCancellation(id)

        assertNull(DownloadWorkerLifetime.acquire(id))
        val idle = backgroundScope.async { DownloadWorkerLifetime.awaitIdle(id) }
        assertFalse(idle.isCompleted)

        lease.close()
        idle.await()
        assertTrue(idle.isCompleted)
        assertNull(DownloadWorkerLifetime.acquire(id))
    }
}
