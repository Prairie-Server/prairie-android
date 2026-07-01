package org.siloserver.silo.common.data.repository

import org.siloserver.silo.common.data.db.SiloDatabase
import org.siloserver.silo.common.data.db.entity.DownloadDeletionEntity
import org.siloserver.silo.repository.port.DownloadDeletionPort
import org.siloserver.silo.repository.port.PendingDownloadDeletion

/**
 * Room-backed [DownloadDeletionPort] (Track B offline-first delete). Persists the
 * tombstone for a deleted download so its server record is reconciled on the next
 * online refresh and the recordId is filtered out of refreshes until then.
 */
class RoomDownloadDeletionStore(
    db: SiloDatabase,
    private val now: () -> Long = { System.currentTimeMillis() },
) : DownloadDeletionPort {

    private val dao = db.downloadDeletionDao()

    override suspend fun enqueue(serverId: String, profileId: String, recordId: String, mediaFileId: Int?) {
        dao.upsert(
            DownloadDeletionEntity(
                serverId = serverId,
                profileId = profileId,
                recordId = recordId,
                mediaFileId = mediaFileId,
                enqueuedAtMs = now(),
            ),
        )
    }

    override suspend fun allPendingRecordIds(): Set<String> = dao.allRecordIds().toSet()

    override suspend fun pendingForScope(serverId: String, profileId: String): List<PendingDownloadDeletion> =
        dao.forScope(serverId, profileId).map {
            PendingDownloadDeletion(it.serverId, it.profileId, it.recordId, it.mediaFileId)
        }

    override suspend fun remove(serverId: String, profileId: String, recordId: String) {
        dao.delete(serverId, profileId, recordId)
    }
}
