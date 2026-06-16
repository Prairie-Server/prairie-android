package com.continuum.app.common.data.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.intOrNull

/**
 * A typed, coalescible entry for the offline-first sync outbox (Track B).
 *
 * The outbox is limited to the four ops today's server APIs can represent:
 * SET_POSITION (PersonalDataApi.syncProgress), SET_WATCHED (markWatched/Unwatched),
 * SET_RATING (setRating/deleteRating), SET_FAVORITE (addFavorite/removeFavorite).
 * SET_TRACK_SELECTION has no server projection API (local-only) and CFI rides the
 * existing EbookProgressSyncer, so neither is an outbox op.
 *
 * [coalesceKey] lets a newer op replace older un-synced ops of the same kind+target
 * (e.g. repeated SET_POSITION). Pure (JSON payloads) so it is unit-tested.
 */
data class OutboxOperation(
    val kind: String,
    val coalesceKey: String,
    val payloadJson: String,
    val createdAtMs: Long,
) {
    companion object {
        const val SET_POSITION = "SET_POSITION"
        const val SET_WATCHED = "SET_WATCHED"
        const val SET_RATING = "SET_RATING"
        const val SET_FAVORITE = "SET_FAVORITE"

        private val json = Json

        fun setPosition(
            profileId: String,
            contentId: String,
            fileId: Int,
            positionSeconds: Double,
            durationSeconds: Double?,
            atMs: Long,
        ): OutboxOperation = OutboxOperation(
            kind = SET_POSITION,
            coalesceKey = "$profileId|$contentId|$fileId|$SET_POSITION",
            payloadJson = """{"position":$positionSeconds,"duration":${durationSeconds ?: "null"}}""",
            createdAtMs = atMs,
        )

        fun setWatched(profileId: String, contentId: String, watched: Boolean, atMs: Long): OutboxOperation =
            OutboxOperation(SET_WATCHED, "$profileId|$contentId|$SET_WATCHED", JsonPrimitive(watched).toString(), atMs)

        fun setRating(profileId: String, contentId: String, rating: Int?, atMs: Long): OutboxOperation =
            OutboxOperation(
                SET_RATING,
                "$profileId|$contentId|$SET_RATING",
                if (rating == null) "null" else JsonPrimitive(rating).toString(),
                atMs,
            )

        fun setFavorite(profileId: String, contentId: String, favorite: Boolean, atMs: Long): OutboxOperation =
            OutboxOperation(SET_FAVORITE, "$profileId|$contentId|$SET_FAVORITE", JsonPrimitive(favorite).toString(), atMs)

        fun decodeBooleanPayload(payloadJson: String): Boolean =
            json.parseToJsonElement(payloadJson).let { (it as JsonPrimitive).boolean }

        /** Rating payload is the int value, or null for "clear rating". */
        fun decodeRatingPayload(payloadJson: String): Int? =
            json.parseToJsonElement(payloadJson).let { (it as JsonPrimitive).intOrNull }
    }
}
