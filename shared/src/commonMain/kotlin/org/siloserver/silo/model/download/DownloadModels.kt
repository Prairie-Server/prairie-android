package org.siloserver.silo.model.download

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape of a download record. Mirrors the server's `downloadResponse`
 * struct in `silo-server/internal/api/handlers/downloads.go:9-22` verbatim
 * (snake_case via @SerialName).
 *
 * `status` and `kind` are kept as raw strings so an unknown literal from a
 * newer server build doesn't fail decoding; clients map to [DownloadStatus] /
 * [DownloadKind] via `fromWire`.
 */
@Serializable
data class DownloadRecord(
    val id: String,
    @SerialName("content_id") val contentId: String,
    @SerialName("episode_id") val episodeId: String? = null,
    @SerialName("batch_id") val batchId: String? = null,
    @SerialName("media_file_id") val mediaFileId: Int,
    @SerialName("file_size") val fileSize: Long = 0L,
    @SerialName("bytes_sent") val bytesSent: Long = 0L,
    val kind: String,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("completed_at") val completedAt: String? = null,
)

/**
 * List response wrapper — matches the server's `downloadsListResponse`
 * (`downloads.go:26-28`).
 */
@Serializable
data class DownloadsListResponse(
    val downloads: List<DownloadRecord> = emptyList(),
)

/**
 * Client-side download quality presets. "Original" preserves the source file
 * and its public/discoverable filename. Bitrate presets prepare the client
 * contract for server-side transcoded downloads; until the server supports
 * them, older servers ignore these additive request fields.
 */
enum class DownloadQuality(
    val wire: String,
    val label: String,
    val targetBitrateKbps: Int?,
) {
    Original("original", "Original", null),
    Mbps20("20mbps", "20 Mbps", 20_000),
    Mbps10("10mbps", "10 Mbps", 10_000),
    Mbps5("5mbps", "5 Mbps", 5_000),
    Mbps2("2mbps", "2 Mbps", 2_000),
    Mbps1("1mbps", "1 Mbps", 1_000);

    companion object {
        fun fromWire(value: String?): DownloadQuality =
            entries.firstOrNull { it.wire == value?.lowercase()?.trim() } ?: Original
    }
}

/**
 * POST /api/v1/downloads body. Either `episodeId` or `fileId` is set on
 * top of the always-required `contentId`. `series = true` requests batch
 * download of all episodes for a series content id (server expands and
 * returns one DownloadRecord per file under a shared batchId).
 */
@Serializable
data class DownloadRequest(
    @SerialName("content_id") val contentId: String,
    @SerialName("episode_id") val episodeId: String? = null,
    @SerialName("file_id") val fileId: Int? = null,
    val series: Boolean = false,
    val quality: String? = null,
    @SerialName("target_bitrate_kbps") val targetBitrateKbps: Int? = null,
)

/**
 * Client-side status enum. Mirrors the server's `Status` field in the
 * `downloads` table (`migrations/042_downloads.up.sql`). Wire strings are
 * lowercased; unknown values resolve to [Unknown] so a server-side enum
 * extension doesn't crash the client.
 */
enum class DownloadStatus(val wire: String) {
    Queued("queued"),
    Downloading("downloading"),
    Completed("completed"),
    Failed("failed"),
    Cancelled("cancelled"),
    Unknown("");

    companion object {
        fun fromWire(value: String?): DownloadStatus =
            entries.firstOrNull { it.wire == value?.lowercase() } ?: Unknown
    }
}

/**
 * `direct` = browser one-shot serve (no persistent server record).
 * `queued` = tracked record visible in the user's downloads list.
 */
enum class DownloadKind(val wire: String) {
    Direct("direct"),
    Queued("queued"),
    Unknown("");

    companion object {
        fun fromWire(value: String?): DownloadKind =
            entries.firstOrNull { it.wire == value?.lowercase() } ?: Unknown
    }
}

/** Convenience: type-safe accessor. */
fun DownloadRecord.statusEnum(): DownloadStatus = DownloadStatus.fromWire(status)

/** Convenience: type-safe accessor. */
fun DownloadRecord.kindEnum(): DownloadKind = DownloadKind.fromWire(kind)
