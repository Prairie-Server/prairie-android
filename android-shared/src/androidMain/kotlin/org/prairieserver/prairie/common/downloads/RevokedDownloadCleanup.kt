package org.prairieserver.prairie.common.downloads

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.prairieserver.prairie.repository.port.RevokedDownloadCleanupPort

/**
 * Deletes this device's copy of an administrator-revoked download: stops its
 * transfer, then removes the media file, offline assets and metadata row under
 * the slot lock, as a user delete does. A slot now holding another download of
 * the same file is left alone.
 */
class RevokedDownloadCleanup(
    private val context: Context,
    private val storage: DownloadStorage,
    private val metadataStore: DownloadMetadataStore,
) : RevokedDownloadCleanupPort {

    override suspend fun removeLocalCopy(serverId: String, profileId: String, recordId: String, mediaFileId: Int): Boolean {
        DownloadWorker.cancelTransferAndAwait(context, recordId)
        return DownloadSlotLocks.of(serverId, profileId, mediaFileId).withLock {
            val row = metadataStore.readSidecar(serverId, profileId, mediaFileId)
            if (row != null && row.record.id != recordId) return@withLock true
            val bytesGone = withContext(Dispatchers.IO) {
                storage.delete(serverId, profileId, mediaFileId) || !storage.exists(serverId, profileId, mediaFileId)
            }
            if (bytesGone && row != null) metadataStore.deleteSidecar(serverId, profileId, mediaFileId)
            bytesGone
        }
    }
}
