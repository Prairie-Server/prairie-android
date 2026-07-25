package org.prairieserver.prairie.common.data.db.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "download_subscriptions",
    primaryKeys = ["serverId", "profileId", "id"],
    indices = [
        Index(value = ["serverId", "profileId", "enabled"]),
        Index(value = ["serverId", "profileId", "targetType", "targetId"], unique = true),
    ],
)
data class DownloadSubscriptionEntity(
    val id: String,
    val serverId: String,
    val profileId: String,
    val targetType: String,
    val targetId: String,
    val displayTitle: String,
    val mediaKind: String,
    val quality: String,
    val wifiOnly: Boolean,
    val enabled: Boolean,
    val includeExisting: Boolean,
    val keepUnwatchedLimit: Int,
    val deleteWatchedAfterDays: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val lastEvaluatedAt: Long?,
    val lastError: String?,
)
