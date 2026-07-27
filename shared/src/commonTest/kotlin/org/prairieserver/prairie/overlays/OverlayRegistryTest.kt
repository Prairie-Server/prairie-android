package org.prairieserver.prairie.overlays

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OverlayRegistryTest {

    @Test
    fun defFor_resolvesEveryId() {
        for (id in OverlayId.entries) {
            val def = OverlayRegistry.def(id)
            assertTrue(def != null, "no def for $id")
            assertEquals(id, def.id)
        }
    }

    @Test
    fun defsIn_groupByCategory_andCoverAll() {
        var total = 0
        for (category in OverlayCategory.entries) {
            val defs = OverlayRegistry.defs(category)
            assertTrue(defs.all { it.category == category })
            total += defs.size
        }
        assertEquals(OverlayRegistry.all.size, total)
    }

    @Test
    fun resolutionHdr_suppressesStandalones_whenEnabled() {
        val base = OverlaySchema.buildDefaults()
        val items = base.items.toMutableMap()
        items[OverlayId.ResolutionHdr] = items.getValue(OverlayId.ResolutionHdr).copy(enabled = true)
        val prefs = base.copy(items = items)
        assertTrue(OverlayRegistry.isSuppressed(OverlayId.Resolution, prefs))
        assertTrue(OverlayRegistry.isSuppressed(OverlayId.Hdr, prefs))
        assertTrue(!OverlayRegistry.isSuppressed(OverlayId.Audio, prefs))
    }

    @Test
    fun enabled_respectsUserOrder() {
        val base = OverlaySchema.buildDefaults()
        val ordered = base.copy(order = listOf(OverlayId.Audio, OverlayId.Hdr, OverlayId.Resolution))
        val ids = OverlayRegistry.enabled(OverlayPosition.TopLeft, ordered).map { it.id }
        assertEquals(listOf(OverlayId.Audio, OverlayId.Hdr, OverlayId.Resolution), ids)
    }

    @Test
    fun enabled_fallsBackToRegistryOrder_whenOrderEmpty() {
        val prefs = OverlaySchema.buildDefaults()
        val ids = OverlayRegistry.enabled(OverlayPosition.TopLeft, prefs).map { it.id }
        assertEquals(listOf(OverlayId.Resolution, OverlayId.Hdr, OverlayId.Audio), ids)
    }

    @Test
    fun resolutionHdr_getValue_combinesPretty() {
        val def = OverlayRegistry.def(OverlayId.ResolutionHdr)!!
        assertEquals("4K DV", def.getValue(OverlayData(resolution = "2160p", hdr = "DV")))
        assertEquals("4K HDR", def.getValue(OverlayData(resolution = "4k", hdr = "HDR10")))
        assertEquals("8K", def.getValue(OverlayData(resolution = "4320p", hdr = null)))
        assertEquals("720p", def.getValue(OverlayData(resolution = "720p", hdr = null)))
        assertEquals("1080p", def.getValue(OverlayData(resolution = "1080p", hdr = null)))
        assertNull(def.getValue(OverlayData(resolution = null)))
    }

    @Test
    fun imdbRating_getValue_formatsOneDecimal() {
        val def = OverlayRegistry.def(OverlayId.RatingImdb)!!
        assertEquals("8.4", def.getValue(OverlayData(ratingImdb = 8.42)))
        assertEquals("7.0", def.getValue(OverlayData(ratingImdb = 7.0)))
        assertNull(def.getValue(OverlayData(ratingImdb = null)))
    }

    @Test
    fun runtime_getValue_formatsHoursMinutes() {
        val def = OverlayRegistry.def(OverlayId.Runtime)!!
        assertEquals("2h 22m", def.getValue(OverlayData(runtime = 142)))
        assertEquals("45m", def.getValue(OverlayData(runtime = 45)))
        assertNull(def.getValue(OverlayData(runtime = 0)))
    }

    @Test
    fun techRatingAndMetadataGetValuesCoverHelpers() {
        assertEquals("HDR10", OverlayRegistry.def(OverlayId.Hdr)!!.getValue(OverlayData(hdr = "HDR10")))
        assertEquals(OverlayIconId.DolbyVision, OverlayRegistry.def(OverlayId.Hdr)!!.getIcon!!(OverlayData(hdr = "DV")))
        assertEquals(OverlayIconId.Hdr10, OverlayRegistry.def(OverlayId.Hdr)!!.getIcon!!(OverlayData(hdr = "HDR10")))
        assertEquals(OverlayIconId.Hdr, OverlayRegistry.def(OverlayId.Hdr)!!.getIcon!!(OverlayData(hdr = "HLG")))

        assertEquals("TrueHD Atmos", OverlayRegistry.def(OverlayId.Audio)!!.getValue(OverlayData(audio = "TrueHD Atmos")))
        assertEquals(OverlayIconId.Atmos, OverlayRegistry.def(OverlayId.Audio)!!.getIcon!!(OverlayData(audio = "Atmos")))
        assertEquals(OverlayIconId.Volume, OverlayRegistry.def(OverlayId.Audio)!!.getIcon!!(OverlayData(audio = "DTS")))

        assertEquals("AV1", OverlayRegistry.def(OverlayId.VideoCodec)!!.getValue(OverlayData(videoCodec = "AV1")))
        assertEquals(OverlayIconId.Av1, OverlayRegistry.def(OverlayId.VideoCodec)!!.getIcon!!(OverlayData(videoCodec = "AV1")))
        assertEquals(OverlayIconId.Film, OverlayRegistry.def(OverlayId.VideoCodec)!!.getIcon!!(OverlayData(videoCodec = "HEVC")))

        assertEquals("Multi-Audio", OverlayRegistry.def(OverlayId.MultiAudio)!!.getValue(OverlayData(multiAudio = true)))
        assertNull(OverlayRegistry.def(OverlayId.MultiAudio)!!.getValue(OverlayData(multiAudio = false)))
        assertEquals("CC", OverlayRegistry.def(OverlayId.MultiSub)!!.getValue(OverlayData(multiSub = true)))

        assertEquals("92%", OverlayRegistry.def(OverlayId.RatingRt)!!.getValue(OverlayData(ratingRtCritic = 92)))
        assertEquals("88%", OverlayRegistry.def(OverlayId.RatingRtAudience)!!.getValue(OverlayData(ratingRtAudience = 88)))
        assertEquals("7.5", OverlayRegistry.def(OverlayId.RatingTmdb)!!.getValue(OverlayData(ratingTmdb = 7.5)))
        assertEquals("PG-13", OverlayRegistry.def(OverlayId.ContentRating)!!.getValue(OverlayData(contentRating = "PG-13")))
        assertEquals("2020", OverlayRegistry.def(OverlayId.Year)!!.getValue(OverlayData(year = 2020)))
        assertEquals("A24", OverlayRegistry.def(OverlayId.Studio)!!.getValue(OverlayData(studio = "A24")))
        assertEquals("HBO", OverlayRegistry.def(OverlayId.Network)!!.getValue(OverlayData(network = "HBO")))
        assertEquals("EN", OverlayRegistry.def(OverlayId.OriginalLanguage)!!.getValue(OverlayData(originalLanguage = "en")))
        // Non-standard resolution falls through to uppercase (prettyResolution else).
        assertEquals("FOO", OverlayRegistry.def(OverlayId.ResolutionHdr)!!.getValue(OverlayData(resolution = "foo")))
        assertEquals("5.1", OverlayRegistry.def(OverlayId.AudioChannels)!!.getValue(OverlayData(audioChannels = "5.1")))
        assertEquals("mkv", OverlayRegistry.def(OverlayId.Container)!!.getValue(OverlayData(container = "mkv")))
        assertEquals("2.39:1", OverlayRegistry.def(OverlayId.AspectRatio)!!.getValue(OverlayData(aspectRatio = "2.39:1")))
        assertEquals("BluRay", OverlayRegistry.def(OverlayId.ReleaseType)!!.getValue(OverlayData(releaseType = "BluRay")))
        assertEquals("Extended", OverlayRegistry.def(OverlayId.Edition)!!.getValue(OverlayData(edition = "Extended")))
        assertEquals("Ended", OverlayRegistry.def(OverlayId.ShowStatus)!!.getValue(OverlayData(showStatus = "Ended")))
        assertEquals("Returning", OverlayRegistry.def(OverlayId.ShowStatus)!!.getValue(OverlayData(showStatus = "returning series")))
        assertEquals("Cancelled", OverlayRegistry.def(OverlayId.ShowStatus)!!.getValue(OverlayData(showStatus = "canceled")))
        assertEquals("Pilot", OverlayRegistry.def(OverlayId.ShowStatus)!!.getValue(OverlayData(showStatus = "Pilot")))
        assertNull(OverlayRegistry.def(OverlayId.ImdbTop250)!!.getValue(OverlayData()))
        assertEquals("#12", OverlayRegistry.def(OverlayId.ImdbTop250)!!.getValue(OverlayData(imdbTop250 = 12)))
        assertNull(OverlayRegistry.def(OverlayId.RtCertifiedFresh)!!.getValue(OverlayData()))
        assertEquals(
            "Certified Fresh",
            OverlayRegistry.def(OverlayId.RtCertifiedFresh)!!.getValue(OverlayData(rtCertifiedFresh = true)),
        )
    }
}
