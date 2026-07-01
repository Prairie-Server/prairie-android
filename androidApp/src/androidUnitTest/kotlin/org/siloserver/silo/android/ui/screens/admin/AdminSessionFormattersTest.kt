package org.siloserver.silo.android.ui.screens.admin

import kotlin.test.Test
import kotlin.test.assertEquals

class AdminSessionFormattersTest {

    @Test
    fun directPlayShowsDirectWithBitrateAndResolution() {
        val line = sessionSummaryLine(
            isTranscoding = false, playMethod = "DirectPlay",
            bitrateBps = 12_000_000, widthTarget = 1920, heightTarget = 1080,
            videoCodecSource = "h264", videoCodecTarget = "h264",
        )
        assertEquals("Direct Play • 12.0 Mbps • 1080p", line)
    }

    @Test
    fun transcodeShowsCodecArrowAndResolution() {
        val line = sessionSummaryLine(
            isTranscoding = true, playMethod = "Transcode",
            bitrateBps = 4_500_000, widthTarget = 1280, heightTarget = 720,
            videoCodecSource = "hevc", videoCodecTarget = "h264",
        )
        assertEquals("Transcode hevc→h264 • 4.5 Mbps • 720p", line)
    }

    @Test
    fun missingBitrateAndResolutionAreOmitted() {
        val line = sessionSummaryLine(
            isTranscoding = false, playMethod = "DirectStream",
            bitrateBps = null, widthTarget = null, heightTarget = null,
            videoCodecSource = null, videoCodecTarget = null,
        )
        assertEquals("Direct Stream", line)
    }

    @Test
    fun resolutionBucketsToNearestStandardLabel() {
        assertEquals("4K", resolutionLabel(3840, 2160))
        assertEquals("1080p", resolutionLabel(1920, 1080))
        assertEquals("720p", resolutionLabel(1280, 720))
        assertEquals("480p", resolutionLabel(854, 480))
        assertEquals("576p", resolutionLabel(720, 576))
    }

    @Test
    fun bitrateRendersMbpsWithOneDecimal() {
        assertEquals("4.5 Mbps", bitrateLabel(4_500_000))
        assertEquals("950 Kbps", bitrateLabel(950_000))
    }

    @Test
    fun progressLabelIsPositionOfDuration() {
        assertEquals("0:30 / 1:00:00", sessionProgressLabel(30.0, 3600.0))
        assertEquals("0:30", sessionProgressLabel(30.0, 0.0))
    }
}
