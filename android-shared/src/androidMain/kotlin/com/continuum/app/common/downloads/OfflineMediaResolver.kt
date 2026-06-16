package com.continuum.app.common.downloads

import com.continuum.app.model.download.DownloadSidecar
import com.continuum.app.model.download.DownloadStatus
import com.continuum.app.model.download.statusEnum

data class OfflineMedia(
    val serverId: String,
    val profileId: String,
    val fileId: Int,
    val uriString: String,
    val displayName: String,
    val sizeBytes: Long,
    val sidecar: DownloadSidecar,
) {
    val fileUrl: String get() = uriString
    val file: java.io.File?
        get() = if (uriString.startsWith("file://")) java.io.File(uriString.removePrefix("file://")) else null
}

class OfflineMediaResolver(
    private val metadata: DownloadMetadataStore,
    private val storage: DownloadStorage,
    private val legacyImporter: LegacyDownloadImporter,
) {

    suspend fun listLocalMedia(
        serverId: String,
        profileId: String,
        contentId: String,
    ): List<OfflineMedia> {
        // Ensure the one-time legacy sidecar→Room import has finished before
        // resolving offline media; otherwise a pre-cutover download could read as
        // missing on the first cold start. Memoized: a no-op after it runs once.
        legacyImporter.awaitImport(System.currentTimeMillis())
        return metadata.listSidecars(serverId, profileId)
            .filter { sidecar ->
                sidecar.record.contentId == contentId &&
                    sidecar.record.statusEnum() == DownloadStatus.Completed
            }
            .mapNotNull { sidecar ->
                val fileId = sidecar.record.mediaFileId
                // Bytes are resolved by fileId (sync); metadata confirms scope/content.
                val media = storage.locateLocalMedia(serverId, profileId, fileId) ?: return@mapNotNull null
                OfflineMedia(
                    serverId = serverId,
                    profileId = profileId,
                    fileId = fileId,
                    uriString = media.uriString,
                    displayName = media.displayName,
                    sizeBytes = media.sizeBytes,
                    sidecar = sidecar,
                )
            }
    }

    suspend fun findLocalMedia(
        serverId: String,
        profileId: String,
        contentId: String,
        requestedFileId: Int?,
        allowFallback: Boolean = true,
    ): OfflineMedia? {
        val candidates = listLocalMedia(serverId, profileId, contentId)
            .let { matches ->
                if (requestedFileId == null) {
                    matches
                } else {
                    val requested = matches.filter { it.fileId == requestedFileId }
                    if (requested.isNotEmpty() || !allowFallback) requested else matches
                }
            }

        return candidates.firstOrNull()
    }
}
