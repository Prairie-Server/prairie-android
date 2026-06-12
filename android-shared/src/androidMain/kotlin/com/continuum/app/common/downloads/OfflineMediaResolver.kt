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

class OfflineMediaResolver(private val storage: DownloadStorage) {

    fun findLocalMedia(
        contentId: String,
        requestedFileId: Int?,
        allowFallback: Boolean = true,
    ): OfflineMedia? {
        val candidates = storage.listAllSidecars()
            .filter { sidecar ->
                sidecar.record.contentId == contentId &&
                    sidecar.record.statusEnum() == DownloadStatus.Completed
            }
            .let { matches ->
                if (requestedFileId == null) {
                    matches
                } else {
                    val requested = matches.filter { it.record.mediaFileId == requestedFileId }
                    if (requested.isNotEmpty() || !allowFallback) requested else matches
                }
            }

        for (sidecar in candidates) {
            val fileId = sidecar.record.mediaFileId
            val located = storage.locateSidecarByFileId(fileId) ?: continue
            val (serverId, profileId, locatedSidecar) = located
            if (locatedSidecar.record.contentId != contentId) continue
            val media = storage.locateLocalMedia(serverId, profileId, fileId) ?: continue
            return OfflineMedia(
                serverId = serverId,
                profileId = profileId,
                fileId = fileId,
                uriString = media.uriString,
                displayName = media.displayName,
                sizeBytes = media.sizeBytes,
                sidecar = sidecar,
            )
        }
        return null
    }
}
