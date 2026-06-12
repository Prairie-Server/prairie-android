package com.continuum.app.model.download

import kotlinx.serialization.Serializable

/**
 * Local-only on-disk record stored next to a downloaded media file at
 * `<filesDir>/downloads/<serverId>/<profileId>/<fileId>.record.json`.
 *
 * Unlike [DownloadRecord] (which is the wire shape — strictly mirrors the
 * server's `downloadResponse` struct and cannot grow new fields without
 * breaking the API contract), the sidecar is the client's full picture of
 * what's on disk: server state + the catalog metadata we need to render
 * the row offline. The whole reason it exists: without the catalog fields
 * stashed locally, an offline Downloads tab can show neither title nor
 * poster even when the bytes are sitting right there on disk.
 *
 * Written by [com.continuum.app.common.downloads.DownloadEnqueuer] at
 * download start and rewritten by [com.continuum.app.common.downloads.DownloadWorker]
 * on every status transition (downloading → completed / failed). Read by
 * [com.continuum.app.repository.DownloadsRepository] on init to seed the
 * in-memory cache before any server refresh, and by the player's offline
 * path to find the local file's contentId without a network round-trip.
 */
@Serializable
data class DownloadSidecar(
    val record: DownloadRecord,
    val title: String,
    val subtitle: String? = null,
    val posterUrl: String? = null,
    val posterThumbhash: String? = null,
    val year: Int? = null,
    val seriesTitle: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    /** Original server-provided basename for the downloaded file, when
     *  available. Downloads are stored using this name under a fileId-owned
     *  directory so local playback/reading sees the real format. */
    val fileName: String? = null,
    /** Server-provided container / extension hint for the downloaded file. */
    val container: String? = null,
    /** Android MediaStore / public file URI for the downloaded bytes. */
    val localUri: String? = null,
    /** Stable wire string of [com.continuum.app.model.download.DownloadMediaType]
     *  — drives Downloads-tab section + renderer choice. Default empty
     *  for back-compat with sidecars written before this field existed;
     *  those decode as [DownloadMediaType.Unknown] but the viewmodel can
     *  reclassify via `seriesTitle != null` heuristic when re-rendering. */
    val mediaType: String = "",
    /** Wall-clock millis when the sidecar was last written. Diagnostic only;
     *  helps debug stale-file scenarios via `ls -la`. */
    val updatedAtMs: Long,
)
