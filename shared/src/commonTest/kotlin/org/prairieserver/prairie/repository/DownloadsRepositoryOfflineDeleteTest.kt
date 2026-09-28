package org.prairieserver.prairie.repository

import org.prairieserver.prairie.model.download.DownloadRecord
import org.prairieserver.prairie.model.download.DownloadsListResponse
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.repository.port.DownloadDeletionPort
import org.prairieserver.prairie.repository.port.PendingDownloadDeletion
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun rec(id: String, fileId: Int): DownloadRecord = DownloadRecord(
    id = id,
    contentId = "tt$fileId",
    mediaFileId = fileId,
    fileSize = 1000,
    bytesSent = 1000,
    kind = "queued",
    status = "completed",
    createdAt = "2026-06-16T00:00:00Z",
)

/** In-memory durable tombstone store standing in for the Room-backed port. */
private class FakeDeletionPort : DownloadDeletionPort {
    val rows = mutableMapOf<Triple<String, String, String>, Int?>()
    override suspend fun enqueue(serverId: String, profileId: String, recordId: String, mediaFileId: Int?) {
        rows[Triple(serverId, profileId, recordId)] = mediaFileId
    }
    override suspend fun allPendingRecordIds(): Set<String> = rows.keys.map { it.third }.toSet()
    override suspend fun pendingForScope(serverId: String, profileId: String): List<PendingDownloadDeletion> =
        rows.filterKeys { it.first == serverId && it.second == profileId }
            .map { (k, v) -> PendingDownloadDeletion(k.first, k.second, k.third, v) }
    override suspend fun remove(serverId: String, profileId: String, recordId: String) {
        rows.remove(Triple(serverId, profileId, recordId))
    }
}

private object OfflineDeleteNoDevices : org.prairieserver.prairie.network.DeviceMetadataProvider {
    override suspend fun current(): org.prairieserver.prairie.network.PrairieDeviceMetadata? = null
}

private class FakeApi(
    var serverList: List<DownloadRecord> = emptyList(),
    var deleteResult: (String) -> ApiResult<Unit> = { ApiResult.Success(Unit) },
) : org.prairieserver.prairie.network.api.DownloadsApi(
    registry = org.prairieserver.prairie.network.apiv2.DownloadRegistryV2Api(HttpClient(), org.prairieserver.prairie.network.TokenManagerImpl(), OfflineDeleteNoDevices, org.prairieserver.prairie.network.apiv2.ApiV2Gate.Unrestricted),
    tokens = org.prairieserver.prairie.network.TokenManagerImpl(),
    creation = org.prairieserver.prairie.network.apiv2.DownloadCreationV2Api(HttpClient(), org.prairieserver.prairie.network.TokenManagerImpl(), OfflineDeleteNoDevices,
        org.prairieserver.prairie.network.apiv2.DownloadRegistryV2Api(HttpClient(), org.prairieserver.prairie.network.TokenManagerImpl(), OfflineDeleteNoDevices, org.prairieserver.prairie.network.apiv2.ApiV2Gate.Unrestricted), org.prairieserver.prairie.network.apiv2.ApiV2Gate.Unrestricted),
) {
    val deleteCalls = mutableListOf<String>()
    override suspend fun list(scope: org.prairieserver.prairie.network.AuthScopeSnapshot?): ApiResult<DownloadsListResponse> = ApiResult.Success(DownloadsListResponse(serverList))
    override suspend fun delete(id: String, scope: org.prairieserver.prairie.network.AuthScopeSnapshot?): ApiResult<Unit> {
        deleteCalls += id
        return deleteResult(id)
    }
}

class DownloadsRepositoryOfflineDeleteTest {
    @Test fun onlyCurrentLoginTombstonesReplayOrClear() = runTest {
        val scope = org.prairieserver.prairie.network.AuthScopeSnapshot("s", "p", "https://example.invalid", null, identityGeneration = 0)
        val authority = org.prairieserver.prairie.network.DurableLoginAuthority("login", scope)
        val owners = object : org.prairieserver.prairie.network.DurableLoginAuthorityProvider {
            override suspend fun snapshotDurableLoginAuthority() = authority
        }
        val devices = object : org.prairieserver.prairie.network.DeviceMetadataProvider {
            override suspend fun current() = org.prairieserver.prairie.network.PrairieDeviceMetadata("device", "test", "android")
        }
        val rows = mutableListOf(
            PendingDownloadDeletion("s", "p", "owned", 1, "login", scope.serverUrl, "device"),
            PendingDownloadDeletion("s", "p", "foreign", 2, "prior-login", scope.serverUrl, "device"),
            PendingDownloadDeletion("s", "p", "legacy", 3),
        )
        val port = object : DownloadDeletionPort {
            override suspend fun enqueue(serverId: String, profileId: String, recordId: String, mediaFileId: Int?) = Unit
            override suspend fun allPendingRecordIds() = rows.map { it.recordId }.toSet()
            override suspend fun pendingForScope(serverId: String, profileId: String) = rows.toList()
            override suspend fun remove(serverId: String, profileId: String, recordId: String) { rows.removeAll { it.recordId == recordId } }
        }
        val api = FakeApi(serverList = rows.mapIndexed { i, row -> rec(row.recordId, i + 1) })
        val repo = DownloadsRepository(api, port, owners, devices, org.prairieserver.prairie.network.DefaultIdentityTransitionBarrier())
        repo.refresh(serverId = "s", profileId = "p")
        assertEquals(listOf("owned", "owned"), api.deleteCalls)
        assertEquals(listOf("owned"), repo.pendingDeletionsForScope("s", "p").map { it.recordId })
        api.serverList = emptyList()
        repo.refresh(serverId = "s", profileId = "p")
        assertEquals(listOf("foreign", "legacy"), rows.map { it.recordId })
    }


    @Test
    fun `enqueueDurableDelete drops the record and persists a tombstone`() = runTest {
        val port = FakeDeletionPort()
        val api = FakeApi(serverList = listOf(rec("a", 1), rec("b", 2)))
        val repo = DownloadsRepository(api, port)
        repo.refresh()
        assertEquals(setOf("a", "b"), repo.records.first().map { it.id }.toSet())

        repo.enqueueDurableDelete("srv1", "prof1", "a", mediaFileId = 1)

        assertEquals(listOf("b"), repo.records.first().map { it.id })
        assertEquals(setOf("a"), port.allPendingRecordIds())
    }

    @Test
    fun `durable tombstone filters a still-present server record across a restart`() = runTest {
        val port = FakeDeletionPort()
        // Offline delete happened in a previous "session": tombstone persisted,
        // but the server still lists the record (delete never reached it).
        port.enqueue("srv1", "prof1", "a", 1)

        // Fresh repository instance = app restart (in-memory pendingDelete empty).
        val api = FakeApi(serverList = listOf(rec("a", 1), rec("b", 2)), deleteResult = { ApiResult.NetworkError(RuntimeException("offline")) })
        val repo = DownloadsRepository(api, port)

        repo.refresh()  // no scope → pure filter, no reconcile

        assertEquals(listOf("b"), repo.records.first().map { it.id }) // 'a' stays filtered
    }

    @Test
    fun `scoped refresh replays the server delete for a still-present tombstone`() = runTest {
        val port = FakeDeletionPort()
        port.enqueue("srv1", "prof1", "a", 1)
        val api = FakeApi(serverList = listOf(rec("a", 1)))
        val repo = DownloadsRepository(api, port)

        repo.refresh(serverId = "srv1", profileId = "prof1")

        // Two-phase DELETE replayed for the still-present record.
        assertTrue(api.deleteCalls.contains("a"))
        // Still present in this list → tombstone kept for the next refresh.
        assertTrue(port.allPendingRecordIds().contains("a"))
    }

    @Test
    fun `tombstone clears only once the server list confirms absence`() = runTest {
        val port = FakeDeletionPort()
        port.enqueue("srv1", "prof1", "a", 1)
        val api = FakeApi(serverList = listOf(rec("a", 1)))
        val repo = DownloadsRepository(api, port)

        repo.refresh(serverId = "srv1", profileId = "prof1")
        assertTrue(port.allPendingRecordIds().contains("a")) // still listed → kept

        api.serverList = emptyList() // server now confirms it's gone
        repo.refresh(serverId = "srv1", profileId = "prof1")
        assertFalse(port.allPendingRecordIds().contains("a")) // cleared
    }

    @Test
    fun `refreshing one scope never clears another scope's tombstone`() = runTest {
        val port = FakeDeletionPort()
        port.enqueue("srvA", "prof1", "a", 1) // offline delete on server A, still pending
        port.enqueue("srvB", "prof1", "b", 2)
        // Refresh scope B; its server lists nothing (b already gone there).
        val api = FakeApi(serverList = emptyList())
        val repo = DownloadsRepository(api, port)

        repo.refresh(serverId = "srvB", profileId = "prof1")

        // Scope B's tombstone cleared (confirmed gone); scope A's is untouched
        // even though it's absent from scope B's list.
        assertFalse(port.allPendingRecordIds().contains("b"))
        assertTrue(port.allPendingRecordIds().contains("a"))
    }
}
