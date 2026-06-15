package com.continuum.app.tv.ui.screens.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(UnstableApi::class)
class PlayerTrackEntriesTest {

    @Test
    fun textTracksExposeEveryTrackInsideMedia3Group() {
        val group = TrackGroup(
            subtitle(label = "English", language = "en"),
            subtitle(label = "English → Dutch (AI) (translated)", language = "en"),
            subtitle(label = "English Forced", language = "en", forced = true),
        )
        val tracks = Tracks(
            listOf(
                Tracks.Group(
                    group,
                    false,
                    intArrayOf(C.FORMAT_HANDLED, C.FORMAT_HANDLED, C.FORMAT_HANDLED),
                    booleanArrayOf(false, true, false),
                ),
            ),
        )

        val entries = extractTrackEntries(tracks, C.TRACK_TYPE_TEXT)

        assertEquals(3, entries.size)
        assertEquals(listOf(0, 1, 2), entries.map { it.index })
        assertEquals(listOf(false, true, false), entries.map { it.isSelected })
        assertTrue(entries[1].displayLabel.contains("AI Translation"))
        assertTrue(entries[2].displayLabel.contains("Forced"))
    }

    @Test
    fun subtitleSelectionStateUpdatesOptimistically() {
        val tracks = listOf(
            PlayerTrackEntry(index = 0, label = "English", language = "en", isSelected = false),
            PlayerTrackEntry(index = 1, label = "Dutch", language = "nl", isSelected = false),
        )

        val selected = subtitleTracksWithSelection(tracks, selectedIndex = 1)
        val disabled = subtitleTracksWithSelection(selected, selectedIndex = -1)

        assertEquals(listOf(false, true), selected.map { it.isSelected })
        assertEquals(listOf(false, false), disabled.map { it.isSelected })
    }

    @Test
    fun textTracksExposeEmbeddedBitmapSubtitlesAndPreserveMedia3FlatIndex() {
        val group = TrackGroup(
            Format.Builder()
                .setLabel("English SDH")
                .setLanguage("en")
                .setSampleMimeType("application/x-media3-cues")
                .setCodecs(MimeTypes.APPLICATION_PGS)
                .build(),
            subtitle(label = "English VTT", language = "en"),
        )
        val tracks = Tracks(
            listOf(
                Tracks.Group(
                    group,
                    false,
                    intArrayOf(C.FORMAT_HANDLED, C.FORMAT_HANDLED),
                    booleanArrayOf(false, false),
                ),
            ),
        )

        val entries = extractTrackEntries(tracks, C.TRACK_TYPE_TEXT)

        assertEquals(2, entries.size)
        assertEquals(listOf(0, 1), entries.map { it.index })
        assertEquals(listOf("English SDH", "English VTT"), entries.map { it.label })
        assertTrue(entries[0].displayLabel.contains("PGS"))
    }

    private fun subtitle(
        label: String,
        language: String,
        forced: Boolean = false,
    ): Format = Format.Builder()
        .setLabel(label)
        .setLanguage(language)
        .setSampleMimeType(MimeTypes.APPLICATION_SUBRIP)
        .setSelectionFlags(if (forced) C.SELECTION_FLAG_FORCED else 0)
        .build()
}
