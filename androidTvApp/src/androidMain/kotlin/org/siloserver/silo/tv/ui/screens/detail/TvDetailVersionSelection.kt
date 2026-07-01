package org.siloserver.silo.tv.ui.screens.detail

import org.siloserver.silo.model.catalog.FileVersion
import org.siloserver.silo.playback.selectPlaybackVersion

internal fun selectTvDetailDisplayVersion(
    versions: List<FileVersion>,
    selectedFileId: Int?,
    lastFileId: Int?,
    preferredQuality: String?,
): FileVersion? {
    if (versions.isEmpty()) return null
    if (selectedFileId != null) {
        versions.firstOrNull { it.fileId == selectedFileId }?.let { return it }
    }
    return selectPlaybackVersion(
        versions = versions,
        lastFileId = lastFileId,
        preferredQuality = preferredQuality,
    )
}
