package org.prairieserver.prairie.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubtitleCodecAndLanguageTest {
    @Test
    fun canonicalSubtitleCodecFamilyNormalizesAliases() {
        assertNull(canonicalSubtitleCodecFamily(null))
        assertNull(canonicalSubtitleCodecFamily("   "))
        assertNull(canonicalSubtitleCodecFamily("!!!"))

        assertEquals("pgs", canonicalSubtitleCodecFamily(" application/pgs "))
        assertEquals("vobsub", canonicalSubtitleCodecFamily("dvd_subtitle"))
        assertEquals("vobsub", canonicalSubtitleCodecFamily("VobSub"))
        assertEquals("dvbsub", canonicalSubtitleCodecFamily("dvb_sub"))
        assertEquals("subrip", canonicalSubtitleCodecFamily("text/x-subrip"))
        assertEquals("subrip", canonicalSubtitleCodecFamily("english.srt"))
        assertEquals("webvtt", canonicalSubtitleCodecFamily("text/vtt"))
        assertEquals("webvtt", canonicalSubtitleCodecFamily("captions.vtt"))
        assertEquals("webvtt", canonicalSubtitleCodecFamily("textvtt"))
        assertEquals("tx3g", canonicalSubtitleCodecFamily("mov_text"))
        assertEquals("ssa", canonicalSubtitleCodecFamily("ass"))
        assertEquals("ssa", canonicalSubtitleCodecFamily("text/x-ssa"))
        assertEquals("ttml", canonicalSubtitleCodecFamily("application/ttml+xml"))
        assertEquals("cea608", canonicalSubtitleCodecFamily("eia_608"))
        assertEquals("cea708", canonicalSubtitleCodecFamily("cea_708"))
        assertEquals("customcodec", canonicalSubtitleCodecFamily("custom_codec"))
    }

    @Test
    fun textAndBitmapMountabilityFollowCanonicalFamilies() {
        assertTrue(isTextSubtitleCodecFamily("subrip"))
        assertTrue(isTextSubtitleCodecFamily("webvtt"))
        assertTrue(isTextSubtitleCodecFamily("ssa"))
        assertTrue(isTextSubtitleCodecFamily("ttml"))
        assertTrue(isTextSubtitleCodecFamily("tx3g"))
        assertFalse(isTextSubtitleCodecFamily("pgs"))
        assertFalse(isTextSubtitleCodecFamily(null))

        assertTrue(isClientMountableBitmapCodecFamily("application/pgs"))
        assertFalse(isClientMountableBitmapCodecFamily("vobsub"))
        assertFalse(isClientMountableBitmapCodecFamily("dvbsub"))
        assertFalse(isClientMountableBitmapCodecFamily("subrip"))
    }

    @Test
    fun canonicalSubtitleLanguageMapsIsoAliasesAndDropsUndetermined() {
        assertNull(canonicalSubtitleLanguage(null))
        assertNull(canonicalSubtitleLanguage(" "))
        assertNull(canonicalSubtitleLanguage("und"))
        assertNull(canonicalSubtitleLanguage("UND"))

        assertEquals("en", canonicalSubtitleLanguage("eng"))
        assertEquals("en", canonicalSubtitleLanguage("EN-US"))
        assertEquals("es", canonicalSubtitleLanguage("spa"))
        assertEquals("fr", canonicalSubtitleLanguage("fre"))
        assertEquals("fr", canonicalSubtitleLanguage("fra"))
        assertEquals("de", canonicalSubtitleLanguage("ger"))
        assertEquals("de", canonicalSubtitleLanguage("deu"))
        assertEquals("nl", canonicalSubtitleLanguage("dut"))
        assertEquals("nl", canonicalSubtitleLanguage("nld"))
        assertEquals("ja", canonicalSubtitleLanguage("jpn"))
        assertEquals("da", canonicalSubtitleLanguage("dan"))
        assertEquals("pt", canonicalSubtitleLanguage("pt_BR"))
        assertEquals("ko", canonicalSubtitleLanguage("ko"))
    }
}
