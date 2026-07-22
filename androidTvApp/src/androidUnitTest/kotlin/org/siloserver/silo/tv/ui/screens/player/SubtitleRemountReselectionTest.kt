package org.siloserver.silo.tv.ui.screens.player

import org.siloserver.silo.model.playback.PlayerSubtitleInfo
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubtitleRemountReselectionTest {
    private val viewModelSource = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/player/TvPlayerViewModel.kt",
    ).readText()

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

    @Test
    fun everyTransportRemountMustDeclareTheSubtitleSelectionToRestore() {
        assertFalse(
            viewModelSource.contains("nextTransportMountNonce()"),
            "Every mount must explicitly pass the stable subtitle index, or null for the first mount",
        )
    }

    @Test
    fun seekRecoveryReappliesTheSelectedSubtitleAfterItsRemount() {
        val seekRecoveryBlock = viewModelSource
            .substringAfter("private suspend fun adoptSeekRecoveryDecision(")
            .substringBefore("private fun isCurrentSeekRecovery(")

        assertTrue(seekRecoveryBlock.contains("val selectedSubtitle = selectedSubtitleTrackIndex(before)"))
        assertTrue(seekRecoveryBlock.contains("subtitleTrackIndex = selectedSubtitle"))
        assertTrue(seekRecoveryBlock.contains("nextTransportMountNonce(selectedSubtitle)"))
    }
}
