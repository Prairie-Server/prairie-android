package com.continuum.app.tv.watchnext

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram

@SuppressLint("RestrictedApi")
class WatchNextRepository(private val context: Context) {

    fun diffAndApply(remoteFields: List<WatchNextProgramFields>): DiffResult {
        val resolver = context.contentResolver
        val existing = readOurExistingPrograms()
        val remoteByExternalId = remoteFields.associateBy { it.externalId }

        var inserted = 0
        var updated = 0
        var deleted = 0

        for (fields in remoteFields) {
            val existingId = existing[fields.externalId]
            if (existingId == null) {
                val uri = resolver.insert(
                    TvContractCompat.WatchNextPrograms.CONTENT_URI,
                    fields.toContentValues(),
                )
                if (uri != null) inserted++
            } else {
                val rowUri = ContentUris.withAppendedId(
                    TvContractCompat.WatchNextPrograms.CONTENT_URI,
                    existingId,
                )
                val rows = resolver.update(rowUri, fields.toContentValues(), null, null)
                if (rows > 0) updated++
            }
        }

        for ((externalId, rowId) in existing) {
            if (externalId !in remoteByExternalId) {
                val rowUri = ContentUris.withAppendedId(
                    TvContractCompat.WatchNextPrograms.CONTENT_URI,
                    rowId,
                )
                if (resolver.delete(rowUri, null, null) > 0) deleted++
            }
        }

        return DiffResult(inserted = inserted, updated = updated, deleted = deleted)
    }

    fun clearAll() {
        val resolver = context.contentResolver
        for ((_, rowId) in readOurExistingPrograms()) {
            val rowUri = ContentUris.withAppendedId(
                TvContractCompat.WatchNextPrograms.CONTENT_URI,
                rowId,
            )
            resolver.delete(rowUri, null, null)
        }
    }

    private fun readOurExistingPrograms(): Map<String, Long> {
        val resolver = context.contentResolver
        val projection = arrayOf(
            TvContractCompat.WatchNextPrograms._ID,
            TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID,
        )
        val result = mutableMapOf<String, Long>()
        resolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(TvContractCompat.WatchNextPrograms._ID)
            val extIdx = cursor.getColumnIndexOrThrow(
                TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID
            )
            while (cursor.moveToNext()) {
                val rowId = cursor.getLong(idIdx)
                val externalId = cursor.getString(extIdx) ?: continue
                result[externalId] = rowId
            }
        }
        return result
    }

    private fun WatchNextProgramFields.toContentValues(): ContentValues =
        WatchNextProgram.Builder()
            .setType(programType)
            .setWatchNextType(watchNextType)
            .setTitle(title)
            .setPosterArtUri(Uri.parse(posterArtUri))
            .setPosterArtAspectRatio(posterArtAspectRatio)
            .setLastEngagementTimeUtcMillis(lastEngagementTimeMs)
            .setIntentUri(Uri.parse(intentUri))
            .setInternalProviderId(externalId)
            .build()
            .toContentValues()

    data class DiffResult(val inserted: Int, val updated: Int, val deleted: Int)
}
