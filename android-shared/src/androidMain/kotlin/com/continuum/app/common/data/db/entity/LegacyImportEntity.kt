package com.continuum.app.common.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Bookkeeping for the one-time legacy import (Task 6) so it can re-run
 * idempotently. Each row records that a specific legacy source — a download
 * sidecar file, a scoped-JSON store entry, etc. — was imported into Room, and
 * the content hash + mtime at the time so a later import pass can skip
 * unchanged sources and re-import changed ones.
 *
 * Keyed by `(sourceKind, sourcePath)` (unique) rather than path alone, because
 * path namespaces are not globally unique across source kinds.
 */
@Entity(
    tableName = "legacy_imports",
    indices = [Index(value = ["sourceKind", "sourcePath"], unique = true)],
)
data class LegacyImportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceKind: String,    // e.g. "download_sidecar", "scoped_json"
    val sourcePath: String,
    val sourceHash: String,
    val sourceMtimeMs: Long,
    val importedAtMs: Long,
    val importVersion: Int,
    val status: String,        // "imported", "skipped", "failed"
    val error: String?,
)
