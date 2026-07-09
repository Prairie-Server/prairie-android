package org.siloserver.silo.common.player.backend

import org.siloserver.silo.model.playback.PlayMethod
import org.siloserver.silo.model.playback.PlaybackDelivery
import org.siloserver.silo.model.playback.PlaybackEngineKind
import kotlin.test.Test
import kotlin.test.assertEquals

class VideoPlaybackBackendSelectorTest {
    @Test
    fun explicitMedia3PreferenceWins() {
        val request = VideoPlaybackBackendRequest(
            preference = VideoPlaybackBackendPreference.Media3,
            hasHardContainer = true,
            hasStyledSubtitles = true,
        )

        assertEquals(VideoPlaybackBackendKind.Media3, VideoPlaybackBackendSelector.select(request))
    }

    @Test
    fun explicitMpvPreferenceWins() {
        val request = VideoPlaybackBackendRequest(preference = VideoPlaybackBackendPreference.Mpv)

        assertEquals(VideoPlaybackBackendKind.Mpv, VideoPlaybackBackendSelector.select(request))
    }

    @Test
    fun explicitMpvPreferenceFallsBackToMedia3WhenDeviceUnsupported() {
        val request = VideoPlaybackBackendRequest(
            preference = VideoPlaybackBackendPreference.Mpv,
            mpvSupportedOnDevice = false,
        )

        assertEquals(VideoPlaybackBackendKind.Media3, VideoPlaybackBackendSelector.select(request))
    }

    @Test
    fun plannedMpvDirectFallsBackToMedia3WhenDeviceUnsupported() {
        val request = VideoPlaybackBackendRequest(
            playMethod = PlayMethod.DIRECT,
            delivery = PlaybackDelivery.ORIGINAL_HTTP,
            plannedEngine = PlaybackEngineKind.MPV_DIRECT,
            hasHardContainer = true,
            hasStyledSubtitles = true,
            mpvSupportedOnDevice = false,
        )

        assertEquals(VideoPlaybackBackendKind.Media3, VideoPlaybackBackendSelector.select(request))
    }

    @Test
    fun autoUsesMedia3ForTranscode() {
        val request = VideoPlaybackBackendRequest(playMethod = PlayMethod.TRANSCODE)

        assertEquals(VideoPlaybackBackendKind.Media3, VideoPlaybackBackendSelector.select(request))
    }

    @Test
    fun autoHonorsPlannedMpvDirectWhenDeviceSupportsIt() {
        val request = VideoPlaybackBackendRequest(
            playMethod = PlayMethod.DIRECT,
            delivery = PlaybackDelivery.ORIGINAL_HTTP,
            plannedEngine = PlaybackEngineKind.MPV_DIRECT,
            mpvSupportedOnDevice = true,
        )

        assertEquals(VideoPlaybackBackendKind.Mpv, VideoPlaybackBackendSelector.select(request))
    }

    @Test
    fun autoForcesMedia3ForPlannedHlsDelivery() {
        val request = VideoPlaybackBackendRequest(
            playMethod = PlayMethod.REMUX,
            delivery = PlaybackDelivery.SERVER_REMUX_HLS,
            plannedEngine = PlaybackEngineKind.MPV_DIRECT,
            hasHardContainer = true,
        )

        assertEquals(VideoPlaybackBackendKind.Media3, VideoPlaybackBackendSelector.select(request))
    }

    @Test
    fun autoUsesMpvForLegacyProgressiveServerRemuxWhenDeviceSupportsIt() {
        val request = VideoPlaybackBackendRequest(
            playMethod = PlayMethod.REMUX,
            delivery = PlaybackDelivery.SERVER_REMUX_PROGRESSIVE,
            hasHardContainer = true,
        )

        assertEquals(VideoPlaybackBackendKind.Mpv, VideoPlaybackBackendSelector.select(request))
    }

    @Test
    fun autoHonorsExplicitPlannedMedia3ProgressiveServerRemux() {
        val request = VideoPlaybackBackendRequest(
            playMethod = PlayMethod.REMUX,
            delivery = PlaybackDelivery.SERVER_REMUX_PROGRESSIVE,
            plannedEngine = PlaybackEngineKind.MEDIA3_PROGRESSIVE_REMUX,
            hasHardContainer = true,
        )

        assertEquals(VideoPlaybackBackendKind.Media3, VideoPlaybackBackendSelector.select(request))
    }

    @Test
    fun autoUsesMpvForHardContainersOrStyledSubtitles() {
        assertEquals(
            VideoPlaybackBackendKind.Mpv,
            VideoPlaybackBackendSelector.select(VideoPlaybackBackendRequest(hasHardContainer = true)),
        )
        assertEquals(
            VideoPlaybackBackendKind.Mpv,
            VideoPlaybackBackendSelector.select(VideoPlaybackBackendRequest(hasStyledSubtitles = true)),
        )
    }

    @Test
    fun autoUsesMedia3ForAdaptiveHlsEvenWithStyledSubtitles() {
        val request = VideoPlaybackBackendRequest(
            playMethod = PlayMethod.REMUX,
            hasStyledSubtitles = true,
            isAdaptiveHlsStream = true,
        )

        assertEquals(VideoPlaybackBackendKind.Media3, VideoPlaybackBackendSelector.select(request))
    }

    @Test
    fun autoUsesMedia3ForPlainProgressiveMp4Direct() {
        val request = VideoPlaybackBackendRequest(
            playMethod = PlayMethod.DIRECT,
            delivery = PlaybackDelivery.ORIGINAL_HTTP,
            hasHardContainer = false,
            hasStyledSubtitles = false,
        )

        assertEquals(VideoPlaybackBackendKind.Media3, VideoPlaybackBackendSelector.select(request))
    }

    @Test
    fun hlsStreamUrlDetectionHandlesPlaylistUrlsWithoutLeakingUrlIntoRequest() {
        assertEquals(true, isLikelyAdaptiveHlsStreamUrl("/api/v1/playback/transcode/session/master.m3u8"))
        assertEquals(true, isLikelyAdaptiveHlsStreamUrl("https://example.test/video/playlist.m3u8?token=secret"))
        assertEquals(false, isLikelyAdaptiveHlsStreamUrl("https://example.test/video/file.mkv"))
        assertEquals(false, VideoPlaybackBackendRequest().isAdaptiveHlsStream)
    }

    @Test
    fun autoUsesMedia3ForKnownVideoFormFactors() {
        assertEquals(
            VideoPlaybackBackendKind.Media3,
            VideoPlaybackBackendSelector.select(
                VideoPlaybackBackendRequest(formFactor = VideoPlaybackFormFactor.Mobile),
            ),
        )
        assertEquals(
            VideoPlaybackBackendKind.Media3,
            VideoPlaybackBackendSelector.select(
                VideoPlaybackBackendRequest(formFactor = VideoPlaybackFormFactor.Tv),
            ),
        )
    }

    @Test
    fun autoKeepsMedia3ForUnknownFormFactor() {
        assertEquals(
            VideoPlaybackBackendKind.Media3,
            VideoPlaybackBackendSelector.select(VideoPlaybackBackendRequest()),
        )
    }

    @Test
    fun autoForcesMedia3WhenCasting() {
        val request = VideoPlaybackBackendRequest(isCasting = true, hasHardContainer = true)
        assertEquals(VideoPlaybackBackendKind.Media3, VideoPlaybackBackendSelector.select(request))
    }

    @Test
    fun autoForcesMedia3WhenDrmProtected() {
        val request = VideoPlaybackBackendRequest(isDrmProtected = true, hasStyledSubtitles = true)
        assertEquals(VideoPlaybackBackendKind.Media3, VideoPlaybackBackendSelector.select(request))
    }

    @Test
    fun autoForcesMedia3OnExternalDisplay() {
        val request = VideoPlaybackBackendRequest(isExternalDisplay = true, hasHardContainer = true)
        assertEquals(VideoPlaybackBackendKind.Media3, VideoPlaybackBackendSelector.select(request))
    }

    @Test
    fun autoFallsBackToMedia3BelowMpvDeviceFloor() {
        val request = VideoPlaybackBackendRequest(
            hasHardContainer = true,
            mpvSupportedOnDevice = false,
        )
        assertEquals(VideoPlaybackBackendKind.Media3, VideoPlaybackBackendSelector.select(request))
    }
    @Test
    fun autoRoutesSoftwareOnlyVideoCodecToMpv() {
        assertEquals(
            VideoPlaybackBackendKind.Mpv,
            VideoPlaybackBackendSelector.select(
                VideoPlaybackBackendRequest(
                    playMethod = PlayMethod.DIRECT,
                    hasSoftwareOnlyVideoCodec = true,
                ),
            ),
        )
    }

    @Test
    fun softwareOnlyCodecNeverSelectsMpvBelowDeviceFloor() {
        assertEquals(
            VideoPlaybackBackendKind.Media3,
            VideoPlaybackBackendSelector.select(
                VideoPlaybackBackendRequest(
                    playMethod = PlayMethod.DIRECT,
                    hasSoftwareOnlyVideoCodec = true,
                    mpvSupportedOnDevice = false,
                ),
            ),
        )
    }

    @Test
    fun autoRoutesDolbyVisionToMpv() {
        assertEquals(
            VideoPlaybackBackendKind.Mpv,
            VideoPlaybackBackendSelector.select(
                VideoPlaybackBackendRequest(
                    playMethod = PlayMethod.DIRECT,
                    hasDolbyVisionVideo = true,
                ),
            ),
        )
    }

    @Test
    fun dolbyVisionNeverSelectsMpvBelowDeviceFloor() {
        assertEquals(
            VideoPlaybackBackendKind.Media3,
            VideoPlaybackBackendSelector.select(
                VideoPlaybackBackendRequest(
                    playMethod = PlayMethod.DIRECT,
                    hasDolbyVisionVideo = true,
                    mpvSupportedOnDevice = false,
                ),
            ),
        )
    }

    @Test
    fun dolbyVisionOutranksPlannedMedia3Direct() {
        // The v2 plan is computed without phone-side HDR knowledge; a planned
        // MEDIA3_DIRECT must not pull HDR back onto the black-screen path.
        assertEquals(
            VideoPlaybackBackendKind.Mpv,
            VideoPlaybackBackendSelector.select(
                VideoPlaybackBackendRequest(
                    playMethod = PlayMethod.DIRECT,
                    plannedEngine = PlaybackEngineKind.MEDIA3_DIRECT,
                    hasDolbyVisionVideo = true,
                ),
            ),
        )
    }

    @Test
    fun dolbyVisionStillTranscodesOnMedia3() {
        assertEquals(
            VideoPlaybackBackendKind.Media3,
            VideoPlaybackBackendSelector.select(
                VideoPlaybackBackendRequest(
                    playMethod = PlayMethod.TRANSCODE,
                    hasDolbyVisionVideo = true,
                ),
            ),
        )
    }

}
