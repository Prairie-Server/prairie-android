package org.prairieserver.prairie.common.diagnostics

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogRingTest {
    @Test
    fun snapshotIsNewestLastAndGenerationIsolated() {
        val ring = LogRing(capacity = 3, maxBytes = 256)
        val old = ring.currentGeneration
        ring.offer("old")

        val next = ring.rotateGeneration()
        ring.offer("one")
        ring.offer("two")

        assertEquals(listOf("one", "two"), ring.snapshot(next).lines)
        assertTrue(ring.snapshot(old).lines.isEmpty())
        assertEquals(next, ring.snapshot().generation)
    }

    @Test
    fun enforcesEntryAndUtf8ByteCapacityAndCountsDrops() {
        val ring = LogRing(capacity = 3, maxBytes = 10)

        ring.offer("aaaa")
        ring.offer("bbbb")
        ring.offer("cccc")

        val snapshot = ring.snapshot()
        assertEquals(listOf("bbbb", "cccc"), snapshot.lines)
        assertEquals(1, snapshot.droppedCount)
        assertTrue(snapshot.lines.sumOf { it.encodeToByteArray().size } <= 10)

        ring.offer("界".repeat(4))
        assertEquals(2, ring.snapshot().droppedCount)
        assertEquals(listOf("bbbb", "cccc"), ring.snapshot().lines)
    }

    @Test
    fun rejectsTornLinesAndClearKeepsTheCurrentGeneration() {
        val ring = LogRing(capacity = 3, maxBytes = 256)
        val generation = ring.currentGeneration
        ring.offer("")
        ring.offer("complete\npartial")
        ring.offer("complete")

        val beforeClear = ring.snapshot()
        assertEquals(listOf("complete"), beforeClear.lines)
        assertEquals(2, beforeClear.tornCount)

        ring.clear()
        assertEquals(generation, ring.currentGeneration)
        assertTrue(ring.snapshot().lines.isEmpty())
        assertEquals(0, ring.snapshot().droppedCount)
        assertEquals(0, ring.snapshot().tornCount)
    }

    @Test
    fun concurrentOffersPublishUniqueOrderedBoundedEntries() {
        val ring = LogRing(capacity = 4_000, maxBytes = 1_500_000)
        repeat(1_000) { ring.offer("warm-$it") }
        ring.clear()

        val workers = 4
        val offersPerWorker = 25_000
        val ready = CountDownLatch(workers)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(workers)
        repeat(workers) { worker ->
            executor.execute {
                ready.countDown()
                start.await()
                repeat(offersPerWorker) { index -> ring.offer("$worker-$index") }
            }
        }
        ready.await()
        start.countDown()
        executor.shutdown()
        assertTrue(executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS))

        val snapshot = ring.snapshot()
        assertEquals(4_000, snapshot.lines.size)
        assertEquals(snapshot.lines.size, snapshot.lines.toSet().size)
        assertTrue(snapshot.lines.sumOf { it.encodeToByteArray().size } <= 1_500_000)
        assertEquals((workers * offersPerWorker - 4_000).toLong(), snapshot.droppedCount)
    }

    @Test
    fun offerHotPathHasNoBlockingOrWriterLockPrimitive() {
        val source = File(
            "src/androidMain/kotlin/org/prairieserver/prairie/common/diagnostics/LogRing.kt",
        ).readText()
        val offerPath = source.substringAfter("override fun offer").substringBefore("override fun snapshot")

        listOf("synchronized", "Mutex", "runBlocking", ".send(", "Thread.sleep", "File(").forEach { forbidden ->
            assertTrue(forbidden !in offerPath, "offer path must not contain $forbidden")
        }
    }
}
