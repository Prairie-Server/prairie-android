package org.siloserver.silo.tv.ui.screens.detail

import org.siloserver.silo.model.audiobook.AudiobookMetadata
import org.siloserver.silo.model.catalog.FileVersion
import org.siloserver.silo.model.catalog.ItemDetail
import org.siloserver.silo.model.ebook.MediaPerson
import kotlin.test.Test
import kotlin.test.assertEquals

class TvDetailMetadataTest {
    @Test
    fun audiobookSourceTokensIncludeTypePublisherAndNarrator() {
        val detail = ItemDetail(
            contentId = "a1",
            type = "audiobook",
            title = "Audio",
            audiobook = AudiobookMetadata(
                publisher = "Silo Press",
                narrators = listOf(MediaPerson(name = "Nia Narrator")),
            ),
        )

        assertEquals(
            listOf("Audiobook", "Silo Press", "Narrated by Nia Narrator"),
            TvDetailMetadata.sourceTokens(detail),
        )
    }

    @Test
    fun factsLineUsesPreferredQualityForVersionBadges() {
        val detail = ItemDetail(
            contentId = "m1",
            type = "movie",
            title = "Movie",
            versions = listOf(
                FileVersion(fileId = 1080, resolution = "1080p"),
                FileVersion(fileId = 2160, resolution = "2160p", hdr = true),
            ),
        )

        assertEquals(
            listOf(TvHeroFactToken.Chip("HD")),
            TvDetailMetadata.factsLine(detail, preferredQuality = "1080p"),
        )
    }
}
