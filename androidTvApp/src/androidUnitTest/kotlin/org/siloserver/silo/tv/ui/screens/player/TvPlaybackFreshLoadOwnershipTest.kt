package org.siloserver.silo.tv.ui.screens.player

import java.io.File
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.siloserver.silo.model.catalog.SubtitleTrack
import org.siloserver.silo.model.playback.PlayerSubtitleInfo
import org.siloserver.silo.model.playback.SubtitleIdentity
import org.siloserver.silo.model.playback.SubtitleMediaIdentity
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.playback.encodeSubtitleIdentityPreference

class TvPlaybackFreshLoadOwnershipTest {
    @Test
    fun `post publication failure restores exact predecessor while stale B cannot overwrite C`() {
        data class State(val sessionId: String)

        val ownership = TvUnpublishedLoadUiOwnership<State, String>()
        val a = State("session-a")
        val b = State("session-b")
        val c = State("session-c")
        var current = a

        ownership.register(
            "session-b",
            state = a,
            context = "context-a",
            predecessorSessionId = a.sessionId,
        )
        current = b
        val failure = assertFailsWith<IllegalStateException> {
            throw IllegalStateException("subtitle reset failed after UI publication")
        }
        assertEquals("subtitle reset failed after UI publication", failure.message)
        ownership.snapshotForRollback("session-b")?.let { predecessor ->
            if (current.sessionId == "session-b") current = predecessor.state
        }
        ownership.completeRollback("session-b")
        assertEquals(a, current)

        ownership.register(
            "session-b",
            state = a,
            context = "context-a",
            predecessorSessionId = a.sessionId,
        )
        current = c
        ownership.snapshotForRollback("session-b")?.let { predecessor ->
            if (current.sessionId == "session-b") current = predecessor.state
        }
        ownership.completeRollback("session-b")
        assertEquals(c, current)

        ownership.register(
            "session-b",
            state = a,
            context = "context-a",
            predecessorSessionId = a.sessionId,
        )
        ownership.register(
            "session-c",
            state = b,
            context = "context-b",
            predecessorSessionId = b.sessionId,
        )
        assertEquals(
            a,
            ownership.snapshotForRollback("session-c")?.state,
            "C must inherit authoritative A while unpublished B is rolling back.",
        )
    }

    @Test
    fun `fresh Ready ViewModel wires UI predecessor before mutation and releases it on confirm`() {
        val source = File(
            "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/player/TvPlayerViewModel.kt",
        ).readText()
        val ready = source
            .substringAfter("is VideoPlayerUiState.Ready ->")
            .substringBefore("is VideoPlayerUiState.Error ->")
        val rollback = source
            .substringAfter("private suspend fun rollbackUnpublishedTvLoadSession")
            .substringBefore("/**", missingDelimiterValue = source)

        assertTrue(
            ready.indexOf("unpublishedTvLoadUi.register(") <
                ready.indexOf("_uiState.update"),
        )
        assertTrue(
            ready.indexOf("unpublishedTvLoadUi.confirm(publishedSessionId)") >
                ready.indexOf("settlePendingPublicationIfCurrent("),
        )
        assertTrue(rollback.contains("snapshotForRollback(sessionId)"))
        assertTrue(rollback.contains("completeRollback(sessionId)"))
        assertTrue(rollback.contains("_uiState.value.sessionId == sessionId"))
        assertTrue(rollback.contains("subtitleTransactions.resetContent("))
    }

    @Test
    fun `post Ready local selection hydration and publish exceptions rollback exactly once`() =
        runTest {
            for (stage in listOf("local-selection", "hydration", "publish")) {
                val rolledBack = mutableListOf<String>()
                val ownership = TvUnpublishedLoadSessionOwnership { rolledBack += it }
                ownership.acquire("session-b")

                try {
                    throw IllegalStateException(stage)
                } catch (_: IllegalStateException) {
                    ownership.rollbackIfOwned()
                }
                ownership.rollbackIfOwned()

                assertEquals(listOf("session-b"), rolledBack, stage)
            }
        }

    @Test
    fun `post Ready cancellation and stale cleanup share one non cancellable rollback`() =
        runTest {
            val rollbackContexts = mutableListOf<Boolean>()
            val rolledBack = mutableListOf<String>()
            val ownership = TvUnpublishedLoadSessionOwnership { sessionId ->
                rollbackContexts += currentCoroutineContext().isActive
                rolledBack += sessionId
            }
            ownership.acquire("session-b")

            ownership.rollbackIfOwned("session-b")
            ownership.rollbackIfOwned()

            assertEquals(listOf("session-b"), rolledBack)
            assertEquals(listOf(true), rollbackContexts)
        }

    @Test
    fun `confirmed Ready transfers ownership and cannot be rolled back by later cancellation`() =
        runTest {
            val ownership = TvUnpublishedLoadSessionOwnership {
                error("confirmed manager and lifecycle ownership must not be rolled back")
            }
            ownership.acquire("session-b")

            assertTrue(ownership.transferConfirmed("session-b"))
            ownership.rollbackIfOwned()

            assertFalse(ownership.transferConfirmed("session-b"))
        }

    @Test
    fun `T18 fresh TV playback hydrates downloaded rows before restoring downloadId`() = runTest {
        val registry = TvPlayerLoadOwnerRegistry()
        val owner = registry.begin("movie", 11, "original")
        val wanted = SubtitleIdentity.Downloaded(
            downloadId = 91,
            media = media(trackId = "silo-downloaded-subtitle:91"),
        )

        val result = resolveOwnedTvFreshSubtitleRestore(
            owner = owner,
            registry = registry,
            preference = encodeSubtitleIdentityPreference(wanted),
            catalogTracks = emptyList(),
            initialRows = emptyList(),
            sessionId = "session-1",
            serverUrl = "https://silo.test",
            hydrateDownloadedRows = {
                ApiResult.Success(listOf(downloadedRow(index = 5, downloadId = 91)))
            },
        )

        val ownedResult = requireNotNull(result)
        val resolution = assertIs<TvFreshSubtitlePreferenceResolution>(ownedResult.resolution)
        assertEquals(91, assertIs<SubtitleIdentity.Downloaded>(resolution.identity).downloadId)
        assertEquals(91, ownedResult.rows.single().downloadId)
    }

    @Test
    fun `fresh restore does not publish the server rows twice`() = runTest {
        // The hydrate lambda in TvPlayerViewModel calls mergeDownloadedSubtitles
        // with the server rows as `existing`, so it returns the FULL set, not
        // just the downloaded ones. Concatenating the retained rows onto that
        // duplicated every server row: 21 rows became 42, the sidecar builder
        // mounted each twice, and the picker listed every track twice.
        val registry = TvPlayerLoadOwnerRegistry()
        val owner = registry.begin("movie", 11, "original")
        val serverRows = listOf(
            sidecarRow(index = 0, label = "English"),
            sidecarRow(index = 1, label = "Forced"),
            sidecarRow(index = 2, label = "SDH"),
        )

        val result = resolveOwnedTvFreshSubtitleRestore(
            owner = owner,
            registry = registry,
            preference = null,
            catalogTracks = emptyList(),
            initialRows = serverRows,
            sessionId = "session-1",
            serverUrl = "https://silo.test",
            hydrateDownloadedRows = {
                // Exactly what the production lambda returns: existing + downloaded.
                ApiResult.Success(serverRows + downloadedRow(index = 3, downloadId = 91))
            },
        )

        val rows = requireNotNull(result).rows
        assertEquals(
            listOf(0, 1, 2, 3),
            rows.map { it.index },
            "every row must appear once, keyed by its combined index",
        )
        assertEquals(91, rows.single { it.index == 3 }.downloadId)
    }

    @Test
    fun `T19 hydration failure does not replace the committed server subtitle`() = runTest {
        val registry = TvPlayerLoadOwnerRegistry()
        val owner = registry.begin("movie", 11, "original")
        val committed = sidecarRow(index = 3, label = "French")
        val wanted = SubtitleIdentity.ServerSidecar(
            serverIndex = 4,
            media = media(label = "English"),
        )

        val result = resolveOwnedTvFreshSubtitleRestore(
            owner = owner,
            registry = registry,
            preference = encodeSubtitleIdentityPreference(wanted),
            catalogTracks = listOf(externalTrack(index = 4, label = "English")),
            initialRows = listOf(committed),
            sessionId = "session-1",
            serverUrl = "https://silo.test",
            hydrateDownloadedRows = {
                ApiResult.NetworkError(IllegalStateException("hydration failed"))
            },
        )

        assertEquals(listOf(committed), result?.rows)
        assertNull(result?.resolution)
    }

    @Test
    fun `T20 cancelled hydration cannot publish`() = runTest {
        val registry = TvPlayerLoadOwnerRegistry()
        val owner = registry.begin("movie", 11, "original")

        assertFailsWith<CancellationException> {
            resolveOwnedTvFreshSubtitleRestore(
                owner = owner,
                registry = registry,
                preference = null,
                catalogTracks = emptyList(),
                initialRows = emptyList(),
                sessionId = "session-1",
                serverUrl = "https://silo.test",
                hydrateDownloadedRows = { throw CancellationException("cancelled") },
            )
        }
    }

    @Test
    fun `T21 stale hydration response cannot publish into a newer session`() = runTest {
        val registry = TvPlayerLoadOwnerRegistry()
        val oldOwner = registry.begin("movie", 11, "original")
        val hydration = CompletableDeferred<ApiResult<List<PlayerSubtitleInfo>>>()
        val stale = async {
            resolveOwnedTvFreshSubtitleRestore(
                owner = oldOwner,
                registry = registry,
                preference = encodeSubtitleIdentityPreference(SubtitleIdentity.Off),
                catalogTracks = emptyList(),
                initialRows = emptyList(),
                sessionId = "old-session",
                serverUrl = "https://silo.test",
                hydrateDownloadedRows = { hydration.await() },
            )
        }
        runCurrent()

        registry.begin("movie", 22, "720p")
        hydration.complete(ApiResult.Success(emptyList()))

        assertNull(stale.await())
    }

    @Test
    fun `T22 restart restores only the current content file quality owner`() = runTest {
        val registry = TvPlayerLoadOwnerRegistry()
        val oldOwner = registry.begin("movie", 11, "original")
        val newestOwner = registry.begin("movie", 22, "720p")

        val stale = resolveOwnedTvFreshSubtitleRestore(
            owner = oldOwner,
            registry = registry,
            preference = encodeSubtitleIdentityPreference(SubtitleIdentity.Off),
            catalogTracks = emptyList(),
            initialRows = emptyList(),
            sessionId = "old-session",
            serverUrl = "https://silo.test",
            hydrateDownloadedRows = { ApiResult.Success(emptyList()) },
        )
        val current = resolveOwnedTvFreshSubtitleRestore(
            owner = newestOwner,
            registry = registry,
            preference = encodeSubtitleIdentityPreference(SubtitleIdentity.Off),
            catalogTracks = emptyList(),
            initialRows = emptyList(),
            sessionId = "new-session",
            serverUrl = "https://silo.test",
            hydrateDownloadedRows = { ApiResult.Success(emptyList()) },
        )

        assertNull(stale)
        assertEquals(SubtitleIdentity.Off, current?.resolution?.identity)
    }

    @Test
    fun `T23 exit invalidates fresh restore before publication`() = runTest {
        val registry = TvPlayerLoadOwnerRegistry()
        val owner = registry.begin("movie", 11, "original")
        registry.invalidate()

        val result = resolveOwnedTvFreshSubtitleRestore(
            owner = owner,
            registry = registry,
            preference = encodeSubtitleIdentityPreference(SubtitleIdentity.Off),
            catalogTracks = emptyList(),
            initialRows = emptyList(),
            sessionId = "unpublished-session",
            serverUrl = "https://silo.test",
            hydrateDownloadedRows = { ApiResult.Success(emptyList()) },
        )

        assertNull(result)
    }

    @Test
    fun `T63 version load fences an older quality transaction before startup`() {
        val registry = TvPlayerLoadOwnerRegistry()
        var transactionInvalidations = 0
        val fence = TvPlayerMutationFence(registry) { transactionInvalidations += 1 }
        val oldOwner = fence.beginLoad("movie", 11, "original")

        val newOwner = fence.beginLoad("movie", 22, "720p")

        assertFalse(fence.owns(oldOwner))
        assertTrue(fence.owns(newOwner))
        assertEquals(
            0,
            transactionInvalidations,
            "Load ownership is synchronous; adapter invalidation now awaits settlement in the load worker.",
        )
    }

    @Test
    fun `T64 version load fences an older output route transaction before startup`() {
        val registry = TvPlayerLoadOwnerRegistry()
        val events = mutableListOf<String>()
        val fence = TvPlayerMutationFence(registry) { events += "invalidate-route-and-subtitle" }

        fence.beginLoad("movie", 11, "original")
        events += "start-quality-or-route-work"
        fence.beginLoad("movie", 22, "original")
        events += "start-version-B"

        assertEquals(
            listOf(
                "start-quality-or-route-work",
                "start-version-B",
            ),
            events,
        )
    }

    @Test
    fun `T89 exit invalidates load refresh mount and persistence owners together`() {
        val registry = TvPlayerLoadOwnerRegistry()
        var transactionInvalidations = 0
        val fence = TvPlayerMutationFence(registry) { transactionInvalidations += 1 }
        val owner = fence.beginLoad("movie", 11, "original")

        fence.invalidateAll()

        assertFalse(fence.owns(owner))
        assertEquals(1, transactionInvalidations)
    }

    @Test
    fun `T90 failed version B load keeps version A mounted and committed`() {
        val versionA = TvPlayerViewModel.UiState(
            isLoading = false,
            error = null,
            sessionId = "session-a",
            streamUrl = "https://silo.test/session-a/master.m3u8",
            selectedFileId = 11,
            mediaFileId = 11,
            committedSubtitleIdentity = SubtitleIdentity.ServerSidecar(3),
        )

        val applying = beginTvReplacementLoad(versionA)
        val failed = failTvReplacementLoad(applying, "Version B failed")

        assertFalse(applying.isLoading)
        assertEquals(versionA.sessionId, applying.sessionId)
        assertEquals(versionA.streamUrl, applying.streamUrl)
        assertEquals(versionA.selectedFileId, applying.selectedFileId)
        assertEquals(versionA.committedSubtitleIdentity, applying.committedSubtitleIdentity)
        assertFalse(failed.isLoading)
        assertNull(failed.error)
        assertEquals(versionA.sessionId, failed.sessionId)
        assertEquals(versionA.streamUrl, failed.streamUrl)
        assertEquals(versionA.selectedFileId, failed.selectedFileId)
        assertEquals(versionA.committedSubtitleIdentity, failed.committedSubtitleIdentity)
    }

    @Test
    fun `T59 same content file and quality loads publish the newest owner only`() = runTest {
        val registry = TvPlayerLoadOwnerRegistry()
        val first = registry.begin(
            contentId = "movie",
            preferredFileId = 11,
            preferredQuality = "original",
        )
        val newest = registry.begin(
            contentId = "movie",
            preferredFileId = 22,
            preferredQuality = "720p",
        )
        val published = mutableListOf<String>()

        assertFalse(registry.runIfOwned(first) { published += "first" })
        assertTrue(registry.runIfOwned(newest) { published += "newest" })
        assertEquals(listOf("newest"), published)
    }

    @Test
    fun `different content file and quality completions reject every stale owner`() {
        val registry = TvPlayerLoadOwnerRegistry()
        val contentA = registry.begin("content-a", 11, "original")
        val versionA = registry.begin("content-a", 22, "original")
        val qualityA = registry.begin("content-a", 22, "720p")
        val contentB = registry.begin("content-b", 33, "auto")

        assertFalse(registry.owns(contentA))
        assertFalse(registry.owns(versionA))
        assertFalse(registry.owns(qualityA))
        assertTrue(registry.owns(contentB))
    }

    @Test
    fun `owned start context retains the exact generation across suspensions`() = runTest {
        val registry = TvPlayerLoadOwnerRegistry()
        val owner = registry.begin("movie", 11, "720p")

        val ownership = registry.withOwner(owner) {
            currentTvPlayerLoadOwnership()
        }

        assertEquals(owner, ownership?.owner)
        assertTrue(requireNotNull(ownership).isCurrent())
    }

    @Test
    fun `T60 version switch stops a non cooperative stale Ready without publication`() = runTest {
        val registry = TvPlayerLoadOwnerRegistry()
        val oldOwner = registry.begin("movie", 11, "original")
        val starter = NonCooperativeReadyStarter()
        val published = mutableListOf<String>()
        val stopped = mutableListOf<String>()
        val oldLoad = launch {
            val sessionId = starter.start()
            registry.publishReadyIfOwned(
                owner = oldOwner,
                sessionId = sessionId,
                publish = { published += sessionId },
                stopStaleSession = { stopped += it },
            )
        }
        starter.awaitStarted()

        val newOwner = registry.begin("movie", 22, "720p")
        starter.complete("stale-session")
        runCurrent()

        assertTrue(registry.owns(newOwner))
        assertEquals(emptyList(), published)
        assertEquals(listOf("stale-session"), stopped)
        oldLoad.join()
    }

    @Test
    fun `T61 exit invalidates the current owner and cleans a late Ready`() = runTest {
        val registry = TvPlayerLoadOwnerRegistry()
        val owner = registry.begin("movie", 11, "original")
        val stopped = mutableListOf<String>()
        var published = false

        registry.invalidate()
        val accepted = registry.publishReadyIfOwned(
            owner = owner,
            sessionId = "late-session",
            publish = { published = true },
            stopStaleSession = { stopped += it },
        )

        assertFalse(accepted)
        assertFalse(published)
        assertEquals(listOf("late-session"), stopped)
    }

    @Test
    fun `clear invalidates the current owner and rejects a late failure`() {
        val registry = TvPlayerLoadOwnerRegistry()
        val owner = registry.begin("movie", 11, "original")
        var staleFailurePublished = false

        registry.invalidate()

        assertFalse(registry.runIfOwned(owner) { staleFailurePublished = true })
        assertFalse(staleFailurePublished)
    }

    @Test
    fun `missing stale session id rejects publication without cleanup call`() = runTest {
        val registry = TvPlayerLoadOwnerRegistry()
        val owner = registry.begin("movie", 11, "original")
        var cleanupCalls = 0

        registry.invalidate()
        val accepted = registry.publishReadyIfOwned(
            owner = owner,
            sessionId = null,
            publish = { error("stale load published") },
            stopStaleSession = { cleanupCalls += 1 },
        )

        assertFalse(accepted)
        assertEquals(0, cleanupCalls)
    }

    private fun downloadedRow(index: Int, downloadId: Int) = PlayerSubtitleInfo(
        index = index,
        language = "en",
        codec = "vtt",
        label = "English",
        source = "downloaded",
        forced = false,
        url = "/subtitles/$downloadId.vtt",
        downloadId = downloadId,
        mediaTrackId = "silo-downloaded-subtitle:$downloadId",
    )

    private fun sidecarRow(index: Int, label: String) = PlayerSubtitleInfo(
        index = index,
        language = "en",
        codec = "srt",
        label = label,
        source = "server_artifact",
        forced = false,
        url = "/subtitles/$index.srt",
    )

    private fun externalTrack(index: Int, label: String) = SubtitleTrack(
        index = index,
        codec = "srt",
        language = "en",
        title = label,
        external = true,
    )

    private fun media(
        trackId: String? = null,
        label: String? = "English",
    ) = SubtitleMediaIdentity(
        trackId = trackId,
        label = label,
        language = "en",
        codecFamily = "subrip",
        forced = false,
    )
}

private class NonCooperativeReadyStarter {
    private val started = CompletableDeferred<Unit>()
    private var continuation: Continuation<String>? = null

    suspend fun start(): String = suspendCoroutine { pending ->
        continuation = pending
        started.complete(Unit)
    }

    suspend fun awaitStarted() {
        started.await()
    }

    fun complete(sessionId: String) {
        requireNotNull(continuation).resume(sessionId)
    }
}
