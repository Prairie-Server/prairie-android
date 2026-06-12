package com.continuum.app.tv.ui.screens.detail

import com.continuum.app.model.audiobook.AudiobookMetadata
import com.continuum.app.model.catalog.ItemDetail
import com.continuum.app.model.ebook.MediaPerson
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
                publisher = "Continuum Press",
                narrators = listOf(MediaPerson(name = "Nia Narrator")),
            ),
        )

        assertEquals(
            listOf("Audiobook", "Continuum Press", "Narrated by Nia Narrator"),
            TvDetailMetadata.sourceTokens(detail),
        )
    }
}
