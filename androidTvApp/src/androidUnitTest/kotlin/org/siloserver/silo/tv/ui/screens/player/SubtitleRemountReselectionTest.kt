package org.siloserver.silo.tv.ui.screens.player

import org.siloserver.silo.model.playback.PlayerSubtitleInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubtitleRemountReselectionTest {
    @Test
    fun selectedServerSubtitleIsReappliedWhenTheReplannedTrackArrives() {
        val latch = SubtitleRemountReselection()
        val mounted = PlayerSubtitleInfo(
            index = 7,
            language = "en",
            codec = "webvtt",
            label = "English",
            url = "/subtitles/7.vtt",
        )
        val remountedTrack = PlayerTrackEntry(
            index = 3,
            label = "English",
            language = "en",
            codecOrMime = "text/vtt",
            isSelected = false,
        )

        latch.arm(serverSubtitleIndex = 7)

        assertNull(latch.consume(emptyList(), listOf(mounted)))
        assertEquals(3, latch.consume(listOf(remountedTrack), listOf(mounted)))
        assertNull(latch.consume(listOf(remountedTrack), listOf(mounted)))
    }

    @Test
    fun subtitleOffIsReappliedOnceAfterReplan() {
        val latch = SubtitleRemountReselection()

        latch.arm(serverSubtitleIndex = -1)

        assertEquals(-1, latch.consume(emptyList(), emptyList()))
        assertNull(latch.consume(emptyList(), emptyList()))
    }
}
