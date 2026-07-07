package org.siloserver.silo.common.player.backend

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.siloserver.silo.common.player.video.isMpvSoftwareDecodableVideoCodec
import org.siloserver.silo.common.player.video.mpvSoftwareVideoCodecs

class MpvSoftwareVideoCodecPolicyTest {

    @Test
    fun coversAppleCodecTailEquivalents() {
        // Apple's CompatibilityPlayer software-decodes AV1 (dav1d), VP9/VP8,
        // MPEG-4 Part 2 (DivX/Xvid), MPEG-2, VC-1/WMV9 — MPV's FFmpeg build
        // decodes the same set.
        listOf("av1", "vp9", "vp8", "mpeg4", "mpeg2video", "vc1", "wmv3").forEach {
            assertTrue(isMpvSoftwareDecodableVideoCodec(it), "codec=$it")
        }
        assertTrue(isMpvSoftwareDecodableVideoCodec("AV1"))
        assertTrue(isMpvSoftwareDecodableVideoCodec(" vp9 "))
    }

    @Test
    fun neverClaimsUnknownOrModernHwCodecs() {
        // h264/hevc are always hardware-probed; never claim them via the
        // software tail (they'd bypass the DV/HDR capability checks).
        listOf("h264", "hevc", null, "", "prores", "unknown").forEach {
            assertFalse(isMpvSoftwareDecodableVideoCodec(it), "codec=$it")
        }
    }

    @Test
    fun advertisedListMatchesPredicate() {
        mpvSoftwareVideoCodecs.forEach {
            assertTrue(isMpvSoftwareDecodableVideoCodec(it), "codec=$it")
        }
    }
}
