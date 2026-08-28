package org.prairieserver.prairie.tv.ui.screens.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.prairieserver.prairie.model.playback.CommittedSubtitle
import org.prairieserver.prairie.model.playback.PlaybackSubtitleModeV3
import org.prairieserver.prairie.model.playback.SubtitleIdentity
import org.prairieserver.prairie.network.ApiResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TvSubtitleFinalRollbackTest {
    @Test
    fun `ordinary sidecar keeps A committed until backend acknowledgement succeeds`() = runTest {
        val harness = harness(backgroundScope)

        harness.adapter.select(sidecarB)
        runCurrent()
        harness.port.completeStage(sidecarCandidate())
        runCurrent()

        assertEquals(sidecarA, harness.adapter.snapshot.committedIdentity)
        assertEquals(sidecarB, harness.adapter.snapshot.pendingIdentity)
        assertEquals(sidecarB, harness.adapter.snapshot.localMountIdentity)
        assertTrue(harness.persistence.persisted.isEmpty())

        harness.adapter.reportMountedSelection(
            identity = sidecarB,
            selected = true,
            snapshotKey = "ordinary-sidecar-mounted",
            settled = true,
        )
        runCurrent()

        assertEquals(sidecarB, harness.adapter.snapshot.committedIdentity)
        assertNull(harness.adapter.snapshot.pendingIdentity)
        assertEquals(listOf(sidecarB), harness.persistence.persisted.map { it.identity })
    }

    @Test
    fun `ordinary sidecar backend rejection rolls back to A without persistence`() = runTest {
        val harness = harness(backgroundScope)

        harness.adapter.select(sidecarB)
        runCurrent()
        harness.port.completeStage(sidecarCandidate())
        runCurrent()
        harness.adapter.reportMountedSelection(
            identity = sidecarB,
            selected = false,
            snapshotKey = "ordinary-sidecar-rejected",
            settled = true,
        )
        runCurrent()

        assertEquals(sidecarA, harness.adapter.snapshot.committedIdentity)
        assertEquals(sidecarA, harness.adapter.snapshot.pendingIdentity)
        assertTrue(harness.persistence.persisted.isEmpty())
        assertEquals(
            "The selected subtitle could not be mounted.",
            harness.adapter.snapshot.failureMessage,
        )
        assertEquals(listOf("session-sidecar-b"), harness.port.rolledBackSessions)
        assertEquals("session-1", harness.port.managerSession)
        assertEquals("session-1", harness.port.lifecycleSession)
        assertEquals(sidecarA, harness.port.backendIdentity)

        harness.port.completeStage(restoreCandidate())
        runCurrent()
        harness.adapter.reportMountedSelection(
            identity = sidecarA,
            selected = true,
            snapshotKey = "ordinary-sidecar-restored",
            settled = true,
        )
        runCurrent()
        assertNull(harness.adapter.snapshot.pendingIdentity)
        assertTrue(harness.persistence.persisted.isEmpty())
    }

    @Test
    fun `ordinary sidecar backend timeout rolls back to A without persistence`() = runTest {
        val harness = harness(backgroundScope)

        harness.adapter.select(sidecarB)
        runCurrent()
        harness.port.completeStage(sidecarCandidate())
        runCurrent()
        advanceTimeBy(5_001L)
        runCurrent()

        assertEquals(sidecarA, harness.adapter.snapshot.committedIdentity)
        assertEquals(sidecarA, harness.adapter.snapshot.pendingIdentity)
        assertTrue(harness.persistence.persisted.isEmpty())
        assertEquals(
            "The selected subtitle could not be mounted.",
            harness.adapter.snapshot.failureMessage,
        )
        assertEquals(listOf("session-sidecar-b"), harness.port.rolledBackSessions)
        assertEquals("session-1", harness.port.managerSession)
        assertEquals("session-1", harness.port.lifecycleSession)
        assertEquals(sidecarA, harness.port.backendIdentity)

        harness.port.completeStage(restoreCandidate())
        runCurrent()
        harness.adapter.reportMountedSelection(
            identity = sidecarA,
            selected = true,
            snapshotKey = "ordinary-sidecar-timeout-restored",
            settled = true,
        )
        runCurrent()
        assertNull(harness.adapter.snapshot.pendingIdentity)
        assertTrue(harness.persistence.persisted.isEmpty())
    }

    @Test
    fun `ordinary sidecar reset invalidates backend acknowledgement without persistence`() = runTest {
        val harness = harness(backgroundScope)

        harness.adapter.select(sidecarB)
        runCurrent()
        harness.port.completeStage(sidecarCandidate())
        runCurrent()
        harness.adapter.resetContent(
            context = context(contentId = "content-2", sessionId = "session-2"),
            committedIdentity = sidecarC,
        )
        harness.adapter.reportMountedSelection(
            identity = sidecarB,
            selected = true,
            snapshotKey = "stale-sidecar-after-reset",
            settled = true,
        )
        runCurrent()

        assertEquals(sidecarC, harness.adapter.snapshot.committedIdentity)
        assertNull(harness.adapter.snapshot.pendingIdentity)
        assertTrue(harness.persistence.persisted.isEmpty())
        assertEquals(listOf("session-sidecar-b"), harness.port.rolledBackSessions)
    }

    @Test
    fun `ordinary Off keeps A committed until backend acknowledgement succeeds`() = runTest {
        val harness = harness(backgroundScope)

        harness.adapter.select(SubtitleIdentity.Off)
        runCurrent()
        harness.port.completeStage(offCandidate())
        runCurrent()

        assertEquals(sidecarA, harness.adapter.snapshot.committedIdentity)
        assertEquals(SubtitleIdentity.Off, harness.adapter.snapshot.pendingIdentity)
        assertEquals(SubtitleIdentity.Off, harness.adapter.snapshot.localMountIdentity)
        assertTrue(harness.persistence.persisted.isEmpty())

        harness.adapter.reportMountedSelection(
            identity = SubtitleIdentity.Off,
            selected = true,
            snapshotKey = "ordinary-off-disabled",
            settled = true,
        )
        runCurrent()

        assertEquals(SubtitleIdentity.Off, harness.adapter.snapshot.committedIdentity)
        assertNull(harness.adapter.snapshot.pendingIdentity)
        assertEquals(
            listOf(SubtitleIdentity.Off),
            harness.persistence.persisted.map { it.identity },
        )
    }

    @Test
    fun `ordinary Off backend rejection rolls back to A without persistence`() = runTest {
        val harness = harness(backgroundScope)

        harness.adapter.select(SubtitleIdentity.Off)
        runCurrent()
        harness.port.completeStage(offCandidate())
        runCurrent()
        harness.adapter.reportMountedSelection(
            identity = SubtitleIdentity.Off,
            selected = false,
            snapshotKey = "ordinary-off-rejected",
            settled = true,
        )
        runCurrent()

        assertEquals(sidecarA, harness.adapter.snapshot.committedIdentity)
        assertEquals(sidecarA, harness.adapter.snapshot.pendingIdentity)
        assertTrue(harness.persistence.persisted.isEmpty())
        assertEquals(
            "The selected subtitle could not be mounted.",
            harness.adapter.snapshot.failureMessage,
        )
        assertEquals(listOf("session-off"), harness.port.rolledBackSessions)
        assertEquals("session-1", harness.port.managerSession)
        assertEquals("session-1", harness.port.lifecycleSession)
        assertEquals(sidecarA, harness.port.backendIdentity)

        harness.port.completeStage(restoreCandidate())
        runCurrent()
        harness.adapter.reportMountedSelection(
            identity = sidecarA,
            selected = true,
            snapshotKey = "ordinary-off-restored",
            settled = true,
        )
        runCurrent()
        assertNull(harness.adapter.snapshot.pendingIdentity)
        assertTrue(harness.persistence.persisted.isEmpty())
    }

    @Test
    fun `ordinary Off backend timeout rolls back to A without persistence`() = runTest {
        val harness = harness(backgroundScope)

        harness.adapter.select(SubtitleIdentity.Off)
        runCurrent()
        harness.port.completeStage(offCandidate())
        runCurrent()
        advanceTimeBy(5_001L)
        runCurrent()

        assertEquals(sidecarA, harness.adapter.snapshot.committedIdentity)
        assertEquals(sidecarA, harness.adapter.snapshot.pendingIdentity)
        assertTrue(harness.persistence.persisted.isEmpty())
        assertEquals(
            "The selected subtitle could not be mounted.",
            harness.adapter.snapshot.failureMessage,
        )
        assertEquals(listOf("session-off"), harness.port.rolledBackSessions)
        assertEquals("session-1", harness.port.managerSession)
        assertEquals("session-1", harness.port.lifecycleSession)
        assertEquals(sidecarA, harness.port.backendIdentity)

        harness.port.completeStage(restoreCandidate())
        runCurrent()
        harness.adapter.reportMountedSelection(
            identity = sidecarA,
            selected = true,
            snapshotKey = "ordinary-off-timeout-restored",
            settled = true,
        )
        runCurrent()
        assertNull(harness.adapter.snapshot.pendingIdentity)
        assertTrue(harness.persistence.persisted.isEmpty())
    }

    @Test
    fun `ordinary Off reset invalidates backend acknowledgement without persistence`() = runTest {
        val harness = harness(backgroundScope)

        harness.adapter.select(SubtitleIdentity.Off)
        runCurrent()
        harness.port.completeStage(offCandidate())
        runCurrent()
        harness.adapter.resetContent(
            context = context(contentId = "content-2", sessionId = "session-2"),
            committedIdentity = sidecarC,
        )
        harness.adapter.reportMountedSelection(
            identity = SubtitleIdentity.Off,
            selected = true,
            snapshotKey = "stale-off-after-reset",
            settled = true,
        )
        runCurrent()

        assertEquals(sidecarC, harness.adapter.snapshot.committedIdentity)
        assertNull(harness.adapter.snapshot.pendingIdentity)
        assertTrue(harness.persistence.persisted.isEmpty())
        assertEquals(listOf("session-off"), harness.port.rolledBackSessions)
    }

    private fun harness(scope: CoroutineScope): Harness {
        val port = FinalRollbackStagedPort()
        val persistence = FinalRollbackPersistence()
        val adapter = TvSubtitleTransactionAdapter(
            scope = scope,
            stagedPort = port,
            persistencePort = persistence,
            durablePersistenceScope = scope,
            isLocallyMountable = { identity -> identity == port.mountedSidecarIdentity },
            onCommittedPlayback = { adoption ->
                port.lifecycleSession = adoption.playback.sessionId
                port.backendIdentity = adoption.committed.identity
                TvSubtitleAdoptionResult.Adopted
            },
        )
        adapter.resetContent(context(), committedIdentity = sidecarA)
        return Harness(adapter, port, persistence)
    }

    private fun context(
        contentId: String = "content-1",
        sessionId: String = "session-1",
    ): TvSubtitlePlaybackContext = TvSubtitlePlaybackContext(
        contentId = contentId,
        mediaFileId = 11,
        versionId = "version-11",
        sessionId = sessionId,
        positionSeconds = 42.0,
        audioTrackIndex = 2,
        qualityPreference = "auto",
        subtitleTracks = emptyList(),
        writeScope = tvTestPlaybackWriteScope,
    )

    private fun sidecarCandidate(): TvStagedSubtitleCandidate = TvStagedSubtitleCandidate(
        id = "sidecar-b",
        sessionId = "session-sidecar-b",
        selectedAudioIndex = 2,
        selectedSubtitleIndex = 4,
        subtitleMode = PlaybackSubtitleModeV3.RENDER,
        hasSidecar = true,
        subtitleTracks = emptyList(),
        qualityPreference = "auto",
    )

    private fun offCandidate(): TvStagedSubtitleCandidate = TvStagedSubtitleCandidate(
        id = "off",
        sessionId = "session-off",
        selectedAudioIndex = 2,
        selectedSubtitleIndex = -1,
        subtitleMode = PlaybackSubtitleModeV3.OFF,
        hasSidecar = false,
        subtitleTracks = emptyList(),
        qualityPreference = "auto",
    )

    private fun restoreCandidate(): TvStagedSubtitleCandidate = TvStagedSubtitleCandidate(
        id = "restore-a",
        sessionId = "session-restore-a",
        selectedAudioIndex = 2,
        selectedSubtitleIndex = 3,
        subtitleMode = PlaybackSubtitleModeV3.RENDER,
        hasSidecar = true,
        subtitleTracks = emptyList(),
        qualityPreference = "auto",
    )

    private data class Harness(
        val adapter: TvSubtitleTransactionAdapter,
        val port: FinalRollbackStagedPort,
        val persistence: FinalRollbackPersistence,
    )

    private companion object {
        val sidecarA: SubtitleIdentity = SubtitleIdentity.ServerSidecar(3)
        val sidecarB: SubtitleIdentity = SubtitleIdentity.ServerSidecar(4)
        val sidecarC: SubtitleIdentity = SubtitleIdentity.ServerSidecar(5)
    }
}

private class FinalRollbackStagedPort : TvSubtitleStagedReplanPort {
    private val staged = Channel<TvStagedSubtitleCandidate>(Channel.UNLIMITED)
    var managerSession: String = "session-1"
    var lifecycleSession: String = "session-1"
    var backendIdentity: SubtitleIdentity = SubtitleIdentity.ServerSidecar(3)
    var mountedSidecarIdentity: SubtitleIdentity? = null
    val rolledBackSessions = mutableListOf<String>()

    override suspend fun stage(
        request: TvSubtitleStageRequest,
    ): ApiResult<TvStagedSubtitleCandidate> = ApiResult.Success(staged.receive())

    override suspend fun commit(
        candidate: TvStagedSubtitleCandidate,
    ): ApiResult<TvSubtitleCommittedPlayback> {
        managerSession = candidate.sessionId
        mountedSidecarIdentity = candidate.selectedSubtitleIndex
            ?.takeIf { it >= 0 }
            ?.let(SubtitleIdentity::ServerSidecar)
        return ApiResult.Success(TvSubtitleCommittedPlayback(
            sessionId = candidate.sessionId,
            subtitleTracks = candidate.subtitleTracks,
            outputRouteGeneration = candidate.outputRouteGeneration,
        ))
    }

    override suspend fun discard(candidate: TvStagedSubtitleCandidate) = Unit

    override suspend fun abandonCommitted(playback: TvSubtitleCommittedPlayback) {
        rolledBackSessions += playback.sessionId
        managerSession = "session-1"
        lifecycleSession = "session-1"
        backendIdentity = SubtitleIdentity.ServerSidecar(3)
    }

    suspend fun completeStage(candidate: TvStagedSubtitleCandidate) {
        staged.send(candidate)
    }
}

private class FinalRollbackPersistence : TvSubtitlePersistencePort {
    val persisted = mutableListOf<CommittedSubtitle>()

    override suspend fun persist(
        committed: CommittedSubtitle,
        context: TvSubtitlePlaybackContext,
    ): Boolean {
        persisted += committed
        return true
    }
}
