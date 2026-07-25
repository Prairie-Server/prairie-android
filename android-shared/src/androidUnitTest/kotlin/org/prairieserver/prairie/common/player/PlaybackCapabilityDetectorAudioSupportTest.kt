package org.prairieserver.prairie.common.player

import androidx.media3.common.MimeTypes
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun `TV advertises platform decoders and leaves encoded support to passthrough`() {
        assertEquals(
            listOf("aac", "eac3"),
            advertisedAudioDecodeCodecs(
                platformCodecs = listOf("aac", "eac3"),
                ffmpegAvailable = true,
                isTv = true,
            ),
        )
    }

    @Test
    fun `phone advertises FFmpeg audio decoders`() {
        val codecs = advertisedAudioDecodeCodecs(
            platformCodecs = listOf("aac", "eac3"),
            ffmpegAvailable = true,
            isTv = false,
        )

        assertTrue("truehd" in codecs)
        assertTrue("dts_hd" in codecs)
    }

    @Test
    fun `TV does not lose codecs backed by platform decoders`() {
        assertEquals(
            listOf("aac", "truehd"),
            advertisedAudioDecodeCodecs(
                platformCodecs = listOf("aac", "truehd"),
                ffmpegAvailable = true,
                isTv = true,
            ),
        )
    }
}
