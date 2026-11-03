package org.prairieserver.prairie.model.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeriesSubtitleChoiceOfTest {
    private val row = PlayerSubtitleInfo(
        index = 4, language = "fr", codec = "subrip", label = "French", source = "external", url = "/s/4.srt",
    )
    private val media = SubtitleMediaIdentity(
        label = " Spanish ", language = " es ", codecFamily = " webvtt ", forced = true, hearingImpaired = true,
    )

    @Test
    fun identitiesListedInTheInventoryUseTheirRow() {
        assertEquals(SeriesSubtitleChoice.Off, SeriesSubtitleChoice.of(SubtitleIdentity.Off, listOf(row)))
        val expected = SeriesSubtitleChoice.of(row)
        assertEquals(4, expected.subtitleTrackIndex)
        assertEquals("always", expected.subtitleMode)
        assertEquals(expected, SeriesSubtitleChoice.of(SubtitleIdentity.ServerSidecar(4), listOf(row)))
        assertEquals(expected, SeriesSubtitleChoice.of(SubtitleIdentity.ServerBurnIn(4), listOf(row)))
        assertEquals(expected, SeriesSubtitleChoice.of(SubtitleIdentity.Embedded(4, media), listOf(row)))
        assertNull(SeriesSubtitleChoice.of(SubtitleIdentity.ServerSidecar(9), listOf(row)))
        assertNull(SeriesSubtitleChoice.of(SubtitleIdentity.Embedded(9, media), listOf(row)))
    }

    @Test
    fun localTracksAreDescribedFromTheirMedia() {
        val downloaded = SeriesSubtitleChoice.of(SubtitleIdentity.Downloaded(7, media), listOf(row))!!
        assertEquals(-1, downloaded.subtitleTrackIndex)
        assertEquals("always", downloaded.subtitleMode)
        val signature = downloaded.trackSignature!!
        assertEquals("downloaded", signature.source)
        assertEquals("es", signature.language)
        assertEquals("webvtt", signature.codec)
        assertEquals("Spanish", signature.label)
        assertTrue(signature.forced)
        assertTrue(signature.hearingImpaired)

        val local = SeriesSubtitleChoice.of(SubtitleIdentity.LocalMedia3(SubtitleMediaIdentity(language = "de")), emptyList())!!
        assertNull(local.trackSignature!!.source)
        assertNull(local.trackSignature!!.label)
        assertFalse(local.trackSignature!!.forced)

        assertNull(SeriesSubtitleChoice.of(SubtitleIdentity.LocalMedia3(SubtitleMediaIdentity(label = " ")), emptyList()))
    }
}
