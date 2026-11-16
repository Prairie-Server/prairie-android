package org.prairieserver.prairie.model.catalog

import org.prairieserver.prairie.common.i18n.english
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class TrailerRailTest {
    private fun detail(videos: List<ItemVideo>? = null, extras: List<ItemExtra>? = null) =
        ItemDetail(contentId = "m1", type = "movie", title = "Film", videos = videos, extras = extras)

    @Test
    fun keepsLocalExtrasThenYoutubeVideosWithoutDuplicates() {
        val trailer = ItemVideo(kind = "trailer", site = "YouTube", siteKey = "abc", name = "  Official Trailer ")
        val entries = trailerRailEntries(
            detail(
                videos = listOf(
                    trailer,
                    trailer.copy(name = "Duplicate"),
                    ItemVideo(kind = "teaser", site = "vimeo", siteKey = "v1"),
                    ItemVideo(kind = "teaser", site = "youtube", siteKey = " "),
                ),
                extras = listOf(ItemExtra(contentId = "x1", kind = "behind_the_scenes", durationSeconds = 3725)),
            ),
        )
        // Local extras first (upstream #640).
        assertEquals(listOf("local:x1", "remote:YouTube:abc"), entries.map { it.key })
        val remote = assertIs<TrailerRailEntry.Remote>(entries[1])
        assertEquals("Official Trailer", remote.title.english())
        assertEquals("trailer", remote.kind)
        val local = assertIs<TrailerRailEntry.Local>(entries[0])
        assertEquals("Behind the Scenes", local.title.english())
        assertEquals("behind_the_scenes", local.kind)
        assertEquals(emptyList<TrailerRailEntry>(), trailerRailEntries(detail()))
    }

    @Test
    fun durationLabelsOnlyForTimedLocalExtras() {
        fun local(seconds: Int?) = TrailerRailEntry.Local(ItemExtra(contentId = "x", kind = "clip", durationSeconds = seconds))
        assertEquals("1:02:05", trailerRailDurationLabel(local(3725)))
        assertEquals("2:05", trailerRailDurationLabel(local(125)))
        assertNull(trailerRailDurationLabel(local(0)))
        assertNull(trailerRailDurationLabel(local(null)))
        assertNull(trailerRailDurationLabel(TrailerRailEntry.Remote(ItemVideo(kind = "trailer", site = "youtube", siteKey = "k"))))
        assertEquals("https://i.ytimg.com/vi/k/hqdefault.jpg", youtubeThumbnailUrl("k"))
    }

    @Test
    fun kindLabels() {
        assertEquals("Trailer", extraKindLabel("TRAILER").english())
        assertEquals("Teaser", extraKindLabel("teaser").english())
        assertEquals("Featurette", extraKindLabel("featurette").english())
        assertEquals("Behind the Scenes", extraKindLabel("behind-the-scenes").english())
        assertEquals("Deleted Scene", extraKindLabel("deleted_scene").english())
        assertEquals("Deleted Scene", extraKindLabel("deleted-scene").english())
        assertEquals("Interview", extraKindLabel("interview").english())
        assertEquals("Short Film", extraKindLabel("short_film").english())
        assertEquals("Extra", extraKindLabel(" _ ").english())
        assertEquals("Blank Title", TrailerRailEntry.Local(ItemExtra(contentId = "y", kind = "blank-title", title = " ")).title.english())
    }
}
