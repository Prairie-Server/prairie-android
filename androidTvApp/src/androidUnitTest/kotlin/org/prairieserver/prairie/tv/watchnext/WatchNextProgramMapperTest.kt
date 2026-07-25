package org.prairieserver.prairie.tv.watchnext

import org.prairieserver.prairie.model.section.SectionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchNextProgramMapperTest {

    private fun sectionItem(
        id: String = "tt1234",
        title: String = "Test Item",
        type: String = "movie",
        posterUrl: String? = "https://example.com/poster.jpg",
        backdropUrl: String? = "https://example.com/backdrop.jpg",
        progressUpdatedAt: String? = null,
    ) = SectionItem(
        contentId = id,
        title = title,
        type = type,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        progressUpdatedAt = progressUpdatedAt,
    )

    @Test
    fun `continue_watching section maps to CONTINUE type with play URI`() {
        val fields = WatchNextProgramMapper.map(
            sectionItem(id = "abc"),
            sectionType = "continue_watching",
        )
        assertEquals(WatchNextProgramMapper.WATCH_NEXT_TYPE_CONTINUE, fields?.watchNextType)
        assertEquals("prairie://play/abc?type=movie", fields?.intentUri)
        assertEquals("continue_watching:abc", fields?.externalId)
    }

    @Test
    fun `next_up section maps to NEXT type with item URI`() {
        val fields = WatchNextProgramMapper.map(
            sectionItem(id = "xyz"),
            sectionType = "next_up",
        )
        assertEquals(WatchNextProgramMapper.WATCH_NEXT_TYPE_NEXT, fields?.watchNextType)
        assertEquals("prairie://item/xyz", fields?.intentUri)
    }

    @Test
    fun `unknown sectionType returns null`() {
        val fields = WatchNextProgramMapper.map(
            sectionItem(),
            sectionType = "random_recommendations",
        )
        assertNull(fields)
    }

    @Test
    fun `prefers backdrop URL over poster URL`() {
        val fields = WatchNextProgramMapper.map(
            sectionItem(posterUrl = "P", backdropUrl = "B"),
            sectionType = "continue_watching",
        )
        assertEquals("B", fields?.posterArtUri)
    }

    @Test
    fun `falls back to poster when backdrop missing`() {
        val fields = WatchNextProgramMapper.map(
            sectionItem(posterUrl = "P", backdropUrl = null),
            sectionType = "continue_watching",
        )
        assertEquals("P", fields?.posterArtUri)
    }

    @Test
    fun `returns null when both poster and backdrop missing`() {
        val fields = WatchNextProgramMapper.map(
            sectionItem(posterUrl = null, backdropUrl = null),
            sectionType = "continue_watching",
        )
        assertNull(fields)
    }

    @Test
    fun `parses ISO-8601 progressUpdatedAt into lastEngagementTimeMs`() {
        val fields = WatchNextProgramMapper.map(
            sectionItem(progressUpdatedAt = "2026-01-01T00:00:00Z"),
            sectionType = "continue_watching",
        )
        // 2026-01-01T00:00:00Z = 1767225600000 ms
        assertEquals(1_767_225_600_000L, fields?.lastEngagementTimeMs)
    }

    @Test
    fun `leaves lastEngagementTimeMs null when progressUpdatedAt missing`() {
        val fields = WatchNextProgramMapper.map(
            sectionItem(progressUpdatedAt = null),
            sectionType = "continue_watching",
        )
        // Never re-stamped to "now" — the provider keeps any prior value.
        assertNull(fields?.lastEngagementTimeMs)
    }

    @Test
    fun `leaves lastEngagementTimeMs null when progressUpdatedAt unparseable`() {
        val fields = WatchNextProgramMapper.map(
            sectionItem(progressUpdatedAt = "not-a-date"),
            sectionType = "continue_watching",
        )
        assertNull(fields?.lastEngagementTimeMs)
    }

    @Test
    fun `movie type maps to PROGRAM_TYPE_MOVIE with 16-9 art`() {
        val fields = WatchNextProgramMapper.map(
            sectionItem(type = "movie"),
            sectionType = "continue_watching",
        )
        assertEquals(WatchNextProgramMapper.PROGRAM_TYPE_MOVIE, fields?.programType)
        assertEquals(WatchNextProgramMapper.ASPECT_RATIO_16_9, fields?.posterArtAspectRatio)
    }

    @Test
    fun `episode type maps to PROGRAM_TYPE_TV_EPISODE`() {
        val fields = WatchNextProgramMapper.map(
            sectionItem(type = "episode"),
            sectionType = "next_up",
        )
        assertEquals(WatchNextProgramMapper.PROGRAM_TYPE_TV_EPISODE, fields?.programType)
    }

    @Test
    fun `audiobook type maps to PROGRAM_TYPE_ALBUM with square art`() {
        val fields = WatchNextProgramMapper.map(
            sectionItem(type = "audiobook"),
            sectionType = "continue_watching",
        )
        assertEquals(WatchNextProgramMapper.PROGRAM_TYPE_ALBUM, fields?.programType)
        assertEquals(WatchNextProgramMapper.ASPECT_RATIO_1_1, fields?.posterArtAspectRatio)
    }

    @Test
    fun `play intent carries url-encoded item type for deep-link routing`() {
        val fields = WatchNextProgramMapper.map(
            sectionItem(id = "bk1", type = "audiobook"),
            sectionType = "continue_watching",
        )
        assertEquals("prairie://play/bk1?type=audiobook", fields?.intentUri)
    }
}
