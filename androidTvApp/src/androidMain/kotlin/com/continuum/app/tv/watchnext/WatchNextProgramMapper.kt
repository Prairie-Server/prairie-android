package com.continuum.app.tv.watchnext

import com.continuum.app.model.section.SectionItem
import java.time.Instant
import java.time.format.DateTimeParseException

data class WatchNextProgramFields(
    val externalId: String,
    val title: String,
    val watchNextType: Int,
    val programType: Int,
    val posterArtUri: String,
    val posterArtAspectRatio: Int,
    val lastEngagementTimeMs: Long,
    val intentUri: String,
)

object WatchNextProgramMapper {

    fun map(item: SectionItem, sectionType: String, nowMs: Long): WatchNextProgramFields? {
        val poster = item.backdropUrl ?: item.posterUrl ?: return null
        val watchNextType = when (sectionType) {
            "continue_watching" -> WATCH_NEXT_TYPE_CONTINUE
            "next_up" -> WATCH_NEXT_TYPE_NEXT
            else -> return null
        }
        val programType = when (item.type) {
            "movie" -> PROGRAM_TYPE_MOVIE
            "episode", "show", "series" -> PROGRAM_TYPE_TV_EPISODE
            else -> PROGRAM_TYPE_TV_EPISODE
        }
        val intentUri = when (watchNextType) {
            WATCH_NEXT_TYPE_CONTINUE -> "continuum://play/${item.contentId}"
            else -> "continuum://item/${item.contentId}"
        }
        return WatchNextProgramFields(
            externalId = "$sectionType:${item.contentId}",
            title = item.title,
            watchNextType = watchNextType,
            programType = programType,
            posterArtUri = poster,
            posterArtAspectRatio = ASPECT_RATIO_16_9,
            lastEngagementTimeMs = parseProgressTimestamp(item.progressUpdatedAt) ?: nowMs,
            intentUri = intentUri,
        )
    }

    private fun parseProgressTimestamp(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return try {
            Instant.parse(iso).toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }
    }

    const val WATCH_NEXT_TYPE_CONTINUE = 0
    const val WATCH_NEXT_TYPE_NEXT = 1
    const val PROGRAM_TYPE_MOVIE = 0
    const val PROGRAM_TYPE_TV_EPISODE = 3
    const val ASPECT_RATIO_16_9 = 0
}
