package org.siloserver.silo.common.data.db.entity

import androidx.room.Entity

/**
 * Generic key-value JSON cache for catalog browse reads (Track B), keyed by
 * `(serverId, profileId, cacheKey)`. [cacheKey] is e.g. `"libraries"` or
 * `"library:{id}"`; [json] is the serialized response. A single flexible table
 * (vs one per read) — the goal is "serve the last response offline", not query it.
 */
@Entity(tableName = "catalog_cache", primaryKeys = ["serverId", "profileId", "cacheKey"])
data class CatalogCacheEntity(
    val serverId: String,
    val profileId: String,
    val cacheKey: String,
    val json: String,
    val cachedAtMs: Long,
)
