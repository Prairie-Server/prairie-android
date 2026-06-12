package com.continuum.app.tv.ui.screens.player

import com.continuum.app.model.playback.PlayerSubtitleInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvSubtitleAiSupportTest {

    private fun track(codec: String?, source: String?) = PlayerSubtitleInfo(
        index = 0, language = "en", codec = codec, label = null,
        source = source, forced = null, url = "/u",
    )

    @Test
    fun embeddedTextTracksAreTranslatable() {
        assertTrue(isTranslatableSource(track("subrip", "embedded")))
        assertTrue(isTranslatableSource(track("ass", "embedded"))) // ffmpeg-extractable
    }

    @Test
    fun embeddedBitmapTracksAreNot() {
        assertFalse(isTranslatableSource(track("hdmv_pgs_subtitle", "embedded")))
        assertFalse(isTranslatableSource(track("dvd_subtitle", "embedded")))
    }

    @Test
    fun externalAndDownloadedMustBeParseableText() {
        assertTrue(isTranslatableSource(track("srt", "external")))
        assertTrue(isTranslatableSource(track("vtt", "downloaded")))
        assertFalse(isTranslatableSource(track("ass", "external"))) // server can't parse external ASS
        assertFalse(isTranslatableSource(track(null, "external")))
    }
}
