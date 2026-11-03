package org.prairieserver.prairie.android.cast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.prairieserver.prairie.cast.PrairieCastPlaybackRequest
import org.prairieserver.prairie.model.catalog.FileVersion
import org.prairieserver.prairie.model.catalog.SubtitleTrack

class PrairieCastRokuTracksTest {
    // Catalog order mixes embedded and external tracks; the server's combined
    // order puts the externals first.
    private val version = FileVersion(
        fileId = 7,
        subtitleTracks = listOf(
            SubtitleTrack(index = 2, language = "eng"),
            SubtitleTrack(index = 0, language = "fre", external = true),
            SubtitleTrack(index = 3, language = "spa"),
            SubtitleTrack(index = 1, language = "ger", external = true),
        ),
    )
    private val other = FileVersion(fileId = 8, subtitleTracks = listOf(SubtitleTrack(index = 0, language = "eng")))

    private fun launch(subtitle: Int?, fileId: Int? = 7, audio: Int? = 1) = PrairieCastPlaybackRequest(
        contentId = "movie-1",
        fileId = fileId,
        audioTrackIndex = audio,
        subtitleTrackIndex = subtitle,
        startFromBeginning = true,
    )

    @Test
    fun sendsTheCombinedSubtitleOrdinalOfTheLaunchedVersion() {
        val versions = listOf(other, version)
        assertEquals(listOf(2, 0, 3, 1), (0..3).map { PrairieCastRokuTracks.forRoku(launch(it), versions).subtitleTrackIndex })
        // The audio ordinal is the picker's position already.
        assertEquals(1, PrairieCastRokuTracks.forRoku(launch(1), versions).audioTrackIndex)
    }

    @Test
    fun keepsOffAndNoChoice() {
        assertEquals(-1, PrairieCastRokuTracks.forRoku(launch(-1), listOf(version)).subtitleTrackIndex)
        assertNull(PrairieCastRokuTracks.forRoku(launch(null), listOf(version)).subtitleTrackIndex)
    }

    @Test
    fun usesTheOnlyVersionWithoutAFileIdAndDropsWhatItCannotMap() {
        assertEquals(2, PrairieCastRokuTracks.forRoku(launch(0, fileId = null), listOf(version)).subtitleTrackIndex)
        assertNull(PrairieCastRokuTracks.forRoku(launch(0, fileId = null), listOf(other, version)).subtitleTrackIndex)
        assertNull(PrairieCastRokuTracks.forRoku(launch(9), listOf(version)).subtitleTrackIndex)
        assertNull(PrairieCastRokuTracks.forRoku(launch(0), emptyList()).subtitleTrackIndex)
    }
}
