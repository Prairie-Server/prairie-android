package org.prairieserver.prairie.android.ui.screens.reader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SerializedCloseableTest {

    private class FakeResource : AutoCloseable {
        @Volatile var closeCount = 0
        val closed: Boolean get() = closeCount > 0
        override fun close() { closeCount++ }
    }

    @Test
    fun `close waits for in-flight use instead of closing underneath it`(): Unit = runBlocking {
        val resource = FakeResource()
        val handle = SerializedCloseable(resource)
        val useStarted = CountDownLatch(1)
        val releaseUse = CountDownLatch(1)

        val user = launch(Dispatchers.Default) {
            handle.withResource {
                useStarted.countDown()
                releaseUse.await(5, TimeUnit.SECONDS)
                // The resource must still be open while a use holds the lock.
                assertFalse(resource.closed)
            }
        }
        assertTrue(useStarted.await(5, TimeUnit.SECONDS))

        val closer = launch(Dispatchers.Default) { handle.close() }
        releaseUse.countDown()
        user.join()
        closer.join()
        assertTrue(resource.closed)
    }

    @Test
    fun `use after close fails fast without touching the resource`(): Unit = runBlocking {
        val resource = FakeResource()
        val handle = SerializedCloseable(resource)
        handle.close()

        assertFailsWith<IllegalStateException> {
            handle.withResource { }
        }
    }

    @Test
    fun `double close only closes the resource once`(): Unit = runBlocking {
        val resource = FakeResource()
        val handle = SerializedCloseable(resource)
        handle.close()
        handle.close()
        assertEquals(1, resource.closeCount)
    }
}
