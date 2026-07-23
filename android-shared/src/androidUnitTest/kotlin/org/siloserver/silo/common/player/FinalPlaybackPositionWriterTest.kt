package org.siloserver.silo.common.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.siloserver.silo.network.AuthScopeSnapshot
import org.siloserver.silo.repository.port.PlaybackWriteScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FinalPlaybackPositionWriterTest {
    private val scopeA = PlaybackWriteScope("server", "profile-a", null, 4L)
    private val scopeB = PlaybackWriteScope("server", "profile-b", null, 5L)

    @Test
    fun submitReturnsBeforeSuspendedWriteCompletes() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val writer = FinalPlaybackPositionWriter(
            scope = backgroundScope,
            scopeProvider = { null },
            write = {
                started.complete(Unit)
                release.await()
            },
        )

        assertTrue(writer.submit(FinalPlaybackPosition(scopeA, "movie", 7, 42.0, 100.0)))
        runCurrent()
        assertTrue(started.isCompleted)
        assertFalse(release.isCompleted)
        release.complete(Unit)
    }

    @Test
    fun invalidSnapshotsAreRejected() = runTest {
        val writer = FinalPlaybackPositionWriter(
            scope = backgroundScope,
            scopeProvider = { null },
            write = { error("must not write") },
        )

        assertFalse(writer.submit(FinalPlaybackPosition(scopeA, "", 7, 1.0, 2.0)))
        assertFalse(writer.submit(FinalPlaybackPosition(scopeA, "movie", 7, -1.0, 2.0)))
        assertFalse(writer.submit(FinalPlaybackPosition(scopeA, "movie", 7, Double.NaN, 2.0)))
        assertFalse(writer.submit(FinalPlaybackPosition(scopeA, "movie", 7, 1.0, Double.NaN)))
    }

    @Test
    fun pendingSnapshotsForSameFileCoalesceToNewestValue() = runTest {
        val written = mutableListOf<FinalPlaybackPosition>()
        val writer = FinalPlaybackPositionWriter(
            scope = backgroundScope,
            scopeProvider = { null },
            write = { written += it },
        )

        writer.submit(FinalPlaybackPosition(scopeA, "movie", 7, 10.0, 100.0))
        writer.submit(FinalPlaybackPosition(scopeA, "movie", 7, 20.0, 100.0))
        writer.submit(FinalPlaybackPosition(scopeA, "movie", 7, 30.0, 100.0))
        runCurrent()

        assertEquals(listOf(FinalPlaybackPosition(scopeA, "movie", 7, 30.0, 100.0)), written)
    }

    @Test
    fun pendingSnapshotsFromDifferentProfilesNeverCoalesce() = runTest {
        val written = mutableListOf<FinalPlaybackPosition>()
        val writer = FinalPlaybackPositionWriter(
            scope = backgroundScope,
            scopeProvider = { null },
            write = { written += it },
        )

        writer.submit(FinalPlaybackPosition(scopeA, "movie", 7, 10.0, 100.0))
        writer.submit(FinalPlaybackPosition(scopeB, "movie", 7, 20.0, 100.0))
        runCurrent()

        assertEquals(listOf(scopeA, scopeB), written.map { it.scope })
    }

    @Test
    fun playbackScopeContainsIdentifiersOnly() {
        assertEquals(
            setOf("serverId", "profileId", "credentialGenerationId", "identityGeneration"),
            PlaybackWriteScope::class.java.declaredFields
                .filterNot { it.isSynthetic }
                .map { it.name }
                .toSet(),
        )
    }

    @Test
    fun captureScopeCopiesOnlyIdentityFields() = runTest {
        val writer = FinalPlaybackPositionWriter(
            scope = backgroundScope,
            scopeProvider = {
                AuthScopeSnapshot(
                    serverId = "server",
                    profileId = "profile-a",
                    serverUrl = "https://server.example",
                    profileToken = "secret-profile-token",
                    credentialGenerationId = "credential",
                    identityGeneration = 4L,
                )
            },
            write = { error("must not write") },
        )

        assertEquals(
            PlaybackWriteScope("server", "profile-a", "credential", 4L),
            writer.captureScope(),
        )
    }

    @Test
    fun captureScopeRejectsMissingProfile() = runTest {
        val writer = FinalPlaybackPositionWriter(
            scope = backgroundScope,
            scopeProvider = {
                AuthScopeSnapshot(
                    serverId = "server",
                    profileId = null,
                    serverUrl = "https://server.example",
                    profileToken = null,
                    identityGeneration = 4L,
                )
            },
            write = { error("must not write") },
        )

        assertNull(writer.captureScope())
    }
}
