package org.prairieserver.prairie.overlays

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OverlayRegistryValuesTest {
    private fun value(id: OverlayId, data: OverlayData): String? =
        assertNotNull(OverlayRegistry.def(id)).getValue(data)

    private fun icon(id: OverlayId, data: OverlayData): OverlayIconId? =
        OverlayRegistry.def(id)?.getIcon?.invoke(data)

    @Test
    fun resolutionMatchesWebSpellings() {
        assertEquals("4K", value(OverlayId.Resolution, OverlayData(resolution = "2160p")))
        assertEquals("8K", value(OverlayId.Resolution, OverlayData(resolution = "4320p")))
        assertEquals("8K", value(OverlayId.Resolution, OverlayData(resolution = "8k")))
        assertEquals("1080p", value(OverlayId.Resolution, OverlayData(resolution = "1080p")))
        assertEquals("SD", value(OverlayId.Resolution, OverlayData(resolution = "sd")))
        assertNull(value(OverlayId.Resolution, OverlayData()))
    }

    @Test
    fun techIconsUseExactWordmarks() {
        assertEquals(OverlayIconId.DolbyVision, icon(OverlayId.Hdr, OverlayData(hdr = "DV")))
        assertEquals(OverlayIconId.Hdr10, icon(OverlayId.Hdr, OverlayData(hdr = "HDR10")))
        assertEquals(OverlayIconId.Hdr, icon(OverlayId.Hdr, OverlayData(hdr = "HDR")))
        assertNull(icon(OverlayId.Hdr, OverlayData(hdr = "HLG")))
        assertNull(icon(OverlayId.Hdr, OverlayData()))
        assertEquals("HLG", value(OverlayId.Hdr, OverlayData(hdr = "HLG")))

        assertEquals(OverlayIconId.DolbyVision, icon(OverlayId.ResolutionHdr, OverlayData(resolution = "2160p", hdr = "DV")))
        assertNull(icon(OverlayId.ResolutionHdr, OverlayData(resolution = "2160p", hdr = "HDR10")))

        assertEquals(OverlayIconId.Atmos, icon(OverlayId.Audio, OverlayData(audio = "TrueHD Atmos")))
        assertEquals(OverlayIconId.Volume, icon(OverlayId.Audio, OverlayData(audio = "AAC")))
        assertNull(icon(OverlayId.Audio, OverlayData()))
        assertEquals("AAC", value(OverlayId.Audio, OverlayData(audio = "AAC")))

        assertEquals(OverlayIconId.Av1, icon(OverlayId.VideoCodec, OverlayData(videoCodec = "AV1")))
        assertEquals(OverlayIconId.Film, icon(OverlayId.VideoCodec, OverlayData(videoCodec = "HEVC")))
        assertNull(icon(OverlayId.VideoCodec, OverlayData()))
        assertEquals("HEVC", value(OverlayId.VideoCodec, OverlayData(videoCodec = "HEVC")))
    }

    @Test
    fun passThroughAndFlagOverlays() {
        val data = OverlayData(
            audioChannels = "5.1",
            container = "MKV",
            aspectRatio = "2.39",
            releaseType = "BluRay",
            edition = "Director's Cut",
            multiAudio = true,
            multiSub = true,
            contentRating = "PG-13",
            studio = "A24",
            network = "HBO",
            rtCertifiedFresh = true,
            imdbTop250 = 7,
            year = 2020,
        )
        assertEquals("5.1", value(OverlayId.AudioChannels, data))
        assertEquals("MKV", value(OverlayId.Container, data))
        assertEquals("2.39", value(OverlayId.AspectRatio, data))
        assertEquals("BluRay", value(OverlayId.ReleaseType, data))
        assertEquals("Director's Cut", value(OverlayId.Edition, data))
        assertEquals("Multi-Audio", value(OverlayId.MultiAudio, data))
        assertEquals("CC", value(OverlayId.MultiSub, data))
        assertEquals("PG-13", value(OverlayId.ContentRating, data))
        assertEquals("A24", value(OverlayId.Studio, data))
        assertEquals("HBO", value(OverlayId.Network, data))
        assertEquals("Certified Fresh", value(OverlayId.RtCertifiedFresh, data))
        assertEquals("#7", value(OverlayId.ImdbTop250, data))
        assertEquals("2020", value(OverlayId.Year, data))

        val empty = OverlayData(multiAudio = false, multiSub = false, rtCertifiedFresh = false, year = 0)
        assertNull(value(OverlayId.MultiAudio, empty))
        assertNull(value(OverlayId.MultiSub, empty))
        assertNull(value(OverlayId.RtCertifiedFresh, empty))
        assertNull(value(OverlayId.ImdbTop250, empty))
        assertNull(value(OverlayId.Year, empty))
        assertNull(value(OverlayId.Year, OverlayData()))
    }

    @Test
    fun ratingsFormatting() {
        assertEquals("7.5", value(OverlayId.RatingTmdb, OverlayData(ratingTmdb = 7.5)))
        assertNull(value(OverlayId.RatingTmdb, OverlayData()))
        assertEquals("93%", value(OverlayId.RatingRt, OverlayData(ratingRtCritic = 93)))
        assertNull(value(OverlayId.RatingRt, OverlayData()))
        assertEquals("88%", value(OverlayId.RatingRtAudience, OverlayData(ratingRtAudience = 88)))
        assertNull(value(OverlayId.RatingRtAudience, OverlayData()))
    }

    @Test
    fun showStatusSpellings() {
        assertEquals("Ended", value(OverlayId.ShowStatus, OverlayData(showStatus = "ended")))
        assertEquals("Cancelled", value(OverlayId.ShowStatus, OverlayData(showStatus = "canceled")))
        assertEquals("Cancelled", value(OverlayId.ShowStatus, OverlayData(showStatus = "Cancelled")))
        assertEquals("Upcoming", value(OverlayId.ShowStatus, OverlayData(showStatus = "planned")))
        assertEquals("Returning", value(OverlayId.ShowStatus, OverlayData(showStatus = "Returning Series")))
        assertEquals("Pilot", value(OverlayId.ShowStatus, OverlayData(showStatus = " Pilot ")))
        assertNull(value(OverlayId.ShowStatus, OverlayData(showStatus = "  ")))
    }

    @Test
    fun everyDefinitionEvaluatesAgainstPopulatedAndEmptyData() {
        val populated = OverlayData(
            resolution = "1080p", hdr = "HDR10", audio = "EAC3", audioChannels = "7.1", videoCodec = "H264",
            container = "MP4", aspectRatio = "1.78", releaseType = "WEB", edition = "Extended",
            multiAudio = true, multiSub = true, ratingImdb = 8.1, ratingTmdb = 7.9, ratingRtCritic = 90,
            ratingRtAudience = 80, contentRating = "R", year = 1999, runtime = 136, originalLanguage = "en",
            studio = "WB", network = "AMC", showStatus = "ended", imdbTop250 = 12, rtCertifiedFresh = true,
        )
        for (def in OverlayRegistry.all) {
            assertNotNull(def.getValue(populated), "no value for ${def.id}")
            def.getValue(OverlayData())
            def.getIcon?.invoke(populated)
            def.getIcon?.invoke(OverlayData())
        }
    }
}
