package org.prairieserver.prairie.common.diagnostics

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsFileLoggerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun writesJsonLinesUnderNoBackupAndFreezeRetainsAStableSnapshot() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val noBackup = temporaryFolder.newFolder("no-backup")
        val logger = DiagnosticsFileLogger(noBackup, writerDispatcher = dispatcher)

        logger.start(generation = 7)
        logger.offer("one")
        logger.offer("two")
        advanceUntilIdle()
        val frozen = logger.freeze(expectedGeneration = 7)
        logger.offer("ignored-after-freeze")

        assertEquals(7, frozen.generation)
        assertEquals(0, frozen.droppedCount)
        assertEquals(listOf("one", "two"), frozen.files.flatMap { it.readLines() })
        assertTrue(frozen.files.all { it.toPath().startsWith(noBackup.toPath()) })
        assertTrue(frozen.files.all { it.name.endsWith(".jsonl") })
        assertFalse(frozen.files.any { it.readText().contains("ignored-after-freeze") })
    }

    @Test
    fun rotatesAppendOnlySegmentsAndKeepsOnlyTheNewestBoundedSet() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val logger = DiagnosticsFileLogger(
            noBackupFilesDir = temporaryFolder.newFolder("segments"),
            writerDispatcher = dispatcher,
            channelCapacity = 16,
            maxSegments = 2,
            maxSegmentBytes = 14,
        )

        logger.start(generation = 9)
        repeat(5) { logger.offer("line-$it") }
        advanceUntilIdle()
        val frozen = logger.freeze(expectedGeneration = 9)

        assertEquals(2, frozen.files.size)
        assertEquals(listOf("line-2", "line-3", "line-4"), frozen.files.flatMap { it.readLines() })
        assertTrue(frozen.files.all { it.length() <= 14 })
        assertEquals(frozen.files.sumOf { it.length() }, frozen.bytes)
    }

    @Test
    fun channelOverflowDropsOldestWithoutBlockingCaller() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val logger = DiagnosticsFileLogger(
            noBackupFilesDir = temporaryFolder.newFolder("overflow"),
            writerDispatcher = dispatcher,
            channelCapacity = 2,
            maxSegments = 5,
            maxSegmentBytes = 1_024,
        )

        logger.start(generation = 11)
        repeat(5) { logger.offer("line-$it") }
        advanceUntilIdle()
        val frozen = logger.freeze(expectedGeneration = 11)

        assertEquals(listOf("line-3", "line-4"), frozen.files.flatMap { it.readLines() })
        assertEquals(3, frozen.droppedCount)
    }

    @Test
    fun oversizedLinesAreDroppedAndCancelPurgesOnlyActiveGeneration() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val noBackup = temporaryFolder.newFolder("cancel")
        val logger = DiagnosticsFileLogger(
            noBackupFilesDir = noBackup,
            writerDispatcher = dispatcher,
            maxSegmentBytes = 8,
        )

        logger.start(generation = 13)
        logger.offer("this-line-is-too-large")
        logger.offer("small")
        advanceUntilIdle()
        logger.cancel(expectedGeneration = 13)

        assertFalse(noBackup.resolve("client-diagnostics/logs/generation-13").exists())
        assertFalse(logger.isActive)
    }
}
