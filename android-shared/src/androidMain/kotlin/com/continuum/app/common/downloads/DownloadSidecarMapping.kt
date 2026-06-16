package com.continuum.app.common.downloads

import com.continuum.app.common.data.db.entity.DownloadEntity
import com.continuum.app.model.catalog.VersionChapter
import com.continuum.app.model.download.DownloadRecord
import com.continuum.app.model.download.DownloadSidecar
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Maps between the on-the-wire-ish [DownloadSidecar] (the download's full local
 * picture) and the Room [DownloadEntity] projection. Chapters serialize to a JSON
 * column (no TypeConverter). `episodeId`/`batchId` on [DownloadRecord] have no
 * entity column and aren't read anywhere in the download/playback paths, so they
 * round-trip as null.
 */
private val mappingJson = Json { ignoreUnknownKeys = true }

fun DownloadSidecar.toEntity(serverId: String, profileId: String): DownloadEntity =
    DownloadEntity(
        serverId = serverId,
        profileId = profileId,
        mediaFileId = record.mediaFileId,
        recordId = record.id,
        contentId = record.contentId,
        title = title,
        subtitle = subtitle,
        posterUrl = posterUrl,
        posterThumbhash = posterThumbhash,
        year = year,
        seriesTitle = seriesTitle,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        fileName = fileName,
        container = container,
        localUri = localUri,
        mediaType = mediaType,
        overview = overview,
        author = author,
        narrator = narrator,
        durationSeconds = durationSeconds,
        chaptersJson = chapters?.let { mappingJson.encodeToString(it) },
        status = record.status,
        kind = record.kind,
        fileSize = record.fileSize,
        bytesSent = record.bytesSent,
        createdAt = record.createdAt,
        completedAt = record.completedAt,
        updatedAtMs = updatedAtMs,
    )

fun DownloadEntity.toSidecar(): DownloadSidecar =
    DownloadSidecar(
        record = DownloadRecord(
            id = recordId,
            contentId = contentId,
            episodeId = null,
            batchId = null,
            mediaFileId = mediaFileId,
            fileSize = fileSize,
            bytesSent = bytesSent,
            kind = kind,
            status = status,
            createdAt = createdAt,
            completedAt = completedAt,
        ),
        title = title,
        subtitle = subtitle,
        posterUrl = posterUrl,
        posterThumbhash = posterThumbhash,
        year = year,
        seriesTitle = seriesTitle,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        fileName = fileName,
        container = container,
        localUri = localUri,
        mediaType = mediaType,
        overview = overview,
        author = author,
        narrator = narrator,
        durationSeconds = durationSeconds,
        chapters = chaptersJson?.let { runCatching { mappingJson.decodeFromString<List<VersionChapter>>(it) }.getOrNull() },
        updatedAtMs = updatedAtMs,
    )
