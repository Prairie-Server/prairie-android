package org.prairieserver.prairie.playback

import org.prairieserver.prairie.model.playback.PlayerSubtitleInfo
import org.prairieserver.prairie.model.playback.SubtitleIdentity
import org.prairieserver.prairie.model.playback.SubtitleMediaIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackSubtitleIdentityRoutingTest {
    private fun row(
        url: String = "",
        source: String? = null,
        codec: String? = null,
        catalogSource: String? = null,
        downloadId: Int? = null,
        label: String? = null,
        language: String? = null,
        mediaTrackId: String? = null,
        serverTrackId: String? = null,
        serverDelivery: String? = null,
        nativeContainerTrackId: String? = null,
        forced: Boolean? = null,
    ) = PlayerSubtitleInfo(
        index = 3, url = url, source = source, codec = codec, catalogSource = catalogSource,
        downloadId = downloadId, label = label, language = language, mediaTrackId = mediaTrackId,
        serverTrackId = serverTrackId, serverDelivery = serverDelivery,
        nativeContainerTrackId = nativeContainerTrackId, forced = forced,
    )

    @Test
    fun routesEachRowShapeToItsIdentity() {
        val native = assertIs<SubtitleIdentity.Embedded>(playbackSubtitleIdentity(row(nativeContainerTrackId = "2")))
        assertEquals("2", native.containerTrackId)
        assertIs<SubtitleIdentity.ServerBurnIn>(playbackSubtitleIdentity(row(serverTrackId = "t", serverDelivery = "burn_in_only")))
        assertIs<SubtitleIdentity.ServerSidecar>(playbackSubtitleIdentity(row(serverTrackId = "t", serverDelivery = "sidecar")))

        val downloaded = assertIs<SubtitleIdentity.Downloaded>(playbackSubtitleIdentity(row(downloadId = 9, url = "file:///x.srt")))
        assertEquals("prairie-downloaded-subtitle:9", downloaded.media.trackId)
        assertEquals("subrip", downloaded.media.codecFamily)
        assertIs<SubtitleIdentity.LocalMedia3>(playbackSubtitleIdentity(row(source = "downloaded", mediaTrackId = "m")))
        assertIs<SubtitleIdentity.LocalMedia3>(playbackSubtitleIdentity(row(catalogSource = "Downloaded")))

        assertIs<SubtitleIdentity.ServerBurnIn>(playbackSubtitleIdentity(row(source = "embedded", codec = "dvd_subtitle")))
        assertIs<SubtitleIdentity.Embedded>(playbackSubtitleIdentity(row(source = "embedded", codec = "hdmv_pgs_subtitle")))
        assertIs<SubtitleIdentity.Embedded>(playbackSubtitleIdentity(row(catalogSource = "embedded", codec = "subrip")))

        assertIs<SubtitleIdentity.ServerBurnIn>(playbackSubtitleIdentity(row(source = "external", codec = "vobsub")))
        assertIs<SubtitleIdentity.ServerSidecar>(playbackSubtitleIdentity(row(url = "https://srv/sub.sup?x=1#y", codec = "pgs")))
        assertIs<SubtitleIdentity.ServerSidecar>(playbackSubtitleIdentity(row(source = "server_artifact", codec = "webvtt")))
        assertIs<SubtitleIdentity.ServerSidecar>(playbackSubtitleIdentity(row()))
    }

    @Test
    fun hearingImpairedLabels() {
        assertTrue(subtitleLabelIndicatesHearingImpaired("English (SDH)"))
        assertTrue(subtitleLabelIndicatesHearingImpaired("Closed Captions"))
        assertTrue(subtitleLabelIndicatesHearingImpaired("hearing-impaired"))
        assertTrue(subtitleLabelIndicatesHearingImpaired("en cc"))
        assertFalse(subtitleLabelIndicatesHearingImpaired("Accent"))
        assertFalse(subtitleLabelIndicatesHearingImpaired(null))
        val hi = playbackSubtitleIdentity(row(label = "English SDH")).subtitleMediaIdentityOrNull()
        assertEquals(true, hi?.hearingImpaired)
    }

    @Test
    fun mediaIdentityAccessorAndMatching() {
        val media = SubtitleMediaIdentity(trackId = "t", label = "English", language = "en", codecFamily = "subrip", forced = false, hearingImpaired = false)
        assertEquals(media, SubtitleIdentity.ServerBurnIn(1, media).subtitleMediaIdentityOrNull())
        assertEquals(media, SubtitleIdentity.Embedded(1, media).subtitleMediaIdentityOrNull())
        assertEquals(media, SubtitleIdentity.Downloaded(1, media).subtitleMediaIdentityOrNull())
        assertEquals(media, SubtitleIdentity.LocalMedia3(media).subtitleMediaIdentityOrNull())
        assertEquals(media, SubtitleIdentity.ServerSidecar(1, media).subtitleMediaIdentityOrNull())
        assertNull(SubtitleIdentity.Off.subtitleMediaIdentityOrNull())

        assertTrue(media.matchesSubtitleMediaIdentity(media))
        assertTrue(media.matchesSubtitleMediaIdentity(SubtitleMediaIdentity()))
        assertFalse(media.matchesSubtitleMediaIdentity(SubtitleMediaIdentity(trackId = "other")))
        assertFalse(media.matchesSubtitleMediaIdentity(SubtitleMediaIdentity(label = "French")))
        assertFalse(media.matchesSubtitleMediaIdentity(SubtitleMediaIdentity(language = "fr")))
        assertFalse(media.matchesSubtitleMediaIdentity(SubtitleMediaIdentity(codecFamily = "pgs")))
        assertFalse(media.matchesSubtitleMediaIdentity(SubtitleMediaIdentity(forced = true)))
        assertFalse(media.matchesSubtitleMediaIdentity(SubtitleMediaIdentity(hearingImpaired = true)))

        assertFalse(SubtitleMediaIdentity().hasPositiveSubtitleDiscriminator())
        assertTrue(SubtitleMediaIdentity(forced = true).hasPositiveSubtitleDiscriminator())
        assertTrue(SubtitleMediaIdentity(hearingImpaired = true).hasPositiveSubtitleDiscriminator())
    }

    @Test
    fun downloadedPreferenceResolution() {
        val identity = SubtitleIdentity.Downloaded(
            5,
            SubtitleMediaIdentity(trackId = downloadedSubtitleArtifactTrackId(5), language = "en", codecFamily = "subrip", forced = false),
        )
        assertEquals(1, resolveDownloadedSubtitlePreferenceOrdinal(identity, listOf(row(downloadId = 4), row(downloadId = 5))))
        assertNull(resolveDownloadedSubtitlePreferenceOrdinal(identity, listOf(row(downloadId = 5), row(downloadId = 5))))

        val inventory = listOf(
            row(serverTrackId = "a", serverDelivery = "sidecar", source = "downloaded", language = "en", codec = "subrip"),
            row(serverTrackId = "b", serverDelivery = "sidecar", source = "downloaded", language = "fr", codec = "subrip"),
            row(serverTrackId = null, serverDelivery = "sidecar", source = "downloaded", language = "en"),
            row(serverTrackId = "c", serverDelivery = "sidecar", source = "external", language = "en"),
        )
        assertEquals(0, resolveDownloadedSubtitlePreferenceOrdinal(identity, inventory))
        val blank = SubtitleIdentity.Downloaded(6, SubtitleMediaIdentity(trackId = downloadedSubtitleArtifactTrackId(6), forced = false))
        assertNull(resolveDownloadedSubtitlePreferenceOrdinal(blank, inventory))
    }
}
