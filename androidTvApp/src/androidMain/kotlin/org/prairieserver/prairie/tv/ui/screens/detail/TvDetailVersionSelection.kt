package org.prairieserver.prairie.tv.ui.screens.detail

import org.prairieserver.prairie.model.catalog.FileVersion
import org.prairieserver.prairie.model.catalog.ItemDetail
import org.prairieserver.prairie.playback.hasResumePoint
import org.prairieserver.prairie.playback.selectDetailPlaybackVersion

/**
 * The version a TV detail page shows and plays for [detail]: the explicit
 * pick [selectedFileId], else Auto within the current edition
 * ([selectedEditionId], else the last-played one). See
 * [selectDetailPlaybackVersion].
 */
internal fun selectTvDetailDisplayVersion(
    detail: ItemDetail?,
    selectedFileId: Int?,
    selectedEditionId: String?,
    preferredQuality: String?,
): FileVersion? = detail?.let {
    selectDetailPlaybackVersion(
        versions = it.versions,
        variants = it.playbackVariants,
        pinnedFileId = selectedFileId,
        editionId = selectedEditionId,
        lastFileId = it.userData?.lastFileId,
        lastEditionKey = it.userData?.lastEditionKey,
        inProgress = it.userData.hasResumePoint,
        preferredQuality = preferredQuality,
    )
}
