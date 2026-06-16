package com.continuum.app.common.data.db.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Local-first projection of per-item user state — resume position, watched,
 * rating, favorite, track selections, and ebook reading position.
 *
 * Identity is `(serverId, profileId, contentId, fileId)` because Silo scopes
 * all user state by server + profile (see `ScopedJsonFileStore` and
 * `DownloadStorage`), and progress/track/CFI are **file-level** (a multi-file
 * content item — e.g. a movie with multiple versions, or an audiobook with
 * many tracks — has independent positions per file).
 *
 * `watched` / `ratingValue` / `favorite` are **content-level** in the server
 * contract (`PersonalDataApi` keys them by item id only, no fileId). They are
 * stored on every file row for read convenience, but the outbox coalesces
 * those mutations at content scope (the SET_WATCHED/RATING/FAVORITE coalesce
 * keys omit fileId — see [com.continuum.app.common.data.sync.OutboxOperation])
 * so a multi-file item never emits duplicate/conflicting ops per file.
 *
 * Track selections are stored as **stable fingerprints**
 * `(index|language|codec|title|forced)` rather than raw UI indices, so a
 * selection survives a re-extracted track list whose ordering changed.
 *
 * `clientUpdatedAtMs` drives last-writer-wins + resume scans; `serverUpdatedAtMs`
 * is the projection's last-known server timestamp (null until first ack).
 */
@Entity(
    tableName = "user_item_state",
    primaryKeys = ["serverId", "profileId", "contentId", "fileId"],
    indices = [
        Index(value = ["serverId", "profileId", "contentId"]),
        Index(value = ["serverId", "profileId", "clientUpdatedAtMs"]),
    ],
)
data class UserItemStateEntity(
    val serverId: String,
    val profileId: String,
    val contentId: String,
    val fileId: Int,
    val positionSeconds: Double,
    val durationSeconds: Double?,
    val watched: Boolean,
    val ratingValue: Int?,
    val favorite: Boolean,
    // Stable selection fingerprints: (index|language|codec|title|forced), not raw UI index.
    val audioFingerprint: String?,
    val subtitleFingerprint: String?,
    val cfi: String?,
    val readProgress: Double?,
    val clientUpdatedAtMs: Long,
    val serverUpdatedAtMs: Long?,
)
