package com.continuum.app.android.ui.screens.detail

import com.continuum.app.model.catalog.FileVersion
import com.continuum.app.model.download.DownloadRecord
import com.continuum.app.model.download.DownloadStatus
import com.continuum.app.model.download.statusEnum

internal data class DetailDownloadState(
    val isDownloaded: Boolean = false,
    val progress: Float? = null,
    val needsLocalRecovery: Boolean = false,
)

internal enum class DetailDownloadTapAction {
    Start,
    Cancel,
    Ignore,
    ReplaceAndStart,
}

internal fun detailDownloadStateFor(
    version: FileVersion?,
    records: List<DownloadRecord>,
    hasLocalMedia: Boolean? = null,
): DetailDownloadState {
    val record = version?.let { v -> records.firstOrNull { it.mediaFileId == v.fileId } }
    val status = record?.statusEnum()
    val progress = record
        ?.takeIf {
            status == DownloadStatus.Downloading ||
                status == DownloadStatus.Queued
        }
        ?.let { rec ->
            if (rec.fileSize > 0) {
                (rec.bytesSent.toFloat() / rec.fileSize.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    val isCompleted = status == DownloadStatus.Completed
    val isMissingLocal = isCompleted && hasLocalMedia == false
    return DetailDownloadState(
        isDownloaded = isCompleted && !isMissingLocal,
        progress = progress,
        needsLocalRecovery = isMissingLocal,
    )
}

internal fun detailDownloadTapAction(
    status: DownloadStatus?,
    forceRedownloadMissingLocal: Boolean,
): DetailDownloadTapAction = when (status) {
    DownloadStatus.Queued,
    DownloadStatus.Downloading,
    -> DetailDownloadTapAction.Cancel
    DownloadStatus.Completed -> {
        if (forceRedownloadMissingLocal) {
            DetailDownloadTapAction.ReplaceAndStart
        } else {
            DetailDownloadTapAction.Ignore
        }
    }
    else -> DetailDownloadTapAction.Start
}
