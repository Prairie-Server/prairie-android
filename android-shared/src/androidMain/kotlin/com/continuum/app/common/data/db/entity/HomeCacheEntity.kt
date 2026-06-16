package com.continuum.app.common.data.db.entity

import androidx.room.Entity

/**
 * Offline read cache of the resolved home layout (Track B), one row per
 * `(serverId, profileId)`. [sectionsJson] is a serialized `List<ResolvedSection>`
 * (the models are `@Serializable`, so a JSON blob avoids a per-item schema —
 * the goal is "render the last home offline", not query it). [cachedAtMs] backs
 * an optional staleness/"offline" hint.
 */
@Entity(tableName = "home_cache", primaryKeys = ["serverId", "profileId"])
data class HomeCacheEntity(
    val serverId: String,
    val profileId: String,
    val sectionsJson: String,
    val cachedAtMs: Long,
)
