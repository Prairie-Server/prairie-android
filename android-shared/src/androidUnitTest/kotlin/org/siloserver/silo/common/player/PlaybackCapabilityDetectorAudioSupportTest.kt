package org.siloserver.silo.common.player

import androidx.media3.common.MimeTypes
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackCapabilityDetectorAudioSupportTest {

    @Test
    fun `DTS HD is software decodable when FFmpeg renderer is available`() {
        assertTrue(
            isSoftwareDecodableAudioMime(
                mime = MimeTypes.AUDIO_DTS_HD,
                ffmpegAvailable = true,
            ),
        )
    }

    @Test
    fun `DTS HD is not software decodable without FFmpeg renderer`() {
        assertFalse(
            isSoftwareDecodableAudioMime(
                mime = MimeTypes.AUDIO_DTS_HD,
                ffmpegAvailable = false,
            ),
        )
    }

    @Test
    fun `AAC remains platform software decodable without FFmpeg renderer`() {
        assertTrue(
            isSoftwareDecodableAudioMime(
                mime = MimeTypes.AUDIO_AAC,
                ffmpegAvailable = false,
            ),
        )
    }
}
