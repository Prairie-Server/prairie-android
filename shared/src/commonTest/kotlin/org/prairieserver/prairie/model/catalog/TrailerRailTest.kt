package org.prairieserver.prairie.model.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class TrailerRailTest {
    private fun detail(videos: List<ItemVideo>? = null, extras: List<ItemExtra>? = null) =
        ItemDetail(contentId = "m1", type = "movie", title = "Film", videos = videos, extras = extras)

    @Test
    fun keepsYoutubeVideosThenLocalExtrasWithoutDuplicates() {
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
        assertEquals(listOf("remote:YouTube:abc", "local:x1"), entries.map { it.key })
        val remote = assertIs<TrailerRailEntry.Remote>(entries[0])
        assertEquals("Official Trailer", remote.title)
        assertEquals("trailer", remote.kind)
        val local = assertIs<TrailerRailEntry.Local>(entries[1])
        assertEquals("Behind the Scenes", local.title)
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
        assertEquals("Trailer", extraKindLabel("TRAILER"))
        assertEquals("Teaser", extraKindLabel("teaser"))
        assertEquals("Featurette", extraKindLabel("featurette"))
        assertEquals("Behind the Scenes", extraKindLabel("behind-the-scenes"))
        assertEquals("Deleted Scene", extraKindLabel("deleted_scene"))
        assertEquals("Deleted Scene", extraKindLabel("deleted-scene"))
        assertEquals("Interview", extraKindLabel("interview"))
        assertEquals("Short Film", extraKindLabel("short_film"))
        assertEquals("Extra", extraKindLabel(" _ "))
        assertEquals("Blank Title", TrailerRailEntry.Local(ItemExtra(contentId = "y", kind = "blank-title", title = " ")).title)
    }
}
