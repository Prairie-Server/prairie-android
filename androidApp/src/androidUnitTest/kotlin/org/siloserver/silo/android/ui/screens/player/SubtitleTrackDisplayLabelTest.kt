package org.siloserver.silo.android.ui.screens.player

import org.siloserver.silo.model.playback.PlayerSubtitleInfo
import org.siloserver.silo.player.formatSubtitleTrackDisplayLabel
import kotlin.test.Test
import kotlin.test.assertEquals

class SubtitleTrackDisplayLabelTest {

    @Test
    fun mobileTrackSheetUsesFriendlySubtitleLabels() {
        val label = subtitleTrackLabel(
            sub = PlayerSubtitleInfo(
                index = 0,
                language = "eng",
                codec = null,
                label = "SUBRIP",
                forced = null,
                url = "/stream/subtitles/0.srt",
            ),
            index = 0,
        )

        assertEquals("English • SRT", label)
    }

    @Test
    fun sharedFormatterMarksAiTranslations() {
        val label = formatSubtitleTrackDisplayLabel(
            rawLabel = "English → Dutch (AI) (translated)",
            language = "nl",
            codecOrMime = "srt",
            isForced = false,
            index = 1,
        )

        assertEquals("Dutch • AI Translation • SRT", label)
    }
}
