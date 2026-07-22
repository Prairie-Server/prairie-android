package org.siloserver.silo.common.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FinalPlaybackPositionWriterTest {
    @Test
    fun submitReturnsBeforeSuspendedWriteCompletes() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val writer = FinalPlaybackPositionWriter(backgroundScope) {
            started.complete(Unit)
            release.await()
        }

        assertTrue(writer.submit(FinalPlaybackPosition("movie", 7, 42.0, 100.0)))
        runCurrent()
        assertTrue(started.isCompleted)
        assertFalse(release.isCompleted)
        release.complete(Unit)
    }

    @Test
    fun invalidSnapshotsAreRejected() = runTest {
        val writer = FinalPlaybackPositionWriter(backgroundScope) { error("must not write") }

        assertFalse(writer.submit(FinalPlaybackPosition("", 7, 1.0, 2.0)))
        assertFalse(writer.submit(FinalPlaybackPosition("movie", 7, -1.0, 2.0)))
        assertFalse(writer.submit(FinalPlaybackPosition("movie", 7, Double.NaN, 2.0)))
        assertFalse(writer.submit(FinalPlaybackPosition("movie", 7, 1.0, Double.NaN)))
    }

    @Test
    fun pendingSnapshotsForSameFileCoalesceToNewestValue() = runTest {
        val written = mutableListOf<FinalPlaybackPosition>()
        val writer = FinalPlaybackPositionWriter(backgroundScope) { written += it }

        writer.submit(FinalPlaybackPosition("movie", 7, 10.0, 100.0))
        writer.submit(FinalPlaybackPosition("movie", 7, 20.0, 100.0))
        writer.submit(FinalPlaybackPosition("movie", 7, 30.0, 100.0))
        runCurrent()

        assertEquals(listOf(FinalPlaybackPosition("movie", 7, 30.0, 100.0)), written)
    }
}
