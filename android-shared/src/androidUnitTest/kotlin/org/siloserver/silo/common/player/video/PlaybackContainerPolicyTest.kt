package org.siloserver.silo.common.player.video

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackContainerPolicyTest {
    @Test
    fun mpvDirectContainersIncludeCommonOriginalFormats() {
        listOf(
            "mkv", "matroska", "mp4", "m4v", "webm", "avi", "mov", "qt", "ts", "mpegts", "mpeg-ts", "m2ts", "mts",
            // Legacy Windows Media: FFmpeg demuxes ASF fine; Media3 has no
            // support at all, so MPV is the only direct path (Apple parity —
            // its codec-tail direct-plays these).
            "asf", "wmv",
        )
            .forEach { container ->
                assertTrue(isMpvOriginalPlaybackContainer(container), "container=$container")
            }
    }

    @Test
    fun mpvPreferredContainersExcludePlainMedia3FriendlyFormats() {
        listOf("avi", "mov", "qt", "ts", "mpegts", "mpeg-ts", "m2ts", "mts", "asf", "wmv")
            .forEach { container ->
                assertTrue(isMpvPreferredOriginalPlaybackContainer(container), "container=$container")
            }
        assertFalse(isMpvPreferredOriginalPlaybackContainer("mkv"))
        assertFalse(isMpvPreferredOriginalPlaybackContainer("matroska"))
        assertFalse(isMpvPreferredOriginalPlaybackContainer("mp4"))
        assertFalse(isMpvPreferredOriginalPlaybackContainer("webm"))
    }
}
